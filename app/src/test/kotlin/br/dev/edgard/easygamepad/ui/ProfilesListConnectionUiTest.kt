// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import br.dev.edgard.easygamepad.ble.LinkPhase
import br.dev.edgard.easygamepad.protocol.CalibrationState
import br.dev.edgard.easygamepad.protocol.ConfigurationProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilesListConnectionUiTest {
    private val device = ScreenUiFixtures.savedX10Disconnected

    private fun state(
        linkPhase: LinkPhase,
        linkAddress: String? = device.address,
        switching: Boolean = false,
        busy: Boolean = false,
        calibration: CalibrationState = CalibrationState(),
        draftValid: Boolean = true,
    ): ProfilesEditorUiState = profilesEditorUiState(
        device,
        linkPhase,
        linkAddress,
        ConfigurationProtocol.KRLY,
        switching,
        busy,
        calibration,
        draftValid,
    )

    @Test fun rowHiddenWhenConfigurationReady() {
        val ready = state(LinkPhase.READY)
        assertTrue(ready.configurationReady)
        assertFalse(profilesListConfigurationRowVisible(true, ready, null))
    }

    @Test fun rowVisibleWhenDisconnected() {
        val offline = state(LinkPhase.DISCONNECTED)
        assertTrue(profilesListConfigurationRowVisible(true, offline, null))
        assertEquals("App configuration: offline", profilesListConfigurationChannelStatus(offline, null, false))
    }

    @Test fun readyButBusyShowsBusyNotOffline() {
        val busyReady = state(LinkPhase.READY, busy = true)
        assertFalse(busyReady.configurationReady)
        assertTrue(profilesListConfigurationRowVisible(true, busyReady, null))
        assertEquals("Wait for the controller operation.", profilesListConfigurationChannelStatus(busyReady, null, false))
        assertNull(profileEditorConfigurationChannelStatus(LinkPhase.READY, null, false))
        assertFalse(profilesListConnectEnabled(busyReady, switching = false))
    }

    @Test fun readyWrongAddressShowsConnectHint() {
        val mismatched = state(LinkPhase.READY, linkAddress = "00:11:22:33:44:55")
        assertFalse(mismatched.configurationLinkSelected)
        assertTrue(profilesListConfigurationRowVisible(true, mismatched, null))
        assertEquals("Connect the selected controller in the app.", profilesListConfigurationChannelStatus(mismatched, null, false))
        assertFalse(profilesListConnectEnabled(mismatched, switching = false))
    }

    @Test fun connectEnabledOnlyWhenDisconnectedIdle() {
        val offline = state(LinkPhase.DISCONNECTED)
        assertTrue(profilesListConnectEnabled(offline, switching = false))
        assertFalse(profilesListConnectEnabled(offline, switching = true))
        assertFalse(profilesListConnectEnabled(state(LinkPhase.CONNECTING), switching = false))
        assertFalse(profilesListConnectEnabled(state(LinkPhase.DISCONNECTED, busy = true), switching = false))
    }

    @Test fun calibrationRequiresFinishingBeforeConnectingEvenIfLinkDropped() {
        val calibrating = state(LinkPhase.DISCONNECTED, calibration = CalibrationState(stopRequired = true))
        assertTrue(profilesListConfigurationRowVisible(true, calibrating, null))
        assertEquals("Finalize a calibração no controle.", profilesListConfigurationChannelStatus(calibrating, null, true))
        assertFalse(profilesListConnectEnabled(calibrating, switching = false))
    }
}
