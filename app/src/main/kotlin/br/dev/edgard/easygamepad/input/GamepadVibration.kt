// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.input

enum class GamepadVibrationAvailability { NO_DEVICE, UNSUPPORTED, AVAILABLE }

fun InputState.vibrationAvailability(): GamepadVibrationAvailability {
    val selected = pads.firstOrNull { it.id == selectedId } ?: return GamepadVibrationAvailability.NO_DEVICE
    return if (selected.canVibrate) GamepadVibrationAvailability.AVAILABLE else GamepadVibrationAvailability.UNSUPPORTED
}
