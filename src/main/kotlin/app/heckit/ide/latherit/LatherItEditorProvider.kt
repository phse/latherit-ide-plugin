package app.heckit.ide.latherit

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/** Projektdateien (`*.latherit.yaml`) bekommen einen LatherIt-Tab vor dem normalen Text-Editor. */
class LatherItEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean =
        file.isInLocalFileSystem && file.name.endsWith(".$FILE_EXTENSION")

    override fun createEditor(project: Project, file: VirtualFile): FileEditor = LatherItEditor(project, file)

    override fun getEditorTypeId(): String = "latherit"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR

    companion object {
        /** Wie `FILE_EXTENSION` in src/lib/projectFile.ts */
        const val FILE_EXTENSION = "latherit.yaml"
    }
}
