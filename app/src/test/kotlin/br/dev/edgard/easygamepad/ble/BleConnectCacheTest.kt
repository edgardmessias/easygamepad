// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleConnectCacheTest {
    @Test fun freshObservationHonorsWindow() {
        val now = 100_000L
        assertTrue(BleConnectCache.isFreshObservation(now - 29_999, now))
        assertFalse(BleConnectCache.isFreshObservation(now - 30_000, now))
    }

    @Test fun mergeKeepsBondedWhenCandidateReplacesExisting() {
        val existing = FoundDevice("aa:bb:cc:dd:ee:ff", "aa:bb:cc:dd:ee:ff", bonded = true, candidate = false)
        val incoming = FoundDevice("aa:bb:cc:dd:ee:ff", "QMacro", rssi = -55, bonded = false, candidate = true)
        val merged = mergeFoundDevice(existing, incoming)
        assertTrue(merged.bonded)
        assertTrue(merged.candidate)
        assertEquals("QMacro", merged.name)
    }

    @Test fun mergeOrBondedFlags() {
        val existing = FoundDevice("11:22:33:44:55:66", "X10", bonded = false)
        val incoming = FoundDevice("11:22:33:44:55:66", "X10", bonded = true)
        assertTrue(mergeFoundDevice(existing, incoming).bonded)
    }
}
