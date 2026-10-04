package app.heckit.ide.latherit

import com.google.gson.Gson
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import java.util.UUID

/**
 * Projektdateien anlegen, öffnen und aus SoapUI importieren. Im Plugin gehört jedes LatherIt-Projekt zu
 * genau einer Datei mit eigenem Editor-Tab - so gibt es keine ungespeicherten Projekte, die beim Schließen
 * verloren gehen könnten.
 */
object LatherItProjects {
    /** SoapUI-Datei, die die Oberfläche beim ersten Laden in die neue Projektdatei importieren soll. */
    val IMPORT_FROM = Key.create<String>("latherit.importFrom")

    private const val EXTENSION = LatherItEditorProvider.FILE_EXTENSION

    /** Fragt nach dem Namen, legt die Datei an und öffnet sie. */
    fun createNew(project: Project, directory: VirtualFile?) {
        val target = directory ?: project.guessProjectDir() ?: return
        val name = askName(project, target, LatherItBundle.message("dialog.newProject.title"), LatherItBundle.message("default.projectName"))
            ?: return
        open(project, createFile(project, target, name))
    }

    /** Wählt eine Projektdatei und öffnet sie in einem Editor-Tab. */
    fun chooseAndOpen(project: Project, directory: VirtualFile?) {
        val descriptor = FileChooserDescriptorFactory.singleFile()
            .withTitle(LatherItBundle.message("dialog.openProject.title"))
            .withExtensionFilter(LatherItBundle.message("filter.project"), "yaml")
        val file = FileChooser.chooseFile(descriptor, project, directory ?: project.guessProjectDir()) ?: return
        open(project, file)
    }

    /** Wählt ein SoapUI-Projekt, legt daneben (bzw. im Zielordner) eine Projektdatei an und importiert hinein. */
    fun importSoapUi(project: Project, directory: VirtualFile?) {
        val descriptor = FileChooserDescriptorFactory.singleFile()
            .withTitle(LatherItBundle.message("dialog.importSoapUi.title"))
            .withExtensionFilter(LatherItBundle.message("filter.soapUi"), "xml")
        val source = FileChooser.chooseFile(descriptor, project, directory ?: project.guessProjectDir()) ?: return
        val target = directory ?: source.parent ?: return
        val suggested = source.name.removeSuffix(".xml").removeSuffix("-soapui-project")
        val name = askName(project, target, LatherItBundle.message("dialog.importSoapUi.title"), suggested) ?: return
        val file = createFile(project, target, name)
        file.putUserData(IMPORT_FROM, source.path)
        open(project, file)
    }

    private fun open(project: Project, file: VirtualFile) {
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    private fun askName(project: Project, directory: VirtualFile, title: String, initial: String): String? =
        Messages.showInputDialog(
            project,
            LatherItBundle.message("dialog.projectName", directory.presentableUrl),
            title,
            null,
            initial,
            object : InputValidatorEx {
                override fun getErrorText(inputString: String): String? {
                    val name = inputString.trim()
                    return when {
                        name.isEmpty() -> LatherItBundle.message("error.nameEmpty")
                        name.any { it in "/\\:*?\"<>|" } -> LatherItBundle.message("error.nameInvalid")
                        directory.findChild("$name.$EXTENSION") != null -> LatherItBundle.message("error.exists", "$name.$EXTENSION")
                        else -> null
                    }
                }
            },
        )?.trim()

    /** Minimale Projektdatei (Format wie src/lib/projectFile.ts); den Rest schreibt die Oberfläche. */
    private fun createFile(project: Project, directory: VirtualFile, name: String): VirtualFile {
        val gson = Gson()
        val content = "# LatherIt-Projekt\nlatherit: 1\nid: ${UUID.randomUUID()}\nname: ${gson.toJson(name)}\n"
        return WriteAction.compute<VirtualFile, Exception> {
            directory.createChildData(project, "$name.$EXTENSION").also { it.setBinaryContent(content.toByteArray()) }
        }
    }

    /** Ordner aus der Auswahl (Projektbaum): der Ordner selbst oder der einer Datei. */
    fun selectedDirectory(event: AnActionEvent): VirtualFile? {
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return null
        return if (file.isDirectory) file else file.parent
    }
}

/** Datei › Neu › LatherIt-Projekt */
class NewLatherItProjectAction : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        LatherItProjects.createNew(project, LatherItProjects.selectedDirectory(event))
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

/** Datei › Neu › LatherIt-Projekt aus SoapUI … */
class ImportSoapUiProjectAction : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        LatherItProjects.importSoapUi(project, LatherItProjects.selectedDirectory(event))
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
