package app.heckit.ide.latherit

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList

/**
 * HTTP gegen einen lokalen Testserver. Braucht die IDE-Umgebung (Proxy-Einstellungen, Zertifikate).
 */
class HttpBackendTest : BasePlatformTestCase() {
    private lateinit var server: HttpServer
    private val received = CopyOnWriteArrayList<Received>()
    private val base get() = "http://127.0.0.1:${server.address.port}"

    private class Received(val method: String, val path: String, val headers: Map<String, List<String>>, val body: String)

    override fun setUp() {
        super.setUp()
        server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
        server.createContext("/") { exchange -> exchange.use { handle(it) } }
        server.start()
    }

    override fun tearDown() {
        try {
            server.stop(0)
        } finally {
            super.tearDown()
        }
    }

    private fun handle(exchange: HttpExchange) {
        val body = String(exchange.requestBody.readAllBytes(), Charsets.UTF_8)
        received += Received(exchange.requestMethod, exchange.requestURI.toString(), exchange.requestHeaders.toMap(), body)
        fun reply(status: Int, text: ByteArray, vararg headers: Pair<String, String>) {
            for ((name, value) in headers) exchange.responseHeaders.add(name, value)
            exchange.sendResponseHeaders(status, if (text.isEmpty()) -1 else text.size.toLong())
            if (text.isNotEmpty()) exchange.responseBody.write(text)
        }
        when (exchange.requestURI.path) {
            "/latin1" -> reply(200, "Grüße".toByteArray(Charsets.ISO_8859_1), "Content-Type" to "text/plain; charset=\"ISO-8859-1\"")
            "/umleitung" -> reply(302, ByteArray(0), "Location" to "/ziel")
            "/ziel" -> reply(200, "angekommen".toByteArray())
            "/fehlt" -> reply(404, "nicht da".toByteArray())
            "/langsam" -> {
                Thread.sleep(2_000)
                reply(200, "zu spät".toByteArray())
            }
            "/gzip" -> {
                val packed = java.io.ByteArrayOutputStream().also { out ->
                    java.util.zip.GZIPOutputStream(out).use { it.write("entpackt äöü".toByteArray()) }
                }.toByteArray()
                reply(200, packed, "Content-Encoding" to "gzip", "Content-Type" to "text/plain; charset=utf-8")
            }
            "/geschuetzt" -> reply(401, ByteArray(0), "WWW-Authenticate" to "Basic realm=\"x\"", "WWW-Authenticate" to "NTLM")
            "/mehrfach" -> reply(200, ByteArray(0), "Set-Cookie" to "a=1", "Set-Cookie" to "b=2")
            else -> reply(200, "<ok/>".toByteArray(), "Content-Type" to "text/xml")
        }
    }

    private fun request(
        url: String,
        method: String = "GET",
        headers: List<List<String>>? = null,
        body: String? = null,
        timeoutMs: Long? = null,
        auth: AuthData? = null,
    ) = HttpBackend.execute(HttpRequestData(method, url, headers, body, timeoutMs, auth))

    private fun failure(action: () -> Unit): String {
        val error = runCatching(action).exceptionOrNull()
        assertTrue("HostException erwartet, war $error", error is HostException)
        return error!!.message!!
    }

    // ---------- execute ----------

    fun testSoapPost() {
        val response = request(
            "$base/service?wsdl=0",
            method = "post",
            headers = listOf(listOf("Content-Type", "text/xml; charset=utf-8"), listOf("SOAPAction", "\"urn:x\"")),
            body = "<Envelope>ä</Envelope>",
        )
        assertEquals(200, response.status)
        assertEquals("OK", response.statusText)
        assertEquals("<ok/>", response.body)
        assertEquals(5, response.size)
        assertTrue(response.headers.any { it[0].equals("content-type", true) && it[1] == "text/xml" })

        val seen = received.single()
        assertEquals("POST", seen.method)
        assertEquals("/service?wsdl=0", seen.path)
        assertEquals("<Envelope>ä</Envelope>", seen.body)
        assertEquals(listOf("\"urn:x\""), seen.headers["Soapaction"])
        assertEquals(listOf("LatherIt"), seen.headers["User-agent"])
    }

    /** Header, die Javas Client selbst setzt, würden den Request sonst scheitern lassen. */
    fun testGesperrteHeaderWerdenWeggelassen() {
        val response = request(
            "$base/",
            method = "POST",
            headers = listOf(
                listOf("Content-Length", "999"),
                listOf("Host", "anderswo"),
                listOf("Connection", "close"),
                listOf("User-Agent", "Eigener"),
                listOf("kaputt"),
            ),
            body = "abc",
        )
        assertEquals(200, response.status)
        val seen = received.single()
        assertEquals(listOf("3"), seen.headers["Content-length"])
        assertEquals(listOf("127.0.0.1:${server.address.port}"), seen.headers["Host"])
        assertEquals(listOf("Eigener"), seen.headers["User-agent"])
    }

