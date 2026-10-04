package app.heckit.ide.latherit

import java.net.URI
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HTTP-Anmeldung (Gegenstück zu src-tauri/src/auth.rs): Basic und Bearer sind ein fester Header (preemptiv),
 * Digest und NTLM sind Challenge/Response - erst ohne bzw. mit Aushandlungs-Nachricht senden, die Challenge
 * aus `WWW-Authenticate` beantworten und den Request mit der Antwort wiederholen.
 *
 * `send(authorization)` schickt jeweils einen Versuch, optional mit `Authorization`-Header.
 */
object ChallengeAuth {
    private val random = SecureRandom()

    /** `Authorization`-Wert für Basic und Bearer. */
    fun simpleHeader(auth: AuthData): String =
        if (auth.kind == "bearer") "Bearer ${auth.token.orEmpty()}"
        else "Basic " + Base64.getEncoder().encodeToString("${auth.username.orEmpty()}:${auth.password.orEmpty()}".toByteArray())

    // ---------- Digest (RFC 7616) ----------

    /** Ohne Anmeldung senden; kommt 401 mit Digest-Challenge, beantworten und wiederholen. */
    fun digest(auth: AuthData, method: String, uri: URI, body: String?, send: (String?) -> HttpResponse<ByteArray>): HttpResponse<ByteArray> {
        val first = send(null)
        if (first.statusCode() != 401) return first
        val header = first.headers().allValues("WWW-Authenticate")
            .firstOrNull { it.trimStart().startsWith("digest", ignoreCase = true) } ?: return first
        val path = uri.rawPath.orEmpty().ifEmpty { "/" } + (uri.rawQuery?.let { "?$it" } ?: "")
        return send(digestHeader(auth, method, path, body, header, cnonce()))
    }

    internal fun digestHeader(auth: AuthData, method: String, uri: String, body: String?, challenge: String, cnonce: String): String {
        val params = parseParams(challenge.trimStart().substring("digest".length))
        val realm = params["realm"].orEmpty()
        val nonce = params["nonce"] ?: throw HostException("Digest-Challenge ohne nonce")
        val algorithm = params["algorithm"] ?: "MD5"
        val session = algorithm.endsWith("-sess", ignoreCase = true)
        val digestName = when (algorithm.uppercase().removeSuffix("-SESS")) {
            "MD5" -> "MD5"
            "SHA-256" -> "SHA-256"
            "SHA-512-256" -> "SHA-512/256"
            else -> throw HostException("Digest-Algorithmus $algorithm wird nicht unterstützt")
        }
        fun h(text: String) = hex(MessageDigest.getInstance(digestName).digest(text.toByteArray(Charsets.UTF_8)))

        val offered = params["qop"]?.split(',')?.map { it.trim().lowercase() }.orEmpty()
        val qop = when {
            "auth" in offered -> "auth"
            "auth-int" in offered -> "auth-int"
            offered.isEmpty() -> null
            else -> throw HostException("Digest-qop ${params["qop"]} wird nicht unterstützt")
        }
        val nc = "00000001"
        var ha1 = h("${auth.username.orEmpty()}:$realm:${auth.password.orEmpty()}")
        if (session) ha1 = h("$ha1:$nonce:$cnonce")
        val ha2 = if (qop == "auth-int") h("$method:$uri:${h(body.orEmpty())}") else h("$method:$uri")
        val response = if (qop != null) h("$ha1:$nonce:$nc:$cnonce:$qop:$ha2") else h("$ha1:$nonce:$ha2")

        return buildString {
            append("Digest username=\"${quote(auth.username.orEmpty())}\", realm=\"${quote(realm)}\", nonce=\"${quote(nonce)}\"")
            append(", uri=\"${quote(uri)}\", algorithm=$algorithm, response=\"$response\"")
            if (qop != null) append(", qop=$qop, nc=$nc, cnonce=\"$cnonce\"")
            params["opaque"]?.let { append(", opaque=\"${quote(it)}\"") }
        }
    }

    /** `name=wert, name="wert, mit Komma"` → Map (Namen klein). */
    internal fun parseParams(text: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var i = 0
        while (i < text.length) {
            while (i < text.length && (text[i] == ',' || text[i].isWhitespace())) i++
            val nameStart = i
            while (i < text.length && text[i] != '=' && text[i] != ',') i++
            val name = text.substring(nameStart, i).trim().lowercase()
            if (i >= text.length || text[i] != '=') continue
            i++
            val value = StringBuilder()
            if (i < text.length && text[i] == '"') {
                i++
                while (i < text.length && text[i] != '"') {
                    if (text[i] == '\\' && i + 1 < text.length) i++
                    value.append(text[i++])
                }
                i++
            } else {
                while (i < text.length && text[i] != ',') value.append(text[i++])
            }
            if (name.isNotEmpty()) result[name] = value.toString().trim()
        }
        return result
    }

    private fun quote(text: String) = text.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun cnonce(): String = hex(ByteArray(16).also(random::nextBytes))

