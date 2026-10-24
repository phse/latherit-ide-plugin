package app.heckit.ide.latherit

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile

/**
 * Vor dem Schließen einer Projektdatei den LatherIt-Tab nach vorn holen: Die IDE merkt sich den Tab, der
 * beim Schließen vorn war, und wählt ihn beim nächsten Öffnen vor - war das „Text“, blitzte der Text-Tab
 * kurz auf, bis [LatherItOpenListener] umschaltet.
 */
class LatherItCloseListener : FileEditorManagerListener.Before {
    override fun beforeFileClosed(source: FileEditorManager, file: VirtualFile) {
        if (!LatherItEditorProvider.isProjectFile(file.name)) return
        if (source.getSelectedEditor(file) is LatherItEditor) return
        if (source.getEditors(file).any { it is LatherItEditor }) {
            source.setSelectedEditor(file, LatherItEditorProvider.EDITOR_TYPE_ID)
        }
    }
}
