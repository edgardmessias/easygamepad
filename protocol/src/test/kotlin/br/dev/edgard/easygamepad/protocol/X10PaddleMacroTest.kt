// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.protocol

import org.junit.Assert.*
import org.junit.Test

class X10PaddleMacroTest {
    private val factory = bytes(
        1, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 160,
        2, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 161,
    )

    @Test fun parseFactoryM1M2Passthrough() {
        val slots = X10PaddleMacroCodec.parseContent(factory)
        assertEquals(2, slots.size)
        assertEquals(PadKey.M1, slots[0].trigger)
        assertEquals(PadKey.M2, slots[1].trigger)
        val merged = X10PaddleMacroCodec.mergeFromSlots(emptyMap(), slots)
        assertEquals(mapOf(PadKey.M1 to PadKey.A, PadKey.M2 to PadKey.B), merged.mappings)
        assertTrue(merged.macros.isEmpty())
        assertTrue(merged.disabledRearPaddles.isEmpty())
    }

    @Test fun mergeMYFromSingleStep() {
        val m1y = bytes(1, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 163)
        val merged = X10PaddleMacroCodec.mergeFromSlots(emptyMap(), X10PaddleMacroCodec.parseContent(m1y))
        assertEquals(mapOf(PadKey.M1 to PadKey.Y), merged.mappings)
    }

    @Test fun emptyStepCountYieldsNoMapping() {
        val cleared = bytes(1, 1, 0, 0, 0, 0, 0)
        val merged = X10PaddleMacroCodec.mergeFromSlots(emptyMap(), X10PaddleMacroCodec.parseContent(cleared))
        assertTrue(merged.mappings.isEmpty())
        assertTrue(merged.macros.isEmpty())
        assertEquals(setOf(PadKey.M1), merged.disabledRearPaddles)
    }

    @Test fun clearStepEncodingIsSevenBytes() {
        assertArrayEquals(bytes(1, 1, 0, 0, 0, 0, 0), X10PaddleMacroCodec.clearStep(PadKey.M1))
    }

    @Test fun repeatFlagIsDecodedFromSlotHeader() {
        val repeating = bytes(1, 0, 0, 0, 0, 0, 1, 0, 0, 100, 0, 100, 1, 160)
        val slot = X10PaddleMacroCodec.parseContent(repeating).single()
        assertTrue(slot.repeat)
        val macro = slot.let { X10PaddleMacroCodec.mergeFromSlots(emptyMap(), listOf(slot)).macros.single() }
        assertTrue(macro.repeat)
    }

    @Test fun cmd80RearMappingDroppedWhen121SlotCleared() {
        val cleared = bytes(1, 1, 0, 0, 0, 0, 0)
        val merged = X10PaddleMacroCodec.mergeFromSlots(
            mapOf(PadKey.M1 to PadKey.A, PadKey.B to PadKey.X),
            X10PaddleMacroCodec.parseContent(cleared),
        )
        assertFalse(PadKey.M1 in merged.mappings)
        assertEquals(mapOf(PadKey.B to PadKey.X), merged.mappings)
        assertEquals(setOf(PadKey.M1), merged.disabledRearPaddles)
    }

    @Test fun cmd80RearMappingDroppedWhen121HasMacro() {
        val macroSlot = bytes(1, 0, 0, 0, 0, 0, 1, 0, 0, 100, 0, 100, 1, 160)
        val merged = X10PaddleMacroCodec.mergeFromSlots(
            mapOf(PadKey.M1 to PadKey.Y),
            X10PaddleMacroCodec.parseContent(macroSlot),
        )
        assertFalse(PadKey.M1 in merged.mappings)
        assertEquals(1, merged.macros.size)
        assertEquals(PadKey.M1, merged.macros.single().trigger)
    }

    @Test fun truncatedMacroBlobDoesNotHangParser() {
        val truncated = bytes(1, 1, 0, 0, 0, 0, 2, 0, 0, 0)
        X10PaddleMacroCodec.parseContent(truncated)
    }

    @Test fun disabledPaddleUploadSendsClear() {
        val plan = ControllerConfig(disabledRearPaddles = setOf(PadKey.M1)).x10UploadPlan()
        assertEquals(1, plan.commands.count { it.first == 58 })
        assertArrayEquals(X10PaddleMacroCodec.clearStep(PadKey.M1), plan.commands.single { it.first == 58 }.second)
        assertEquals(listOf(PadKey.M1), plan.clearedPaddles)
    }

