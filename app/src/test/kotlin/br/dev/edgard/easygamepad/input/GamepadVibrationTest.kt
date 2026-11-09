// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.input

import org.junit.Assert.*
import org.junit.Test

class GamepadVibrationTest {
    private val unsupported = InputPad(1, "Gamepad", 1, 1, false, "shared")
    private val supported = InputPad(2, "Gamepad", 1, 1, true, "shared")

    @Test fun vibrationSupportFollowsTheSelectedRuntimeDeviceEvenWithIdenticalNamesAndDescriptors() {
        val sessions = InputSessions()
        sessions.refresh(listOf(unsupported, supported))
        sessions.select(unsupported.id)
        assertEquals(GamepadVibrationAvailability.UNSUPPORTED, sessions.state.value.vibrationAvailability())
        sessions.select(supported.id)
        assertEquals(GamepadVibrationAvailability.AVAILABLE, sessions.state.value.vibrationAvailability())
        sessions.select(null)
        assertEquals(GamepadVibrationAvailability.NO_DEVICE, sessions.state.value.vibrationAvailability())
    }

    @Test fun unpluggingTheSelectedGamepadDoesNotOfferAnotherControllersMotors() {
        val sessions = InputSessions()
        sessions.refresh(listOf(supported, unsupported.copy(id = 3, canVibrate = true)))
        sessions.select(supported.id)
        sessions.refresh(listOf(unsupported.copy(id = 3, canVibrate = true)))
        assertEquals(GamepadVibrationAvailability.NO_DEVICE, sessions.state.value.vibrationAvailability())
    }

    @Test fun changedCapabilitiesUpdateTheTestWithoutReselectingTheDevice() {
        val sessions = InputSessions()
        sessions.refresh(listOf(supported))
        sessions.select(supported.id)
        assertEquals(GamepadVibrationAvailability.AVAILABLE, sessions.state.value.vibrationAvailability())
        sessions.refresh(listOf(supported.copy(canVibrate = false)))
        assertEquals(GamepadVibrationAvailability.UNSUPPORTED, sessions.state.value.vibrationAvailability())
    }
}
