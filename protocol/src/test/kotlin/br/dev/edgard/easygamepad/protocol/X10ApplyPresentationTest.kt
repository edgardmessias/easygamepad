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
    ) = ControllerSettings(
        mappings = mappings, turbos = turbos, leftVibration = leftVibration, rightVibration = rightVibration,
        leftTrigger = leftTrigger, rightTrigger = rightTrigger, leftStick = leftStick, rightStick = rightStick,
    )

    @Test fun matchedListsReadableFieldsWithoutMacroClaims() {
        val settings = requested.asSettings()
        val view = x10ApplyPresentation(requested, X10ApplyResult.Matched(settings))
        assertEquals(X10ApplyOutcomeKind.MATCHED, view.kind)
        assertTrue(view.matchedFields.contains(X10SettingField.VIBRATION))
        assertFalse(view.macrosChecked)
        assertFalse(view.persistenceChecked)
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

    @Test fun unconfirmedCarriesReason() {
        val view = x10ApplyPresentation(requested, X10ApplyResult.Unconfirmed("timeout"))
        assertEquals(X10ApplyOutcomeKind.UNCONFIRMED, view.kind)
        assertEquals("timeout", view.unconfirmedReason)
    }

    @Test fun staleRearMappingSurfacesAsButtonsMismatch() {
        val identity = ControllerConfig().mappings
        val sent = ControllerConfig(mappings = identity, turbos = requested.turbos)
        val settings = sent.asSettings(mappings = identity + (PadKey.M1 to PadKey.A))
        val fields = settings.differences(sent)
        assertEquals(setOf(X10SettingField.BUTTONS), fields)
        val view = x10ApplyPresentation(sent, X10ApplyResult.Mismatch(settings, fields))
        assertTrue(view.fieldNotes[X10SettingField.BUTTONS]!!.contains("M1"))
    }

    @Test fun independentMotorMismatchListsBothSides() {
        val settings = requested.asSettings(leftVibration = 10, rightVibration = 99)
        val view = x10ApplyPresentation(requested, X10ApplyResult.Mismatch(settings, setOf(X10SettingField.VIBRATION)))
        val note = view.fieldNotes[X10SettingField.VIBRATION]!!
        assertTrue(note.contains("40") && note.contains("55") && note.contains("10") && note.contains("99"))
    }
}
