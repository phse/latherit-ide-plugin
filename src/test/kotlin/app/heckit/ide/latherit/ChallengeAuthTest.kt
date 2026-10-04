package app.heckit.ide.latherit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

class ChallengeAuthTest {
    private fun bytes(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun md4() {
        assertEquals("31d6cfe0d16ae931b73c59d7e0c089c0", ChallengeAuth.hex(ChallengeAuth.md4(ByteArray(0))))
        assertEquals("a448017aaf21d8525fc10ae87aa6729d", ChallengeAuth.hex(ChallengeAuth.md4("abc".toByteArray())))
        val long = "12345678901234567890123456789012345678901234567890123456789012345678901234567890"
        assertEquals("e33b4ddc9c38f2199c3e7b164fcc0536", ChallengeAuth.hex(ChallengeAuth.md4(long.toByteArray())))
    }

    /** Beispiel aus MS-NLMP 4.2.4 (NTLMv2). */
    @Test
    fun ntlmv2NachSpezifikation() {
        val targetInfo = bytes("02000c0044006f006d00610069006e0001000c0053006500720076006500720000000000")
        val challenge = ByteArray(48 + targetInfo.size)
        "NTLMSSP\u0000".toByteArray().copyInto(challenge)
        challenge[8] = 2
        bytes("0123456789abcdef").copyInto(challenge, 24)
        challenge[40] = targetInfo.size.toByte()
        challenge[42] = targetInfo.size.toByte()
        challenge[44] = 48
        targetInfo.copyInto(challenge, 48)

        val message = ChallengeAuth.authenticateMessage(challenge, "User", "Domain", "Password", "COMPUTER", bytes("aaaaaaaaaaaaaaaa"), now = 0)
        assertEquals("NTLMSSP\u0000", String(message, 0, 8))
        assertEquals(3, message[8].toInt())
        // NT-Antwort: Länge bei 20, Offset bei 24; die ersten 16 Bytes sind NTProofStr
        val ntOffset = (message[24].toInt() and 0xFF) or ((message[25].toInt() and 0xFF) shl 8)
        assertEquals("68cd0ab851e51c96aabc927bebef6a1c", ChallengeAuth.hex(message.copyOfRange(ntOffset, ntOffset + 16)))
        // LMv2-Antwort (ohne Zeitstempel vom Server)
        val lmOffset = (message[16].toInt() and 0xFF) or ((message[17].toInt() and 0xFF) shl 8)
        assertEquals("86c35097ac9cec102554764a57cccc19aaaaaaaaaaaaaaaa", ChallengeAuth.hex(message.copyOfRange(lmOffset, lmOffset + 24)))
    }

    /** Beispiel aus RFC 2617, 3.5. */
    @Test
    fun digestNachRfc2617() {
        val challenge = "Digest realm=\"testrealm@host.com\", qop=\"auth,auth-int\", " +
            "nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", opaque=\"5ccc069c403ebaf9f0171e9517f40e41\""
        val header = ChallengeAuth.digestHeader(
            AuthData(kind = "digest", username = "Mufasa", password = "Circle Of Life"),
            "GET", "/dir/index.html", null, challenge, cnonce = "0a4f113b",
        )
        assertTrue(header, header.contains("response=\"6629fae49393a05397450978507c4ef1\""))
        assertTrue(header.contains("qop=auth, nc=00000001, cnonce=\"0a4f113b\""))
        assertTrue(header.contains("opaque=\"5ccc069c403ebaf9f0171e9517f40e41\""))
    }

    @Test
    fun parameterMitKommaInAnfuehrungszeichen() {
        val params = ChallengeAuth.parseParams(" realm=\"a, b\", qop=auth, nonce=\"x\\\"y\"")
        assertEquals(mapOf("realm" to "a, b", "qop" to "auth", "nonce" to "x\"y"), params)
    }

    @Test
    fun domaeneAusDemBenutzernamen() {
        assertEquals("FIRMA" to "peter", ChallengeAuth.splitDomain(AuthData(kind = "ntlm", username = "FIRMA\\peter")))
        assertEquals("X" to "peter", ChallengeAuth.splitDomain(AuthData(kind = "ntlm", username = "peter", domain = "X")))
    }

    // ---------- Gegen einen echten Server ----------

    /** Testserver: `answer(request, verbindungsnummer)` liefert die komplette HTTP-Antwort. */
    private fun server(answer: (String, Int) -> String): Pair<String, MutableList<Pair<Int, String>>> {
        val socket = ServerSocket(0)
        val seen = CopyOnWriteArrayList<Pair<Int, String>>()
        thread(isDaemon = true) {
            var connection = 0
            while (true) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                val number = ++connection
                thread(isDaemon = true) {
                    client.use {
                        val reader = BufferedReader(InputStreamReader(it.getInputStream(), Charsets.ISO_8859_1))
                        while (true) {
                            val head = generateSequence { reader.readLine() }.takeWhile { line -> line.isNotEmpty() }.joinToString("\n")
                            if (head.isEmpty()) break
                            val length = Regex("(?im)^content-length:\\s*(\\d+)").find(head)?.groupValues?.get(1)?.toInt() ?: 0
                            val body = CharArray(length).also { chars -> reader.read(chars) }
                            val request = head + "\n\n" + String(body)
                            seen += number to request
                            it.getOutputStream().write(answer(request, number).toByteArray())
                            it.getOutputStream().flush()
                        }
                    }
                }
            }
        }
        return "http://127.0.0.1:${socket.localPort}/svc?x=1" to seen
    }

