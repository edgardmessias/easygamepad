// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package br.dev.edgard.easygamepad.protocol

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class CommandSessionTest {
    private class FakeTransport : GamepadTransport {
        val incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
        override val notifications = incoming.asSharedFlow()
        val writes = mutableListOf<ByteArray>()
        var aborted = false
        override suspend fun write(frame: ByteArray) { writes += frame }
        override fun abort(cause: Throwable) { aborted = true }
    }
    @Test fun asyncCalibrationDoesNotCompleteMappingWrite() = runTest {
        val transport = FakeTransport(); val session = CommandSession(transport, backgroundScope)
        val operation = async { session.ack(61, bytes(1, 160)) }
        runCurrent()
        transport.incoming.emit(bytes(5, 120, 3, 0, 1)); runCurrent()
        assertFalse(operation.isCompleted)
        transport.incoming.emit(bytes(4, 61, 0, 1)); runCurrent()
        operation.await(); session.close()
    }
    @Test fun commandsAreSerializedUntilAcknowledged() = runTest {
        val transport = FakeTransport(); val session = CommandSession(transport, backgroundScope)
        val first = async { session.ack(61, bytes(1, 160)) }; runCurrent()
        val second = async { session.ack(53, bytes(50, 50)) }; runCurrent()
        assertEquals(1, transport.writes.size)
        transport.incoming.emit(bytes(4, 61, 0, 1)); runCurrent()
        assertEquals(2, transport.writes.size)
        transport.incoming.emit(bytes(4, 53, 0, 2)); runCurrent()
        first.await(); second.await(); session.close()
    }
    @Test fun timeoutInvalidatesSessionToPreventLateAckReuse() = runTest {
        val transport = FakeTransport(); val session = CommandSession(transport, backgroundScope)
        val operation = async { runCatching { session.ack(61, bytes(1, 160)) } }
        runCurrent(); advanceTimeBy(5001); runCurrent()
        assertTrue(operation.await().isFailure); assertTrue(transport.aborted)
        transport.incoming.emit(bytes(4, 61, 0, 1))
        assertTrue(runCatching { session.ack(61, bytes(1, 161)) }.isFailure)
        assertEquals(1, transport.writes.size)
    }
    @Test fun cancellationAlsoInvalidatesAnInflightSession() = runTest {
        val transport = FakeTransport(); val session = CommandSession(transport, backgroundScope)
        val operation = launch { session.ack(61, bytes(1, 160)) }
        runCurrent(); operation.cancelAndJoin()
        assertTrue(transport.aborted)
        assertTrue(runCatching { session.ack(61, bytes(1, 161)) }.isFailure)
    }
    @Test fun closeImmediatelyReleasesPendingCommand() = runTest {
        val transport = FakeTransport(); val session = CommandSession(transport, backgroundScope)
        val operation = async { runCatching { session.request(49) } }; runCurrent()
        session.close(); runCurrent()
        assertTrue(operation.await().isFailure)
    }
    @Test fun rejectedAckDoesNotBecomeSuccess() = runTest {
        val transport = FakeTransport(); val session = CommandSession(transport, backgroundScope)
        val operation = async { runCatching { session.ack(53, bytes(50, 50)) } }; runCurrent()
        transport.incoming.emit(bytes(4, 53, 2, 1)); runCurrent()
        assertTrue(operation.await().isFailure); session.close()
    }
    @Test fun malformedMappingReplyFailsRequestAndCollectorKeepsWorking() = runTest {
        val transport = FakeTransport()
        val session = CommandSession(transport, backgroundScope)
        val pages = MappingPages()
        val operation = async {
            runCatching { session.request(80, bytes(1)) { pages.accept(it) != null } }
        }
        runCurrent()
        transport.incoming.emit(bytes(6, 80, 2, 2, 0, 9))
        runCurrent()
        assertTrue(operation.await().isFailure)
        val followUp = async { session.ack(61, bytes(1, 160)) }
        runCurrent()
        transport.incoming.emit(bytes(4, 61, 0, 1))
        runCurrent()
        followUp.await()
        assertFalse(session.isClosed)
        session.close()
    }

    @Test fun multipartRequestWaitsForLastPage() = runTest {
        val transport = FakeTransport(); val session = CommandSession(transport, backgroundScope)
        val pages = MappingPages(); var mappings: Map<PadKey, PadKey>? = null
        val operation = async { session.request(80, bytes(1)) { pages.accept(it).also { result -> mappings = result } != null } }
        runCurrent()
        transport.incoming.emit(bytes(8, 80, 2, 1, 6, 0, 1, 9)); runCurrent()
        assertFalse(operation.isCompleted)
        transport.incoming.emit(bytes(9, 80, 2, 2, 160, 0, 161, 162, 10)); runCurrent()
        operation.await()
        assertEquals(PadKey.A, mappings!![PadKey.M1]); session.close()
    }
}
