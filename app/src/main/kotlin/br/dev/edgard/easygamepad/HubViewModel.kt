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
import br.dev.edgard.easygamepad.protocol.*
import br.dev.edgard.easygamepad.protocol.keylinker.KeyLinkerFeature
import br.dev.edgard.easygamepad.protocol.keylinker.KeyLinkerSerials
import br.dev.edgard.easygamepad.protocol.keylinker.KeyLinkerWritePlan
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.UUID

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
    val tab = MutableStateFlow(0)
    val profileSection = MutableStateFlow(0)
    val connectionSheet = MutableStateFlow(false)
    val hardware = repository.state
    val keyLinker = app.keyLinker.state
    val input = app.input.state
    val link = ble.link
    private var connectingGatt: GattProfile? = null
    private var connectingModel: String? = null
    private var reconnectJob: Job? = null
    private var disconnectJob: Job? = null
    private var manuallyDisconnected = false
    private var isForeground = false

    init {
        viewModelScope.launch {
            val prefs = app.store.preferences.first()
            selectedId.value = prefs.controllers.firstOrNull { it.address == prefs.address }?.let { "ble:${it.address}" }
                ?: controllerDevices(prefs.controllers, app.input.state.value.pads).firstOrNull()?.id
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
                    if (previousId != target) mutableDraft.value = drafts.switch(previousId, target, mutableDraft.value, previousId == SETUP_ID)
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
    }
    fun chooseDevice(device: ControllerDevice) {
        if (device.id == selectedId.value) { deviceSheet.value = false; return }
        changeSelection(device.id)
    }
    private fun changeSelection(id: String?, descriptor: String? = null) {
        if (hardware.value.busy || hardware.value.calibration.stopRequired || switching.value) {
            repository.notice("Finish the current controller operation before switching.", "Finalize a operação atual do controle antes de trocar.", true)
            return
        }
        switching.value = true
        selectionRevision++
        viewModelScope.launch {
            try {
                disconnectJob?.join()
                repository.disconnect()
                mutableDraft.value = drafts.switch(selectedId.value, id, mutableDraft.value)
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
        if (hardware.value.busy || hardware.value.calibration.stopRequired || switching.value) {
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
        if (hardware.value.busy || hardware.value.calibration.stopRequired || switching.value) return
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
                        repository.notice("Name saved for this session. Android reports the same identity for these controllers.",
                            "Nome salvo para esta sessão. O Android identifica esses controles da mesma forma.")
                        return@launch
                    }
                }
                repository.notice("Controller renamed", "Nome do controle alterado")
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
    fun edit(update: (ControllerConfig) -> ControllerConfig) { mutableDraft.update(update) }
    fun load(config: ControllerConfig) { mutableDraft.value = config; tab.value = 1 }
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
        if (device?.hasConfigurationLink != true || switching.value) return
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
            .onSuccess { repository.notice("Saved as the identified model", "Salvo como o modelo identificado") } }
    }
    fun saveGatt(gatt: GattProfile) { runCatching {
        listOf(gatt.service, gatt.write, gatt.notify).forEach { UUID.fromString(it) }
    }.onSuccess { viewModelScope.launch {
        val address = selectedDevice.value?.address
        if (address != null) app.store.device(address, gatt) else setupGatt.value = gatt
        repository.notice("Connection profile saved", "Perfil de conexão salvo")
    } }.onFailure { repository.notice("Enter three valid UUIDs", "Informe três UUIDs válidos", true) } }
    /** Only reconnect the last verified configuration address, never an arbitrary paired pad. */
    fun foreground(active: Boolean) {
        if (isForeground == active) return
        isForeground = active
        reconnectJob?.cancel(); reconnectJob = null
        if (!active) {
            disconnectJob = app.scope.launch { repository.disconnect() }
            return
        }
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
                if (!manuallyDisconnected && !switching.value && !deviceSheet.value && !connectionSheet.value && !ble.scanning.value &&
                    ble.link.value.phase == LinkPhase.DISCONNECTED && ble.enabled() &&
                    device?.hasConfigurationLink == true && !device.address.isNullOrBlank() && now >= retryAt) {
                    selectionRevision++
                    runCatching { openLink(requireNotNull(device.address), device.gatt, device.profile ?: DeviceProfile.X10) }
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
    fun readController() { if (configurationTarget()) viewModelScope.launch {
        repository.readSettings()?.let { settings ->
            edit { current -> settings.mergeInto(current) }
        }
    } }
    fun readVersions() { if (configurationTarget()) viewModelScope.launch { repository.readVersions() } }
    fun apply() { if (configurationTarget()) { val snapshot = draft.value; viewModelScope.launch { repository.apply(snapshot) } } }
    fun save() { viewModelScope.launch { runCatching { app.store.save(SavedProfile(UUID.randomUUID().toString(), draft.value)) }
        .onSuccess { repository.notice("Profile saved on this phone", "Perfil salvo neste celular") }
        .onFailure { repository.notice("Check the profile name and values (up to 30 saved profiles).", "Confira o nome e os valores do perfil (até 30 perfis salvos).", true) } } }
    fun delete(id: String) { viewModelScope.launch { app.store.delete(id) } }
    fun importProfile(json: String) { runCatching { ProfileStore.import(json) }.onSuccess {
        load(it); repository.notice("Profile imported. Save it or apply it to your controller when ready.", "Perfil importado. Salve ou aplique ao controle quando quiser.")
    }.onFailure { repository.notice("Invalid or incompatible profile file", "Arquivo de perfil inválido ou incompatível", true) } }
    fun startCalibration(kind: CalibrationKind) { if (configurationTarget()) viewModelScope.launch { repository.startCalibration(kind) } }
    fun stopCalibration() { viewModelScope.launch { repository.stopCalibration() } }
    fun reset() { if (configurationTarget()) viewModelScope.launch { repository.reset() } }
    fun rumble() { if (!app.input.rumble()) repository.notice("This gamepad does not expose vibration to Android.", "Este controle não disponibiliza vibração para o Android.", true) }
    companion object { private const val SETUP_ID = "new-x10" }
}
