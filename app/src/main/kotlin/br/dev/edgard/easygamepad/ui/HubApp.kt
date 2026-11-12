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
import br.dev.edgard.easygamepad.data.ConsumerCopy
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
        val chooserLabel = tr("Choose controller: ", "Escolher controle: ") + (controller?.displayName() ?: tr("None selected", "Nenhum selecionado"))
        val chooserState = ConsumerCopy.chooserSummary(
            status.gameplayConnected,
            status.configurationConnected,
            LocalPortuguese.current,
        )
        LaunchedEffect(controller?.id, controller?.supportsConfiguration, keyLinkerDevice) {
            val krly = controller?.supportsConfiguration == true
            if (controller != null && ((!krly && !keyLinkerDevice && model.tab.value == 1) || (!krly && model.tab.value == 3))) model.tab.value = 2
        }
        LaunchedEffect(tab, controller?.supportsConfiguration) {
            if (tab != 1 || controller?.supportsConfiguration != true) model.cancelX10VibrationPreview()
        }
        val context = LocalContext.current
        val snackbar = remember { SnackbarHostState() }
        val latestPortuguese by rememberUpdatedState(LocalPortuguese.current)
        var permissionDenied by remember { mutableStateOf(false) }
        LaunchedEffect(model) {
            model.repository.notices.collect { notice ->
                val message = model.noticePresenter.present(notice, latestPortuguese) ?: return@collect
                snackbar.showSnackbar(
                    message = message.text,
                    duration = if (message.longLived) SnackbarDuration.Long else SnackbarDuration.Short,
                )
            }
        }
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
                RailDeviceButton(controller?.artwork, controller?.displayName()?.removePrefix("EasySMX ") ?: tr("Devices", "Controles"),
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
                            Text(controller?.displayName() ?: tr("Devices", "Controles"), modifier = Modifier.widthIn(max = if (largeText) 220.dp else 100.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
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
