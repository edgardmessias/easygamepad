// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.ble.LinkPhase

enum class BleScanSituation {
    UNSUPPORTED,
    PERMISSION_REQUIRED,
    PERMISSION_DENIED,
    BLUETOOTH_OFF,
    LOCATION_REQUIRED,
    SCANNING,
    SCAN_FAILED,
    READY,
}

enum class ConfigurationLinkSituation {
    NOT_APPLICABLE,
    READY,
    CONNECTING,
    SAVED_OFFLINE,
    ERROR,
}

enum class SavedDeviceLinkLabel {
    SAVED_ONLY,
    GAMEPLAY_LINKED,
    APP_CONNECTING,
    APP_READY,
    APP_OFFLINE,
}

data class ControllerConnectionContext(
    val bluetoothSupported: Boolean = true,
    val permissionsGranted: Boolean = true,
    val bluetoothEnabled: Boolean = true,
    val locationEnabled: Boolean = true,
    val permissionDeniedThisSession: Boolean = false,
    val scanning: Boolean = false,
    val scanFailed: Boolean = false,
    val linkPhase: LinkPhase = LinkPhase.DISCONNECTED,
    val linkAddress: String? = null,
    val linkError: String? = null,
)

fun ControllerConnectionContext.bleScanSituation(): BleScanSituation = when {
    !bluetoothSupported -> BleScanSituation.UNSUPPORTED
    permissionDeniedThisSession -> BleScanSituation.PERMISSION_DENIED
    !permissionsGranted -> BleScanSituation.PERMISSION_REQUIRED
    !bluetoothEnabled -> BleScanSituation.BLUETOOTH_OFF
    !locationEnabled -> BleScanSituation.LOCATION_REQUIRED
    scanning -> BleScanSituation.SCANNING
    scanFailed -> BleScanSituation.SCAN_FAILED
    else -> BleScanSituation.READY
}

fun configurationLinkSituation(
    device: ControllerDevice?,
    context: ControllerConnectionContext,
): ConfigurationLinkSituation {
    val address = device?.address ?: return ConfigurationLinkSituation.NOT_APPLICABLE
    if (!device.hasConfigurationLink) return ConfigurationLinkSituation.NOT_APPLICABLE
    val selectedLink = context.linkAddress != null && context.linkAddress == address
    return when {
        context.linkError != null && selectedLink -> ConfigurationLinkSituation.ERROR
        selectedLink && context.linkPhase == LinkPhase.READY -> ConfigurationLinkSituation.READY
        selectedLink && context.linkPhase != LinkPhase.DISCONNECTED -> ConfigurationLinkSituation.CONNECTING
        else -> ConfigurationLinkSituation.SAVED_OFFLINE
    }
}

fun savedDeviceLinkLabel(device: ControllerDevice, selected: Boolean, context: ControllerConnectionContext): SavedDeviceLinkLabel {
    val configReady = selected && context.linkPhase == LinkPhase.READY &&
        device.address != null && device.address == context.linkAddress
    val configConnecting = selected && device.address != null && device.address == context.linkAddress &&
        context.linkPhase !in listOf(LinkPhase.READY, LinkPhase.DISCONNECTED)
    return when {
        configReady -> SavedDeviceLinkLabel.APP_READY
        configConnecting -> SavedDeviceLinkLabel.APP_CONNECTING
        !selected -> SavedDeviceLinkLabel.SAVED_ONLY
        device.inputId != null && device.address == null -> SavedDeviceLinkLabel.GAMEPLAY_LINKED
        device.address != null -> SavedDeviceLinkLabel.APP_OFFLINE
        device.inputId != null -> SavedDeviceLinkLabel.GAMEPLAY_LINKED
        else -> SavedDeviceLinkLabel.SAVED_ONLY
    }
}

/** Android may omit battery telemetry; 0 can still be a real reading when reported. */
fun gameplayBatteryHint(percent: Int?, charging: Boolean): GameplayBatteryHint = when (percent) {
    null -> GameplayBatteryHint.MISSING
    else -> GameplayBatteryHint.REPORTED(percent, charging)
}

sealed interface GameplayBatteryHint {
    data object MISSING : GameplayBatteryHint
    data class REPORTED(val percent: Int, val charging: Boolean) : GameplayBatteryHint
}

fun ambiguousInputDescriptor(descriptor: String, padsSharingDescriptor: Int): Boolean =
    descriptor.isBlank() || padsSharingDescriptor > 1
