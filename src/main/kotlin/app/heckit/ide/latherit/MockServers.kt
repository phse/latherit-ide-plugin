package app.heckit.ide.latherit

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong

/** Was die Oberfläche über einen eingehenden Request erfährt (wie `MockRequestEvent` in src/lib/backend.ts). */
class MockRequestEvent(
    val mockId: String,
    val requestId: Long,
    val method: String,
    val path: String,
    val query: String,
    val headers: List<List<String>>,
    val body: String,
    val remote: String,
)

/** Antwort der Oberfläche über `mock_respond`. */
class MockAnswer(
    val status: Int?,
    val headers: List<List<String>>?,
    val body: String?,
)

/**
 * Mock-Server (Gegenstück zu src-tauri/src/mock.rs): nimmt HTTP-Requests auf einem lokalen Port an und fragt
 * die Oberfläche, was geantwortet werden soll - die ganze Logik steckt im TypeScript-Teil.
 *
 * Ablauf: Request kommt an → Ereignis `mock-request` an die Oberfläche → die ruft `mock_respond` mit derselben
 * Request-ID auf → Antwort geht an den Client. Antwortet die Oberfläche nicht rechtzeitig, bekommt der Client 504.
 *
 * Die Mocks gehören zum Editor-Tab, der sie gestartet hat, und enden mit ihm ([stopAll]).
 */
class MockServers(private val emit: (event: String, payload: Any) -> Unit) {
    /** Wie lange auf die Antwort der Oberfläche gewartet wird (Mock-Responses können Verzögerungen haben). */
    internal var responseTimeoutMs = 120_000L

    private class Running(val server: HttpServer, val executor: ExecutorService)

    private val running = ConcurrentHashMap<String, Running>()
    private val pending = ConcurrentHashMap<Long, CompletableFuture<MockAnswer>>()
    private val nextId = AtomicLong(1)

    /** Startet einen Mock auf `port` (0 = beliebiger freier). `public` = auch aus dem Netzwerk erreichbar. */
    @Synchronized
    fun start(mockId: String, port: Int, public: Boolean): Int {
        if (running.containsKey(mockId)) throw HostException("Der Mock läuft bereits")
        val host = if (public) InetAddress.getByName("0.0.0.0") else InetAddress.getByName("127.0.0.1")
        val server = try {
            HttpServer.create(InetSocketAddress(host, port), 0)
        } catch (e: IOException) {
            throw HostException("Port $port lässt sich nicht öffnen: ${e.message}")
        }
        val executor = Executors.newVirtualThreadPerTaskExecutor()
        server.executor = executor
        server.createContext("/") { exchange -> exchange.use { handle(mockId, it) } }
        server.start()
        running[mockId] = Running(server, executor)
        return server.address.port
    }

    fun stop(mockId: String) {
        running.remove(mockId)?.let { shutdown(it) }
    }

    /** Laufende Mocks (ID → Port) - z. B. nach einem Neuladen der Oberfläche. */
    fun running(): Map<String, Int> = running.mapValues { it.value.server.address.port }

    fun respond(requestId: Long, answer: MockAnswer) {
        pending.remove(requestId)?.complete(answer)
    }

    fun stopAll() {
        running.keys.toList().forEach(::stop)
    }

    private fun shutdown(entry: Running) {
        entry.server.stop(0)
        entry.executor.shutdownNow()
    }

    private fun handle(mockId: String, exchange: HttpExchange) {
        val body = try {
            String(exchange.requestBody.readAllBytes(), Charsets.UTF_8)
        } catch (e: IOException) {
            return plain(exchange, 400, "Body nicht lesbar: ${e.message}")
        }
        val requestId = nextId.getAndIncrement()
        val future = CompletableFuture<MockAnswer>()
        pending[requestId] = future
        val remote = exchange.remoteAddress
        emit(
            "mock-request",
            MockRequestEvent(
                mockId = mockId,
                requestId = requestId,
                method = exchange.requestMethod,
                path = exchange.requestURI.rawPath ?: "/",
                query = exchange.requestURI.rawQuery.orEmpty(),
                headers = exchange.requestHeaders.flatMap { (name, values) -> values.map { listOf(name, it) } },
                body = body,
                remote = "${remote.address.hostAddress}:${remote.port}",
            ),
        )
        val answer = try {
            future.get(responseTimeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            null
        } catch (_: InterruptedException) {
            null
        } finally {
            pending.remove(requestId)
        }
        if (answer == null) return plain(exchange, 504, "LatherIt hat nicht rechtzeitig geantwortet")
        send(exchange, answer.status ?: 200, answer.headers.orEmpty(), answer.body.orEmpty())
    }

    private fun send(exchange: HttpExchange, status: Int, headers: List<List<String>>, body: String) {
        for (header in headers.filter { it.size == 2 }) {
            // Länge und Übertragungsart bestimmt der Server selbst
            if (header[0].lowercase() in setOf("content-length", "transfer-encoding")) continue
            exchange.responseHeaders.add(header[0], header[1])
        }
        val bytes = body.toByteArray(Charsets.UTF_8)
        val noBody = exchange.requestMethod.equals("HEAD", ignoreCase = true) || status == 204 || status == 304 || status < 200
        // -1 = ohne Body; 0 hieße „chunked“
        exchange.sendResponseHeaders(status, if (noBody || bytes.isEmpty()) -1 else bytes.size.toLong())
        if (!noBody && bytes.isNotEmpty()) exchange.responseBody.write(bytes)
    }

    private fun plain(exchange: HttpExchange, status: Int, text: String) =
        send(exchange, status, listOf(listOf("Content-Type", "text/plain; charset=utf-8")), text)
}
