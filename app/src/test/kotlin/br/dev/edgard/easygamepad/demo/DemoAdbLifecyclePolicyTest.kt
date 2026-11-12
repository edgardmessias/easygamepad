// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.demo

import br.dev.edgard.easygamepad.ble.LinkPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoAdbLifecyclePolicyTest {
    private val physicalBle = "ble:AA:BB:CC:DD:EE:01"
    private val demoLinkReady = DemoLinkSnapshot(LinkPhase.READY, DemoController.ADDRESS)
    private val bleReady = DemoLinkSnapshot(LinkPhase.READY, "AA:BB:CC:DD:EE:01")

    @Test
    fun connectBlockedForEveryPhaseWhenPhysicalSelected() {
        LinkPhase.entries.forEach { phase ->
            val link = DemoLinkSnapshot(phase, "AA:BB:CC:DD:EE:01")
            assertEquals(
                DemoAdbConnectAction.BLOCKED,
                DemoAdbLifecyclePolicy.decideConnect(true, physicalBle, true, usesDemo = false, link),
            )
            assertEquals(
                DemoAdbConnectAction.BLOCKED,
                DemoAdbLifecyclePolicy.decideConnect(true, physicalBle, true, usesDemo = true, demoLinkReady),
            )
        }
    }

    @Test
    fun connectBlockedWhenRealBleActiveRegardlessOfSelection() {
        listOf(
            LinkPhase.CONNECTING,
            LinkPhase.DISCOVERING,
            LinkPhase.SUBSCRIBING,
            LinkPhase.READY,
        ).forEach { phase ->
            val link = DemoLinkSnapshot(phase, "AA:BB:CC:DD:EE:01")
            assertEquals(
                DemoAdbConnectAction.BLOCKED,
                DemoAdbLifecyclePolicy.decideConnect(true, selectedId = null, true, usesDemo = false, link),
            )
        }
    }

    @Test
    fun connectFromWelcomeWhenBleIdle() {
        assertEquals(
            DemoAdbConnectAction.SELECT_DEMO,
            DemoAdbLifecyclePolicy.decideConnect(true, null, true, false, DemoLinkSnapshot(LinkPhase.DISCONNECTED)),
        )
    }

    @Test
    fun connectWhenDemoSelected() {
        val disconnected = DemoLinkSnapshot(LinkPhase.DISCONNECTED)
        assertEquals(
            DemoAdbConnectAction.CONNECT_DEMO,
            DemoAdbLifecyclePolicy.decideConnect(true, DemoController.ID, true, false, disconnected),
        )
        assertEquals(
            DemoAdbConnectAction.NOOP,
            DemoAdbLifecyclePolicy.decideConnect(true, DemoController.ID, true, true, demoLinkReady),
        )
    }

    @Test
    fun connectRespectsOperationGate() {
        assertEquals(
            DemoAdbConnectAction.BLOCKED,
            DemoAdbLifecyclePolicy.decideConnect(false, DemoController.ID, true, true, demoLinkReady),
        )
    }

    @Test
    fun disconnectOnlyForDemoSelectionOrActiveDemoRoute() {
        assertTrue(DemoAdbLifecyclePolicy.allowDisconnect(true, DemoController.ID, usesDemo = false))
        assertTrue(DemoAdbLifecyclePolicy.allowDisconnect(true, physicalBle, usesDemo = true))
        assertFalse(DemoAdbLifecyclePolicy.allowDisconnect(true, physicalBle, usesDemo = false))
        assertFalse(DemoAdbLifecyclePolicy.allowDisconnect(false, DemoController.ID, usesDemo = true))
    }

    @Test
    fun factoryResetRequiresReadyDemoLink() {
        assertTrue(
            DemoAdbLifecyclePolicy.allowFactoryReset(true, DemoController.ID, true, demoLinkReady),
        )
        assertFalse(
            DemoAdbLifecyclePolicy.allowFactoryReset(true, DemoController.ID, true, DemoLinkSnapshot(LinkPhase.CONNECTING, DemoController.ADDRESS)),
        )
        assertFalse(
            DemoAdbLifecyclePolicy.allowFactoryReset(true, physicalBle, true, demoLinkReady),
        )
        assertFalse(
            DemoAdbLifecyclePolicy.allowFactoryReset(true, DemoController.ID, false, bleReady),
        )
    }
}
