package app.heckit.ide.latherit

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros

/**
 * Aufgeklappte Knoten und Auswahl im Baum je Projektdatei (JSON der Oberfläche), im Workspace gespeichert:
 * übersteht Neuladen der Seite, Schließen des Tabs und IDE-Neustart. Statt als Editor-Zustand, weil
 * `FileEditorProvider.readState` ab 2026.3 veraltet ist und die Ablösung in 2026.2 noch fehlt.
 */
@Service(Service.Level.PROJECT)
@State(name = "LatherItTreeStates", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class LatherItTreeStates : SimplePersistentStateComponent<LatherItTreeStates.TreeStates>(TreeStates()) {
    class TreeStates : BaseState() {
        /** Datei-URL → Baumzustand */
        var byFile by map<String, String>()
    }

    fun get(fileUrl: String): String? = state.byFile[fileUrl]

    fun set(fileUrl: String, tree: String) {
        if (state.byFile[fileUrl] == tree) return
        // Neu zuweisen statt ändern: Nur so merkt BaseState die Änderung
        state.byFile = state.byFile.toMutableMap().apply { put(fileUrl, tree) }
    }
}
