// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.dev.edgard.easygamepad.data.ConfigurationLinkSituation
import br.dev.edgard.easygamepad.data.ControllerDevice
import br.dev.edgard.easygamepad.demo.DemoController

@Composable
internal fun SelectedControllerStatusCard(device: ControllerDevice, state: ControllerPickerUiState, onConnect: () -> Unit) {
    HubCard(Modifier.fillMaxWidth()) {
        Text(device.productName ?: tr("Generic controller", "Controle genérico"), style = MaterialTheme.typography.titleMedium)
        Text(device.displayName(), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (DemoController.isDevice(device)) {
            Description(tr("Simulated · no Bluetooth", "Simulado · sem Bluetooth"))
        }
        val gameplay = when {
            device.inputId == null -> tr("Gameplay: not linked", "Jogo: não vinculado")
            DemoController.isDevice(device) -> tr("Gameplay: simulated input active", "Jogo: entrada simulada ativa")
            else -> tr("Gameplay: linked to Android input #${device.inputId}", "Jogo: vinculado à entrada Android #${device.inputId}")
        }
        Description(gameplay)
        if (device.hasConfigurationLink) {
            val config = when (state.configurationSituation) {
                ConfigurationLinkSituation.READY -> tr("App configuration: connected", "Configuração do app: conectada")
                ConfigurationLinkSituation.CONNECTING -> tr("App configuration: connecting…", "Configuração do app: conectando…")
                ConfigurationLinkSituation.ERROR -> tr("App configuration: last attempt failed", "Configuração do app: última tentativa falhou")
                ConfigurationLinkSituation.SAVED_OFFLINE -> tr("App configuration: saved but offline", "Configuração do app: salva, mas offline")
                ConfigurationLinkSituation.NOT_APPLICABLE -> tr("App configuration: not available for this entry", "Configuração do app: indisponível para esta entrada")
            }
            Description(config)
            if (state.configurationSituation != ConfigurationLinkSituation.READY) {
                val label = if (DemoController.isDevice(device)) {
                    tr("Connect demo", "Conectar demo")
                } else {
                    tr("Connect or find device", "Conectar ou buscar dispositivo")
                }
                OutlinedButton(onClick = onConnect, modifier = Modifier.fillMaxWidth()) { Text(label) }
            }
        } else {
            Description(tr("This entry is for gameplay testing only. Add an EasySMX model to customize.", "Esta entrada serve só para teste de jogo. Adicione um modelo EasySMX para personalizar."))
        }
    }
}
