// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.protocol

import java.util.concurrent.atomic.AtomicInteger

fun Byte.u8(): Int = toInt() and 255
fun ByteArray.hex(): String = joinToString(" ") { "%02X".format(it.u8()) }
fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].also { v -> require(v in 0..255) }.toByte() }

class FrameEncoder {
    private val sequence = AtomicInteger(0)
    fun encode(id: Int, payload: ByteArray = byteArrayOf()): ByteArray {
        require(id in 0..255 && payload.size <= 252)
        return bytes(payload.size + 3, id) + payload + (sequence.getAndIncrement() and 255).toByte()
    }
}

/** A BLE notification is not necessarily a complete frame (MTU/chunking). */
class FrameDecoder {
    private var buffer = byteArrayOf()
    @Synchronized fun feed(chunk: ByteArray): List<ByteArray> {
        if (chunk.isEmpty()) return emptyList()
        buffer += chunk
        val frames = mutableListOf<ByteArray>()
        while (buffer.isNotEmpty()) {
            val length = buffer[0].u8()
            if (length < MIN_FRAME_LENGTH || length > MAX_FRAME_LENGTH) {
                buffer = buffer.drop(1).toByteArray()
                continue
            }
            if (buffer.size < length) break
            frames += buffer.copyOfRange(0, length)
            buffer = buffer.copyOfRange(length, buffer.size)
        }
        if (buffer.size > MAX_RETAINED_BYTES) {
            buffer = byteArrayOf()
        }
        return frames
    }
    @Synchronized fun clear() { buffer = byteArrayOf() }

    companion object {
        const val MIN_FRAME_LENGTH = 3
        const val MAX_FRAME_LENGTH = 255
        const val MAX_BUFFER_BYTES = 512
        const val MAX_RETAINED_BYTES = 255
    }
}

object KrlyCodec {
    fun battery(raw: ByteArray, now: Long = System.currentTimeMillis()): Battery? {
        if (raw.size < 7 || raw[1].u8() != 49 || raw[5].u8() > 100) return null
        return Battery(raw[5].u8(), raw[3].u8() != 0, raw[6].u8() != 0, now)
    }
    fun firmware(raw: ByteArray): Firmware? {
        if (raw.size < 2) return null
        fun part(start: Int, end: Int) = raw.copyOfRange(start, end).joinToString("") { "%02X".format(it.u8()) }
        return when (raw[1].u8()) {
            9 -> if (raw.size >= 11) Firmware(part(2, 5), part(5, 8), part(8, 11), if (raw.size >= 15) part(11, 14) else "—") else null
            // QueryHandleInfoReq, used by the official firmware details screen.
            132 -> if (raw.size >= 17) Firmware(part(4, 7), part(7, 10), part(10, 14), part(14, 17)) else null
            else -> null
        }
    }
    fun calibration(previous: CalibrationState, raw: ByteArray): CalibrationState {
        if (!previous.stopRequired || raw.size < 4) return previous
        return when (raw[1].u8()) {
            67, 73 -> if (previous.kind == CalibrationKind.SELF) previous.copy(
                phase = if (raw[2].u8() == 0) CalibrationPhase.SUCCEEDED else CalibrationPhase.FAILED,
                result = raw[2].u8(),
            ) else previous
            120 -> if (previous.kind != CalibrationKind.SELF && raw.size >= 4) previous.copy(
                phase = when (raw[2].u8()) {
                    1 -> CalibrationPhase.ACTIVE
                    2 -> CalibrationPhase.NEEDS_RETRY
                    3 -> CalibrationPhase.SUCCEEDED
                    else -> previous.phase
                }, result = raw[3].u8(),
            ) else previous
            else -> previous
        }
    }
    fun sticks(left: StickSettings, right: StickSettings): ByteArray {
        left.validate(); right.validate()
        return bytes(left.min, left.max, if (left.invertX) 1 else 0, if (left.invertY) 1 else 0,
            right.min, right.max, if (right.invertX) 1 else 0, if (right.invertY) 1 else 0)
    }
    fun macroStep(macro: Macro, index: Int): ByteArray {
        macro.validate(); require(index in macro.steps.indices)
        val step = macro.steps[index]
        return bytes(macro.trigger.code, if (macro.repeat) 0 else 1, index,
            step.holdMs shr 8, step.holdMs and 255, step.delayMs shr 8, step.delayMs and 255, step.key.code)
    }
}

/** QueryMappingKeyReq uses page headers, with a length-prefixed list of triples. */
class MappingPages {
    private var expectedPage: Int? = null
    private var totalPages: Int? = null
    private var content = byteArrayOf()
    fun accept(raw: ByteArray): Map<PadKey, PadKey>? {
        require(raw.size >= 5 && raw[0].u8() == raw.size && raw[1].u8() == 80)
        val total = raw[2].u8(); val page = raw[3].u8()
        require(total in 0..64 && page <= total &&
            (if (expectedPage == null) page in 0..1 else page == expectedPage) &&
            (totalPages == null || totalPages == total))
        totalPages = total
        content += raw.copyOfRange(4, raw.size - 1)
        require(content.size <= 1024)
        expectedPage = page + 1
        if (page < total) return null
        require(content.isNotEmpty())
        val count = content[0].u8()
        require(count % 3 == 0 && content.size >= count + 1)
        return buildMap {
            for (offset in 1..count step 3) {
                if (content[offset].u8() != 0) continue
                val source = PadKey.from(content[offset + 1].u8()) ?: continue
                val target = PadKey.from(content[offset + 2].u8()) ?: continue
                require(!containsKey(source)) { "Duplicate mapping entry" }
                put(source, target)
            }
        }
    }
}
