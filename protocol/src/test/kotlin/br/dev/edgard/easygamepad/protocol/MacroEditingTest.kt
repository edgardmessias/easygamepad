// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.protocol

import org.junit.Assert.*
import org.junit.Test

class MacroEditingTest {
    private val twoSteps = Macro(
        steps = listOf(
            MacroStep(PadKey.A, holdMs = 80, delayMs = 40),
            MacroStep(PadKey.B, holdMs = 120, delayMs = 0),
        ),
    )

    @Test fun summaryJoinsStepsWithArrow() {
        assertEquals("A 80/40ms → B 120/0ms", twoSteps.sequenceSummary())
    }

    @Test fun moveStepSwapsNeighbors() {
        val moved = twoSteps.moveStep(from = 1, by = -1)
        assertEquals(listOf(PadKey.B, PadKey.A), moved.steps.map { it.key })
    }

    @Test fun moveStepRejectsOutOfRange() {
        assertThrows(IllegalArgumentException::class.java) { twoSteps.moveStep(0, -1) }
        assertThrows(IllegalArgumentException::class.java) { twoSteps.moveStep(1, 1) }
    }

    @Test fun duplicateInsertsAfterSource() {
        val dup = twoSteps.duplicateStepAt(0)
        assertEquals(3, dup.steps.size)
        assertEquals(listOf(PadKey.A, PadKey.A, PadKey.B), dup.steps.map { it.key })
    }

    @Test fun duplicateRejectsAtStepLimit() {
        val full = Macro(steps = List(MACRO_STEP_LIMIT) { MacroStep(PadKey.A) })
        assertThrows(IllegalArgumentException::class.java) { full.duplicateStepAt(0) }
    }

    @Test fun removeStepKeepsOrder() {
        val trimmed = twoSteps.removeStepAt(0)
        assertEquals(1, trimmed.steps.size)
        assertEquals(PadKey.B, trimmed.steps.single().key)
    }

    @Test fun emptyMacroFailsValidation() {
        assertTrue(ControllerConfig(macros = listOf(Macro(steps = emptyList()))).profileValidationIssues().isNotEmpty())
    }

    @Test fun repeatFlagDoesNotChangeSummary() {
        val repeating = twoSteps.copy(repeat = true)
        assertEquals(twoSteps.sequenceSummary(), repeating.sequenceSummary())
    }

    @Test fun longFixtureSummaryStaysSingleLine() {
        val long = Macro(steps = List(12) { i -> MacroStep(PadKey.outputs[i % PadKey.outputs.size], 50 + i, 10) })
        val summary = long.sequenceSummary()
        assertTrue(summary.contains("→"))
        assertEquals(12, summary.split("→").size)
    }
}
