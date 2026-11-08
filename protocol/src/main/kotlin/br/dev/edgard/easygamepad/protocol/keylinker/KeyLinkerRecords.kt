// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Amjad AAYD
// Ported from X20CTL (https://github.com/AmjadAAYD/x20ctl, GPL-3.0-or-later),
// x20ctl/protocol.py at revision aac5461. Decoders follow the recorded hardware replies
// pinned in X20CTL's tests; see protocol/src/test/.../keylinker for the fixtures.
package br.dev.edgard.easygamepad.protocol.keylinker

import br.dev.edgard.easygamepad.protocol.u8

private fun ByteArray.hexString() = joinToString("") { "%02x".format(it.u8()) }

/**
 * A reply payload after its length prefix: byte 0 of every reply payload is the
 * size of the record that follows. When [declared] exceeds what arrived, the rest
 * is fetched by re-querying with the next position.
 */
class KeyLinkerBody(val declared: Int, val data: ByteArray) {
    val complete get() = data.size == declared
    fun groups(size: Int): List<ByteArray> {
        require(size > 0)
        return (data.indices step size).map { data.copyOfRange(it, minOf(it + size, data.size)) }
    }
    companion object {
        fun unwrap(payload: ByteArray): KeyLinkerBody {
            require(payload.isNotEmpty()) { "Empty payload has no length prefix" }
            return KeyLinkerBody(payload[0].u8(), payload.copyOfRange(1, payload.size))
        }
    }
}

/** Decoded 0x91 (READ_VID_PID_VERSION). VID/PID are vendor-internal identifiers, not USB ids. */
data class KeyLinkerIdentity(
    val vid: String, val pid: String, val firmware: String,
    /** Operating mode (the app's field is misleadingly called "model"). Null unless the record is 7 bytes. */
    val mode: Int? = null, val sensor: Int? = null, val rawGeneration: Int? = null,
    val record: String = "",
) {
    /** The generation the official app routes on: raw 2 counts as 1 in modes 0, 7 and 8. */
    val effectiveGeneration: Int? get() = when {
        rawGeneration == null || mode == null -> null
        rawGeneration == 2 && mode in setOf(0, 7, 8) -> 1
        else -> rawGeneration
    }

    companion object {
        /** A raw 0x91 reply payload: size byte then the identity record. Incomplete records are refused. */
        fun parse(payload: ByteArray): KeyLinkerIdentity {
            val body = KeyLinkerBody.unwrap(payload)
            require(body.data.size >= body.declared) { "Identity reply is incomplete: ${body.data.size} of ${body.declared} bytes" }
            val record = body.data.copyOfRange(0, body.declared)
            require(record.size >= 6) { "Identity record too short: ${record.size} bytes" }
            val vid = "%02x%02x".format(record[0].u8(), record[1].u8())
            val pid = "%02x%02x".format(record[2].u8(), record[3].u8())
            val version = "%x.%02x".format(record[4].u8(), record[5].u8())
            if (record.size != 7) return KeyLinkerIdentity(vid, pid, version, record = record.hexString())
            val bits = record[6].u8()
            return KeyLinkerIdentity(vid, pid, version, bits and 0x0F, (bits shr 4) and 1, (bits shr 5) and 7, record.hexString())
        }
    }
}

