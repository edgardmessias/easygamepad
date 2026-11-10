// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.ble.LinkPhase
import br.dev.edgard.easygamepad.ble.LinkState
import br.dev.edgard.easygamepad.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class ControllerRepositoryBindingTest {
    private class RecordPad : GamepadTransport {
        val incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
        override val notifications = incoming.asSharedFlow()
        val writes = mutableListOf<ByteArray>()
        override suspend fun write(frame: ByteArray) {
            writes += frame
            val id = frame[1].u8()
            incoming.emit(bytes(3, id, 0))
        }
        override fun abort(cause: Throwable) = Unit
    }

    @Test
    fun staleBindingSkipsApplyBeforeWrites() = runTest {
        val pad = RecordPad()
        val link = MutableStateFlow(LinkState(LinkPhase.READY, "AA:BB", protocol = ConfigurationProtocol.KRLY))
        val repository = ControllerRepository(pad, link, backgroundScope, { _, _ -> }, onDisconnect = {})
        val stale = OperationBinding.capture(0, "ble:1", link.value, draftEpoch = 0)!!
        var revision = 1L
        val stillBound: (OperationBinding) -> Boolean = { it.matchesResult(revision, "ble:2", link.value) }
        runCurrent()
        val result = repository.apply(ControllerConfig(leftVibration = 12), stale, stillBound)
        runCurrent()
        assertFalse(result)
        val profileWrites = pad.writes.filter { it.getOrNull(1)?.u8() in listOf(1, 53, 54, 61, 62) }
        assertTrue(profileWrites.isEmpty())
    }

}
