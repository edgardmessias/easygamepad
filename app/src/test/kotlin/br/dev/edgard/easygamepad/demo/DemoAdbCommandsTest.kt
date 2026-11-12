// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.demo

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoAdbCommandsTest {
    @Test
    fun parsesPressAndAxis() {
        val press = DemoAdbCommands.parse("press", mapOf("button" to "A")) as DemoAdbCommands.Parsed.Press
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, press.keyCode)
        assertTrue(press.down)
        val axis = DemoAdbCommands.parse("axis", mapOf("stick" to "left", "x" to "0.5", "y" to "-1")) as DemoAdbCommands.Parsed.Axis
        assertEquals(DemoAdbCommands.Stick.LEFT, axis.stick)
        assertEquals(0.5f, axis.x)
        assertEquals(-1f, axis.y)
    }

    @Test
    fun rejectsUnknownCommand() {
        assertEquals(null, DemoAdbCommands.parse("apply_profile", emptyMap()))
    }
}
