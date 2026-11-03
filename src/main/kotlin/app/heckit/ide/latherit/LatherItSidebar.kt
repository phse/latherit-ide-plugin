package app.heckit.ide.latherit

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.ide.projectView.impl.ProjectViewState
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
            Behavior.PREVIEW_TAB -> state.previewTab
            Behavior.SINGLE_CLICK_OPEN -> state.singleClickOpen
            Behavior.SINGLE_CLICK_EXPAND -> state.singleClickExpand
            Behavior.ALWAYS_SELECT -> state.alwaysSelect
        }
    }

    fun setBehavior(option: Behavior, value: Boolean) {
        initBehavior()
        when (option) {
            Behavior.PREVIEW_TAB -> state.previewTab = value
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
        val projectView = ProjectViewState.getInstance(project)
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
        val scope = GlobalSearchScope.projectScope(project)
        return FilenameIndex.getAllFilesByExt(project, "yaml", scope)
            .plus(FilenameIndex.getAllFilesByExt(project, "yml", scope))
            .filter { LatherItEditorProvider.isProjectFile(it.name) }
            .minByOrNull { it.path }
            ?.also { state.projectUrl = it.url }
    }

    companion object {
        const val TOOL_WINDOW_ID = "LatherIt"

        fun getInstance(project: Project): LatherItSidebar = project.getService(LatherItSidebar::class.java)

        /** [preview]: per Einfachklick geöffnet - mit „Enable Preview Tab“ im wiederverwendeten Vorschau-Tab. */
        fun openNode(project: Project, projectFile: VirtualFile, id: String, name: String, method: String?, preview: Boolean) {
            val sidebar = getInstance(project)
            val title = listOfNotNull(method, name).joinToString(" ")
            val file = sidebar.nodeFiles.getOrPut("${projectFile.url}#$id") { LatherItNodeFile(projectFile, id, title) }
            OpenFileDescriptor(project, file)
                .setUsePreviewTab(preview && sidebar.behavior(Behavior.PREVIEW_TAB))
                .navigate(!preview)
        }
    }
}

/** Ein Knoten einer Projektdatei als eigener Editor-Tab (nur im Seitenleisten-Modus). */
class LatherItNodeFile(val projectFile: VirtualFile, val nodeId: String, title: String) : LightVirtualFile(title) {
    init {
        isWritable = false
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
        toolWindow.setTitleActions(listOf(SwitchToEditorViewAction()))
        toolWindow.setAdditionalGearActions(DefaultActionGroup().apply {
            add(DefaultActionGroup(LatherItBundle.message("sidebar.behavior"), true).apply {
                LatherItSidebar.Behavior.entries.forEach { add(BehaviorToggle(it)) }
            })
        })
        val file = LatherItSidebar.getInstance(project).projectFile()
        val content = if (file == null || !JBCefApp.isSupported()) {
            val text = if (file == null) "sidebar.noProject" else "jcef.unavailable"
            ContentFactory.getInstance().createContent(JBPanelWithEmptyText().withEmptyText(LatherItBundle.message(text)), null, false)
        } else {
            val disposable = Disposer.newDisposable("LatherIt sidebar")
            val view = LatherItView(project, file, disposable, "nav")
            val sidebar = LatherItSidebar.getInstance(project)
            sidebar.navView = view
            Disposer.register(disposable) { if (sidebar.navView === view) sidebar.navView = null }
            ContentFactory.getInstance().createContent(view.component, null, false).apply { setDisposer(disposable) }
        }
        toolWindow.contentManager.addContent(content)
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
    com.intellij.icons.AllIcons.Actions.MoveToWindow,
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
}
