// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad

import android.app.Application
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.dev.edgard.easygamepad.data.*
import br.dev.edgard.easygamepad.ble.LinkPhase
import br.dev.edgard.easygamepad.input.GamepadVibrationAvailability
import br.dev.edgard.easygamepad.input.vibrationAvailability
import br.dev.edgard.easygamepad.protocol.*
import br.dev.edgard.easygamepad.protocol.keylinker.KeyLinkerFeature
import br.dev.edgard.easygamepad.protocol.keylinker.KeyLinkerSerials
import br.dev.edgard.easygamepad.protocol.keylinker.KeyLinkerWritePlan
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import java.util.UUID

@OptIn(FlowPreview::class)
class HubViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as HubApplication
    val repository = app.repository
    val ble = app.ble
    val preferences = app.store.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, HubPreferences())
    private val selectedId = MutableStateFlow<String?>(null)
    private val setupInput = MutableStateFlow<String?>(null)
    private val setupGatt = MutableStateFlow<GattProfile?>(null)
    /** Model chosen when adding a controller; decides the protocol, artwork and GATT profile. */
    val setupModel = MutableStateFlow(DeviceProfile.X10)
    private val runtimeLinks = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val sessionNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val deviceSheet = MutableStateFlow(false)
    val switching = MutableStateFlow(false)
    private val drafts = ControllerDrafts()
    private var selectionRevision = 0L
    private var draftEpoch = 0L
    private var editorGeneration = 0L
    private val draftBaselineRevision = MutableStateFlow(0L)
    val devices = combine(preferences, inputPads(), runtimeLinks, sessionNames) { prefs, pads, links, names ->
        controllerDevices(prefs.controllers, pads, links, prefs.inputNames, names)
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val selectedDevice = combine(devices, selectedId, setupInput, setupGatt, setupModel) { rows, id, descriptor, gatt, model ->
        rows.firstOrNull { it.id == id } ?: if (id == SETUP_ID) ControllerDevice(SETUP_ID, model.name, model.id,
            inputDescriptor = descriptor, inputId = app.input.state.value.pads.firstOrNull { it.descriptor == descriptor }?.id, gatt = gatt ?: model.gatt) else null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private fun inputPads() = app.input.state.map { it.pads }.distinctUntilChanged()
    private val mutableDraft = MutableStateFlow(ControllerConfig())
    val draft = mutableDraft.asStateFlow()
    private val mutableSavedProfileId = MutableStateFlow<String?>(null)
    /** When set, the draft was loaded from this on-phone profile and can be updated in place. */
    val savedProfileId = mutableSavedProfileId.asStateFlow()
    val draftDirty = combine(selectedId, draft, savedProfileId, draftBaselineRevision) { id, config, _, _ ->
        drafts.isDirty(id ?: LOCAL_EDITOR_WORKSPACE_KEY, config)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val draftStatus = combine(draftDirty, savedProfileId) { dirty, linked ->
        draftStatusKind(dirty, linked)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DraftStatusKind.CLEAN)
    val tab = MutableStateFlow(0)
    val profileSection = MutableStateFlow(0)
    val connectionSheet = MutableStateFlow(false)
    val hardware = repository.state
    val keyLinker = app.keyLinker.state
    private val operationGate = ControllerOperationGate(hardware, keyLinker, switching)
    val noticePresenter = NoticePresenter()
    val canSwitchController = operationGate.canSwitch.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val input = app.input.state
    val vibrationTestAvailability = input.map { it.vibrationAvailability() }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, GamepadVibrationAvailability.NO_DEVICE)
    val link = ble.link
    val canTestX10Vibration = combine(selectedDevice, link, operationGate.canSwitch) { device, connection, idle ->
        device?.isX10 == true && device.address != null && device.address == connection.address &&
            connection.phase == LinkPhase.READY && connection.protocol == ConfigurationProtocol.KRLY && idle
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private var connectingGatt: GattProfile? = null
    private var connectingModel: String? = null
    private var reconnectJob: Job? = null
    private var disconnectJob: Job? = null
    private var backgroundDisconnectJob: Job? = null
    private var manuallyDisconnected = false
    private var isForeground = false
    private val vibrationPreviewRequests = Channel<VibrationPreviewRequest>(Channel.CONFLATED)
    private val vibrationPreviewActivity = MutableStateFlow(X10VibrationPreviewActivity())
    val x10VibrationPreview = vibrationPreviewActivity.asStateFlow()

    init {
        viewModelScope.launch { ble.link.collect { noticePresenter.onLinkPhase(it.phase) } }
        viewModelScope.launch {
            val prefs = app.store.preferences.first()
            drafts.restore(prefs.workspaceDrafts)
            selectedId.value = prefs.controllers.firstOrNull { it.address == prefs.address }?.let { "ble:${it.address}" }
                ?: controllerDevices(prefs.controllers, app.input.state.value.pads).firstOrNull()?.id
            val workspaceKey = selectedId.value ?: LOCAL_EDITOR_WORKSPACE_KEY
            prefs.workspaceDrafts[workspaceKey]?.let { snap ->
                mutableDraft.value = snap.config
                mutableSavedProfileId.value = snap.linkedSavedProfileId
            }
        }
        viewModelScope.launch {
            combine(selectedId, draft, savedProfileId, draftBaselineRevision) { id, config, linked, _ ->
                Triple(id ?: LOCAL_EDITOR_WORKSPACE_KEY, config, linked)
            }
                .debounce(600)
                .collect { (workspace, config, linked) ->
                    drafts.remember(workspace, config, linked)
                    runCatching { app.store.persistWorkspaceDrafts(drafts.export()) }
                        .onFailure {
                            repository.notice(
                                "Could not autosave draft workspaces on this phone. Export your profile if needed.",
                                "Não foi possível salvar rascunhos automaticamente neste celular. Exporte o perfil se necessário.",
                                error = true,
                            )
                        }
                }
        }
        viewModelScope.launch {
            devices.collect { rows -> if (selectedId.value == null && rows.isNotEmpty()) selectedId.value = rows.first().id }
        }
        viewModelScope.launch {
            inputPads().collect { pads ->
                val active = pads.map { "${it.id}:${it.descriptor}" }.toSet()
                sessionNames.update { names -> names.filterKeys { it in active } }
            }
        }
        viewModelScope.launch { selectedDevice.collect { app.input.select(it?.inputId) } }
        viewModelScope.launch {
            ble.link.collect { state ->
                if (state.phase == LinkPhase.READY && state.address != null) {
                    val revision = selectionRevision
                    val previousId = selectedId.value
                    val target = "ble:${state.address}"
                    val descriptor = setupInput.value ?: selectedDevice.value?.takeIf { it.address == state.address }?.inputDescriptor
                    app.store.device(state.address, connectingGatt ?: preferences.value.gatt, descriptor, connectingModel)
                    if (selectionRevision != revision || switching.value || manuallyDisconnected ||
                        ble.link.value.phase != LinkPhase.READY || ble.link.value.address != state.address) return@collect
                    if (previousId != target) {
                        val switched = drafts.switch(previousId, target, mutableDraft.value, savedProfileId.value, previousId == SETUP_ID)
                        mutableDraft.value = switched.config
                        mutableSavedProfileId.value = switched.linkedSavedProfileId
                    }
                    selectedId.value = target
                    setupInput.value = null
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            val manager = app.getSystemService(LocaleManager::class.java)
            val current = manager.applicationLocales.toLanguageTags()
            viewModelScope.launch {
                val language = app.store.reconcileSystemLanguage(current)
                if (current.isBlank() && language != "auto") manager.applicationLocales = LocaleList.forLanguageTags(language)
            }
        }
        viewModelScope.launch { runX10VibrationPreviewWorker() }
    }
    fun chooseDevice(device: ControllerDevice) {
        if (device.id == selectedId.value) { deviceSheet.value = false; return }
        changeSelection(device.id)
    }
    private fun changeSelection(id: String?, descriptor: String? = null) {
        if (!operationGate.canSwitchNow()) {
            repository.notice("Finish the current controller operation before switching.", "Finalize a operação atual do controle antes de trocar.", true)
            return
        }
        switching.value = true
        selectionRevision++
        viewModelScope.launch {
            try {
                disconnectJob?.join()
                repository.disconnect()
                val switched = drafts.switch(selectedId.value, id, mutableDraft.value, savedProfileId.value)
                mutableDraft.value = switched.config
                mutableSavedProfileId.value = switched.linkedSavedProfileId
                manuallyDisconnected = false
                setupInput.value = descriptor
                setupGatt.value = null
                selectedId.value = id
                deviceSheet.value = false
                connectionSheet.value = false
            } finally { switching.value = false }
        }
    }
    fun addX10(fromSelectedInput: Boolean = false) = addController(DeviceProfile.X10, fromSelectedInput)
    /** Starts setup for a model the user picked. Nothing is sent until they choose a device to connect to. */
    fun addController(model: DeviceProfile, fromSelectedInput: Boolean = false) {
        val descriptor = if (fromSelectedInput) selectedDevice.value?.inputDescriptor else null
        if (!operationGate.canSwitchNow()) {
            repository.notice("Finish the current controller operation before switching.", "Finalize a operação atual do controle antes de trocar.", true)
            return
        }
        setupModel.value = model
        changeSelection(SETUP_ID, descriptor)
        viewModelScope.launch {
            switching.first { !it }
            if (selectedId.value == SETUP_ID) connectionSheet.value = true
        }
    }
    fun linkGameplay(inputId: Int?) {
        val target = selectedDevice.value ?: return
        val address = target.address ?: return
        if (!operationGate.canSwitchNow()) return
        val pad = app.input.state.value.pads.firstOrNull { it.id == inputId }
        runtimeLinks.update { links -> (links.filterValues { it != inputId } - address) + mapOf(address to (pad?.id ?: -1)) }
        viewModelScope.launch { app.store.linkInput(address, pad?.descriptor) }
    }
    fun renameDevice(device: ControllerDevice, value: String) {
        val name = runCatching { controllerName(value) }.getOrElse { return }
        viewModelScope.launch {
            runCatching {
                if (device.address != null) app.store.rename(device.address, name)
                else {
                    val pads = app.input.state.value.pads
                    val pad = pads.firstOrNull { it.id == device.inputId && it.descriptor == device.inputDescriptor } ?: return@launch
                    if (pad.descriptor.isNotBlank() && pads.count { it.descriptor == pad.descriptor } == 1) app.store.renameInput(pad.descriptor, name)
                    else {
                        sessionNames.update { it + ("${pad.id}:${pad.descriptor}" to name) }
                        repository.notice(
                            "Name saved for this session. Android reports the same identity for these controllers.",
                            "Nome salvo para esta sessão. O Android identifica esses controles da mesma forma.",
                            survivesDisconnect = true,
                        )
                        return@launch
                    }
                }
                repository.notice("Controller renamed", "Nome do controle alterado", survivesDisconnect = true)
            }.onFailure { repository.notice("Could not save the name. Try again.", "Não foi possível salvar o nome. Tente novamente.", true) }
        }
    }
    private fun configurationTarget(): Boolean = selectedDevice.value?.let {
        it.supportsConfiguration && !switching.value && it.address != null && it.address == ble.link.value.address &&
            ble.link.value.phase == LinkPhase.READY && ble.link.value.protocol == ConfigurationProtocol.KRLY
    } == true
    private fun keyLinkerTarget(): Boolean = selectedDevice.value?.let {
        it.supportsKeyLinker && !switching.value && it.address != null && it.address == ble.link.value.address &&
            ble.link.value.phase == LinkPhase.READY && ble.link.value.protocol == ConfigurationProtocol.KEYLINKER
    } == true
    private fun captureOperationBinding(): OperationBinding? =
        OperationBinding.capture(selectionRevision, selectedId.value, ble.link.value, draftEpoch)
    private fun stillBoundToOperation(binding: OperationBinding): Boolean =
        binding.matchesResult(selectionRevision, selectedId.value, ble.link.value)
    fun edit(update: (ControllerConfig) -> ControllerConfig) {
        val workspace = editorWorkspaceKey()
        val before = mutableDraft.value
        draftEpoch++
        mutableDraft.update(update)
        if (!drafts.hasWorkspace(workspace)) {
            drafts.adoptBaseline(workspace, before, savedProfileId.value)
        }
        drafts.remember(workspace, mutableDraft.value, savedProfileId.value)
    }
    private fun editorWorkspaceKey(): String = selectedId.value ?: LOCAL_EDITOR_WORKSPACE_KEY
    fun captureEditorBinding(): EditorSaveBinding =
        EditorSaveBinding(editorGeneration, editorWorkspaceKey(), savedProfileId.value)
    fun captureEditorConfirmBinding(): EditorConfirmBinding =
        EditorConfirmBinding(captureEditorBinding(), draftEpoch)
    fun editorConfirmBindingMatches(binding: EditorConfirmBinding): Boolean =
        binding.matchesActive(
            editorGeneration,
            editorWorkspaceKey(),
            savedProfileId.value,
            draftEpoch,
        )
    private fun replaceEditorDraft(config: ControllerConfig, linkedSavedProfileId: String?) {
        editorGeneration++
        draftEpoch++
        mutableDraft.value = config
        mutableSavedProfileId.value = linkedSavedProfileId
    }
    private fun finishProfileSave(binding: EditorSaveBinding, snapshot: ControllerConfig, profileId: String, newCopy: Boolean) {
        val activeWorkspace = editorWorkspaceKey()
        val live = SaveCompletionPolicy.liveConfigForCompletion(binding, editorGeneration, activeWorkspace, draft.value)
        drafts.completeSave(binding.workspaceKey, snapshot, profileId, live)
        if (SaveCompletionPolicy.shouldLinkSavedProfile(binding, editorGeneration, activeWorkspace, savedProfileId.value, profileId, newCopy)) {
            mutableSavedProfileId.value = profileId
        }
        draftBaselineRevision.update { it + 1 }
        viewModelScope.launch {
            runCatching { app.store.persistWorkspaceDrafts(drafts.export()) }
                .onFailure {
                    repository.notice(
                        "Could not autosave draft workspaces on this phone. Export your profile if needed.",
                        "Não foi possível salvar rascunhos automaticamente neste celular. Exporte o perfil se necessário.",
                        error = true,
                    )
                }
        }
    }
    fun load(config: ControllerConfig) {
        replaceEditorDraft(config, null)
        drafts.adoptBaseline(editorWorkspaceKey(), config, null)
        tab.value = 1
    }
    fun loadSaved(profile: SavedProfile) {
        replaceEditorDraft(profile.config, profile.id)
        drafts.adoptBaseline(editorWorkspaceKey(), profile.config, profile.id)
        tab.value = 1
    }
    fun clearSavedProfileLink() {
        mutableSavedProfileId.value = null
        drafts.clearSavedLink(editorWorkspaceKey())
    }
    fun language(tag: String) { viewModelScope.launch {
        app.store.language(tag)
        if (Build.VERSION.SDK_INT >= 33) app.getSystemService(LocaleManager::class.java).applicationLocales =
            if (tag == "auto") LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    } }
    fun theme(theme: String) { viewModelScope.launch { app.store.theme(theme) } }
    fun scan() { runCatching { ble.scan() }.onFailure {
        ble.log("ERROR", it.message ?: "Scan failed")
        repository.notice("Enable Bluetooth and allow Nearby devices. Android 8–11 also needs Location enabled.", "Ative o Bluetooth e permita dispositivos próximos. Android 8–11 também exige localização ativa.", true)
    } }
    fun connect(address: String, gatt: GattProfile = selectedDevice.value?.gatt ?: preferences.value.gatt) {
        val device = selectedDevice.value
        if (device?.hasConfigurationLink != true || !operationGate.canSwitchNow()) return
        val model = device.profile ?: DeviceProfile.X10
        manuallyDisconnected = false
        selectionRevision++
        runCatching { openLink(address.uppercase(), gatt, model) }.onSuccess {
            connectionSheet.value = false
        }.onFailure {
            repository.notice("Could not connect. Check that the controller is on and Bluetooth permissions are allowed.", "Não foi possível conectar. Confira se o controle está ligado e se o app tem permissão para usar Bluetooth.", true)
        }
    }
    /** The protocol comes from the model the user picked, never from the advertised name. */
    private fun openLink(address: String, gatt: GattProfile, model: DeviceProfile) {
        val protocol = model.configurationProtocol ?: ConfigurationProtocol.KRLY
        // KeyLinker always uses its own GATT service; custom UUIDs apply to the X10 channel only.
        val profile = if (protocol == ConfigurationProtocol.KEYLINKER) model.gatt else gatt
        connectingGatt = profile; connectingModel = model.id
        ble.connect(address, profile, protocol, model.name)
    }
    /** Saves the model the controller reported, after the person confirmed it. */
    fun useIdentifiedModel() {
        val address = selectedDevice.value?.address ?: return
        val identified = keyLinker.value.identifiedModelId ?: return
        viewModelScope.launch { runCatching { app.store.model(address, identified) }
            .onSuccess { repository.notice("Saved as the identified model", "Salvo como o modelo identificado", survivesDisconnect = true) } }
    }
    fun saveGatt(gatt: GattProfile) { runCatching {
        listOf(gatt.service, gatt.write, gatt.notify).forEach { UUID.fromString(it) }
    }.onSuccess { viewModelScope.launch {
        val address = selectedDevice.value?.address
        if (address != null) app.store.device(address, gatt) else setupGatt.value = gatt
        repository.notice("Connection profile saved", "Perfil de conexão salvo", survivesDisconnect = true)
    } }.onFailure { repository.notice("Enter three valid UUIDs", "Informe três UUIDs válidos", true) } }
    /** Only reconnect the last verified configuration address, never an arbitrary paired pad. */
    fun foreground(active: Boolean) {
        if (isForeground == active) return
        isForeground = active
        reconnectJob?.cancel(); reconnectJob = null
        if (!active) {
            stopActiveVibrationPreview()
            repository.stopX10VibrationPreviewAsync()
            backgroundDisconnectJob?.cancel()
            if (repository.needsBackgroundDisconnect()) {
                disconnectJob = app.scope.launch { repository.disconnect() }
            } else {
                backgroundDisconnectJob = app.scope.launch {
                    repository.backgroundDisconnectRequired().filter { it }.collect {
                        disconnectJob?.join()
                        repository.disconnect()
                        cancel()
                    }
                }
            }
            return
        }
        backgroundDisconnectJob?.cancel()
        backgroundDisconnectJob = null
        manuallyDisconnected = false
        reconnectJob = viewModelScope.launch {
            disconnectJob?.join()
            var retryAt = 0L
            var backoff = 5_000L
            var retryDevice: String? = null
            var inputRefreshAt = 0L
            while (isActive) {
                val now = android.os.SystemClock.elapsedRealtime()
                if (now >= inputRefreshAt) { app.input.refreshDevices(); inputRefreshAt = now + 30_000 }
                val device = selectedDevice.value
                if (device?.id != retryDevice) { retryDevice = device?.id; retryAt = 0; backoff = 5_000 }
                if (shouldRetryConfigurationConnect(
                        manuallyDisconnected,
                        switching.value,
                        deviceSheet.value,
                        connectionSheet.value,
                        ble.scanning.value,
                        ble.link.value.phase,
                        ble.enabled(),
                        device,
                        now,
                        retryAt,
                    )) {
                    selectionRevision++
                    val target = requireNotNull(device)
                    runCatching { openLink(requireNotNull(target.address), target.gatt, target.profile ?: DeviceProfile.X10) }
                        .onFailure { ble.log("RECONNECT", it.message ?: "Retry failed") }
                    retryAt = now + backoff + 18_000L
                    backoff = (backoff * 2).coerceAtMost(60_000L)
                }
                if (ble.link.value.phase == LinkPhase.READY) { backoff = 5_000; retryAt = 0 }
                delay(1_500)
            }
        }
    }
    fun disconnect() {
        manuallyDisconnected = true
        selectionRevision++
        disconnectJob = app.scope.launch { repository.disconnect() }
    }
    fun refreshBattery() {
        app.input.refreshDevices()
        if (configurationTarget()) viewModelScope.launch { repository.refreshBattery() }
        else if (keyLinkerTarget()) app.keyLinker.refreshBatteryAsync()
    }
    fun readKeyLinker() { if (keyLinkerTarget()) app.keyLinker.readSettingsAsync() }
    /** Shows the exact frames a KeyLinker write would send, and whether it may be sent. Nothing is sent yet. */
    fun previewKeyLinker(feature: KeyLinkerFeature, build: (KeyLinkerSerials) -> KeyLinkerWritePlan) = app.keyLinker.preview(feature, build)
    fun clearKeyLinkerPreview() = app.keyLinker.clearPreview()
    /** Sends the reviewed write when X20CTL's evidence allows it, then confirms it by reading it back. */
    fun sendKeyLinker() { if (keyLinkerTarget()) app.keyLinker.sendPreviewAsync() }
    fun customize(section: Int = 0) {
        val device = selectedDevice.value
        if (device?.supportsKeyLinker == true) { profileSection.value = section.coerceIn(0, 5); tab.value = 1; return }
        if (device?.supportsConfiguration != true) { tab.value = 2; return }
        profileSection.value = section.coerceIn(0, 5); tab.value = 1
    }
    fun connectSelected() {
        val target = selectedDevice.value ?: return
        if (target.address != null && ble.enabled()) connect(target.address) else connectionSheet.value = true
    }
    fun readMappings() { if (configurationTarget()) viewModelScope.launch { repository.readMappings()?.let { mappings -> edit { it.copy(mappings = mappings) } } } }
    fun readController() {
        if (!configurationTarget()) return
        val binding = captureOperationBinding() ?: return
        val readEpoch = binding.draftEpoch
        viewModelScope.launch {
            repository.readSettings(binding, ::stillBoundToOperation)?.let { settings ->
                if (stillBoundToOperation(binding) && readEpoch == draftEpoch) {
                    edit { current -> settings.mergeInto(current) }
                }
            }
        }
    }
    fun readVersions() { if (configurationTarget()) viewModelScope.launch { repository.readVersions() } }
    fun apply(snapshot: ControllerConfig = draft.value) {
        stopActiveVibrationPreview()
        if (!configurationTarget()) return
        val binding = captureOperationBinding() ?: return
        viewModelScope.launch { repository.apply(snapshot, binding, ::stillBoundToOperation) }
    }
    fun save() = saveAsNewCopy()
    fun updateSavedCopy() {
        val id = savedProfileId.value ?: return
        val snapshot = draft.value
        val binding = captureEditorBinding().copy(linkedProfileId = id)
        viewModelScope.launch { runCatching { app.store.save(SavedProfile(id, snapshot)) }
            .onSuccess {
                finishProfileSave(binding, snapshot, id, newCopy = false)
                repository.notice("Saved profile updated", "Perfil salvo atualizado", survivesDisconnect = true)
            }
            .onFailure { e -> profileSaveFailureNotice(e) } }
    }
    fun saveAsNewCopy() {
        if (!canAddSavedProfile(preferences.value.profiles) && savedProfileId.value == null) {
            repository.notice("You can keep up to 30 saved profiles. Delete one or update an existing copy.", "É possível guardar até 30 perfis. Exclua um ou atualize uma cópia existente.", true)
            return
        }
        val snapshot = draft.value
        val binding = captureEditorBinding()
        val id = UUID.randomUUID().toString()
        viewModelScope.launch { runCatching { app.store.save(SavedProfile(id, snapshot)) }
            .onSuccess {
                finishProfileSave(binding, snapshot, id, newCopy = true)
                repository.notice("Profile saved on this phone", "Perfil salvo neste celular", survivesDisconnect = true)
            }
            .onFailure { e -> profileSaveFailureNotice(e) } }
    }
    fun delete(id: String) {
        viewModelScope.launch {
            runCatching { app.store.delete(id) }
                .onSuccess {
                    drafts.clearSavedProfileLinkEverywhere(id)
                    if (savedProfileId.value == id) mutableSavedProfileId.value = null
                    draftBaselineRevision.update { it + 1 }
                    runCatching { app.store.persistWorkspaceDrafts(drafts.export()) }
                }
                .onFailure { e -> profileSaveFailureNotice(e, delete = true) }
        }
    }

    private fun profileSaveFailureNotice(error: Throwable, delete: Boolean = false) {
        if (error is StoragePersistenceException) {
            repository.notice(
                if (delete) "Could not delete the profile. Local storage is unavailable." else "Could not save the profile. Local storage is unavailable.",
                if (delete) "Não foi possível excluir o perfil. O armazenamento local está indisponível." else "Não foi possível salvar o perfil. O armazenamento local está indisponível.",
                true,
            )
        } else {
            repository.notice(
                if (delete) "Could not delete the profile. Try again." else "Check the profile name and values (up to 30 saved profiles).",
                if (delete) "Não foi possível excluir o perfil. Tente novamente." else "Confira o nome e os valores do perfil (até 30 perfis salvos).",
                true,
            )
        }
    }
    fun importProfile(json: String, origin: EditorSaveBinding? = null) {
        if (origin != null && !origin.matchesActive(editorGeneration, editorWorkspaceKey())) return
        runCatching { ProfileStore.import(json) }.onSuccess { imported ->
            if (origin != null && !origin.matchesActive(editorGeneration, editorWorkspaceKey())) return@onSuccess
            replaceEditorDraft(imported, null)
            drafts.adoptBaseline(editorWorkspaceKey(), imported, null)
            tab.value = 1
            repository.notice(
                "Profile imported. Save it or apply it to your controller when ready.",
                "Perfil importado. Salve ou aplique ao controle quando quiser.",
                survivesDisconnect = true,
            )
        }.onFailure { repository.notice("Invalid or incompatible profile file", "Arquivo de perfil inválido ou incompatível", true) }
    }
    fun startCalibration(kind: CalibrationKind) { if (configurationTarget()) viewModelScope.launch { repository.startCalibration(kind) } }
    fun stopCalibration() { viewModelScope.launch { repository.stopCalibration() } }
    fun reset() { if (configurationTarget()) viewModelScope.launch { repository.reset() } }
    fun rumble() { if (!app.input.rumble()) repository.notice("This gamepad does not expose vibration to Android.", "Este controle não disponibiliza vibração para o Android.", true) }
    fun setLeftVibration(value: Int) {
        edit { it.copy(leftVibration = value) }
        holdX10VibrationPreview(X10Motor.LEFT, value)
    }
    fun setRightVibration(value: Int) {
        edit { it.copy(rightVibration = value) }
        holdX10VibrationPreview(X10Motor.RIGHT, value)
    }
    fun finishLeftVibrationPreview() = finishX10VibrationPreview(X10Motor.LEFT)
    fun finishRightVibrationPreview() = finishX10VibrationPreview(X10Motor.RIGHT)
    private fun holdX10VibrationPreview(motor: X10Motor, intensity: Int) {
        if (!canPreviewX10Vibration()) {
            vibrationPreviewActivity.value = X10VibrationPreviewActivity()
            return
        }
        vibrationPreviewActivity.value = X10VibrationPreviewActivity(motor)
        vibrationPreviewRequests.trySend(VibrationPreviewRequest.Hold(motor, intensity))
    }
    private fun finishX10VibrationPreview(motor: X10Motor) {
        if (vibrationPreviewActivity.value.active != motor) return
        vibrationPreviewActivity.value = X10VibrationPreviewActivity()
        vibrationPreviewRequests.trySend(VibrationPreviewRequest.Stop)
    }
    /** Stops a held X10 motor preview when leaving the profile editor or starting another operation. */
    fun cancelX10VibrationPreview() = stopActiveVibrationPreview()
    private fun stopActiveVibrationPreview() {
        if (vibrationPreviewActivity.value.active == null) return
        vibrationPreviewActivity.value = X10VibrationPreviewActivity()
        vibrationPreviewRequests.trySend(VibrationPreviewRequest.Stop)
    }
    private suspend fun runX10VibrationPreviewWorker() {
        var activeMotor: X10Motor? = null
        for (request in vibrationPreviewRequests) {
            when (request) {
                is VibrationPreviewRequest.Hold -> {
                    if (!canPreviewX10Vibration()) continue
                    if (activeMotor != null && activeMotor != request.motor) repository.stopX10VibrationPreview()
                    repository.holdX10VibrationPreview(request.motor, request.intensity)
                    activeMotor = request.motor
                }
                VibrationPreviewRequest.Stop -> {
                    repository.stopX10VibrationPreview()
                    activeMotor = null
                }
            }
        }
    }
    private fun canPreviewX10Vibration() =
        selectedDevice.value?.isX10 == true && configurationTarget() && operationGate.canSwitchNow()
    override fun onCleared() {
        backgroundDisconnectJob?.cancel()
        repository.stopX10VibrationPreviewAsync()
        app.scope.launch {
            disconnectJob?.join()
            repository.disconnect()
        }
        super.onCleared()
    }
    private sealed interface VibrationPreviewRequest {
        data class Hold(val motor: X10Motor, val intensity: Int) : VibrationPreviewRequest
        data object Stop : VibrationPreviewRequest
    }
    companion object {
        private const val SETUP_ID = "new-x10"
    }
}
