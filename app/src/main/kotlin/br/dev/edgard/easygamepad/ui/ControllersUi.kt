// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

@Composable internal fun RenameControllerDialog(device: ControllerDevice, dismiss: () -> Unit, save: (String) -> Unit) {
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

/** A model tile with the product's own photograph, or the drawn generic gamepad. */
@Composable internal fun ModelChoice(profile: DeviceProfile, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ArtworkThumbnail(ControllerArtworkCatalog.forModel(profile.id), Modifier.fillMaxWidth().heightIn(max = 110.dp).aspectRatio(1.5f, matchHeightConstraintsFirst = true))
            Text(profile.name.removePrefix("EasySMX "), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
