package app.heckit.ide.latherit

import com.intellij.util.net.JdkProxyProvider
import java.io.IOException
import java.net.ConnectException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/** Request wie in src/lib/backend.ts (`HttpRequest`). */
class HttpRequestData(
    val method: String?,
    val url: String?,
    val headers: List<List<String>>?,
    val body: String?,
    val timeoutMs: Long?,
    val auth: AuthData?,
)

/** Anmeldung wie `BackendAuth` in src/lib/auth.ts - Werte bereits expandiert. */
class AuthData(
    val kind: String?,
    val username: String? = null,
    val password: String? = null,
    /** Nur NTLM: Windows-Domäne (alternativ `DOMÄNE\\benutzer` als Benutzername) */
    val domain: String? = null,
    /** Nur NTLM: Name des Rechners, der sich anmeldet */
    val workstation: String? = null,
    /** Nur Bearer */
    val token: String? = null,
)

/** Antwort wie in src/lib/backend.ts (`HttpResponse`). */
class HttpResponseData(
    val status: Int,
    val statusText: String,
    val headers: List<List<String>>,
    val body: String,
    val timeMs: Long,
    val size: Int,
)

/**
 * HTTP für die Oberfläche (Gegenstück zu src-tauri/src/http.rs). Proxy aus den Einstellungen der IDE,
 * Zertifikate wie im Browser (siehe [SystemTrust]).
 */
object HttpBackend {
    private val client: HttpClient by lazy { newClient() }

    private fun newClient(): HttpClient {
        val proxies = JdkProxyProvider.getInstance()
        return HttpClient.newBuilder()
            // SOAP-Server erwarten HTTP/1.1; HTTP/2 würde Header-Namen außerdem kleinschreiben.
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .proxy(proxies.proxySelector)
            .authenticator(proxies.authenticator)
            .sslContext(SystemTrust.sslContext())
            .build()
    }

    /** Diese Header setzt Javas HTTP-Client selbst und lehnt sie im Request ab. */
    private val restrictedHeaders = setOf("connection", "content-length", "expect", "host", "upgrade")

    /** Digest und NTLM brauchen mehrere Runden (Challenge/Response); gemessen wird die Gesamtzeit. */
    fun execute(request: HttpRequestData): HttpResponseData {
        val url = request.url ?: throw HostException("Ungültiger Request: Adresse fehlt")
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            throw HostException("Ungültige Adresse $url: ${e.message}")
        }
        val method = request.method.orEmpty().uppercase()
        val base = try {
            val builder = HttpRequest.newBuilder(uri)
            for ((name, value) in request.headers.orEmpty().filter { it.size == 2 }.map { it[0] to it[1] }) {
                if (name.lowercase() !in restrictedHeaders) builder.header(name, value)
            }
            if (request.headers.orEmpty().none { it.firstOrNull().equals("User-Agent", ignoreCase = true) }) {
                builder.setHeader("User-Agent", "LatherIt")
            }
            val body = request.body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody()
            builder.method(method, body)
            request.timeoutMs?.takeIf { it > 0 }?.let { builder.timeout(Duration.ofMillis(it)) }
            builder
        } catch (e: IllegalArgumentException) {
            throw HostException("Ungültiger Request: ${e.message}")
        }

        /** Ein Versuch, optional mit `Authorization`-Header. */
        fun attempt(via: HttpClient, authorization: String?): HttpResponse<ByteArray> {
            val builder = base.copy()
            authorization?.let { builder.setHeader("Authorization", it) }
            return send(via, builder.build())
        }

