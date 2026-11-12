// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.protocol

import org.junit.Assert.*
import org.junit.Test

class ControllerConfigValidationTest {
    @Test fun defaultConfigIsValid() {
        assertTrue(ControllerConfig().profileIsValid())
        assertTrue(ControllerConfig().profileValidationIssues().isEmpty())
    }

    @Test fun blankNameAndLongName() {
        val blank = ControllerConfig(name = " ")
        assertTrue(blank.profileValidationIssues().any { it.kind == ProfileIssueKind.BLANK_NAME })
        val long = ControllerConfig(name = "a".repeat(41))
        assertTrue(long.profileValidationIssues().any { it.kind == ProfileIssueKind.NAME_TOO_LONG })
    }

    @Test fun stickMinMustStayBelowMax() {
        val invalid = ControllerConfig(leftStick = StickSettings(min = 50, max = 40))
        assertTrue(invalid.profileValidationIssues().any { it.field == ProfileField.LEFT_STICK })
    }

    @Test fun duplicateMacroAndTurboKeysAreReported() {
        val macro = Macro(steps = listOf(MacroStep()))
        val dupMacro = ControllerConfig(macros = listOf(macro, macro.copy(repeat = true)))
        assertTrue(dupMacro.profileValidationIssues().any { it.kind == ProfileIssueKind.DUPLICATE_MACRO_TRIGGER })
        val turbo = Turbo()
        val dupTurbo = ControllerConfig(turbos = listOf(turbo, turbo.copy(automatic = true)))
        assertTrue(dupTurbo.profileValidationIssues().any { it.kind == ProfileIssueKind.DUPLICATE_TURBO_KEY })
    }

    @Test fun macroWithoutStepsBlocksSave() {
        val issues = ControllerConfig(macros = listOf(Macro())).profileValidationIssues()
        assertTrue(issues.any { it.kind == ProfileIssueKind.MACRO_NO_STEPS })
    }

    @Test fun macroStepTimingBounds() {
        val issues = ControllerConfig(macros = listOf(Macro(steps = listOf(MacroStep(holdMs = 0))))).profileValidationIssues()
        assertTrue(issues.any { it.kind == ProfileIssueKind.MACRO_STEP })
    }

    @Test fun disabledPaddleConflictsWithMappingOrMacro() {
        val conflict = ControllerConfig(
            disabledRearPaddles = setOf(PadKey.M1),
            macros = listOf(Macro(steps = listOf(MacroStep()))),
        )
        assertTrue(conflict.profileValidationIssues().any { it.kind == ProfileIssueKind.DISABLED_REAR_CONFLICT })
    }

    @Test fun validateThrowsMatchStructuredIssues() {
        val config = ControllerConfig(name = "")
        assertFalse(config.profileIsValid())
        try {
            config.validate()
            fail("expected validate() to throw")
        } catch (_: IllegalArgumentException) {
        }
    }
}
