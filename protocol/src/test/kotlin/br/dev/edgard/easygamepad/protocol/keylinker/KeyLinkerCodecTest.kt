// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Amjad AAYD
package br.dev.edgard.easygamepad.protocol.keylinker

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/**
 * Fixtures come from X20CTL revision aac5461:
 *  - "recorded" replies are hardware captures pinned in tests/test_protocol.py and X20CodecTests.cs;
 *  - "reference" frames were produced by x20ctl/protocol.py (or asserted by the C# tests) with a fixed nonce.
 * Matching them proves byte-identical framing with X20CTL, not live Android behaviour.
 */
class KeyLinkerCodecTest {
    private fun hex(s: String) = s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun ints(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }

    /** Verbatim from the official app's CodeHelper, as kept in X20CTL. */
    private val appTable = intArrayOf(
        0, 121, 242, 139, 51, 74, 193, 184, 102, 31, 148, 237, 85, 44, 167, 222, 204, 181, 62, 71, 255, 134, 13, 116, 170, 211, 88, 33, 153, 224, 107, 18,
        79, 54, 189, 196, 124, 5, 142, 247, 41, 80, 219, 162, 26, 99, 232, 145, 131, 250, 113, 8, 176, 201, 66, 59, 229, 156, 23, 110, 214, 175, 36, 93,
        158, 231, 108, 21, 173, 212, 95, 38, 248, 129, 10, 115, 203, 178, 57, 64, 82, 43, 160, 217, 97, 24, 147, 234, 52, 77, 198, 191, 7, 126, 245, 140,
        209, 168, 35, 90, 226, 155, 16, 105, 183, 206, 69, 60, 132, 253, 118, 15, 29, 100, 239, 150, 46, 87, 220, 165, 123, 2, 137, 240, 72, 49, 186, 195,
        235, 146, 25, 96, 216, 161, 42, 83, 141, 244, 127, 6, 190, 199, 76, 53, 39, 94, 213, 172, 20, 109, 230, 159, 65, 56, 179, 202, 114, 11, 128, 249,
        164, 221, 86, 47, 151, 238, 101, 28, 194, 187, 48, 73, 241, 136, 3, 122, 104, 17, 154, 227, 91, 34, 169, 208, 14, 119, 252, 133, 61, 68, 207, 182,
        117, 12, 135, 254, 70, 63, 180, 205, 19, 106, 225, 152, 32, 89, 210, 171, 185, 192, 75, 50, 138, 243, 120, 1, 223, 166, 45, 84, 236, 149, 30, 103,
        58, 67, 200, 177, 9, 112, 251, 130, 92, 37, 174, 215, 111, 22, 157, 228, 246, 143, 4, 125, 197, 188, 55, 78, 144, 233, 98, 27, 163, 218, 81, 40,
    )

    @Test fun generatedCrcTableMatchesTheApp() { assertArrayEquals(appTable, KeyLinkerCodec.CRC_TABLE); assertEquals(0, KeyLinkerCodec.crc8(byteArrayOf())) }

    @Test fun identityQueryIsByteIdenticalToX20ctl() =
        assertArrayEquals(ints(87, 203, 129, 199, 94), KeyLinkerCodec.build(KeyLinkerOp.READ_VID_PID_VERSION, serial = 1, nonce = 0))

    @Test fun powerQueryIsByteIdenticalToX20ctl() =
        assertArrayEquals(ints(212, 199, 128, 199, 56, 201, 56, 230), KeyLinkerCodec.build(KeyLinkerOp.SET_MODE, KeyLinkerEvidence.POWER_PAYLOAD, 2, nonce = 0))

    @Test fun checkQueriesAreByteIdenticalToX20ctl() {
        assertArrayEquals(ints(90, 201, 128, 199, 58, 107), KeyLinkerCodec.build(KeyLinkerOp.HOST_GUID, ints(0), 2, nonce = 0))
        assertArrayEquals(ints(91, 202, 131, 199, 36, 224), KeyLinkerCodec.build(KeyLinkerOp.HOST_GUID, ints(1), 3, nonce = 0))
        assertArrayEquals(ints(123, 202, 130, 199, 36, 41, 163), KeyLinkerCodec.build(KeyLinkerOp.HOST_MENU, ints(0, 1), 4, nonce = 0))
        assertArrayEquals(ints(117, 200, 129, 199, 59, 65), KeyLinkerCodec.build(KeyLinkerOp.HOST_MOTOR, ints(0), 1, nonce = 0))
    }

