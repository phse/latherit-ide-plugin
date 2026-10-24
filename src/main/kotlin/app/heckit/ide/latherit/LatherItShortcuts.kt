package app.heckit.ide.latherit

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.DumbAwareAction

/** Kürzel des LatherIt-Tabs: geht als Ereignis `shortcut` an die Oberfläche (siehe LatherItView.installShortcuts). */
class LatherItShortcutAction(private val name: String, private val emit: (String) -> Unit) : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) = emit(name)
}

/**
 * Belegen IDE oder JCEF dasselbe Kürzel (z. B. ⌘A für „Select All“), gewinnt sonst deren Aktion -
 * im LatherIt-Tab soll die Oberfläche es bekommen.
 */
class LatherItShortcutPromoter : ActionPromoter {
    override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> =
        actions.filterIsInstance<LatherItShortcutAction>()
}
