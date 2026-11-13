// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import br.dev.edgard.easygamepad.ble.LinkPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilesEditorConnectionUiTest {
    @Test fun readyHidesStatusLabel() {
        assertNull(profileEditorConfigurationChannelStatus(LinkPhase.READY, null, false))
        assertFalse(profileEditorConfigurationRowVisible(LinkPhase.READY, null))
    }

    @Test fun disconnectedUsesOfflineCopy() {
        assertEquals("App configuration: offline", profileEditorConfigurationChannelStatus(LinkPhase.DISCONNECTED, null, false))
        assertEquals("Configuração do app: offline", profileEditorConfigurationChannelStatus(LinkPhase.DISCONNECTED, null, true))
    }

    @Test fun inFlightPhasesUseConnectingFamilyNotOffline() {
        assertEquals("App configuration: connecting…", profileEditorConfigurationChannelStatus(LinkPhase.CONNECTING, null, false))
        assertEquals("App configuration: discovering services…", profileEditorConfigurationChannelStatus(LinkPhase.DISCOVERING, null, false))
        assertEquals("App configuration: preparing channel…", profileEditorConfigurationChannelStatus(LinkPhase.SUBSCRIBING, null, false))
    }

    @Test fun linkErrorOnDisconnectedSurfacesError() {
        assertEquals("App configuration: connection error", profileEditorConfigurationChannelStatus(LinkPhase.DISCONNECTED, "timeout", false))
        assertTrue(profileEditorConfigurationRowVisible(LinkPhase.DISCONNECTED, "timeout"))
    }

    @Test fun connectOnlyWhenDisconnectedAndIdle() {
        assertTrue(profileEditorConnectEnabled(LinkPhase.DISCONNECTED, switching = false, operationBusy = false))
        assertFalse(profileEditorConnectEnabled(LinkPhase.CONNECTING, switching = false, operationBusy = false))
        assertFalse(profileEditorConnectEnabled(LinkPhase.DISCOVERING, switching = false, operationBusy = false))
        assertFalse(profileEditorConnectEnabled(LinkPhase.SUBSCRIBING, switching = false, operationBusy = false))
        assertFalse(profileEditorConnectEnabled(LinkPhase.DISCONNECTED, switching = true, operationBusy = false))
        assertFalse(profileEditorConnectEnabled(LinkPhase.DISCONNECTED, switching = false, operationBusy = true))
    }
}
