package app.heckit.ide.latherit

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.net.ssl.CertificateManager
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Zertifikatsprüfung wie im Browser und in der Desktop-App: Die IDE vertraut nur Javas eigener Liste,
 * selbst eingetragenen und per Dialog akzeptierten Zertifikaten - nicht den Stammzertifikaten, die
 * macOS bzw. Windows mitbringen (z. B. „Certum Trusted Root CA“). Daher zuerst die Systemwurzeln,
 * danach wie gewohnt die IDE (einschließlich ihrer Rückfrage bei unbekannten Zertifikaten).
 */
object SystemTrust {
    private val log = logger<SystemTrust>()

    fun sslContext(): SSLContext {
        val ide = CertificateManager.getInstance().trustManager
        val system = systemTrustManager() ?: return CertificateManager.getInstance().sslContext
        return SSLContext.getInstance("TLS").apply { init(null, arrayOf(FirstTrusting(system, ide)), null) }
    }

    /** Stammzertifikate des Betriebssystems (Linux: keine eigene Quelle, dort genügt die IDE). */
    private fun systemTrustManager(): X509TrustManager? {
        val type = when {
            SystemInfo.isMac -> "KeychainStore-ROOT"
            SystemInfo.isWindows -> "Windows-ROOT"
            else -> return null
        }
        return try {
            val store = KeyStore.getInstance(type).apply { load(null, null) }
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
            factory.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
        } catch (e: Exception) {
            log.warn("Stammzertifikate des Systems ($type) nicht lesbar", e)
            null
        }
    }

    /**
     * Gilt als vertrauenswürdig, wenn eine der Prüfungen zustimmt. Bewusst nur `X509TrustManager`:
     * Java ergänzt dann selbst die Prüfung des Hostnamens.
     */
    private class FirstTrusting(private val first: X509TrustManager, private val second: X509TrustManager) : X509TrustManager {
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            try {
                first.checkServerTrusted(chain, authType)
            } catch (_: CertificateException) {
                second.checkServerTrusted(chain, authType)
            }
        }

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
            second.checkClientTrusted(chain, authType)

        override fun getAcceptedIssuers(): Array<X509Certificate> = first.acceptedIssuers + second.acceptedIssuers
    }
}
