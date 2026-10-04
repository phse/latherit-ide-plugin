package app.heckit.ide.latherit

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.jcef.JBCefApp
import java.beans.PropertyChangeListener
import javax.swing.JComponent

/**
 * Editor-Tab mit der LatherIt-Oberfläche für eine Projektdatei. Gespeichert wird von der Oberfläche
 * selbst (kurz nach jeder Änderung), daher gilt der Editor nie als ungespeichert.
 */
class LatherItEditor(project: Project, private val file: VirtualFile) : UserDataHolderBase(), FileEditor {
    private val view = if (JBCefApp.isSupported()) LatherItView(project, file, this) else null

    private val component: JComponent =
        view?.component ?: JBPanelWithEmptyText().withEmptyText(LatherItBundle.message("jcef.unavailable"))

    override fun getComponent(): JComponent = component

    override fun getPreferredFocusedComponent(): JComponent = component

    override fun getName(): String = LatherItBundle.message("editor.name")

    override fun getFile(): VirtualFile = file

    override fun setState(state: FileEditorState) = Unit

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = file.isValid

    override fun selectNotify() {
        view?.reloadIfChangedElsewhere()
    }

    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

    // Die Ansicht hängt per Disposer an diesem Editor und wird mit ihm freigegeben.
    override fun dispose() = Unit
}
