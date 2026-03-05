package dev.breischl.keneth.server

import dev.breischl.keneth.transport.MessageTransport
import dev.breischl.keneth.transport.TransportListener
import dev.breischl.keneth.transport.tcp.RawTcpClientTransport

internal actual fun EpNode.startPlatformSpecific() {
    config.listenPort?.let { port ->
        check(tcpAcceptor == null) { "Node already started" }
        val acc = TcpAcceptor(server, port, config.transportListener)
        acc.start()
        tcpAcceptor = acc
        _localPort = acc.localPort
    }
}

internal actual fun defaultOutboundFactory(
    listener: TransportListener?
): (suspend (String, Int) -> MessageTransport)? =
    { host, port -> MessageTransport(RawTcpClientTransport(host, port, listener)) }
