// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.protocol

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * In-process KRLY (X10 / QMacro) behaviour for UI tests and the app demo controller.
 * Applies profile writes to an internal [ControllerConfig] and answers the read-back queries
 * [X10ProfileClient] expects after apply.
 */
class SimulatedX10KrlyPad {
    private val encoder = FrameEncoder()
    private val incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val notifications = incoming.asSharedFlow()
    val requests = mutableListOf<ByteArray>()
    var config = ControllerConfig()
    var batteryPercent = 72
    var batteryCharging = false
    var batteryLow = false

    suspend fun onWrite(frame: ByteArray) {
        requests += frame
        val id = frame[1].u8()
        val payload = frame.copyOfRange(2, frame.size - 1)
        when (id) {
            49 -> {
                incoming.emit(
                    encoder.encode(
                        49,
                        bytes(0x01, if (batteryCharging) 1 else 0, 0, batteryPercent, if (batteryLow) 1 else 0, 0),
                    ),
                )
                return
            }
            132 -> {
                incoming.emit(
                    encoder.encode(
                        132,
                        bytes(1, 0) + bytes(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E),
                    ),
                )
                return
            }
            53 -> config = config.copy(leftVibration = payload[0].u8(), rightVibration = payload[1].u8())
            55 -> config = config.copy(
                turbos = config.turbos.filterNot { it.key.code == payload[0].u8() } +
                    Turbo(requireNotNull(PadKey.from(payload[0].u8())), payload[1].u8() == 1, payload[2].u8()),
            )
            57 -> config = config.copy(
                leftTrigger = TriggerSettings(payload[0].u8(), payload[1].u8()),
                rightTrigger = TriggerSettings(payload[2].u8(), payload[3].u8()),
            )
            58 -> applyRearPayload(payload)
            61 -> config = config.copy(
                mappings = config.mappings + (requireNotNull(PadKey.from(payload[0].u8())) to requireNotNull(PadKey.from(payload[1].u8()))),
            )
            62 -> config = config.copy(
                leftStick = StickSettings(payload[0].u8(), payload[1].u8(), payload[2].u8() == 1, payload[3].u8() == 1),
                rightStick = StickSettings(payload[4].u8(), payload[5].u8(), payload[6].u8() == 1, payload[7].u8() == 1),
            )
            119 -> emitSettings()
            80 -> emitFaceMappings()
            121 -> emitRearMappings()
            69 -> {
                incoming.emit(encoder.encode(69, bytes(0)))
                return
            }
            5 -> {
                config = ControllerConfig()
                incoming.emit(encoder.encode(5, bytes(0)))
                return
            }
        }
        incoming.emit(encoder.encode(id, bytes(0)))
    }

    fun resetToDefaults() {
        config = ControllerConfig()
        batteryPercent = 72
        batteryCharging = false
        batteryLow = false
        requests.clear()
    }

    private suspend fun applyRearPayload(payload: ByteArray) {
        val trigger = PadKey.from(payload[0].u8()) ?: return
        if (payload.size == 7) {
            config = config.copy(
                mappings = config.mappings - trigger,
                macros = config.macros.filterNot { it.trigger == trigger },
                disabledRearPaddles = config.disabledRearPaddles + trigger,
            )
        } else if (payload.size >= 8 &&
            payload[3].u8() == 0 && payload[4].u8() == 0 && payload[5].u8() == 0 && payload[6].u8() == 0
        ) {
            val key = PadKey.from(payload[7].u8()) ?: return
            config = config.copy(
                mappings = config.mappings.filterKeys { it != trigger } + (trigger to key),
                macros = config.macros.filterNot { it.trigger == trigger },
                disabledRearPaddles = config.disabledRearPaddles - trigger,
            )
        } else if (payload.size >= 8) {
            val hold = (payload[3].u8() shl 8) or payload[4].u8()
            val delay = (payload[5].u8() shl 8) or payload[6].u8()
            val key = requireNotNull(PadKey.from(payload[7].u8()))
            val step = MacroStep(key, hold, delay)
            val index = payload[2].u8()
            val existing = config.macros.firstOrNull { it.trigger == trigger }
            val steps = (existing?.steps ?: emptyList()).toMutableList().also {
                while (it.size <= index) it += MacroStep()
                it[index] = step
            }.filter { it.key in PadKey.outputs }
            val macro = Macro(trigger, payload[1].u8() == 0, steps)
            config = config.copy(
                mappings = config.mappings - trigger,
                macros = config.macros.filterNot { it.trigger == trigger } + macro,
                disabledRearPaddles = config.disabledRearPaddles - trigger,
            )
        }
    }

    private suspend fun emitSettings() {
        val turbo = config.turbos.flatMap { listOf(it.key.code, if (it.automatic) 1 else 0, it.rate) }.toIntArray()
        val content = bytes(turbo.size) + bytes(*turbo) + bytes(4, config.leftTrigger.min, config.leftTrigger.max,
            config.rightTrigger.min, config.rightTrigger.max, 9) + KrlyCodec.sticks(config.leftStick, config.rightStick) +
            bytes(0, 2, config.leftVibration, config.rightVibration)
        val middle = content.size / 2
        incoming.emit(encoder.encode(119, bytes(2, 2, 1) + content.copyOfRange(0, middle)))
        incoming.emit(encoder.encode(119, bytes(2, 2, 2) + content.copyOfRange(middle, content.size)))
    }

    private suspend fun emitFaceMappings() {
        val face = config.mappings.filterKeys { it in PadKey.outputs }
        val entries = face.filterNot { it.key == it.value }.flatMap { (from, to) -> listOf(0, from.code, to.code) }.toIntArray()
        val body = if (entries.isEmpty()) bytes(0) else bytes(entries.size) + bytes(*entries)
        incoming.emit(encoder.encode(80, bytes(1, 1) + body))
    }

    private suspend fun emitRearMappings() {
        val blob = DeviceProfile.X10.rearButtons.flatMap { rear ->
            when {
                rear in config.disabledRearPaddles -> listOf(rear.code, 1, 0, 0, 0, 0, 0)
                else -> config.macros.firstOrNull { it.trigger == rear }?.let { macro ->
                    listOf(rear.code, if (macro.repeat) 0 else 1, 0, 0, 0, 0, macro.steps.size) +
                        macro.steps.flatMapIndexed { index, step ->
                            listOf(index, step.holdMs shr 8, step.holdMs and 255, step.delayMs shr 8,
                                step.delayMs and 255, 1, step.key.code)
                        }
                } ?: config.mappings[rear]?.let { key ->
                    listOf(rear.code, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, key.code)
                } ?: emptyList()
            }
        }.toIntArray()
        incoming.emit(encoder.encode(121, bytes(1, 1) + bytes(*blob)))
    }
}