    @Test fun recordedX20IdentityReplyDecodes() {
        val raw = hex("268cb68ef74c7e10bfc1fddf62")
        val frame = KeyLinkerCodec.parse(raw)
        assertEquals(KeyLinkerOp.RESPONSE, frame.opcode); assertTrue(frame.crcValid); assertEquals(1, frame.serial)
        assertEquals("0710132009012352", frame.payload.hex()); assertEquals(raw.size, frame.declaredLength)
        val id = KeyLinkerIdentity.parse(frame.payload)
        assertEquals(KeyLinkerIdentity("1013", "2009", "1.23", 2, 1, 2, "10132009012352"), id)
        assertEquals(2, id.effectiveGeneration)
    }

    @Test fun recordedX20ProIdentityReplyDecodes() {
        val raw = ints(0xCA, 0xF8, 0x8A, 0xB2, 0x2B, 0x30, 0xB9, 0x3C, 0xEC, 0xAD, 0x66, 0x37, 0x9A)
        val frame = KeyLinkerCodec.parse(raw)
        assertTrue(frame.crcValid); assertEquals(1, frame.serial); assertEquals(11, frame.nonce); assertEquals(raw.size, frame.declaredLength)
        val id = KeyLinkerIdentity.parse(frame.payload)
        assertEquals("1020", id.vid); assertEquals("2006", id.pid); assertEquals("1.44", id.firmware); assertEquals(6, id.mode)
        assertEquals(KeyLinkerClassification.VERIFIED, KeyLinkerEvidence.assess(id).classification)
        assertEquals("easysmx-x20-pro", KeyLinkerEvidence.assess(id).known?.modelId)
    }

    @Test fun recordedX20ProPowerReplyDecodes() {
        val raw = ints(0x9D, 0x6D, 0xE8, 0x5C, 0xE7, 0xF1, 0x33, 0x0A, 0x11, 0x04, 0xDD, 0xF2, 0x13, 0xCF, 0x7E)
        val frame = KeyLinkerCodec.parse(raw)
        assertTrue(frame.crcValid); assertEquals(2, frame.serial); assertEquals(10, frame.payload.size)
        assertEquals(KeyLinkerBattery(4, true, 0x90), KeyLinkerBattery.parse(KeyLinkerBody.unwrap(frame.payload)))
    }

    @Test fun noopRemapWriteMatchesTheBytesHardwareAccepted() {
        val packet = KeyLinkerCodec.build(KeyLinkerOp.WRITE_CHANGEKEY, ints(0), KeyLinkerCodec.saveButtonSerial(0, 1),
            KeyLinkerCodec.hostLength(1, 7), 0x5A)
        assertEquals("36e6815a00ae", KeyLinkerCodec.unscramble(packet).hex())
        val frame = KeyLinkerCodec.parse(packet)
        assertEquals(0x81, frame.serial); assertEquals(7, frame.length shr 5); assertEquals(6, frame.declaredLength); assertTrue(frame.crcValid)
    }

    @Test fun countersFollowTheOfficialApp() {
        val s = KeyLinkerSerials()
        assertEquals(listOf(1, 130, 3, 132, 5, 134), List(6) { s.nextSerial() })
        assertEquals(0b1_0000_001, KeyLinkerCodec.saveButtonSerial(0, 1))
        assertEquals(0b0_0101_010, KeyLinkerCodec.saveButtonSerial(5, 2))
        assertEquals(11, KeyLinkerCodec.hostLength(6, 7) and 0x1F); assertEquals(7, KeyLinkerCodec.hostLength(6, 7) shr 5)
        assertEquals(5, KeyLinkerCodec.hostLength(0, 3)); assertEquals(238, KeyLinkerCodec.hostLength(9, 7))
        val h = KeyLinkerSerials(); assertEquals(listOf(7, 6, 5, 4, 3, 2, 1, 0, 7), List(9) { h.nextHost() })
    }

