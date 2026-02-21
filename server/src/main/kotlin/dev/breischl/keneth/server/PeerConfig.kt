package dev.breischl.keneth.server

import dev.breischl.keneth.transport.SocketTransport

/**
 * Configuration for a known peer.
 *
 * @property peerId Unique identifier for this peer.
 * @property host Hostname or IP to connect to (required for [PeerDirection.OUTBOUND] and [PeerDirection.BIDIRECTIONAL]).
 * @property port TCP port to connect to.
 * @property direction Whether this peer connects to us, we connect to it, or both.
 * @property expectedIdentity The [dev.breischl.keneth.core.messages.SessionParameters.identity]
 *   we expect from this peer during handshake. Defaults to [peerId] if not set.
 */
data class PeerConfig(
    val peerId: String,
    val host: String? = null,
    val port: Int = SocketTransport.DEFAULT_PORT,
    val direction: PeerDirection,
    val expectedIdentity: String? = null,
) {
    /** The identity to match against incoming [dev.breischl.keneth.core.messages.SessionParameters]. */
    val resolvedExpectedIdentity: String get() = expectedIdentity ?: peerId
}
