// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.ble.LinkPhase
import br.dev.edgard.easygamepad.ble.LinkState
import br.dev.edgard.easygamepad.protocol.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControllerRepositoryLifecycleTest {
    private class FakeKrlyPad : GamepadTransport {
        val link = MutableStateFlow(LinkState())
        private val incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
        override val notifications = incoming.asSharedFlow()
        var aborts = 0

        fun connect(address: String) {
            link.value = LinkState(LinkPhase.READY, address, protocol = ConfigurationProtocol.KRLY)
        }

        override suspend fun write(frame: ByteArray) {
            val id = frame.getOrNull(1)?.toInt()?.and(0xFF) ?: return
            if (id == 49) incoming.emit(bytes(0x08, 0x31, 0x01, 0x00, 0x01, 0x3C, 0x00, 0x00))
            else incoming.emit(bytes(3, id, 0))
        }

        override fun abort(cause: Throwable) {
            aborts++
        }
    }

    @Test
    fun krlyReconnectReplacesSessionWithoutAbortingBacking() = runTest {
        val pad = FakeKrlyPad()
        ControllerRepository(pad, pad.link, backgroundScope, { _, _ -> }, onDisconnect = {})
        pad.connect("first")
        runCurrent()
        pad.connect("second")
        runCurrent()
        assertEquals(0, pad.aborts)
    }

    @Test
    fun disconnectClearsReadableHardwareUntilNextRead() = runTest {
        val pad = FakeKrlyPad()
        val repository = ControllerRepository(pad, pad.link, backgroundScope, { _, _ -> }, onDisconnect = {})
        pad.connect("device")
        runCurrent()
        repository.refreshBattery()
        runCurrent()
        assertEquals(60, repository.state.value.battery?.percent)
        pad.link.value = LinkState(LinkPhase.DISCONNECTED)
        runCurrent()
        assertNull(repository.state.value.battery)
    }

    @Test
    fun closingOneConnectionDoesNotAbortAnUnrelatedTransportUser() = runTest {
        val pad = FakeKrlyPad()
        ControllerRepository(pad, pad.link, backgroundScope, { _, _ -> }, onDisconnect = {})
        val secondLink = MutableStateFlow(LinkState())
        var secondAborts = 0
        val secondPad = object : GamepadTransport {
            override val notifications = MutableSharedFlow<ByteArray>().asSharedFlow()
            override suspend fun write(frame: ByteArray) {}
            override fun abort(cause: Throwable) {
                secondAborts++
            }
        }
        ControllerRepository(secondPad, secondLink, backgroundScope, { _, _ -> }, onDisconnect = {})
        pad.connect("krly")
        runCurrent()
        secondLink.value = LinkState(LinkPhase.READY, "other", protocol = ConfigurationProtocol.KRLY)
        runCurrent()
        pad.link.value = LinkState(LinkPhase.DISCONNECTED)
        runCurrent()
        assertEquals(0, secondAborts)
    }
}
