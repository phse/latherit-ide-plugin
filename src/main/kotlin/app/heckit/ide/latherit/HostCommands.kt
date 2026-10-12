package app.heckit.ide.latherit

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.EnvironmentUtil
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

/** Fehler, deren Text die Oberfläche so anzeigen soll. */
class HostException(message: String) : Exception(message)

/**
 * Die Befehle, die in der Desktop-App das Rust-Backend ausführt (siehe src/lib/backend.ts) -
 * gleiche Namen, Parameter und Ergebnisse - plus Dialoge und Zwischenablage aus der IDE.
 * Die Mock-Server laufen in [MockServers].
 */
class HostCommands(private val project: Project, private val file: VirtualFile, private val mocks: MockServers) {
    private val gson = Gson()

    fun run(command: String, args: JsonObject): CompletableFuture<Any?> = when (command) {
        "http_request" -> background { HttpBackend.execute(gson.fromJson(args.get("request"), HttpRequestData::class.java)) }
        "load_resource" -> background { HttpBackend.loadResource(args.string("url")) }
        "read_text_file" -> background { readTextFile(args.string("path")) }
        "write_text_file" -> background { writeTextFile(args.string("path"), args.string("content")); null }
        "system_info" -> background { systemInfo() }
        "open_dialog" -> onEdt { openDialog(filters(args)) }
        "save_dialog" -> onEdt { saveDialog(args.optString("defaultPath"), filters(args)) }
        "clipboard_read" -> onEdt { CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor) ?: "" }
        "clipboard_write" -> onEdt { CopyPasteManager.getInstance().setContents(StringSelection(args.string("text"))); null }
        "open_url" -> background { BrowserUtil.browse(args.string("url")); null }
        // Projekte gehören im Plugin je zu einer Datei mit eigenem Tab (siehe LatherItProjects)
        "new_project" -> onEdt { LatherItProjects.createNew(project, file.parent); null }
        "open_project" -> onEdt { LatherItProjects.chooseAndOpen(project, file.parent); null }
        "import_soapui" -> onEdt { LatherItProjects.importSoapUi(project, file.parent); null }
        "mock_start" -> background { mocks.start(args.string("mockId"), args.int("port"), args.get("public")?.asBoolean == true) }
        "mock_stop" -> background { mocks.stop(args.string("mockId")); null }
        "mock_running" -> CompletableFuture.completedFuture(mocks.running())
        "mock_respond" -> background {
            mocks.respond(args.long("requestId"), gson.fromJson(args.get("response"), MockAnswer::class.java)); null
        }
        else -> CompletableFuture.failedFuture(HostException("Unbekannter Befehl: $command"))
    }

    // ---------- Dateien ----------

    /** Ist die Datei in der IDE geöffnet, gilt der Stand im Editor (auch ungespeichert). */
    private fun readTextFile(path: String): String {
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Path.of(path))
            ?: throw HostException("$path: Datei nicht gefunden")
        FileDocumentManager.getInstance().getCachedDocument(file)?.let { document ->
            return ApplicationManager.getApplication().runReadAction<String> { document.text }
        }
        return HttpBackend.decode(file.contentsToByteArray(), null)
    }

    /**
     * Schreibt über das Dokument der IDE, wenn die Datei geöffnet ist (sonst würde die IDE einen Konflikt
     * zwischen Datei und Editor melden), andernfalls atomar wie die Desktop-App und meldet es dem VFS.
     */
    private fun writeTextFile(path: String, content: String) {
        val nioPath = Path.of(path)
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(nioPath)
        val document = file?.let { FileDocumentManager.getInstance().getCachedDocument(it) }
        if (document != null) {
            val text = StringUtil.convertLineSeparators(content)
            ApplicationManager.getApplication().invokeAndWait({
                // Auch das Speichern zählt als eigene Änderung: Die IDE kann dabei z. B. einen
                // Zeilenumbruch am Dateiende ergänzen, das soll die Oberfläche nicht neu laden.
                ownWrites.add(document)
                try {
                    if (document.text != text) {
                        WriteCommandAction.runWriteCommandAction(project, LatherItBundle.message("command.save"), null, {
                            document.setText(text)
                        })
                    }
                    FileDocumentManager.getInstance().saveDocument(document)
                } finally {
                    ownWrites.remove(document)
                }
            }, ModalityState.defaultModalityState())
            return
        }
        try {
            val temp = nioPath.resolveSibling(".${nioPath.fileName}.tmp")
            Files.writeString(temp, content)
            Files.move(temp, nioPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            throw HostException("$path: ${e.message}")
        }
        LocalFileSystem.getInstance().refreshNioFiles(listOf(nioPath))
    }

    // ---------- System ----------

    /** Für `${#System#…}` und `${#Env#…}`. Die IDE hat die Variablen der Login-Shell schon geladen. */
    private fun systemInfo(): Map<String, Map<String, String>> {
        val keys = listOf(
            "user.home", "user.name", "user.dir", "java.io.tmpdir",
            "os.name", "os.arch", "file.separator", "line.separator",
        )
        return mapOf(
            "properties" to keys.associateWith { System.getProperty(it).orEmpty() },
            "env" to EnvironmentUtil.getEnvironmentMap(),
        )
    }

    // ---------- Dialoge ----------

    private data class Filter(val name: String, val extensions: List<String>)

    private fun filters(args: JsonObject): List<Filter> =
        args.getAsJsonArray("filters")?.map { element ->
            val filter = element.asJsonObject
            Filter(filter.string("name"), filter.getAsJsonArray("extensions").map { it.asString })
        }.orEmpty()

    private fun openDialog(filters: List<Filter>): String? {
        var descriptor = FileChooserDescriptorFactory.singleFile().withTitle(LatherItBundle.message("dialog.open.title"))
        filters.firstOrNull()?.let { descriptor = descriptor.withExtensionFilter(it.name, *it.extensions.toTypedArray()) }
        return FileChooser.chooseFile(descriptor, project, null)?.path
    }

    private fun saveDialog(defaultPath: String?, filters: List<Filter>): String? {
        val title = LatherItBundle.message("dialog.save.title")
        val extension = filters.firstOrNull()?.extensions?.firstOrNull()
        val descriptor = if (extension != null) FileSaverDescriptor(title, "", extension) else FileSaverDescriptor(title, "")
        val default = defaultPath?.let { Path.of(it) }
        val directory = default?.parent ?: project.basePath?.let { Path.of(it) }
        val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
        return dialog.save(directory, default?.fileName?.toString())?.file?.path
    }

    // ---------- Hilfen ----------

    private fun <T> background(action: () -> T): CompletableFuture<Any?> =
        CompletableFuture.supplyAsync({ action() }, { ApplicationManager.getApplication().executeOnPooledThread(it) })

    private fun <T> onEdt(action: () -> T): CompletableFuture<Any?> {
        val future = CompletableFuture<Any?>()
        ApplicationManager.getApplication().invokeLater({
            runCatching(action).fold(future::complete, future::completeExceptionally)
        }, ModalityState.defaultModalityState())
        return future
    }

    private fun JsonObject.string(name: String): String =
        optString(name) ?: throw HostException("Parameter fehlt: $name")

    private fun JsonObject.int(name: String): Int =
        get(name)?.takeUnless { it.isJsonNull }?.asInt ?: throw HostException("Parameter fehlt: $name")

    private fun JsonObject.long(name: String): Long =
        get(name)?.takeUnless { it.isJsonNull }?.asLong ?: throw HostException("Parameter fehlt: $name")

    private fun JsonObject.optString(name: String): String? =
        get(name)?.takeUnless { it.isJsonNull }?.asString

    companion object {
        /** Dokumente, die gerade von der Oberfläche selbst geschrieben werden (nur auf dem EDT benutzt). */
        private val ownWrites = mutableSetOf<Document>()

        fun isOwnWrite(document: Document): Boolean = document in ownWrites

        /** Fehlertext für die Oberfläche. */
        fun describe(error: Throwable): String {
            val cause = if (error is CompletionException) error.cause ?: error else error
            return if (cause is HostException) cause.message.orEmpty() else "Fehler: ${cause.message ?: cause.javaClass.simpleName}"
        }
    }
}
