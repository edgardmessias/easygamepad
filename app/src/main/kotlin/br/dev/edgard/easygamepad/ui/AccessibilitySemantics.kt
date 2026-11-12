// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import br.dev.edgard.easygamepad.ble.LinkPhase
import br.dev.edgard.easygamepad.data.ConsumerCopy
import br.dev.edgard.easygamepad.data.ControllerDevice
import br.dev.edgard.easygamepad.data.ControllerStatus
import br.dev.edgard.easygamepad.protocol.CalibrationState

/** Screen-reader copy kept off composables so it can be unit-tested without a device. */
object AccessibilitySemantics {
    fun statusState(active: Boolean, portuguese: Boolean): String = when {
        active && portuguese -> "Ativo"
        active -> "Active"
        portuguese -> "Inativo"
        else -> "Inactive"
    }

    fun vibrationSliderLabel(label: String, value: Int, suffix: String, previewing: Boolean, portuguese: Boolean): String {
        val base = "$label, $value$suffix"
        if (!previewing) return base
        return base + if (portuguese) ", pré-visualização de vibração ativa" else ", vibration preview active"
    }

    fun profilesReadDisabledReason(state: ProfilesEditorUiState, portuguese: Boolean): String? = when {
        state.canReadFromDevice -> null
        state.calibration.stopRequired -> if (portuguese) "Finalize a calibração antes de ler." else "Finish calibration before reading."
        state.busy -> if (portuguese) "Aguarde a operação atual do controle." else "Wait for the current controller operation."
        state.linkPhase != LinkPhase.READY -> if (portuguese) "Conecte o app ao controle primeiro." else "Connect the app to the controller first."
        else -> if (portuguese) "Leitura indisponível." else "Reading unavailable."
    }

    fun profilesApplyDisabledReason(state: ProfilesEditorUiState, portuguese: Boolean): String? = when {
        state.canApplyToDevice -> null
        !state.draftValid -> if (portuguese) "Corrija o perfil antes de aplicar ao controle." else "Fix the profile before applying to the controller."
        state.calibration.stopRequired -> if (portuguese) "Finalize a calibração antes de aplicar." else "Finish calibration before applying."
        state.busy -> if (portuguese) "Aguarde a operação atual do controle." else "Wait for the current controller operation."
        state.linkPhase != LinkPhase.READY -> if (portuguese) "Conecte o app ao controle primeiro." else "Connect the app to the controller first."
        else -> if (portuguese) "Ação indisponível." else "Action unavailable."
    }

    fun profilesSaveDisabledReason(state: ProfilesEditorUiState, portuguese: Boolean): String? = when {
        state.canSaveProfile -> null
        else -> if (portuguese) "Corrija os valores inválidos do perfil." else "Fix invalid profile values."
    }

    fun metricTileSummary(label: String, value: String, detail: String): String = "$label, $value. $detail"

    /**
     * Matches [HubViewModel.refreshBattery]: KRLY/KeyLinker when the configuration link is ready,
     * otherwise Android input refresh only when gameplay is linked and a config link is not required first.
     */
    fun dashboardBatteryRefreshEnabled(device: ControllerDevice, status: ControllerStatus): Boolean = when {
        status.batteryPercent != null || status.batteryLevel != null -> true
        status.charging -> true
        status.configurationConnected -> true
        device.hasConfigurationLink && !status.configurationConnected -> false
        status.gameplayConnected -> true
        else -> false
    }

    fun dashboardConnectDisabledReason(switching: Boolean, connecting: Boolean, portuguese: Boolean): String? = when {
        connecting -> if (portuguese) "Conexão em andamento." else "Connection in progress."
        switching -> if (portuguese) "Aguarde a troca de controle terminar." else "Wait for the controller switch to finish."
        else -> null
    }

    fun controllerSwitcherLabel(name: String, selected: Boolean, portuguese: Boolean): String = when {
        selected && portuguese -> "$name, controle atual"
        selected -> "$name, current controller"
        portuguese -> "Alternar para $name"
        else -> "Switch to $name"
    }

    fun controllerViewLabel(rear: Boolean, portuguese: Boolean): String =
        if (rear) {
            if (portuguese) "Vista traseira do controle selecionada" else "Controller back view selected"
        } else {
            if (portuguese) "Vista frontal do controle selecionada" else "Controller front view selected"
        }

