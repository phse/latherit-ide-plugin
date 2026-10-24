package app.heckit.ide.latherit

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.editor.StripTrailingSpacesFilter
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files

/** Welche Dateien den LatherIt-Tab bekommen und wie die IDE sie beim Speichern behandelt. */
class ProjectFilesTest : BasePlatformTestCase() {
    private val dir by lazy { Files.createTempDirectory("latherit-test") }

    override fun tearDown() {
        try {
            dir.toFile().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    private fun localFile(name: String) =
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir.resolve(name).also { Files.writeString(it, "latherit: 1\n") })!!

    fun testNurProjektdateienAufDerPlatte() {
        val provider = LatherItEditorProvider()
        assertTrue(provider.accept(project, localFile("shop.latherit.yaml")))
        assertTrue(provider.accept(project, localFile("shop.latherit.yml")))
        assertFalse(provider.accept(project, localFile("shop.yaml")))
        assertFalse(provider.accept(project, localFile("latherit.yaml")))
        assertEquals(FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR, provider.policy)
    }

    /** Leerzeichen am Zeilenende gehören zu den Daten (z. B. in Request-Bodies) und bleiben stehen. */
    fun testKeineLeerzeichenEntfernenInProjektdateien() {
        val factory = LatherItStripFilterFactory()
        val projectDocument = FileDocumentManager.getInstance().getDocument(localFile("shop.latherit.yaml"))!!
        assertSame(StripTrailingSpacesFilter.NOT_ALLOWED, factory.createFilter(project, projectDocument))
        val otherDocument = FileDocumentManager.getInstance().getDocument(localFile("other.yaml"))!!
        assertSame(StripTrailingSpacesFilter.ALL_LINES, factory.createFilter(project, otherDocument))
        assertSame(StripTrailingSpacesFilter.ALL_LINES, factory.createFilter(project, DocumentImpl("x")))
    }

    /** Ende-zu-Ende: Speichern über die IDE lässt Leerzeichen am Zeilenende in Projektdateien stehen. */
    fun testSpeichernBehaeltLeerzeichen() {
        val file = localFile("shop.latherit.yaml")
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText("body: |\n  <a>   \n  </a>  \n") }
        FileDocumentManager.getInstance().saveDocument(document)
        assertEquals("body: |\n  <a>   \n  </a>  \n", Files.readString(dir.resolve("shop.latherit.yaml")))
    }
}