/** Decoded 0x93 (HOST_GUID): updater hardware id (16 bytes) then a little-endian version in hundredths. */
data class KeyLinkerHostGuid(val hardwareId: String, val modelCode: String, val model: String?, val version: String) {
    companion object {
        private val PREFIX = "a1ca51da724cb6103658c05d7d4e"
        /** From the vendor updaters' transfer headers; an unlisted code is unknown, not wrong. */
        val UPDATER_MODELS = mapOf(
            "2091" to "EasySMX X20", "2177" to "EasySMX X15", "2267" to "EasySMX D05", "2093" to "EasySMX S15",
            "2350" to "EasySMX S10", "2371" to "EasySMX S10 Lite", "2395" to "EasySMX X20 Pro",
        )
        fun parse(body: KeyLinkerBody): KeyLinkerHostGuid {
            require(body.complete && body.data.size == 18) { "HOST_GUID needs all 18 bytes, got ${body.data.size} of ${body.declared}" }
            val id = body.data.copyOfRange(0, 16)
            val code = id.copyOfRange(14, 16).hexString()
            val raw = body.data[16].u8() or (body.data[17].u8() shl 8)
            val known = id.copyOfRange(0, 14).hexString() == PREFIX
            return KeyLinkerHostGuid(id.hexString(), code, if (known) UPDATER_MODELS[code] else null,
                "${raw / 100}.${"%02d".format(raw % 100)}")
        }
    }
}

/**
 * HOST_MENU kind 1: the configuration descriptor the official app builds its menu from.
 * A zero means "not configurable over this protocol", not "the hardware lacks the feature".
 */
data class KeyLinkerCapabilities(
    val sticks: Int, val triggers: Int, val motors: Int, val turbo: Int, val macros: Int,
    val changeKey: Int, val lighting: Int, val engine: Int, val nfc: Int, val sensor: Int, val raw: String,
) {
    private fun bit(value: Int, index: Int) = value shr index and 1 == 1
    val leftStick get() = bit(sticks, 1)
    val rightStick get() = bit(sticks, 0)
    val leftTrigger get() = bit(triggers, 1)
    val rightTrigger get() = bit(triggers, 0)
    val motorCount get() = (0 until 4).count { bit(motors, it) }
    val macroSlots get() = SLOT_NAMES.filterIndexed { index, _ -> bit(macros, index) }
    val lightingZones get() = (0 until 4).count { bit(lighting, it) }
    val remapSelectList get() = bit(changeKey, 1)
    val newMacroEngine get() = bit(engine, 1)

    companion object {
        val SLOT_NAMES = listOf("M1", "M2", "M3", "M4", "M5", "M6", "ML", "MR")
        fun parse(body: KeyLinkerBody): KeyLinkerCapabilities {
            require(body.data.size >= 10) { "Capability record needs 10 bytes, got ${body.data.size}" }
            val d = body.data
            return KeyLinkerCapabilities(d[0].u8(), d[1].u8(), d[2].u8(), d[3].u8(), d[4].u8(), d[5].u8(), d[6].u8(),
                d[7].u8(), d[8].u8(), d[9].u8(), d.hexString())
        }
    }
}

/** The power reply: a four-step gauge. There is no percentage in this protocol. */
data class KeyLinkerBattery(val level: Int, val charging: Boolean, val status: Int) {
    companion object {
        const val LEVELS = 4
        /** ButtonTestActivity precedence: bit 5, then 6, then 7; charging is bit 4. Not confirmed on a discharging pad. */
        fun parse(body: KeyLinkerBody): KeyLinkerBattery {
            require(body.data.size >= 4) { "Power reply too short: ${body.data.size} bytes" }
            require(body.data[0].u8() == 0xDF && body.data[1].u8() == 0xAA) { "Unrelated power reply" }
            val status = body.data[3].u8()
            val level = when { status shr 5 and 1 == 1 -> 2; status shr 6 and 1 == 1 -> 3; status shr 7 and 1 == 1 -> 4; else -> 1 }
            return KeyLinkerBattery(level, status shr 4 and 1 == 1, status)
        }
    }
}

