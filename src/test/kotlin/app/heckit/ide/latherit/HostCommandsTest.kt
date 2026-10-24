package app.heckit.ide.latherit

import com.google.gson.JsonObject
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

/**
 * Befehle der Oberfläche mit echter IDE-Umgebung: Dateien über Dokumente und VFS, Schutz vor dem Überschreiben
 * fremder Änderungen, Mocks. Die Tests laufen auf dem EDT - [await] arbeitet währenddessen dessen Ereignisse ab,
 * weil `write_text_file` per `invokeAndWait` auf den EDT wechselt.
 */
class HostCommandsTest : BasePlatformTestCase() {
    private lateinit var dir: Path
    private lateinit var projectFile: VirtualFile
    private val mocks = MockServers({ _, _ -> })
    private var reloads = 0
    private lateinit var commands: HostCommands

    override fun setUp() {
        super.setUp()
        dir = Files.createTempDirectory("latherit-test")
        projectFile = file("shop.latherit.yaml", "latherit: 1\nname: Shop\n")
        commands = HostCommands(project, projectFile, mocks, reload = { reloads++ })
    }

    override fun tearDown() {
        try {
            mocks.stopAll()
            dir.toFile().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    // ---------- Befehle allgemein ----------

    fun testUnbekannterBefehl() {
        assertEquals("Unbekannter Befehl: gibt_es_nicht", error(commands.run("gibt_es_nicht", JsonObject())))
    }

    fun testFehlenderParameter() {
        assertEquals("Parameter fehlt: path", error(commands.run("read_text_file", JsonObject())))
        assertEquals("Parameter fehlt: port", error(run("mock_start", "mockId" to "m1")))
    }

    fun testFehlertextOhneHostException() {
        assertEquals("Fehler: kaputt", HostCommands.describe(CompletionException(IllegalStateException("kaputt"))))
        assertEquals("Fehler: NullPointerException", HostCommands.describe(NullPointerException()))
        assertEquals("so", HostCommands.describe(CompletionException(HostException("so"))))
    }

    @Suppress("UNCHECKED_CAST")
    fun testSystemInfo() {
        val info = await(run("system_info")) as Map<String, Map<String, String>>
        assertEquals(System.getProperty("user.home"), info.getValue("properties")["user.home"])
        assertTrue(info.getValue("env").isNotEmpty())
    }

    // ---------- Lesen ----------

    fun testLiestGeschlosseneDateiVonDerPlatte() {
        val path = dir.resolve("bom.txt")
        Files.write(path, byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Grüße".toByteArray())
        assertEquals("Grüße", await(run("read_text_file", "path" to path.toString())))
    }

    fun testLiestUngespeichertenStandAusDemEditor() {
        val document = document(projectFile)
        edit(document, "latherit: 1\nname: Ungespeichert\n")
        assertEquals("latherit: 1\nname: Ungespeichert\n", await(run("read_text_file", "path" to projectFile.path)))
    }

    fun testFehlendeDatei() {
        val path = dir.resolve("fehlt.yaml").toString()
        assertEquals("$path: Datei nicht gefunden", error(run("read_text_file", "path" to path)))
    }

    // ---------- Schreiben ----------

    fun testSchreibtGeschlosseneDateiAtomar() {
        val path = dir.resolve("neu.latherit.yaml")
        await(run("write_text_file", "path" to path.toString(), "content" to "name: Neu\n"))
        assertEquals("name: Neu\n", Files.readString(path))
        // Keine Zwischendatei bleibt liegen, und das VFS kennt den neuen Inhalt
        assertEquals(listOf("neu.latherit.yaml", "shop.latherit.yaml"), Files.list(dir).map { it.fileName.toString() }.sorted().toList())
        val file = LocalFileSystem.getInstance().findFileByNioFile(path)!!
        assertEquals("name: Neu\n", String(file.contentsToByteArray()))
    }

    fun testSchreibtGeoeffneteDateiUeberDasDokument() {
        val document = document(projectFile)
        await(read())
        await(write("latherit: 1\r\nname: Neu\r\n"))
        assertEquals("latherit: 1\nname: Neu\n", document.text)
        assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(document))
        assertEquals("latherit: 1\nname: Neu\n", Files.readString(dir.resolve("shop.latherit.yaml")))
        assertEquals(0, reloads)
    }

    /** Die eigene Änderung lädt die eigene Ansicht nicht neu - eine fremde schon (siehe LatherItView.watchDocument). */
    fun testEigeneAenderungIstAlsSolcheErkennbar() {
        val document = document(projectFile)
        val seen = mutableListOf<Boolean>()
        document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                seen += commands.isOwnWrite(document)
            }
        }, testRootDisposable)
        await(read())
        await(write("name: Eigen\n"))
        edit(document, "name: Fremd\n")
        assertEquals(listOf(true, false), seen)
        assertFalse(commands.isOwnWrite(document))
    }

    fun testMehrereSpeichernNacheinander() {
        val document = document(projectFile)
        await(read())
        for (i in 1..5) await(write("name: Stand $i\n"))
        assertEquals("name: Stand 5\n", document.text)
    }

    /**
     * Fall „Text-Tab oder Git ändert die Datei, während das automatische Speichern der Oberfläche noch aussteht“:
     * Der alte Stand der Oberfläche darf die Änderung nicht überschreiben.
     */
    fun testUeberschreibtKeineFremdeAenderung() {
        val document = document(projectFile)
        await(read())
        edit(document, "name: Aus dem Text-Tab\n")
        val message = error(write("name: Alter Stand der Oberfläche\n"))
        assertEquals("shop.latherit.yaml wurde außerhalb von LatherIt geändert - nicht gespeichert, die Ansicht lädt neu", message)
        assertEquals("name: Aus dem Text-Tab\n", document.text)
        assertEquals(1, reloads)

        // Nach dem Neuladen (= neues Lesen) wird wieder gespeichert
        await(read())
        await(write("name: Nach dem Neuladen\n"))
        assertEquals("name: Nach dem Neuladen\n", document.text)
    }

    fun testUeberschreibtKeineAenderungVonAussen() {
        val document = document(projectFile)
        await(read())
        // Andere Programme (Git, Editor) schreiben auf die Platte, die IDE lädt das Dokument neu
        Files.writeString(dir.resolve("shop.latherit.yaml"), "name: Von Git\n")
        LocalFileSystem.getInstance().refreshFiles(listOf(projectFile))
        assertEquals("name: Von Git\n", document.text)
        error(write("name: Alter Stand\n"))
        assertEquals("name: Von Git\n", Files.readString(dir.resolve("shop.latherit.yaml")))
    }

    fun testBaumzustand() {
        assertNull(await(run("tree_state_get")))
        await(run("tree_state_set", "state" to "{\"expanded\":[\"a\"]}"))
        assertEquals("{\"expanded\":[\"a\"]}", await(run("tree_state_get")))
        assertEquals("{\"expanded\":[\"a\"]}", project.getService(LatherItTreeStates::class.java).get(projectFile.url))
    }

    /** Die Oberfläche kennt nur den Pfad vom Öffnen: Nach Umbenennen und Verschieben gilt er weiter für die Datei. */
    fun testUmbenennenUndVerschieben() {
        val startPath = projectFile.path
        val document = document(projectFile)
        val moveTo = Files.createDirectory(dir.resolve("anderswo"))
        WriteCommandAction.runWriteCommandAction(project) {
            projectFile.rename(this, "umbenannt.latherit.yaml")
            projectFile.move(this, LocalFileSystem.getInstance().refreshAndFindFileByNioFile(moveTo)!!)
        }
        await(run("read_text_file", "path" to startPath))
        await(run("write_text_file", "path" to startPath, "content" to "name: Verschoben\n"))
        assertEquals("name: Verschoben\n", document.text)
        assertEquals("name: Verschoben\n", Files.readString(moveTo.resolve("umbenannt.latherit.yaml")))
        assertFalse(Files.exists(Path.of(startPath)))
    }

    /** Zwei LatherIt-Tabs auf derselben Datei (geteilter Editor): Jeder sieht die Änderungen des anderen. */
    fun testZweiAnsichtenAufDerselbenDatei() {
        val document = document(projectFile)
        var otherReloads = 0
        val other = HostCommands(project, projectFile, mocks, reload = { otherReloads++ })
        val otherSeen = mutableListOf<Boolean>()
        document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                otherSeen += other.isOwnWrite(document)
            }
        }, testRootDisposable)
        await(read())
        await(other.run("read_text_file", args("path" to projectFile.path)))

        await(write("name: Von Tab 1\n"))
        // Für den zweiten Tab ist das eine fremde Änderung - er lädt neu
        assertEquals(listOf(false), otherSeen)
        // Sein ausstehendes Speichern mit altem Stand wird abgewiesen
        error(other.run("write_text_file", args("path" to projectFile.path, "content" to "name: Von Tab 2\n")))
        assertEquals("name: Von Tab 1\n", document.text)
        assertEquals(1, otherReloads)
    }

    // ---------- Mocks ----------

    fun testMocksUeberBefehle() {
        val port = await(run("mock_start", "mockId" to "m1", "port" to 0)) as Int
        assertTrue(port > 0)
        assertEquals(mapOf("m1" to port), await(run("mock_running")))
        assertEquals("Der Mock läuft bereits", error(run("mock_start", "mockId" to "m1", "port" to 0)))
        await(run("mock_stop", "mockId" to "m1"))
        assertEquals(emptyMap<String, Int>(), await(run("mock_running")))
    }

    // ---------- Hilfen ----------

    private fun file(name: String, content: String): VirtualFile {
        val path = dir.resolve(name)
        Files.writeString(path, content)
        return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)!!
    }

    private fun document(file: VirtualFile): Document = FileDocumentManager.getInstance().getDocument(file)!!

    private fun edit(document: Document, text: String) =
        WriteCommandAction.runWriteCommandAction(project) { document.setText(text) }

    private fun read() = run("read_text_file", "path" to projectFile.path)

    private fun write(content: String) = run("write_text_file", "path" to projectFile.path, "content" to content)

    private fun run(command: String, vararg args: Pair<String, Any>) = commands.run(command, args(*args))

    private fun args(vararg args: Pair<String, Any>) = JsonObject().apply {
        for ((name, value) in args) {
            when (value) {
                is String -> addProperty(name, value)
                is Number -> addProperty(name, value)
                is Boolean -> addProperty(name, value)
            }
        }
    }

    private fun <T> await(future: CompletableFuture<T>): T {
        val deadline = System.currentTimeMillis() + 10_000
        while (!future.isDone) {
            check(System.currentTimeMillis() < deadline) { "Zeitüberschreitung" }
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            Thread.sleep(5)
        }
        return future.join()
    }

    private fun error(future: CompletableFuture<*>): String {
        val error = runCatching { await(future) }.exceptionOrNull()
        assertNotNull("Fehler erwartet", error)
        return HostCommands.describe(error!!)
    }
}
