// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Amjad AAYD
package br.dev.edgard.easygamepad.protocol.keylinker

import org.junit.Assert.*
import org.junit.Test

/** Record decoders against the X20 replies X20CTL recorded (tests/test_protocol.py, revision aac5461). */
class KeyLinkerRecordsTest {
    private fun hex(s: String) = s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 255) }
    private val guidFirst = hex("12a1ca51da724cb6103658c05d7d4e")

    @Test fun identityNeedsTheWholeRecord() {
        assertThrows(IllegalArgumentException::class.java) { KeyLinkerIdentity.parse(hex("07101320090123")) }
        val six = KeyLinkerIdentity.parse(hex("06101320090123"))
        assertNull(six.mode); assertEquals("1.23", six.firmware)
    }

    @Test fun hostGuidIdentifiesX20AndX20Pro() {
        val x20 = KeyLinkerHostGuid.parse(KeyLinkerBody(18, KeyLinkerBody.unwrap(guidFirst).data + hex("20917b00")))
        assertEquals("EasySMX X20", x20.model); assertEquals("2091", x20.modelCode); assertEquals("1.23", x20.version)
        val pro = KeyLinkerHostGuid.parse(KeyLinkerBody(18, KeyLinkerBody.unwrap(guidFirst).data + hex("23959000")))
        assertEquals("EasySMX X20 Pro", pro.model); assertEquals("1.44", pro.version)
        assertNull(KeyLinkerHostGuid.parse(KeyLinkerBody(18, ByteArray(14) + hex("20917b00"))).model)
        assertThrows(IllegalArgumentException::class.java) { KeyLinkerHostGuid.parse(KeyLinkerBody.unwrap(guidFirst)) }
    }

    @Test fun x20CapabilityRecord() {
        val caps = KeyLinkerCapabilities.parse(KeyLinkerBody.unwrap(hex("0a030303000f010002c000")))
        assertTrue(caps.leftStick && caps.rightStick && caps.leftTrigger && caps.rightTrigger)
        assertEquals(2, caps.motorCount); assertEquals(listOf("M1", "M2", "M3", "M4"), caps.macroSlots)
        assertEquals(0, caps.turbo); assertEquals(0, caps.lighting); assertEquals(0, caps.sensor)
        assertTrue(caps.newMacroEngine)
        assertThrows(IllegalArgumentException::class.java) { KeyLinkerCapabilities.parse(KeyLinkerBody(3, byteArrayOf(1, 2, 3))) }
    }

    @Test fun recordedCurvesRoundTrip() {
        val sticks = KeyLinkerCurve.parseRecord(KeyLinkerBody.unwrap(hex("0e08085555aaaa0008085555aaaa00")), KeyLinkerCurve.STICK_MAX_PROGRESS)
        assertEquals(2, sticks.size); assertEquals("default", sticks[0].presetName)
        assertEquals(8, sticks[0].innerDeadzone); assertEquals(92, sticks[0].outerDeadzone)
        assertEquals("0e08085555aaaa0008085555aaaa00", KeyLinkerCurve.buildRecord(sticks).hex())
        val triggers = KeyLinkerCurve.parseRecord(KeyLinkerBody.unwrap(hex("0e04225285e5eb0004225285e5eb00")), KeyLinkerCurve.TRIGGER_MAX_PROGRESS)
        assertNull(triggers[0].presetName)
        assertEquals("0e04225285e5eb0004225285e5eb00", KeyLinkerCurve.buildRecord(triggers).hex())
        assertEquals("medium", triggers[0].withDeadzones(20, 40).gearName)
    }

    @Test fun curveValidationRefusesImpossibleValues() {
        val base = KeyLinkerCurve(8, 8, 85 to 85, 170 to 170)
        assertThrows(IllegalArgumentException::class.java) { base.withDeadzones(60, 50).validate() }
        assertThrows(IllegalArgumentException::class.java) { base.copy(point1 = 200 to 10, point2 = 100 to 10).validate() }
        val flipped = base.copy(flags = 0x84).withFlags(invertX = true)
        assertEquals(0x85, flipped.flags) // unknown bits preserved
    }

    @Test fun curveShapeMatchesX20ctl() {
        val shape = KeyLinkerCurve(0, 0, 35 to 35, 219 to 145).shape(5)
        val expected = listOf(0.0 to 0.0, 63.75 to 50.698, 127.5 to 68.889, 191.25 to 107.921, 255.0 to 255.0)
        shape.zip(expected).forEach { (a, e) -> assertEquals(e.first, a.first.toDouble(), 1e-3); assertEquals(e.second, a.second.toDouble(), 1e-3) }
    }

    @Test fun motorRecordCarriesVibrationAndIdleTimeout() {
        val record = KeyLinkerMotorRecord.parse(KeyLinkerBody.unwrap(hex("084c4c0000c0d40100")))
        assertEquals(30, record.leftPercent); assertEquals(30, record.rightPercent); assertEquals(10, record.shutdownMinutes)
        assertNull(record.withShutdown(null).shutdownMinutes)
        assertEquals("ffffffff", record.withShutdown(null).data.copyOfRange(4, 8).hex())
        assertEquals(25, record.withShutdown(25).shutdownMinutes)
        assertThrows(IllegalArgumentException::class.java) { record.withShutdown(31) }
        assertEquals(204, KeyLinkerMotorRecord.toLevel(80))
        assertThrows(IllegalArgumentException::class.java) { KeyLinkerMotorRecord(2, hex("4c4c")).shutdownMinutes }
    }

    @Test fun keyListsStopAtTheirCount() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 13, 14, 15, 16, 11, 12, 93, 94), KeyLinkerKeyList.decode(hex("1001880d840b825d82")))
        assertEquals(KeyLinkerKeyList.X20_MACRO_KEYS, KeyLinkerKeyList.decode(hex("1212130d8401880b825d82")))
        assertEquals(12, KeyLinkerKeyList.decode(hex("0c01880d840c01880d84")).size)
        assertThrows(IllegalArgumentException::class.java) { KeyLinkerKeyList.decode(hex("0581")) }
        assertThrows(IllegalArgumentException::class.java) { KeyLinkerKeyList.decode(hex("0501")) }
    }

    @Test fun remapRecordEncodesUnchangedAsZero() {
        assertEquals("0402000000", KeyLinkerRemap.buildPayload(listOf(1, 2, 3, 4), listOf(2, 2, 3, 4)).hex())
        assertEquals(mapOf(1 to 2), KeyLinkerRemap.parse(listOf(1, 2, 3, 4), hex("02000000")))
        assertThrows(IllegalArgumentException::class.java) { KeyLinkerRemap.parse(listOf(1, 2, 3), hex("02")) }
    }

    @Test fun macroMaskNeverDrivesSticksByAccident() {
        assertEquals(0x88, KeyLinkerMaskLayout.X20.neutral)
        assertEquals(0x88, KeyLinkerMacroStep.released(60).mask)
        val step = KeyLinkerMacro.parseSequence("A+LS_UP:100/0")
        assertEquals(listOf("A", "LS_UP"), KeyLinkerMacro.describeMask(step.single().mask))
        assertThrows(IllegalArgumentException::class.java) { KeyLinkerMacro.parseSequence("A:12") }
        assertThrows(IllegalArgumentException::class.java) { KeyLinkerMacro.parseSequence("HOME") }
        assertThrows(IllegalArgumentException::class.java) { KeyLinkerMacro.parseSequence("LS_UP+LS_DOWN") }
        assertEquals("A+B:150/40,X:100/60", KeyLinkerMacro(KeyLinkerMacro.parseSequence("A+B:150/40, X")).describe())
    }

    @Test fun macroLimitsAreTheHardwareCeiling() {
        assertEquals(47, KeyLinkerMacro.MAX_ENTRIES)
        assertThrows(IllegalArgumentException::class.java) { KeyLinkerMacro(List(48) { KeyLinkerMacroStep.released(5) }).payload() }
        assertTrue(KeyLinkerMacro.parse(hex("020000")).steps.isEmpty())
    }

    @Test fun recordedLightingRecordHasFourEntries() {
        val entries = KeyLinkerLightingEntry.parseRecord(KeyLinkerBody.unwrap(hex("18ff00000000ff0000ff1010ff0000ff40004080808 0a080ff")))
        assertEquals(4, entries.size); assertEquals("#ff0000", entries[0].hex)
        assertThrows(IllegalArgumentException::class.java) { KeyLinkerLightingEntry.parseRecord(KeyLinkerBody(24, ByteArray(12))) }
    }
}
