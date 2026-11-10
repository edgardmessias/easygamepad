// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import br.dev.edgard.easygamepad.ble.LinkPhase
import org.junit.Assert.*
import org.junit.Test

class ControllerPickerUiStateTest {
    @Test fun configurationReadyOnlyForMatchingAddress() {
        val fixture = ScreenUiFixtures.pickerSavedX10Disconnected()
        val device = requireNotNull(fixture.selected)
        assertFalse(fixture.configurationReadyFor(device))
        val connected = fixture.copy(linkPhase = LinkPhase.READY, linkAddress = device.address)
        assertTrue(connected.configurationReadyFor(device))
        assertFalse(connected.configurationReadyFor(device.copy(address = "00:11:22:33:44:55")))
    }

    @Test fun switchBlockedIsExposedForBanner() {
        assertTrue(ScreenUiFixtures.pickerSwitchBlocked().switchBlocked)
        assertFalse(ScreenUiFixtures.pickerEmpty().switchBlocked)
    }

    @Test fun emptyListFlag() {
        assertTrue(ScreenUiFixtures.pickerEmpty().emptyDeviceList)
        assertFalse(ScreenUiFixtures.pickerSwitchBlocked().emptyDeviceList)
    }

    @Test fun noopActionsDoNotThrow() {
        ControllerPickerActions.noop.onChooseDevice(ScreenUiFixtures.savedX10Disconnected)
        ControllerPickerActions.noop.onAddController(br.dev.edgard.easygamepad.protocol.DeviceProfile.X10, false)
        ControllerPickerActions.noop.onRenameDevice(ScreenUiFixtures.savedX10Disconnected, "Test")
        ControllerPickerActions.noop.onLinkGameplay(1)
    }
}
