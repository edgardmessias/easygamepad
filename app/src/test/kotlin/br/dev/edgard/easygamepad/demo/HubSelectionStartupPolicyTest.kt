// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.demo

import br.dev.edgard.easygamepad.ble.LinkPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HubSelectionStartupPolicyTest {
    private val physicalBle = "ble:AA:BB:CC:DD:EE:01"
    private val idle = DemoLinkSnapshot(LinkPhase.DISCONNECTED)

    @Test
    fun adbConnectBlockedUntilRestoreCompletes() {
        assertEquals(
            DemoAdbConnectAction.BLOCKED,
            DemoAdbLifecyclePolicy.decideConnect(true, null, selectionRestoreComplete = false, false, idle),
        )
        assertEquals(
            DemoAdbConnectAction.SELECT_DEMO,
            DemoAdbLifecyclePolicy.decideConnect(true, null, selectionRestoreComplete = true, false, idle),
        )
    }

    @Test
    fun interleavingAdbBeforeRestoreDoesNotWinOverPendingPhysicalRestore() {
        val restoreRevision = 0L
        assertFalse(
            HubSelectionStartupPolicy.shouldApplyRestoredSelection(restoreRevision, currentRevision = 1L, currentSelectedId = DemoController.ID),
        )
        assertEquals(
            DemoAdbConnectAction.BLOCKED,
            DemoAdbLifecyclePolicy.decideConnect(true, null, false, false, idle),
        )
    }

    @Test
    fun interleavingUserSelectionDuringRestoreIsNotOverwritten() {
        val restoreRevision = 0L
        assertFalse(
            HubSelectionStartupPolicy.shouldApplyRestoredSelection(restoreRevision, restoreRevision, currentSelectedId = physicalBle),
        )
        assertTrue(HubSelectionStartupPolicy.mayApplyLinkReadySelection(selectionRestoreComplete = false, physicalBle))
    }

    @Test
    fun restoredPhysicalSelectionAppliesWhenNoConcurrentChange() {
        assertTrue(
            HubSelectionStartupPolicy.shouldApplyRestoredSelection(0L, 0L, currentSelectedId = null),
        )
        assertFalse(HubSelectionStartupPolicy.shouldAutoPickFirstDevice(false, null, hasRows = true))
        assertTrue(HubSelectionStartupPolicy.shouldAutoPickFirstDevice(true, null, hasRows = true))
    }

    @Test
    fun linkCollectorIgnoresEarlyDemoReadyWhileRestorePendingAndUnselected() {
        assertFalse(HubSelectionStartupPolicy.mayApplyLinkReadySelection(false, null))
        assertTrue(HubSelectionStartupPolicy.mayApplyLinkReadySelection(false, DemoController.ID))
    }
}
