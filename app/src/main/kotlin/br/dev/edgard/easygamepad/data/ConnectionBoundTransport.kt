// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.protocol.GamepadTransport
import kotlinx.coroutines.flow.filter

/** Routes transport I/O to a single connection epoch; stale epochs ignore notifications and abort. */
internal class ConnectionBoundTransport(
    private val backing: GamepadTransport,
    private val isCurrent: () -> Boolean,
) : GamepadTransport {
    override val notifications = backing.notifications.filter { isCurrent() }
    override suspend fun write(frame: ByteArray) {
        check(isCurrent()) { "Connection changed" }
        backing.write(frame)
    }
    override fun abort(cause: Throwable) {
        if (isCurrent()) backing.abort(cause)
    }
}
