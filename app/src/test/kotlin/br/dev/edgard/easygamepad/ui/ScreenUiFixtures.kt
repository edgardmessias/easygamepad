// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import br.dev.edgard.easygamepad.ble.LinkPhase
import br.dev.edgard.easygamepad.data.ControllerDevice
import br.dev.edgard.easygamepad.protocol.CalibrationPhase
import br.dev.edgard.easygamepad.protocol.CalibrationKind
import br.dev.edgard.easygamepad.protocol.CalibrationState
import br.dev.edgard.easygamepad.protocol.DeviceProfile

/** Deterministic UI snapshots for controller-free tests; no BLE auto-connect. */
object ScreenUiFixtures {
    val savedX10Disconnected = ControllerDevice("ble:aa:11", "Sala X10", DeviceProfile.X10.id, "aa:bb:cc:11:22:33")
    val genericGameplay = ControllerDevice("hid:4", "Xbox pad", inputId = 4)
    val connectedX10Ready = savedX10Disconnected

    fun pickerEmpty() = ControllerPickerUiState(emptyList(), null, LinkPhase.DISCONNECTED, null, canSwitch = true)
    fun pickerSwitchBlocked() = ControllerPickerUiState(
        listOf(savedX10Disconnected, genericGameplay), savedX10Disconnected, LinkPhase.READY, savedX10Disconnected.address, canSwitch = false,
    )
    fun pickerSavedX10Disconnected() = ControllerPickerUiState(
        listOf(savedX10Disconnected), savedX10Disconnected, LinkPhase.DISCONNECTED, null,
    )
    fun configurationNoDevice() = ConfigurationUnavailableUiState(null, gameplayLinked = false, hasBleAddress = false)
    fun configurationGenericSelected() = ConfigurationUnavailableUiState("Xbox pad", gameplayLinked = true, hasBleAddress = false)

    fun profilesDisconnectedValid() = ProfilesEditorUiState(LinkPhase.DISCONNECTED, busy = false, draftValid = true)
    fun profilesBusyOnReady() = ProfilesEditorUiState(LinkPhase.READY, busy = true, draftValid = true)
    fun profilesCalibrationStop() = ProfilesEditorUiState(
        LinkPhase.READY, busy = false,
        calibration = CalibrationState(CalibrationKind.STICKS, CalibrationPhase.ACTIVE, stopRequired = true),
        draftValid = true,
    )
    fun profilesReadyToApply() = ProfilesEditorUiState(LinkPhase.READY, busy = false, draftValid = true)
}
