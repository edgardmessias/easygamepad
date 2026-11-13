// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import br.dev.edgard.easygamepad.ble.LinkPhase
import br.dev.edgard.easygamepad.data.EditorSaveBinding
import br.dev.edgard.easygamepad.data.SavedProfile
import br.dev.edgard.easygamepad.protocol.ConfigurationProtocol
import br.dev.edgard.easygamepad.protocol.ControllerConfig
import br.dev.edgard.easygamepad.protocol.DeviceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilesLandingNavigationTest {
    @Test fun listApplySnapshotIsIsolatedFromDraft() {
        val draft = ControllerConfig(name = "Workspace", leftVibration = 12)
        val profile = SavedProfile("p1", ControllerConfig(name = "Living room", leftVibration = 88))
        val snapshot = savedProfileApplySnapshot(profile)
        assertEquals(profile.config, snapshot)
        assertNotEquals(draft, snapshot)
    }

    @Test fun hubBackStaysEnabledForKeyLinkerWhileFlagStale() {
        assertTrue(hubProfilesTabBackToHomeEnabled(tab = 1, connectionSheet = false, profileEditorOpen = true, configurationEditor = false))
    }

    @Test fun hubBackSuppressedOnlyForOpenX10Editor() {
        assertFalse(hubProfilesTabBackToHomeEnabled(tab = 1, connectionSheet = false, profileEditorOpen = true, configurationEditor = true))
        assertTrue(hubProfilesTabBackToHomeEnabled(tab = 1, connectionSheet = false, profileEditorOpen = false, configurationEditor = true))
    }

    @Test fun customizeDoesNotOpenX10EditorForKeyLinker() {
        assertFalse(customizeOpensX10ProfileEditor(supportsKeyLinker = true, supportsConfiguration = false))
        assertFalse(customizeOpensX10ProfileEditor(supportsKeyLinker = true, supportsConfiguration = true))
        assertTrue(customizeOpensX10ProfileEditor(supportsKeyLinker = false, supportsConfiguration = true))
    }

    @Test fun applyBlockedWhenReadyLinkBelongsToAnotherAddress() {
        val selected = ScreenUiFixtures.savedX10Disconnected
        val readyOther = profilesConfigurationLinkSelected(
            selected,
            LinkPhase.READY,
            linkAddress = "00:11:22:33:44:55",
            linkProtocol = ConfigurationProtocol.KRLY,
            switching = false,
        )
        assertFalse(readyOther)
        val readyMatch = profilesConfigurationLinkSelected(
            selected,
            LinkPhase.READY,
            selected.address,
            ConfigurationProtocol.KRLY,
            switching = false,
        )
        assertTrue(readyMatch)
        val state = profilesEditorUiState(
            selected,
            LinkPhase.READY,
            "00:11:22:33:44:55",
            ConfigurationProtocol.KRLY,
            switching = false,
            busy = false,
            calibration = br.dev.edgard.easygamepad.protocol.CalibrationState(),
            draftValid = true,
        )
        assertFalse(state.canApplyToDevice)
    }

    @Test fun importDiscardsStalePickerBindingAfterEditorGenerationMoves() {
        val origin = EditorSaveBinding(1L, "ble:aa", linkedProfileId = null)
        assertTrue(origin.matchesActive(1L, "ble:aa"))
        assertFalse(origin.matchesActive(2L, "ble:aa"))
    }

    @Test fun switchingBlocksConfigurationAccessEvenWhenLinkReady() {
        val device = ScreenUiFixtures.savedX10Disconnected.copy(modelId = DeviceProfile.X10.id)
        val state = profilesEditorUiState(
            device,
            LinkPhase.READY,
            device.address,
            ConfigurationProtocol.KRLY,
            switching = true,
            busy = false,
            calibration = br.dev.edgard.easygamepad.protocol.CalibrationState(),
            draftValid = true,
        )
        assertFalse(state.configurationLinkSelected)
        assertFalse(state.canApplyToDevice)
    }
}