/** One stick or trigger channel: deadzones, two curve control points and flag bits. */
data class KeyLinkerCurve(
    val innerDeadzone: Int, val outerRaw: Int,
    val point1: Pair<Int, Int>, val point2: Pair<Int, Int>,
    val flags: Int = 0, val maxProgress: Int = STICK_MAX_PROGRESS,
) {
    /** The value a person reads; the wire stores max − outer. */
    val outerDeadzone get() = maxProgress - outerRaw
    val invertX get() = flags and FLAG_INVERT_X != 0
    val invertY get() = flags and FLAG_INVERT_Y != 0
    val swapped get() = flags and FLAG_SWAP != 0
    val presetName get() = CURVE_PRESETS.entries.firstOrNull { it.value == (point1 to point2) }?.key
    val gearName get() = TRIGGER_GEARS.entries.firstOrNull { it.value == (innerDeadzone to outerDeadzone) }?.key

    fun withDeadzones(inner: Int = innerDeadzone, outer: Int = outerDeadzone) = copy(innerDeadzone = inner, outerRaw = maxProgress - outer)
    fun withPreset(name: String) = requireNotNull(CURVE_PRESETS[name]) { "Unknown preset $name" }.let { copy(point1 = it.first, point2 = it.second) }
    /** Unknown flag bits are kept verbatim. */
    fun withFlags(invertX: Boolean = this.invertX, invertY: Boolean = this.invertY): KeyLinkerCurve {
        var f = flags
        f = if (invertX) f or FLAG_INVERT_X else f and FLAG_INVERT_X.inv()
        f = if (invertY) f or FLAG_INVERT_Y else f and FLAG_INVERT_Y.inv()
        return copy(flags = f)
    }

    /** Reading never validates (show what the pad says); writing does. */
    fun validate() {
        require(innerDeadzone in 0..maxProgress) { "Inner deadzone $innerDeadzone is outside 0-$maxProgress" }
        require(outerRaw in 0..maxProgress) { "Outer deadzone $outerDeadzone is outside 0-$maxProgress" }
        require(innerDeadzone < outerDeadzone) { "Inner deadzone must stay below the outer deadzone" }
        listOf(point1, point2).forEach { require(it.first in 0..255 && it.second in 0..255) { "Curve point $it is outside 0-255" } }
        require(point1.first <= point2.first) { "Curve points run left to right" }
    }

    fun toBytes(): ByteArray = intArrayOf(innerDeadzone, outerRaw, point1.first, point1.second, point2.first, point2.second, flags)
        .also { values -> values.forEach { require(it in 0..255) } }.let { v -> ByteArray(v.size) { v[it].toByte() } }

    /**
     * Points for drawing. The control points are certain; how the firmware interpolates between
     * them is not, so this is a monotone cubic through (0,0), both points and (255,255).
     */
    fun shape(steps: Int = 33): List<Pair<Float, Float>> = curveShape(point1, point2, steps)

    companion object {
        const val CHANNEL_SIZE = 7
        const val STICK_MAX_PROGRESS = 100
        const val TRIGGER_MAX_PROGRESS = 200
        const val FLAG_INVERT_X = 0x01
        const val FLAG_INVERT_Y = 0x02
        const val FLAG_SWAP = 0x04
        /** HostActivity's preset tables, identical for sticks and triggers. */
        val CURVE_PRESETS = linkedMapOf(
            "default" to ((85 to 85) to (170 to 170)), "quick" to ((35 to 35) to (136 to 204)),
            "slow" to ((35 to 35) to (219 to 145)), "smooth" to ((142 to 142) to (220 to 157)),
            "fine" to ((67 to 67) to (72 to 106)),
        )
        /** Trigger gears are deadzone pairs written into the existing trigger record. */
        val TRIGGER_GEARS = linkedMapOf("zero" to (0 to 0), "small" to (4 to 34), "medium" to (20 to 40), "large" to (30 to 60))

        fun parse(raw: ByteArray, maxProgress: Int): KeyLinkerCurve {
            require(raw.size == CHANNEL_SIZE) { "A channel is $CHANNEL_SIZE bytes, got ${raw.size}" }
            return KeyLinkerCurve(raw[0].u8(), raw[1].u8(), raw[2].u8() to raw[3].u8(), raw[4].u8() to raw[5].u8(), raw[6].u8(), maxProgress)
        }
        fun parseRecord(body: KeyLinkerBody, maxProgress: Int): List<KeyLinkerCurve> {
            require(body.complete && body.declared % CHANNEL_SIZE == 0) { "Curve record of ${body.declared} is not whole channels" }
            return body.groups(CHANNEL_SIZE).map { parse(it, maxProgress) }
        }
        /** Write payload, length prefix included. */
        fun buildRecord(channels: List<KeyLinkerCurve>): ByteArray {
            channels.forEach { it.validate() }
            val data = channels.fold(byteArrayOf()) { acc, c -> acc + c.toBytes() }
            return byteArrayOf(data.size.toByte()) + data
        }
    }
}

