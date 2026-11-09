// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Amjad AAYD
// Ported from X20CTL (https://github.com/AmjadAAYD/x20ctl, GPL-3.0-or-later),
// x20ctl/protocol.py and desktop-dotnet/X20Ctl.Product/X20Codec.cs at revision aac5461.
package br.dev.edgard.easygamepad.protocol.keylinker

import br.dev.edgard.easygamepad.protocol.u8
import kotlin.random.Random

/**
 * KeyLinker configuration framing used by the EasySMX X20 and X20 Pro.
 *
 * This is a different protocol from KRLY: different GATT service, framing, CRC and
 * command numbers. Nothing in this package is shared with [br.dev.edgard.easygamepad.protocol.KrlyCodec].
 *
 * Plain frame, before scrambling:
 * ```
 * 0      opcode
 * 1      length field (low 5 bits: frame length; high 3 bits: rotating counter on writes)
 * 2      serial (echoed by the controller's reply)
 * 3      nonce
 * 4..n-2 payload
 * n-1    CRC-8 (reflected polynomial 0xEB, init 0) over bytes 0..n-2
 * ```
 * The scrambling is obfuscation, not cryptography.
 */
object KeyLinkerCodec {
    const val SERVICE = "d7f010e0-660d-46e9-96c3-19c4148bdab5"
    const val WRITE = "d7f010e1-660d-46e9-96c3-19c4148bdab5"
    const val NOTIFY = "d7f010e2-660d-46e9-96c3-19c4148bdab5"

    /** One frame fits a default 23-byte ATT MTU; the serial key table is 2 × 20 bytes. */
    const val MAX_PACKET = 20
    const val FRAME_OVERHEAD = 5
    private const val CRC_POLY = 0xEB

    val CRC_TABLE: IntArray = IntArray(256) { value ->
        var crc = value
        repeat(8) { crc = if (crc and 1 != 0) ((crc ushr 1) xor CRC_POLY) and 0xFF else (crc ushr 1) and 0xFF }
        crc
    }

    private val SEND_DATA_KEY = intArrayOf(51, 99, 157, 121, 242, 219, 162, 26, 170, 33, 139, 232, 116, 211, 88)
    private val SERIAL_KEY = intArrayOf(
        161, 82, 213, 163, 245, 137, 246, 143, 240, 157, 72, 147, 234, 52,
        49, 186, 195, 77, 198, 235, 73, 96, 216, 163, 218, 42, 83, 141,
        244, 97, 24, 191, 174, 215, 111, 81, 228, 160, 217, 146,
    )

    fun crc8(data: ByteArray, length: Int = data.size): Int {
        var crc = 0
        for (i in 0 until length) crc = CRC_TABLE[crc xor data[i].u8()]
        return crc
    }

    fun scramble(packet: ByteArray): ByteArray {
        require(packet.size in FRAME_OVERHEAD..MAX_PACKET) { "A KeyLinker frame is 5 to 20 bytes, got ${packet.size}" }
        val out = ByteArray(packet.size)
        val b2 = packet[2].u8(); val b3 = packet[3].u8()
        out[0] = (packet[0].u8() xor ((b2 + b3) - 154)).toByte()
        out[1] = (packet[1].u8() xor ((b2 + b3) + 155)).toByte()
        out[2] = ((b2 - 173) xor b3).toByte()
        out[3] = ((b3 + 191) xor 219).toByte()
        // The vendor loop stops one short of the end, leaving the CRC byte alone.
        for (i in 4 until packet.size - 1) out[i] = (packet[i].u8() xor ((b2 + b3) - SEND_DATA_KEY[i - 4])).toByte()
        out[packet.size - 1] = packet[packet.size - 1]
        val offset = (out[3].u8() and 2) * 10
        for (i in packet.indices) out[i] = (out[i].u8() xor SERIAL_KEY[offset + i]).toByte()
        return out
    }

    fun unscramble(packet: ByteArray): ByteArray {
        require(packet.size in FRAME_OVERHEAD..MAX_PACKET) { "A KeyLinker frame is 5 to 20 bytes, got ${packet.size}" }
        val offset = if ((packet[3].u8() xor SERIAL_KEY[3]) and 2 != 0) 20 else 0
        val out = IntArray(packet.size) { packet[it].u8() xor SERIAL_KEY[offset + it] }
        val b3 = ((out[3] xor 219) - 191) and 0xFF
        out[3] = b3
        val b2 = ((out[2] xor b3) + 173) and 0xFF
        out[2] = b2
        out[1] = (out[1] xor ((b2 + b3) + 155)) and 0xFF
        out[0] = (out[0] xor ((b2 + b3) - 154)) and 0xFF
        for (i in 4 until packet.size - 1) out[i] = (out[i] xor ((b2 + b3) - SEND_DATA_KEY[i - 4])) and 0xFF
        return ByteArray(out.size) { out[it].toByte() }
    }

