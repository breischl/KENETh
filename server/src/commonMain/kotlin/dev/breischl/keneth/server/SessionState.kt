package dev.breischl.keneth.server

/**
 * Lifecycle state of a [DeviceSession].
 *
 * State transitions:
 * ```
 * AWAITING_SESSION → ACTIVE → DISCONNECTING → CLOSED
 *                  ↘                         ↗
 *                    ──── CLOSED ────────────
 * ```
 */
enum class SessionState {
    /** Connection accepted, waiting for the device to send [dev.breischl.keneth.core.messages.SessionParameters]. */
    AWAITING_SESSION,

    /** Handshake complete, session is active. */
    ACTIVE,

    /** [dev.breischl.keneth.core.messages.SoftDisconnect] sent or received, winding down. */
    DISCONNECTING,

    /** Session is closed and will be removed. */
    CLOSED
}
