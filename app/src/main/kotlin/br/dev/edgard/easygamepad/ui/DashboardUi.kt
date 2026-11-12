// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.dev.edgard.easygamepad.HubViewModel
import br.dev.edgard.easygamepad.ble.LinkState
import br.dev.edgard.easygamepad.data.*
import br.dev.edgard.easygamepad.demo.DemoController
import br.dev.edgard.easygamepad.input.InputState

data class DashboardUiState(
    val selected: ControllerDevice? = null,
    val devices: List<ControllerDevice> = emptyList(),
    val status: ControllerStatus = ControllerStatus(false, false, false, null, false),
    val profileCount: Int = 0,
    val firmwareLabel: String? = null,
    val firmwareIdentified: Boolean = false,
    val canSwitch: Boolean = true,
    val switching: Boolean = false,
    val gameplayInput: InputState? = null,
) {
    val emptyHome: Boolean get() = selected == null
    val switcherDevices: List<ControllerDevice>
        get() = devices.filter { it.id != DemoController.ID }.ifEmpty { devices }
    val showDeviceSwitcher: Boolean get() = switcherDevices.size > 1
    val showConnectCard: Boolean get() = selected?.hasConfigurationLink == true && !status.configurationConnected
    val connectEnabled: Boolean get() = !status.connecting && !switching
}

data class DashboardActions(
    val onOpenDeviceSheet: () -> Unit,
    val onOpenConnectionSheet: () -> Unit,
    val onConnectSelected: () -> Unit,
    val onDisconnect: () -> Unit,
    val onRefreshBattery: () -> Unit,
    val onChooseDevice: (ControllerDevice) -> Unit,
    val onOpenTest: () -> Unit,
    val onCustomize: (Int) -> Unit,
) {
    companion object {
        val noop = DashboardActions({}, {}, {}, {}, {}, {}, {}, {})
    }
}

