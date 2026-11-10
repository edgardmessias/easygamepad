// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package br.dev.edgard.easygamepad.protocol

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class X10ProfileClientTest {
    private class Pad : GamepadTransport {
        private val incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
        override val notifications = incoming.asSharedFlow()
        val requests = mutableListOf<ByteArray>()
        var config = ControllerConfig()
        var ignoreVibration = false
        var answerSettings = true
        var answerMappings = true
        var malformedSettings = false
        var rejectCommand: Int? = null
        var aborted = false
        private val encoder = FrameEncoder()

        override suspend fun write(frame: ByteArray) {
            requests += frame
            val id = frame[1].u8()
            val payload = frame.copyOfRange(2, frame.size - 1)
            if (id == rejectCommand) { incoming.emit(encoder.encode(id, bytes(2))); return }
            when (id) {
                53 -> if (!ignoreVibration) config = config.copy(leftVibration = payload[0].u8(), rightVibration = payload[1].u8())
                55 -> config = config.copy(turbos = config.turbos.filterNot { it.key.code == payload[0].u8() } +
                    Turbo(requireNotNull(PadKey.from(payload[0].u8())), payload[1].u8() == 1, payload[2].u8()))
                57 -> config = config.copy(leftTrigger = TriggerSettings(payload[0].u8(), payload[1].u8()),
                    rightTrigger = TriggerSettings(payload[2].u8(), payload[3].u8()))
                61 -> config = config.copy(mappings = config.mappings + (requireNotNull(PadKey.from(payload[0].u8())) to requireNotNull(PadKey.from(payload[1].u8()))))
                62 -> config = config.copy(leftStick = StickSettings(payload[0].u8(), payload[1].u8(), payload[2].u8() == 1, payload[3].u8() == 1),
                    rightStick = StickSettings(payload[4].u8(), payload[5].u8(), payload[6].u8() == 1, payload[7].u8() == 1))
                119 -> {
                    if (answerSettings) {
                        if (malformedSettings) { incoming.emit(encoder.encode(119, bytes(2, 1, 1, 0))); return }
                        val turbo = config.turbos.flatMap { listOf(it.key.code, if (it.automatic) 1 else 0, it.rate) }.toIntArray()
                        val content = bytes(turbo.size) + bytes(*turbo) + bytes(4, config.leftTrigger.min, config.leftTrigger.max,
                            config.rightTrigger.min, config.rightTrigger.max, 9) + KrlyCodec.sticks(config.leftStick, config.rightStick) +
                            bytes(0, 2, config.leftVibration, config.rightVibration)
                        val middle = content.size / 2
                        incoming.emit(encoder.encode(119, bytes(2, 2, 1) + content.copyOfRange(0, middle)))
                        incoming.emit(encoder.encode(119, bytes(2, 2, 2) + content.copyOfRange(middle, content.size)))
                    }
                    return
                }
                80 -> {
                    if (answerMappings) {
                        val entries = config.mappings.filterNot { it.key == it.value }.flatMap { (from, to) -> listOf(0, from.code, to.code) }.toIntArray()
                        incoming.emit(encoder.encode(80, bytes(1, 1, entries.size) + bytes(*entries)))
                    }
                    return
                }
            }
            incoming.emit(encoder.encode(id, bytes(0)))
        }
        override fun abort(cause: Throwable) { aborted = true }
    }

    @Test fun applyEmitsOrderedUploadOpcodesThroughReadBack() = runTest {
        val pad = Pad()
        val config = ControllerConfig(
            leftVibration = 25,
            rightVibration = 35,
            mappings = ControllerConfig().mappings + (PadKey.M1 to PadKey.A),
            turbos = listOf(Turbo(PadKey.B, automatic = true, rate = 10)),
        )
        val planned = config.x10UploadPlan().commands.map { it.first }
        val client = X10ProfileClient(CommandSession(pad, backgroundScope))
        client.apply(config)
        val opcodes = pad.requests.map { it[1].u8() }
        assertEquals(1, opcodes.first())
        assertEquals(54, opcodes[1])
        assertEquals(planned, opcodes.subList(2, 2 + planned.size))
        assertEquals(listOf(1, 119, 80), opcodes.takeLast(3))
    }

    @Test fun applyStartsWithEditingModeBeforeFeatureWrites() = runTest {
        val pad = Pad()
        val client = X10ProfileClient(CommandSession(pad, backgroundScope))
        client.apply(
            ControllerConfig(
                leftVibration = 25,
                rightVibration = 35,
                mappings = ControllerConfig().mappings + (PadKey.M1 to PadKey.A),
                turbos = listOf(Turbo(PadKey.B, automatic = true, rate = 10)),
            ),
        )
        assertTrue(pad.requests.isNotEmpty())
        assertEquals(1, pad.requests.first()[1].u8())
        assertTrue(pad.requests.any { it[1].u8() == 54 })
        assertTrue(pad.requests.any { it[1].u8() == 53 })
        assertTrue(pad.requests.any { it[1].u8() == 61 })
        assertTrue(pad.requests.any { it[1].u8() == 119 })
        assertTrue(pad.requests.any { it[1].u8() == 80 })
    }

    @Test fun uploadReturnsToNormalModeThenReadsBothQueriesBeforeReportingAMatch() = runTest {
        val pad = Pad()
        val client = X10ProfileClient(CommandSession(pad, backgroundScope))
        val profile = ControllerConfig(leftStick = StickSettings(5, 95, invertX = true), rightTrigger = TriggerSettings(4, 96),
            leftVibration = 40, rightVibration = 80, mappings = ControllerConfig().mappings + (PadKey.M1 to PadKey.B),
            turbos = listOf(Turbo(PadKey.A, true, 12)), macros = listOf(Macro(steps = listOf(MacroStep()))))
        val modes = mutableListOf<Boolean>()
        val progress = mutableListOf<Float>()
        val result = client.apply(profile, modes::add, progress::add)
        assertTrue(result is X10ApplyResult.Matched)
        assertEquals(listOf(true, false), modes)
        assertEquals(listOf(1, 119, 80), pad.requests.takeLast(3).map { it[1].u8() })
        assertEquals(0, pad.requests[pad.requests.lastIndex - 2][2].u8())
        assertEquals(1f, progress.last())
        assertTrue(progress.zipWithNext().all { (from, to) -> to >= from })
        val settings = (result as X10ApplyResult.Matched).settings
        assertTrue(settings.differences(profile).isEmpty())
        assertTrue(pad.requests.any { it[1].u8() == 58 }) // Sending a macro is distinct from checking it.
    }

    @Test fun acceptedButIgnoredMotorWriteReportsAMismatch() = runTest {
        val pad = Pad().apply { ignoreVibration = true }
        val result = X10ProfileClient(CommandSession(pad, backgroundScope)).apply(ControllerConfig(leftVibration = 40))
        assertTrue(result is X10ApplyResult.Mismatch)
        result as X10ApplyResult.Mismatch
        assertEquals(setOf(X10SettingField.VIBRATION), result.fields)
        assertEquals(70, result.settings.leftVibration)
    }

    @Test fun leftoverMappingsAndTurboAreReportedInsteadOfAssumingTheyWereCleared() = runTest {
        val pad = Pad().apply { config = config.copy(mappings = config.mappings + (PadKey.M1 to PadKey.A), turbos = listOf(Turbo())) }
        val result = X10ProfileClient(CommandSession(pad, backgroundScope)).apply(ControllerConfig())
        assertEquals(setOf(X10SettingField.BUTTONS, X10SettingField.TURBO), (result as X10ApplyResult.Mismatch).fields)
    }

    @Test fun missingSettingsOrMappingReplyNeverReportsAMatch() = runTest {
        for (missing in listOf(119, 80)) {
            val pad = Pad().apply { answerSettings = missing != 119; answerMappings = missing != 80 }
            val session = CommandSession(pad, backgroundScope)
            val result = X10ProfileClient(session).apply(ControllerConfig())
            assertTrue("Missing reply $missing", result is X10ApplyResult.Unconfirmed)
            assertTrue(session.isClosed); assertTrue(pad.aborted)
        }
    }

    @Test fun rejectedUploadRestoresNormalModeAndDoesNotReadBack() = runTest {
        val pad = Pad().apply { rejectCommand = 53 }
        val modes = mutableListOf<Boolean>()
        val result = runCatching { X10ProfileClient(CommandSession(pad, backgroundScope)).apply(ControllerConfig(), modes::add) }
        assertTrue(result.isFailure)
        assertEquals(listOf(true, false), modes)
        assertEquals(1, pad.requests.last()[1].u8()); assertEquals(0, pad.requests.last()[2].u8())
        assertFalse(pad.requests.any { it[1].u8() == 119 || it[1].u8() == 80 })
    }

    @Test fun malformedReadbackIsUnconfirmedAndClosesTheSession() = runTest {
        val pad = Pad().apply { malformedSettings = true }
        val result = X10ProfileClient(CommandSession(pad, backgroundScope)).apply(ControllerConfig())
        assertTrue(result is X10ApplyResult.Unconfirmed)
        assertTrue(pad.aborted)
        assertEquals(listOf(1, 119), pad.requests.takeLast(2).map { it[1].u8() })
    }

    @Test fun cancellationDuringReadbackIsNotConvertedToAResult() = runTest {
        val pad = Pad().apply { answerSettings = false }
        var result: X10ApplyResult? = null
        val operation = launch { result = X10ProfileClient(CommandSession(pad, backgroundScope)).apply(ControllerConfig()) }
        runCurrent()
        assertEquals(119, pad.requests.last()[1].u8())
        operation.cancelAndJoin()
        assertNull(result); assertTrue(pad.aborted)
    }

    @Test fun comparisonsNormalizeIdentityMappingsAndTurboOrderWithoutCheckingLocalFields() {
        val config = ControllerConfig(turbos = listOf(Turbo(PadKey.A), Turbo(PadKey.B)),
            macros = listOf(Macro(steps = listOf(MacroStep()))))
        val settings = ControllerSettings(config.leftTrigger, config.rightTrigger, config.leftStick, config.rightStick,
            config.leftVibration, config.rightVibration, config.turbos.reversed())
        assertTrue(settings.differences(config).isEmpty())
        assertTrue(settings.copy(mappings = mapOf(PadKey.A to PadKey.A)).differences(config).isEmpty())
        assertEquals(setOf(X10SettingField.STICKS, X10SettingField.TRIGGERS), settings.copy(
            leftStick = StickSettings(4, 99), rightTrigger = TriggerSettings(3, 97)).differences(config))
    }
}
