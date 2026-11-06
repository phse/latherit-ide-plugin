package app.heckit.ide.latherit

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.icons.AllIcons
import com.intellij.ide.FileIconProvider
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.ide.projectView.impl.ProjectViewState
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VfsUtilCore
import javax.swing.Icon
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefApp
import java.beans.PropertyChangeListener
import javax.swing.JComponent

/**
 * Seitenleisten-Modus (je Projekt wählbar): Tool Window „LatherIt“ mit dem Projektbaum, jeder gewählte Knoten
 * öffnet sich als eigener Editor-Tab. Ist der Modus aus, bleibt alles wie bisher (ganze Oberfläche im Editor).
 *
 * Abgleich zwischen Baum und Tabs läuft über die Projektdatei: Jede Ansicht speichert selbst, die anderen laden
 * bei fremden Änderungen am Dokument neu (siehe `LatherItView.watchDocument`).
 */
@Service(Service.Level.PROJECT)
@State(name = "LatherItSidebar", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class LatherItSidebar(private val project: Project) :
    SimplePersistentStateComponent<LatherItSidebar.SidebarState>(SidebarState()) {
    class SidebarState : BaseState() {
        var enabled by property(false)

        /** Zuletzt im Tool Window gezeigte Projektdatei (URL) */
        var projectUrl by string()

        /** Behavior-Schalter wie im Project-Fenster; beim ersten Einschalten von dort übernommen. */
        var behaviorInitialized by property(false)
        var previewTab by property(false)
        var singleClickOpen by property(false)
        var singleClickExpand by property(false)
        var alwaysSelect by property(false)
    }

    /** Behavior-Schalter des Tool Windows (Menü ⋮ → Behavior). */
    enum class Behavior { PREVIEW_TAB, SINGLE_CLICK_OPEN, SINGLE_CLICK_EXPAND, ALWAYS_SELECT }

    /** Die Ansicht im Tool Window (Baum), solange es offen ist. */
    var navView: LatherItView? = null

    fun behavior(option: Behavior): Boolean {
        initBehavior()
        return when (option) {
            // Global wie im Project-Fenster: Die IDE öffnet Vorschau-Tabs nur, wenn diese Einstellung an ist
            Behavior.PREVIEW_TAB -> UISettings.getInstance().openInPreviewTabIfPossible
            Behavior.SINGLE_CLICK_OPEN -> state.singleClickOpen
            Behavior.SINGLE_CLICK_EXPAND -> state.singleClickExpand
            Behavior.ALWAYS_SELECT -> state.alwaysSelect
        }
    }

    fun setBehavior(option: Behavior, value: Boolean) {
        initBehavior()
        when (option) {
            Behavior.PREVIEW_TAB -> UISettings.getInstance().let {
                it.openInPreviewTabIfPossible = value
                it.fireUISettingsChanged()
            }
            Behavior.SINGLE_CLICK_OPEN -> state.singleClickOpen = value
            Behavior.SINGLE_CLICK_EXPAND -> state.singleClickExpand = value
            Behavior.ALWAYS_SELECT -> state.alwaysSelect = value
        }
        navView?.emit("sidebar-settings", clickSettings())
    }

    /** Für die Oberfläche (siehe `ideSingleClickOpen` in src/lib/ide.ts) */
    fun clickSettings(): Map<String, Boolean> = mapOf(
        "singleClickOpen" to behavior(Behavior.SINGLE_CLICK_OPEN),
        "singleClickExpand" to behavior(Behavior.SINGLE_CLICK_EXPAND),
    )

    private fun initBehavior() {
        if (state.behaviorInitialized) return
        val projectView = project.getService(ProjectViewState::class.java)
        state.previewTab = UISettings.getInstance().openInPreviewTabIfPossible
        state.singleClickOpen = projectView.autoscrollToSource
        state.singleClickExpand = projectView.openDirectoriesWithSingleClick
        state.alwaysSelect = projectView.autoscrollFromSource
        state.behaviorInitialized = true
    }

    /** Request-Tab gewählt: im Baum markieren („Always Select Opened File“). */
    fun editorSelected(file: VirtualFile?) {
        if (file !is LatherItNodeFile || !behavior(Behavior.ALWAYS_SELECT)) return
        if (file.projectFile.url != state.projectUrl) return
        navView?.emit("reveal-node", file.nodeId)
    }

    /** Knoten-ID → virtuelle Datei: derselbe Knoten holt seinen offenen Tab nach vorn statt einen neuen zu öffnen. */
    private val nodeFiles = mutableMapOf<String, LatherItNodeFile>()

    var enabled: Boolean
        get() = state.enabled
        set(value) {
            state.enabled = value
            ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.let {
                it.isAvailable = value
                if (value) it.activate(null)
            }
        }

    /** Gemerkte Projektdatei oder die erste `*.latherit.yaml` im Projekt. */
    fun projectFile(): VirtualFile? {
        state.projectUrl?.let { url -> VirtualFileManager.getInstance().findFileByUrl(url)?.takeIf { it.isValid } }
            ?.let { return it }
        return allProjectFiles().firstOrNull()?.also { state.projectUrl = it.url }
    }

    /** Alle Projektdateien im Projekt, nach Pfad sortiert. */
    fun allProjectFiles(): List<VirtualFile> {
        val scope = GlobalSearchScope.projectScope(project)
        return (FilenameIndex.getAllFilesByExt(project, "yaml", scope) + FilenameIndex.getAllFilesByExt(project, "yml", scope))
            .filter { LatherItEditorProvider.isProjectFile(it.name) }
            .sortedBy { it.path }
    }

    /** Projektdatei im Tool Window zeigen (Auswahl, Öffnen einer `latherit.yaml` bei eingeschaltetem Modul). */
    fun showProject(file: VirtualFile) {
        val changed = state.projectUrl != file.url
        state.projectUrl = file.url
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return
        if (changed && navView != null) fillToolWindow(project, toolWindow)
        toolWindow.activate(null)
    }

    /** „Als Seitenleisten-Modul öffnen“ aus der Ansicht im Editor: Modus an, Datei ins Tool Window. */
    fun enableFor(file: VirtualFile) {
        state.projectUrl = file.url
        enabled = true
        FileEditorManager.getInstance(project).closeFile(file)
    }

    /** Titel/Art aller Knoten aus dem Baum: offene Tabs umbenennen, Tabs gelöschter Knoten schließen. */
    fun syncNodes(projectFile: VirtualFile, nodes: List<NodeInfo>) {
        val byId = nodes.associateBy { it.id }
        val editors = FileEditorManager.getInstance(project) as FileEditorManagerEx
        val prefix = "${projectFile.url}#"
        for ((key, file) in nodeFiles.entries.toList()) {
            if (!key.startsWith(prefix)) continue
            val node = byId[file.nodeId]
            if (node == null) {
                nodeFiles.remove(key)
                editors.closeFile(file)
            } else {
                val oldName = file.name
                if (!file.update(node.title, node.kind, node.path)) continue
                editors.updateFilePresentation(file)
                // Alter und neuer Name: Wer bisher Zwilling war, verliert seinen Zusatz, wer es wird, bekommt ihn
                refreshTwins(project, oldName)
                refreshTwins(project, file.name)
            }
        }
    }

    /** Knoten, wie ihn die Oberfläche meldet (`sidebar_nodes`, `open_node`). */
    data class NodeInfo(
        val id: String,
        val name: String,
        val method: String?,
        val kind: String?,
        /** Namen der Eltern ohne Projekt, vom obersten zum nächsten (Interface, Operation, …) */
        val path: List<String> = emptyList(),
    ) {
        val title get() = listOfNotNull(method, name).joinToString(" ")
    }

    companion object {
        const val TOOL_WINDOW_ID = "LatherIt"

        fun getInstance(project: Project): LatherItSidebar = project.getService(LatherItSidebar::class.java)

        /** [preview]: per Einfachklick geöffnet - mit „Enable Preview Tab“ im wiederverwendeten Vorschau-Tab. */
        fun openNode(project: Project, projectFile: VirtualFile, node: NodeInfo, preview: Boolean) {
            val sidebar = getInstance(project)
            val file = sidebar.nodeFiles.getOrPut("${projectFile.url}#${node.id}") {
                LatherItNodeFile(projectFile, node.id, node.title, node.kind)
            }
            // Nach dem Umbenennen kommt der neue Name oft hier an, bevor sidebar_nodes ihn meldet - dann muss
            // auch der bisherige Zwilling unter dem alten Namen seinen Zusatz verlieren
            val oldName = file.name
            file.update(node.title, node.kind, node.path)
            OpenFileDescriptor(project, file)
                .setUsePreviewTab(preview && sidebar.behavior(Behavior.PREVIEW_TAB))
                .navigate(!preview)
            if (oldName != file.name) refreshTwins(project, oldName)
            refreshTwins(project, file.name)
        }

        /** Titel gleichnamiger Tabs neu berechnen (siehe [LatherItNodeTabTitleProvider]). */
        fun refreshTwins(project: Project, name: String) {
            val editors = FileEditorManager.getInstance(project) as FileEditorManagerEx
            editors.openFiles.filter { it is LatherItNodeFile && it.name == name }.forEach { editors.updateFilePresentation(it) }
        }
    }
}

/** Ein Knoten einer Projektdatei als eigener Editor-Tab (nur im Seitenleisten-Modus). */
class LatherItNodeFile(val projectFile: VirtualFile, val nodeId: String, title: String, kind: String?) : LightVirtualFile(title) {
    private var title = title

    /** Art des Knotens (`request`, `testCase`, … wie `NodeKind` in src/model/project.ts) - fürs Tab-Icon */
    var kind = kind
        private set

    init {
        isWritable = false
    }

    override fun getName(): String = title

    override fun getPresentableName(): String = title

    /** Namen der Eltern (ohne Projekt) - für gleichnamige Tabs und den Tooltip */
    var path: List<String> = emptyList()
        private set

    /** Liefert `true`, wenn sich etwas geändert hat. */
    fun update(newTitle: String, newKind: String?, newPath: List<String>): Boolean {
        if (newTitle == title && newKind == kind && newPath == path) return false
        title = newTitle
        kind = newKind
        path = newPath
        return true
    }
}

/**
 * Gleichnamige Request-Tabs („Request 1“ unter verschiedenen Operationen) bekommen den Namen des nächsten
 * Elternknotens dazu, der sie unterscheidet - wie die IDE es bei gleichnamigen Dateien mit dem Ordner macht.
 */
class LatherItNodeTabTitleProvider : EditorTabTitleProvider, DumbAware {
    override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
        if (file !is LatherItNodeFile) return null
        val twins = FileEditorManager.getInstance(project).openFiles
            .filterIsInstance<LatherItNodeFile>()
            .filter { it !== file && it.name == file.name }
        if (twins.isEmpty()) return null
        // Vom nächsten Elternteil aufwärts den ersten Namen, der sich bei allen Zwillingen unterscheidet
        val own = file.path.reversed()
        val level = own.indices.firstOrNull { i -> twins.all { it.path.reversed().getOrNull(i) != own[i] } }
            ?: own.indices.lastOrNull()
            ?: return null
        return "${file.name} — ${own[level]}"
    }

    override fun getEditorTabTooltipHtml(project: Project, virtualFile: VirtualFile): HtmlChunk? =
        (virtualFile as? LatherItNodeFile)?.let { HtmlChunk.text((it.path + it.name).joinToString(" › ")) }
}

