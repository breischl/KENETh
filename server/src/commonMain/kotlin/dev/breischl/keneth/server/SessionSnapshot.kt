package dev.breischl.keneth.server

import dev.breischl.keneth.core.messages.SessionParameters
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Immutable snapshot of a session's state at a point in time, optionally linked to a [Peer].
 *
 * Unified session snapshot type. [peerId] is null for sessions not linked to any configured peer.
 *
 * @property sessionId The session's unique ID.
 * @property peerId The ID of the linked peer, or null if the session is not linked to a peer.
 * @property sessionParameters Device identity from the handshake, or null if not yet completed.
 * @property state Session lifecycle state at the time of the snapshot.
 * @property remoteIdentity The identity reported by the remote device, or null if not yet connected.
 * @property timestamp When the snapshot was taken.
 */
data class SessionSnapshot(
    val sessionId: String,
    val peerId: String?,
    val sessionParameters: SessionParameters?,
    val state: SessionState,
    val remoteIdentity: String?,
    val timestamp: Instant,
) {
    /** True only when the session state is [SessionState.ACTIVE]. */
    val isConnected: Boolean get() = state == SessionState.ACTIVE
}

@OptIn(ExperimentalTime::class)
internal fun DeviceSession.snapshot(peerId: String? = null) = SessionSnapshot(
    sessionId = id,
    peerId = peerId,
    sessionParameters = sessionParameters,
    state = state,
    remoteIdentity = sessionParameters?.identity,
    timestamp = Clock.System.now(),
)
