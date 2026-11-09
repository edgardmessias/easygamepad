// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.dev.edgard.easygamepad.HubViewModel
import br.dev.edgard.easygamepad.ble.LinkPhase
import br.dev.edgard.easygamepad.data.ControllerDevice
import br.dev.edgard.easygamepad.data.ControllerArtworkCatalog
import br.dev.edgard.easygamepad.data.controllerName
import br.dev.edgard.easygamepad.protocol.DeviceProfile

@Composable fun ControllerChoiceCard(device: ControllerDevice, selected: Boolean, ready: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(20.dp), color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val art = device.artwork
            if (art != null) ArtworkThumbnail(art, Modifier.width(64.dp).height(44.dp))
            else HubIcon(Glyph.TEST, Modifier.size(28.dp), if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(device.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text((device.productName?.takeIf { it != device.name }?.let { "$it · " } ?: "") + device.shortId,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(when {
                    ready && device.inputId != null -> tr("Ready to play and customize", "Pronto para jogar e personalizar")
                    ready -> tr("Connected for customization", "Conectado para personalizar")
                    device.inputId != null -> tr("Gameplay connected", "Conectado para jogar")
                    else -> tr("Saved controller", "Controle salvo")
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) HubIcon(Glyph.CHECK, Modifier.size(19.dp), MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable fun ControllerPicker(model: HubViewModel) {
    val devices by model.devices.collectAsStateWithLifecycle()
    val selected by model.selectedDevice.collectAsStateWithLifecycle()
    val link by model.link.collectAsStateWithLifecycle()
    val hardware by model.hardware.collectAsStateWithLifecycle()
    val switching by model.switching.collectAsStateWithLifecycle()
    val input by model.input.collectAsStateWithLifecycle()
    val canSwitch = !hardware.busy && !hardware.calibration.stopRequired && !switching
    var renaming by remember { mutableStateOf<ControllerDevice?>(null) }
    Column(Modifier.fillMaxWidth().heightIn(max = 680.dp).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(tr("Your controllers", "Seus controles"), style = MaterialTheme.typography.headlineMedium)
        Description(tr("Choose the device for testing and settings. Your other controllers stay available to Android games.", "Escolha o dispositivo para testes e ajustes. Seus outros controles continuam disponíveis para os jogos do Android."))
        if (!canSwitch) InfoBanner(tr("Finish the current operation before switching controllers.", "Finalize a operação atual antes de trocar de controle."))
        devices.forEach { device ->
            ControllerChoiceCard(device, selected?.id == device.id, link.phase == LinkPhase.READY && link.address == device.address, canSwitch) { model.chooseDevice(device) }
            TextButton(onClick = { renaming = device }) {
                HubIcon(Glyph.EDIT, Modifier.size(16.dp), MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(tr("Rename", "Renomear") + " · " + device.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (devices.isEmpty()) Description(tr("Pair a gamepad in Android settings or add an EasySMX configuration connection.", "Pareie um controle nos ajustes do Android ou adicione a conexão de configuração de um EasySMX."))
        Text(tr("Add an EasySMX controller", "Adicionar controle EasySMX"), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DeviceProfile.ALL.forEach { profile ->
                ModelChoice(profile, Modifier.weight(1f), canSwitch) { model.addController(profile) }
            }
        }
        Description(tr("X10, X20 and X20 Pro settings can be changed. X20 and X20 Pro changes are read back to confirm them.",
            "Os ajustes do X10, X20 e X20 Pro podem ser alterados. No X20 e X20 Pro, cada alteração é relida para confirmar."))
        if (selected?.hasConfigurationLink == true && selected?.address != null) {
            HorizontalDivider()
            Text(tr("Which controller do you want to test?", "Qual controle você quer testar?"), style = MaterialTheme.typography.titleMedium)
            val product = selected?.productName ?: "EasySMX X10"
            Description(tr("Your $product may appear under another name in Android, such as Xbox Wireless Controller. If you have more than one, choose the one that matches this controller.",
                "Seu $product pode aparecer com outro nome no Android, como Xbox Wireless Controller. Se houver mais de um, escolha o que corresponde a este controle."))
            input.pads.forEach { pad ->
                Surface(onClick = { model.linkGameplay(pad.id) }, enabled = canSwitch, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(devices.firstOrNull { it.inputId == pad.id }?.name ?: pad.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Mono("#${pad.id} · VID %04X · PID %04X".format(pad.vendor, pad.product))
                        }
                        RadioButton(selected?.inputId == pad.id, onClick = null)
                    }
                }
            }
            if (selected?.inputDescriptor != null) TextButton(onClick = { model.linkGameplay(null) }, enabled = canSwitch) { Text(tr("Remove test connection", "Desvincular controle do teste")) }
        }
        if (selected != null && selected?.address == null && selected?.inputId != null) {
            DeviceProfile.ALL.forEach { profile ->
                TextButton(onClick = { model.addController(profile, fromSelectedInput = true) }, enabled = canSwitch) {
                    Text(tr("This controller is an ${profile.name}", "Este controle é um ${profile.name}"))
                }
            }
        }
    }
    renaming?.let { device -> RenameControllerDialog(device, { renaming = null }) { name ->
        model.renameDevice(device, name); renaming = null
    } }
}

@Composable private fun RenameControllerDialog(device: ControllerDevice, dismiss: () -> Unit, save: (String) -> Unit) {
    var name by rememberSaveable(device.id) { mutableStateOf(device.name) }
    val valid = runCatching { controllerName(name) }.isSuccess
    AlertDialog(onDismissRequest = dismiss,
        title = { Text(tr("Rename controller", "Renomear controle")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Description(tr("Give it a name you recognize, like Living room or Player 2. This name appears only in EasyGamepad.",
                "Escolha um nome fácil de reconhecer, como Sala ou Jogador 2. Esse nome aparece apenas no EasyGamepad."))
            OutlinedTextField(name, { name = it.take(40) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(tr("Controller name", "Nome do controle")) },
                supportingText = { Text("${name.length}/40") }, isError = !valid)
        } },
        confirmButton = { TextButton(onClick = { save(name.trim()) }, enabled = valid) { Text(tr("Save", "Salvar")) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(tr("Cancel", "Cancelar")) } })
}

@Composable fun ConfigurationUnavailable(model: HubViewModel) {
    val device by model.selectedDevice.collectAsStateWithLifecycle()
    ScreenColumn {
        ScreenHeading(tr("Selected controller", "Controle selecionado"), device?.name ?: tr("Choose your controller", "Escolha seu controle"))
        HubCard(Modifier.fillMaxWidth()) {
            HubIcon(Glyph.PROFILES, Modifier.size(34.dp), MaterialTheme.colorScheme.secondary)
            Text(tr("Device-specific settings", "Ajustes por dispositivo"), style = MaterialTheme.typography.titleLarge)
            Description(tr("You can customize the EasySMX X10, X20 and X20 Pro in this app. With other controllers, you can check buttons, sticks and triggers in Test.",
                "Você pode personalizar o EasySMX X10, X20 e X20 Pro neste app. Com outros controles, confira botões, analógicos e gatilhos na aba Teste."))
            Button(onClick = { model.tab.value = 2 }, enabled = device?.inputId != null) { Text(tr("Test selected controller", "Testar controle selecionado")) }
            OutlinedButton(onClick = { model.deviceSheet.value = true }) { Text(tr("Choose controller", "Escolher controle")) }
            if (device != null && device?.address == null) DeviceProfile.ALL.forEach { profile ->
                TextButton(onClick = { model.addController(profile, true) }) { Text(tr("Set up as ${profile.name}", "Configurar como ${profile.name}")) }
            }
        }
    }
}

/** A model tile with the product's own photograph, or the drawn generic gamepad. */
@Composable private fun ModelChoice(profile: DeviceProfile, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ArtworkThumbnail(ControllerArtworkCatalog.forModel(profile.id), Modifier.fillMaxWidth().heightIn(max = 110.dp).aspectRatio(1.5f, matchHeightConstraintsFirst = true))
            Text(profile.name.removePrefix("EasySMX "), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
