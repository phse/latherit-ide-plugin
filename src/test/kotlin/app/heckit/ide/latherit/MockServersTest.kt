package app.heckit.ide.latherit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

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
}
