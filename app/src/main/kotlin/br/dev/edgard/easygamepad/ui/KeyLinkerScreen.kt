// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Amjad AAYD
package br.dev.edgard.easygamepad.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.dev.edgard.easygamepad.HubViewModel
import br.dev.edgard.easygamepad.ble.LinkPhase
import br.dev.edgard.easygamepad.data.KeyLinkerPhase
import br.dev.edgard.easygamepad.data.KeyLinkerPlanPreview
import br.dev.edgard.easygamepad.data.KeyLinkerState
import br.dev.edgard.easygamepad.protocol.keylinker.*

/**
 * EasySMX X20 / X20 Pro over KeyLinker. Everything shown here was read from the controller.
 * Editors show the exact frames a write sends; the user confirms, and writes X20CTL proved on the same
 * product are sent and then read back. Calibration, factory reset, lighting and turbo stay unavailable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun KeyLinkerScreen(model: HubViewModel) {
    val device by model.selectedDevice.collectAsStateWithLifecycle()
    val link by model.link.collectAsStateWithLifecycle()
    val state by model.keyLinker.collectAsStateWithLifecycle()
    val switching by model.switching.collectAsStateWithLifecycle()
    val connected = device?.address != null && link.address == device?.address && link.phase == LinkPhase.READY
    val connecting = device?.address != null && link.address == device?.address && link.phase !in listOf(LinkPhase.READY, LinkPhase.DISCONNECTED)
    var section by rememberSaveable { mutableIntStateOf(model.profileSection.value.coerceIn(0, 5)) }
    val sections = connected && state.mayRead
    val closing: @Composable ColumnScope.() -> Unit = {
        if (device?.modelId == KeyLinkerEvidence.X20_PRO.modelId) DisplayCard()
        DisabledActionsCard()
    }
    // Wide windows: identity and battery on the left, the settings being read on the right.
    AdaptivePanes(secondary = if (sections) ({
        SettingsSections(state, section, { section = it; model.profileSection.value = it }, model)
        closing()
    }) else null) {
        ScreenHeading(tr("KeyLinker settings", "Ajustes KeyLinker"), device?.name ?: "EasySMX X20",
            tr("Read from the controller. Each change is shown before it is sent, then read back to confirm.",
                "Lidos do controle. Cada alteração é mostrada antes do envio e depois relida para confirmar."))
        if (!connected) HubCard(Modifier.fillMaxWidth().animateContentSize()) {
            Text(tr("Connect to read settings", "Conecte para ler os ajustes"), style = MaterialTheme.typography.titleMedium)
            Description(tr("EasyGamepad opens the configuration channel you chose, asks the controller to identify itself, then reads. It sends nothing to devices you did not choose.",
                "O EasyGamepad abre o canal de configuração escolhido, pede a identificação do controle e então lê. Nada é enviado a dispositivos que você não escolheu."))
            AnimatedVisibility(connecting, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            Button(onClick = model::connectSelected, enabled = !connecting && !switching, modifier = Modifier.fillMaxWidth()) {
                Text(if (connecting) tr("Connecting…", "Conectando…") else tr("Connect to app", "Conectar ao app"))
            }
        }
        if (connected) {
            IdentityCard(state, device?.modelId, model::useIdentifiedModel)
            if (state.mayRead) {
                BatteryCard(state, model::refreshBattery)
                ReadCard(state, model::readKeyLinker)
            }
        }
        if (!sections) closing()
    }
    state.preview?.let { preview -> PlanDialog(preview, connected && !state.busy, model::sendKeyLinker, model::clearKeyLinkerPreview) }
}

@Composable private fun classificationLabel(state: KeyLinkerState): Pair<String, Boolean> = when (state.assessment?.classification) {
    KeyLinkerClassification.VERIFIED -> tr("Verified model and firmware", "Modelo e firmware verificados") to true
    KeyLinkerClassification.KNOWN_MODEL_UNVALIDATED_FIRMWARE -> tr("Known model · firmware not validated", "Modelo conhecido · firmware não validado") to true
    KeyLinkerClassification.KNOWN_MODEL_UNVALIDATED_MODE -> tr("Known model · operating mode not validated", "Modelo conhecido · modo de operação não validado") to false
    KeyLinkerClassification.UNKNOWN_MODEL -> tr("KeyLinker controller · unknown model", "Controle KeyLinker · modelo desconhecido") to false
    null -> when (state.phase) {
        KeyLinkerPhase.NO_REPLY -> tr("No identity reply", "Sem resposta de identificação") to false
        else -> tr("Identifying…", "Identificando…") to false
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun IdentityCard(state: KeyLinkerState, savedModelId: String?, useIdentified: () -> Unit) {
    val (label, ok) = classificationLabel(state)
    HubCard(Modifier.fillMaxWidth().animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("Identification", "Identificação"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            StatusPill(label, ok, color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
        }
        if (state.phase == KeyLinkerPhase.IDENTIFYING) LinearProgressIndicator(Modifier.fillMaxWidth())
        val id = state.identification
        id?.identity?.let { identity ->
            InfoRow(tr("Vendor : product", "Fornecedor : produto"), "${identity.vid}:${identity.pid}")
            InfoRow(tr("Firmware", "Firmware"), identity.firmware)
            InfoRow(tr("Operating mode", "Modo de operação"), identity.mode?.toString() ?: "—")
            InfoRow(tr("Generation", "Geração"), identity.effectiveGeneration?.toString() ?: "—")
        }
        id?.guid?.let { guid -> InfoRow(tr("Updater id", "ID de atualização"), "${guid.model ?: guid.modelCode} · ${guid.version}") }
        state.assessment?.let { Description(it.reason) }
        id?.findings?.forEach { Description("• $it") }
        id?.capabilities?.let { caps ->
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (caps.sticks != 0) StatusPill(tr("Sticks", "Analógicos"), true)
                if (caps.triggers != 0) StatusPill(tr("Triggers", "Gatilhos"), true)
                if (caps.motors != 0) StatusPill(tr("${caps.motorCount} motors", "${caps.motorCount} motores"), true)
                if (caps.changeKey != 0) StatusPill(tr("Remapping", "Remapeamento"), true)
                if (caps.macroSlots.isNotEmpty()) StatusPill(caps.macroSlots.joinToString(" "), true)
                if (caps.turbo != 0) StatusPill("Turbo", true)
                if (caps.lighting != 0) StatusPill(tr("Lighting", "Iluminação"), true)
            }
        }
        val identified = state.identifiedModelId
        if (identified != null && savedModelId != null && identified != savedModelId) {
            InfoBanner(tr("The controller reports ${state.assessment?.known?.product}, but it was saved as a different model.",
                "O controle informa ${state.assessment?.known?.product}, mas foi salvo como outro modelo."), true)
            OutlinedButton(onClick = useIdentified) { Text(tr("Save as ${state.assessment?.known?.product}", "Salvar como ${state.assessment?.known?.product}")) }
        }
        if (state.assessment != null && !state.mayRead) Description(tr("Only identification was requested. Settings are read from verified X20 and X20 Pro identities only.",
            "Somente a identificação foi solicitada. Os ajustes são lidos apenas de identidades verificadas do X20 e X20 Pro."))
    }
}

@Composable private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Mono(value)
    }
}

@Composable private fun BatteryCard(state: KeyLinkerState, refresh: () -> Unit) {
    val level = state.battery?.level ?: 0
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val description = state.battery?.let { tr("Battery level $level of 4", "Bateria nível $level de 4") } ?: tr("Battery not read", "Bateria não lida")
    Surface(onClick = refresh, enabled = !state.busy, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            HubIcon(Glyph.BATTERY, Modifier.size(22.dp), accent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tr("Battery", "Bateria"), style = MaterialTheme.typography.titleMedium)
                Row(Modifier.semantics(mergeDescendants = true) { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(KeyLinkerBattery.LEVELS) { index ->
                        val filled by animateColorAsState(if (index < level) accent else track, motionTween(Motion.MEDIUM), label = "cell")
                        Box(Modifier.width(26.dp).height(10.dp).background(filled, RoundedCornerShape(4.dp)))
                    }
                }
                Description(when {
                    state.battery == null -> tr("Tap to read. The controller reports four steps, not a percentage.", "Toque para ler. O controle informa quatro níveis, não uma porcentagem.")
                    state.battery.charging -> tr("Charging · tap to refresh", "Carregando · toque para atualizar")
                    else -> tr("Tap to refresh", "Toque para atualizar")
                })
            }
        }
    }
}

@Composable private fun ReadCard(state: KeyLinkerState, read: () -> Unit) {
    val progress by animateFloatAsState(state.progress, motionTween(Motion.MEDIUM), label = "read")
    HubCard(Modifier.fillMaxWidth().animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Controller settings", "Ajustes do controle"), style = MaterialTheme.typography.titleMedium)
                Description(state.settings?.let { tr("Read at ", "Lido às ") + timeLabel(it.readAt) } ?: tr("Not read yet", "Ainda não lido"))
            }
            Button(onClick = read, enabled = !state.busy) { Text(if (state.phase == KeyLinkerPhase.READING) tr("Reading…", "Lendo…") else tr("Read again", "Ler novamente")) }
        }
        if (state.phase == KeyLinkerPhase.READING) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable private fun <T> ReadingView(reading: Reading<T>?, content: @Composable (T) -> Unit) {
    when (reading) {
        null, Reading.NotRequested -> Description(tr("Not read yet.", "Ainda não lido."))
        Reading.Unsupported -> Description(tr("This controller does not expose this setting over KeyLinker.", "Este controle não disponibiliza este ajuste pelo KeyLinker."))
        is Reading.Unreadable -> Description(tr("No usable reply: ", "Sem resposta utilizável: ") + reading.reason)
        is Reading.Value -> content(reading.value)
    }
}

@Composable private fun SettingsSections(state: KeyLinkerState, section: Int, choose: (Int) -> Unit, model: HubViewModel) {
    val titles = listOf(tr("Sticks", "Analógicos"), tr("Triggers", "Gatilhos"), tr("Vibration", "Vibração"),
        tr("Buttons", "Botões"), tr("Macros", "Macros"), tr("Lighting", "Iluminação"))
    PrimaryScrollableTabRow(selectedTabIndex = section, edgePadding = 0.dp, containerColor = MaterialTheme.colorScheme.background) {
        titles.forEachIndexed { index, title -> Tab(section == index, { choose(index) }, text = { Text(title) }) }
    }
    val settings = state.settings
    HubCard(Modifier.fillMaxWidth().animateContentSize()) {
        when (section) {
            0, 1 -> {
                val sticks = section == 0
                ReadingView(if (sticks) settings?.sticks else settings?.triggers) { channels ->
                    CurveEditor(channels, sticks) { edited ->
                        model.previewKeyLinker(if (sticks) KeyLinkerFeature.STICK_CURVES else KeyLinkerFeature.TRIGGER_CURVES) { serials ->
                            edited.forEach { it.validate() }
                            KeyLinkerClient.planCurves(sticks, edited, serials, before = channels)
                        }
                    }
                }
            }
            2 -> ReadingView(settings?.motor) { motor -> MotorEditor(motor, model) }
            3 -> ReadingView(settings?.remaps) { remaps -> RemapView(remaps, model) }
            4 -> ReadingView(settings?.macros) { macros -> MacroView(macros, model) }
            else -> {
                ReadingView(settings?.lighting) { entries ->
                    Text(tr("Lighting zones", "Zonas de iluminação"), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        entries.forEach { entry ->
                            Box(Modifier.size(34.dp).background(Color(entry.r, entry.g, entry.b), CircleShape)
                                .semantics { contentDescription = entry.hex })
                        }
                    }
                    Description(tr("Read only. Lighting writes are not verified on any controller.", "Somente leitura. A gravação de iluminação não foi verificada em nenhum controle."))
                }
                ReadingView(settings?.turboRaw) { raw ->
                    Text("Turbo", style = MaterialTheme.typography.titleMedium); Mono(raw)
                    Description(tr("Shown as raw bytes: the turbo record layout is not decoded yet.", "Exibido como bytes: o formato do turbo ainda não foi decodificado."))
                }
            }
        }
    }
}

private val presetNames = mapOf("default" to ("Default" to "Padrão"), "quick" to ("Quick" to "Rápido"), "slow" to ("Slow" to "Lento"),
    "smooth" to ("Smooth" to "Suave"), "fine" to ("Fine" to "Preciso"))

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun CurveEditor(channels: List<KeyLinkerCurve>, sticks: Boolean, preview: (List<KeyLinkerCurve>) -> Unit) {
    var edited by remember(channels) { mutableStateOf(channels) }
    val names = if (sticks) listOf(tr("Left stick", "Analógico esquerdo"), tr("Right stick", "Analógico direito"))
        else listOf(tr("Left trigger (LT)", "Gatilho esquerdo (LT)"), tr("Right trigger (RT)", "Gatilho direito (RT)"))
    edited.forEachIndexed { index, curve ->
        Text(names.getOrElse(index) { "#$index" }, style = MaterialTheme.typography.titleMedium)
        CurveGraph(curve, Modifier.fillMaxWidth().height(150.dp))
        val preset = curve.presetName?.let { presetNames[it] }?.let { tr(it.first, it.second) } ?: tr("Custom curve", "Curva personalizada")
        Description(preset + " · " + tr("dead zones ", "zonas mortas ") + "${curve.innerDeadzone} / ${curve.outerDeadzone}" +
            (if (curve.invertX) tr(" · X inverted", " · X invertido") else "") + (if (curve.invertY) tr(" · Y inverted", " · Y invertido") else ""))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyLinkerCurve.CURVE_PRESETS.keys.forEach { name ->
                FilterChip(curve.presetName == name, { edited = edited.toMutableList().also { it[index] = curve.withPreset(name) } },
                    label = { Text(presetNames[name]?.let { tr(it.first, it.second) } ?: name) })
            }
        }
        val max = curve.maxProgress
        Text(tr("Inner dead zone ", "Zona morta interna ") + curve.innerDeadzone, style = MaterialTheme.typography.labelMedium)
        Slider(curve.innerDeadzone.toFloat(), { v -> edited = edited.toMutableList().also { it[index] = curve.withDeadzones(inner = v.toInt()) } },
            valueRange = 0f..max.toFloat())
        Text(tr("Outer dead zone ", "Zona morta externa ") + curve.outerDeadzone, style = MaterialTheme.typography.labelMedium)
        Slider(curve.outerDeadzone.toFloat(), { v -> edited = edited.toMutableList().also { it[index] = curve.withDeadzones(outer = v.toInt()) } },
            valueRange = 0f..max.toFloat())
    }
    PreviewButton(edited != channels) { preview(edited) }
}

/** The response curve, drawn from the same monotone interpolation X20CTL uses. */
@Composable fun CurveGraph(curve: KeyLinkerCurve, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val points = remember(curve.point1, curve.point2) { curve.shape(48) }
    val reveal = remember(points) { androidx.compose.animation.core.Animatable(0f) }
    val reduced = rememberReducedMotion()
    LaunchedEffect(points) { if (reduced) reveal.snapTo(1f) else { reveal.snapTo(0f); reveal.animateTo(1f, androidx.compose.animation.core.tween(Motion.LONG)) } }
    Canvas(modifier.semantics { contentDescription = "Curve ${curve.point1} ${curve.point2}" }) {
        val w = size.width; val h = size.height
        for (i in 0..4) { val x = w * i / 4; val y = h * i / 4
            drawLine(grid, Offset(x, 0f), Offset(x, h), 1f); drawLine(grid, Offset(0f, y), Offset(w, y), 1f) }
        drawLine(grid.copy(alpha = .8f), Offset(0f, h), Offset(w, 0f), 1.5f)
        val shown = (points.size * reveal.value).toInt().coerceIn(2, points.size)
        val path = Path()
        points.take(shown).forEachIndexed { i, (x, y) ->
            val px = x / 255f * w; val py = h - y / 255f * h
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        listOf(curve.point1, curve.point2).forEach { (x, y) -> drawCircle(accent, 5.dp.toPx(), Offset(x / 255f * w, h - y / 255f * h)) }
    }
}

@Composable private fun MotorEditor(motor: KeyLinkerMotorRecord, model: HubViewModel) {
    var strength by remember(motor) { mutableFloatStateOf(motor.leftPercent.toFloat()) }
    var idle by remember(motor) { mutableFloatStateOf((motor.shutdownMinutes ?: 0).toFloat()) }
    Text(tr("Vibration", "Vibração"), style = MaterialTheme.typography.titleMedium)
    Description(tr("Left ${motor.leftPercent}% · right ${motor.rightPercent}%", "Esquerda ${motor.leftPercent}% · direita ${motor.rightPercent}%"))
    Slider(strength, { strength = it }, valueRange = 0f..100f, steps = 19)
    Text(tr("Turn off when idle", "Desligar quando ocioso"), style = MaterialTheme.typography.titleMedium)
    Description(motor.shutdownMinutes?.let { tr("After $it min", "Após $it min") } ?: tr("Never", "Nunca"))
    Slider(idle, { idle = it }, valueRange = 0f..KeyLinkerMotorRecord.MAX_SHUTDOWN_MINUTES.toFloat(), steps = KeyLinkerMotorRecord.MAX_SHUTDOWN_MINUTES - 1)
    Description(if (idle.toInt() == 0) tr("Draft: never", "Rascunho: nunca") else tr("Draft: ${idle.toInt()} min", "Rascunho: ${idle.toInt()} min"))
    val summary = tr("Vibration and idle timeout", "Vibração e desligamento automático")
    val changedStrength = strength.toInt() != motor.leftPercent
    val changedIdle = (idle.toInt().takeIf { it > 0 }) != motor.shutdownMinutes
    PreviewButton(changedStrength || changedIdle) {
        val feature = if (changedStrength) KeyLinkerFeature.VIBRATION else KeyLinkerFeature.IDLE_TIMEOUT
        model.previewKeyLinker(feature) { serials ->
            val record = motor.withVibration(strength.toInt()).withShutdown(idle.toInt().takeIf { it > 0 })
            KeyLinkerClient.planMotor(record, serials, feature, summary, before = motor)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun RemapView(remaps: KeyLinkerRemaps, model: HubViewModel) {
    var source by remember { mutableStateOf<Int?>(null) }
    Text(tr("Button remapping", "Remapeamento de botões"), style = MaterialTheme.typography.titleMedium)
    if (remaps.targets.isEmpty()) Description(tr("Every button keeps its own function.", "Todos os botões mantêm a própria função."))
    remaps.targets.forEach { (from, to) -> InfoRow(KeyLinkerKey.label(from), "→ " + KeyLinkerKey.label(to)) }
    if (!remaps.sourcesFromPad) Description(tr("The controller did not list its buttons; the recorded X20 list is used.", "O controle não listou os botões; foi usada a lista registrada do X20."))
    Text(tr("Draft a change", "Rascunhar alteração"), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        remaps.sources.filter { code -> KeyLinkerKey.TARGET_ONLY.none { it.code == code } }.forEach { code ->
            FilterChip(source == code, { source = code }, label = { Text(KeyLinkerKey.label(code)) })
        }
    }
    source?.let { from ->
        Description(tr("${KeyLinkerKey.label(from)} becomes…", "${KeyLinkerKey.label(from)} vira…"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            remaps.sources.forEach { to ->
                AssistChip(onClick = {
                    model.previewKeyLinker(KeyLinkerFeature.REMAPS) { serials ->
                        KeyLinkerClient.planRemaps(remaps.sources, remaps.targets + (from to to), serials, before = remaps.targets)
                    }
                }, label = { Text(KeyLinkerKey.label(to)) })
            }
        }
    }
}

@Composable private fun MacroView(macros: Map<String, KeyLinkerMacro?>, model: HubViewModel) {
    var editing by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    Text(tr("Rear-button macros", "Macros dos botões traseiros"), style = MaterialTheme.typography.titleMedium)
    macros.forEach { (slot, macro) ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(slot, style = MaterialTheme.typography.titleSmall)
                Mono(macro?.takeIf { it.steps.isNotEmpty() }?.describe() ?: tr("Empty", "Vazio"))
            }
            TextButton(onClick = { editing = slot; text = macro?.takeIf { it.steps.isNotEmpty() && it.loopIntervalMs == 0 }?.describe().orEmpty() }) { Text(tr("Draft", "Rascunhar")) }
        }
    }
    editing?.let { slot ->
        OutlinedTextField(text, { text = it }, label = { Text(slot) }, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text(tr("Example: A+B:150/40, X  (keys:hold/gap in ms, multiples of 5)", "Exemplo: A+B:150/40, X  (teclas:segurar/pausa em ms, múltiplos de 5)")) })
        PreviewButton(true) {
            val index = KeyLinkerCapabilities.SLOT_NAMES.indexOf(slot)
            model.previewKeyLinker(KeyLinkerFeature.MACROS) { serials ->
                val macro = text.trim().takeIf { it.isNotEmpty() }?.let { KeyLinkerMacro(KeyLinkerMacro.parseSequence(it)) }
                KeyLinkerClient.planMacro(index, macro, serials)
            }
        }
    }
}

@Composable private fun PreviewButton(enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(tr("Review and save…", "Revisar e salvar…")) }
}

@Composable private fun PlanDialog(preview: KeyLinkerPlanPreview, ready: Boolean, send: () -> Unit, dismiss: () -> Unit) {
    val blocked = preview.decision as? KeyLinkerWriteDecision.Blocked
    var showFrames by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss,
        title = { Text(tr("Save to controller?", "Salvar no controle?")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(preview.plan.summary, style = MaterialTheme.typography.titleSmall)
                if (blocked != null) InfoBanner(blocked.detail, true)
                else Description(tr("X20CTL proved this write and its read-back on this controller model. EasyGamepad checks the setting has not changed, sends it, then reads it back to confirm. Keep the controller nearby.",
                    "O X20CTL comprovou esta gravação e a releitura neste modelo de controle. O EasyGamepad confere se o ajuste não mudou, envia e relê para confirmar. Mantenha o controle por perto."))
                TextButton(onClick = { showFrames = !showFrames }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (showFrames) tr("Hide frames", "Ocultar quadros") else tr("Show ${preview.plan.packets.size} frame(s)", "Mostrar ${preview.plan.packets.size} quadro(s)"))
                }
                if (showFrames) preview.packetsHex.forEach { Mono(it) }
            }
        },
        confirmButton = { Button(onClick = send, enabled = blocked == null && ready) { Text(tr("Save", "Salvar")) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(tr("Cancel", "Cancelar")) } })
}

@Composable private fun DisplayCard() {
    HubCard(Modifier.fillMaxWidth()) {
        Text(tr("X20 Pro display", "Tela do X20 Pro"), style = MaterialTheme.typography.titleMedium)
        Description(tr("The X20 Pro's screen image is uploaded over USB while the controller is in its update mode, not over Bluetooth. EasyGamepad cannot change it on Android.",
            "A imagem da tela do X20 Pro é enviada por USB com o controle em modo de atualização, não por Bluetooth. O EasyGamepad não consegue alterá-la no Android."))
    }
}

@Composable private fun DisabledActionsCard() {
    HubCard(Modifier.fillMaxWidth()) {
        Text(tr("Not available here", "Indisponível aqui"), style = MaterialTheme.typography.titleMedium)
        Description(tr("Calibration, factory reset, firmware updates, lighting and turbo stay disabled for the X20 and X20 Pro. Use the official KeyLinker app for those.",
            "Calibração, restauração de fábrica, atualização de firmware, iluminação e turbo ficam desativados para o X20 e X20 Pro. Use o app oficial KeyLinker para isso."))
    }
}
