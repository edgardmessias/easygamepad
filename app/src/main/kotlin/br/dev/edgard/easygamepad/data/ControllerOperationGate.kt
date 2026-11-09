// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** The UI and command entry points share the same rule for changing controller identity. */
class ControllerOperationGate(
    private val hardware: StateFlow<HardwareState>,
    private val keyLinker: StateFlow<KeyLinkerState>,
    private val switching: StateFlow<Boolean>,
) {
    val canSwitch = combine(hardware, keyLinker, switching, ::allowed).distinctUntilChanged()

    // Read the sources directly: a combined flow may not have delivered the latest busy state yet.
    fun canSwitchNow() = allowed(hardware.value, keyLinker.value, switching.value)

    private fun allowed(hardware: HardwareState, keyLinker: KeyLinkerState, switching: Boolean) =
        !hardware.busy && !hardware.calibration.stopRequired && !keyLinker.busy && !switching
}
