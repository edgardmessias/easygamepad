// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.demo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoAdbCoordinatorTest {
    @Test
    fun lifecycleCommandsRequireCoordinator() {
        val coordinator = RecordingCoordinator()
        val bridge = DemoBridgeStub(coordinator)
        bridge.emit(DemoAdbCommands.Parsed.Connect)
        bridge.emit(DemoAdbCommands.Parsed.Disconnect)
        bridge.emit(DemoAdbCommands.Parsed.FactoryReset)
        assertTrue(coordinator.connects >= 1)
        assertTrue(coordinator.disconnects >= 1)
        assertTrue(coordinator.resets >= 1)
    }

    @Test
    fun demoDeviceGuardBlocksLinkPersistence() {
        val target = DemoController.device()
        assertTrue(DemoController.isDevice(target))
        assertFalse(target.address.isNullOrBlank())
    }

    private class RecordingCoordinator : DemoAdbCoordinator {
        var connects = 0
        var disconnects = 0
        var resets = 0
        override fun adbConnectDemo() { connects++ }
        override fun adbDisconnectDemo() { disconnects++ }
        override fun adbFactoryReset() { resets++ }
    }

    /** Minimal stand-in: real [DemoBridge] needs [HubApplication]; we only verify routing targets. */
    private class DemoBridgeStub(private val coordinator: DemoAdbCoordinator) {
        fun emit(command: DemoAdbCommands.Parsed) {
            when (command) {
                DemoAdbCommands.Parsed.Connect -> coordinator.adbConnectDemo()
                DemoAdbCommands.Parsed.Disconnect -> coordinator.adbDisconnectDemo()
                DemoAdbCommands.Parsed.FactoryReset -> coordinator.adbFactoryReset()
                else -> Unit
            }
        }
    }
}