internal fun curveShape(p1: Pair<Int, Int>, p2: Pair<Int, Int>, steps: Int): List<Pair<Float, Float>> {
    val knots = listOf(0.0 to 0.0, p1.first.toDouble() to p1.second.toDouble(), p2.first.toDouble() to p2.second.toDouble(), 255.0 to 255.0)
    val unique = mutableListOf<Pair<Double, Double>>()
    knots.forEach { (x, y) -> if (unique.isNotEmpty() && kotlin.math.abs(unique.last().first - x) < 1e-9) unique[unique.lastIndex] = x to y else unique += x to y }
    if (unique.size < 2) return listOf(0f to 0f, 255f to 255f)
    val xs = unique.map { it.first }; val ys = unique.map { it.second }
    val deltas = (0 until xs.size - 1).map { (ys[it + 1] - ys[it]) / (xs[it + 1] - xs[it]) }
    val slopes = MutableList(xs.size) { 0.0 }
    slopes[0] = deltas[0]
    for (i in 1 until xs.size - 1) slopes[i] = if (deltas[i - 1] * deltas[i] <= 0) 0.0 else (deltas[i - 1] + deltas[i]) / 2
    slopes[xs.size - 1] = deltas.last()
    deltas.forEachIndexed { i, delta ->
        if (delta == 0.0) { slopes[i] = 0.0; slopes[i + 1] = 0.0; return@forEachIndexed }
        val a = slopes[i] / delta; val b = slopes[i + 1] / delta
        if (a * a + b * b > 9) { val s = 3.0 / kotlin.math.sqrt(a * a + b * b); slopes[i] = s * a * delta; slopes[i + 1] = s * b * delta }
    }
    val count = maxOf(steps, 2)
    return (0 until count).map { step ->
        val x = 255.0 * step / (count - 1)
        val y = when {
            x <= xs.first() -> ys.first()
            x >= xs.last() -> ys.last()
            else -> {
                val i = (0 until xs.size - 1).last { xs[it] <= x }
                val span = xs[i + 1] - xs[i]; val t = (x - xs[i]) / span; val t2 = t * t; val t3 = t2 * t
                (2 * t3 - 3 * t2 + 1) * ys[i] + (t3 - 2 * t2 + t) * span * slopes[i] + (-2 * t3 + 3 * t2) * ys[i + 1] + (t3 - t2) * span * slopes[i + 1]
            }
        }
        x.toFloat() to y.toFloat()
    }
}

/** HOST_MOTOR: motor levels 0-255 then, from byte 4, a 32-bit little-endian idle timeout in 5 ms ticks. */
data class KeyLinkerMotorRecord(val declared: Int, val data: ByteArray) {
    val leftPercent get() = toPercent(data[0].u8())
    val rightPercent get() = toPercent(data[1].u8())
    /** Minutes, or null for "never". Throws when the record has no timeout field. */
    val shutdownMinutes: Int? get() {
        require(data.size >= SHUTDOWN_OFFSET + 4) { "Motor record of ${data.size} bytes has no shutdown field" }
        val raw = data.copyOfRange(SHUTDOWN_OFFSET, SHUTDOWN_OFFSET + 4)
        if (raw.all { it.u8() == 0xFF }) return null
        val ticks = raw.foldIndexed(0L) { i, acc, b -> acc or (b.u8().toLong() shl (8 * i)) }
        return (ticks / TICKS_PER_MINUTE).toInt()
    }