    private fun header(request: String, name: String): String? =
        request.lines().firstNotNullOfOrNull { line ->
            line.split(':', limit = 2).takeIf { it.size == 2 && it[0].equals(name, ignoreCase = true) }?.get(1)?.trim()
        }

    private fun sender(client: HttpClient, url: String): (String?) -> HttpResponse<ByteArray> = { authorization ->
        val builder = HttpRequest.newBuilder(URI(url)).POST(HttpRequest.BodyPublishers.ofString("<x/>"))
        authorization?.let { builder.header("Authorization", it) }
        client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
    }

    private fun newClient() = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()

    @Test
    fun digestBeantwortetDieChallenge() {
        val (url, seen) = server { request, _ ->
            if (header(request, "authorization") == null) {
                "HTTP/1.1 401 Unauthorized\r\nWWW-Authenticate: Basic realm=\"x\"\r\nWWW-Authenticate: Digest realm=\"test\", qop=\"auth\", nonce=\"abc123\", algorithm=MD5\r\nContent-Length: 0\r\n\r\n"
            } else "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok"
        }
        newClient().use { client ->
            val auth = AuthData(kind = "digest", username = "peter", password = "geheim")
            val response = ChallengeAuth.digest(auth, "POST", URI(url), "<x/>", sender(client, url))
            assertEquals(200, response.statusCode())
        }
        val authorization = header(seen[1].second, "authorization")!!
        assertTrue(authorization.startsWith("Digest "))
        assertTrue(authorization.contains("username=\"peter\""))
        assertTrue(authorization.contains("uri=\"/svc?x=1\""))
        assertTrue(authorization.contains("nonce=\"abc123\""))
    }

    @Test
    fun ntlmHandshakeAufEinerVerbindung() {
        val challenge = ByteArray(48)
        "NTLMSSP\u0000".toByteArray().copyInto(challenge)
        challenge[8] = 2
        challenge[44] = 48
        val challengeB64 = Base64.getEncoder().encodeToString(challenge)
        val (url, seen) = server { request, _ ->
            val message = header(request, "authorization")?.removePrefix("NTLM ")?.let { Base64.getDecoder().decode(it) }
            when (message?.getOrNull(8)?.toInt()) {
                1 -> "HTTP/1.1 401 Unauthorized\r\nWWW-Authenticate: Negotiate, NTLM $challengeB64\r\nContent-Length: 4\r\n\r\nnein"
                3 -> "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok"
                else -> "HTTP/1.1 400 Bad Request\r\nContent-Length: 0\r\n\r\n"
            }
        }
        newClient().use { client ->
            val response = ChallengeAuth.ntlm(AuthData(kind = "ntlm", username = "FIRMA\\peter", password = "geheim"), sender(client, url))
            assertEquals(200, response.statusCode())
        }
        // Beide Runden auf derselben Verbindung, zweite Nachricht ist ein NTLM-Authenticate (Typ 3)
        assertEquals(seen[0].first, seen[1].first)
        val third = Base64.getDecoder().decode(header(seen[1].second, "authorization")!!.removePrefix("NTLM "))
        assertEquals(3, third[8].toInt())
    }
}