@Composable fun Dashboard(model: HubViewModel, hardware: HardwareState, link: LinkState) {
    val selected by model.selectedDevice.collectAsStateWithLifecycle()
    val devices by model.devices.collectAsStateWithLifecycle()
    val prefs by model.preferences.collectAsStateWithLifecycle()
    val input by model.input.collectAsStateWithLifecycle()
    val switching by model.switching.collectAsStateWithLifecycle()
    val canSwitch by model.canSwitchController.collectAsStateWithLifecycle()
    val keyLinker by model.keyLinker.collectAsStateWithLifecycle()
    val status = controllerStatus(selected, link, hardware, keyLinker)
    val state = DashboardUiState(
        selected = selected,
        devices = devices,
        status = status,
        profileCount = prefs.profiles.size,
        firmwareLabel = keyLinker.identification?.identity?.firmware,
        firmwareIdentified = keyLinker.assessment?.known?.product != null,
        canSwitch = canSwitch,
        switching = switching,
        gameplayInput = input.takeIf { status.gameplayConnected },
    )
    DashboardContent(
        state,
        DashboardActions(
            onOpenDeviceSheet = { model.deviceSheet.value = true },
            onOpenConnectionSheet = { model.connectionSheet.value = true },
            onConnectSelected = model::connectSelected,
            onDisconnect = model::disconnect,
            onRefreshBattery = model::refreshBattery,
            onChooseDevice = model::chooseDevice,
            onOpenTest = { model.tab.value = 2 },
            onCustomize = model::customize,
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun DashboardContent(state: DashboardUiState, actions: DashboardActions) {
    val device = state.selected
    val status = state.status
    val portuguese = LocalPortuguese.current
    var rear by rememberSaveable(device?.id) { mutableStateOf(false) }
    val layout = LocalWindowLayout.current
    val connectDisabledReason = AccessibilitySemantics.dashboardConnectDisabledReason(state.switching, status.connecting, portuguese)
    val isDemo = device?.let(DemoController::isDevice) == true
    if (device == null) {
        AdaptivePanes(secondary = {
            ActionCard(
                Glyph.HUB,
                tr("Add a controller", "Adicionar um controle"),
                tr(
                    "Connect a controller by Bluetooth or USB to test it. Add an EasySMX X10, X20 or X20 Pro to customize it.",
                    "Conecte um controle por Bluetooth ou USB para testar. Adicione um EasySMX X10, X20 ou X20 Pro para personalizar.",
                ),
                MaterialTheme.colorScheme.primary,
                actions.onOpenDeviceSheet,
            )
        }) {
            ScreenHeading(
                tr("Welcome to EasyGamepad", "Bem-vindo ao EasyGamepad"),
                tr("Ready for your next game.", "Pronto para a próxima partida."),
                tr("Pick a saved controller or add one to get started.", "Escolha um controle salvo ou adicione um para começar."),
            )
            ControllerStage(Modifier.fillMaxWidth().height(layout.stageHeight(200.dp)))
        }
        return
    }
    AdaptivePanes(secondary = {
        FlowRow(
            maxItemsInEachRow = if (LocalDensity.current.fontScale >= 1.5f) 1 else 2,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (device.supportsKeyLinker) {
                MetricTile(
                    tr("Firmware", "Firmware"),
                    state.firmwareLabel ?: "—",
                    if (state.firmwareIdentified) tr("Identified", "Identificado") else tr("Read on connect", "Lido ao conectar"),
                    Glyph.INFO,
                    Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.secondary,
                    onClick = { actions.onCustomize(0) },
                )
            }
        }
        AnimatedVisibility(
            state.showConnectCard,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ScreenConnectCard(
                    title = if (device.supportsKeyLinker) tr("Battery & settings", "Bateria e ajustes")
                    else tr("Battery & customization", "Bateria e personalização"),
                    description = when {
                        device.supportsKeyLinker -> tr(
                            "Connect to read battery, firmware and settings. Close the KeyLinker app on other phones first.",
                            "Conecte para ler bateria, firmware e ajustes. Feche o app KeyLinker em outros celulares antes.",
                        )
                        status.gameplayConnected -> tr(
                            "Your game is connected. Connect to QMacro for battery and settings. Disconnect it on other phones first.",
                            "O jogo já está conectado. Conecte ao QMacro para consultar a carga e os ajustes. Desconecte de outros celulares antes.",
                        )
                        else -> tr("Turn on your controller in BT mode and keep it nearby.", "Ligue o controle no modo BT e mantenha por perto.")
                    },
                    connecting = status.connecting,
                    connectEnabled = state.connectEnabled,
                    connectLabel = if (isDemo) tr("Connect demo", "Conectar demo") else tr("Connect to app", "Conectar ao app"),
                    connectingLabel = tr("Connecting…", "Conectando…"),
                    onConnect = actions.onConnectSelected,
                    connectDisabledReason = connectDisabledReason,
                )
                if (!isDemo) {
                    TextButton(
                        onClick = actions.onOpenConnectionSheet,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text(tr("Find configuration connection", "Buscar conexão do controle"))
                    }
                }
            }
        }
        SectionTitle(tr("Make it yours", "Do seu jeito"))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FeatureTile(
                tr("Test", "Testar"),
                tr("See every button and movement", "Confira cada botão e movimento"),
                Glyph.TEST,
                MaterialTheme.colorScheme.secondary,
                Modifier.weight(1f),
                actions.onOpenTest,
            )
            if (device.supportsConfiguration) {
                FeatureTile(
                    tr("Buttons", "Botões"),
                    tr("Choose what each button does", "Escolha a função de cada botão"),
                    Glyph.EDIT,
                    MaterialTheme.colorScheme.primary,
                    Modifier.weight(1f),
                ) { actions.onCustomize(0) }
            } else if (device.supportsKeyLinker) {
                FeatureTile(
                    tr("Buttons", "Botões"),
                    tr("Choose what each button does", "Escolha a função de cada botão"),
                    Glyph.EDIT,
                    MaterialTheme.colorScheme.primary,
                    Modifier.weight(1f),
                ) { actions.onCustomize(3) }
            }
        }
        if (device.supportsKeyLinker) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FeatureTile(
                    tr("Sticks", "Analógicos"),
                    tr("Tune dead zones and response curves", "Ajuste zonas mortas e curvas de resposta"),
                    Glyph.TOOLS,
                    MaterialTheme.colorScheme.tertiary,
                    Modifier.weight(1f),
                ) { actions.onCustomize(0) }
                FeatureTile(
                    tr("Macros", "Macros"),
                    tr("Set what each rear button plays", "Defina o que cada botão traseiro executa"),
                    Glyph.PROFILES,
                    MaterialTheme.colorScheme.secondary,
                    Modifier.weight(1f),
                ) { actions.onCustomize(4) }
            }
        }
        if (device.supportsConfiguration) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FeatureTile(
                    tr("Sticks", "Analógicos"),
                    tr("Tune sensitivity and movement", "Ajuste a resposta dos movimentos"),
                    Glyph.TOOLS,
                    MaterialTheme.colorScheme.tertiary,
                    Modifier.weight(1f),
                ) { actions.onCustomize(1) }
                FeatureTile(
                    tr("Vibration", "Vibração"),
                    tr("Find your ideal intensity", "Encontre a intensidade ideal"),
                    Glyph.SETTINGS,
                    MaterialTheme.colorScheme.secondary,
                    Modifier.weight(1f),
                ) { actions.onCustomize(5) }
            }
        }
        if (status.configurationConnected) {
            TextButton(onClick = actions.onDisconnect, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(tr("Disconnect from app", "Desconectar do app"))
            }
        }
    }) {
        val sides: @Composable () -> Unit = {
            SingleChoiceSegmentedButtonRow {
                listOf(tr("Front", "Frente"), tr("Back", "Traseira")).forEachIndexed { index, title ->
                    SegmentedButton(
                        selected = rear == (index == 1),
                        onClick = { rear = index == 1 },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                        label = { Text(title) },
                        icon = { if (!layout.twoPane) SegmentedButtonDefaults.Icon(rear == (index == 1)) },
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Eyebrow(tr("Your controller", "Seu controle"), color = MaterialTheme.colorScheme.primary)
                Text(device.displayName(), style = screenTitleStyle())
                Description(device.listSubtitle())
            }
            if (layout.twoPane && device.artwork != null) sides()
            else {
                val manageControllersLabel = tr("Manage controllers", "Gerenciar controles")
                TextButton(
                    onClick = actions.onOpenDeviceSheet,
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = manageControllersLabel },
                ) {
                    Text(tr("Manage", "Gerenciar"))
                }
            }
        }
        DashboardStatusStrip(
            state = state,
            status = status,
            device = device,
            portuguese = portuguese,
            actions = actions,
        )
        if (isDemo) SimulatedControllerBanner()
        if (state.showDeviceSwitcher) {
            val switchBlockedReason = tr("Finish the current operation first.", "Finalize a operação atual primeiro.")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.switcherDevices.forEach { row ->
                    val selectedChip = row.id == device.id
                    FilterChip(
                        selected = selectedChip,
                        onClick = { if (!state.switching) actions.onChooseDevice(row) },
                        label = { Text(row.displayName()) },
                        enabled = state.canSwitch,
                        modifier = Modifier.semantics {
                            contentDescription = AccessibilitySemantics.controllerSwitcherLabel(row.name, selectedChip, portuguese)
                            if (!state.canSwitch) stateDescription = switchBlockedReason
                        },
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (device.artwork != null && !layout.twoPane) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { sides() }
            }
            ControllerStage(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (layout.shortHeight) Modifier.height(layout.stageHeight(176.dp))
                        else Modifier.heightIn(min = 248.dp, max = layout.stageHeight(300.dp)).aspectRatio(1.05f),
                    ),
                device.artwork,
                state.gameplayInput,
                rear,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardStatusStrip(
    state: DashboardUiState,
    status: ControllerStatus,
    device: ControllerDevice,
    portuguese: Boolean,
    actions: DashboardActions,
) {
    val batteryRefresh = AccessibilitySemantics.dashboardBatteryRefreshEnabled(device, status)
    val channelConnect = AccessibilitySemantics.dashboardAppLinkEnabled(status) && state.connectEnabled
    val batteryLabel = tr("Battery", "Bateria")
    val batteryValue = AccessibilitySemantics.dashboardBatteryValue(status)
    val gameLabel = AccessibilitySemantics.dashboardGameLabel(portuguese)
    val gameValue = AccessibilitySemantics.dashboardGameValue(status, portuguese)
    val channelLabel = AccessibilitySemantics.dashboardChannelLabel(device)
    val channelValue = AccessibilitySemantics.dashboardAppValue(status, portuguese)
    val profilesLabel = tr("Profiles", "Perfis")
    val profilesValue = AccessibilitySemantics.dashboardProfilesValue(state.profileCount)
    val fontScale = LocalDensity.current.fontScale
    val batteryActive = status.batteryPercent != null || status.batteryLevel != null || status.charging
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Full words on a large font or a narrow window; icons and short marks share one line otherwise.
        val stacked = fontScale >= 1.5f || maxWidth < 300.dp
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            InlineStatus(
                AccessibilitySemantics.metricTileSummary(
                    batteryLabel,
                    batteryValue,
                    AccessibilitySemantics.dashboardBatteryDetail(device, status, batteryRefresh, portuguese),
                ),
                stacked,
                actions.onRefreshBattery.takeIf { batteryRefresh },
            ) {
                BatteryStatusIcon(
                    AccessibilitySemantics.dashboardBatteryProgress(status),
                    status.charging,
                    batteryActive,
                )
                if (stacked) StatusCaption(batteryLabel)
                StatusReadout(batteryValue, emphasize = batteryActive)
            }
            InlineStatus(
                AccessibilitySemantics.metricTileSummary(
                    gameLabel,
                    gameValue,
                    AccessibilitySemantics.dashboardGameDetail(status, portuguese),
                ),
                stacked,
            ) {
                HubIcon(
                    Glyph.TEST,
                    Modifier.size(20.dp),
                    if (status.gameplayConnected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StatusCaption(gameLabel)
                if (stacked) StatusReadout(gameValue) else StatusMark(status.gameplayConnected, busy = false, MaterialTheme.colorScheme.tertiary)
            }
            if (device.hasConfigurationLink) {
                InlineStatus(
                    AccessibilitySemantics.metricTileSummary(channelLabel, channelValue, AccessibilitySemantics.dashboardAppDetail(
                        status,
                        portuguese,
                        state.connectEnabled,
                        state.switching,
                    )),
                    stacked,
                    actions.onConnectSelected.takeIf { channelConnect },
                ) {
                    HubIcon(
                        Glyph.BLUETOOTH,
                        Modifier.size(20.dp),
                        if (status.configurationConnected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StatusCaption(channelLabel)
                    when {
                        channelConnect -> StatusReadout(AccessibilitySemantics.dashboardConnectCue(portuguese), action = true)
                        stacked -> StatusReadout(channelValue)
                        else -> StatusMark(status.configurationConnected, status.connecting, MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            if (device.supportsConfiguration) {
                InlineStatus(
                    AccessibilitySemantics.metricTileSummary(
                        profilesLabel,
                        profilesValue,
                        AccessibilitySemantics.dashboardProfilesDetail(state.profileCount, portuguese),
                    ),
                    stacked,
                    onClick = { actions.onCustomize(0) },
                ) {
                    HubIcon(
                        Glyph.PROFILES,
                        Modifier.size(20.dp),
                        if (state.profileCount > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StatusCaption(profilesLabel)
                    StatusReadout(profilesValue, emphasize = state.profileCount > 0)
                }
            }
        }
    }
}

@Composable
private fun InlineStatus(
    summary: String,
    expand: Boolean,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val sizing = (if (expand) Modifier.fillMaxWidth() else Modifier)
        .heightIn(min = 48.dp)
        .then(
            if (onClick != null) Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).clickable(onClick = onClick)
            else Modifier,
        )
        .semantics(mergeDescendants = true) { contentDescription = summary }
    Box(sizing, contentAlignment = Alignment.CenterStart) {
        Row(
            Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

@Composable
private fun StatusCaption(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun StatusReadout(text: String, emphasize: Boolean = true, action: Boolean = false) {
    Text(
        text,
        fontSize = 14.sp,
        fontWeight = if (emphasize || action) FontWeight.SemiBold else FontWeight.Medium,
        color = when {
            action -> MaterialTheme.colorScheme.primary
            emphasize -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun StatusMark(active: Boolean, busy: Boolean, accent: Color) {
    when {
        busy -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = accent)
        active -> HubIcon(Glyph.CHECK, Modifier.size(16.dp), accent)
        else -> Text(
            "—",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun BatteryStatusIcon(progress: Float?, charging: Boolean, active: Boolean) {
    val ink = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val knockout = MaterialTheme.colorScheme.background
    Canvas(Modifier.size(22.dp)) {
        val factor = size.minDimension / 24f
        scale(factor, factor, Offset.Zero) {
            val interior = Path().apply {
                addRoundRect(RoundRect(Rect(2.4f, 8.0f, 18.6f, 16.0f), CornerRadius(1.2f)))
            }
            clipPath(interior) {
                val level = progress?.coerceIn(0f, 1f)
                if (level != null && level > 0f) {
                    drawRoundRect(
                        ink,
                        Offset(2.4f, 8.0f),
                        Size((16.2f * level).coerceAtLeast(1.8f), 8f),
                        CornerRadius(1f),
                    )
                }
                if (charging) {
                    drawPath(batteryBolt(outer = true), knockout)
                    drawPath(batteryBolt(outer = false), ink)
                }
            }
            drawRoundRect(ink, Offset(1.4f, 6.8f), Size(18.2f, 10.4f), CornerRadius(2f), style = Stroke(1.7f))
            drawRoundRect(ink, Offset(20.2f, 9.8f), Size(2.4f, 4.4f), CornerRadius(0.8f))
        }
    }
}

private fun batteryBolt(outer: Boolean) = Path().apply {
    if (outer) {
        moveTo(12.6f, 6.6f)
        lineTo(7.6f, 13.2f)
        lineTo(11.3f, 13.2f)
        lineTo(10.0f, 17.6f)
        lineTo(16.2f, 10.4f)
        lineTo(12.4f, 10.4f)
        close()
    } else {
        moveTo(12.4f, 7.8f)
        lineTo(8.8f, 12.6f)
        lineTo(11.0f, 12.6f)
        lineTo(10.3f, 16.2f)
        lineTo(15.0f, 11.2f)
        lineTo(12.6f, 11.2f)
        close()
    }
}
