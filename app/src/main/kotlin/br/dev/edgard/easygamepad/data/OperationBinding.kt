// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.ble.LinkPhase
import br.dev.edgard.easygamepad.ble.LinkState
import br.dev.edgard.easygamepad.protocol.ConfigurationProtocol

/** Snapshot taken when a long-running controller command starts. */
data class OperationBinding(
    val selectionRevision: Long,
    val deviceId: String?,
    val linkAddress: String,
    val protocol: ConfigurationProtocol,
    /** Draft generation when the operation started; used to ignore reads after local edits. */
    val draftEpoch: Long = 0L,
) {
    fun matchesResult(currentRevision: Long, currentDeviceId: String?, link: LinkState): Boolean =
        selectionRevision == currentRevision &&
            deviceId == currentDeviceId &&
            link.phase == LinkPhase.READY &&
            link.address == linkAddress &&
            link.protocol == protocol

    companion object {
        fun capture(
            selectionRevision: Long,
            deviceId: String?,
            link: LinkState,
            draftEpoch: Long = 0L,
        ): OperationBinding? {
            val address = link.address ?: return null
            if (link.phase != LinkPhase.READY) return null
            return OperationBinding(selectionRevision, deviceId, address, link.protocol, draftEpoch)
        }
    }
}
