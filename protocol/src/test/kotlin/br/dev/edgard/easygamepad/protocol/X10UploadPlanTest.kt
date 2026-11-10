// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.protocol

import org.junit.Assert.*
import org.junit.Test

class X10UploadPlanTest {
    @Test fun standardRestorationIsAnExplicitMappingWriteAndOmissionSendsNothing() {
        val plan = ControllerConfig(mappings = mapOf(PadKey.A to PadKey.A, PadKey.B to PadKey.X, PadKey.M1 to PadKey.Y)).x10UploadPlan()
        assertEquals(mapOf(PadKey.A to PadKey.A), plan.restoredMappings)
        assertEquals(mapOf(PadKey.B to PadKey.X, PadKey.M1 to PadKey.Y), plan.assignedMappings)
        assertTrue(PadKey.M2 in plan.omittedMappings)
        assertTrue(PadKey.X in plan.omittedMappings)
        val writes = plan.commands.filter { it.first == 61 }.map { it.second.map(Byte::u8) }
        assertEquals(listOf(listOf(160, 160), listOf(161, 162), listOf(1, 163)), writes)
    }

    @Test fun removingRulesFromTheProfileNeverInventsAnEraseCommand() {
        val original = ControllerConfig(mappings = mapOf(PadKey.M1 to PadKey.A),
            macros = listOf(Macro(steps = listOf(MacroStep()))), turbos = listOf(Turbo()))
        val originalPlan = original.x10UploadPlan()
        assertEquals(listOf(PadKey.M1), originalPlan.macroTriggers)
        assertEquals(listOf(PadKey.A), originalPlan.turboKeys)
        val removed = original.copy(mappings = emptyMap(), macros = emptyList(), turbos = emptyList()).x10UploadPlan()
        assertTrue(removed.macroTriggers.isEmpty()); assertTrue(removed.turboKeys.isEmpty())
        assertEquals(listOf(57, 62, 53, 52), removed.commands.map { it.first })
        assertTrue(PadKey.M1 in removed.omittedMappings)
    }

    @Test fun defaultsRestoreRegularOutputsWithoutPretendingToClearRearButtons() {
        val plan = ControllerConfig().x10UploadPlan()
        assertEquals(PadKey.outputs.toSet(), plan.restoredMappings.keys)
        assertEquals(DeviceProfile.X10.rearButtons, plan.omittedMappings)
        assertTrue(plan.assignedMappings.isEmpty())
    }
}
