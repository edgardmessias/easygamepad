// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.demo

import br.dev.edgard.easygamepad.ble.LinkPhase

/**
 * Pure decisions for debug ADB demo lifecycle commands.
 *
 * Invariant: a saved or HID-selected physical controller, or any in-flight real BLE
 * configuration session, blocks demo [DemoAdbConnectAction] until the user selects the
 * demo entry (or clears selection on the welcome screen) and real BLE is idle.
 * ADB connect may auto-select the demo only when restore finished and [selectedId] is null (welcome).
 */
data class DemoLinkSnapshot(val phase: LinkPhase, val address: String? = null)

enum class DemoAdbConnectAction {
    BLOCKED,
    /** Welcome or no selection: switch selection to demo (ViewModel disconnects real BLE only when changing selection). */
    SELECT_DEMO,
    /** Demo already selected: open the in-process demo route. */
    CONNECT_DEMO,
    /** Demo selected and demo link already ready. */
    NOOP,
}

object DemoAdbLifecyclePolicy {
    fun isPhysicalSelection(selectedId: String?): Boolean =
        selectedId != null && selectedId != DemoController.ID

    /** True while the BLE configuration route is not idle (any phase after disconnect). */
    fun realBleConfigurationActive(usesDemo: Boolean, link: DemoLinkSnapshot): Boolean {
        if (usesDemo) return false
        return link.phase != LinkPhase.DISCONNECTED
    }

    fun blocksDemoAdbLifecycle(
        selectedId: String?,
        selectionRestoreComplete: Boolean,
        usesDemo: Boolean,
        link: DemoLinkSnapshot,
    ): Boolean =
        (!selectionRestoreComplete && selectedId == null) ||
            isPhysicalSelection(selectedId) ||
            realBleConfigurationActive(usesDemo, link)

    fun decideConnect(
        canSwitch: Boolean,
        selectedId: String?,
        selectionRestoreComplete: Boolean,
        usesDemo: Boolean,
        link: DemoLinkSnapshot,
    ): DemoAdbConnectAction {
        if (!canSwitch) return DemoAdbConnectAction.BLOCKED
        if (blocksDemoAdbLifecycle(selectedId, selectionRestoreComplete, usesDemo, link)) {
            return DemoAdbConnectAction.BLOCKED
        }
        return when (selectedId) {
            null -> DemoAdbConnectAction.SELECT_DEMO
            DemoController.ID -> when {
                link.phase == LinkPhase.READY && usesDemo && DemoController.isDemoAddress(link.address) ->
                    DemoAdbConnectAction.NOOP
                else -> DemoAdbConnectAction.CONNECT_DEMO
            }
            else -> DemoAdbConnectAction.BLOCKED
        }
    }

    fun allowDisconnect(canSwitch: Boolean, selectedId: String?, usesDemo: Boolean): Boolean {
        if (!canSwitch) return false
        if (selectedId == DemoController.ID) return true
        return usesDemo
    }

    fun allowFactoryReset(canSwitch: Boolean, selectedId: String?, usesDemo: Boolean, link: DemoLinkSnapshot): Boolean {
        if (!canSwitch) return false
        if (selectedId != DemoController.ID) return false
        if (!usesDemo) return false
        if (link.phase != LinkPhase.READY) return false
        if (!DemoController.isDemoAddress(link.address)) return false
        return true
    }
}
