package dev.breischl.keneth.server

import dev.breischl.keneth.transport.PeerConnector

/**
 * Configuration for a known peer.
 *
 * Use [Inbound] for peers that connect to us, and [Outbound] for peers we connect to.
 */
sealed class PeerConfig {
    /** Unique identifier for this peer. */
    abstract val peerId: String

    /**
     * The [dev.breischl.keneth.core.messages.SessionParameters.identity] we expect from
     * this peer during handshake. Defaults to [peerId] if not set.
     */
    abstract val expectedIdentity: String?

    /** The identity to match against incoming [dev.breischl.keneth.core.messages.SessionParameters]. */
    val resolvedExpectedIdentity: String get() = expectedIdentity ?: peerId

    /**
     * An inbound-only peer that connects to us.
     *
     * @property peerId Unique identifier for this peer.
     * @property expectedIdentity Expected identity during handshake; defaults to [peerId].
     */
    data class Inbound(
        override val peerId: String,
        override val expectedIdentity: String? = null,
    ) : PeerConfig()

    /**
     * An outbound peer that we connect to.
     *
     * @property peerId Unique identifier for this peer.
     * @property connector Strategy for establishing the outbound connection.
     * @property expectedIdentity Expected identity during handshake; defaults to [peerId].
     * @property acceptInbound If true, also accepts inbound connections from this peer.
     */
    data class Outbound(
        override val peerId: String,
        val connector: PeerConnector,
        override val expectedIdentity: String? = null,
        val acceptInbound: Boolean = false,
    ) : PeerConfig()
}
