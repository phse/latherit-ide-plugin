package app.heckit.ide.latherit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Auslieferung der Oberfläche aus dem Plugin (Vite-Build unter /latherit-web/). */
class WebResourcesTest {
    private val resources = WebResources { "window.__test = 1" }

    @Test
    fun indexMitBrueckenSkript() {
        val index = resources.resource("index.html")
        assertEquals(200, index.status)
        assertEquals("text/html", index.mimeType)
        val html = index.bytes.toString(Charsets.UTF_8)
        // Das Brücken-Skript muss vor allen Skripten der Seite laufen
        assertTrue(html.contains("<head>\n<script>window.__test = 1</script>"))
        assertTrue(html.indexOf("window.__test") < html.indexOf("<script type=\"module\""))
    }

    /** Alle Dateien, auf die die Seite verweist, sind im Plugin und haben den richtigen Typ. */
    @Test
    fun verweiseDerIndexSindVorhanden() {
        val html = resources.resource("index.html").bytes.toString(Charsets.UTF_8)
        val paths = Regex("""(?:src|href)="/?([^"]+)"""").findAll(html).map { it.groupValues[1] }.filterNot { it.startsWith("http") }.toList()
        assertTrue(paths.isNotEmpty())
        for (path in paths) {
            val resource = resources.resource(path)
            assertEquals(path, 200, resource.status)
            when (path.substringAfterLast('.')) {
                "js" -> assertEquals("text/javascript", resource.mimeType)
                "css" -> assertEquals("text/css", resource.mimeType)
            }
        }
    }

    @Test
    fun fehlendeDatei() {
        val missing = resources.resource("assets/gibt-es-nicht.js")
        assertEquals(404, missing.status)
        assertEquals("Not found: assets/gibt-es-nicht.js", missing.bytes.toString(Charsets.UTF_8))
    }

    /** Kein Zugriff auf andere Ressourcen des Plugins oder der IDE. */
    @Test
    fun keinAusbruchAusDemVerzeichnis() {
        assertEquals(404, resources.resource("../META-INF/plugin.xml").status)
        assertEquals(404, resources.resource("assets/../../META-INF/plugin.xml").status)
    }
}
