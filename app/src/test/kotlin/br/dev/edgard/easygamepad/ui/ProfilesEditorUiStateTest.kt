// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import br.dev.edgard.easygamepad.ble.LinkPhase
import org.junit.Assert.*
import org.junit.Test

class ProfilesEditorUiStateTest {
    @Test fun disconnectedDraftStillAllowsLocalSave() {
        val state = ScreenUiFixtures.profilesDisconnectedValid()
        assertTrue(state.canSaveProfile)
        assertFalse(state.configurationReady)
        assertFalse(state.canApplyOrReadFromDevice)
    }

    @Test fun busyBlocksApplyEvenWhenLinkReady() {
        val state = ScreenUiFixtures.profilesBusyOnReady()
        assertFalse(state.configurationReady)
        assertFalse(state.canApplyOrReadFromDevice)
    }

    @Test fun staleReadyLinkWithoutSelectedMatchBlocksHardwareActions() {
        val mismatched = ScreenUiFixtures.profilesReadyToApply().copy(configurationLinkSelected = false)
        assertFalse(mismatched.configurationReady)
        assertFalse(mismatched.canApplyToDevice)
        assertFalse(mismatched.canReadFromDevice)
    }

    @Test fun calibrationStopBlocksHardwareActions() {
        val state = ScreenUiFixtures.profilesCalibrationStop()
        assertFalse(state.canApplyOrReadFromDevice)
    }

    @Test fun invalidDraftBlocksSaveAndApply() {
        val ready = ScreenUiFixtures.profilesReadyToApply().copy(draftValid = false)
        assertFalse(ready.canSaveProfile)
        assertFalse(ready.canApplyToDevice)
        assertTrue(ready.canReadFromDevice)
    }

    @Test fun readyLinkAndValidDraftEnableApply() {
        val state = ScreenUiFixtures.profilesReadyToApply()
        assertTrue(state.configurationReady)
        assertTrue(state.canApplyOrReadFromDevice)
    }

    @Test fun disabledApplyReasonsStayStableAcrossLocales() {
        val busy = ScreenUiFixtures.profilesBusyOnReady()
        assertTrue(AccessibilitySemantics.profilesApplyDisabledReason(busy, true)!!.contains("operação"))
        assertTrue(AccessibilitySemantics.profilesApplyDisabledReason(busy, false)!!.contains("operation"))
    }
}