    fun testZeichensatzAusDemHeader() {
        assertEquals("Grüße", request("$base/latin1").body)
    }

    fun testGzipWirdEntpackt() {
        val response = request("$base/gzip")
        assertEquals("entpackt äöü", response.body)
        assertEquals("entpackt äöü".toByteArray().size, response.size)
    }

    fun testUnbekannteKompressionBleibt() {
        assertEquals("abc", String(HttpBackend.decompress("abc".toByteArray(), "br")))
        assertEquals("abc", String(HttpBackend.decompress("abc".toByteArray(), "gzip")))
    }

    /** 401 ohne eigene Anmeldung ist eine normale Antwort - nicht „No credentials provided“ vom JDK-Client */
    fun testUnauthorizedOhneAnmeldung() {
        val response = request("$base/geschuetzt")
        assertEquals(401, response.status)
        assertTrue(response.headers.any { it[0].equals("WWW-Authenticate", true) && it[1] == "NTLM" })
    }

    fun testUmleitungWirdVerfolgt() {
        val response = request("$base/umleitung")
        assertEquals(200, response.status)
        assertEquals("angekommen", response.body)
    }

    fun testFehlerstatusIstKeinFehler() {
        val response = request("$base/fehlt")
        assertEquals(404, response.status)
        assertEquals("Not Found", response.statusText)
        assertEquals("nicht da", response.body)
    }

    fun testMehrfacheHeaderBleibenErhalten() {
        val cookies = request("$base/mehrfach").headers.filter { it[0].equals("set-cookie", true) }.map { it[1] }
        assertEquals(listOf("a=1", "b=2"), cookies)
    }

    fun testBasicAuth() {
        request("$base/", auth = AuthData("basic", username = "anna", password = "geheim"))
        assertEquals(listOf("Basic YW5uYTpnZWhlaW0="), received.single().headers["Authorization"])
    }

    fun testZeitueberschreitung() {
        val message = failure { request("$base/langsam", timeoutMs = 200) }
        assertTrue(message, message.startsWith("Zeitüberschreitung: GET $base/langsam"))
    }

    fun testVerbindungFehlgeschlagen() {
        val port = ServerSocket(0).use { it.localPort }
        val message = failure { request("http://127.0.0.1:$port/") }
        assertTrue(message, message.startsWith("Verbindung fehlgeschlagen: GET http://127.0.0.1:$port/"))
    }

    fun testUngueltigeRequests() {
        assertEquals("Ungültiger Request: Adresse fehlt", failure { HttpBackend.execute(HttpRequestData("GET", null, null, null, null, null)) })
        assertTrue(failure { request("http://a b/") }.startsWith("Ungültige Adresse http://a b/"))
        assertTrue(failure { request("$base/", method = "GE T") }.startsWith("Ungültiger Request"))
        assertEquals("Unbekannte Anmeldung: kerberos", failure { request("$base/", auth = AuthData("kerberos")) })
    }

    // ---------- loadResource ----------

    fun testLaedtDatei() {
        val file = Files.createTempFile("latherit", ".wsdl")
        try {
            Files.writeString(file, "<definitions/>")
            assertEquals("<definitions/>", HttpBackend.loadResource(file.toUri().toString()))
        } finally {
            Files.delete(file)
        }
    }

    fun testFehlendeDatei() {
        val file = Files.createTempFile("latherit", ".wsdl").also(Files::delete)
        assertTrue(failure { HttpBackend.loadResource(file.toUri().toString()) }.startsWith("$file: "))
    }

    fun testLaedtUeberHttp() {
        assertEquals("<ok/>", HttpBackend.loadResource("$base/service?wsdl"))
        assertEquals("$base/fehlt lieferte HTTP 404", failure { HttpBackend.loadResource("$base/fehlt") })
    }

    // ---------- decode ----------

    fun testDecode() {
        fun bytes(vararg values: Int) = values.map { it.toByte() }.toByteArray()
        assertEquals("ä", HttpBackend.decode(bytes(0xEF, 0xBB, 0xBF) + "ä".toByteArray(), "ISO-8859-1"))
        assertEquals("ä", HttpBackend.decode(bytes(0xFE, 0xFF) + "ä".toByteArray(Charsets.UTF_16BE), null))
        assertEquals("ä", HttpBackend.decode(bytes(0xFF, 0xFE) + "ä".toByteArray(Charsets.UTF_16LE), null))
        assertEquals("ä", HttpBackend.decode("ä".toByteArray(Charsets.ISO_8859_1), "iso-8859-1"))
        // Unbekannter Zeichensatz: UTF-8
        assertEquals("ä", HttpBackend.decode("ä".toByteArray(), "gibt-es-nicht"))
        assertEquals("", HttpBackend.decode(ByteArray(0), null))
    }
}