    @Test fun scrambleRoundTripsEveryAllowedLength() {
        val random = Random(1234)
        for (length in 6..KeyLinkerCodec.MAX_PACKET) repeat(50) {
            val body = ByteArray(length - 1) { random.nextInt(256).toByte() }
            val packet = body + KeyLinkerCodec.crc8(body).toByte()
            assertArrayEquals(packet, KeyLinkerCodec.unscramble(KeyLinkerCodec.scramble(packet)))
        }
    }

    @Test fun malformedFramesAreRejected() {
        assertNull(KeyLinkerCodec.tryParse(ByteArray(4)))
        assertNull(KeyLinkerCodec.tryParse(ByteArray(21)))
        assertThrows(IllegalArgumentException::class.java) { KeyLinkerCodec.build(0x33, ByteArray(16), 1, nonce = 0) }
        val bad = hex("268cb68ef74c7e10bfc1fddf62").also { it[it.size - 1] = (it.last().toInt() xor 1).toByte() }
        assertFalse(KeyLinkerCodec.parse(bad).crcValid)
    }

    @Test fun motorWritePlanMatchesX20ctlVibrationTest() {
        val s = KeyLinkerSerials()
        val record = KeyLinkerMotorRecord(8, hex("4c4c0000c0d40100"))
        val write = KeyLinkerClient.planMotor(record.withVibration(80), s, KeyLinkerFeature.VIBRATION, "80%") { 0 }
        assertArrayEquals(ints(117, 160, 1, 199, 179, 91, 222, 135, 127, 251, 67, 245, 61, 247), write.packets.single())
        val restore = KeyLinkerClient.planMotor(record, s, KeyLinkerFeature.VIBRATION, "restore") { 0 }
        assertArrayEquals(ints(250, 1, 128, 199, 50, 90, 223, 6, 224, 122, 252, 122, 178, 50), restore.packets.single())
    }

    @Test fun macroPlanMatchesX20ctlReferenceFrames() {
        val steps = listOf(KeyLinkerKey.A, KeyLinkerKey.B).flatMap { listOf(KeyLinkerMacroStep(KeyLinkerMacro.maskFor(listOf(it.code)), 100), KeyLinkerMacroStep.released(60)) }
        val macro = KeyLinkerMacro(steps)
        assertEquals("16000088100014008800000c0088200014008800000c00", macro.payload().hex())
        val plan = KeyLinkerClient.planMacro(2, macro, KeyLinkerSerials()) { 7 }
        assertEquals(listOf("7a2506beb6ac1d087630bafdbc53cc16d770d63c", "e3b38fbe2b33829fefab2b646a"), plan.packets.map { it.hex() })
        plan.packets.forEachIndexed { i, p -> val f = KeyLinkerCodec.parse(p); assertEquals(i, f.serial shr 3 and 0xF); assertEquals(2, f.length shr 5) }
        assertEquals("A:100/60,B:100/60", KeyLinkerMacro.parse(macro.payload()).describe())
    }

    @Test fun remapPlanMatchesX20ctlReferenceFrames() {
        val sources = KeyLinkerKeyList.X20_CHANGEKEY_SOURCES
        // Reference built with save counters 1, 2 and host counters 7, 6 (a fresh X20CTL client).
        val plan = KeyLinkerClient.planRemaps(sources, mapOf(1 to 2), KeyLinkerSerials()) { 3 }
        assertEquals(listOf("7db902bab4aa11846234aaf93057c826d3fceab2", "e43d8bba2f23e2"), plan.packets.map { it.hex() })
        assertThrows(IllegalArgumentException::class.java) { KeyLinkerClient.planRemaps(sources, mapOf(93 to 1), KeyLinkerSerials()) }
    }

    @Test fun stickPlanMatchesX20ctlReferenceFrame() {
        val linear = KeyLinkerCurve.parse(hex("08085555aaaa00"), KeyLinkerCurve.STICK_MAX_PROGRESS)
        val plan = KeyLinkerClient.planCurves(true, listOf(linear, linear.withPreset("quick")), KeyLinkerSerials()) { 9 }
        assertEquals("88b105b08305b6c939645acf46b6b3d07adbebc9", plan.packets.single().hex())
    }
}
