// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package br.dev.edgard.easygamepad.protocol

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class X10VibrationTestTest {
    private class Pad : GamepadTransport {
        private val incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
        override val notifications = incoming.asSharedFlow()
        val writes = mutableListOf<ByteArray>()
        var rejectStart = false
        var rejectStop = false
        var ignoreStart = false
        var changedMotor = false
        var aborted = false
        private val encoder = FrameEncoder()
        override suspend fun write(frame: ByteArray) {
            writes += frame
            val reply = when (frame[1].u8()) {
                119 -> bytes(2, 1, 1, 0, 4, 0, 100, 0, 100, 9, 0, 100, 0, 0, 0, 100, 0, 0, 0, 2,
                    if (changedMotor && writes.any { it[1].u8() == 69 }) 40 else 70, 70)
                80 -> bytes(1, 1, 0)
                121 -> bytes(1, 1, 0)
                69 -> {
                    val stop = frame.copyOfRange(2, 5).all { it == 0.toByte() }
                    if (!stop && ignoreStart) return
                    bytes(if (if (stop) rejectStop else rejectStart) 2 else 0)
                }
                else -> error("Unexpected command ${frame[1].u8()}")
            }
            incoming.emit(encoder.encode(frame[1].u8(), reply))
        }
        override fun abort(cause: Throwable) { aborted = true }
    }

    @Test fun testsUsePhysicallyCheckedPayloadsAndReadBeforeAndAfterWithoutUploadingAProfile() = runTest {
        val checkedPayloads = mapOf(X10Motor.LEFT to bytes(20, 0, 1), X10Motor.RIGHT to bytes(0, 40, 2))
        for ((motor, payload) in checkedPayloads) {
            val pad = Pad()
            val started = currentTime
            val intensity = if (motor == X10Motor.LEFT) 20 else 40
            val result = X10VibrationTest(CommandSession(pad, backgroundScope)).run(motor, intensity)
            assertTrue(result.settingsUnchanged)
            assertEquals(listOf(119, 80, 121, 69, 69, 119, 80, 121), pad.writes.map { it[1].u8() })
            assertArrayEquals(bytes(6, 69) + payload, pad.writes[3].copyOfRange(0, 5))
            assertArrayEquals(bytes(0, 0, 0), pad.writes[4].copyOfRange(2, 5))
            assertTrue(currentTime - started >= 500)
        }
    }

    @Test fun rejectedStartStillAttemptsStopAndDoesNotReportSuccess() = runTest {
        val pad = Pad().apply { rejectStart = true }
        val result = runCatching { X10VibrationTest(CommandSession(pad, backgroundScope)).run(X10Motor.LEFT, 20) }
        assertTrue(result.isFailure)
        assertArrayEquals(bytes(0, 0, 0), pad.writes.last().copyOfRange(2, 5))
    }

    @Test fun customValuesVaryOnlyTheSelectedByteAndKeepCleanupAndReadback() = runTest {
        for (motor in X10Motor.entries) for (value in listOf(0, 20, 40, 60, 80, 100)) {
            val pad = Pad()
            val result = X10VibrationTest(CommandSession(pad, backgroundScope)).run(motor, value)
            val expected = if (motor == X10Motor.LEFT) bytes(value, 0, 1) else bytes(0, value, 2)
            assertArrayEquals(expected, pad.writes[3].copyOfRange(2, 5))
            assertArrayEquals(bytes(0, 0, 0), pad.writes[4].copyOfRange(2, 5))
            assertEquals(listOf(119, 80, 121, 69, 69, 119, 80, 121), pad.writes.map { it[1].u8() })
            assertTrue(result.settingsUnchanged)
        }
    }

    @Test fun outOfRangeCustomValuesAreRejectedBeforeAnyCommunication() = runTest {
        for (value in listOf(-1, 101)) {
            val pad = Pad()
            val result = runCatching { X10VibrationTest(CommandSession(pad, backgroundScope)).run(X10Motor.RIGHT, value) }
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertTrue(pad.writes.isEmpty())
        }
    }

    @Test fun cancellationWhileWaitingAttemptsStopBeforeItFinishes() = runTest {
        val pad = Pad()
        val operation = launch { X10VibrationTest(CommandSession(pad, backgroundScope)).run(X10Motor.LEFT, 20) }
        runCurrent()
        assertTrue(pad.writes.size >= 3)
        val beforeCancel = pad.writes.size
        operation.cancelAndJoin()
        assertTrue(pad.writes.size > beforeCancel)
        assertArrayEquals(bytes(0, 0, 0), pad.writes.last().copyOfRange(2, 5))
    }

    @Test fun rejectedFollowUpAndMissingStartReplyNeverReportACompletedTest() = runTest {
        for (missingReply in listOf(false, true)) {
            val pad = Pad().apply { rejectStop = !missingReply; ignoreStart = missingReply }
            val result = runCatching { X10VibrationTest(CommandSession(pad, backgroundScope)).run(X10Motor.LEFT, 20) }
            assertTrue(result.isFailure)
            if (missingReply) assertTrue(pad.aborted)
        }
    }

    @Test fun settingsChangesAreReportedWithoutSendingAnAutomaticProfileRestore() = runTest {
        val pad = Pad().apply { changedMotor = true }
        val result = X10VibrationTest(CommandSession(pad, backgroundScope)).run(X10Motor.LEFT, 20)
        assertFalse(result.settingsUnchanged)
        assertEquals(listOf(119, 80, 121, 69, 69, 119, 80, 121), pad.writes.map { it[1].u8() })
    }
}
