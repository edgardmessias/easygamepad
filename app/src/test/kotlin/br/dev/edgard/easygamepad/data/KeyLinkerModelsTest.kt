// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Amjad AAYD
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.ble.LinkPhase
import br.dev.edgard.easygamepad.ble.LinkState
import br.dev.edgard.easygamepad.protocol.ConfigurationProtocol
import br.dev.edgard.easygamepad.protocol.DeviceProfile
import br.dev.edgard.easygamepad.protocol.PadKey
import br.dev.edgard.easygamepad.protocol.keylinker.KeyLinkerBattery
import org.junit.Assert.*
import org.junit.Test

class KeyLinkerModelsTest {
    @Test fun x20ModelsUseKeyLinkerNeverKrly() {
        listOf(DeviceProfile.X20, DeviceProfile.X20_PRO).forEach { profile ->
            val device = ControllerDevice("ble:${profile.id}", profile.name, profile.id, "AA:BB:CC:DD:EE:20", gatt = profile.gatt)
            assertFalse("KRLY editor must stay X10-only", device.supportsConfiguration)
            assertTrue(device.supportsKeyLinker)
            assertTrue(device.hasConfigurationLink)
            assertEquals(ConfigurationProtocol.KEYLINKER, device.configurationProtocol)
            assertFalse(device.usesX10Art)
            assertEquals(profile.id, device.artwork?.modelId)
        }
        val x10 = ControllerDevice("ble:x10", "Sala", DeviceProfile.X10.id, "AA:BB:CC:DD:EE:10")
        assertTrue(x10.supportsConfiguration); assertFalse(x10.supportsKeyLinker)
        assertEquals(ConfigurationProtocol.KRLY, x10.configurationProtocol)
        assertEquals("EasySMX X10", x10.productName)
    }

    @Test fun nameAloneNeverSelectsKeyLinker() {
        val generic = ControllerDevice("hid:3", "EasySMX X20 Pro", inputId = 3)
        assertFalse(generic.hasConfigurationLink)
        assertNull(generic.artwork)
        assertNull(generic.configurationProtocol)
    }

    @Test fun rearButtonsFollowTheModel() {
        assertEquals(listOf(PadKey.M1, PadKey.M2, PadKey.M3, PadKey.M4), ControllerDevice("a", "X20", DeviceProfile.X20.id).rearButtons)
        assertEquals(6, ControllerDevice("b", "X20 Pro", DeviceProfile.X20_PRO.id).rearButtons.size)
    }

    @Test fun savedX20KeepsItsModelAndGatt() {
        val saved = SavedController("AA:BB:CC:DD:EE:20", DeviceProfile.X20_PRO.name, DeviceProfile.X20_PRO.id, gatt = DeviceProfile.X20_PRO.gatt)
        val device = controllerDevices(listOf(saved), emptyList()).single()
        assertEquals(DeviceProfile.X20_PRO.id, device.modelId)
        assertEquals(DeviceProfile.KEYLINKER_GATT, device.gatt)
        assertTrue(device.supportsKeyLinker)
    }

    @Test fun keyLinkerBatteryIsAGaugeOnlyForTheConnectedDevice() {
        val device = ControllerDevice("ble:one", "X20", DeviceProfile.X20.id, address = "one")
        val keyLinker = KeyLinkerState(KeyLinkerPhase.IDENTIFIED, "one", battery = KeyLinkerBattery(3, true, 0x50))
        val ready = LinkState(LinkPhase.READY, "one", protocol = ConfigurationProtocol.KEYLINKER)
        val status = controllerStatus(device, ready, HardwareState(), keyLinker)
        assertEquals(3, status.batteryLevel); assertNull(status.batteryPercent); assertTrue(status.charging)
        assertNull(controllerStatus(device, LinkState(), HardwareState(), keyLinker).batteryLevel)
        assertNull(controllerStatus(device.copy(address = "two"), ready, HardwareState(), keyLinker).batteryLevel)
    }

    @Test fun artworkCatalogFallsBackToGeneric() {
        assertSame(ControllerArtworkCatalog.X20, ControllerArtworkCatalog.forModel("easysmx-x20"))
        assertSame(ControllerArtworkCatalog.X20_PRO, ControllerArtworkCatalog.forModel("easysmx-x20-pro"))
        assertSame(ControllerArtworkCatalog.X10, ControllerArtworkCatalog.forModel("easysmx-x10"))
        assertNull(ControllerArtworkCatalog.forModel(null))
        assertNull(ControllerArtworkCatalog.forModel("easysmx-d10"))
        ControllerArtworkCatalog.ALL.forEach { art ->
            assertEquals(DeviceProfile.find(art.modelId)?.rearButtons, art.rearButtons)
            assertTrue(art.layoutAsset.startsWith("controllers/") && art.layoutAsset.endsWith("-layout.json"))
        }
        assertNotNull(ControllerArtworkCatalog.X10.homeCenter)
        assertNull(ControllerArtworkCatalog.X20.homeCenter) // never guessed for another model
    }
}
