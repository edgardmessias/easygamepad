// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package br.dev.edgard.easygamepad.protocol

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class X10VibrationPreviewTest {
    private class Pad : GamepadTransport {
        private val incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 8)
        override val notifications = incoming.asSharedFlow()
        val writes = mutableListOf<ByteArray>()
        private val encoder = FrameEncoder()
        override suspend fun write(frame: ByteArray) {
            writes += frame
            val reply = when (frame[1].u8()) {
                69 -> bytes(0)
                else -> error("Unexpected command ${frame[1].u8()}")
            }
            incoming.emit(encoder.encode(frame[1].u8(), reply))
        }
        override fun abort(cause: Throwable) = Unit
    }

    @Test fun holdAndStopUseCommand69WithoutProfileReads() = runTest {
        val pad = Pad()
        val preview = X10VibrationPreview(CommandSession(pad, backgroundScope))
        preview.hold(X10Motor.RIGHT, 55)
        preview.stop()
        assertEquals(listOf(69, 69), pad.writes.map { it[1].u8() })
        assertArrayEquals(bytes(0, 55, 2), pad.writes[0].copyOfRange(2, 5))
        assertArrayEquals(bytes(0, 0, 0), pad.writes[1].copyOfRange(2, 5))
    }
}