/** Tab-Icon je Knotenart. */
class LatherItNodeIconProvider : FileIconProvider {
    override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
        if (file !is LatherItNodeFile) return null
        return when (file.kind) {
            "testSuite", "testCase", "testStep" -> AllIcons.Nodes.Test
            "loadTest" -> AllIcons.Actions.Profile
            "mockService", "mockOperation", "mockResponse" -> AllIcons.Nodes.Servlet
            "project" -> AllIcons.Nodes.Folder
            else -> LATHERIT_ICON
        }
    }

    private companion object {
        val LATHERIT_ICON = IconLoader.getIcon("/icons/latherit.svg", LatherItNodeIconProvider::class.java)
    }
}

class LatherItNodeEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean = file is LatherItNodeFile

    override fun createEditor(project: Project, file: VirtualFile): FileEditor = LatherItNodeEditor(project, file as LatherItNodeFile)

    override fun getEditorTypeId(): String = "latherit-node"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

class LatherItNodeEditor(project: Project, private val file: LatherItNodeFile) : UserDataHolderBase(), FileEditor {
    private val view: LatherItView? = if (JBCefApp.isSupported()) {
        runCatching { LatherItView(project, file.projectFile, this, "detail", file.nodeId) }.getOrNull()
    } else {
        null
    }