    @Test fun withRearMacrosRemovedPaddleBecomesDisabled() {
        val withMacro = ControllerConfig(
            macros = listOf(Macro(PadKey.M1, steps = listOf(MacroStep(PadKey.A)))),
        )
        val afterRemove = withMacro.withRearMacros(emptyList())
        assertEquals(setOf(PadKey.M1), afterRemove.disabledRearPaddles)
        assertTrue(afterRemove.macros.isEmpty())
        assertArrayEquals(
            X10PaddleMacroCodec.clearStep(PadKey.M1),
            afterRemove.x10UploadPlan().commands.single { it.first == 58 }.second,
        )
    }

    @Test fun withRearMacrosDisabledPaddleStaysAssignable() {
        val disabled = ControllerConfig(
            disabledRearPaddles = setOf(PadKey.M1),
            macros = emptyList(),
        )
        val reassigned = disabled.withRearMacros(
            listOf(Macro(PadKey.M1, steps = listOf(MacroStep(PadKey.B)))),
        )
        assertTrue(reassigned.disabledRearPaddles.isEmpty())
        assertEquals(PadKey.M1, reassigned.macros.single().trigger)
    }

    @Test fun rearPaddlesNeedingMacroConfirmForImportedMacroPlusRemap() {
        val imported = ControllerConfig(
            mappings = mapOf(PadKey.M1 to PadKey.Y),
            macros = listOf(Macro(PadKey.M1, steps = listOf(MacroStep(PadKey.A)))),
        )
        assertEquals(setOf(PadKey.M1), imported.rearPaddlesNeedingMacroConfirm(imported.macros))
    }

    @Test fun rearPaddlesNeedingMacroConfirmOnlyForNewMacroOnMappedPaddle() {
        val remapped = ControllerConfig(mappings = mapOf(PadKey.M1 to PadKey.Y))
        assertEquals(setOf(PadKey.M1), remapped.rearPaddlesNeedingMacroConfirm(
            listOf(Macro(PadKey.M1, steps = listOf(MacroStep(PadKey.A)))),
        ))
        val withMacro = ControllerConfig(
            macros = listOf(Macro(PadKey.M1, steps = listOf(MacroStep(PadKey.A)))),
        )
        val moreSteps = withMacro.withRearMacros(
            listOf(Macro(PadKey.M1, steps = listOf(MacroStep(PadKey.A), MacroStep(PadKey.B)))),
        )
        assertTrue(moreSteps.rearPaddlesNeedingMacroConfirm(moreSteps.macros).isEmpty())
    }

    @Test fun macroMappingConflictCancelLeavesDraftUntouched() {
        val remapped = ControllerConfig(mappings = mapOf(PadKey.M1 to PadKey.Y))
        val next = listOf(Macro(PadKey.M1, steps = listOf(MacroStep(PadKey.A))))
        assertTrue(remapped.rearPaddlesNeedingMacroConfirm(next).isNotEmpty())
        assertEquals(mapOf(PadKey.M1 to PadKey.Y), remapped.mappings)
        assertTrue(remapped.macros.isEmpty())
    }

    @Test fun withRearRemapDropsMacroAndDisabled() {
        val mixed = ControllerConfig(
            mappings = emptyMap(),
            macros = listOf(Macro(PadKey.M1, steps = listOf(MacroStep(PadKey.A)))),
            disabledRearPaddles = setOf(PadKey.M2),
        )
        val remapped = mixed.withRearRemap(PadKey.M1, PadKey.Y)
        assertEquals(mapOf(PadKey.M1 to PadKey.Y), remapped.mappings)
        assertTrue(remapped.macros.isEmpty())
        assertEquals(setOf(PadKey.M2), remapped.disabledRearPaddles)
    }

    @Test fun withRearMacrosClearsConflictingRearRemap() {
        val remapped = ControllerConfig(
            mappings = mapOf(PadKey.M1 to PadKey.Y),
            disabledRearPaddles = setOf(PadKey.M2),
        )
        val withMacro = remapped.withRearMacros(
            listOf(Macro(PadKey.M1, steps = listOf(MacroStep(PadKey.A)))),
        )
        assertFalse(PadKey.M1 in withMacro.mappings)
        assertTrue(withMacro.disabledRearPaddles.contains(PadKey.M2))
        val plan = withMacro.x10UploadPlan()
        assertEquals(1, plan.macroTriggers.size)
        assertTrue(plan.commands.any { it.first == 58 && it.second.first().u8() == PadKey.M1.code })
    }
}