        val started = System.nanoTime()
        val auth = request.auth
        val response = when (auth?.kind) {
            null -> attempt(client, null)
            "basic", "bearer" -> attempt(client, ChallengeAuth.simpleHeader(auth))
            "digest" -> ChallengeAuth.digest(auth, method, uri, request.body) { attempt(client, it) }
            // NTLM authentifiziert eine Verbindung - daher ein eigener Client: Beide Runden laufen nacheinander
            // über dessen einzige Verbindung.
            "ntlm" -> newClient().use { single -> ChallengeAuth.ntlm(auth) { attempt(single, it) } }
            else -> throw HostException("Unbekannte Anmeldung: ${auth.kind}")
        }
        val bytes = response.body()
        val timeMs = (System.nanoTime() - started) / 1_000_000
        return HttpResponseData(
            status = response.statusCode(),
            statusText = reasonPhrase(response.statusCode()),
            headers = response.headers().map().flatMap { (name, values) -> values.map { listOf(name, it) } },
            body = decode(bytes, charset(response)),
            timeMs = timeMs,
            size = bytes.size,
        )
    }

    /** Lädt eine Textressource (WSDL, XSD) per `http(s)://` oder `file://`. */
    fun loadResource(url: String): String {
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            throw HostException("Ungültige Adresse $url: ${e.message}")
        }
        if (uri.scheme == "file") {
            val path = try {
                Path.of(uri)
            } catch (_: Exception) {
                throw HostException("Ungültiger Dateipfad: $url")
            }
            return try {
                decode(Files.readAllBytes(path), null)
            } catch (e: IOException) {
                throw HostException("$path: ${e.message}")
            }
        }
        val request = HttpRequest.newBuilder(uri)
            .GET()
            .header("User-Agent", "LatherIt")
            .timeout(Duration.ofSeconds(60))
            .build()
        val response = send(client, request)
        if (response.statusCode() !in 200..299) throw HostException("$url lieferte HTTP ${response.statusCode()}")
        return decode(response.body(), charset(response))
    }

    /** Bytes in Text umwandeln: BOM hat Vorrang, dann der Charset-Header, sonst UTF-8. */
    fun decode(bytes: ByteArray, charset: String?): String {
        fun startsWith(vararg prefix: Int) = bytes.size >= prefix.size && prefix.indices.all { bytes[it] == prefix[it].toByte() }
        return when {
            startsWith(0xEF, 0xBB, 0xBF) -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            startsWith(0xFE, 0xFF) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            startsWith(0xFF, 0xFE) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            else -> String(bytes, charset?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8)
        }
    }

    private fun send(via: HttpClient, request: HttpRequest): HttpResponse<ByteArray> = try {
        via.send(request, HttpResponse.BodyHandlers.ofByteArray())
    } catch (e: HttpTimeoutException) {
        throw HostException(describe("Zeitüberschreitung", request, e))
    } catch (e: ConnectException) {
        throw HostException(describe("Verbindung fehlgeschlagen", request, e))
    } catch (e: IOException) {
        throw HostException(describe("Fehler", request, e))
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw HostException("Abgebrochen")
    }

    /** Zeichensatz aus `Content-Type: …; charset=xyz`. */
    private fun charset(response: HttpResponse<*>): String? =
        response.headers().firstValue("Content-Type").orElse(null)
            ?.split(';')
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("charset=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.trim('"', ' ')

    /** Meldung mit Adresse und Ursachenkette, wie in http.rs. Javas Ausnahmen haben oft keinen Text. */
    private fun describe(kind: String, request: HttpRequest, error: Throwable): String = buildString {
        append("$kind: ${request.method()} ${request.uri()}")
        val seen = mutableSetOf<String>()
        var cause: Throwable? = error
        while (cause != null) {
            val text = cause.message?.takeIf { it.isNotBlank() }
            if (text != null && seen.add(text)) append("\n  → $text")
            cause = cause.cause?.takeIf { it !== cause }
        }
    }

    /** Javas HTTP-Client liefert den Status-Text des Servers nicht mit - daher die Standardtexte. */
    private fun reasonPhrase(status: Int): String = when (status) {
        100 -> "Continue"
        101 -> "Switching Protocols"
        200 -> "OK"
        201 -> "Created"
        202 -> "Accepted"
        204 -> "No Content"
        206 -> "Partial Content"
        301 -> "Moved Permanently"
        302 -> "Found"
        303 -> "See Other"
        304 -> "Not Modified"
        307 -> "Temporary Redirect"
        308 -> "Permanent Redirect"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        406 -> "Not Acceptable"
        408 -> "Request Timeout"
        409 -> "Conflict"
        410 -> "Gone"
        411 -> "Length Required"
        412 -> "Precondition Failed"
        413 -> "Content Too Large"
        415 -> "Unsupported Media Type"
        422 -> "Unprocessable Content"
        429 -> "Too Many Requests"
        500 -> "Internal Server Error"
        501 -> "Not Implemented"
        502 -> "Bad Gateway"
        503 -> "Service Unavailable"
        504 -> "Gateway Timeout"
        else -> ""
    }
}