    /** The whole record is preserved; only the motor bytes change. */
    fun withVibration(percent: Int): KeyLinkerMotorRecord {
        require(percent in 0..100) { "Vibration is 0-100%" }
        val copy = data.copyOf(); copy[0] = toLevel(percent).toByte(); copy[1] = toLevel(percent).toByte()
        return KeyLinkerMotorRecord(declared, copy)
    }
    fun withShutdown(minutes: Int?): KeyLinkerMotorRecord {
        require(data.size >= SHUTDOWN_OFFSET + 4) { "This motor record has no shutdown field" }
        val bytes = if (minutes == null) ByteArray(4) { 0xFF.toByte() } else {
            require(minutes in 1..MAX_SHUTDOWN_MINUTES) { "Shutdown is 1-$MAX_SHUTDOWN_MINUTES minutes or never" }
            val ticks = minutes.toLong() * TICKS_PER_MINUTE
            ByteArray(4) { (ticks shr (8 * it) and 0xFF).toByte() }
        }
        val copy = data.copyOf(); bytes.copyInto(copy, SHUTDOWN_OFFSET)
        return KeyLinkerMotorRecord(declared, copy)
    }
    fun payload(): ByteArray = byteArrayOf(declared.toByte()) + data
    override fun equals(other: Any?) = other is KeyLinkerMotorRecord && declared == other.declared && data.contentEquals(other.data)
    override fun hashCode() = 31 * declared + data.contentHashCode()

    companion object {
        const val SHUTDOWN_OFFSET = 4
        const val TICKS_PER_MINUTE = 12_000
        const val MAX_SHUTDOWN_MINUTES = 30
        fun toLevel(percent: Int) = (percent * 255 / 100.0).let { kotlin.math.round(it).toInt() }
        fun toPercent(level: Int) = (level * 100 / 255.0).let { kotlin.math.round(it).toInt() }
        fun parse(body: KeyLinkerBody): KeyLinkerMotorRecord {
            require(body.complete && body.data.size >= 2) { "Incomplete motor record" }
            return KeyLinkerMotorRecord(body.declared, body.data)
        }
    }
}

/** Button codes recovered from the official app's resource table (see X20CTL protocol.Key). */
enum class KeyLinkerKey(val code: Int, val label: String) {
    A(1, "A"), B(2, "B"), X(3, "X"), Y(4, "Y"), LB(5, "LB"), RB(6, "RB"), LT(7, "LT"), RT(8, "RT"),
    L3(11, "L3"), R3(12, "R3"), DPAD_DOWN(13, "↓"), DPAD_UP(14, "↑"), DPAD_RIGHT(15, "→"), DPAD_LEFT(16, "←"),
    HOME(17, "Home"), LSTICK_ANALOG(18, "LS"), RSTICK_ANALOG(19, "RS"),
    CAPTURE(95, "C"), TURBO(96, "T"), M_LEFT(97, "ML"), M2(104, "M2"),
    /** The X20 itself uses 93/94 for Select and Start; 9/10 are legacy vocabulary it rejects. */
    SELECT(93, "View"), START(94, "Menu");
    companion object {
        fun from(code: Int) = entries.firstOrNull { it.code == code }
        fun label(code: Int) = from(code)?.label ?: "#$code"
        /** Listed as remap sources by the pad, stored, read back, then ignored: targets only. */
        val TARGET_ONLY = setOf(SELECT, START)
    }
}

object KeyLinkerKeyList {
    /** The source list an X20 on firmware 1.23 reports; only used when the pad gives none. */
    val X20_CHANGEKEY_SOURCES = listOf(1, 2, 3, 4, 5, 6, 7, 8, 13, 14, 15, 16, 11, 12, 93, 94)
    val X20_MACRO_KEYS = listOf(18, 19, 13, 14, 15, 16, 1, 2, 3, 4, 5, 6, 7, 8, 11, 12, 93, 94)

