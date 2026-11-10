// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.dev.edgard.easygamepad.HubViewModel
import br.dev.edgard.easygamepad.ble.FoundDevice
import br.dev.edgard.easygamepad.data.BleScanSituation
import br.dev.edgard.easygamepad.data.ControllerConnectionContext
import br.dev.edgard.easygamepad.data.bleScanSituation

data class ConnectionSheetUiState(
    val productShort: String,
    val keyLinker: Boolean,
    val connection: ControllerConnectionContext,
    val savedAddress: String? = null,
    val devices: List<FoundDevice> = emptyList(),
    val linkError: String? = null,
)

data class ConnectionSheetActions(
    val onScan: () -> Unit,
    val onReconnectSaved: () -> Unit,
    val onConnect: (String) -> Unit,
    val onOpenAppSettings: () -> Unit,
)

@Composable
fun ConnectionSheet(model: HubViewModel, scan: () -> Unit, permissionDenied: Boolean) {
    val devices by model.ble.devices.collectAsStateWithLifecycle()
    val scanning by model.ble.scanning.collectAsStateWithLifecycle()
    val controller by model.selectedDevice.collectAsStateWithLifecycle()
    val link by model.link.collectAsStateWithLifecycle()
    val scanFailure by model.ble.scanFailure.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val keyLinker = controller?.supportsKeyLinker == true
    val product = controller?.productName ?: "EasySMX X10"
    val connection = ControllerConnectionContext(
        bluetoothSupported = model.ble.supported(),
        permissionsGranted = model.ble.hasPermissions(),
        bluetoothEnabled = model.ble.radioEnabled(),
        locationEnabled = model.ble.locationEnabled(),
        permissionDeniedThisSession = permissionDenied,
        scanning = scanning,
        scanFailed = scanFailure != null,
        linkPhase = link.phase,
        linkAddress = link.address,
        linkError = link.error,
    )
    ConnectionSheetContent(
        ConnectionSheetUiState(
            product.removePrefix("EasySMX "),
            keyLinker,
            connection,
            controller?.address,
            devices,
            link.error,
        ),
        ConnectionSheetActions(
            onScan = scan,
            onReconnectSaved = { model.connect(requireNotNull(controller?.address)) },
            onConnect = model::connect,
            onOpenAppSettings = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", context.packageName, null)
                })
            },
        ),
    )
}

@Composable
fun ConnectionSheetContent(state: ConnectionSheetUiState, actions: ConnectionSheetActions) {
    var address by rememberSaveable { mutableStateOf("") }
    var manual by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val short = state.productShort
    val situation = state.connection.bleScanSituation()
    Column(
        Modifier.fillMaxWidth().heightIn(max = 650.dp).verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp).padding(bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(tr("Connect to the app", "Conectar ao aplicativo"), style = MaterialTheme.typography.headlineMedium)
        ConnectionScanGuidance(state.keyLinker, short, situation)
        ScanSituationBanner(situation, actions.onOpenAppSettings)
        if (state.linkError != null) {
            InfoBanner(
                if (state.keyLinker) tr(
                    "The app could not connect. Close the KeyLinker app on other phones and try again.",
                    "O app não conseguiu conectar. Feche o KeyLinker em outros celulares e tente novamente.",
                ) else tr(
                    "The app could not connect. Disconnect QMacro on other phones and try again.",
                    "O app não conseguiu conectar. Desconecte o QMacro em outros celulares e tente novamente.",
                ),
                error = true,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val canScan = situation !in listOf(BleScanSituation.UNSUPPORTED, BleScanSituation.SCANNING)
            Button(
                onClick = actions.onScan,
                enabled = canScan && situation != BleScanSituation.SCANNING,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    when (situation) {
                        BleScanSituation.SCANNING -> tr("Searching…", "Buscando…")
                        BleScanSituation.PERMISSION_REQUIRED, BleScanSituation.PERMISSION_DENIED ->
                            tr("Allow and search", "Permitir e buscar")
                        BleScanSituation.BLUETOOTH_OFF -> tr("Turn on Bluetooth", "Ativar Bluetooth")
                        BleScanSituation.LOCATION_REQUIRED -> tr("Turn on Location", "Ativar localização")
                        else -> tr("Find $short", "Buscar $short")
                    },
                )
            }
            OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) {
                Text(tr("Bluetooth", "Bluetooth"))
            }
        }
        AnimatedVisibility(situation == BleScanSituation.SCANNING, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        state.savedAddress?.let { saved ->
            OutlinedButton(onClick = actions.onReconnectSaved, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Reconnect saved $short", "Reconectar $short salvo"))
            }
            Mono(saved)
        }
        if (state.devices.isEmpty() && situation == BleScanSituation.READY) {
            Description(
                tr(
                    "No nearby configuration devices yet. Turn on $short and tap Find $short while it stays near your phone.",
                    "Nenhum dispositivo de configuração por perto ainda. Ligue o $short e toque em Buscar $short com o controle perto do celular.",
                ),
            )
        }
        state.devices.forEach { device ->
            Surface(onClick = { actions.onConnect(device.address) }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HubIcon(Glyph.BLUETOOTH, color = if (device.candidate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f)) {
                        Text(device.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Mono(device.address)
                        Description(
                            when {
                                device.rssi == null -> tr("Saved · waiting for signal", "Salvo · aguardando sinal")
                                device.bonded -> tr("Paired · nearby", "Pareado · por perto")
                                else -> tr("Nearby Bluetooth device", "Dispositivo Bluetooth próximo")
                            },
                        )
                    }
                    device.rssi?.let { Mono("$it dBm") }
                }
            }
        }
        TextButton(onClick = { manual = !manual }) {
            Text(if (manual) tr("Hide manual connection", "Ocultar conexão manual") else tr("Connect manually", "Conectar manualmente"))
        }
        if (manual) {
            OutlinedTextField(
                address, { address = it.take(17) },
                label = { Text(tr("BLE address (optional)", "Endereço BLE (opcional)")) },
                placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { actions.onConnect(address.trim()) }, enabled = BluetoothAdapter.checkBluetoothAddress(address.uppercase())) {
                Text(tr("Connect by address", "Conectar por endereço"))
            }
            Description(
                tr(
                    "The configuration address may differ from the gameplay connection. Only a matching GATT service is accepted. Advanced UUIDs are in Settings.",
                    "O endereço de configuração pode ser diferente da conexão de jogo. Só um serviço GATT compatível é aceito. UUIDs avançados ficam em Ajustes.",
                ),
            )
        }
    }
}

