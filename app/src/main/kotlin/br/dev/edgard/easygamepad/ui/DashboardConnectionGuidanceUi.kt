// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import br.dev.edgard.easygamepad.data.ControllerDevice
import br.dev.edgard.easygamepad.data.ControllerStatus

/** Where the home connect CTA lives so compact phones see it above the controller artwork. */
enum class DashboardConnectPlacement { HIDDEN, PRIMARY_COMPACT, SECONDARY }

fun dashboardConnectPlacement(twoPane: Boolean, showConnectCard: Boolean): DashboardConnectPlacement = when {
    !showConnectCard -> DashboardConnectPlacement.HIDDEN
    twoPane -> DashboardConnectPlacement.SECONDARY
    else -> DashboardConnectPlacement.PRIMARY_COMPACT
}

@Composable
fun DashboardConfigurationConnectSection(
    device: ControllerDevice,
    status: ControllerStatus,
    isDemo: Boolean,
    connecting: Boolean,
    connectEnabled: Boolean,
    connectDisabledReason: String?,
    compact: Boolean,
    onConnect: () -> Unit,
    onOpenConnectionSheet: () -> Unit,
) {
    val title = when {
        device.supportsKeyLinker -> tr("Battery & settings", "Bateria e ajustes")
        else -> tr("Battery & customization", "Bateria e personalização")
    }
    val description = when {
        device.supportsKeyLinker -> tr(
            "Connect to read battery, firmware and settings. Close the KeyLinker app on other phones first.",
            "Conecte para ler bateria, firmware e ajustes. Feche o app KeyLinker em outros celulares antes.",
        )
        status.gameplayConnected -> tr(
            "Your game is connected. Connect to QMacro for battery and settings. Disconnect it on other phones first.",
            "O jogo já está conectado. Conecte ao QMacro para consultar a carga e os ajustes. Desconecte de outros celulares antes.",
        )
        else -> tr("Turn on your controller in BT mode and keep it nearby.", "Ligue o controle no modo BT e mantenha por perto.")
    }
    val connectLabel = if (isDemo) tr("Connect demo", "Conectar demo") else tr("Connect to app", "Conectar ao app")
    val connectingLabel = tr("Connecting…", "Conectando…")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ScreenConnectCard(
            title = title,
            description = description,
            connecting = connecting,
            connectEnabled = connectEnabled,
            connectLabel = connectLabel,
            connectingLabel = connectingLabel,
            onConnect = onConnect,
            connectDisabledReason = connectDisabledReason,
        )
        if (compact && !isDemo) DashboardTwoConnectionsHelp(device)
        if (!isDemo) {
            TextButton(
                onClick = onOpenConnectionSheet,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(tr("Find configuration connection", "Buscar conexão do controle"))
            }
        }
    }
}

@Composable
private fun DashboardTwoConnectionsHelp(device: ControllerDevice) {
    if (!device.hasConfigurationLink) return
    var expanded by rememberSaveable(device.id) { mutableStateOf(false) }
    val title = tr("Why two connections?", "Por que duas conexões?")
    val expandedLabel = tr("Expanded", "Expandido")
    val collapsedLabel = tr("Collapsed", "Recolhido")
    Column(Modifier.fillMaxWidth()) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.heightIn(min = 48.dp).semantics {
                stateDescription = if (expanded) expandedLabel else collapsedLabel
                contentDescription = title
            },
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            HubIcon(Glyph.INFO, Modifier.size(17.dp), MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(title)
        }
        if (expanded) {
            val body = when {
                device.supportsKeyLinker -> tr(
                    "Games and the input tester use your controller's Bluetooth or USB connection to Android. Battery and settings use a separate KeyLinker connection. If it fails, close the KeyLinker app on other phones and try again.",
                    "Jogos e o teste usam a conexão Bluetooth ou USB do controle com o Android. Bateria e ajustes usam uma conexão KeyLinker separada. Se falhar, feche o app KeyLinker em outros celulares e tente de novo.",
                )
                else -> tr(
                    "Games and the input tester use your controller's Bluetooth or USB connection to Android. Battery and profiles use the separate QMacro configuration channel. Disconnect QMacro on other phones before connecting here.",
                    "Jogos e o teste usam a conexão Bluetooth ou USB do controle com o Android. Bateria e perfis usam o canal de configuração QMacro separado. Desconecte o QMacro em outros celulares antes de conectar aqui.",
                )
            }
            Description(body)
        }
    }
}
