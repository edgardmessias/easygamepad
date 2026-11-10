// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import br.dev.edgard.easygamepad.ble.LinkPhase
import br.dev.edgard.easygamepad.protocol.CalibrationState

/** Immutable KRLY profile editor chrome derived from link and hardware, without ViewModel access. */
data class ProfilesEditorUiState(
    val linkPhase: LinkPhase = LinkPhase.DISCONNECTED,
    val busy: Boolean = false,
    val calibration: CalibrationState = CalibrationState(),
    val draftValid: Boolean = true,
) {
    val configurationReady: Boolean get() = linkPhase == LinkPhase.READY && !busy && !calibration.stopRequired
    val canSaveProfile: Boolean get() = draftValid
    val canReadFromDevice: Boolean get() = configurationReady
    val canApplyToDevice: Boolean get() = configurationReady && draftValid
    val canApplyOrReadFromDevice: Boolean get() = canApplyToDevice
}

fun profilesEditorUiState(
    linkPhase: LinkPhase,
    busy: Boolean,
    calibration: CalibrationState,
    draftValid: Boolean,
) = ProfilesEditorUiState(linkPhase, busy, calibration, draftValid)