@Composable
private fun ConnectionScanGuidance(keyLinker: Boolean, short: String, situation: BleScanSituation) {
    if (situation == BleScanSituation.UNSUPPORTED) {
        InfoBanner(tr("Bluetooth is not available on this device.", "Bluetooth não está disponível neste dispositivo."), error = true)
        return
    }
    if (keyLinker) {
        Description(
            tr(
                "Choose the $short configuration device below. The app identifies it only after your choice and reads settings without changing them.",
                "Escolha abaixo o dispositivo de configuração do $short. O app só identifica depois da sua escolha e lê os ajustes sem alterá-los.",
            ),
        )
        Description(
            tr("Close the KeyLinker app on other phones first. The game connection can stay active.",
                "Feche o KeyLinker em outros celulares antes. A conexão de jogo pode continuar ativa."),
        )
    } else {
        Description(
            tr("Use BT mode on your X10. Choose QMacro below for battery and customization. Xbox Wireless Controller is the separate game connection.",
                "Use o modo BT no X10. Escolha QMacro abaixo para bateria e personalização. Xbox Wireless Controller é a conexão de jogo separada."),
        )
        Description(
            tr("If QMacro is connected to another phone, disconnect it there first.",
                "Se o QMacro estiver conectado a outro celular, desconecte lá antes."),
        )
    }
}

@Composable
private fun ScanSituationBanner(situation: BleScanSituation, openAppSettings: () -> Unit) {
    when (situation) {
        BleScanSituation.PERMISSION_DENIED -> {
            InfoBanner(tr("Nearby devices permission denied. Allow it in app settings, then search again.", "Permissão de dispositivos próximos negada. Permita nas configurações do app e busque novamente."), error = true)
            OutlinedButton(onClick = openAppSettings, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Open app permissions", "Abrir permissões do app"))
            }
        }
        BleScanSituation.PERMISSION_REQUIRED -> InfoBanner(tr("Allow Nearby devices when prompted, then search again.", "Permita dispositivos próximos quando solicitado e busque novamente."), error = true)
        BleScanSituation.BLUETOOTH_OFF -> InfoBanner(tr("Turn on Bluetooth to search for your controller.", "Ative o Bluetooth para buscar seu controle."), error = true)
        BleScanSituation.LOCATION_REQUIRED -> InfoBanner(tr("Android needs Location on for Bluetooth scanning on this version.", "O Android precisa da localização ativada para buscar Bluetooth nesta versão."), error = true)
        BleScanSituation.SCAN_FAILED -> InfoBanner(tr("The search could not finish. Wait and try again.", "A busca não terminou. Aguarde e tente novamente."), error = true)
        else -> Unit
    }
}
