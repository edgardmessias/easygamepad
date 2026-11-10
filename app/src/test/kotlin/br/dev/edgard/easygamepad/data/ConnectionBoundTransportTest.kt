// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.protocol.GamepadTransport
import br.dev.edgard.easygamepad.protocol.bytes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionBoundTransportTest {
    @Test
    fun notificationsStopAfterEpochEnds() = runTest {
        val incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 8)
        val backing = object : GamepadTransport {
            override val notifications = incoming.asSharedFlow()
            override suspend fun write(frame: ByteArray) {}
            override fun abort(cause: Throwable) {}
        }
        var current = true
        val bound = ConnectionBoundTransport(backing) { current }
        val received = mutableListOf<ByteArray>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { bound.notifications.collect { received += it } }
        incoming.emit(bytes(1))
        runCurrent()
        assertEquals(1, received.size)
        current = false
        incoming.emit(bytes(2))
        runCurrent()
        assertEquals(1, received.size)
    }

    @Test
    fun abortAndWriteRespectCurrentEpoch() = runTest {
        var current = true
        var aborts = 0
        val backing = object : GamepadTransport {
            override val notifications = emptyFlow<ByteArray>()
            override suspend fun write(frame: ByteArray) {}
            override fun abort(cause: Throwable) {
                aborts++
            }
        }
        val bound = ConnectionBoundTransport(backing) { current }
        bound.write(bytes(3, 49, 0))
        current = false
        assertTrue(runCatching { bound.write(bytes(3, 49, 0)) }.isFailure)
        bound.abort(IllegalStateException("late"))
        assertEquals(0, aborts)
        current = true
        bound.abort(IllegalStateException("current"))
        assertEquals(1, aborts)
    }
}
