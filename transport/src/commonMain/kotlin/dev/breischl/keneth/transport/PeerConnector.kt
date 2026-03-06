package dev.breischl.keneth.transport

/**
 * Strategy for establishing an outbound connection to a peer.
 *
 * Implementations provide transport-specific connection logic (TCP, BLE, in-memory, etc.),
 * decoupling [dev.breischl.keneth.server.EpServer] from the underlying network layer.
 */
interface PeerConnector {
    /**
     * Establish a connection and return a [MessageTransport] for communicating with the peer.
     *
     * @param listener Optional listener for transport lifecycle events.
     */
    suspend fun connect(listener: TransportListener?): MessageTransport
}