    private val component: JComponent = view?.component
        ?: JBPanelWithEmptyText().withEmptyText(LatherItBundle.message("jcef.unavailable"))

    override fun getComponent(): JComponent = component
    override fun getPreferredFocusedComponent(): JComponent = component
    override fun getName(): String = LatherItBundle.message("editor.name")
    override fun getFile(): VirtualFile = file
    override fun setState(state: FileEditorState) = Unit
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = file.projectFile.isValid
    override fun selectNotify() {
        view?.reloadIfChangedElsewhere()
    }
    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit
    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit
    override fun dispose() = Unit
}

/** Tool Window links neben Project; nur sichtbar, wenn der Seitenleisten-Modus für das Projekt an ist. */
class LatherItToolWindowFactory : ToolWindowFactory, DumbAware {
    override suspend fun isApplicableAsync(project: Project): Boolean = true

    override fun shouldBeAvailable(project: Project): Boolean = LatherItSidebar.getInstance(project).enabled

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.setTitleActions(listOf(ChooseProjectFileAction(), SwitchToEditorViewAction()))
        toolWindow.setAdditionalGearActions(DefaultActionGroup().apply {
            add(DefaultActionGroup(LatherItBundle.message("sidebar.behavior"), true).apply {
                LatherItSidebar.Behavior.entries.forEach { add(BehaviorToggle(it)) }
            })
        })
        fillToolWindow(project, toolWindow)
    }
}

