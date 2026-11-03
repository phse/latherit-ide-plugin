package app.heckit.ide.latherit

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected

/** Settings → Tools → LatherIt: Seitenleisten-Modus und sein Verhalten, je Projekt. */
class LatherItConfigurable(private val project: Project) : BoundConfigurable(LatherItBundle.message("settings.title")) {
    override fun createPanel(): DialogPanel {
        val sidebar = LatherItSidebar.getInstance(project)
        return panel {
            lateinit var enabled: com.intellij.ui.dsl.builder.Cell<javax.swing.JCheckBox>
            row {
                enabled = checkBox(LatherItBundle.message("settings.sidebarModule"))
                    .bindSelected({ sidebar.enabled }, { sidebar.enabled = it })
                    .comment(LatherItBundle.message("settings.sidebarModule.comment"))
            }
            group(LatherItBundle.message("sidebar.behavior")) {
                for (option in LatherItSidebar.Behavior.entries) {
                    row {
                        checkBox(LatherItBundle.message("sidebar.behavior." + option.name.lowercase()))
                            .bindSelected({ sidebar.behavior(option) }, { sidebar.setBehavior(option, it) })
                    }
                }
            }.enabledIf(enabled.component.selected)
        }
    }
}
