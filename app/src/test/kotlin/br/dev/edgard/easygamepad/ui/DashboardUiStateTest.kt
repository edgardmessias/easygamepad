// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import org.junit.Assert.*
import org.junit.Test

class DashboardUiStateTest {
    @Test fun emptyHomeWhenNoSelection() {
        assertTrue(DashboardUiState().emptyHome)
        assertFalse(DashboardUiState(selected = ScreenUiFixtures.savedX10Disconnected).emptyHome)
    }

    @Test fun connectCardHiddenWhenAlreadyLinked() {
        val ready = DashboardUiState(
            selected = ScreenUiFixtures.savedX10Disconnected,
            status = br.dev.edgard.easygamepad.data.ControllerStatus(false, true, false, 85, false),
        )
        assertFalse(ready.showConnectCard)
    }

    @Test fun connectCardShownForLinkedButOfflineConfiguration() {
        val offline = DashboardUiState(
            selected = ScreenUiFixtures.savedX10Disconnected,
            status = br.dev.edgard.easygamepad.data.ControllerStatus(false, false, false, null, false),
        )
        assertTrue(offline.showConnectCard)
    }

    @Test fun connectBlockedWhileSwitchingOrConnecting() {
        val base = DashboardUiState(
            selected = ScreenUiFixtures.savedX10Disconnected,
            status = br.dev.edgard.easygamepad.data.ControllerStatus(false, false, true, null, false),
        )
        assertFalse(base.connectEnabled)
        assertFalse(base.copy(switching = true, status = base.status.copy(connecting = false)).connectEnabled)
        assertTrue(base.copy(switching = false, status = base.status.copy(connecting = false)).connectEnabled)
    }

    @Test fun noopActionsDoNotThrow() {
        val device = ScreenUiFixtures.savedX10Disconnected
        DashboardActions.noop.onOpenDeviceSheet()
        DashboardActions.noop.onConnectSelected()
        DashboardActions.noop.onChooseDevice(device)
        DashboardActions.noop.onCustomize(1)
    }
}
