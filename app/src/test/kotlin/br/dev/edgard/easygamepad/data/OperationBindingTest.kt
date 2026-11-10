// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.ble.LinkPhase
import br.dev.edgard.easygamepad.ble.LinkState
import br.dev.edgard.easygamepad.protocol.CalibrationState
import br.dev.edgard.easygamepad.protocol.ConfigurationProtocol
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OperationBindingTest {
    private val readyKrly = LinkState(LinkPhase.READY, "AA:BB:CC:DD", protocol = ConfigurationProtocol.KRLY)

    @Test fun captureRequiresReadyLinkWithAddress() {
        assertNull(OperationBinding.capture(0, "ble:AA", LinkState(LinkPhase.CONNECTING, "AA")))
        assertNull(OperationBinding.capture(0, "ble:AA", LinkState(LinkPhase.READY, address = null)))
        val bound = OperationBinding.capture(3, "ble:AA:BB:CC:DD", readyKrly, draftEpoch = 2)
        requireNotNull(bound)
        assertEquals(3, bound.selectionRevision)
        assertEquals("ble:AA:BB:CC:DD", bound.deviceId)
        assertEquals("AA:BB:CC:DD", bound.linkAddress)
        assertEquals(2, bound.draftEpoch)
    }

    @Test fun lateResultRejectedAfterControllerSwitch() {
        val binding = OperationBinding.capture(1, "ble:old", readyKrly)!!
        val newLink = readyKrly.copy(address = "11:22:33:44")
        assertFalse(binding.matchesResult(2, "ble:new", newLink))
        assertFalse(binding.matchesResult(1, "ble:new", readyKrly))
    }

    @Test fun lateResultRejectedOnProtocolMismatch() {
        val binding = OperationBinding.capture(1, "ble:AA", readyKrly)!!
        val keyLinkerLink = readyKrly.copy(protocol = ConfigurationProtocol.KEYLINKER)
        assertFalse(binding.matchesResult(1, "ble:AA", keyLinkerLink))
    }

    @Test fun readMergeSkippedWhenDraftEditedDuringRead() {
        var draftEpoch = 5L
        val binding = OperationBinding.capture(1, "ble:AA", readyKrly, draftEpoch = draftEpoch)!!
        draftEpoch++
        assertFalse(binding.draftEpoch == draftEpoch)
    }

    @Test fun calibrationBlocksOperationGateEvenWhenDriversAreIdle() {
        val hardware = MutableStateFlow(HardwareState(calibration = CalibrationState(stopRequired = true)))
        val keyLinker = MutableStateFlow(KeyLinkerState())
        val switching = MutableStateFlow(false)
        val gate = ControllerOperationGate(hardware, keyLinker, switching)
        assertFalse(gate.canSwitchNow())
    }

    @Test fun delayedRevisionAdvanceDropsBindingBeforeUiWouldApply() = runTest {
        var selectionRevision = 1L
        val binding = OperationBinding.capture(selectionRevision, "ble:AA", readyKrly)!!
        val observed = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            selectionRevision = 2
            observed += binding.matchesResult(selectionRevision, "ble:BB", readyKrly.copy(address = "BB"))
        }
        runCurrent()
        assertEquals(listOf(false), observed)
    }
}
