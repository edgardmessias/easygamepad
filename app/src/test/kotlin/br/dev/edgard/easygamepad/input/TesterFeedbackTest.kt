// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TesterFeedbackTest {
    @Test
    fun livePressTakesPriorityOverRememberedTest() {
        val state = InputState(tested = setOf(KeyEvent.KEYCODE_BUTTON_A), pressed = setOf(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(InputChipState.LIVE, inputChipState(KeyEvent.KEYCODE_BUTTON_A, state, live = true))
        assertEquals(InputChipState.TESTED, inputChipState(KeyEvent.KEYCODE_BUTTON_A, state, live = false))
    }

    @Test
    fun selectedPadLabelShowsHardwareIdentity() {
        val state = InputState(
            pads = listOf(InputPad(42, "Xbox pad", 1, 2, false, "desc")),
            selectedId = 42,
        )
        val label = selectedPadLabel(state, preferPortuguese = false)!!
        assertTrue(label.contains("Xbox pad"))
        assertTrue(label.contains("#42"))
    }
}
