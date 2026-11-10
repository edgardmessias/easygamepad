// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.core.net.toUri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.dev.edgard.easygamepad.BuildConfig
import br.dev.edgard.easygamepad.HubViewModel
import br.dev.edgard.easygamepad.protocol.GattProfile

@Composable fun SettingsScreen(model: HubViewModel) {
    val prefs by model.preferences.collectAsStateWithLifecycle()
    val device by model.selectedDevice.collectAsStateWithLifecycle()
    val gatt = device?.gatt ?: prefs.gatt
    var advanced by rememberSaveable { mutableStateOf(false) }
    var about by rememberSaveable { mutableStateOf(false) }
    val settingsScroll = rememberScrollState()
    var service by rememberSaveable(device?.id, gatt.service) { mutableStateOf(gatt.service) }
    var write by rememberSaveable(device?.id, gatt.write) { mutableStateOf(gatt.write) }
    var notify by rememberSaveable(device?.id, gatt.notify) { mutableStateOf(gatt.notify) }
    val context = LocalContext.current
    if (about) {
        BackHandler { about = false }
        AboutScreen(onBack = { about = false })
        return
    }
    ScreenColumn(scrollState = settingsScroll) {
        ScreenHeading(tr("Your app", "Seu aplicativo"), tr("Your preferences.", "Suas preferências."), tr("The same colors. Your preferred way to use them.", "Idioma, aparência e conexão do seu jeito."))
        ActionCard(Glyph.INFO, tr("About EasyGamepad", "Sobre o EasyGamepad"),
            tr("Version ", "Versão ") + BuildConfig.VERSION_NAME + tr(" · License, credits and links", " · Licença, créditos e links"),
            MaterialTheme.colorScheme.secondary) { about = true }
        HubCard(Modifier.fillMaxWidth()) {
            Text(tr("Appearance", "Aparência"), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("dark" to tr("Dark", "Escuro"), "light" to tr("Light", "Claro"), "system" to tr("System", "Sistema")).forEach { (key, label) ->
                    ThemePreview(label, key, prefs.theme == key, Modifier.weight(1f)) { model.theme(key) }
                }
            }
        }
        HubCard(Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                HubIcon(Glyph.LANGUAGE, color = MaterialTheme.colorScheme.primary)
                Text(tr("Language", "Idioma"), style = MaterialTheme.typography.titleLarge)
            }
            LanguageOption(tr("Automatic", "Automático"), tr("Use your phone’s language", "Usar o idioma do celular"), prefs.language == "auto") { model.language("auto") }
            LanguageOption("English", tr("Use English", "Usar inglês"), prefs.language == "en") { model.language("en") }
            LanguageOption("Português (Brasil)", tr("Use Brazilian Portuguese", "Usar português do Brasil"), prefs.language == "pt-BR") { model.language("pt-BR") }
        }
        HubCard(Modifier.fillMaxWidth()) {
            Text(tr("Connection & permissions", "Conexão e permissões"), style = MaterialTheme.typography.titleMedium)
            StatusPill(if (model.ble.hasPermissions()) tr("Bluetooth allowed", "Bluetooth permitido") else tr("Permission needed", "Permissão necessária"), model.ble.hasPermissions())
            Description(tr("The game connection and the app connection are separate. Allow Nearby devices to read battery and customize. Your X10 reconnects while the app is open. Android 8–11 also needs Location for scanning.", "A conexão de jogo e a conexão do app são separadas. Permita dispositivos próximos para consultar a bateria e personalizar. Seu X10 reconecta enquanto o app está aberto. No Android 8–11, a busca também precisa de localização."))
            OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) }) { Text(tr("Open app permissions", "Abrir permissões do app")) }
        }
        HubCard(Modifier.fillMaxWidth()) {
            Text(tr("Your data stays yours.", "Seus dados ficam com você."), style = MaterialTheme.typography.titleLarge)
            Description(tr("No account, ads, analytics or internet permission. Profiles are stored locally. Bluetooth logs stay in memory until you choose to share them. Export your profiles before uninstalling the app.", "Sem conta, anúncios, telemetria ou permissão de internet. Os perfis são armazenados localmente. Os registros Bluetooth ficam na memória até você decidir compartilhá-los. Exporte seus perfis antes de desinstalar o app."))
            if (prefs.storageIoError) {
                InfoBanner(tr("Local storage could not be read or written. Restart the app or free space on your phone.", "Não foi possível ler ou gravar os dados locais. Reinicie o app ou libere espaço no celular."), error = true)
            } else if (prefs.storageWarnings.isNotEmpty()) {
                val skipped = prefs.storageWarnings.sumOf { it.skippedEntries }
                val unreadable = prefs.storageWarnings.any { it.rootUnreadable }
                val detail = when {
                    unreadable && skipped > 0 -> tr("Some saved data could not be read ($skipped entries skipped). Export valid profiles before fixing storage.", "Parte dos dados salvos não pôde ser lida ($skipped entradas ignoradas). Exporte perfis válidos antes de corrigir o armazenamento.")
                    unreadable -> tr("A saved data block could not be read. Valid entries are still shown; broken bytes were not rewritten.", "Um bloco de dados salvos não pôde ser lido. As entradas válidas continuam visíveis; os bytes corrompidos não foram regravados.")
                    else -> tr("$skipped saved entries were skipped because they are invalid. The rest is intact.", "$skipped entradas salvas foram ignoradas por estarem inválidas. O restante está intacto.")
                }
                InfoBanner(detail, error = true)
            }
        }
        if (device?.supportsConfiguration == true) HubCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Advanced connection", "Conexão avançada"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) tr("Hide", "Ocultar") else tr("Show", "Mostrar")) }
            }
            if (advanced) {
                Description(tr("Change these values only if you have connection instructions for your controller. Changes apply the next time you connect.", "Altere estes valores apenas se tiver instruções de conexão específicas para seu controle. As alterações valem na próxima conexão."))
                OutlinedTextField(service, { service = it }, label = { Text(tr("Service UUID", "UUID do serviço")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(write, { write = it }, label = { Text(tr("Write UUID", "UUID de escrita")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(notify, { notify = it }, label = { Text(tr("Notify UUID", "UUID de notificação")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(onClick = { model.saveGatt(GattProfile(service.trim(), write.trim(), notify.trim())) }) { Text(tr("Save connection profile", "Salvar perfil de conexão")) }
                TextButton(onClick = { service = GattProfile().service; write = GattProfile().write; notify = GattProfile().notify }) { Text(tr("Use X10 defaults", "Usar padrões do X10")) }
            }
        }
    }
}
@Composable private fun LanguageOption(title: String, subtitle: String, selected: Boolean, click: () -> Unit) {
    Surface(onClick = click, color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected, onClick = null)
        }
    }
}

@Composable private fun ThemePreview(title: String, mode: String, selected: Boolean, modifier: Modifier, choose: () -> Unit) {
    val lines = if (LocalDensity.current.fontScale >= 1.3f) 2 else 1
    val edge = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val background = if (mode == "light") Color(0xFFF5F6F2) else Color(0xFF0D1017)
    val panel = if (mode == "light") Color(0xFFDEE4D8) else Color(0xFF272E3B)
    Surface(onClick = choose, selected = selected, modifier = modifier.semantics { role = Role.RadioButton },
        shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, edge)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.fillMaxWidth().height(60.dp).background(background, MaterialTheme.shapes.small).padding(7.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.fillMaxWidth(.55f).height(4.dp).background(Lime, MaterialTheme.shapes.small))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.weight(1f).height(25.dp).background(panel, MaterialTheme.shapes.small))
                    Box(Modifier.weight(1f).height(25.dp).background(if (mode == "system") Color(0xFFE1F3C4) else panel, MaterialTheme.shapes.small))
                }
            }
            Text(title, minLines = lines, maxLines = 2, style = MaterialTheme.typography.labelMedium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
}