    /** Count byte then run-length encoded codes; the count is where the list ends, not the buffer. */
    fun decode(data: ByteArray): List<Int> {
        if (data.isEmpty()) return emptyList()
        val expected = data[0].u8()
        val keys = mutableListOf<Int>()
        for (i in 1 until data.size) {
            if (keys.size >= expected) break
            val byte = data[i].u8()
            if (byte and 0x80 != 0) {
                require(keys.isNotEmpty()) { "Run-length marker with no preceding literal" }
                repeat((byte and 0x7F) - 1) { keys += keys.last() + 1 }
            } else keys += byte
        }
        require(expected == keys.size) { "Key list declares $expected keys but decoded ${keys.size}" }
        return keys
    }
}

object KeyLinkerRemap {
    const val CHUNK_SIZE = 15
    /** `[count] + one target per source`, 0 meaning unchanged. */
    fun buildPayload(sources: List<Int>, targets: List<Int>): ByteArray {
        require(sources.size == targets.size) { "Sources and targets must be the same length" }
        require(sources.size <= 255)
        return byteArrayOf(sources.size.toByte()) + ByteArray(sources.size) { if (sources[it] == targets[it]) 0 else (targets[it] and 0xFF).toByte() }
    }
    fun parse(sources: List<Int>, data: ByteArray): Map<Int, Int> {
        require(data.size >= sources.size) { "Remap record has ${data.size} targets for ${sources.size} sources" }
        return buildMap { sources.forEachIndexed { i, source -> val t = data[i].u8(); if (t != 0 && t != source) put(source, t) } }
    }
}

/** Where each key sits in a macro step's 24-bit mask; derived from the pad's own macro key list. */
class KeyLinkerMaskLayout private constructor(val digital: Map<Int, Int>, val analog: Map<Int, Int>, val keyList: List<Int>) {
    val neutral get() = analog.values.fold(0) { mask, shift -> mask or (ANALOG_IDLE shl shift) }
    companion object {
        const val ANALOG_IDLE = 0b1000
        fun from(keys: List<Int>): KeyLinkerMaskLayout {
            val digital = linkedMapOf<Int, Int>(); val analog = linkedMapOf<Int, Int>()
            var position = 0
            for (code in keys) {
                when {
                    KeyLinkerKey.from(code) == null -> position += 1
                    code == KeyLinkerKey.LSTICK_ANALOG.code || code == KeyLinkerKey.RSTICK_ANALOG.code -> { analog[code] = position; position += 4 }
                    else -> { digital[code] = position; position += 1 }
                }
            }
            return KeyLinkerMaskLayout(digital, analog, keys)
        }
        val X20 = from(KeyLinkerKeyList.X20_MACRO_KEYS)
    }
}

enum class StickDirection { UP, UP_RIGHT, RIGHT, DOWN_RIGHT, DOWN, DOWN_LEFT, LEFT, UP_LEFT }

data class KeyLinkerMacroStep(val mask: Int, val durationMs: Int) {
    fun toBytes(): ByteArray {
        require(durationMs % 5 == 0) { "Durations are multiples of 5 ms" }
        val ticks = durationMs / 5
        require(ticks in 0..0xFFFF && mask in 0 until (1 shl 24))
        return byteArrayOf((mask and 0xFF).toByte(), (mask shr 8 and 0xFF).toByte(), (mask shr 16 and 0xFF).toByte(),
            (ticks and 0xFF).toByte(), (ticks shr 8 and 0xFF).toByte())
    }
    companion object {
        const val SIZE = 5
        /** Never a zero mask: zero analog nibbles mean "direction UP" and drive the sticks. */
        fun released(durationMs: Int, layout: KeyLinkerMaskLayout = KeyLinkerMaskLayout.X20) = KeyLinkerMacroStep(layout.neutral, durationMs)
        fun parse(raw: ByteArray): KeyLinkerMacroStep {
            require(raw.size == SIZE)
            return KeyLinkerMacroStep(raw[0].u8() or (raw[1].u8() shl 8) or (raw[2].u8() shl 16), (raw[3].u8() or (raw[4].u8() shl 8)) * 5)
        }
    }
}