    /** Assemble, checksum and scramble one frame. The nonce is random in real use. */
    fun build(opcode: Int, payload: ByteArray = byteArrayOf(), serial: Int, lengthField: Int = plainLength(payload.size),
        nonce: Int = Random.nextInt(256)): ByteArray {
        require(opcode in 0..255 && serial in 0..255 && lengthField in 0..255 && nonce in 0..255)
        require(payload.size + FRAME_OVERHEAD <= MAX_PACKET) { "Payload of ${payload.size} bytes does not fit one frame" }
        val body = byteArrayOf(opcode.toByte(), lengthField.toByte(), serial.toByte(), nonce.toByte()) + payload
        return scramble(body + crc8(body).toByte())
    }

    fun parse(raw: ByteArray): KeyLinkerFrame {
        val data = unscramble(raw)
        return KeyLinkerFrame(data[0].u8(), data[1].u8(), data[2].u8(), data[3].u8(),
            data.copyOfRange(4, data.size - 1), data[data.size - 1].u8(),
            crc8(data, data.size - 1) == data[data.size - 1].u8(), raw.size)
    }

    /** Null for anything that cannot be a frame (wrong size). */
    fun tryParse(raw: ByteArray): KeyLinkerFrame? = if (raw.size in FRAME_OVERHEAD..MAX_PACKET) parse(raw) else null

    /** CodeHelper.getLength(). */
    fun plainLength(payloadLength: Int) = if (payloadLength <= 0) 5 else payloadLength + 5

    /** CodeHelper.getHostLength(): 3-bit counter, 5-bit length. */
    fun hostLength(payloadLength: Int, counter: Int) =
        if (payloadLength <= 0) 5 else ((counter % 8) shl 5) or ((payloadLength + 5) and 0x1F)

    /** CodeHelper.getSaveButtonSerial(): parity, 4-bit slot/chunk index and a 3-bit counter. */
    fun saveButtonSerial(slot: Int, counter: Int): Int {
        require(slot in 0..15) { "Chunk index must fit four bits" }
        return ((counter % 2) shl 7) or ((slot and 0x0F) shl 3) or (counter % 8)
    }
}

data class KeyLinkerFrame(
    val opcode: Int, val length: Int, val serial: Int, val nonce: Int,
    val payload: ByteArray, val crc: Int, val crcValid: Boolean, val rawSize: Int,
) {
    /** Low five bits of the length field; the top three are a rotating counter. */
    val declaredLength get() = length and 0x1F

    override fun equals(other: Any?) = other is KeyLinkerFrame && opcode == other.opcode && length == other.length &&
        serial == other.serial && nonce == other.nonce && payload.contentEquals(other.payload) && crc == other.crc
    override fun hashCode() = arrayOf<Any>(opcode, length, serial, nonce, payload.contentHashCode(), crc).contentHashCode()
}

/** Opcodes, named after the official app's constants (see X20CTL protocol.py). */
object KeyLinkerOp {
    const val READ_NAME = 0x90
    const val READ_VID_PID_VERSION = 0x91
    const val HOST_GUID = 0x93
    const val HOST_MENU = 0xB0
    const val HOST_STICK = 0xB1
    const val HOST_TRIGGER = 0xB2
    const val HOST_MOTOR = 0xB3
    const val HOST_TURBO = 0xB4
    const val HOST_MACRO = 0xB5
    const val HOST_CHANGEKEY = 0xB6
    const val HOST_LIGHTING = 0xB7

    const val WRITE_STICK = 0x31
    const val WRITE_TRIGGER = 0x32
    const val WRITE_MOTOR = 0x33
    const val WRITE_TURBO = 0x34
    const val WRITE_MACRO = 0x35
    const val WRITE_CHANGEKEY = 0x36
    const val WRITE_LIGHTING = 0x37

    const val RECOVER = 0x16
    const val RESPONSE = 0x19
    const val SET_MODE = 0x1D
    const val NORMAL_MODE = 0x1E
}

/** CodeHelper.getSerial(): odd calls n % 128, even calls n % 128 + 128. */
class KeyLinkerSerials {
    private var serial = 0
    private var host = 8
    private var save = 0
    @Synchronized fun nextSerial(): Int { serial++; return if (serial % 2 == 0) serial % 128 + 128 else serial % 128 }
    /** CodeHelper.countHost: counts down from 8, wrapping. */
    @Synchronized fun nextHost(): Int { if (host == 0) host = 8; return --host }
    /** CodeHelper.countSaveButtons: counts up. */
    @Synchronized fun nextSave(): Int = ++save
}
