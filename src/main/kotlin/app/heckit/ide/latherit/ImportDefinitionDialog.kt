package app.heckit.ide.latherit

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.ComponentPredicate
import java.awt.event.ItemEvent
import javax.swing.JComponent

/**
 * „WSDL / OpenAPI importieren“ als Dialog der IDE. Im Seitenleisten-Modul ist die Oberfläche zu schmal
 * für den Dialog im Browser; hier werden nur die Angaben abgefragt, den Import selbst macht die
 * Oberfläche (wie im Browser-Dialog).
 */
class ImportDefinitionDialog(project: Project) : DialogWrapper(project) {

    /** Ergebnis für die Oberfläche (Felder wie `AuthSettings` in LatherIt). */
    data class Result(val location: String, val auth: Map<String, String>?, val apply: Boolean)

    private enum class AuthKind(val id: String, val key: String) {
        NONE("none", "import.auth.none"), BASIC("basic", "import.auth.basic"), BEARER("bearer", "import.auth.bearer"),
        DIGEST("digest", "import.auth.digest"), NTLM("ntlm", "import.auth.ntlm");

        override fun toString() = LatherItBundle.message(key)
    }

    private val location = TextFieldWithBrowseButton().apply {
        textField.columns = 48
        addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.singleFile()
                .withTitle(LatherItBundle.message("import.title"))
                .withExtensionFilter(LatherItBundle.message("import.filter"), "wsdl", "xml", "json", "yaml", "yml"),
        )
    }
    private val kind = ComboBox(AuthKind.entries.toTypedArray())
    private val username = JBTextField()
    private val password = JBPasswordField()
    private val token = JBTextField()
    private val domain = JBTextField()
    private val apply = JBCheckBox(LatherItBundle.message("import.auth.apply"), true)

    private fun kindIs(vararg kinds: AuthKind) = object : ComponentPredicate() {
        override fun invoke() = kind.item in kinds
        override fun addListener(listener: (Boolean) -> Unit) {
            kind.addItemListener { if (it.stateChange == ItemEvent.SELECTED) listener(invoke()) }
        }
    }

    init {
        title = LatherItBundle.message("import.title")
        setOKButtonText(LatherItBundle.message("import.run"))
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row { comment(LatherItBundle.message("import.sub")) }
        row(LatherItBundle.message("import.location")) {
            cell(location).align(AlignX.FILL).focused()
        }
        group(LatherItBundle.message("import.auth")) {
            row(LatherItBundle.message("import.auth.type")) { cell(kind) }
            row(LatherItBundle.message("import.auth.username")) { cell(username).align(AlignX.FILL) }
                .visibleIf(kindIs(AuthKind.BASIC, AuthKind.DIGEST, AuthKind.NTLM))
            row(LatherItBundle.message("import.auth.password")) { cell(password).align(AlignX.FILL) }
                .visibleIf(kindIs(AuthKind.BASIC, AuthKind.DIGEST, AuthKind.NTLM))
            row(LatherItBundle.message("import.auth.domain")) { cell(domain).align(AlignX.FILL) }
                .visibleIf(kindIs(AuthKind.NTLM))
            row(LatherItBundle.message("import.auth.token")) { cell(token).align(AlignX.FILL) }
                .visibleIf(kindIs(AuthKind.BEARER))
            row { comment(LatherItBundle.message("import.auth.placeholders")) }
                .visibleIf(kindIs(AuthKind.BASIC, AuthKind.BEARER, AuthKind.DIGEST, AuthKind.NTLM))
            row { cell(apply) }.visibleIf(kindIs(AuthKind.BASIC, AuthKind.BEARER, AuthKind.DIGEST, AuthKind.NTLM))
        }
    }

    override fun doValidate(): ValidationInfo? =
        if (cleanedLocation().isEmpty()) ValidationInfo(LatherItBundle.message("import.location.empty"), location) else null

    /** Aus Texten kopierte Adressen bringen oft Anführungszeichen mit. */
    private fun cleanedLocation() = location.text.trim().trim('\'', '"', '<', '>', '„', '“').trim()

    fun result(): Result {
        val selected = kind.item ?: AuthKind.NONE
        val auth = if (selected == AuthKind.NONE) null else buildMap<String, String> {
            put("type", selected.id)
            when (selected) {
                AuthKind.BEARER -> put("token", token.text)
                else -> {
                    put("username", username.text)
                    put("password", String(password.password))
                    if (selected == AuthKind.NTLM && domain.text.isNotBlank()) put("domain", domain.text)
                }
            }
        }
        return Result(cleanedLocation(), auth, apply.isSelected)
    }
}
