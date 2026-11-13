// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import br.dev.edgard.easygamepad.protocol.ControllerConfig
import br.dev.edgard.easygamepad.protocol.Macro
import br.dev.edgard.easygamepad.protocol.PadKey
import br.dev.edgard.easygamepad.protocol.ProfileIssueKind
import br.dev.edgard.easygamepad.protocol.StickSettings
import br.dev.edgard.easygamepad.protocol.Turbo
import br.dev.edgard.easygamepad.protocol.profileValidationIssues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilesEditorRedesignTest {
    @Test fun stickRangeRejectsInvertedMinMax() {
        val issues = ControllerConfig(leftStick = StickSettings(min = 60, max = 40)).profileValidationIssues()
        assertTrue(issues.any { it.kind == ProfileIssueKind.STICK_RANGE })
    }

    @Test fun turboRateOutOfRangeReportsRateIssueNotDuplicateKey() {
        val issues = ControllerConfig(turbos = listOf(Turbo(rate = 0))).profileValidationIssues()
        assertEquals(ProfileIssueKind.TURBO_RATE, issues.single().kind)
    }

    @Test fun duplicateMacroTriggersAreFlagged() {
        val macros = listOf(Macro(PadKey.M1), Macro(PadKey.M1, steps = listOf(br.dev.edgard.easygamepad.protocol.MacroStep(PadKey.B))))
        val issues = ControllerConfig(macros = macros).profileValidationIssues()
        assertTrue(issues.any { it.kind == ProfileIssueKind.DUPLICATE_MACRO_TRIGGER })
    }

    @Test fun vibrationPreviewGuardNeedsConfigurationReady() {
        val disconnected = ScreenUiFixtures.profilesDisconnectedValid()
        assertFalse(disconnected.configurationReady)
    }
}