/** Inhalt des Tool Windows (neu) aufbauen - mit der gewählten Projektdatei. */
internal fun fillToolWindow(project: Project, toolWindow: ToolWindow) {
    val sidebar = LatherItSidebar.getInstance(project)
    toolWindow.contentManager.removeAllContents(true)
    val file = sidebar.projectFile()
    val content = if (file == null || !JBCefApp.isSupported()) {
        val text = if (file == null) "sidebar.noProject" else "jcef.unavailable"
        ContentFactory.getInstance().createContent(JBPanelWithEmptyText().withEmptyText(LatherItBundle.message(text)), null, false)
    } else {
        val disposable = Disposer.newDisposable("LatherIt sidebar")
        val view = LatherItView(project, file, disposable, "nav")
        sidebar.navView = view
        Disposer.register(disposable) { if (sidebar.navView === view) sidebar.navView = null }
        val name = file.name.removeSuffix(".yaml").removeSuffix(".yml").removeSuffix(".latherit")
        ContentFactory.getInstance().createContent(view.component, name, false).apply { setDisposer(disposable) }
    }
    toolWindow.contentManager.addContent(content)
}

/** Bei mehreren Projektdateien: Auswahl, welche das Tool Window zeigt. */
private class ChooseProjectFileAction : com.intellij.openapi.project.DumbAwareAction(
    { LatherItBundle.message("sidebar.chooseProject") },
    AllIcons.General.ArrowDown,
) {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null && !DumbService.isDumb(project) &&
            LatherItSidebar.getInstance(project).allProjectFiles().size > 1
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val sidebar = LatherItSidebar.getInstance(project)
        val current = sidebar.projectFile()
        val base = project.guessProjectDir()
        val files = sidebar.allProjectFiles()
        val step = object : BaseListPopupStep<VirtualFile>(null, files) {
            override fun getTextFor(value: VirtualFile) =
                (base?.let { VfsUtilCore.getRelativePath(value, it) } ?: value.path) + if (value == current) "  ✓" else ""

            override fun onChosen(selectedValue: VirtualFile, finalChoice: Boolean): PopupStep<*>? {
                sidebar.showProject(selectedValue)
                return FINAL_CHOICE
            }
        }
        JBPopupFactory.getInstance().createListPopup(step).showUnderneathOf(e.inputEvent?.component ?: return)
    }
}

/** Ein/Aus für das aktuelle Projekt (Menü View → LatherIt Sidebar Module). */
class ToggleSidebarModuleAction : DumbAwareToggleAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun isSelected(e: AnActionEvent): Boolean = e.project?.let { LatherItSidebar.getInstance(it).enabled } == true

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val project = e.project ?: return
        LatherItSidebar.getInstance(project).enabled = state
    }
}

/** Im Tool Window: zurück zur gewohnten Ansicht im Editor (schaltet den Modus aus und öffnet die Datei). */
private class SwitchToEditorViewAction : com.intellij.openapi.project.DumbAwareAction(
    { LatherItBundle.message("action.switchToEditor") },
    AllIcons.Actions.MoveToWindow,
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val sidebar = LatherItSidebar.getInstance(project)
        val file = sidebar.projectFile()
        sidebar.enabled = false
        if (file != null) OpenFileDescriptor(project, file).navigate(true)
    }
}

/** Ein Schalter im Menü ⋮ → Behavior des Tool Windows. */
private class BehaviorToggle(private val option: LatherItSidebar.Behavior) :
    DumbAwareToggleAction({ LatherItBundle.message("sidebar.behavior." + option.name.lowercase()) }) {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun isSelected(e: AnActionEvent): Boolean =
        e.project?.let { LatherItSidebar.getInstance(it).behavior(option) } == true

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        e.project?.let { LatherItSidebar.getInstance(it).setBehavior(option, state) }
    }
}

/** Gewählter Editor-Tab → Baum (siehe [LatherItSidebar.editorSelected]). */
class LatherItSidebarSelectionListener(private val project: Project) : FileEditorManagerListener {
    override fun selectionChanged(event: FileEditorManagerEvent) {
        LatherItSidebar.getInstance(project).editorSelected(event.newFile)
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        if (file !is LatherItNodeFile) return
        // Während fileClosed zählt die geschlossene Datei noch zu openFiles - der Zwilling behielte den Zusatz
        ApplicationManager.getApplication().invokeLater({ LatherItSidebar.refreshTwins(project, file.name) }, project.disposed)
    }
}
