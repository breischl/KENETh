package dev.breischl.keneth.server

/**
 * Strategy for accepting inbound connections and feeding them into an [EpServer].
 *
 * Implementations handle the transport-specific accept loop (TCP, BLE, in-memory, etc.).
 * Pass an instance via [NodeConfig.acceptor]; [EpNode] calls [start] when the node starts
 * and [close] when the node closes.
 */
interface InboundAcceptor : AutoCloseable {
    fun start(server: EpServer)
}
