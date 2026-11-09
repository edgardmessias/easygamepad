// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.dev.edgard.easygamepad.HubViewModel
import br.dev.edgard.easygamepad.data.ControllerArtworkCatalog
import br.dev.edgard.easygamepad.ble.LinkPhase
import br.dev.edgard.easygamepad.data.ProfileStore
import br.dev.edgard.easygamepad.protocol.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun ProfilesScreen(model: HubViewModel) {
    val draft by model.draft.collectAsStateWithLifecycle()
    val prefs by model.preferences.collectAsStateWithLifecycle()
    val hardware by model.hardware.collectAsStateWithLifecycle()
    val link by model.link.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shortWindow = LocalWindowLayout.current.shortHeight
    val actionSpace = if (LocalDensity.current.fontScale >= 1.3f) 160.dp else if (shortWindow) 64.dp else 84.dp
    val section by model.profileSection.collectAsStateWithLifecycle()
    var savedVisible by rememberSaveable { mutableStateOf(false) }
    var editingName by rememberSaveable { mutableStateOf(false) }
    var rear by rememberSaveable { mutableStateOf(false) }
    var showAllButtons by rememberSaveable { mutableStateOf(false) }
    var keyPicker by remember { mutableStateOf<PadKey?>(null) }
    var applyDialog by remember { mutableStateOf(false) }
    var readDialog by remember { mutableStateOf(false) }
    var loadDialog by remember { mutableStateOf<ControllerConfig?>(null) }
    var deleteDialog by remember { mutableStateOf<String?>(null) }
    var exportSnapshot by remember { mutableStateOf<ControllerConfig?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val snapshot = exportSnapshot
        if (uri != null && snapshot != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                requireNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(ProfileStore.export(snapshot)) }
            } }.onSuccess { model.repository.notice("Profile exported", "Perfil exportado") }
                .onFailure { model.repository.notice("Could not export profile", "Não foi possível exportar o perfil", true) }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                requireNotNull(context.contentResolver.openInputStream(uri)).bufferedReader().use { reader ->
                    val buffer = CharArray(100001); var count = 0
                    while (count < buffer.size) { val read = reader.read(buffer, count, buffer.size - count); if (read < 0) break; count += read }
                    require(count <= 100000); String(buffer, 0, count)
                }
            } }.onSuccess(model::importProfile).onFailure { model.repository.notice("Could not read this profile file", "Não foi possível ler este arquivo de perfil", true) }
        }
    }
    Box(Modifier.fillMaxSize()) {
    ScreenColumn(Modifier.padding(bottom = actionSpace)) {
        ScreenHeading(tr("Your setup", "Seus ajustes"), tr("Make it yours.", "Do seu jeito."))
        if (prefs.profiles.isNotEmpty()) {
            SectionTitle(tr("Saved profiles", "Perfis salvos") + " · ${prefs.profiles.size}",
                if (savedVisible) tr("Hide", "Ocultar") else tr("Open", "Abrir")) { savedVisible = !savedVisible }
            if (savedVisible) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                prefs.profiles.forEach { profile ->
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.width(176.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HubIcon(Glyph.PROFILES, color = MaterialTheme.colorScheme.secondary)
                            Text(profile.config.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                            Description(tr("Saved on this phone", "Salvo neste celular"))
                            Row {
                                TextButton(onClick = { loadDialog = profile.config }, modifier = Modifier.weight(1f)) { Text(tr("Load", "Carregar")) }
                                val deleteLabel = tr("Delete profile", "Excluir perfil")
                                IconButton(onClick = { deleteDialog = profile.id }, modifier = Modifier.semantics { contentDescription = deleteLabel }) {
                                    HubIcon(Glyph.TRASH, Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        }
        HubCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { editingName = !editingName }, modifier = Modifier.weight(1f)) {
                    HubIcon(Glyph.EDIT, Modifier.size(16.dp), MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(draft.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape) {
                    Text(tr("Editing", "Em edição"), Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                val readLabel = tr("Get settings from X10", "Trazer ajustes do X10")
                IconButton(onClick = { readDialog = true }, enabled = link.phase == LinkPhase.READY && !hardware.busy && !hardware.calibration.stopRequired,
                    modifier = Modifier.semantics { contentDescription = readLabel }) {
                    HubIcon(Glyph.DOWNLOAD, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (editingName) OutlinedTextField(draft.name, { name -> model.edit { it.copy(name = name.take(40)) } }, modifier = Modifier.fillMaxWidth(), label = { Text(tr("Profile name", "Nome do perfil")) }, singleLine = true, isError = draft.name.isBlank())
            hardware.settingsReadAt?.let { Description(tr("Last read from controller: ", "Última leitura do controle: ") + timeLabel(it)) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(tr("Buttons", "Botões"), tr("Sticks", "Analógicos"), tr("Triggers", "Gatilhos"), tr("Macros", "Macros"), tr("Turbo", "Turbo"), tr("Vibration", "Vibração")).forEachIndexed { index, label ->
                FilterChip(selected = section == index, onClick = { model.profileSection.value = index }, label = { Text(label) })
            }
        }
        when (section) {
            0 -> {
                HubCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Eyebrow(tr("Tap to edit", "Toque para editar"), color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(!rear, { rear = false }, label = { Text(tr("Front", "Frente")) })
                            FilterChip(rear, { rear = true }, label = { Text(tr("Back", "Traseira")) })
                        }
                    }
                    if (!rear) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(PadKey.LB, PadKey.LT, PadKey.RT, PadKey.RB).forEach { source ->
                            OutlinedButton(onClick = { keyPicker = source }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(6.dp)) {
                                Text(source.label + (draft.mappings[source]?.takeIf { it != source }?.let { " → ${it.label}" } ?: ""),
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    ControllerStage(Modifier.fillMaxWidth().then(if (LocalWindowLayout.current.shortHeight) Modifier.height(220.dp) else Modifier.aspectRatio(1.65f)), artwork = ControllerArtworkCatalog.X10, rear = rear, mappings = draft.mappings, onPick = { keyPicker = it })
                    Description(tr("Tap a button to choose its function. Highlights show your custom assignments.",
                        "Toque em um botão para escolher sua função. Os destaques mostram suas alterações."))
                }
                TextButton(onClick = { showAllButtons = !showAllButtons }) {
                    Text(if (showAllButtons) tr("Hide button list", "Ocultar lista de botões") else tr("Show button list", "Ver lista de botões"))
                }
                if (showAllButtons) {
                HubCard(Modifier.fillMaxWidth()) {
                    (PadKey.outputs + listOf(PadKey.M1, PadKey.M2) + listOf(PadKey.M3, PadKey.M4).filter { it in draft.mappings }).chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { source ->
                                val target = draft.mappings[source] ?: source.takeIf { it in PadKey.outputs }
                                Surface(onClick = { keyPicker = source }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(source.label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            HubIcon(Glyph.EDIT, Modifier.size(13.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            HubIcon(Glyph.ARROW, Modifier.size(14.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(target?.label ?: tr("None", "Sem função"), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge,
                                                color = if (target != null && target != source) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }
                TextButton(onClick = { model.edit { it.copy(mappings = it.mappings + PadKey.outputs.associateWith { key -> key }) } }) { Text(tr("Restore default button functions", "Restaurar funções originais dos botões")) }
                Description(tr("Removing a rear button function here may leave its previous function on the controller. Apply your changes, then get the settings from X10 to check.", "Remover uma função traseira aqui pode manter a função anterior no controle. Aplique as alterações e use Trazer ajustes do X10 para conferir."))
            }
            1 -> {
                Description(tr("Reduce unwanted movement near the center or choose how far you move the stick to reach full speed.", "Reduza movimentos involuntários perto do centro ou escolha quanto mover o analógico para atingir a velocidade máxima."))
                StickEditor(tr("Left stick", "Analógico esquerdo"), draft.leftStick) { value -> model.edit { it.copy(leftStick = value) } }
                StickEditor(tr("Right stick", "Analógico direito"), draft.rightStick) { value -> model.edit { it.copy(rightStick = value) } }
            }
            2 -> {
                Description(tr("Choose where each trigger begins responding and reaches full output.", "Escolha onde cada gatilho começa a responder e atinge o valor máximo."))
                HubCard(Modifier.fillMaxWidth()) { Text("LT", style = MaterialTheme.typography.titleLarge); RangeEditor(draft.leftTrigger.min, draft.leftTrigger.max) { min, max -> model.edit { it.copy(leftTrigger = TriggerSettings(min, max)) } } }
                HubCard(Modifier.fillMaxWidth()) { Text("RT", style = MaterialTheme.typography.titleLarge); RangeEditor(draft.rightTrigger.min, draft.rightTrigger.max) { min, max -> model.edit { it.copy(rightTrigger = TriggerSettings(min, max)) } } }
            }
            3 -> MacroEditor(draft.macros) { value -> model.edit { it.copy(macros = value) } }
            4 -> TurboEditor(draft.turbos) { value -> model.edit { it.copy(turbos = value) } }
            else -> HubCard(Modifier.fillMaxWidth()) {
                Text(tr("Feel every moment.", "Sinta cada momento."), style = MaterialTheme.typography.titleLarge)
                Description(tr("Set the intensity of each controller motor. Availability depends on the controller's operating mode.", "Ajuste a intensidade de cada motor do controle. A disponibilidade depende do modo de operação."))
                ValueSlider(tr("Left motor", "Motor esquerdo"), draft.leftVibration, 0..100, "%") { value -> model.edit { it.copy(leftVibration = value) } }
                ValueSlider(tr("Right motor", "Motor direito"), draft.rightVibration, 0..100, "%") { value -> model.edit { it.copy(rightVibration = value) } }
                OutlinedButton(onClick = model::rumble) { Text(tr("Test Android gamepad vibration", "Testar vibração do controle no Android")) }
                Description(tr("The test uses the vibration supported by your phone. Apply the profile to try your chosen intensity on the controller.", "O teste usa a vibração disponível no seu celular. Aplique o perfil para experimentar a intensidade escolhida no controle."))
            }
        }
        if (hardware.busy) LinearProgressIndicator(progress = { hardware.progress }, modifier = Modifier.fillMaxWidth())
        if (link.phase != LinkPhase.READY) TextButton(onClick = model::connectSelected, modifier = Modifier.fillMaxWidth()) { Text(tr("Connect your controller to apply", "Conecte o controle para aplicar")) }
        OutlinedButton(onClick = { exportSnapshot = draft; export.launch("easygamepad-profile.json") }, enabled = runCatching { draft.validate() }.isSuccess, modifier = Modifier.fillMaxWidth()) { Text(tr("Export profile", "Exportar perfil")) }
        TextButton(onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Text(tr("Import profile from a file", "Importar perfil de um arquivo")) }
        hardware.appliedAt?.let { Description(tr("Last controller confirmation: ", "Última confirmação do controle: ") + timeLabel(it)) }
    }
    Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Short landscape windows keep the action bar to one row; the apply dialog still explains this.
            if (!shortWindow) Text(tr("Edits reach your controller when you apply.", "Os ajustes só mudam no controle ao aplicar."),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = model::save, enabled = runCatching { draft.validate() }.isSuccess,
                    modifier = Modifier.weight(1f)) { Text(tr("Save profile", "Salvar perfil")) }
                Button(onClick = { applyDialog = true }, enabled = link.phase == LinkPhase.READY && !hardware.busy && !hardware.calibration.stopRequired && runCatching { draft.validate() }.isSuccess,
                    modifier = Modifier.weight(1f)) { Text(tr("Apply to X10", "Aplicar ao X10")) }
            }
        }
    }
    }
    keyPicker?.let { source -> KeyPicker(tr("Output for ", "Função para ") + source.label, PadKey.outputs, onDismiss = { keyPicker = null },
        clearLabel = if (source in PadKey.outputs) tr("Restore standard output", "Restaurar função padrão") else tr("Remove assignment", "Remover atribuição"),
        onClear = { model.edit { it.copy(mappings = if (source in PadKey.outputs) it.mappings + (source to source) else it.mappings - source) }; keyPicker = null }) { target ->
        model.edit { it.copy(mappings = it.mappings + (source to target)) }; keyPicker = null
    } }
    if (readDialog) ConfirmDialog(tr("Read current settings?", "Ler configurações atuais?"),
        tr("Replace the profile you are editing with the settings on your X10? Save first if you want to keep your edits. The profile name and macro sequences will be kept.",
            "Substituir o perfil em edição pelos ajustes do seu X10? Salve antes se quiser guardar suas alterações. O nome do perfil e as sequências de macro serão mantidos."),
        tr("Get settings", "Trazer ajustes"), { readDialog = false }) { readDialog = false; model.readController() }
    if (applyDialog) ConfirmDialog(tr("Apply this setup?", "Aplicar esta configuração?"),
        tr("Send this profile to your X10? Its current settings may change. Keep the controller nearby until it confirms completion. Removing a macro from this profile does not erase one already saved on the controller.",
            "Enviar este perfil para seu X10? Os ajustes atuais podem mudar. Mantenha o controle por perto até a confirmação. Remover uma macro deste perfil não apaga uma sequência já salva no controle."),
        tr("Apply setup", "Aplicar configuração"), { applyDialog = false }) { applyDialog = false; model.apply() }
    loadDialog?.let { config -> ConfirmDialog(tr("Load profile?", "Carregar perfil?"), tr("This replaces the profile you are editing. Save first if you want to keep your changes.", "Isso substitui o perfil em edição. Salve antes se quiser guardar suas alterações."), tr("Load", "Carregar"), { loadDialog = null }) { model.load(config); loadDialog = null } }
    deleteDialog?.let { id -> ConfirmDialog(tr("Delete saved profile?", "Excluir perfil salvo?"), tr("The saved copy will be removed from this phone.", "A cópia salva será removida deste celular."), tr("Delete", "Excluir"), { deleteDialog = null }) { model.delete(id); deleteDialog = null } }
}

@Composable fun ConfirmDialog(title: String, body: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(action) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "Cancelar")) } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun KeyPicker(title: String, keys: List<PadKey>, onDismiss: () -> Unit,
    clearLabel: String? = null, onClear: (() -> Unit)? = null, onPick: (PadKey) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            keys.forEach { key -> OutlinedButton(onClick = { onPick(key) }, contentPadding = PaddingValues(horizontal = 14.dp), modifier = Modifier.widthIn(min = 54.dp)) { Text(key.label) } }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "Cancelar")) } },
        dismissButton = { if (onClear != null && clearLabel != null) TextButton(onClick = onClear) { Text(clearLabel) } })
}

@Composable fun ValueSlider(label: String, value: Int, range: IntRange, suffix: String = "", onChange: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) { Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); Text("$value$suffix", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
        Slider(value.toFloat(), { onChange(it.toInt().coerceIn(range)) }, valueRange = range.first.toFloat()..range.last.toFloat(), modifier = Modifier.semantics { contentDescription = label })
    }
}
@Composable fun RangeEditor(min: Int, max: Int, change: (Int, Int) -> Unit) {
    ResponseCurve(min, max)
    ValueSlider(tr("Ignore movement up to", "Ignorar movimentos até"), min, 0..(max - 1), "%") { change(it, max) }
    ValueSlider(tr("Reach maximum at", "Atingir o máximo em"), max, (min + 1)..100, "%") { change(min, it) }
}

@Composable private fun ResponseCurve(min: Int, max: Int) {
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val label = tr("Response: ignore the first $min%, reach full output at $max%.", "Resposta: ignora os primeiros $min% e atinge o máximo em $max%.")
    Canvas(Modifier.fillMaxWidth().height(90.dp).semantics { contentDescription = label }) {
        val inset = 8.dp.toPx(); val w = size.width - 2 * inset; val h = size.height - 2 * inset
        for (step in 0..4) {
            val t = step / 4f
            drawLine(grid, Offset(inset + w * t, inset), Offset(inset + w * t, inset + h))
            drawLine(grid, Offset(inset, inset + h * t), Offset(inset + w, inset + h * t))
        }
        val path = Path().apply {
            moveTo(inset, inset + h); lineTo(inset + w * min / 100f, inset + h)
            lineTo(inset + w * max / 100f, inset); lineTo(inset + w, inset)
        }
        drawPath(path, color, style = Stroke(2.dp.toPx()))
        drawCircle(color, 4.dp.toPx(), Offset(inset + w * min / 100f, inset + h))
        drawCircle(color, 4.dp.toPx(), Offset(inset + w * max / 100f, inset))
    }
    Description(tr("Movement → response", "Movimento → resposta"))
}
@Composable fun ToggleRow(title: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); Switch(checked, change) }
}
@Composable private fun StickEditor(title: String, value: StickSettings, change: (StickSettings) -> Unit) {
    HubCard(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        RangeEditor(value.min, value.max) { min, max -> change(value.copy(min = min, max = max)) }
        ToggleRow(tr("Reverse left and right", "Trocar esquerda e direita"), value.invertX) { change(value.copy(invertX = it)) }
        ToggleRow(tr("Reverse up and down", "Trocar cima e baixo"), value.invertY) { change(value.copy(invertY = it)) }
    }
}

@Composable private fun MacroEditor(macros: List<Macro>, change: (List<Macro>) -> Unit) {
    var picker by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Description(tr("Build a sequence for a rear paddle. Each step presses a button, holds it, then waits. Up to 32 steps per paddle.", "Crie uma sequência para um botão traseiro. Cada etapa pressiona um botão, segura e espera. Até 32 etapas por botão."))
        if (macros.isEmpty()) InfoBanner(tr("Start with a simple sequence, like A → B, assigned to M1.", "Comece com uma sequência simples, como A → B, atribuída ao M1."))
        macros.forEachIndexed { index, macro ->
            fun update(updated: Macro) = change(macros.toMutableList().also { it[index] = updated })
            HubCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(macro.trigger.label, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { change(macros.filterIndexed { i, _ -> i != index }) }) { Text(tr("Remove", "Remover")) }
                }
                ToggleRow(tr("Repeat sequence", "Repetir sequência"), macro.repeat) { update(macro.copy(repeat = it)) }
                macro.steps.forEachIndexed { stepIndex, step ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Mono("%02d".format(stepIndex + 1))
                        OutlinedButton(onClick = { picker = index to stepIndex }) { Text(step.key.label) }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { if (stepIndex > 0) update(macro.copy(steps = macro.steps.toMutableList().also { java.util.Collections.swap(it, stepIndex, stepIndex - 1) })) }, enabled = stepIndex > 0) { Text("↑") }
                        val removeLabel = tr("Remove step", "Remover etapa")
                        IconButton(onClick = { update(macro.copy(steps = macro.steps.filterIndexed { i, _ -> i != stepIndex })) }, modifier = Modifier.semantics { contentDescription = removeLabel }) { HubIcon(Glyph.CLOSE, Modifier.size(18.dp)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberField(tr("Hold (ms)", "Pressionar (ms)"), step.holdMs, 1..65535, Modifier.weight(1f)) { value -> update(macro.copy(steps = macro.steps.toMutableList().also { it[stepIndex] = step.copy(holdMs = value) })) }
                        NumberField(tr("Wait (ms)", "Esperar (ms)"), step.delayMs, 0..65535, Modifier.weight(1f)) { value -> update(macro.copy(steps = macro.steps.toMutableList().also { it[stepIndex] = step.copy(delayMs = value) })) }
                    }
                }
                OutlinedButton(onClick = { update(macro.copy(steps = macro.steps + MacroStep())) }, enabled = macro.steps.size < 32, modifier = Modifier.fillMaxWidth()) { Text(tr("Add step", "Adicionar etapa")) }
                if (macro.steps.isEmpty()) Description(tr("Add at least one step before saving or applying.", "Adicione pelo menos uma etapa antes de salvar ou aplicar."))
            }
        }
        val available = listOf(PadKey.M1, PadKey.M2).filter { key -> macros.none { it.trigger == key } }
        if (available.isNotEmpty()) {
            Eyebrow(tr("Add a paddle sequence", "Adicionar sequência traseira"))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                available.forEach { key -> OutlinedButton(onClick = { change(macros + Macro(key, steps = listOf(MacroStep()))) }) { Text("+ ${key.label}") } }
            }
        }
    }
    picker?.let { (index, stepIndex) -> KeyPicker(tr("Step button", "Botão da etapa"), PadKey.outputs, { picker = null }) { key ->
        val macro = macros[index]
        change(macros.toMutableList().also { it[index] = macro.copy(steps = macro.steps.toMutableList().also { steps -> steps[stepIndex] = steps[stepIndex].copy(key = key) }) }); picker = null
    } }
}

@Composable private fun NumberField(label: String, value: Int, range: IntRange, modifier: Modifier, change: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(text, { candidate ->
        if (candidate.length <= 5 && candidate.all(Char::isDigit)) {
            text = candidate
            candidate.toIntOrNull()?.takeIf { it in range }?.let(change)
        }
    }, label = { Text(label) }, singleLine = true, isError = text.toIntOrNull() !in range, modifier = modifier,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
}

@Composable private fun TurboEditor(turbos: List<Turbo>, change: (List<Turbo>) -> Unit) {
    var picker by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Description(tr("Make a button repeat automatically. Choose the number of presses per second. Automatic turbo behavior depends on your controller.", "Faça um botão repetir automaticamente. Escolha quantas vezes por segundo ele será pressionado. O turbo automático depende do modo do controle."))
        turbos.forEachIndexed { index, turbo ->
            fun update(value: Turbo) = change(turbos.toMutableList().also { it[index] = value })
            HubCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(turbo.key.label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { change(turbos.filterIndexed { i, _ -> i != index }) }) { Text(tr("Remove", "Remover")) }
                }
                ValueSlider(tr("Presses per second", "Pressionamentos por segundo"), turbo.rate, 1..20) { update(turbo.copy(rate = it)) }
                ToggleRow(tr("Automatic turbo", "Turbo automático"), turbo.automatic) { update(turbo.copy(automatic = it)) }
            }
        }
        OutlinedButton(onClick = { picker = true }, enabled = turbos.size < PadKey.outputs.size, modifier = Modifier.fillMaxWidth()) { Text(tr("Add turbo button", "Adicionar botão turbo")) }
        Description(tr("Removing turbo here may leave the previous turbo setting on your controller. Get the settings from X10 after applying to check.", "Remover o turbo aqui pode manter o ajuste anterior no controle. Use Trazer ajustes do X10 depois de aplicar para conferir."))
    }
    if (picker) KeyPicker(tr("Turbo button", "Botão turbo"), PadKey.outputs.filter { key -> turbos.none { it.key == key } }, { picker = false }) { key -> change(turbos + Turbo(key)); picker = false }
}
