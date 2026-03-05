package dev.breischl.keneth.transport.tls

import java.io.InputStream
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Configuration for TLS connections.
 *
 * Trust precedence in [createSslContext]:
 * 1. [insecureTrustAll] = true — uses a no-op trust manager that accepts all certificates
 * 2. [trustStore] != null — uses the provided custom trust store
 * 3. Otherwise — uses the system default trust store
 *
 * @property keyStore The client's key store containing the private key and certificate chain (for mTLS).
 * @property keyStorePassword The password for the key store's private key.
 * @property trustStore The trust store containing trusted CA certificates.
 * @property trustStorePassword The password for the trust store (if encrypted).
 * @property protocol The TLS protocol version to use (default: "TLSv1.3").
 * @property verifyHostname Whether to verify the server's hostname against its certificate.
 * @property insecureTrustAll When true, disables all certificate verification. For testing only.
 * @property clientAuth Server-side client authentication mode.
 */
@Suppress("ArrayInDataClass")
data class TlsConfig(
    val keyStore: KeyStore? = null,
    val keyStorePassword: CharArray? = null,
    val trustStore: KeyStore? = null,
    val trustStorePassword: CharArray? = null,
    val protocol: String = "TLSv1.3",
    val verifyHostname: Boolean = true,
    val insecureTrustAll: Boolean = false,
    val clientAuth: ClientAuth = ClientAuth.NONE
) {
    /**
     * Creates an SSLContext configured with this TLS configuration.
     */
    fun createSslContext(): SSLContext {
        val keyManagerFactory = if (keyStore != null) {
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore, keyStorePassword)
            }
        } else null

        val trustManagers = when {
            insecureTrustAll -> arrayOf(InsecureTrustManager)
            trustStore != null -> {
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                    init(trustStore)
                }.trustManagers
            }

            else -> {
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                    init(null as KeyStore?)
                }.trustManagers
            }
        }

        return SSLContext.getInstance(protocol).apply {
            init(keyManagerFactory?.keyManagers, trustManagers, null)
        }
    }

    companion object {
        /**
         * Loads a key store from an input stream.
         *
         * @param inputStream The input stream containing the key store data.
         * @param password The password for the key store.
         * @param type The key store type (default: "PKCS12").
         * @return The loaded KeyStore.
         */
        fun loadKeyStore(
            inputStream: InputStream,
            password: CharArray,
            type: String = "PKCS12"
        ): KeyStore {
            return KeyStore.getInstance(type).apply {
                load(inputStream, password)
            }
        }
    }
}

/** Server-side client authentication mode. */
enum class ClientAuth { NONE, WANT, NEED }

private object InsecureTrustManager : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}
