// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import br.dev.edgard.easygamepad.ble.LinkPhase
import br.dev.edgard.easygamepad.data.ControllerDevice
import br.dev.edgard.easygamepad.protocol.CalibrationState
import br.dev.edgard.easygamepad.protocol.ConfigurationProtocol

/** Immutable KRLY profile editor chrome derived from link and hardware, without ViewModel access. */
data class ProfilesEditorUiState(
    val linkPhase: LinkPhase = LinkPhase.DISCONNECTED,
    val configurationLinkSelected: Boolean = false,
    val busy: Boolean = false,
    val calibration: CalibrationState = CalibrationState(),
    val draftValid: Boolean = true,
) {
    val configurationReady: Boolean get() = configurationLinkSelected && !busy && !calibration.stopRequired
    val canSaveProfile: Boolean get() = draftValid
    val canReadFromDevice: Boolean get() = configurationReady
    val canApplyToDevice: Boolean get() = configurationReady && draftValid
    val canApplyOrReadFromDevice: Boolean get() = canApplyToDevice
}

fun profilesConfigurationLinkSelected(
    selected: ControllerDevice?,
    linkPhase: LinkPhase,
    linkAddress: String?,
    linkProtocol: ConfigurationProtocol,
    switching: Boolean,
): Boolean {
    val device = selected ?: return false
    if (!device.supportsConfiguration || switching) return false
    val address = device.address ?: return false
    return address.equals(linkAddress, ignoreCase = true) &&
        linkPhase == LinkPhase.READY &&
        linkProtocol == ConfigurationProtocol.KRLY
}

fun profilesEditorUiState(
    selected: ControllerDevice?,
    linkPhase: LinkPhase,
    linkAddress: String?,
    linkProtocol: ConfigurationProtocol,
    switching: Boolean,
    busy: Boolean,
    calibration: CalibrationState,
    draftValid: Boolean,
): ProfilesEditorUiState = ProfilesEditorUiState(
    linkPhase,
    profilesConfigurationLinkSelected(selected, linkPhase, linkAddress, linkProtocol, switching),
    busy,
    calibration,
    draftValid,
)

/** Home-tab back is suppressed only while the X10 profile editor is on screen, not KeyLinker. */
fun hubProfilesTabBackToHomeEnabled(
    tab: Int,
    connectionSheet: Boolean,
    profileEditorOpen: Boolean,
    configurationEditor: Boolean,
): Boolean = tab != 0 && !connectionSheet && !(tab == 1 && profileEditorOpen && configurationEditor)

/** Dashboard shortcuts must not flag the X10 draft editor open when KeyLinker is shown instead. */
fun customizeOpensX10ProfileEditor(supportsKeyLinker: Boolean, supportsConfiguration: Boolean): Boolean =
  !supportsKeyLinker && supportsConfiguration