data class KeyLinkerMacro(val steps: List<KeyLinkerMacroStep>, val loopIntervalMs: Int = 0, val trigger: Int = 0) {
    /** Header: (entries × 5) + 2, then a 12-bit loop interval in 5 ms ticks and the trigger bit. */
    fun payload(): ByteArray {
        require(loopIntervalMs % 5 == 0 && loopIntervalMs / 5 in 0 until (1 shl 12)) { "Loop interval out of range" }
        require(steps.size <= MAX_ENTRIES) { "A macro holds at most $MAX_ENTRIES entries" }
        val ticks = loopIntervalMs / 5
        val header = byteArrayOf((steps.size * KeyLinkerMacroStep.SIZE + 2).toByte(), (ticks and 0xFF).toByte(),
            ((trigger shl 7) or (ticks shr 8 and 0x0F)).toByte())
        return steps.fold(header) { acc, step -> acc + step.toBytes() }
    }

    /** Collapses press/release pairs back into the editor syntax `A+B:100/60`. */
    fun describe(layout: KeyLinkerMaskLayout = KeyLinkerMaskLayout.X20): String {
        val groups = mutableListOf<String>()
        var i = 0
        while (i < steps.size) {
            val press = steps[i]
            val keys = describeMask(press.mask, layout)
            if (keys.isEmpty()) { i++; continue }
            var gap = 0
            if (i + 1 < steps.size && describeMask(steps[i + 1].mask, layout).isEmpty()) { gap = steps[i + 1].durationMs; i += 2 } else i++
            groups += "${keys.joinToString("+")}:${press.durationMs}/$gap"
        }
        return groups.joinToString(",").ifEmpty { "empty" } + if (loopIntervalMs > 0) " loop:$loopIntervalMs" else ""
    }

