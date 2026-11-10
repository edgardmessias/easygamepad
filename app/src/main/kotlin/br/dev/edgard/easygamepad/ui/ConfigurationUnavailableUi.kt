// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.dev.edgard.easygamepad.HubViewModel
import br.dev.edgard.easygamepad.protocol.DeviceProfile

data class ConfigurationUnavailableUiState(
    val deviceName: String? = null,
    val gameplayLinked: Boolean = false,
    val hasBleAddress: Boolean = false,
)

data class ConfigurationUnavailableActions(
    val onOpenTestTab: () -> Unit,
    val onOpenDeviceSheet: () -> Unit,
    val onAddController: (DeviceProfile, Boolean) -> Unit,
) {
    companion object {
        val noop = ConfigurationUnavailableActions({}, {}, { _, _ -> })
    }
}

@Composable fun ConfigurationUnavailable(model: HubViewModel) {
    val device by model.selectedDevice.collectAsStateWithLifecycle()
    ConfigurationUnavailableContent(
        ConfigurationUnavailableUiState(device?.name, device?.inputId != null, device?.address != null),
        ConfigurationUnavailableActions(
            onOpenTestTab = { model.tab.value = 2 },
            onOpenDeviceSheet = { model.deviceSheet.value = true },
            onAddController = model::addController,
        ),
    )
}

@Composable fun ConfigurationUnavailableContent(state: ConfigurationUnavailableUiState, actions: ConfigurationUnavailableActions) {
    ScreenColumn {
        ScreenHeading(tr("Selected controller", "Controle selecionado"), state.deviceName ?: tr("Choose your controller", "Escolha seu controle"))
        HubCard(Modifier.fillMaxWidth()) {
            HubIcon(Glyph.PROFILES, Modifier.size(34.dp), MaterialTheme.colorScheme.secondary)
            Text(tr("Device-specific settings", "Ajustes por dispositivo"), style = MaterialTheme.typography.titleLarge)
            Description(tr("You can customize the EasySMX X10, X20 and X20 Pro in this app. With other controllers, you can check buttons, sticks and triggers in Test.",
                "Você pode personalizar o EasySMX X10, X20 e X20 Pro neste app. Com outros controles, confira botões, analógicos e gatilhos na aba Teste."))
            Button(onClick = actions.onOpenTestTab, enabled = state.gameplayLinked) { Text(tr("Test selected controller", "Testar controle selecionado")) }
            OutlinedButton(onClick = actions.onOpenDeviceSheet) { Text(tr("Choose controller", "Escolher controle")) }
            if (!state.hasBleAddress) DeviceProfile.ALL.forEach { profile ->
                TextButton(onClick = { actions.onAddController(profile, true) }) { Text(tr("Set up as ${profile.name}", "Configurar como ${profile.name}")) }
            }
        }
    }
}