    // ---------- NTLM (v2, MS-NLMP) ----------

    private const val NEGOTIATE_UNICODE = 0x00000001
    private const val REQUEST_TARGET = 0x00000004
    private const val NEGOTIATE_NTLM = 0x00000200
    private const val NEGOTIATE_ALWAYS_SIGN = 0x00008000
    private const val NEGOTIATE_EXTENDED_SESSION_SECURITY = 0x00080000
    private const val AV_EOL = 0
    private const val AV_TIMESTAMP = 7

    /**
     * Negotiate senden, Challenge beantworten (NTLMv2), Request mit Authenticate wiederholen. NTLM authentifiziert
     * eine Verbindung - `send` muss deshalb beide Runden über dieselbe Verbindung schicken.
     */
    fun ntlm(auth: AuthData, send: (String?) -> HttpResponse<ByteArray>): HttpResponse<ByteArray> {
        val first = send("NTLM " + Base64.getEncoder().encodeToString(negotiateMessage()))
        if (first.statusCode() != 401) return first
        val header = challengeHeader(first.headers().allValues("WWW-Authenticate"), "NTLM ")
            ?: throw HostException("Der Server bietet kein NTLM an (WWW-Authenticate enthält keine NTLM-Challenge)")
        val challenge = try {
            Base64.getDecoder().decode(header.substring(5).trim())
        } catch (_: IllegalArgumentException) {
            throw HostException("NTLM-Challenge nicht lesbar")
        }
        val (domain, user) = splitDomain(auth)
        val workstation = auth.workstation?.takeIf { it.isNotEmpty() } ?: "LATHERIT"
        val authenticate = authenticateMessage(challenge, user, domain, auth.password.orEmpty(), workstation, clientChallenge = ByteArray(8).also(random::nextBytes), now = null)
        return send("NTLM " + Base64.getEncoder().encodeToString(authenticate))
    }

    /** Challenge eines Schemas aus den `WWW-Authenticate`-Headern (auch mehrere in einem Header, durch Komma getrennt). */
    private fun challengeHeader(values: List<String>, scheme: String): String? =
        values.flatMap { it.split(", ") + it }.firstOrNull { it.startsWith(scheme, ignoreCase = true) }

    /** `DOMÄNE\benutzer` zerlegen, falls keine Domäne angegeben ist. */
    internal fun splitDomain(auth: AuthData): Pair<String, String> {
        val username = auth.username.orEmpty()
        auth.domain?.takeIf { it.isNotEmpty() }?.let { return it to username }
        if ('\\' in username) return username.substringBefore('\\') to username.substringAfter('\\')
        return "" to username
    }

    private fun negotiateMessage(): ByteArray {
        val flags = NEGOTIATE_UNICODE or REQUEST_TARGET or NEGOTIATE_NTLM or NEGOTIATE_ALWAYS_SIGN or NEGOTIATE_EXTENDED_SESSION_SECURITY
        // Signatur, Typ, Flags, Domäne und Rechnername leer (Länge 0, Offset 32)
        return ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
            .put(SIGNATURE).putInt(1).putInt(flags)
            .putShort(0).putShort(0).putInt(32)
            .putShort(0).putShort(0).putInt(32)
            .array()
    }

    /** NTLMv2-Antwort auf eine Challenge (Typ 2) als Authenticate-Nachricht (Typ 3). `now` nur für Tests. */
    internal fun authenticateMessage(
        challenge: ByteArray,
        user: String,
        domain: String,
        password: String,
        workstation: String,
        clientChallenge: ByteArray,
        now: Long?,
    ): ByteArray {
        val input = ByteBuffer.wrap(challenge).order(ByteOrder.LITTLE_ENDIAN)
        if (challenge.size < 32 || !challenge.copyOfRange(0, 8).contentEquals(SIGNATURE) || input.getInt(8) != 2) {
            throw HostException("NTLM-Challenge nicht lesbar")
        }
        val serverChallenge = challenge.copyOfRange(24, 32)
        val targetInfo = if (challenge.size >= 48) {
            val length = input.getShort(40).toInt() and 0xFFFF
            val offset = input.getInt(44)
            if (offset < 0 || offset + length > challenge.size) throw HostException("NTLM-Challenge nicht lesbar")
            challenge.copyOfRange(offset, offset + length)
        } else ByteArray(0)

        // Zeitstempel des Servers verwenden, falls vorhanden (dann ohne LMv2-Antwort)
        val serverTime = avPair(targetInfo, AV_TIMESTAMP)
        val timestamp = serverTime ?: ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(now ?: ((System.currentTimeMillis() + 11_644_473_600_000L) * 10_000)).array()

        val ntlmv2Hash = hmacMd5(md4(password.toByteArray(Charsets.UTF_16LE)), (user.uppercase() + domain).toByteArray(Charsets.UTF_16LE))
        val blob = ByteBuffer.allocate(28 + targetInfo.size + 4)
            .put(byteArrayOf(1, 1, 0, 0, 0, 0, 0, 0)).put(timestamp).put(clientChallenge)
            .put(ByteArray(4)).put(targetInfo).put(ByteArray(4))
            .array()
        val ntProof = hmacMd5(ntlmv2Hash, serverChallenge + blob)
        val ntResponse = ntProof + blob
        val lmResponse = if (serverTime != null) ByteArray(24) else hmacMd5(ntlmv2Hash, serverChallenge + clientChallenge) + clientChallenge

        val fields = listOf(
            lmResponse,
            ntResponse,
            domain.toByteArray(Charsets.UTF_16LE),
            user.toByteArray(Charsets.UTF_16LE),
            workstation.toByteArray(Charsets.UTF_16LE),
            ByteArray(0), // Session-Key
        )
        val headerSize = 64
        val out = ByteBuffer.allocate(headerSize + fields.sumOf { it.size }).order(ByteOrder.LITTLE_ENDIAN)
        out.put(SIGNATURE).putInt(3)
        var offset = headerSize
        for (field in fields) {
            out.putShort(field.size.toShort()).putShort(field.size.toShort()).putInt(offset)
            offset += field.size
        }
        out.putInt(NEGOTIATE_UNICODE or NEGOTIATE_NTLM or NEGOTIATE_EXTENDED_SESSION_SECURITY)
        fields.forEach { out.put(it) }
        return out.array()
    }

