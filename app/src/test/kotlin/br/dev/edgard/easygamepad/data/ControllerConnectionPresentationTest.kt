// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.ble.LinkPhase
import br.dev.edgard.easygamepad.protocol.DeviceProfile
import org.junit.Assert.*
import org.junit.Test

class ControllerConnectionPresentationTest {
    private val x10 = ControllerDevice("ble:1", "Living room", DeviceProfile.X10.id, "aa:bb:cc:11:22:33")
    private val gameplay = ControllerDevice("hid:9", "Xbox Wireless Controller", inputId = 9, inputDescriptor = "usb:1")

    @Test fun scanSituationOrdersPermissionBeforeBluetooth() {
        val ctx = ControllerConnectionContext(permissionsGranted = false, bluetoothEnabled = false)
        assertEquals(BleScanSituation.PERMISSION_REQUIRED, ctx.bleScanSituation())
        assertEquals(BleScanSituation.PERMISSION_DENIED,
            ControllerConnectionContext(permissionsGranted = false, permissionDeniedThisSession = true).bleScanSituation())
    }

    @Test fun scanSituationRequiresLocationOnLegacyAndroidPath() {
        val ctx = ControllerConnectionContext(locationEnabled = false, permissionsGranted = true, bluetoothEnabled = true)
        assertEquals(BleScanSituation.LOCATION_REQUIRED, ctx.bleScanSituation())
    }

    @Test fun configurationReadyOnlyForMatchingAddress() {
        val ctx = ControllerConnectionContext(linkPhase = LinkPhase.READY, linkAddress = "aa:bb:cc:11:22:33")
        assertEquals(ConfigurationLinkSituation.READY, configurationLinkSituation(x10, ctx))
        assertEquals(ConfigurationLinkSituation.SAVED_OFFLINE,
            configurationLinkSituation(x10, ctx.copy(linkAddress = "00:11:22:33:44:55")))
    }

    @Test fun connectingPhaseSurfacedForSelectedDevice() {
        val ctx = ControllerConnectionContext(linkPhase = LinkPhase.CONNECTING, linkAddress = x10.address)
        assertEquals(ConfigurationLinkSituation.CONNECTING, configurationLinkSituation(x10, ctx))
        assertEquals(SavedDeviceLinkLabel.APP_CONNECTING, savedDeviceLinkLabel(x10, selected = true, ctx))
    }

    @Test fun genericInputWithoutBleIsGameplayLinked() {
        assertEquals(ConfigurationLinkSituation.NOT_APPLICABLE, configurationLinkSituation(gameplay, ControllerConnectionContext()))
        assertEquals(SavedDeviceLinkLabel.GAMEPLAY_LINKED, savedDeviceLinkLabel(gameplay, selected = true, ControllerConnectionContext()))
    }

    @Test fun gameplayBatteryMissingVsZero() {
        assertTrue(gameplayBatteryHint(null, false) is GameplayBatteryHint.MISSING)
        val zero = gameplayBatteryHint(0, false) as GameplayBatteryHint.REPORTED
        assertEquals(0, zero.percent)
    }

    @Test fun ambiguousDescriptorWhenBlankOrDuplicated() {
        assertTrue(ambiguousInputDescriptor("", 1))
        assertTrue(ambiguousInputDescriptor("usb:1", 2))
        assertFalse(ambiguousInputDescriptor("usb:unique", 1))
    }

    @Test fun linkErrorMarksConfigurationError() {
        val ctx = ControllerConnectionContext(linkPhase = LinkPhase.DISCONNECTED, linkAddress = x10.address, linkError = "timeout")
        assertEquals(ConfigurationLinkSituation.ERROR, configurationLinkSituation(x10, ctx))
    }
}
