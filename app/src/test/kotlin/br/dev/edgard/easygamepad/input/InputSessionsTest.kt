// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.input

import br.dev.edgard.easygamepad.protocol.PadKey
import org.junit.Assert.*
import org.junit.Test

class InputSessionsTest {
    private val pads = listOf(InputPad(3, "Pad", 1, 2, false, "a"), InputPad(7, "Pad", 1, 2, false, "b"))
    @Test fun eventsFromAnotherControllerDoNotChangeSelectedButtonsOrAxes() {
        val sessions = InputSessions(); sessions.refresh(pads); sessions.select(3)
        sessions.update(3, InputState(pressed = setOf(96), axes = mapOf(17 to .3f), events = 1))
        sessions.update(7, InputState(pressed = setOf(97), axes = mapOf(17 to .9f), events = 12))
        assertEquals(setOf(96), sessions.state.value.pressed)
        assertEquals(.3f, sessions.state.value.axes[17])
        sessions.select(7)
        assertEquals(setOf(97), sessions.state.value.pressed)
        assertEquals(.9f, sessions.state.value.axes[17])
        assertEquals(12, sessions.state.value.events)
        sessions.select(3)
        assertEquals(1, sessions.state.value.events)
    }
    @Test fun deviceRemovalDoesNotSelectAnotherControllerAndResetOnlyAffectsSelectedDevice() {
        val sessions = InputSessions(); sessions.refresh(pads); sessions.select(3)
        sessions.update(3, InputState(tested = setOf(96)))
        sessions.update(7, InputState(tested = setOf(97)))
        sessions.reset()
        assertTrue(sessions.snapshot(3).tested.isEmpty())
        assertEquals(setOf(97), sessions.snapshot(7).tested)
        sessions.refresh(listOf(pads[1]))
        assertNull(sessions.state.value.selectedId)
    }
    @Test fun clearPressedCanTargetOneDevice() {
        val sessions = InputSessions()
        sessions.refresh(pads)
        sessions.select(3)
        sessions.update(3, InputState(pressed = setOf(96), axes = mapOf(1 to .5f)))
        sessions.update(7, InputState(pressed = setOf(97), axes = mapOf(2 to .75f)))
        sessions.clearPressed(3)
        assertTrue(sessions.snapshot(3).pressed.isEmpty())
        assertEquals(setOf(97), sessions.snapshot(7).pressed)
        sessions.select(7)
        assertEquals(setOf(97), sessions.state.value.pressed)
    }

    @Test fun backgroundClearsHeldInputsOnAllDevicesAndSwitchCancelsIdentification() {
        val sessions = InputSessions(); sessions.refresh(pads); sessions.select(3)
        sessions.update(3, InputState(pressed = setOf(96), learningPaddle = PadKey.M1))
        sessions.update(7, InputState(pressed = setOf(97)))
        sessions.select(7); assertNull(sessions.snapshot(3).learningPaddle)
        sessions.clearPressed(); assertTrue(sessions.state.value.pressed.isEmpty())
        sessions.select(3); assertTrue(sessions.state.value.pressed.isEmpty())
    }

    @Test fun bothStickTrailsStayWithTheirControllerAndClearTogether() {
        val sessions = InputSessions(); sessions.refresh(pads); sessions.select(3)
        sessions.update(3, InputState(samples = listOf(.1f to .2f), rightSamples = listOf(.8f to -.3f)))
        sessions.select(7)
        assertTrue(sessions.state.value.rightSamples.isEmpty())
        sessions.update(7, InputState(rightSamples = listOf(-.5f to .2f)))
        sessions.select(3)
        assertEquals(listOf(.1f to .2f), sessions.state.value.samples)
        assertEquals(listOf(.8f to -.3f), sessions.state.value.rightSamples)
        sessions.reset()
        assertTrue(sessions.state.value.samples.isEmpty())
        assertTrue(sessions.state.value.rightSamples.isEmpty())
        assertEquals(listOf(-.5f to .2f), sessions.snapshot(7).rightSamples)
    }

    @Test
    fun explicitSelectionDoesNotJumpToAnotherRuntimeId() {
        val sessions = InputSessions()
        sessions.refresh(pads)
        sessions.select(3)
        sessions.refresh(listOf(pads[1]))
        assertNull(sessions.state.value.selectedId)
        sessions.refresh(listOf(InputPad(99, "Replacement", 1, 2, false, "c")))
        assertNull(sessions.state.value.selectedId)
    }

    @Test
    fun ambiguousDescriptorsStayIsolatedPerRuntimeId() {
        val sameName = listOf(
            InputPad(10, "Wireless Controller", 1, 2, false, "a"),
            InputPad(11, "Wireless Controller", 3, 4, false, "b"),
        )
        val sessions = InputSessions()
        sessions.refresh(sameName)
        sessions.select(10)
        sessions.update(10, InputState(tested = setOf(96)))
        sessions.update(11, InputState(tested = setOf(97)))
        sessions.select(11)
        assertEquals(setOf(97), sessions.state.value.tested)
        sessions.select(10)
        assertEquals(setOf(96), sessions.state.value.tested)
    }

    @Test
    fun hatAxesMarkDpadAsTestedWithoutMergingDevices() {
        val sessions = InputSessions()
        sessions.refresh(pads)
        sessions.select(3)
        sessions.update(
            3,
            InputState(
                axes = mapOf(15 to -1f, 16 to 0f),
                tested = setOf(21),
            ),
        )
        assertTrue(21 in sessions.state.value.tested)
        sessions.update(7, InputState(tested = setOf(22)))
        sessions.select(7)
        assertEquals(setOf(22), sessions.state.value.tested)
    }

    @Test
    fun learningCancelClearsPaddleWithoutAffectingBindings() {
        val sessions = InputSessions()
        sessions.refresh(pads)
        sessions.select(3)
        sessions.update(
            3,
            InputState(
                learningPaddle = PadKey.M1,
                paddleBindings = mapOf(PadKey.M2 to 99),
            ),
        )
        sessions.update(3, sessions.snapshot(3).copy(learningPaddle = null))
        assertNull(sessions.state.value.learningPaddle)
        assertEquals(mapOf(PadKey.M2 to 99), sessions.state.value.paddleBindings)
    }
}
