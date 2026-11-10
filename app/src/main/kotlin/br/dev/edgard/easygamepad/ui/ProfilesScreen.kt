// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.DisposableEffect
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
import br.dev.edgard.easygamepad.data.x10ApplyNotice
import br.dev.edgard.easygamepad.input.GamepadVibrationAvailability
import br.dev.edgard.easygamepad.protocol.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun ProfilesScreen(model: HubViewModel) {
    val draft by model.draft.collectAsStateWithLifecycle()
    val prefs by model.preferences.collectAsStateWithLifecycle()
    val hardware by model.hardware.collectAsStateWithLifecycle()
    val vibrationTestAvailability by model.vibrationTestAvailability.collectAsStateWithLifecycle()
    val canTestX10Vibration by model.canTestX10Vibration.collectAsStateWithLifecycle()
    val x10VibrationPreview by model.x10VibrationPreview.collectAsStateWithLifecycle()
    val link by model.link.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layout = LocalWindowLayout.current
    val shortWindow = layout.shortHeight
    val actionSpace = profilesEditorBottomInset(shortWindow, LocalDensity.current.fontScale)
    val section by model.profileSection.collectAsStateWithLifecycle()
    val profileScroll = rememberScrollState()
    val sectionLabels = listOf(tr("Buttons", "Botões"), tr("Sticks", "Analógicos"), tr("Triggers", "Gatilhos"), tr("Macros", "Macros"), tr("Turbo", "Turbo"), tr("Vibration", "Vibração"))
    DisposableEffect(Unit) { onDispose { model.cancelX10VibrationPreview() } }
    LaunchedEffect(section) { profileScroll.scrollTo(0) }
    var savedVisible by rememberSaveable { mutableStateOf(false) }
    var editingName by rememberSaveable { mutableStateOf(false) }
    var rear by rememberSaveable { mutableStateOf(false) }
    var showAllButtons by rememberSaveable { mutableStateOf(false) }
    var keyPicker by remember { mutableStateOf<PadKey?>(null) }
    var applySnapshot by remember { mutableStateOf<ControllerConfig?>(null) }
    var readDialog by remember { mutableStateOf(false) }
    var loadDialog by remember { mutableStateOf<ControllerConfig?>(null) }
    var deleteDialog by remember { mutableStateOf<String?>(null) }
    var exportSnapshot by remember { mutableStateOf<ControllerConfig?>(null) }
    val validationIssues = remember(draft) { draft.profileValidationIssues() }
    val draftValid = validationIssues.isEmpty()
    val editorUi = profilesEditorUiState(link.phase, hardware.busy, hardware.calibration, draftValid)
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
    ScreenColumn(Modifier.padding(bottom = actionSpace), scrollState = profileScroll) {
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
                IconButton(onClick = { readDialog = true }, enabled = editorUi.canApplyOrReadFromDevice,
                    modifier = Modifier.semantics { contentDescription = readLabel }) {
                    HubIcon(Glyph.DOWNLOAD, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (editingName) OutlinedTextField(draft.name, { name -> model.edit { it.copy(name = name.take(40)) } }, modifier = Modifier.fillMaxWidth(), label = { Text(tr("Profile name", "Nome do perfil")) }, singleLine = true,
                isError = validationIssues.any { it.field == br.dev.edgard.easygamepad.protocol.ProfileField.NAME },
                supportingText = validationIssues.firstOrNull { it.field == br.dev.edgard.easygamepad.protocol.ProfileField.NAME }?.localizedMessage()?.let { { Text(it) } })
            ProfileValidationBanner(if (editingName) validationIssues.filter { it.field != br.dev.edgard.easygamepad.protocol.ProfileField.NAME } else validationIssues)
            hardware.settingsReadAt?.let { Description(tr("Last read from controller: ", "Última leitura do controle: ") + timeLabel(it)) }
        }
        ProfileEditorSectionNav(sectionLabels, section, { model.profileSection.value = it })
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
                                val target = draft.mappings[source]
                                Surface(onClick = { keyPicker = source }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(source.label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            HubIcon(Glyph.EDIT, Modifier.size(13.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            HubIcon(Glyph.ARROW, Modifier.size(14.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(target?.label ?: tr("Not sent", "Não enviado"), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge,
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
                Description(tr("An unlisted assignment is not sent and does not request deletion. Existing assignments may remain on the X10. Restoring a standard button sends its own output explicitly.", "Uma atribuição ausente não é enviada e não solicita exclusão. As atribuições existentes podem permanecer no X10. Restaurar um botão padrão envia sua própria função explicitamente."))
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
                ValueSlider(tr("Left motor", "Motor esquerdo"), draft.leftVibration, 0..100, "%",
                    previewing = x10VibrationPreview.busy(X10Motor.LEFT),
                    exactInput = true,
                    onChange = { value -> model.edit { it.copy(leftVibration = value) } },
                    onSliderChange = model::setLeftVibration,
                    onChangeFinished = model::finishLeftVibrationPreview)
                ValueSlider(tr("Right motor", "Motor direito"), draft.rightVibration, 0..100, "%",
                    previewing = x10VibrationPreview.busy(X10Motor.RIGHT),
                    exactInput = true,
                    onChange = { value -> model.edit { it.copy(rightVibration = value) } },
                    onSliderChange = model::setRightVibration,
                    onChangeFinished = model::finishRightVibrationPreview)
                Description(tr("While the X10 configuration channel is ready, hold and drag a slider to feel that motor at the chosen intensity. Release the slider to stop. Nothing is saved until you apply the profile. If vibration continues after release, turn the controller off.",
                    "Com o canal de configuração do X10 pronto, mantenha e arraste um controle para sentir aquele motor na intensidade escolhida. Solte para parar. Nada é gravado até você aplicar o perfil. Se continuar vibrando após soltar, desligue o controle."))
                if (!canTestX10Vibration) Description(tr("Connect the X10 configuration channel and finish any active operation to preview motor intensity on the controller.",
                    "Conecte o canal de configuração do X10 e conclua qualquer operação em andamento para pré-visualizar a intensidade dos motores no controle."))
                OutlinedButton(onClick = model::rumble, enabled = vibrationTestAvailability == GamepadVibrationAvailability.AVAILABLE) {
                    Text(tr("Test Android gamepad vibration", "Testar vibração do controle no Android"))
                }
                Description(when (vibrationTestAvailability) {
                    GamepadVibrationAvailability.NO_DEVICE -> tr(
                        "Connect and select the gameplay controller to test vibration. The app configuration connection alone is not enough.",
                        "Conecte e selecione o controle para jogar antes de testar a vibração. Apenas a conexão de configuração com o app não é suficiente.")
                    GamepadVibrationAvailability.UNSUPPORTED -> tr(
                        "Android does not expose vibration for this controller in its current connection mode. Use the X10 sliders above through its configuration connection.",
                        "O Android não disponibiliza vibração para este controle no modo de conexão atual. Use os controles deslizantes do X10 acima pela conexão de configuração.")
                    GamepadVibrationAvailability.AVAILABLE -> tr(
                        "Plays a short pulse on the selected gamepad using Android. It does not preview this draft's motor intensities.",
                        "Aciona um pulso curto no controle selecionado usando o Android. O teste não reproduz as intensidades dos motores deste rascunho.")
                })
            }
        }
        if (hardware.busy) LinearProgressIndicator(progress = { hardware.progress }, modifier = Modifier.fillMaxWidth())
        if (link.phase != LinkPhase.READY) TextButton(onClick = model::connectSelected, modifier = Modifier.fillMaxWidth()) { Text(tr("Connect your controller to apply", "Conecte o controle para aplicar")) }
        OutlinedButton(onClick = { exportSnapshot = draft; export.launch("easygamepad-profile.json") }, enabled = editorUi.canSaveProfile, modifier = Modifier.fillMaxWidth()) { Text(tr("Export profile", "Exportar perfil")) }
        TextButton(onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Text(tr("Import profile from a file", "Importar perfil de um arquivo")) }
        hardware.applyResult?.let { result ->
            val feedback = x10ApplyNotice(result)
            OperationOutcome(
                tr("Last apply result", "Resultado da última aplicação"),
                tr(feedback.english, feedback.portuguese),
                feedback.error,
                hardware.appliedAt,
                tr("Last settings read-back match: ", "Última conferência dos ajustes por releitura: "),
            )
        } ?: hardware.appliedAt?.let {
            Description(tr("Last settings read-back match: ", "Última conferência dos ajustes por releitura: ") + timeLabel(it))
        }
    }
    ScreenBottomActions(
        if (!draftValid) profileSaveBlockedReason(validationIssues)
            ?: tr("Edits reach your controller when you apply.", "Os ajustes só mudam no controle ao aplicar.")
        else tr("Edits reach your controller when you apply.", "Os ajustes só mudam no controle ao aplicar."),
        showHint = !shortWindow || !draftValid,
        tr("Save profile", "Salvar perfil"),
        tr("Apply to X10", "Aplicar ao X10"),
        onSecondary = model::save,
        onPrimary = { applySnapshot = draft },
        secondaryEnabled = editorUi.canSaveProfile,
        primaryEnabled = editorUi.canApplyOrReadFromDevice,
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding(),
    )
    }
    keyPicker?.let { source -> KeyPicker(tr("Output for ", "Função para ") + source.label, PadKey.outputs, onDismiss = { keyPicker = null },
        clearLabel = if (source in PadKey.outputs) tr("Restore standard output", "Restaurar função padrão") else tr("Do not send assignment", "Não enviar atribuição"),
        onClear = { model.edit { it.copy(mappings = if (source in PadKey.outputs) it.mappings + (source to source) else it.mappings - source) }; keyPicker = null }) { target ->
        model.edit { it.copy(mappings = it.mappings + (source to target)) }; keyPicker = null
    } }
    if (readDialog) ConfirmDialog(tr("Read current settings?", "Ler configurações atuais?"),
        tr("Replace the profile you are editing with the settings on your X10? Save first if you want to keep your edits. The profile name and macro sequences will be kept.",
            "Substituir o perfil em edição pelos ajustes do seu X10? Salve antes se quiser guardar suas alterações. O nome do perfil e as sequências de macro serão mantidos."),
        tr("Get settings", "Trazer ajustes"), { readDialog = false }) { readDialog = false; model.readController() }
    applySnapshot?.let { snapshot ->
        X10ApplyDialog(snapshot, editorUi.configurationReady,
            dismiss = { applySnapshot = null }, apply = { applySnapshot = null; model.apply(snapshot) })
    }
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

@Composable fun ValueSlider(
    label: String,
    value: Int,
    range: IntRange,
    suffix: String = "",
    previewing: Boolean = false,
    exactInput: Boolean = false,
    onChangeFinished: (() -> Unit)? = null,
    onSliderChange: ((Int) -> Unit)? = null,
    onChange: (Int) -> Unit,
) {
    val sliderChange: (Int) -> Unit = { value ->
        if (onSliderChange != null) {
            MotorVibrationInput.applySliderChange(value, onChange, onSliderChange)
        } else {
            onChange(value)
        }
    }
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            VibrationPreviewDot(previewing)
            if (exactInput) {
                ExactValueField(value, range, suffix, Modifier.widthIn(min = 72.dp, max = 96.dp)) { value ->
                    if (onSliderChange != null) {
                        MotorVibrationInput.applyNumericChange(value, onChange, onSliderChange)
                    } else {
                        onChange(value)
                    }
                }
            } else {
                Text("$value$suffix", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
        Slider(
            value.toFloat(),
            onValueChange = { sliderChange(it.toInt().coerceIn(range)) },
            onValueChangeFinished = { onChangeFinished?.invoke() },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}
@Composable private fun VibrationPreviewDot(active: Boolean) {
    if (!active) return
    val transition = rememberInfiniteTransition(label = "vibration-preview")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "vibration-preview-alpha",
    )
    Box(
        Modifier
            .padding(end = 8.dp)
            .size(10.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha), CircleShape),
    )
}

@Composable fun RangeEditor(min: Int, max: Int, change: (Int, Int) -> Unit) {
    ResponseCurve(min, max)
    ValueSlider(tr("Ignore movement up to", "Ignorar movimentos até"), min, 0..(max - 1), "%", exactInput = true) { change(it, max) }
    ValueSlider(tr("Reach maximum at", "Atingir o máximo em"), max, (min + 1)..100, "%", exactInput = true) { change(min, it) }
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
                    TextButton(onClick = { change(macros.filterIndexed { i, _ -> i != index }) }) { Text(tr("Remove from profile", "Remover do perfil")) }
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
        Description(tr("Removing a macro from the profile omits its steps from upload. It does not request deletion of an onboard sequence.",
            "Remover uma macro do perfil omite suas etapas do envio. Isso não solicita a exclusão de uma sequência no controle."))
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

@Composable private fun ExactValueField(value: Int, range: IntRange, suffix: String, modifier: Modifier, change: (Int) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var text by remember(value) { mutableStateOf(value.toString()) }
    LaunchedEffect(value) { if (!focused) text = value.toString() }
    OutlinedTextField(
        text,
        { candidate ->
            if (candidate.length > 5) return@OutlinedTextField
            if (candidate.isEmpty() || candidate.all(Char::isDigit)) {
                text = candidate
                candidate.toIntOrNull()?.takeIf { it in range }?.let(change)
            }
        },
        singleLine = true,
        isError = text.isNotEmpty() && text.toIntOrNull() !in range,
        suffix = { Text(suffix) },
        modifier = modifier.onFocusChanged {
            focused = it.isFocused
            if (!it.isFocused) text = value.toString()
        },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
        textStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable private fun NumberField(label: String, value: Int, range: IntRange, modifier: Modifier, change: (Int) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var text by remember(value) { mutableStateOf(value.toString()) }
    LaunchedEffect(value) { if (!focused) text = value.toString() }
    OutlinedTextField(text, { candidate ->
        if (candidate.length > 5) return@OutlinedTextField
        if (candidate.isEmpty() || candidate.all(Char::isDigit)) {
            text = candidate
            candidate.toIntOrNull()?.takeIf { it in range }?.let(change)
        }
    }, label = { Text(label) }, singleLine = true, isError = text.isNotEmpty() && text.toIntOrNull() !in range, modifier = modifier.onFocusChanged {
        focused = it.isFocused
        if (!it.isFocused) text = value.toString()
    },
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
                    TextButton(onClick = { change(turbos.filterIndexed { i, _ -> i != index }) }) { Text(tr("Remove from profile", "Remover do perfil")) }
                }
                ValueSlider(tr("Presses per second", "Pressionamentos por segundo"), turbo.rate, 1..20, exactInput = true) { update(turbo.copy(rate = it)) }
                ToggleRow(tr("Automatic turbo", "Turbo automático"), turbo.automatic) { update(turbo.copy(automatic = it)) }
            }
        }
        OutlinedButton(onClick = { picker = true }, enabled = turbos.size < PadKey.outputs.size, modifier = Modifier.fillMaxWidth()) { Text(tr("Add turbo button", "Adicionar botão turbo")) }
        Description(tr("Removing turbo here may leave the previous turbo setting on your controller. Get the settings from X10 after applying to check.", "Remover o turbo aqui pode manter o ajuste anterior no controle. Use Trazer ajustes do X10 depois de aplicar para conferir."))
    }
    if (picker) KeyPicker(tr("Turbo button", "Botão turbo"), PadKey.outputs.filter { key -> turbos.none { it.key == key } }, { picker = false }) { key -> change(turbos + Turbo(key)); picker = false }
}
