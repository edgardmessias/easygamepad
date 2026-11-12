// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package br.dev.edgard.easygamepad.protocol

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulatedX10KrlyPadTest {
    @Test
    fun applyRoundTripUpdatesInternalConfig() = runTest {
        val pad = SimulatedX10KrlyPad()
        val session = CommandSession(object : GamepadTransport {
            override val notifications = pad.notifications
            override suspend fun write(frame: ByteArray) = pad.onWrite(frame)
            override fun abort(cause: Throwable) = Unit
        }, backgroundScope)
        val client = X10ProfileClient(session)
        val result = client.apply(ControllerConfig(leftVibration = 30, mappings = mapOf(PadKey.M1 to PadKey.A)))
        assertTrue(result is X10ApplyResult.Matched)
        assertEquals(30, pad.config.leftVibration)
        assertEquals(PadKey.A, pad.config.mappings[PadKey.M1])
    }

    @Test
    fun batteryFrameMatchesCodec() = runTest {
        val pad = SimulatedX10KrlyPad().apply {
            batteryPercent = 55
            batteryCharging = true
            batteryLow = true
        }
        val session = CommandSession(object : GamepadTransport {
            override val notifications = pad.notifications
            override suspend fun write(frame: ByteArray) = pad.onWrite(frame)
            override fun abort(cause: Throwable) = Unit
        }, backgroundScope)
        val raw = session.request(49, accept = { KrlyCodec.battery(it) != null })
        val battery = requireNotNull(KrlyCodec.battery(raw))
        assertEquals(55, battery.percent)
        assertTrue(battery.charging)
        assertTrue(battery.low)
        session.close()
    }
}
