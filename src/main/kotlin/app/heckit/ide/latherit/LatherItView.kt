package app.heckit.ide.latherit

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.browser.CefBrowser
import org.cef.handler.CefLifeSpanHandlerAdapter
import java.awt.Toolkit
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import java.util.concurrent.CompletableFuture
import javax.swing.KeyStroke

/**
 * Die LatherIt-Oberfläche in einem eingebetteten Chromium (JCEF) und die Brücke dorthin:
 * Die Oberfläche ruft `window.__latheritIde.call(befehl, args)` auf, das Plugin führt den Befehl
 * aus ([HostCommands]) und meldet das Ergebnis per `_settle` zurück.
 */
class LatherItView(private val project: Project, private val file: VirtualFile, parent: Disposable) : Disposable {
    private val browser = JBCefBrowser.createBuilder().build()
    private val query = JBCefJSQuery.create(browser as JBCefBrowserBase)
    private val mocks = MockServers(::emit)
    private val commands = HostCommands(project, file, mocks)
    private val gson = Gson()

    /** Die Datei wurde außerhalb der Oberfläche geändert (Text-Tab, Git, …), während sie nicht sichtbar war. */
    private var changedElsewhere = false

    val component: JComponent get() = browser.component

    init {
        Disposer.register(parent, this)
        Disposer.register(this, browser)
        // Nach dem Browser registriert = vor ihm freigegeben
        Disposer.register(this, query)
        // Mocks enden mit dem Tab - vor dem Browser, damit kein Request mehr an ihn geht
        Disposer.register(this) { mocks.stopAll() }

        query.addHandler { request ->
            onCall(request)
            JBCefJSQuery.Response("")
        }
        browser.jbCefClient.addRequestHandler(WebResources(::bridgeScript), browser.cefBrowser)
        browser.jbCefClient.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
            override fun onAfterCreated(cefBrowser: CefBrowser?) = applyTheme()
        }, browser.cefBrowser)
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(LafManagerListener.TOPIC, LafManagerListener { applyTheme() })
        installShortcuts()
        watchDocument()
        browser.loadURL(WebResources.START_URL)
        startScreenshots()
    }

    /** Ruft eine Methode der Chromium-DevTools auf (Ergebnis als JSON). */
    internal fun devTools(method: String, params: String): CompletableFuture<String> =
        browser.cefBrowser.devToolsClient.executeDevToolsMethod(method, params)

    /** Nur in Builds mit -Pscreenshots (siehe README): Marketplace-Screenshots erzeugen. */
    private fun startScreenshots() {
        val dir = System.getProperty("latherit.screenshots") ?: return
        if (screenshotsStarted) return
        screenshotsStarted = true
        runCatching {
            Class.forName("app.heckit.ide.latherit.screenshot.ScreenshotMaker")
                .getMethod("start", Project::class.java, LatherItView::class.java, String::class.java)
                .invoke(null, project, this, dir)
        }.onFailure { thisLogger().warn("Screenshots nicht verfügbar", it) }
    }

    /** Wird beim Wechsel auf den Tab aufgerufen: Änderungen von außen übernehmen. */
    fun reloadIfChangedElsewhere() {
        if (!changedElsewhere) return
        changedElsewhere = false
        browser.cefBrowser.reload()
    }

    /** Ereignis an die Oberfläche (siehe `onIdeEvent` in src/lib/ide.ts). */
    fun emit(event: String, payload: Any?) {
        browser.cefBrowser.executeJavaScript(
            "window.__latheritIde && window.__latheritIde._emit(${gson.toJson(event)}, ${gson.toJson(payload)})",
            WebResources.ORIGIN,
            0,
        )
    }

    private fun onCall(request: String) {
        val message = runCatching { JsonParser.parseString(request).asJsonObject }.getOrNull() ?: return
        val id = message.get("id")?.asLong ?: return
        val command = message.get("command")?.asString ?: return
        val args = message.getAsJsonObject("args") ?: JsonObject()
        commands.run(command, args).whenComplete { result, error ->
            val (ok, value) = if (error == null) true to result else false to HostCommands.describe(error)
            browser.cefBrowser.executeJavaScript(
                "window.__latheritIde._settle($id, $ok, ${gson.toJson(value)})",
                WebResources.ORIGIN,
                0,
            )
        }
    }

    /**
     * Läuft vor allen Skripten der Seite. `cefQuery_…` ist die Funktion, die JCEF für [query] bereitstellt;
     * Antworten kommen nicht über deren Rückruf, sondern später per `_settle` (Befehle laufen asynchron).
     */
    private fun bridgeScript(): String = """
        (() => {
          const pending = new Map()
          const listeners = new Map()
          let nextId = 1
          const send = (request) => { ${query.inject("request")} }
          window.__latheritIde = {
            projectPath: ${gson.toJson(file.path)},
            importFrom: ${gson.toJson(consumeImport())},
            call(command, args) {
              return new Promise((resolve, reject) => {
                const id = nextId++
                pending.set(id, { resolve, reject })
                send(JSON.stringify({ id, command, args: args || {} }))
              })
            },
            on(event, listener) {
              if (!listeners.has(event)) listeners.set(event, new Set())
              listeners.get(event).add(listener)
              return () => listeners.get(event).delete(listener)
            },
            _settle(id, ok, value) {
              const entry = pending.get(id)
              if (!entry) return
              pending.delete(id)
              if (ok) entry.resolve(value)
              else entry.reject(new Error(value))
            },
            _emit(event, payload) {
              for (const listener of listeners.get(event) || []) listener(payload)
            },
          }
        })()
    """.trimIndent()

    /** Nur beim ersten Laden importieren - ein späteres Neuladen öffnet die dann gefüllte Datei. */
    private fun consumeImport(): String? =
        file.getUserData(LatherItProjects.IMPORT_FROM)?.also { file.putUserData(LatherItProjects.IMPORT_FROM, null) }

    /** Hell/Dunkel wie die IDE: `prefers-color-scheme` der Seite wird per DevTools vorgegeben. */
    private fun applyTheme() {
        val scheme = if (JBColor.isBright()) "light" else "dark"
        runCatching {
            browser.cefBrowser.devToolsClient.executeDevToolsMethod(
                "Emulation.setEmulatedMedia",
                """{"features":[{"name":"prefers-color-scheme","value":"$scheme"}]}""",
            )
        }.onFailure { thisLogger().warn("Farbschema konnte nicht gesetzt werden", it) }
    }

    /**
     * Kürzel, die die IDE sonst selbst ausführen würde (⌘S, ⌘B, ⌘1, …), gehen an die Oberfläche.
     * Die Namen entsprechen `ideShortcuts` in src/App.vue.
     */
    private fun installShortcuts() {
        val mod = Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx
        val shift = InputEvent.SHIFT_DOWN_MASK
        val shortcuts = listOf(
            "send" to KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, mod),
            "save" to KeyStroke.getKeyStroke(KeyEvent.VK_S, mod),
            "saveAs" to KeyStroke.getKeyStroke(KeyEvent.VK_S, mod or shift),
            "importWsdl" to KeyStroke.getKeyStroke(KeyEvent.VK_I, mod),
            "newRest" to KeyStroke.getKeyStroke(KeyEvent.VK_N, mod or shift),
            "board" to KeyStroke.getKeyStroke(KeyEvent.VK_B, mod),
            "filter" to KeyStroke.getKeyStroke(KeyEvent.VK_F, mod or shift),
            "focusTree" to KeyStroke.getKeyStroke(KeyEvent.VK_1, mod),
            "focusEditor" to KeyStroke.getKeyStroke(KeyEvent.VK_2, mod),
        )
        for ((name, keyStroke) in shortcuts) {
            DumbAwareAction.create { emit("shortcut", name) }
                .registerCustomShortcutSet(CustomShortcutSet(keyStroke), browser.component, this)
        }
    }

    /** Änderungen am Dokument, die nicht von der Oberfläche selbst kommen, laden die Oberfläche neu. */
    private fun watchDocument() {
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return
        document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (HostCommands.isOwnWrite(document)) return
                if (component.isShowing) browser.cefBrowser.reload() else changedElsewhere = true
            }
        }, this)
    }

    override fun dispose() = Unit

    private companion object {
        var screenshotsStarted = false
    }
}
