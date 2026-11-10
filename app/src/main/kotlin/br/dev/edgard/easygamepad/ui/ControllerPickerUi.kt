// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.dev.edgard.easygamepad.ble.LinkPhase
import br.dev.edgard.easygamepad.data.ControllerDevice
import br.dev.edgard.easygamepad.input.InputPad
import br.dev.edgard.easygamepad.protocol.DeviceProfile

data class ControllerPickerUiState(
    val devices: List<ControllerDevice> = emptyList(),
    val selected: ControllerDevice? = null,
    val linkPhase: LinkPhase = LinkPhase.DISCONNECTED,
    val linkAddress: String? = null,
    val canSwitch: Boolean = true,
    val gameplayPads: List<InputPad> = emptyList(),
) {
    fun configurationReadyFor(device: ControllerDevice) =
        linkPhase == LinkPhase.READY && linkAddress != null && linkAddress == device.address

    val switchBlocked: Boolean get() = !canSwitch
    val emptyDeviceList: Boolean get() = devices.isEmpty()
}

data class ControllerPickerActions(
    val onChooseDevice: (ControllerDevice) -> Unit,
    val onAddController: (DeviceProfile, Boolean) -> Unit,
    val onRenameDevice: (ControllerDevice, String) -> Unit,
    val onLinkGameplay: (Int?) -> Unit,
) {
    companion object {
        val noop = ControllerPickerActions({}, { _, _ -> }, { _, _ -> }, {})
    }
}

@Composable fun ControllerPicker(model: br.dev.edgard.easygamepad.HubViewModel) {
    val devices by model.devices.collectAsStateWithLifecycle()
    val selected by model.selectedDevice.collectAsStateWithLifecycle()
    val link by model.link.collectAsStateWithLifecycle()
    val canSwitch by model.canSwitchController.collectAsStateWithLifecycle()
    val input by model.input.collectAsStateWithLifecycle()
    ControllerPickerContent(
        ControllerPickerUiState(devices, selected, link.phase, link.address, canSwitch, input.pads),
        ControllerPickerActions(
            onChooseDevice = model::chooseDevice,
            onAddController = model::addController,
            onRenameDevice = model::renameDevice,
            onLinkGameplay = model::linkGameplay,
        ),
    )
}

@Composable fun ControllerPickerContent(state: ControllerPickerUiState, actions: ControllerPickerActions) {
    var renaming by remember { mutableStateOf<ControllerDevice?>(null) }
    val selected = state.selected
    Column(Modifier.fillMaxWidth().heightIn(max = 680.dp).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(tr("Your controllers", "Seus controles"), style = MaterialTheme.typography.headlineMedium)
        Description(tr("Choose the device for testing and settings. Your other controllers stay available to Android games.", "Escolha o dispositivo para testes e ajustes. Seus outros controles continuam disponíveis para os jogos do Android."))
        if (state.switchBlocked) InfoBanner(tr("Finish the current operation before switching controllers.", "Finalize a operação atual antes de trocar de controle."))
        state.devices.forEach { device ->
            ControllerChoiceCard(device, selected?.id == device.id, state.configurationReadyFor(device), state.canSwitch) {
                actions.onChooseDevice(device)
            }
            TextButton(onClick = { renaming = device }, enabled = state.canSwitch) {
                HubIcon(Glyph.EDIT, Modifier.size(16.dp), MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(tr("Rename", "Renomear") + " · " + device.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (state.emptyDeviceList) Description(tr("Pair a gamepad in Android settings or add an EasySMX configuration connection.", "Pareie um controle nos ajustes do Android ou adicione a conexão de configuração de um EasySMX."))
        Text(tr("Add an EasySMX controller", "Adicionar controle EasySMX"), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DeviceProfile.ALL.forEach { profile ->
                ModelChoice(profile, Modifier.weight(1f), state.canSwitch) { actions.onAddController(profile, false) }
            }
        }
        Description(tr("X10, X20 and X20 Pro settings can be changed. X20 and X20 Pro changes are read back to confirm them.",
            "Os ajustes do X10, X20 e X20 Pro podem ser alterados. No X20 e X20 Pro, cada alteração é relida para confirmar."))
        if (selected?.hasConfigurationLink == true && selected.address != null) {
            HorizontalDivider()
            Text(tr("Which controller do you want to test?", "Qual controle você quer testar?"), style = MaterialTheme.typography.titleMedium)
            val product = selected.productName ?: "EasySMX X10"
            Description(tr("Your $product may appear under another name in Android, such as Xbox Wireless Controller. If you have more than one, choose the one that matches this controller.",
                "Seu $product pode aparecer com outro nome no Android, como Xbox Wireless Controller. Se houver mais de um, escolha o que corresponde a este controle."))
            state.gameplayPads.forEach { pad ->
                val linkedName = state.devices.firstOrNull { it.inputId == pad.id }?.name ?: pad.name
                Surface(onClick = { actions.onLinkGameplay(pad.id) }, enabled = state.canSwitch, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(linkedName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Mono("#${pad.id} · VID %04X · PID %04X".format(pad.vendor, pad.product))
                        }
                        RadioButton(selected.inputId == pad.id, onClick = null)
                    }
                }
            }
            if (selected.inputDescriptor != null) TextButton(onClick = { actions.onLinkGameplay(null) }, enabled = state.canSwitch) {
                Text(tr("Remove test connection", "Desvincular controle do teste"))
            }
        }
        if (selected != null && selected.address == null && selected.inputId != null) {
            DeviceProfile.ALL.forEach { profile ->
                TextButton(onClick = { actions.onAddController(profile, true) }, enabled = state.canSwitch) {
                    Text(tr("This controller is an ${profile.name}", "Este controle é um ${profile.name}"))
                }
            }
        }
    }
    renaming?.let { device -> RenameControllerDialog(device, { renaming = null }) { name ->
        actions.onRenameDevice(device, name); renaming = null
    } }
}
