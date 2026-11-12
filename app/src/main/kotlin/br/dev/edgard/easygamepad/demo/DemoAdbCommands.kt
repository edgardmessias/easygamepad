// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.demo

import android.view.KeyEvent
import android.view.MotionEvent

/** Parses `adb shell am broadcast` extras for the demo controller (see docs/demo-controller.md). */
object DemoAdbCommands {
    const val ACTION = "br.dev.edgard.easygamepad.demo.COMMAND"

    sealed interface Parsed {
        data object Connect : Parsed
        data object Disconnect : Parsed
        data object FactoryReset : Parsed
        data class Press(val keyCode: Int, val down: Boolean) : Parsed
        data class Axis(val stick: Stick, val x: Float, val y: Float) : Parsed
        data class Battery(val percent: Int?, val charging: Boolean?, val low: Boolean?) : Parsed
    }

    enum class Stick { LEFT, RIGHT }

    fun parse(cmd: String?, extras: Map<String, String?>): Parsed? {
        if (cmd.isNullOrBlank()) return null
        return when (cmd.trim().lowercase()) {
            "connect" -> Parsed.Connect
            "disconnect" -> Parsed.Disconnect
            "reset", "factory_reset" -> Parsed.FactoryReset
            "press" -> {
                val code = buttonKeyCode(extras["button"] ?: return null) ?: return null
                Parsed.Press(code, down = extras["down"]?.let { it == "1" || it.equals("true", true) } != false)
            }
            "release" -> {
                val code = buttonKeyCode(extras["button"] ?: return null) ?: return null
                Parsed.Press(code, down = false)
            }
            "axis" -> {
                val stick = when (extras["stick"]?.lowercase()) {
                    "left", "l" -> Stick.LEFT
                    "right", "r" -> Stick.RIGHT
                    else -> return null
                }
                val x = extras["x"]?.toFloatOrNull() ?: 0f
                val y = extras["y"]?.toFloatOrNull() ?: 0f
                Parsed.Axis(stick, x.coerceIn(-1f, 1f), y.coerceIn(-1f, 1f))
            }
            "battery" -> Parsed.Battery(
                percent = extras["percent"]?.toIntOrNull()?.coerceIn(0, 100),
                charging = extras["charging"]?.let { it == "1" || it.equals("true", true) },
                low = extras["low"]?.let { it == "1" || it.equals("true", true) },
            )
            else -> null
        }
    }

    fun buttonKeyCode(label: String): Int? = when (label.trim().uppercase()) {
        "A" -> KeyEvent.KEYCODE_BUTTON_A
        "B" -> KeyEvent.KEYCODE_BUTTON_B
        "X" -> KeyEvent.KEYCODE_BUTTON_X
        "Y" -> KeyEvent.KEYCODE_BUTTON_Y
        "LB", "L1" -> KeyEvent.KEYCODE_BUTTON_L1
        "RB", "R1" -> KeyEvent.KEYCODE_BUTTON_R1
        "LT", "L2" -> KeyEvent.KEYCODE_BUTTON_L2
        "RT", "R2" -> KeyEvent.KEYCODE_BUTTON_R2
        "L3", "LS" -> KeyEvent.KEYCODE_BUTTON_THUMBL
        "R3", "RS" -> KeyEvent.KEYCODE_BUTTON_THUMBR
        "VIEW", "SELECT" -> KeyEvent.KEYCODE_BUTTON_SELECT
        "MENU", "START" -> KeyEvent.KEYCODE_BUTTON_START
        "UP", "DPAD_UP" -> KeyEvent.KEYCODE_DPAD_UP
        "DOWN", "DPAD_DOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
        "LEFT", "DPAD_LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
        "RIGHT", "DPAD_RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
        "M1" -> KeyEvent.KEYCODE_BUTTON_1
        "M2" -> KeyEvent.KEYCODE_BUTTON_2
        "M3" -> KeyEvent.KEYCODE_BUTTON_3
        "M4" -> KeyEvent.KEYCODE_BUTTON_4
        else -> label.toIntOrNull()
    }

    fun axisValues(stick: Stick, x: Float, y: Float): Map<Int, Float> = when (stick) {
        Stick.LEFT -> mapOf(MotionEvent.AXIS_X to x, MotionEvent.AXIS_Y to y)
        Stick.RIGHT -> mapOf(
            MotionEvent.AXIS_Z to x,
            MotionEvent.AXIS_RZ to y,
        )
    }
}