    companion object {
        const val CHUNK_SIZE = 15
        const val MAX_CHUNKS = 16
        const val MAX_ENTRIES = (MAX_CHUNKS * CHUNK_SIZE - 3) / KeyLinkerMacroStep.SIZE // 47
        /** What the app sends to empty a slot: a bare zero, not an empty header. */
        val CLEAR = byteArrayOf(0)

        fun parse(payload: ByteArray): KeyLinkerMacro {
            require(payload.size >= 3) { "Macro payload too short" }
            val declared = payload[0].u8()
            val loopTicks = ((payload[2].u8() and 0x0F) shl 8) or payload[1].u8()
            val steps = mutableListOf<KeyLinkerMacroStep>()
            var offset = 3
            while (offset + KeyLinkerMacroStep.SIZE <= payload.size && steps.size * KeyLinkerMacroStep.SIZE < declared - 2) {
                steps += KeyLinkerMacroStep.parse(payload.copyOfRange(offset, offset + KeyLinkerMacroStep.SIZE)); offset += KeyLinkerMacroStep.SIZE
            }
            return KeyLinkerMacro(steps, loopTicks * 5, payload[2].u8() shr 7 and 1)
        }

        fun maskFor(keys: List<Int>, sticks: Map<Int, StickDirection> = emptyMap(), layout: KeyLinkerMaskLayout = KeyLinkerMaskLayout.X20): Int {
            var mask = layout.neutral
            sticks.forEach { (stick, direction) ->
                val shift = requireNotNull(layout.analog[stick]) { "${KeyLinkerKey.label(stick)} is not available on this controller" }
                mask = (mask and (0xF shl shift).inv()) or (direction.ordinal and 7 shl shift)
            }
            keys.forEach { code ->
                require(code !in layout.analog) { "${KeyLinkerKey.label(code)} needs a direction" }
                val bit = requireNotNull(layout.digital[code]) { "${KeyLinkerKey.label(code)} is not macro-capable on this controller" }
                mask = mask or (1 shl bit)
            }
            return mask
        }

        fun describeMask(mask: Int, layout: KeyLinkerMaskLayout = KeyLinkerMaskLayout.X20): List<String> =
            layout.digital.filter { (_, bit) -> mask shr bit and 1 == 1 }.map { KeyLinkerKey.label(it.key) } +
                layout.analog.mapNotNull { (stick, shift) ->
                    val nibble = mask shr shift and 0xF
                    if (nibble == KeyLinkerMaskLayout.ANALOG_IDLE) null
                    else "${if (stick == KeyLinkerKey.LSTICK_ANALOG.code) "LS" else "RS"}_${StickDirection.entries[nibble and 7].name}"
                }

        /**
         * Editor syntax from X20CTL: `,` separates steps in time, `+` joins keys, `:hold/gap` in ms.
         * Durations snap to the controller's 5 ms grid.
         */
        fun parseSequence(text: String, defaultHold: Int = 100, defaultGap: Int = 60, layout: KeyLinkerMaskLayout = KeyLinkerMaskLayout.X20): List<KeyLinkerMacroStep> {
            val steps = mutableListOf<KeyLinkerMacroStep>()
            text.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { group ->
                val keysPart = group.substringBefore(":"); val timing = group.substringAfter(":", "").trim()
                var hold = defaultHold; var gap = defaultGap
                if (timing.isNotEmpty()) {
                    hold = requireNotNull(timing.substringBefore("/").trim().toIntOrNull()) { "Could not read the timing in $group" }
                    timing.substringAfter("/", "").trim().takeIf { it.isNotEmpty() }?.let { gap = requireNotNull(it.toIntOrNull()) { "Could not read the gap in $group" } }
                }
                require(hold > 0 && gap >= 0 && hold % 5 == 0 && gap % 5 == 0) { "Durations in $group must be positive multiples of 5 ms" }
                val keys = mutableListOf<Int>(); val sticks = mutableMapOf<Int, StickDirection>()
                keysPart.split("+").map { it.trim().uppercase().replace("-", "_") }.filter { it.isNotEmpty() }.also { require(it.isNotEmpty()) { "No keys in $group" } }.forEach { token ->
                    val prefix = token.substringBefore("_"); val rest = token.substringAfter("_", "")
                    if ((prefix == "LS" || prefix == "RS") && rest.isNotEmpty()) {
                        val stick = if (prefix == "LS") KeyLinkerKey.LSTICK_ANALOG.code else KeyLinkerKey.RSTICK_ANALOG.code
                        val direction = requireNotNull(StickDirection.entries.firstOrNull { it.name == rest }) { "Unknown stick direction $rest" }
                        require(sticks[stick]?.let { it == direction } ?: true) { "A stick can only point one way at a time" }
                        sticks[stick] = direction
                    } else keys += requireNotNull(KeyLinkerKey.entries.firstOrNull { it.name == token }) { "Unknown key $token" }.code
                }
                steps += KeyLinkerMacroStep(maskFor(keys, sticks, layout), hold)
                if (gap > 0) steps += KeyLinkerMacroStep.released(gap, layout)
            }
            require(steps.isNotEmpty()) { "No steps in the sequence" }
            return steps
        }
    }
}

/** One lighting entry: r, g, b, two undocumented bytes kept verbatim, and a light byte. */
data class KeyLinkerLightingEntry(val r: Int, val g: Int, val b: Int, val data4: Int, val data5: Int, val light: Int) {
    val hex get() = "#%02x%02x%02x".format(r, g, b)
    companion object {
        fun parseRecord(body: KeyLinkerBody): List<KeyLinkerLightingEntry> {
            require(body.complete && body.declared % 6 == 0) { "Lighting record is incomplete or misaligned" }
            return body.groups(6).map { KeyLinkerLightingEntry(it[0].u8(), it[1].u8(), it[2].u8(), it[3].u8(), it[4].u8(), it[5].u8()) }
        }
    }
}
