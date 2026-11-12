// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.protocol

import org.junit.Assert.*
import org.junit.Test

class KrlyCodecTest {
    @Test fun batteryRequestMatchesDocumentedBytes() {
        assertArrayEquals(bytes(3, 49, 0), FrameEncoder().encode(49))
    }
    @Test fun realX10CaptureReadsBatteryAndEmptyMapping() {
        val battery = bytes(0x08, 0x31, 0x01, 0x00, 0x01, 0x3C, 0x00, 0x00)
        assertEquals(60, KrlyCodec.battery(battery)!!.percent)
        assertFalse(KrlyCodec.battery(battery)!!.charging)
        assertEquals(emptyMap<PadKey, PadKey>(), MappingPages().accept(bytes(0x06, 0x50, 0x01, 0x01, 0x00, 0x00)))
    }
    @Test fun realX10FirmwareCapture() {
        val frame = bytes(0x12, 0x84, 0x01, 0x00, 0x56, 0x02, 0x87, 0x03, 0x00, 0x40, 0x02, 0x40, 0x71, 0x9A, 0x01, 0x00, 0xA0, 0x02)
        assertEquals(Firmware("560287", "030040", "0240719A", "0100A0"), KrlyCodec.firmware(frame))
    }
    @Test fun sequenceWrapsAtOneByte() {
        val encoder = FrameEncoder()
        repeat(256) { assertEquals(it, encoder.encode(49).last().u8()) }
        assertEquals(0, encoder.encode(49).last().u8())
    }
    @Test fun mappingUsesVendorKeyCodes() {
        assertArrayEquals(bytes(5, 61, 1, 160, 0), FrameEncoder().encode(61, bytes(PadKey.M1.code, PadKey.A.code)))
    }
    @Test fun decoderHandlesChunksAndMultipleFrames() {
        val first = bytes(8, 49, 90, 1, 95, 83, 0, 4)
        val second = bytes(4, 61, 0, 5)
        val decoder = FrameDecoder()
        assertTrue(decoder.feed(first.copyOfRange(0, 3)).isEmpty())
        val frames = decoder.feed(first.copyOfRange(3, 8) + second)
        assertEquals(2, frames.size)
        assertArrayEquals(first, frames[0]); assertArrayEquals(second, frames[1])
    }
    @Test fun decoderResetDropsPartialPacket() {
        val decoder = FrameDecoder(); decoder.feed(bytes(200, 49, 0)); decoder.clear()
        assertEquals(1, decoder.feed(bytes(3, 49, 0)).size)
    }
    @Test fun batteryUsesElectricityNotDisplayOrChargingPercent() {
        val battery = KrlyCodec.battery(bytes(8, 49, 90, 1, 95, 83, 0, 4), 1234)!!
        assertEquals(83, battery.percent); assertTrue(battery.charging); assertFalse(battery.low)
        assertEquals(1234L, battery.receivedAt)
    }
    @Test fun rejectsMalformedAndImpossibleBattery() {
        assertNull(KrlyCodec.battery(byteArrayOf()))
        assertNull(KrlyCodec.battery(bytes(6, 49, 90, 1, 90, 80)))
        assertNull(KrlyCodec.battery(bytes(8, 49, 0, 0, 0, 255, 0, 0)))
        assertNull(KrlyCodec.battery(bytes(8, 50, 0, 0, 0, 50, 0, 0)))
    }
    @Test fun acceptsSevenByteBatteryReport() {
        assertEquals(40, KrlyCodec.battery(bytes(7, 49, 50, 0, 50, 40, 1))!!.percent)
    }
    @Test fun firmwareSlicesReferToHexCharacterOffsets() {
        val frame = bytes(15, 9, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0xaa, 0xbb, 0xcc, 0)
        assertEquals(Firmware("112233", "445566", "778899", "AABBCC"), KrlyCodec.firmware(frame))
    }
    @Test fun calibrationRequiresDeviceSuccessEvent() {
        val active = CalibrationState(CalibrationKind.STICKS, CalibrationPhase.ACTIVE, stopRequired = true)
        assertEquals(CalibrationPhase.ACTIVE, KrlyCodec.calibration(active, bytes(4, 61, 0, 1)).phase)
        assertEquals(CalibrationPhase.NEEDS_RETRY, KrlyCodec.calibration(active, bytes(5, 120, 2, 0, 1)).phase)
        val success = KrlyCodec.calibration(active, bytes(5, 120, 3, 0, 1))
        assertEquals(CalibrationPhase.SUCCEEDED, success.phase)
        assertTrue(success.stopRequired)
    }
    @Test fun currentFirmwareQueryUsesItsOwnOffsetsAndFourByteVersion() {
        val raw = bytes(18, 132, 0, 1, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0xaa, 0xbb, 0xcc, 0xdd, 0)
        assertEquals(Firmware("112233", "445566", "778899AA", "BBCCDD"), KrlyCodec.firmware(raw))
        assertNull(KrlyCodec.firmware(raw.copyOfRange(0, 16)))
    }
    @Test fun lateEventsDoNotChangeInactiveCalibration() {
        val idle = CalibrationState()
        assertEquals(idle, KrlyCodec.calibration(idle, bytes(5, 120, 3, 0, 1)))
    }
    @Test fun selfCalibrationAcceptsOnlySelfResult() {
        val active = CalibrationState(CalibrationKind.SELF, CalibrationPhase.ACTIVE, stopRequired = true)
        assertEquals(active, KrlyCodec.calibration(active, bytes(5, 120, 3, 0, 1)))
        assertEquals(CalibrationPhase.SUCCEEDED, KrlyCodec.calibration(active, bytes(4, 67, 0, 1)).phase)
        assertEquals(CalibrationPhase.FAILED, KrlyCodec.calibration(active, bytes(4, 73, 1, 1)).phase)
    }
    @Test fun macroUsesBigEndianDurationsAndInvertedRepeatFlag() {
        val macro = Macro(PadKey.M2, true, listOf(MacroStep(PadKey.X, 300, 1000)))
        assertArrayEquals(bytes(2, 0, 0, 1, 44, 3, 232, 162), KrlyCodec.macroStep(macro, 0))
        assertEquals(1, KrlyCodec.macroStep(macro.copy(repeat = false), 0)[1].u8())
    }
    @Test fun rearPaddlePassthroughMatchesFactoryMacroFrame() {
        assertArrayEquals(bytes(1, 1, 0, 0, 0, 0, 0, 160), KrlyCodec.rearPaddlePassthrough(PadKey.M1, PadKey.A))
        assertArrayEquals(bytes(2, 1, 0, 0, 0, 0, 0, 163), KrlyCodec.rearPaddlePassthrough(PadKey.M2, PadKey.Y))
    }
    @Test fun stickPayloadMatchesDocumentedAxisOrder() {
        assertArrayEquals(bytes(5, 90, 1, 0, 3, 95, 0, 1), KrlyCodec.sticks(StickSettings(5, 90, true, false), StickSettings(3, 95, false, true)))
    }
    @Test fun mappingPagesAccumulateAndParseTriples() {
        val pages = MappingPages()
        assertNull(pages.accept(bytes(8, 80, 2, 1, 6, 0, 1, 9)))
        val result = pages.accept(bytes(9, 80, 2, 2, 160, 0, 161, 162, 10))!!
        assertEquals(mapOf(PadKey.M1 to PadKey.A, PadKey.B to PadKey.X), result)
    }
    @Test fun mappingPagesRejectReorderedFragments() {
        assertThrows(IllegalArgumentException::class.java) { MappingPages().accept(bytes(6, 80, 2, 2, 0, 9)) }
    }
    @Test fun mappingPagesAlsoAcceptZeroBasedFirmware() {
        val pages = MappingPages()
        assertEquals(mapOf(PadKey.M1 to PadKey.A), pages.accept(bytes(9, 80, 0, 0, 3, 0, 1, 160, 9)))
    }
    @Test fun mappingPagesRejectTruncatedContent() {
        assertThrows(IllegalArgumentException::class.java) { MappingPages().accept(bytes(8, 80, 1, 1, 6, 0, 1, 9)) }
    }
    @Test fun rangeValidationPreventsInvertedOrOverflowSettings() {
        assertThrows(IllegalArgumentException::class.java) { ControllerConfig(leftStick = StickSettings(90, 20)).validate() }
        assertThrows(IllegalArgumentException::class.java) { ControllerConfig(rightVibration = 101).validate() }
        assertThrows(IllegalArgumentException::class.java) { MacroStep(holdMs = 65536).validate() }
        assertThrows(IllegalArgumentException::class.java) { Turbo(rate = 21).validate() }
    }
    @Test fun duplicateMacroTriggersAreRejected() {
        val macro = Macro(steps = listOf(MacroStep()))
        assertThrows(IllegalArgumentException::class.java) { ControllerConfig(macros = listOf(macro, macro)).validate() }
    }
}
