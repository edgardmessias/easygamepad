// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class KrlyParserRobustnessTest {
    @Test
    fun decoderSplitsArbitraryChunkBoundariesDeterministically() {
        val first = bytes(8, 49, 90, 1, 95, 83, 0, 4)
        val second = bytes(4, 61, 0, 5)
        val full = first + second
        val random = Random(15)
        repeat(24) {
            val split = 1 + random.nextInt(full.size - 1)
            val decoder = FrameDecoder()
            val early = decoder.feed(full.copyOfRange(0, split))
            val late = decoder.feed(full.copyOfRange(split, full.size))
            assertEquals(2, early.size + late.size)
            org.junit.Assert.assertArrayEquals(first, (early + late).first())
            org.junit.Assert.assertArrayEquals(second, (early + late).last())
        }
    }

    @Test
    fun decoderResyncsAfterImpossibleLengthBytes() {
        val decoder = FrameDecoder()
        val valid = bytes(3, 49, 0)
        val garbage = bytes(0, 1, 2)
        val frames = decoder.feed(garbage + valid)
        assertEquals(1, frames.size)
        org.junit.Assert.assertArrayEquals(valid, frames[0])
    }

    @Test
    fun decoderBoundsRetainedIncompleteDataWithoutUnboundedGrowth() {
        val decoder = FrameDecoder()
        decoder.feed(bytes(200, 49, 0))
        repeat(6) { decoder.feed(ByteArray(100)) }
        assertEquals(1, decoder.feed(bytes(3, 49, 0)).size)
    }

    @Test
    fun largeFramesDecodeAcrossChunkBoundariesWithoutDroppingCompleteFrames() {
        fun frame200(opcode: Int) = ByteArray(200) { idx ->
            when (idx) {
                0 -> 200.toByte()
                1 -> opcode.toByte()
                else -> 0
            }
        }
        val payload = frame200(49) + frame200(50) + frame200(51)
        val decoder = FrameDecoder()
        val first = decoder.feed(payload.copyOfRange(0, 150))
        val second = decoder.feed(payload.copyOfRange(150, payload.size))
        assertEquals(0, first.size)
        assertEquals(3, second.size)
    }

    @Test
    fun concatenatedFramesSurviveSingleNotification() {
        val decoder = FrameDecoder()
        val frames = decoder.feed(bytes(3, 49, 0, 4, 61, 0, 1))
        assertEquals(2, frames.size)
    }

    @Test
    fun mappingPagesRejectLengthMismatchAndDuplicates() {
        assertThrows(IllegalArgumentException::class.java) {
            MappingPages().accept(bytes(10, 80, 0, 0, 3, 0, 1, 160, 9))
        }
        assertThrows(IllegalArgumentException::class.java) {
            MappingPages().accept(bytes(12, 80, 0, 0, 6, 0, 1, 160, 0, 1, 160, 0))
        }
    }

    @Test
    fun settingsPagesRejectUnsupportedSectionStructure() {
        val content = bytes(3, 160, 1, 12, 4, 5, 95, 10, 90, 9, 2, 98, 1, 0, 3, 97, 0, 1, 0, 2, 60, 80)
        val pages = SettingsPages()
        pages.accept(bytes(16, 119, 2, 2, 1) + content.copyOfRange(0, 10) + bytes(0))
        assertThrows(IllegalArgumentException::class.java) {
            pages.accept(bytes(18, 119, 2, 2, 2) + content.copyOfRange(10, 22) + bytes(1, 99))
        }
    }

    @Test
    fun unknownPadCodesDoNotInventMappings() {
        val pages = MappingPages()
        val result = pages.accept(bytes(12, 80, 0, 0, 6, 0, 1, 200, 0, 2, 200, 0))!!
        assertTrue(result.isEmpty())
    }
}
