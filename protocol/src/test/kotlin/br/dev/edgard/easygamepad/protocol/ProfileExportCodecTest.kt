// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.protocol

import org.junit.Assert.*
import org.junit.Test

class ProfileExportCodecTest {
    private val sample = ControllerConfig(
        name = "Sala café ☕",
        mappings = mapOf(PadKey.M3 to PadKey.X, PadKey.A to PadKey.B),
        macros = listOf(Macro(PadKey.M1, steps = listOf(MacroStep(PadKey.Y, 120, 80)))),
        turbos = listOf(Turbo(PadKey.A, rate = 12)),
        leftVibration = 25,
        rightVibration = 80,
    )

    @Test fun roundTripPreservesUnicodeAndLegacyM3Mapping() {
        val json = ProfileExportCodec.export(sample)
        val restored = ProfileExportCodec.import(json)
        assertEquals(sample.name, restored.name)
        assertEquals(PadKey.X, restored.mappings[PadKey.M3])
        assertEquals(12, restored.turbos.single().rate)
    }

    @Test fun unknownConfigKeysAreIgnored() {
        val root = ProfileExportCodec.encodeConfig(ControllerConfig(name = "Test"))
        root.put("futureField", "ignored")
        val wrapped = org.json.JSONObject()
            .put("format", ProfileExportCodec.FORMAT)
            .put("version", ProfileExportCodec.VERSION)
            .put("device", DeviceProfile.X10.id)
            .put("config", root)
        assertEquals("Test", ProfileExportCodec.import(wrapped.toString()).name)
    }

    @Test fun rejectsM5MappingSource() {
        val root = ProfileExportCodec.encodeConfig(ControllerConfig())
        root.getJSONObject("mappings").put("M5", "A")
        val wrapped = org.json.JSONObject()
            .put("format", ProfileExportCodec.FORMAT)
            .put("version", ProfileExportCodec.VERSION)
            .put("device", DeviceProfile.X10.id)
            .put("config", root)
        assertThrows(IllegalArgumentException::class.java) { ProfileExportCodec.import(wrapped.toString()) }
    }

    @Test fun rejectsNonOutputMappingTarget() {
        val root = ProfileExportCodec.encodeConfig(ControllerConfig())
        root.getJSONObject("mappings").put("A", "M1")
        val wrapped = org.json.JSONObject()
            .put("format", ProfileExportCodec.FORMAT)
            .put("version", ProfileExportCodec.VERSION)
            .put("device", DeviceProfile.X10.id)
            .put("config", root)
        assertThrows(IllegalArgumentException::class.java) { ProfileExportCodec.import(wrapped.toString()) }
    }

    @Test fun rejectsWrongDeviceEnvelope() {
        val json = ProfileExportCodec.export(sample).replace(DeviceProfile.X10.id, DeviceProfile.X20.id)
        assertThrows(IllegalArgumentException::class.java) { ProfileExportCodec.import(json) }
    }

    @Test fun rejectsMalformedJson() {
        assertThrows(Exception::class.java) { ProfileExportCodec.import("{") }
    }

    @Test fun rejectsOversizedPayload() {
        val huge = ProfileExportCodec.export(sample) + " ".repeat(ProfileExportCodec.MAX_BYTES)
        assertThrows(IllegalArgumentException::class.java) { ProfileExportCodec.import(huge) }
    }

    @Test fun rejectsDuplicateTurboKeyAfterImport() {
        val root = ProfileExportCodec.encodeConfig(ControllerConfig(turbos = listOf(Turbo(PadKey.A), Turbo(PadKey.A, automatic = true))))
        val wrapped = org.json.JSONObject()
            .put("format", ProfileExportCodec.FORMAT)
            .put("version", ProfileExportCodec.VERSION)
            .put("device", DeviceProfile.X10.id)
            .put("config", root)
        assertThrows(IllegalArgumentException::class.java) { ProfileExportCodec.import(wrapped.toString()) }
    }
}
