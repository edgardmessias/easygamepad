// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
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
    val switching by model.switching.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
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
    val editorOpen by model.profileEditorOpen.collectAsStateWithLifecycle()
    var editingName by rememberSaveable { mutableStateOf(false) }
    var menuVisible by remember { mutableStateOf(false) }
    var rear by rememberSaveable { mutableStateOf(false) }
    var keyPicker by remember { mutableStateOf<PadKey?>(null) }
    var applySnapshot by remember { mutableStateOf<ControllerConfig?>(null) }
    var applySavedDialog by remember { mutableStateOf<SavedProfile?>(null) }
    var readDialog by remember { mutableStateOf(false) }
    var loadDialog by remember { mutableStateOf<SavedProfile?>(null) }
    var deleteDialog by remember { mutableStateOf<SavedProfile?>(null) }
    var importDialog by remember { mutableStateOf(false) }
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
    val editorUi = profilesEditorUiState(
        selected, link.phase, link.address, link.protocol, switching, hardware.busy, hardware.calibration, draftValid,
    )
    val canSaveCurrent = editorUi.canSaveProfile && (savedProfileId != null || canSaveNewCopy)
    val saveCurrent: () -> Unit = { if (savedProfileId != null) model.updateSavedCopy() else model.saveAsNewCopy() }
    val draftStatusLabel = when (draftStatus) {
        DraftStatusKind.UNSAVED_CHANGES -> tr("Unsaved changes", "Alterações não salvas")
        DraftStatusKind.NEW -> tr("New draft", "Rascunho novo")
        DraftStatusKind.EDITING_SAVED -> tr("Saved copy", "Cópia salva")
        DraftStatusKind.CLEAN -> tr("Ready to edit", "Pronto para editar")
    }
    val moreActionsLabel = tr("More profile actions", "Mais ações do perfil")
    val editNameLabel = tr("Edit profile name: ", "Editar nome do perfil: ") + draft.name
    val listBackLabel = tr("Back to saved profiles", "Voltar aos perfis salvos")
    BackHandler(editorOpen) { model.closeProfilesEditor() }
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
    val portuguese = LocalPortuguese.current
    val profilesListChannelStatus = profilesListConfigurationChannelStatus(editorUi, link.error, portuguese)
    val profilesListChannelVisible = profilesListConfigurationRowVisible(
        supportsConfiguration = selected?.supportsConfiguration == true,
        editorUi = editorUi,
        linkError = link.error,
    )
    if (!editorOpen) {
        Column(Modifier.fillMaxSize()) {
            if (DemoController.isDevice(selected)) SimulatedControllerBanner()
            ProfilesListScreen(
                profiles = prefs.profiles,
                editingId = savedProfileId,
                draftName = draft.name,
                draftStatusLabel = draftStatusLabel,
                showDraftRow = savedProfileId == null,
                canReadFromDevice = editorUi.canReadFromDevice,
                readDisabledReason = AccessibilitySemantics.profilesReadDisabledReason(editorUi, portuguese),
                configurationChannelStatus = profilesListChannelStatus,
                configurationConnectEnabled = profilesListConnectEnabled(editorUi, switching),
                showConfigurationChannelRow = profilesListChannelVisible,
                onConnectConfiguration = model::connectSelected,
                onEditDraft = model::openProfilesEditor,
                onEditSaved = { model.openProfilesEditor() },
                onLoad = { profile ->
                    if (draftStatus == DraftStatusKind.UNSAVED_CHANGES || draftStatus == DraftStatusKind.NEW) {
                        loadDialog = profile
                    } else {
                        model.loadSaved(profile)
                    }
                },
                onApply = { applySavedDialog = it },
                onDelete = { deleteDialog = it },
                onExport = { profile ->
                    exportSnapshot = profile.config
                    export.launch("${profile.config.name.ifBlank { "profile" }}.json")
                },
                onReadFromController = { readDialog = true },
                onImport = { importDialog = true },
            )
        }
    } else {
        Column(Modifier.fillMaxSize().imePadding()) {
            if (DemoController.isDevice(selected)) {
                SimulatedControllerBanner()
            }
            Row(Modifier.fillMaxWidth().padding(start = ScreenLayout.horizontalPadding, end = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = model::closeProfilesEditor, modifier = Modifier.semantics { contentDescription = listBackLabel }) {
                    HubIcon(Glyph.ARROW, Modifier.size(20.dp).rotate(180f), MaterialTheme.colorScheme.onSurface)
                }
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
                Box(Modifier.padding(end = 4.dp)) {
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
                            onClick = { menuVisible = false; importDialog = true })
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
            EditorConfigurationChannelRow(
                phase = link.phase,
                linkError = link.error,
                switching = switching,
                operationBusy = hardware.busy,
                onConnect = model::connectSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenLayout.horizontalPadding),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = ScreenLayout.horizontalPadding),
                verticalAlignment = Alignment.CenterVertically) {
                ProfileEditorSectionNav(sectionLabels, section, { model.profileSection.value = it }, Modifier.fillMaxWidth())
            }
            Box(Modifier.weight(1f).nestedScroll(editorScrollConnection)) {
            ScreenColumn(scrollState = profileScroll) {
                ProfileValidationBanner(if (editingName) validationIssues.filter { it.field != ProfileField.NAME } else validationIssues)
                hardware.settingsReadAt?.let { Description(tr("Last read from controller: ", "Última leitura do controle: ") + timeLabel(it)) }
        when (section) {
            0 -> {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(!rear, { rear = false }, label = { Text(tr("Front", "Frente")) })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(rear, { rear = true }, label = { Text(tr("Back", "Traseira")) })
                    }
                    ControllerStage(
                        Modifier.fillMaxWidth().then(profileEditorControllerStageModifier(shortHeight = true)),
                        artwork = ControllerArtworkCatalog.X10,
                        rear = rear,
                        mappings = draft.mappings,
                        onPick = { keyPicker = it },
                    )
                    Text(
                        tr("Tap a button to edit", "Toque em um botão para editar"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(
                    onClick = { model.edit { it.copy(mappings = it.mappings + PadKey.outputs.associateWith { key -> key }) } },
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    Text(tr("Restore default button functions", "Restaurar funções originais dos botões"))
                }
                ProfileButtonMappingList(draft, onPick = { keyPicker = it })
                ProfileHelp(tr("How button mapping works", "Como funcionam os botões"),
                    tr(
                        "Tap the diagram or a row to map a button. Scroll to reach every control, including shoulders. Restoring defaults affects front buttons only.",
                        "Toque no diagrama ou em uma linha para mapear. Role para alcançar todos os controles, inclusive ombros. Restaurar padrões afeta só a face frontal.",
                    ))
            }
            1 -> {
                SticksPairEditor(draft.leftStick, draft.rightStick,
                    onLeft = { value -> model.edit { it.copy(leftStick = value) } },
                    onRight = { value -> model.edit { it.copy(rightStick = value) } },
                )
                ProfileHelp(tr("About stick response", "Sobre a resposta dos analógicos"),
                    tr("Reduce unwanted movement near the center or choose how far you move the stick to reach full speed.",
                        "Reduza movimentos involuntários perto do centro ou escolha quanto mover o analógico para atingir a velocidade máxima."))
            }
            2 -> {
                TriggersPairEditor(draft.leftTrigger, draft.rightTrigger,
                    onLeft = { value -> model.edit { it.copy(leftTrigger = value) } },
                    onRight = { value -> model.edit { it.copy(rightTrigger = value) } },
                )
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
            else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HubCard(Modifier.fillMaxWidth()) {
                    Text(tr("Motor intensity", "Intensidade dos motores"), style = MaterialTheme.typography.titleMedium)
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
                    if (!canTestX10Vibration) {
                        Text(
                            tr("X10 motor preview needs the configuration channel ready.", "Prévia dos motores X10 exige o canal de configuração pronto."),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedButton(
                    onClick = model::rumble,
                    enabled = vibrationTestAvailability == GamepadVibrationAvailability.AVAILABLE,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(tr("Test Android gamepad vibration", "Testar vibração do controle no Android"))
                }
                ProfileHelp(tr("Vibration help", "Ajuda sobre vibração"),
                    tr(
                        "Slide to set each motor; with the configuration channel ready, hold to preview on the controller. The Android test uses the gamepad input channel only—it does not follow draft intensities.",
                        "Ajuste cada motor; com o canal de configuração pronto, segure para pré-visualizar no controle. O teste Android usa só a entrada de jogo—não segue as intensidades do rascunho.",
                    ))
            }
        }
        hardware.applyResult?.let { result ->
            X10ApplyOutcomeCard(
                hardware.lastAppliedSnapshot, result, hardware.appliedAt, hardware.settingsReadAt,
                onReadFromDevice = { readDialog = true },
            )
        }
                Spacer(Modifier.height(profileEditorFooterClearance))
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
    if (readDialog) {
        val readBody = if (DemoController.isDevice(selected)) {
            tr(
                "Replace the profile you are editing with the simulator’s current settings? Save first if you want to keep your edits. Nothing is read from a physical controller.",
                "Substituir o perfil em edição pelos ajustes atuais do simulador? Salve antes se quiser guardar suas alterações. Nada é lido de um controle físico.",
            )
        } else {
            tr(
                "Replace the profile you are editing with the settings on your X10? Save first if you want to keep your edits. The profile name is kept; rear paddles come from onboard macro slots.",
                "Substituir o perfil em edição pelos ajustes do seu X10? Salve antes se quiser guardar suas alterações. O nome do perfil é mantido; paddles traseiros vêm dos slots de macro do controle.",
            )
        }
        ConfirmDialog(tr("Read current settings?", "Ler configurações atuais?"), readBody,
            ConsumerCopy.readFromX10Action(LocalPortuguese.current), { readDialog = false }) {
            readDialog = false; model.readController()
        }
    }
    if (importDialog) {
        ConfirmDialog(
            tr("Import profile?", "Importar perfil?"),
            tr(
                "This replaces the profile you are editing. Save first if you want to keep your changes.",
                "Isso substitui o perfil em edição. Salve antes se quiser guardar suas alterações.",
            ),
            tr("Import", "Importar"),
            onDismiss = { importDialog = false },
            onConfirm = {
                importDialog = false
                import.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            },
        )
    }
    applySnapshot?.let { snapshot ->
        val simulated = DemoController.isDevice(selected)
        X10ApplyDialog(snapshot, editorUi.canApplyToDevice, simulated,
            dismiss = { applySnapshot = null }, apply = { applySnapshot = null; model.apply(snapshot) })
    }
    applySavedDialog?.let { saved ->
        val simulated = DemoController.isDevice(selected)
        X10ApplyDialog(savedProfileApplySnapshot(saved), editorUi.configurationReady, simulated,
            dismiss = { applySavedDialog = null },
            apply = { applySavedDialog = null; model.applySavedProfile(saved) },
        )
    }
    loadDialog?.let { saved ->
        ConfirmDialog(tr("Load profile?", "Carregar perfil?"), tr("This replaces the profile you are editing. Save first if you want to keep your changes.", "Isso substitui o perfil em edição. Salve antes se quiser guardar suas alterações."), tr("Load", "Carregar"), { loadDialog = null }) {
            model.loadSaved(saved); loadDialog = null
        }
    }
    deleteDialog?.let { saved ->
        ConfirmDialog(
            tr("Delete saved profile?", "Excluir perfil salvo?"),
            tr(
                "The saved copy \"${saved.config.name}\" will be removed from this phone.",
                "A cópia salva \"${saved.config.name}\" será removida deste celular.",
            ),
            tr("Delete", "Excluir"),
            { deleteDialog = null },
        ) { model.delete(saved.id); deleteDialog = null }
    }
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

@Composable fun ToggleRow(title: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); Switch(checked, change) }
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
