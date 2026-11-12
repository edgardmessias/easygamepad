// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.protocol

import org.junit.Assert.*
import org.junit.Test

class X10ApplyPresentationTest {
    private val requested = ControllerConfig(leftVibration = 40, rightVibration = 55, turbos = listOf(Turbo(PadKey.A, rate = 10)))

    private fun ControllerConfig.asSettings(
        mappings: Map<PadKey, PadKey> = this.mappings,
        turbos: List<Turbo> = this.turbos,
        leftVibration: Int = this.leftVibration,
        rightVibration: Int = this.rightVibration,
        macros: List<Macro> = this.macros,
        disabledRearPaddles: Set<PadKey> = this.disabledRearPaddles,
    ) = ControllerSettings(
        mappings = mappings, turbos = turbos, leftVibration = leftVibration, rightVibration = rightVibration,
        leftTrigger = leftTrigger, rightTrigger = rightTrigger, leftStick = leftStick, rightStick = rightStick,
        macros = macros,
        disabledRearPaddles = disabledRearPaddles,
    )

    @Test fun matchedListsReadableFieldsWithoutRearApply() {
        val settings = requested.asSettings()
        val view = x10ApplyPresentation(requested, X10ApplyResult.Matched(settings))
        assertEquals(X10ApplyOutcomeKind.MATCHED, view.kind)
        assertTrue(view.matchedFields.contains(X10SettingField.VIBRATION))
        assertFalse(view.rearConfigSent)
        assertTrue(view.readBackCompleted)
        assertFalse(view.macrosChecked)
        assertFalse(view.persistenceChecked)
    }

    @Test fun matchedWithRearMacroMarksComparedReadBack() {
        val config = requested.copy(macros = listOf(Macro(PadKey.M1, steps = listOf(MacroStep(PadKey.A)))))
        val settings = config.asSettings(macros = config.macros)
        val view = x10ApplyPresentation(config, X10ApplyResult.Matched(settings))
        assertTrue(view.rearConfigSent)
        assertTrue(view.macrosChecked)
        assertTrue(view.readBackCompleted)
    }

    @Test fun mismatchWithRearSentStillMarksReadBackCompared() {
        val config = requested.copy(macros = listOf(Macro(PadKey.M1, steps = listOf(MacroStep(PadKey.A)))))
        val settings = config.asSettings(leftVibration = 5)
        val fields = setOf(X10SettingField.VIBRATION)
        val view = x10ApplyPresentation(config, X10ApplyResult.Mismatch(settings, fields))
        assertTrue(view.rearConfigSent)
        assertTrue(view.macrosChecked)
        assertNull(view.buttonsMismatch)
    }

    @Test fun unconfirmedKeepsRearSentWithoutFalseComparedClaim() {
        val config = requested.copy(disabledRearPaddles = setOf(PadKey.M2))
        val view = x10ApplyPresentation(config, X10ApplyResult.Unconfirmed(X10ApplyReadBackDiagnostics.TIMED_OUT))
        assertEquals(X10ApplyOutcomeKind.UNCONFIRMED, view.kind)
        assertTrue(view.rearConfigSent)
        assertFalse(view.readBackCompleted)
        assertFalse(view.macrosChecked)
        assertEquals(X10ApplyReadBackDiagnostics.TIMED_OUT, view.unconfirmedReason)
    }

    @Test fun identityMappingsDoNotSurfaceAsButtonMismatch() {
        val identity = PadKey.outputs.associateWith { it }
        val config = ControllerConfig(mappings = identity)
        val settings = config.asSettings(mappings = identity)
        val fields = settings.differences(config)
        assertTrue(fields.isEmpty())
    }

    @Test fun turboReorderStillMatchesAsSet() {
        val turbos = listOf(Turbo(PadKey.A, rate = 5), Turbo(PadKey.B, automatic = true, rate = 8))
        val config = ControllerConfig(turbos = turbos)
        val settings = config.asSettings(turbos = turbos.reversed())
        assertFalse(settings.differences(config).contains(X10SettingField.TURBO))
    }

    @Test fun vibrationMismatchGetsMotorNote() {
        val settings = requested.asSettings(leftVibration = 10)
        val view = x10ApplyPresentation(requested, X10ApplyResult.Mismatch(settings, setOf(X10SettingField.VIBRATION)))
        assertEquals(X10ApplyOutcomeKind.MISMATCH, view.kind)
        assertTrue(view.fieldNotes[X10SettingField.VIBRATION]!!.contains("40"))
        assertTrue(view.fieldNotes[X10SettingField.VIBRATION]!!.contains("10"))
    }

