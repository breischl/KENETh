package dev.breischl.keneth.server

import dev.breischl.keneth.transport.MessageTransport
import dev.breischl.keneth.transport.TransportListener

internal actual fun EpNode.startPlatformSpecific() {
    if (config.listenPort != null) {
        TODO("TCP listening is not yet implemented for the Native target")
    }
}

internal actual fun defaultOutboundFactory(
    listener: TransportListener?
): (suspend (String, Int) -> MessageTransport)? = { _, _ ->
    TODO("TCP outbound connections are not yet implemented for the Native target")
}
