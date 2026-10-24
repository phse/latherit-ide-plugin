package app.heckit.ide.latherit

import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefCallback
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceHandler
import org.cef.handler.CefResourceHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import java.net.URI

/**
 * Liefert die Oberfläche (Vite-Build unter /latherit-web/ im Plugin) unter einer festen HTTPS-Adresse aus.
 * Ein echter Origin statt `file://` ist nötig, damit Web-Worker (Monaco, Skripte) und `crypto.randomUUID`
 * funktionieren. In die index.html wird vorher das Brücken-Skript eingesetzt.
 */
class WebResources(private val bridgeScript: () -> String) : CefRequestHandlerAdapter() {
    override fun getResourceRequestHandler(
        browser: CefBrowser?,
        frame: CefFrame?,
        request: CefRequest,
        isNavigation: Boolean,
        isDownload: Boolean,
        requestInitiator: String?,
        disableDefaultHandling: BoolRef?,
    ): CefResourceRequestHandler? {
        if (!request.url.startsWith(ORIGIN)) return null
        return object : CefResourceRequestHandlerAdapter() {
            override fun getResourceHandler(browser: CefBrowser?, frame: CefFrame?, request: CefRequest): CefResourceHandler =
                load(URI(request.url).path.trimStart('/').ifEmpty { "index.html" })
        }
    }

    private fun load(path: String): CefResourceHandler =
        resource(path).let { BytesResourceHandler(it.status, it.mimeType, it.bytes) }

    /** Inhalt zu einem Pfad unter /latherit-web/ (ohne führenden Schrägstrich). */
    internal fun resource(path: String): Resource {
        val stream = if (".." in path) null else javaClass.getResourceAsStream("/latherit-web/$path")
        if (stream == null) return Resource(404, "text/plain", "Not found: $path".toByteArray())
        var bytes = stream.use { it.readBytes() }
        if (path == "index.html") {
            val html = bytes.toString(Charsets.UTF_8)
            bytes = html.replaceFirst("<head>", "<head>\n<script>${bridgeScript()}</script>").toByteArray()
        }
        return Resource(200, mimeType(path), bytes)
    }

    internal class Resource(val status: Int, val mimeType: String, val bytes: ByteArray)

    private fun mimeType(path: String): String = when (path.substringAfterLast('.')) {
        "html" -> "text/html"
        "js", "mjs" -> "text/javascript"
        "css" -> "text/css"
        "json" -> "application/json"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "ttf" -> "font/ttf"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        else -> "application/octet-stream"
    }

    companion object {
        const val ORIGIN = "https://latherit.ide"
        const val START_URL = "$ORIGIN/index.html"
    }
}

/** Antwort aus dem Speicher (die Dateien sind klein genug, um sie ganz zu laden). */
private class BytesResourceHandler(
    private val status: Int,
    private val mimeType: String,
    private val bytes: ByteArray,
) : CefResourceHandlerAdapter() {
    private var offset = 0

    override fun processRequest(request: CefRequest, callback: CefCallback): Boolean {
        callback.Continue()
        return true
    }

    override fun getResponseHeaders(response: CefResponse, responseLength: IntRef, redirectUrl: StringRef) {
        response.status = status
        response.mimeType = mimeType
        // Neue Plugin-Version = neue Dateien; Chromium soll nichts Altes aus dem Cache nehmen.
        response.setHeaderByName("Cache-Control", "no-store", true)
        responseLength.set(bytes.size)
    }

    override fun readResponse(dataOut: ByteArray, bytesToRead: Int, bytesRead: IntRef, callback: CefCallback): Boolean {
        if (offset >= bytes.size) {
            bytesRead.set(0)
            return false
        }
        val count = minOf(bytesToRead, bytes.size - offset)
        System.arraycopy(bytes, offset, dataOut, 0, count)
        offset += count
        bytesRead.set(count)
        return true
    }
}