    private fun avPair(targetInfo: ByteArray, id: Int): ByteArray? {
        val buffer = ByteBuffer.wrap(targetInfo).order(ByteOrder.LITTLE_ENDIAN)
        while (buffer.remaining() >= 4) {
            val avId = buffer.short.toInt() and 0xFFFF
            val length = buffer.short.toInt() and 0xFFFF
            if (avId == AV_EOL || length > buffer.remaining()) return null
            val value = ByteArray(length).also(buffer::get)
            if (avId == id) return value
        }
        return null
    }

    private val SIGNATURE = "NTLMSSP\u0000".toByteArray(Charsets.US_ASCII)

    internal fun hmacMd5(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacMD5").apply { init(SecretKeySpec(key, "HmacMD5")) }.doFinal(data)

    /** MD4 (RFC 1320) für den NT-Hash - Java bietet es nicht als öffentlichen Algorithmus an. */
    internal fun md4(input: ByteArray): ByteArray {
        val bitLength = input.size.toLong() * 8
        val padded = ByteArray(((input.size + 8) / 64 + 1) * 64)
        input.copyInto(padded)
        padded[input.size] = 0x80.toByte()
        ByteBuffer.wrap(padded, padded.size - 8, 8).order(ByteOrder.LITTLE_ENDIAN).putLong(bitLength)

        var a = 0x67452301
        var b = 0xefcdab89.toInt()
        var c = 0x98badcfe.toInt()
        var d = 0x10325476
        val x = IntArray(16)
        val words = ByteBuffer.wrap(padded).order(ByteOrder.LITTLE_ENDIAN)
        for (block in 0 until padded.size / 64) {
            for (i in 0 until 16) x[i] = words.getInt(block * 64 + i * 4)
            val (aa, bb, cc, dd) = listOf(a, b, c, d)
            fun f(x: Int, y: Int, z: Int) = (x and y) or (x.inv() and z)
            fun g(x: Int, y: Int, z: Int) = (x and y) or (x and z) or (y and z)
            fun h(x: Int, y: Int, z: Int) = x xor y xor z
            fun r1(a: Int, b: Int, c: Int, d: Int, k: Int, s: Int) = (a + f(b, c, d) + x[k]).rotateLeft(s)
            fun r2(a: Int, b: Int, c: Int, d: Int, k: Int, s: Int) = (a + g(b, c, d) + x[k] + 0x5a827999).rotateLeft(s)
            fun r3(a: Int, b: Int, c: Int, d: Int, k: Int, s: Int) = (a + h(b, c, d) + x[k] + 0x6ed9eba1).rotateLeft(s)
            for (k in intArrayOf(0, 4, 8, 12)) {
                a = r1(a, b, c, d, k, 3); d = r1(d, a, b, c, k + 1, 7); c = r1(c, d, a, b, k + 2, 11); b = r1(b, c, d, a, k + 3, 19)
            }
            for (k in intArrayOf(0, 1, 2, 3)) {
                a = r2(a, b, c, d, k, 3); d = r2(d, a, b, c, k + 4, 5); c = r2(c, d, a, b, k + 8, 9); b = r2(b, c, d, a, k + 12, 13)
            }
            for (k in intArrayOf(0, 2, 1, 3)) {
                a = r3(a, b, c, d, k, 3); d = r3(d, a, b, c, k + 8, 9); c = r3(c, d, a, b, k + 4, 11); b = r3(b, c, d, a, k + 12, 15)
            }
            a += aa; b += bb; c += cc; d += dd
        }
        return ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(a).putInt(b).putInt(c).putInt(d).array()
    }

    internal fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
