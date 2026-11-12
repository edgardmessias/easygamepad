// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.ble.LinkPhase
import br.dev.edgard.easygamepad.ble.LinkState

/** Disconnect on background only when idle cleanup would leave calibration or macro upload stranded. */
fun needsConfigurationDisconnectOnBackground(
    link: LinkState,
    calibrationStopRequired: Boolean,
    macroUploadActive: Boolean,
): Boolean = link.phase == LinkPhase.READY && (calibrationStopRequired || macroUploadActive)

fun shouldRetryConfigurationConnect(
    manuallyDisconnected: Boolean,
    switching: Boolean,
    deviceSheetOpen: Boolean,
    connectionSheetOpen: Boolean,
    scanning: Boolean,
    linkPhase: LinkPhase,
    bleEnabled: Boolean,
    device: ControllerDevice?,
    nowMillis: Long,
    retryAtMillis: Long,
): Boolean = !manuallyDisconnected &&
    !switching &&
    !deviceSheetOpen &&
    !connectionSheetOpen &&
    !scanning &&
    linkPhase == LinkPhase.DISCONNECTED &&
    bleEnabled &&
    device?.hasConfigurationLink == true &&
    !device.address.isNullOrBlank() &&
    nowMillis >= retryAtMillis
