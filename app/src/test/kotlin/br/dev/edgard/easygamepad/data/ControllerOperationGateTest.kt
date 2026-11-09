// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.protocol.CalibrationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class ControllerOperationGateTest {
    @Test fun keyLinkerOperationBlocksSwitchingBeforeTheUiReceivesItsBusyState() = runTest {
        val hardware = MutableStateFlow(HardwareState())
        val keyLinker = MutableStateFlow(KeyLinkerState())
        val switching = MutableStateFlow(false)
        val gate = ControllerOperationGate(hardware, keyLinker, switching)
        val observed = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { gate.canSwitch.collect { observed += it } }
        runCurrent()
        assertEquals(true, observed.last())
        keyLinker.value = KeyLinkerState(busy = true)
        assertFalse(gate.canSwitchNow())
        runCurrent()
        assertEquals(false, observed.last())
        keyLinker.value = KeyLinkerState()
        runCurrent()
        assertTrue(gate.canSwitchNow()); assertEquals(true, observed.last())
    }

    @Test fun finishingOneDriverDoesNotUnlockAnotherOperationOrCalibration() {
        val hardware = MutableStateFlow(HardwareState(busy = true))
        val keyLinker = MutableStateFlow(KeyLinkerState(busy = true))
        val switching = MutableStateFlow(false)
        val gate = ControllerOperationGate(hardware, keyLinker, switching)
        keyLinker.value = KeyLinkerState()
        assertFalse(gate.canSwitchNow())
        hardware.value = HardwareState(calibration = CalibrationState(stopRequired = true))
        assertFalse(gate.canSwitchNow())
        hardware.value = HardwareState()
        switching.value = true
        assertFalse(gate.canSwitchNow())
        switching.value = false
        assertTrue(gate.canSwitchNow())
    }
}
