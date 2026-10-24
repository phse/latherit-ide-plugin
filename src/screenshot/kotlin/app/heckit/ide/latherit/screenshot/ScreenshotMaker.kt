package app.heckit.ide.latherit.screenshot

import app.heckit.ide.latherit.LatherItView
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WindowManager
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * Erzeugt die Marketplace-Screenshots in der Sandbox-IDE (./gradlew runIde -Pscreenshots=...).
 * Zeichnet den Frame selbst in ein Bild (keine Bildschirmaufnahme-Rechte nötig) und legt die
 * Aufnahme der Oberfläche aus den Chromium-DevTools an die Stelle des Browsers.
 * Ist nur in Builds mit -Pscreenshots enthalten.
 */
object ScreenshotMaker {
    private const val SCALE = 2.0
    private val log = logger<ScreenshotMaker>()

    /** Zugriff auf die Stores der Oberfläche (Pinia). */
    private const val STORES = "const store = (id) => document.querySelector('#app').__vue_app__.config.globalProperties.\$pinia._s.get(id);"

    @JvmStatic
    fun start(project: Project, view: LatherItView, dir: String) {
        val out = File(dir).apply { mkdirs() }
        Thread({
            try {
                run(project, view, out)
            } catch (e: Throwable) {
                log.error("Screenshots failed", e)
            }
        }, "LatherIt screenshots").start()
    }

    private fun run(project: Project, view: LatherItView, out: File) {
        val frame = edt { WindowManager.getInstance().getFrame(project) } ?: error("no frame")
        edt {
            frame.extendedState = JFrame.NORMAL
            frame.setBounds(60, 40, 1440, 860)
            ToolWindowManager.getInstance(project).getToolWindow("Project")?.hide()
        }
        // Saubere Ausgangslage (keine Collections, Tabs o. Ä. aus früheren Sandbox-Sitzungen), nebeneinander,
        // dann neu laden, damit die Oberfläche es übernimmt
        waitFor(view, "return !!document.querySelector('#app')")
        js(view, "localStorage.clear(); localStorage.setItem('latherit.splitOrientation', JSON.stringify('side')); location.reload()")
        Thread.sleep(2000)
        waitFor(view, "$STORES return store('workspace').projects.length > 0")

        // Mock starten, damit die Requests eine Antwort bekommen
        js(view, "$STORES store('mocks').start('mock-shop')")
        Thread.sleep(800)

        select(view, "req-order-get", expand = listOf("rest-shop", "res-orders", "res-order", "m-order-get", "m-orders-get", "m-orders-post"))
        js(view, "document.querySelector('button.send').click()")
        Thread.sleep(1500)
        shot(frame, view, File(out, "01-request.png"))

        js(view, "document.querySelector('button[title^=\"Response unter\"]').click()")
        Thread.sleep(800)
        shot(frame, view, File(out, "02-stacked.png"))
        js(view, "document.querySelector('button[title^=\"Response neben\"]').click()")

        select(view, "req-orders-create")
        js(view, "document.querySelector('button.send').click()")
        Thread.sleep(1200)
        select(view, "mock-shop", expand = listOf("mock-shop"))
        js(view, "document.querySelector('.log tbody tr, .log li, .log button')?.click()")
        Thread.sleep(800)
        shot(frame, view, File(out, "03-mock.png"))

        select(view, "case-orders", expand = listOf("suite-smoke", "case-orders"))
        js(view, "$STORES store('tests').runCase('case-orders')")
        Thread.sleep(2500)
        shot(frame, view, File(out, "04-tests.png"))

        js(view, "$STORES store('mocks').stop('mock-shop')")
        log.warn("LatherIt: screenshots written to $out")
    }

    private fun select(view: LatherItView, id: String, expand: List<String> = emptyList()) {
        val ids = expand.joinToString(",") { "'$it'" }
        js(view, "$STORES const ws = store('workspace'); for (const id of [$ids]) ws.expanded.add(id); ws.select('$id')")
        Thread.sleep(900)
    }

    private fun js(view: LatherItView, script: String): String = view.devTools(
        "Runtime.evaluate",
        """{"expression":${Gson().toJson("(async () => { $script })()")},"awaitPromise":true,"returnByValue":true}""",
    ).get(30, TimeUnit.SECONDS)

    /** Wartet, bis `script` (mit `return`) true liefert. */
    private fun waitFor(view: LatherItView, script: String) {
        repeat(120) {
            val result = runCatching { js(view, script) }.getOrNull()
            if (result != null && result.contains("\"value\":true")) return
            Thread.sleep(500)
        }
        error("Timeout: $script")
    }

    /** Zeichnet den Frame und legt die Aufnahme der Oberfläche über den Browser-Bereich. */
    private fun shot(frame: JFrame, view: LatherItView, file: File) {
        val page = view.devTools("Page.captureScreenshot", """{"format":"png"}""").get(30, TimeUnit.SECONDS)
        val data = JsonParser.parseString(page).asJsonObject.get("data").asString
        val content = ImageIO.read(ByteArrayInputStream(Base64.getDecoder().decode(data)))
        edt {
            val root = frame.rootPane
            val image = BufferedImage((root.width * SCALE).toInt(), (root.height * SCALE).toInt(), BufferedImage.TYPE_INT_ARGB)
            val g = image.createGraphics()
            g.scale(SCALE, SCALE)
            root.paint(g)
            val browser = view.component
            val at = SwingUtilities.convertPoint(browser.parent, browser.location, root)
            g.drawImage(content, at.x, at.y, browser.width, browser.height, null)
            g.dispose()
            ImageIO.write(image, "png", file)
        }
    }

    private fun <T> edt(block: () -> T): T {
        var result: Result<T>? = null
        ApplicationManager.getApplication().invokeAndWait { result = runCatching(block) }
        return result!!.getOrThrow()
    }
}
