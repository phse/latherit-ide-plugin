package app.heckit.ide.latherit

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile

/**
 * Projektdateien öffnen immer im LatherIt-Tab. Die IDE merkt sich sonst je Datei, welcher Tab zuletzt vorn
 * war - wer einmal in „Text“ geschaut hat, bekäme beim nächsten Öffnen nur den Text.
 */
class LatherItOpenListener : FileEditorManagerListener {
    // Meist ist LatherIt schon vorn (siehe LatherItCloseListener); sonst hier umschalten. Das synchrone
    // fileOpenedSync ist veraltet.
    override fun fileOpened(source: FileEditorManager, file: VirtualFile) = selectLatherIt(source, file)

    private fun selectLatherIt(source: FileEditorManager, file: VirtualFile) {
        if (!LatherItEditorProvider.isProjectFile(file.name)) return
        if (source.getSelectedEditor(file) is LatherItEditor) return
        if (source.getEditors(file).any { it is LatherItEditor }) {
            source.setSelectedEditor(file, LatherItEditorProvider.EDITOR_TYPE_ID)
        }
    }
}
