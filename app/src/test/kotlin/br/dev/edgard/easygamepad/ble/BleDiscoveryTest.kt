// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class BleDiscoveryTest {
    @Test fun configurationChannelRecognizesQMacro() {
        assertTrue(BleDiscovery.configurationChannelName("QMacro"))
        assertTrue(BleDiscovery.configurationChannelName("  qmacro "))
    }

    @Test fun configurationChannelIgnoresXboxGameplayName() {
        assertFalse(BleDiscovery.configurationChannelName("Xbox Wireless Controller"))
    }

    @Test fun qMacroSortsAheadOfGenericLe() {
        assertEquals(3, BleDiscovery.configurationNamePriority("QMacro"))
        assertEquals(0, BleDiscovery.configurationNamePriority("Bluetooth LE"))
    }
}
