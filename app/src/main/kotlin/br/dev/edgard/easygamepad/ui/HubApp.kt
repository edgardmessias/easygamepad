// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import br.dev.edgard.easygamepad.HubViewModel
import br.dev.edgard.easygamepad.ble.*
import br.dev.edgard.easygamepad.data.HardwareState
import br.dev.edgard.easygamepad.data.controllerStatus
import br.dev.edgard.easygamepad.protocol.GattProfile
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HubApp(model: HubViewModel) {
    val prefs by model.preferences.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val dark = prefs.theme == "dark" || (prefs.theme == "system" && isSystemInDarkTheme())
    SideEffect {
        activity?.let { owner -> WindowCompat.getInsetsController(owner.window, owner.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        } }
    }
    HubTheme(prefs.theme, prefs.language) {
        val tab by model.tab.collectAsStateWithLifecycle()
        val link by model.link.collectAsStateWithLifecycle()
        val hardware by model.hardware.collectAsStateWithLifecycle()
        val sheet by model.connectionSheet.collectAsStateWithLifecycle()
        val picker by model.deviceSheet.collectAsStateWithLifecycle()
        val controller by model.selectedDevice.collectAsStateWithLifecycle()
        val keyLinker by model.keyLinker.collectAsStateWithLifecycle()
        val status = controllerStatus(controller, link, hardware, keyLinker)
        val keyLinkerDevice = controller?.supportsKeyLinker == true
        val largeText = LocalDensity.current.fontScale >= 1.3f
        val reducedMotion = rememberReducedMotion()
        val chooserLabel = tr("Choose controller: ", "Escolher controle: ") + (controller?.name ?: tr("None selected", "Nenhum selecionado"))
        val chooserState = (if (status.gameplayConnected) tr("Game connected", "Jogo conectado") else tr("Game offline", "Jogo desconectado")) + ", " +
            (if (status.configurationConnected) tr("App connected", "App conectado") else tr("App offline", "App desconectado"))
        LaunchedEffect(controller?.id, controller?.supportsConfiguration, keyLinkerDevice) {
            val krly = controller?.supportsConfiguration == true
            if (controller != null && ((!krly && !keyLinkerDevice && model.tab.value == 1) || (!krly && model.tab.value == 3))) model.tab.value = 2
        }
        val context = LocalContext.current
        val snackbar = remember { SnackbarHostState() }
        val latestPortuguese by rememberUpdatedState(LocalPortuguese.current)
        var permissionDenied by remember { mutableStateOf(false) }
        LaunchedEffect(model) { model.repository.notices.collect { snackbar.showSnackbar(if (latestPortuguese) it.portuguese else it.english) } }
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            permissionDenied = !grants.values.all { it }
            if (!permissionDenied) model.scan()
        }
        val bluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { if (model.ble.enabled()) model.scan() }
        val startScan: () -> Unit = {
            permissionDenied = false
            when {
                !model.ble.supported() -> model.repository.notice("Bluetooth is not available on this device", "Bluetooth indisponível neste dispositivo", true)
                !model.ble.hasPermissions() -> permissionLauncher.launch(model.ble.permissions())
                !model.ble.enabled() -> bluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                !model.ble.locationEnabled() -> context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                else -> model.scan()
            }
        }
        BackHandler(enabled = tab != 0 && !sheet) { model.tab.value = 0 }
        ProvideWindowLayout {
        val layout = LocalWindowLayout.current
        val tabs = buildList {
            add(Triple(0, Glyph.HUB, tr("Home", "Início")))
            if (controller?.supportsConfiguration == true) add(Triple(1, Glyph.PROFILES, tr("Profiles", "Perfis")))
            else if (keyLinkerDevice) add(Triple(1, Glyph.PROFILES, tr("Controller", "Controle")))
            add(Triple(2, Glyph.TEST, tr("Test", "Teste")))
            if (controller?.supportsConfiguration == true) add(Triple(3, Glyph.TOOLS, tr("Tools", "Ferramentas")))
            add(Triple(4, Glyph.SETTINGS, tr("Settings", "Ajustes")))
        }
        @Composable fun visibleTitle(index: Int, title: String) = when {
            largeText && index == 3 -> tr("Tools", "Ações")
            largeText && index == 4 -> "App"
            else -> title
        }
        Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Landscape phones and tablets: a side rail holds the brand, the tabs and the controller chooser,
        // so screens keep the full height and nothing is repeated in a top bar.
        if (layout.rail) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.background,
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start),
                // Landscape phones are too short for the logo as well as five destinations; tablets show it.
                header = if (layout.shortHeight) null else ({ BrandMark(Modifier.padding(top = 12.dp, bottom = 20.dp).size(36.dp)) })) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (layout.shortHeight) 0.dp else 6.dp)) {
                    tabs.forEach { (index, icon, title) ->
                        // Short windows show only the selected label so all five destinations fit without scrolling.
                        NavigationRailItem(selected = tab == index, onClick = { model.tab.value = index }, alwaysShowLabel = !layout.shortHeight,
                            modifier = Modifier.semantics { if (largeText || layout.shortHeight) contentDescription = title },
                            icon = { HubIcon(icon, color = if (tab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                            label = { Text(visibleTitle(index, title), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp) },
                            colors = NavigationRailItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer, selectedTextColor = MaterialTheme.colorScheme.primary))
                    }
                }
                RailDeviceButton(controller?.artwork, controller?.name?.removePrefix("EasySMX ") ?: tr("Devices", "Controles"),
                    status.connected, chooserLabel, chooserState, compact = layout.shortHeight) { model.deviceSheet.value = true }
            }
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
        }
        val horizontalSides = if (layout.rail) WindowInsetsSides.End else WindowInsetsSides.Horizontal
        Scaffold(modifier = Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + horizontalSides +
                (if (layout.rail) WindowInsetsSides.Top else WindowInsetsSides.Bottom)),
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                if (!layout.rail) Row(Modifier.fillMaxWidth().statusBarsPadding().windowInsetsPadding(WindowInsets.safeDrawing.only(horizontalSides))
                    .padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(Modifier.size(32.dp))
                    Spacer(Modifier.width(8.dp))
                    if (!largeText) {
                        Text("Easy", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = (-.7).sp)
                        Text("Gamepad", fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = (-.7).sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Surface(onClick = { model.deviceSheet.value = true }, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.heightIn(min = 48.dp).semantics(mergeDescendants = true) {
                            role = Role.Button; contentDescription = chooserLabel; stateDescription = chooserState
                        }) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Box(Modifier.size(6.dp).background(if (status.connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
                            Text(controller?.name ?: tr("Devices", "Controles"), modifier = Modifier.widthIn(max = if (largeText) 220.dp else 100.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                            HubIcon(Glyph.ARROW, Modifier.size(12.dp).rotate(90f), MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            bottomBar = {
                if (!layout.rail) NavigationBar(modifier = Modifier.heightIn(min = if (largeText) 104.dp else 80.dp), containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    tabs.forEach { (index, icon, title) ->
                        NavigationBarItem(selected = tab == index, onClick = { model.tab.value = index },
                            modifier = Modifier.semantics { if (largeText) contentDescription = title },
                            icon = { HubIcon(icon, color = if (tab == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                            label = { Text(visibleTitle(index, title), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer, selectedTextColor = MaterialTheme.colorScheme.primary))
                    }
                }
            }) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                AnimatedContent(tab, transitionSpec = {
                    if (reducedMotion) EnterTransition.None togetherWith ExitTransition.None
                    else (fadeIn(tween(Motion.MEDIUM)) + slideInVertically(tween(Motion.MEDIUM)) { it / 24 }) togetherWith fadeOut(tween(Motion.SHORT))
                }, label = "screen") { selected ->
                    when (selected) {
                        0 -> Dashboard(model, hardware, link)
                        1 -> if (controller?.supportsConfiguration == true) key(controller?.id) { ProfilesScreen(model) }
                            else if (keyLinkerDevice) key(controller?.id) { KeyLinkerScreen(model) } else ConfigurationUnavailable(model)
                        2 -> key(controller?.id) { TesterScreen(model) }
                        3 -> if (controller?.supportsConfiguration == true) key(controller?.id) { ToolsScreen(model) } else ConfigurationUnavailable(model)
                        else -> SettingsScreen(model)
                    }
                }
            }
        }
        }
        }
        if (picker) ModalBottomSheet(onDismissRequest = { model.deviceSheet.value = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.background) {
            ControllerPicker(model)
        }
        if (sheet) ModalBottomSheet(onDismissRequest = { model.connectionSheet.value = false; model.ble.stopScan() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.background) {
            ConnectionSheet(model, startScan, permissionDenied)
        }
    }
}

/** The controller chooser at the foot of the navigation rail: the selected pad's picture, its name and link state. */
@Composable private fun RailDeviceButton(artwork: br.dev.edgard.easygamepad.data.ControllerArtwork?, name: String, connected: Boolean,
                                         label: String, state: String, compact: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.padding(bottom = if (compact) 6.dp else 12.dp).width(72.dp).semantics(mergeDescendants = true) {
            role = Role.Button; contentDescription = label; stateDescription = state
        }) {
        Column(Modifier.padding(vertical = if (compact) 6.dp else 10.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box {
                ArtworkThumbnail(artwork, Modifier.width(if (compact) 44.dp else 52.dp).height(if (compact) 24.dp else 36.dp))
                Box(Modifier.align(Alignment.TopEnd).size(8.dp).background(
                    if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape))
            }
            if (!compact) Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable fun ScreenColumn(modifier: Modifier = Modifier, scrollState: ScrollState = rememberScrollState(), content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().then(modifier)) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().align(Alignment.TopCenter).verticalScroll(scrollState)
            .padding(horizontal = 18.dp).padding(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable fun ScreenHeading(eyebrow: String, title: String, description: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Eyebrow(eyebrow, color = MaterialTheme.colorScheme.primary)
        Text(title, style = screenTitleStyle())
        if (description != null) Description(description)
    }
}

@Composable fun ActionCard(icon: Glyph, title: String, body: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = color.copy(alpha = .12f)) { HubIcon(icon, Modifier.padding(13.dp).size(23.dp), color) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Description(body) }
            HubIcon(Glyph.ARROW, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable fun InfoBanner(text: String, error: Boolean = false) {
    Surface(shape = RoundedCornerShape(18.dp), color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HubIcon(Glyph.INFO, Modifier.size(19.dp), if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable fun timeLabel(time: Long): String {
    val locale = if (LocalPortuguese.current) Locale.forLanguageTag("pt-BR") else Locale.ENGLISH
    return DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(time))
}

@Composable private fun ConnectionSheet(model: HubViewModel, scan: () -> Unit, permissionDenied: Boolean) {
    val devices by model.ble.devices.collectAsStateWithLifecycle()
    val scanning by model.ble.scanning.collectAsStateWithLifecycle()
    val controller by model.selectedDevice.collectAsStateWithLifecycle()
    val link by model.link.collectAsStateWithLifecycle()
    val scanFailure by model.ble.scanFailure.collectAsStateWithLifecycle()
    var address by rememberSaveable { mutableStateOf("") }
    var manual by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val keyLinker = controller?.supportsKeyLinker == true
    val product = controller?.productName ?: "EasySMX X10"
    val short = product.removePrefix("EasySMX ")
    Column(Modifier.fillMaxWidth().heightIn(max = 650.dp).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(tr("Connect to the app", "Conectar ao aplicativo"), style = MaterialTheme.typography.headlineMedium)
        if (keyLinker) {
            Description(tr("Turn on your $short and choose its configuration device below. EasyGamepad asks it to identify itself only after you choose it, and reads its settings without changing them.",
                "Ligue seu $short e escolha abaixo o dispositivo de configuração. O EasyGamepad só pede a identificação depois da sua escolha e lê os ajustes sem alterá-los."))
            Description(tr("Close the KeyLinker app and disconnect it on other phones first. The game connection can stay active.",
                "Feche o app KeyLinker e desconecte de outros celulares antes. A conexão de jogo pode continuar ativa."))
        } else {
            Description(tr("Use BT mode and turn on your X10. Choose QMacro below for battery and customization. Xbox Wireless Controller is the separate game connection.", "Ligue seu X10 no modo BT. Escolha QMacro abaixo para bateria e personalização. Xbox Wireless Controller é a conexão separada para jogar."))
            Description(tr("If QMacro is connected to another phone, disconnect it there first. The game connection can stay active.", "Se o QMacro estiver conectado a outro celular, desconecte lá primeiro. A conexão de jogo pode continuar ativa."))
        }
        if (permissionDenied) InfoBanner(tr("Allow Nearby devices in the app permissions, then search again.", "Permita dispositivos próximos nas permissões do app e busque novamente."), true)
        if (scanFailure != null) InfoBanner(tr("The search could not finish. Wait a moment and try again.", "Não foi possível concluir a busca. Aguarde um momento e tente novamente."), true)
        if (link.error != null) InfoBanner(if (keyLinker) tr("The app could not connect. Check that the KeyLinker app is closed on other phones and select your controller again.",
            "O app não conseguiu conectar. Confira se o app KeyLinker está fechado em outros celulares e selecione seu controle novamente.")
            else tr("The app could not connect. Check that QMacro is disconnected on other phones and select your controller again.",
            "O app não conseguiu conectar. Confira se o QMacro está desconectado de outros celulares e selecione seu controle novamente."), true)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = scan, enabled = !scanning, modifier = Modifier.weight(1f)) {
                Text(if (scanning) tr("Searching…", "Buscando…") else tr("Find $short", "Buscar $short"))
            }
            OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) { Text(tr("Bluetooth", "Bluetooth")) }
        }
        AnimatedVisibility(scanning, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (controller?.address != null) {
            OutlinedButton(onClick = { model.connect(requireNotNull(controller?.address)) }, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Reconnect saved $short", "Reconectar $short salvo"))
            }
            Mono(controller?.address.orEmpty())
        }
        if (devices.isEmpty() && !scanning) Description(tr("Turn on your $short and tap Find $short. Keep it near your phone while searching.", "Ligue o $short e toque em Buscar $short. Mantenha o controle perto do celular durante a busca."))
        devices.forEach { device ->
            Surface(onClick = { model.connect(device.address) }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HubIcon(Glyph.BLUETOOTH, color = if (device.candidate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.weight(1f)) {
                        Text(device.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Mono(device.address)
                        Description(when {
                            device.rssi == null -> tr("Saved · waiting for a signal", "Salvo · aguardando sinal")
                            device.bonded -> tr("Paired · nearby", "Pareado · por perto")
                            else -> tr("Nearby Bluetooth device", "Dispositivo Bluetooth próximo")
                        })
                    }
                    device.rssi?.let { Mono("$it dBm") }
                }
            }
        }
        TextButton(onClick = { manual = !manual }) { Text(if (manual) tr("Hide manual connection", "Ocultar conexão manual") else tr("Connect manually", "Conectar manualmente")) }
        if (manual) {
        OutlinedTextField(address, { address = it.take(17) }, label = { Text(tr("BLE address (optional)", "Endereço BLE (opcional)")) }, placeholder = { Text("AA:BB:CC:DD:EE:FF") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = { model.connect(address.trim()) }, enabled = BluetoothAdapter.checkBluetoothAddress(address.uppercase())) { Text(tr("Connect by address", "Conectar por endereço")) }
        Description(tr("The configuration address may differ from the Xbox gameplay connection. Only a matching GATT service is accepted. Advanced UUIDs are in Settings.", "O endereço de configuração pode ser diferente da conexão Xbox usada nos jogos. Só um serviço GATT compatível é aceito. UUIDs avançados ficam em Ajustes."))
        }
    }
}
