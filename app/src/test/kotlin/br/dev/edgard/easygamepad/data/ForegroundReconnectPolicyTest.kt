// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.ble.LinkPhase
import br.dev.edgard.easygamepad.ble.LinkState
import br.dev.edgard.easygamepad.protocol.DeviceProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundReconnectPolicyTest {
    private val x10 = ControllerDevice("ble:aa", "X10", modelId = DeviceProfile.X10.id, address = "AA:BB:CC:DD:EE:FF")

    @Test fun idleReadyLinkStaysUpOnBackground() {
        assertFalse(
            needsConfigurationDisconnectOnBackground(
                LinkState(LinkPhase.READY, "AA:BB:CC:DD:EE:FF"),
                calibrationStopRequired = false,
                macroUploadActive = false,
            ),
        )
    }

    @Test fun calibrationMustDisconnectOnBackground() {
        assertTrue(
            needsConfigurationDisconnectOnBackground(
                LinkState(LinkPhase.READY, "AA:BB:CC:DD:EE:FF"),
                calibrationStopRequired = true,
                macroUploadActive = false,
            ),
        )
    }

    @Test fun macroUploadMustDisconnectOnBackground() {
        assertTrue(
            needsConfigurationDisconnectOnBackground(
                LinkState(LinkPhase.READY, "AA:BB:CC:DD:EE:FF"),
                calibrationStopRequired = false,
                macroUploadActive = true,
            ),
        )
    }

    @Test fun connectingPhaseIgnoresOperationFlags() {
        assertFalse(
            needsConfigurationDisconnectOnBackground(
                LinkState(LinkPhase.CONNECTING, "AA:BB:CC:DD:EE:FF"),
                calibrationStopRequired = true,
                macroUploadActive = true,
            ),
        )
    }

    @Test fun readyLinkDoesNotTriggerForegroundRetry() {
        assertFalse(
            shouldRetryConfigurationConnect(
                manuallyDisconnected = false,
                switching = false,
                deviceSheetOpen = false,
                connectionSheetOpen = false,
                scanning = false,
                linkPhase = LinkPhase.READY,
                bleEnabled = true,
                device = x10,
                nowMillis = 10_000,
                retryAtMillis = 0,
            ),
        )
    }

    @Test fun disconnectedSavedDeviceMayRetryWhenIdle() {
        assertTrue(
            shouldRetryConfigurationConnect(
                manuallyDisconnected = false,
                switching = false,
                deviceSheetOpen = false,
                connectionSheetOpen = false,
                scanning = false,
                linkPhase = LinkPhase.DISCONNECTED,
                bleEnabled = true,
                device = x10,
                nowMillis = 10_000,
                retryAtMillis = 0,
            ),
        )
    }
}
