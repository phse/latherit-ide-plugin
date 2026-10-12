package app.heckit.ide.latherit

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.StripTrailingSpacesFilter
import com.intellij.openapi.editor.StripTrailingSpacesFilterFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project

/**
 * Projektdateien schreibt allein die Oberfläche. Würde die IDE beim Speichern Leerzeichen am Zeilenende
 * entfernen, änderte sie Request-Inhalte (Block-Scalars im YAML) und das Dokument - die Oberfläche
 * würde neu geladen und verlöre Auswahl und aufgeklappten Baum.
 */
class LatherItStripFilterFactory : StripTrailingSpacesFilterFactory() {
    override fun createFilter(project: Project?, document: Document): StripTrailingSpacesFilter {
        val file = FileDocumentManager.getInstance().getFile(document)
        return if (file != null && file.name.endsWith(".${LatherItEditorProvider.FILE_EXTENSION}")) {
            StripTrailingSpacesFilter.NOT_ALLOWED
        } else {
            StripTrailingSpacesFilter.ALL_LINES
        }
    }
}
