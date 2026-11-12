// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.protocol

import org.junit.Assert.*
import org.junit.Test

class ControllerSettingsTest {
    private val content = bytes(3, 160, 1, 12, 4, 5, 95, 10, 90, 9, 2, 98, 1, 0, 3, 97, 0, 1, 0, 2, 60, 80)

    @Test fun realX10SettingsCaptureSpansTwoNotifications() {
        val pages = SettingsPages()
        assertNull(pages.accept(bytes(0x14, 0x77, 2, 2, 1, 0, 4, 3, 0x61, 3, 0x61, 9, 5, 0x64, 0, 0, 5, 0x64, 0, 1)))
        val result = pages.accept(bytes(0x0B, 0x77, 2, 2, 2, 0, 0, 2, 0x64, 0x64, 1))!!
        assertEquals(TriggerSettings(3, 97), result.leftTrigger)
        assertEquals(TriggerSettings(3, 97), result.rightTrigger)
        assertEquals(StickSettings(5, 100), result.leftStick)
        assertEquals(StickSettings(5, 100), result.rightStick)
        assertEquals(100, result.leftVibration); assertEquals(100, result.rightVibration)
        assertTrue(result.turbos.isEmpty())
    }

    @Test fun readSettingsDecodesLengthDelimitedSectionsWithoutGuessingDefaults() {
        val result = ControllerSettings.decode(content)
        assertEquals(TriggerSettings(5, 95), result.leftTrigger)
        assertEquals(TriggerSettings(10, 90), result.rightTrigger)
        assertEquals(StickSettings(2, 98, true, false), result.leftStick)
        assertEquals(StickSettings(3, 97, false, true), result.rightStick)
        assertEquals(60, result.leftVibration); assertEquals(80, result.rightVibration)
        assertEquals(listOf(Turbo(PadKey.A, true, 12)), result.turbos)
    }
    @Test fun mergingReadbackPreservesLocalNameAndMacroSequences() {
        val macro = Macro(PadKey.M3, steps = listOf(MacroStep(PadKey.B)))
        val draft = ControllerConfig(name = "Racing", macros = listOf(macro))
        val result = ControllerSettings.decode(content).copy(mappings = mapOf(PadKey.M1 to PadKey.X)).mergeInto(draft)
        assertEquals("Racing", result.name); assertEquals(listOf(macro), result.macros)
        assertEquals(mapOf(PadKey.M1 to PadKey.X), result.mappings)
        assertEquals(60, result.leftVibration)
        assertEquals("", ControllerSettings.decode(content).mergeInto(draft.copy(name = "")).name)
    }
    @Test fun settingsPagesWaitForAllFragments() {
        val pages = SettingsPages()
        assertNull(pages.accept(bytes(16, 119, 2, 2, 1) + content.copyOfRange(0, 10) + bytes(0)))
        val result = pages.accept(bytes(18, 119, 2, 2, 2) + content.copyOfRange(10, 22) + bytes(1))!!
        assertEquals(80, result.rightVibration)
    }
    @Test fun settingsPagesRejectGapsAndChangedPageCounts() {
        val pages = SettingsPages()
        pages.accept(bytes(7, 119, 2, 3, 1, 0, 0))
        assertThrows(IllegalArgumentException::class.java) { pages.accept(bytes(7, 119, 2, 3, 3, 0, 1)) }
    }
    @Test fun truncationInvalidRangesAndUnknownSwapsFailBeforeChangingDraft() {
        assertThrows(IllegalArgumentException::class.java) { ControllerSettings.decode(content.copyOfRange(0, 21)) }
        assertThrows(IllegalArgumentException::class.java) { ControllerSettings.decode(content.copyOf().apply { this[6] = 1 }) }
        assertThrows(IllegalArgumentException::class.java) { ControllerSettings.decode(content.copyOf().apply { this[18] = 3 }) }
        assertThrows(IllegalArgumentException::class.java) { ControllerSettings.decode(content + bytes(0)) }
    }
}
