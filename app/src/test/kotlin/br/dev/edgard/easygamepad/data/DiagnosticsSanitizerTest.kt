// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.ble.BleLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsSanitizerTest {
    @Test
    fun masksMacAddressesByDefault() {
        val line = "Connected to AA:BB:CC:DD:EE:FF with 03 1A 2B 3C 4D"
        val sanitized = DiagnosticsSanitizer.sanitizeLine(line, includeRawPayloads = false)
        assertTrue(sanitized.contains("<controller>"))
        assertFalse(sanitized.contains("AA:BB"))
        assertTrue(sanitized.contains("<payload>"))
    }

    @Test
    fun rawOptInKeepsHexPayloadsButStillMasksAddresses() {
        val line = "TX AA:BB:CC:DD:EE:FF 01 02 03 04"
        val sanitized = DiagnosticsSanitizer.sanitizeLine(line, includeRawPayloads = true)
        assertTrue(sanitized.contains("01 02 03 04"))
        assertFalse(sanitized.contains("AA:BB"))
    }

    @Test
    fun exportCapsAt200EntriesAndDocumentsRawChoice() {
        val logs = (1..250).map { BleLog(time = it.toLong(), direction = "RX", message = "line $it") }
        val export = DiagnosticsSanitizer.buildExport("1.0", "14", logs, includeRawPayloads = false)
        assertTrue(export.contains("Raw payloads: no"))
        assertEquals(200, export.lines().count { it.contains("RX line") })
    }
}
