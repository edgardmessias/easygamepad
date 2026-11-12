// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.dev.edgard.easygamepad.data.EditorSaveBinding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.dev.edgard.easygamepad.HubViewModel
import br.dev.edgard.easygamepad.data.ConsumerCopy
import br.dev.edgard.easygamepad.data.ControllerArtworkCatalog
import br.dev.edgard.easygamepad.ble.LinkPhase
import br.dev.edgard.easygamepad.data.DraftStatusKind
import br.dev.edgard.easygamepad.data.ProfileStore
import br.dev.edgard.easygamepad.data.SavedProfile
import br.dev.edgard.easygamepad.data.canAddSavedProfile
import br.dev.edgard.easygamepad.demo.DemoController
import br.dev.edgard.easygamepad.input.GamepadVibrationAvailability
import br.dev.edgard.easygamepad.protocol.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun ProfilesScreen(model: HubViewModel) {
    val selected by model.selectedDevice.collectAsStateWithLifecycle()
    val draft by model.draft.collectAsStateWithLifecycle()
    val prefs by model.preferences.collectAsStateWithLifecycle()
    val hardware by model.hardware.collectAsStateWithLifecycle()
    val vibrationTestAvailability by model.vibrationTestAvailability.collectAsStateWithLifecycle()
    val canTestX10Vibration by model.canTestX10Vibration.collectAsStateWithLifecycle()
    val x10VibrationPreview by model.x10VibrationPreview.collectAsStateWithLifecycle()
    val link by model.link.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val largeText = density.fontScale >= 1.3f
    val reducedMotion = rememberReducedMotion()
    val section by model.profileSection.collectAsStateWithLifecycle()
    val savedProfileId by model.savedProfileId.collectAsStateWithLifecycle()
    val draftStatus by model.draftStatus.collectAsStateWithLifecycle()
    val canSaveNewCopy = canAddSavedProfile(prefs.profiles)
    val profileScroll = rememberScrollState()
    var actionsCollapsed by remember { mutableStateOf(false) }
    val collapseThreshold = with(density) { 28.dp.toPx() }
    val editorScrollConnection = remember(section, collapseThreshold) {
        object : NestedScrollConnection {
            var travel = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val delta = consumed.y
                when {
                    delta < -1f -> {
                        travel = minOf(0f, travel) + delta
                        if (travel <= -collapseThreshold) { actionsCollapsed = true; travel = 0f }
                    }
                    delta > 1f -> {
                        travel = maxOf(0f, travel) + delta
                        if (travel >= collapseThreshold) { actionsCollapsed = false; travel = 0f }
                    }
                }
                return Offset.Zero
            }
        }
    }
    val sectionLabels = listOf(tr("Buttons", "Botões"), tr("Sticks", "Analógicos"), tr("Triggers", "Gatilhos"), tr("Macros", "Macros"), tr("Turbo", "Turbo"), tr("Vibration", "Vibração"))
    DisposableEffect(Unit) { onDispose { model.cancelX10VibrationPreview() } }
    LaunchedEffect(section) { profileScroll.scrollTo(0); actionsCollapsed = false }
    var savedVisible by rememberSaveable { mutableStateOf(false) }
    var editingName by rememberSaveable { mutableStateOf(false) }
    var menuVisible by remember { mutableStateOf(false) }
    var rear by rememberSaveable { mutableStateOf(false) }
    var showAllButtons by rememberSaveable { mutableStateOf(false) }
    var keyPicker by remember { mutableStateOf<PadKey?>(null) }
    var applySnapshot by remember { mutableStateOf<ControllerConfig?>(null) }
    var readDialog by remember { mutableStateOf(false) }
    var loadDialog by remember { mutableStateOf<SavedProfile?>(null) }
    var deleteDialog by remember { mutableStateOf<String?>(null) }
    var rearMapOverMacro by remember { mutableStateOf<RearRemapOverMacroPending?>(null) }
    var pendingMacroReplace by remember { mutableStateOf<RearMacroReplacePending?>(null) }
    val editorConfirmGeneration = remember(draft, savedProfileId) { model.captureEditorConfirmBinding() }
    LaunchedEffect(editorConfirmGeneration, pendingMacroReplace) {
        pendingMacroReplace?.let { pending ->
            val matches = model.editorConfirmBindingMatches(pending.binding)
            if (!matches || rearMacroReplaceDialogConflict(draft, pending, matches) == null) {
                pendingMacroReplace = null
            }
        }
    }
    LaunchedEffect(editorConfirmGeneration, rearMapOverMacro) {
        rearMapOverMacro?.let { pending ->
            val matches = model.editorConfirmBindingMatches(pending.binding)
            if (!matches || !rearRemapOverMacroDialogStillOpen(draft, pending, matches)) {
                rearMapOverMacro = null
            }
        }
    }
    var exportSnapshot by remember { mutableStateOf<ControllerConfig?>(null) }
    val validationIssues = remember(draft) { draft.profileValidationIssues() }
    val draftValid = validationIssues.isEmpty()
    val editorUi = profilesEditorUiState(link.phase, hardware.busy, hardware.calibration, draftValid)
    val canSaveCurrent = editorUi.canSaveProfile && (savedProfileId != null || canSaveNewCopy)
    val saveCurrent: () -> Unit = { if (savedProfileId != null) model.updateSavedCopy() else model.saveAsNewCopy() }
    val draftStatusLabel = when (draftStatus) {
        DraftStatusKind.UNSAVED_CHANGES -> tr("Unsaved changes", "Alterações não salvas")
        DraftStatusKind.NEW -> tr("New draft", "Rascunho novo")
        DraftStatusKind.EDITING_SAVED -> tr("Saved copy", "Cópia salva")
        DraftStatusKind.CLEAN -> tr("Ready to edit", "Pronto para editar")
    }
    val moreActionsLabel = tr("More profile actions", "Mais ações do perfil")
    val savedProfilesLabel = ConsumerCopy.savedProfileQuota(prefs.profiles.size, portuguese = LocalPortuguese.current)
    val editNameLabel = tr("Edit profile name: ", "Editar nome do perfil: ") + draft.name
    BackHandler(savedVisible) { savedVisible = false }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val snapshot = exportSnapshot
        if (uri != null && snapshot != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                requireNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(ProfileStore.export(snapshot)) }
            } }.onSuccess { model.repository.notice("Profile exported", "Perfil exportado", survivesDisconnect = true) }
                .onFailure { model.repository.notice("Could not export profile", "Não foi possível exportar o perfil", true) }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val origin: EditorSaveBinding = model.captureEditorBinding()
            runCatching { withContext(Dispatchers.IO) {
                requireNotNull(context.contentResolver.openInputStream(uri)).bufferedReader().use { reader ->
                    val buffer = CharArray(100001); var count = 0
                    while (count < buffer.size) { val read = reader.read(buffer, count, buffer.size - count); if (read < 0) break; count += read }
                    require(count <= 100000); String(buffer, 0, count)
                }
            } }.onSuccess { json -> model.importProfile(json, origin) }
                .onFailure { model.repository.notice("Could not read this profile file", "Não foi possível ler este arquivo de perfil", true) }
        }
    }
    if (savedVisible) {
        SavedProfilesScreen(prefs.profiles, savedProfileId, { savedVisible = false },
            onLoad = { loadDialog = it }, onDelete = { deleteDialog = it.id })
    } else {
        Column(Modifier.fillMaxSize().imePadding()) {
            if (DemoController.isDevice(selected)) {
                SimulatedControllerBanner()
            }
            Row(Modifier.fillMaxWidth().padding(start = ScreenLayout.horizontalPadding, end = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { editingName = !editingName }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                    contentDescription = editNameLabel
                    stateDescription = draftStatusLabel
                },
                    contentPadding = PaddingValues(horizontal = 0.dp)) {
                    HubIcon(Glyph.EDIT, Modifier.size(17.dp), MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(draft.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(draftStatusLabel, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                TextButton(onClick = { savedVisible = true }, modifier = Modifier.heightIn(min = 48.dp).semantics {
                    contentDescription = savedProfilesLabel
                },
                    contentPadding = PaddingValues(horizontal = 8.dp)) {
                    HubIcon(Glyph.PROFILES, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(if (largeText) 4.dp else 6.dp))
                    Text((if (largeText) "" else tr("Saved ", "Salvos ")) + prefs.profiles.size, maxLines = 1)
                }
                Box {
                    IconButton(onClick = { menuVisible = true }, modifier = Modifier.semantics {
                        contentDescription = moreActionsLabel
                    }) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
                    DropdownMenu(expanded = menuVisible, onDismissRequest = { menuVisible = false }) {
                        DropdownMenuItem(text = { Text(if (savedProfileId != null) ConsumerCopy.updateSavedCopyAction(LocalPortuguese.current)
                            else ConsumerCopy.saveProfileAction(LocalPortuguese.current)) },
                            enabled = canSaveCurrent, onClick = { menuVisible = false; saveCurrent() })
                        DropdownMenuItem(text = { Text(ConsumerCopy.applyToX10Action(LocalPortuguese.current)) },
                            enabled = editorUi.canApplyToDevice, onClick = { menuVisible = false; applySnapshot = draft })
                        HorizontalDivider()
                        if (savedProfileId != null) DropdownMenuItem(
                            text = { Text(tr("Save as new", "Salvar como novo")) },
                            enabled = editorUi.canSaveProfile && canSaveNewCopy,
                            onClick = { menuVisible = false; model.saveAsNewCopy() },
                        )
                        DropdownMenuItem(text = { Text(ConsumerCopy.readFromX10Action(LocalPortuguese.current)) },
                            enabled = editorUi.canReadFromDevice, onClick = { menuVisible = false; readDialog = true })
                        DropdownMenuItem(text = { Text(tr("Import profile from a file", "Importar perfil de um arquivo")) },
                            onClick = { menuVisible = false; import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
                        DropdownMenuItem(text = { Text(tr("Export profile", "Exportar perfil")) },
                            enabled = editorUi.canSaveProfile,
                            onClick = { menuVisible = false; exportSnapshot = draft; export.launch("easygamepad-profile.json") })
                    }
                }
            }
            if (editingName) OutlinedTextField(draft.name, { name -> model.edit { it.copy(name = name.take(40)) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenLayout.horizontalPadding),
                label = { Text(tr("Profile name", "Nome do perfil")) }, singleLine = true,
                isError = validationIssues.any { it.field == ProfileField.NAME },
                supportingText = validationIssues.firstOrNull { it.field == ProfileField.NAME }?.localizedMessage()?.let { { Text(it) } })
            if (hardware.busy) LinearProgressIndicator(progress = { hardware.progress }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().padding(horizontal = ScreenLayout.horizontalPadding),
                verticalAlignment = Alignment.CenterVertically) {
                ProfileEditorSectionNav(sectionLabels, section, { model.profileSection.value = it }, Modifier.weight(1f))
                if (link.phase != LinkPhase.READY) {
                    val connectLabel = tr("Connect to apply", "Conectar para aplicar")
                    TextButton(onClick = model::connectSelected, modifier = Modifier.heightIn(min = 48.dp).semantics {
                        contentDescription = connectLabel
                    }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text(tr("Connect", "Conectar"))
                    }
                }
            }
            Box(Modifier.weight(1f).nestedScroll(editorScrollConnection)) {
            ScreenColumn(scrollState = profileScroll) {
                ProfileValidationBanner(if (editingName) validationIssues.filter { it.field != ProfileField.NAME } else validationIssues)
                hardware.settingsReadAt?.let { Description(tr("Last read from controller: ", "Última leitura do controle: ") + timeLabel(it)) }
        when (section) {
            0 -> {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    ProfileHelp(tr("How button mapping works", "Como funcionam os botões"),
                        tr("Tap a button to choose its function. Highlights show your custom assignments.",
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
                                val outputKind = draft.buttonsListOutputKind(source)
                                Surface(onClick = { keyPicker = source }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(source.label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            HubIcon(Glyph.EDIT, Modifier.size(13.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            HubIcon(Glyph.ARROW, Modifier.size(14.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                when (outputKind) {
                                                    ButtonsListOutputKind.Disabled -> tr("Disabled", "Desativado")
                                                    ButtonsListOutputKind.Mapping -> requireNotNull(target).label
                                                    ButtonsListOutputKind.Macro -> tr("Macro", "Macro")
                                                    ButtonsListOutputKind.NotSent -> if (source in DeviceProfile.X10.rearButtons) {
                                                        tr("Unchanged", "Inalterado")
                                                    } else {
                                                        tr("Not sent", "Não enviado")
                                                    }
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = when (outputKind) {
                                                    ButtonsListOutputKind.Disabled -> MaterialTheme.colorScheme.error
                                                    ButtonsListOutputKind.Mapping ->
                                                        if (target != source) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                                    ButtonsListOutputKind.Macro -> MaterialTheme.colorScheme.primary
                                                    ButtonsListOutputKind.NotSent -> MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }
                TextButton(onClick = { model.edit { it.copy(mappings = it.mappings + PadKey.outputs.associateWith { key -> key }) } }) { Text(tr("Restore default button functions", "Restaurar funções originais dos botões")) }
                ProfileHelp(tr("About missing assignments", "Sobre atribuições ausentes"),
                    tr(
                        "Face buttons you do not change here are not sent and do not erase the controller. Rear paddles (M1/M2) shown as unchanged are not sent either—the controller keeps its current setup. Disabled paddles are sent so the paddle does nothing.",
                        "Botões da frente que você não altera aqui não são enviados e não apagam o controle. Paddles traseiros (M1/M2) marcados como inalterados também não são enviados — o controle mantém a configuração atual. Paddles desativados são enviados para o botão não fazer nada.",
                    ))
            }
            1 -> {
                StickEditor(tr("Left stick", "Analógico esquerdo"), draft.leftStick) { value -> model.edit { it.copy(leftStick = value) } }
                StickEditor(tr("Right stick", "Analógico direito"), draft.rightStick) { value -> model.edit { it.copy(rightStick = value) } }
                ProfileHelp(tr("About stick response", "Sobre a resposta dos analógicos"),
                    tr("Reduce unwanted movement near the center or choose how far you move the stick to reach full speed.",
                        "Reduza movimentos involuntários perto do centro ou escolha quanto mover o analógico para atingir a velocidade máxima."))
            }
            2 -> {
                HubCard(Modifier.fillMaxWidth()) { Text("LT", style = MaterialTheme.typography.titleLarge); RangeEditor(draft.leftTrigger.min, draft.leftTrigger.max) { min, max -> model.edit { it.copy(leftTrigger = TriggerSettings(min, max)) } } }
                HubCard(Modifier.fillMaxWidth()) { Text("RT", style = MaterialTheme.typography.titleLarge); RangeEditor(draft.rightTrigger.min, draft.rightTrigger.max) { min, max -> model.edit { it.copy(rightTrigger = TriggerSettings(min, max)) } } }
                ProfileHelp(tr("About trigger response", "Sobre a resposta dos gatilhos"),
                    tr("Choose where each trigger begins responding and reaches full output.",
                        "Escolha onde cada gatilho começa a responder e atinge o valor máximo."))
            }
            3 -> MacroEditor(draft.macros) { value ->
                if (draft.rearPaddlesNeedingMacroConfirm(value).isEmpty()) {
                    model.edit { it.withRearMacros(value) }
                } else {
                    pendingMacroReplace = RearMacroReplacePending(model.captureEditorConfirmBinding(), value)
                }
            }
            4 -> TurboEditor(draft.turbos) { value -> model.edit { it.copy(turbos = value) } }
            else -> HubCard(Modifier.fillMaxWidth()) {
                Text(tr("Feel every moment.", "Sinta cada momento."), style = MaterialTheme.typography.titleLarge)
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
                ProfileHelp(tr("About motor intensity", "Sobre a intensidade dos motores"),
                    tr("Set the intensity of each controller motor. Availability depends on the controller's operating mode.",
                        "Ajuste a intensidade de cada motor do controle. A disponibilidade depende do modo de operação."))
                ProfileHelp(tr("Previewing vibration", "Prévia da vibração"),
                    tr("While the X10 configuration channel is ready, hold and drag a slider to feel that motor at the chosen intensity. Release the slider to stop. Nothing is saved until you apply the profile. If vibration continues after release, turn the controller off.",
                        "Com o canal de configuração do X10 pronto, mantenha e arraste um controle para sentir aquele motor na intensidade escolhida. Solte para parar. Nada é gravado até você aplicar o perfil. Se continuar vibrando após soltar, desligue o controle."))
                if (!canTestX10Vibration) Description(tr("Connect the X10 configuration channel and finish any active operation to preview motor intensity on the controller.",
                    "Conecte o canal de configuração do X10 e conclua qualquer operação em andamento para pré-visualizar a intensidade dos motores no controle."))
                OutlinedButton(onClick = model::rumble, enabled = vibrationTestAvailability == GamepadVibrationAvailability.AVAILABLE) {
                    Text(tr("Test Android gamepad vibration", "Testar vibração do controle no Android"))
                }
                ProfileHelp(tr("About the Android vibration test", "Sobre o teste de vibração no Android"), when (vibrationTestAvailability) {
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
        hardware.applyResult?.let { result ->
            X10ApplyOutcomeCard(
                hardware.lastAppliedSnapshot, result, hardware.appliedAt, hardware.settingsReadAt,
                onReadFromDevice = { readDialog = true },
            )
        }
            }
            }
            AnimatedVisibility(
                visible = !imeVisible && !actionsCollapsed,
                enter = if (reducedMotion) EnterTransition.None else expandVertically(),
                exit = if (reducedMotion) ExitTransition.None else shrinkVertically(),
            ) { ProfileEditorBottomBar(
                editingSavedCopy = savedProfileId != null,
                canSave = canSaveCurrent,
                canApply = editorUi.canApplyToDevice,
                saveDisabledReason = when {
                    !editorUi.canSaveProfile -> AccessibilitySemantics.profilesSaveDisabledReason(editorUi, LocalPortuguese.current)
                    savedProfileId == null && !canSaveNewCopy -> tr("Delete a saved profile to make room.", "Exclua um perfil salvo para liberar espaço.")
                    else -> null
                },
                applyDisabledReason = AccessibilitySemantics.profilesApplyDisabledReason(editorUi, LocalPortuguese.current),
                onSave = saveCurrent,
                onApply = { applySnapshot = draft },
            ) }
        }
    }
    keyPicker?.let { source -> KeyPicker(tr("Output for ", "Função para ") + source.label, PadKey.outputs, onDismiss = { keyPicker = null },
        clearLabel = if (source in PadKey.outputs) tr("Restore standard output", "Restaurar função padrão") else tr("Disable paddle", "Desativar paddle"),
        onClear = {
            model.edit {
                if (source in PadKey.outputs) it.copy(mappings = it.mappings + (source to source))
                else it.withRearPaddleDisabled(source)
            }
            keyPicker = null
        },
    ) { target ->
        if (source in DeviceProfile.X10.rearButtons && draft.rearHasMacro(source)) {
            rearMapOverMacro = RearRemapOverMacroPending(model.captureEditorConfirmBinding(), source, target)
        } else if (source in DeviceProfile.X10.rearButtons) {
            model.edit { it.withRearRemap(source, target) }
            keyPicker = null
        } else {
            model.edit { it.copy(mappings = it.mappings + (source to target)) }
            keyPicker = null
        }
    } }
    rearMapOverMacro?.let { pending ->
        if (rearRemapOverMacroDialogStillOpen(draft, pending, model.editorConfirmBindingMatches(pending.binding))) {
            val source = pending.source
            val target = pending.target
            ConfirmDialog(
                tr("Replace macro?", "Substituir macro?"),
                tr(
                    "${source.label} has a macro in this profile. Mapping it to ${target.label} removes that macro.",
                    "${source.label} tem uma macro neste perfil. Mapeá-lo para ${target.label} remove essa macro.",
                ),
                tr("Use mapping", "Usar mapeamento"),
                onDismiss = { rearMapOverMacro = null },
                onConfirm = {
                    if (rearRemapOverMacroDialogStillOpen(draft, pending, model.editorConfirmBindingMatches(pending.binding))) {
                        model.edit { it.withRearRemap(pending.source, pending.target) }
                        keyPicker = null
                    }
                    rearMapOverMacro = null
                },
            )
        }
    }
    pendingMacroReplace?.let { pending ->
        val conflict = rearMacroReplaceDialogConflict(draft, pending, model.editorConfirmBindingMatches(pending.binding))
        if (conflict != null) {
            val (paddle, mappedTo) = conflict
            ConfirmDialog(
                tr("Replace button mapping?", "Substituir mapeamento de botão?"),
                tr(
                    "${paddle.label} is mapped to ${mappedTo.label}. Saving a macro on this paddle removes that mapping.",
                    "${paddle.label} está mapeado para ${mappedTo.label}. Salvar uma macro neste paddle remove esse mapeamento.",
                ),
                tr("Use macro", "Usar macro"),
                onDismiss = { pendingMacroReplace = null },
                onConfirm = {
                    val live = pendingMacroReplace
                    if (live != null &&
                        rearMacroReplaceDialogConflict(draft, live, model.editorConfirmBindingMatches(live.binding)) != null
                    ) {
                        model.edit { it.withRearMacros(live.nextMacros) }
                    }
                    pendingMacroReplace = null
                },
            )
        }
    }
    if (readDialog) ConfirmDialog(tr("Read current settings?", "Ler configurações atuais?"),
        tr("Replace the profile you are editing with the settings on your X10? Save first if you want to keep your edits. The profile name is kept; rear paddles come from onboard macro slots.",
            "Substituir o perfil em edição pelos ajustes do seu X10? Salve antes se quiser guardar suas alterações. O nome do perfil é mantido; paddles traseiros vêm dos slots de macro do controle."),
        ConsumerCopy.readFromX10Action(LocalPortuguese.current), { readDialog = false }) { readDialog = false; model.readController() }
    applySnapshot?.let { snapshot ->
        X10ApplyDialog(snapshot, editorUi.configurationReady,
            dismiss = { applySnapshot = null }, apply = { applySnapshot = null; model.apply(snapshot) })
    }
    loadDialog?.let { saved ->
        ConfirmDialog(tr("Load profile?", "Carregar perfil?"), tr("This replaces the profile you are editing. Save first if you want to keep your changes.", "Isso substitui o perfil em edição. Salve antes se quiser guardar suas alterações."), tr("Load", "Carregar"), { loadDialog = null }) {
            model.loadSaved(saved); loadDialog = null; savedVisible = false
        }
    }
    deleteDialog?.let { id -> ConfirmDialog(tr("Delete saved profile?", "Excluir perfil salvo?"), tr("The saved copy will be removed from this phone.", "A cópia salva será removida deste celular."), tr("Delete", "Excluir"), { deleteDialog = null }) { model.delete(id); deleteDialog = null } }
}

@Composable fun ConfirmDialog(title: String, body: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(action) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "Cancelar")) } })
}

@Composable fun ProfileHelp(title: String, body: String) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val expandedLabel = tr("Expanded", "Expandido")
    val collapsedLabel = tr("Collapsed", "Recolhido")
    Column {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = 48.dp).semantics {
            stateDescription = if (expanded) expandedLabel else collapsedLabel
        },
            contentPadding = PaddingValues(horizontal = 4.dp)) {
            HubIcon(Glyph.INFO, Modifier.size(17.dp), MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(title)
        }
        if (expanded) Description(body)
    }
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
        val portuguese = LocalPortuguese.current
        Slider(
            value.toFloat(),
            onValueChange = { sliderChange(it.toInt().coerceIn(range)) },
            onValueChangeFinished = { onChangeFinished?.invoke() },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            modifier = Modifier.semantics {
                contentDescription = AccessibilitySemantics.vibrationSliderLabel(label, value, suffix, previewing, portuguese)
            },
        )
    }
}
@Composable private fun VibrationPreviewDot(active: Boolean) {
    if (!active) return
    val color = MaterialTheme.colorScheme.primary
    val label = tr("Vibration preview on", "Pré-visualização de vibração ativa")
    if (rememberReducedMotion()) {
        Box(
            Modifier
                .padding(end = 8.dp)
                .size(10.dp)
                .semantics { contentDescription = label }
                .background(color, CircleShape),
        )
        return
    }
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
            .semantics { contentDescription = label }
            .background(color.copy(alpha = alpha), CircleShape),
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
