package app.heckit.ide.latherit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.ServerSocket
import java.net.http.HttpResponse
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MockServersTest {
    /**
     * Voller Durchlauf: Request an den Mock → Ereignis an die „Oberfläche“ → Antwort über `respond` →
     * Client bekommt Status, Header und Body.
     */
    @Test
    fun beantwortetRequestsUeberDieOberflaeche() {
        lateinit var mocks: MockServers
        // Die „Oberfläche“: antwortet auf jedes Ereignis mit einem Echo des Requests
        mocks = MockServers { event, payload ->
            assertEquals("mock-request", event)
            val request = payload as MockRequestEvent
            assertEquals("m1", request.mockId)
            val body = "${request.method} ${request.path}?${request.query} ${request.body}"
            mocks.respond(request.requestId, MockAnswer(201, listOf(listOf("X-Mock", "ja"), listOf("Content-Length", "999")), body))
        }

        val port = mocks.start("m1", 0, false)
        assertTrue(runCatching { mocks.start("m1", 0, false) }.isFailure)
        assertEquals(mapOf("m1" to port), mocks.running())

        val client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
        val response = client.send(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/orders?x=1")).POST(HttpRequest.BodyPublishers.ofString("<a/>")).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(201, response.statusCode())
        assertEquals("ja", response.headers().firstValue("x-mock").orElse(null))
        assertEquals("POST /api/orders?x=1 <a/>", response.body())

        mocks.stop("m1")
        assertTrue(mocks.running().isEmpty())
        val fresh = HttpClient.newHttpClient()
        val error = runCatching { fresh.send(HttpRequest.newBuilder(URI("http://127.0.0.1:$port/")).build(), HttpResponse.BodyHandlers.ofString()) }
        assertTrue(error.exceptionOrNull() is ConnectException)
    }

    private val client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()

    private fun get(port: Int, path: String = "/", method: String = "GET", body: String? = null): HttpResponse<String> {
        val publisher = body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody()
        return client.send(
            HttpRequest.newBuilder(URI("http://127.0.0.1:$port$path")).method(method, publisher).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }

    /** Die Oberfläche hängt (z. B. beim Neuladen): Der Client bekommt 504 statt ewig zu warten. */
    @Test
    fun ohneAntwortDerOberflaeche504() {
        val mocks = MockServers { _, _ -> }
        mocks.responseTimeoutMs = 200
        try {
            val port = mocks.start("m1", 0, false)
            val response = get(port)
            assertEquals(504, response.statusCode())
            assertEquals("LatherIt hat nicht rechtzeitig geantwortet", response.body())
        } finally {
            mocks.stopAll()
        }
    }

    /** Mehrere gleichzeitige Requests: Jede Antwort landet beim richtigen Client, auch in anderer Reihenfolge. */
    @Test
    fun gleichzeitigeRequestsBekommenIhreEigeneAntwort() {
        val events = CopyOnWriteArrayList<MockRequestEvent>()
        val allThere = CountDownLatch(5)
        val mocks = MockServers { _, payload ->
            events += payload as MockRequestEvent
            allThere.countDown()
        }
        try {
            val port = mocks.start("m1", 0, false)
            val futures = (1..5).map { i ->
                client.sendAsync(
                    HttpRequest.newBuilder(URI("http://127.0.0.1:$port/r$i")).build(),
                    HttpResponse.BodyHandlers.ofString(),
                )
            }
            assertTrue(allThere.await(10, TimeUnit.SECONDS))
            for (event in events.reversed()) mocks.respond(event.requestId, MockAnswer(200, null, "Antwort ${event.path}"))
            for ((index, future) in futures.withIndex()) {
                assertEquals("Antwort /r${index + 1}", future.get(10, TimeUnit.SECONDS).body())
            }
            assertEquals(5, events.map { it.requestId }.toSet().size)
        } finally {
            mocks.stopAll()
        }
    }

    /** Das Ereignis enthält alles, was die Oberfläche für das Dispatching braucht. */
    @Test
    fun ereignisEnthaeltRequestDaten() {
        lateinit var mocks: MockServers
        var seen: MockRequestEvent? = null
        mocks = MockServers { _, payload ->
            seen = payload as MockRequestEvent
            mocks.respond(seen!!.requestId, MockAnswer(null, null, null))
        }
        try {
            val port = mocks.start("m1", 0, false)
            val response = client.send(
                HttpRequest.newBuilder(URI("http://127.0.0.1:$port/pfad%20mit/leer?a=1&b=%C3%A4"))
                    .header("SOAPAction", "\"urn:bestellen\"")
                    .POST(HttpRequest.BodyPublishers.ofString("Grüße"))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            // Ohne Angaben: 200 mit leerem Body
            assertEquals(200, response.statusCode())
            assertEquals("", response.body())
            val event = seen!!
            assertEquals("POST", event.method)
            assertEquals("/pfad%20mit/leer", event.path)
            assertEquals("a=1&b=%C3%A4", event.query)
            assertEquals("Grüße", event.body)
            assertTrue(event.headers.any { it[0].equals("SOAPAction", true) && it[1] == "\"urn:bestellen\"" })
            assertTrue(event.remote.startsWith("127.0.0.1:"))
        } finally {
            mocks.stopAll()
        }
    }

    /** HEAD, 204 und 304 haben keinen Body - auch wenn die Oberfläche einen mitschickt. */
    @Test
    fun antwortenOhneBody() {
        lateinit var mocks: MockServers
        mocks = MockServers { _, payload ->
            val request = payload as MockRequestEvent
            val status = if (request.path == "/leer") 204 else 200
            mocks.respond(request.requestId, MockAnswer(status, listOf(listOf("Transfer-Encoding", "chunked")), "Inhalt"))
        }
        try {
            val port = mocks.start("m1", 0, false)
            val head = get(port, "/", "HEAD")
            assertEquals(200, head.statusCode())
            assertEquals("", head.body())
            val noContent = get(port, "/leer")
            assertEquals(204, noContent.statusCode())
            assertEquals("", noContent.body())
            // Die Länge bestimmt der Server, nicht die Oberfläche
            val normal = get(port, "/")
            assertEquals("Inhalt", normal.body())
            assertEquals("6", normal.headers().firstValue("content-length").orElse(null))
        } finally {
            mocks.stopAll()
        }
    }

    @Test
    fun belegterPort() {
        ServerSocket(0, 0, java.net.InetAddress.getByName("127.0.0.1")).use { blocker ->
            val mocks = MockServers { _, _ -> }
            val error = runCatching { mocks.start("m1", blocker.localPort, false) }.exceptionOrNull()
            assertTrue(error is HostException)
            assertTrue(error!!.message!!.startsWith("Port ${blocker.localPort} lässt sich nicht öffnen"))
            assertTrue(mocks.running().isEmpty())
        }
    }

    /** Beim Schließen des Tabs: Wartende Clients hängen nicht bis zur Zeitüberschreitung, der Port wird frei. */
    @Test
    fun stopAllBeendetWartendeRequestsUndGibtPortFrei() {
        val arrived = CountDownLatch(1)
        val mocks = MockServers { _, _ -> arrived.countDown() }
        val port = mocks.start("m1", 0, false)
        mocks.start("m2", 0, false)
        val waiting = client.sendAsync(HttpRequest.newBuilder(URI("http://127.0.0.1:$port/")).build(), HttpResponse.BodyHandlers.ofString())
        assertTrue(arrived.await(10, TimeUnit.SECONDS))

        val started = System.nanoTime()
        mocks.stopAll()
        assertTrue(mocks.running().isEmpty())
        // Antwort (504) oder abgebrochene Verbindung - aber schnell
        runCatching { waiting.get(10, TimeUnit.SECONDS) }
        assertTrue(waiting.isDone)
        assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started) < 5)

        // Derselbe Port lässt sich sofort wieder belegen
        val again = MockServers { _, _ -> }
        try {
            assertEquals(port, again.start("m1", port, false))
        } finally {
            again.stopAll()
        }
    }

    /** Späte oder doppelte Antworten der Oberfläche (nach Zeitüberschreitung) werden ignoriert. */
    @Test
    fun unbekannteRequestIdWirdIgnoriert() {
        val mocks = MockServers { _, _ -> }
        mocks.respond(4711, MockAnswer(200, null, "zu spät"))
        mocks.stop("gibt-es-nicht")
    }
}