    fun dashboardBatteryValue(status: ControllerStatus): String = when {
        status.batteryPercent != null -> "${status.batteryPercent}%"
        status.batteryLevel != null -> "${status.batteryLevel}/4"
        else -> "—"
    }

    fun dashboardBatteryProgress(status: ControllerStatus): Float? = when {
        status.batteryPercent != null -> status.batteryPercent.coerceIn(0, 100) / 100f
        status.batteryLevel != null -> status.batteryLevel.coerceIn(0, 4) / 4f
        else -> null
    }

    fun dashboardBatteryDetail(
        device: ControllerDevice,
        status: ControllerStatus,
        refreshEnabled: Boolean,
        portuguese: Boolean,
    ): String = when {
        status.charging && (status.batteryPercent != null || status.batteryLevel != null) ->
            if (portuguese) "Carregando agora" else "Charging now"
        status.charging ->
            if (portuguese) "Carregando" else "Charging"
        status.batteryPercent != null || status.batteryLevel != null -> when {
            refreshEnabled -> if (portuguese) "Toque para atualizar" else "Tap to refresh"
            else -> if (portuguese) "Última leitura disponível" else "Last reading available"
        }
        status.configurationConnected ->
            if (portuguese) "Consultando carga…" else "Reading charge…"
        device.hasConfigurationLink ->
            if (portuguese) "Conecte ao app para ler" else "Connect to app to read"
        status.gameplayConnected ->
            if (portuguese) "Toque para atualizar pela entrada do jogo" else "Tap to refresh via game input"
        else ->
            if (portuguese) "Sem leitura disponível" else "No reading available"
    }

    fun dashboardGameLabel(portuguese: Boolean): String =
        if (portuguese) "Jogo" else "Game"

    fun dashboardGameValue(status: ControllerStatus, portuguese: Boolean): String = when {
        status.gameplayConnected -> if (portuguese) "Ativo" else "On"
        else -> if (portuguese) "Inativo" else "Off"
    }

    fun dashboardGameDetail(status: ControllerStatus, portuguese: Boolean): String = when {
        status.gameplayConnected ->
            if (portuguese) "Entrada detectada para teste" else "Input detected for testing"
        status.configurationConnected && !status.gameplayConnected ->
            if (portuguese) "Sem entrada de jogo ainda" else "No game input yet"
        else ->
            if (portuguese) "Ligue o controle no modo BT" else "Turn on controller in BT mode"
    }

    /** X10 configuration is the QMacro channel; X20 stays KeyLinker. Product names are not translated. */
    fun dashboardChannelLabel(device: ControllerDevice): String =
        if (device.supportsKeyLinker) "KeyLinker" else "QMacro"

    fun dashboardConnectCue(portuguese: Boolean): String =
        if (portuguese) "Conectar" else "Connect"

    fun dashboardAppValue(status: ControllerStatus, portuguese: Boolean): String = when {
        status.connecting -> if (portuguese) "Conectando…" else "Connecting…"
        status.configurationConnected -> if (portuguese) "Ativo" else "On"
        else -> if (portuguese) "Inativo" else "Off"
    }

    fun dashboardAppDetail(
        status: ControllerStatus,
        portuguese: Boolean,
        connectEnabled: Boolean,
        switching: Boolean = false,
    ): String = when {
        status.configurationConnected ->
            if (portuguese) "Bateria e ajustes disponíveis" else "Battery and settings available"
        status.connecting ->
            if (portuguese) "Conectando ao app…" else "Connecting to app…"
        !connectEnabled ->
            dashboardConnectDisabledReason(switching, status.connecting, portuguese)
                ?: if (portuguese) "Aguarde para conectar" else "Wait to connect"
        status.gameplayConnected ->
            if (portuguese) "Toque para conectar e ler bateria e perfis" else "Tap to connect and read battery and settings"
        else ->
            if (portuguese) "Toque para conectar ao app" else "Tap to connect to app"
    }

    fun dashboardProfilesValue(count: Int): String = count.toString()

    fun dashboardProfilesDetail(count: Int, portuguese: Boolean): String = when {
        count == 0 ->
            if (portuguese) "Toque para criar o primeiro" else "Tap to create your first"
        else -> ConsumerCopy.savedProfileQuota(count, portuguese = portuguese)
    }

    fun dashboardAppLinkEnabled(status: ControllerStatus): Boolean =
        !status.configurationConnected && !status.connecting
}