    @Test fun staleRearMappingSurfacesAsButtonsMismatch() {
        val sent = ControllerConfig(mappings = mapOf(PadKey.B to PadKey.X), turbos = requested.turbos)
        val settings = sent.asSettings(mappings = sent.mappings + (PadKey.M1 to PadKey.A))
        val fields = settings.differences(sent)
        assertEquals(setOf(X10SettingField.BUTTONS), fields)
        val view = x10ApplyPresentation(sent, X10ApplyResult.Mismatch(settings, fields))
        assertNotNull(view.buttonsMismatch)
        assertFalse(view.rearConfigSent)
        assertTrue(view.readBackCompleted)
        assertTrue(view.fieldNotes[X10SettingField.BUTTONS]!!.contains("M1"))
    }

    @Test fun independentMotorMismatchListsBothSides() {
        val settings = requested.asSettings(leftVibration = 10, rightVibration = 99)
        val view = x10ApplyPresentation(requested, X10ApplyResult.Mismatch(settings, setOf(X10SettingField.VIBRATION)))
        val note = view.fieldNotes[X10SettingField.VIBRATION]!!
        assertTrue(note.contains("40") && note.contains("55") && note.contains("10") && note.contains("99"))
    }

    @Test fun rearMacroMismatchBuildsStructuredDetail() {
        val sent = ControllerConfig(macros = listOf(Macro(PadKey.M1, steps = listOf(MacroStep(PadKey.A)))))
        val settings = sent.asSettings(macros = emptyList())
        val fields = settings.differences(sent)
        assertEquals(setOf(X10SettingField.BUTTONS), fields)
        val view = x10ApplyPresentation(sent, X10ApplyResult.Mismatch(settings, fields))
        val detail = requireNotNull(view.buttonsMismatch)
        assertTrue(detail.rearMacrosDiffer)
        assertEquals(listOf(PadKey.M1), detail.sentRearMacroTriggers)
        assertTrue(detail.readRearMacroTriggers.isEmpty())
    }

    @Test fun sameTriggerSequenceAndTimingDifferencesAreIdentified() {
        val macro = Macro(PadKey.M1, steps = listOf(MacroStep(PadKey.A)))
        val sent = requested.copy(macros = listOf(macro))
        for (step in listOf(MacroStep(PadKey.B), MacroStep(PadKey.A, holdMs = 250), MacroStep(PadKey.A, delayMs = 250))) {
            val settings = sent.asSettings(macros = listOf(macro.copy(steps = listOf(step))))
            val view = x10ApplyPresentation(sent, X10ApplyResult.Mismatch(settings, settings.differences(sent)))
            val detail = requireNotNull(view.buttonsMismatch)
            assertEquals(setOf(PadKey.M1), detail.changedRearSequences)
            assertTrue(detail.changedRearRepeat.isEmpty())
        }
    }

    @Test fun sameTriggerRepeatDifferenceIsIdentifiedSeparately() {
        val macro = Macro(PadKey.M1, steps = listOf(MacroStep(PadKey.A)))
        val sent = requested.copy(macros = listOf(macro))
        val settings = sent.asSettings(macros = listOf(macro.copy(repeat = true)))
        val view = x10ApplyPresentation(sent, X10ApplyResult.Mismatch(settings, settings.differences(sent)))
        val detail = requireNotNull(view.buttonsMismatch)
        assertEquals(setOf(PadKey.M1), detail.changedRearRepeat)
        assertTrue(detail.changedRearSequences.isEmpty())
    }

    @Test fun reorderedRearMacrosDoNotCreateFalseMacroDetail() {
        val macros = listOf(
            Macro(PadKey.M1, steps = listOf(MacroStep(PadKey.A))),
            Macro(PadKey.M2, steps = listOf(MacroStep(PadKey.B))),
        )
        val sent = requested.copy(macros = macros)
        val settings = sent.asSettings(macros = macros.reversed(), mappings = mapOf(PadKey.A to PadKey.Y))
        val view = x10ApplyPresentation(sent, X10ApplyResult.Mismatch(settings, settings.differences(sent)))
        val detail = requireNotNull(view.buttonsMismatch)
        assertFalse(detail.rearMacrosDiffer)
        assertTrue(detail.changedRearSequences.isEmpty())
        assertTrue(detail.changedRearRepeat.isEmpty())
    }
}
