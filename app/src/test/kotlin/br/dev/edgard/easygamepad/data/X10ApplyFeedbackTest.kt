// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.protocol.ControllerConfig
import br.dev.edgard.easygamepad.protocol.ControllerSettings
import br.dev.edgard.easygamepad.protocol.Macro
import br.dev.edgard.easygamepad.protocol.MacroStep
import br.dev.edgard.easygamepad.protocol.PadKey
import br.dev.edgard.easygamepad.protocol.X10ApplyResult
import br.dev.edgard.easygamepad.protocol.X10SettingField
import org.junit.Assert.*
import org.junit.Test

class X10ApplyFeedbackTest {
    @Test fun matchedNoticeDoesNotClaimMacrosUnchecked() {
        val config = ControllerConfig(macros = listOf(Macro(PadKey.M1, steps = listOf(MacroStep(PadKey.A)))))
        val notice = x10ApplyNotice(
            X10ApplyResult.Matched(
                ControllerSettings(
                    leftTrigger = config.leftTrigger,
                    rightTrigger = config.rightTrigger,
                    leftStick = config.leftStick,
                    rightStick = config.rightStick,
                    leftVibration = config.leftVibration,
                    rightVibration = config.rightVibration,
                    turbos = config.turbos,
                    macros = config.macros,
                ),
            ),
        )
        assertFalse(notice.english.contains("not checked", ignoreCase = true))
        assertFalse(notice.portuguese.contains("não foram verificadas", ignoreCase = true))
        assertTrue(notice.english.contains("match", ignoreCase = true))
        assertTrue(notice.portuguese.contains("conferem", ignoreCase = true))
        assertTrue(notice.english.contains("Power-off", ignoreCase = true))
        assertTrue(notice.portuguese.contains("desligar", ignoreCase = true))
    }

    @Test fun mismatchNoticeListsFieldsInPortuguese() {
        val notice = x10ApplyNotice(
            X10ApplyResult.Mismatch(
                ControllerSettings(
                    leftTrigger = ControllerConfig().leftTrigger,
                    rightTrigger = ControllerConfig().rightTrigger,
                    leftStick = ControllerConfig().leftStick,
                    rightStick = ControllerConfig().rightStick,
                    leftVibration = 0,
                    rightVibration = 0,
                    turbos = emptyList(),
                ),
                setOf(X10SettingField.BUTTONS, X10SettingField.TURBO),
            ),
        )
        assertTrue(notice.portuguese.contains("botões"))
        assertTrue(notice.portuguese.contains("turbo"))
        assertTrue(notice.error)
    }
}
