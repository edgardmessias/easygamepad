// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilitySemanticsTest {
    @Test
    fun statusStateIsAnnouncedWithoutRelyingOnColor() {
        assertEquals("Active", AccessibilitySemantics.statusState(active = true, portuguese = false))
        assertEquals("Inativo", AccessibilitySemantics.statusState(active = false, portuguese = true))
    }

    @Test
    fun vibrationSliderAnnouncesPreviewWithoutStartingHardware() {
        val label = AccessibilitySemantics.vibrationSliderLabel("Left motor", 40, "%", previewing = true, portuguese = false)
        assertTrue(label.contains("Left motor"))
        assertTrue(label.contains("vibration preview active"))
    }

    @Test
    fun applyDisabledReasonsExplainNextStep() {
        val busy = ScreenUiFixtures.profilesBusyOnReady()
        assertEquals("Wait for the current controller operation.", AccessibilitySemantics.profilesApplyDisabledReason(busy, false))
        val cal = ScreenUiFixtures.profilesCalibrationStop()
        assertTrue(AccessibilitySemantics.profilesApplyDisabledReason(cal, false)!!.contains("calibration"))
        val ready = ScreenUiFixtures.profilesReadyToApply()
        assertNull(AccessibilitySemantics.profilesApplyDisabledReason(ready, false))
    }

    @Test
    fun saveDisabledReasonMentionsValidation() {
        val invalid = ScreenUiFixtures.profilesReadyToApply().copy(draftValid = false)
        assertEquals("Fix invalid profile values.", AccessibilitySemantics.profilesSaveDisabledReason(invalid, false))
    }

    @Test
    fun readStaysAvailableWhenDraftInvalidButApplyIsBlocked() {
        val invalid = ScreenUiFixtures.profilesReadyToApply().copy(draftValid = false)
        assertNull(AccessibilitySemantics.profilesReadDisabledReason(invalid, false))
        assertTrue(AccessibilitySemantics.profilesApplyDisabledReason(invalid, false)!!.contains("applying"))
    }
}
