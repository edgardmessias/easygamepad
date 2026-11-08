// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.ble.*
import br.dev.edgard.easygamepad.protocol.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class HardwareState(
    val battery: Battery? = null, val firmware: Firmware? = null,
    val calibration: CalibrationState = CalibrationState(),
    val busy: Boolean = false, val progress: Float = 0f,
    val appliedAt: Long? = null, val sleepMinutes: Int? = null,
    val settingsReadAt: Long? = null,
)
data class Notice(val english: String, val portuguese: String, val error: Boolean = false)

class ControllerRepository(val ble: BleClient, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(HardwareState())
    val state = mutableState.asStateFlow()
    private val mutableNotices = MutableSharedFlow<Notice>(extraBufferCapacity = 10)
    val notices = mutableNotices.asSharedFlow()
    private var session: CommandSession? = null
    private var eventsJob: Job? = null
    private var pollingJob: Job? = null
    private var calibrationTimeout: Job? = null
    private var macroMode = false
    private val exclusive = Mutex()

    init {
        scope.launch {
            // KRLY commands go only to links opened for the X10 (QMacro). A KeyLinker link is never polled here.
            ble.link.map { it.phase == LinkPhase.READY && it.protocol == ConfigurationProtocol.KRLY }.distinctUntilChanged().collect { krlyReady ->
                if (krlyReady) {
                    val active = CommandSession(ble, scope)
                    session = active
                    eventsJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        active.events.collect { frame ->
                            KrlyCodec.battery(frame)?.let { battery -> mutableState.update { it.copy(battery = battery) } }
                            mutableState.update { it.copy(calibration = KrlyCodec.calibration(it.calibration, frame)) }
                        }
                    }
                    pollingJob = scope.launch {
                        refreshBattery()
                        while (isActive) { delay(180000); if (!mutableState.value.calibration.stopRequired) refreshBattery() }
                    }
                } else {
                    session?.close(); session = null
                    pollingJob?.cancel(); eventsJob?.cancel(); calibrationTimeout?.cancel()
                    mutableState.update { old -> HardwareState(calibration = if (old.calibration.stopRequired)
                        old.calibration.copy(phase = CalibrationPhase.FAILED, stopRequired = false) else old.calibration) }
                }
            }
        }
    }
    fun notice(en: String, pt: String, error: Boolean = false) { mutableNotices.tryEmit(Notice(en, pt, error)) }
    private fun connected() = checkNotNull(session) { "Controller not connected" }
    private suspend fun <T> runOperation(showSuccess: Boolean = true, block: suspend () -> T): T? = exclusive.withLock {
        mutableState.update { it.copy(busy = true, progress = 0f) }
        try {
            val result = block()
            if (showSuccess) notice("Confirmed by controller", "Confirmado pelo controle")
            result
        } catch (timeout: TimeoutCancellationException) {
            notice("The controller response timed out. Reconnect to try again.", "O controle não respondeu a tempo. Reconecte para tentar novamente.", true)
            null
        } catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) {
            ble.log("ERROR", error.message ?: error.javaClass.simpleName)
            notice("The controller did not confirm this operation. Check the connection and diagnostics.",
                "O controle não confirmou a operação. Verifique a conexão e o diagnóstico.", true)
            null
        } finally { mutableState.update { it.copy(busy = false) } }
    }
    suspend fun refreshBattery() = runOperation(false) {
        check(!state.value.calibration.stopRequired)
        val raw = connected().request(49, accept = { KrlyCodec.battery(it) != null })
        val battery = requireNotNull(KrlyCodec.battery(raw))
        mutableState.update { it.copy(battery = battery) }
    }
    suspend fun readMappings(): Map<PadKey, PadKey>? = runOperation {
        check(!state.value.calibration.stopRequired)
        val pages = MappingPages()
        var result: Map<PadKey, PadKey>? = null
        connected().request(80, bytes(1)) { raw -> pages.accept(raw).also { result = it } != null }
        requireNotNull(result)
    }
    suspend fun readSettings(): ControllerSettings? = runOperation(false) {
        check(!state.value.calibration.stopRequired)
        val active = connected()
        val pages = SettingsPages()
        var settings: ControllerSettings? = null
        active.request(119, bytes(2, 1)) { raw -> pages.accept(raw).also { settings = it } != null }
        mutableState.update { it.copy(progress = .6f) }
        val mappings = MappingPages()
        var outputs: Map<PadKey, PadKey>? = null
        active.request(80, bytes(1)) { raw -> mappings.accept(raw).also { outputs = it } != null }
        val result = requireNotNull(settings).copy(mappings = requireNotNull(outputs))
        mutableState.update { it.copy(progress = 1f, settingsReadAt = System.currentTimeMillis()) }
        notice("Read from X10: buttons, sticks, triggers, vibration and turbo. Macro sequences kept from your draft.",
            "Lido do X10: botões, analógicos, gatilhos, vibração e turbo. Sequências de macro mantidas do rascunho.")
        result
    }
    suspend fun readVersions() = runOperation {
        check(!state.value.calibration.stopRequired)
        val firmware = requireNotNull(KrlyCodec.firmware(connected().request(132, bytes(1, 0))))
        mutableState.update { it.copy(firmware = firmware) }
    }
    suspend fun apply(config: ControllerConfig): Boolean = runOperation {
        check(!state.value.calibration.stopRequired)
        config.validate()
        val active = connected()
        try {
            active.ack(1, bytes(16))
            macroMode = true
            active.ack(54, bytes(0, 0))
            val commands = buildList<Pair<Int, ByteArray>> {
                config.macros.forEach { macro -> macro.steps.indices.forEach { add(58 to KrlyCodec.macroStep(macro, it)) } }
                config.turbos.forEach { add(55 to bytes(it.key.code, if (it.automatic) 1 else 0, it.rate)) }
                add(57 to bytes(config.leftTrigger.min, config.leftTrigger.max, config.rightTrigger.min, config.rightTrigger.max))
                add(62 to KrlyCodec.sticks(config.leftStick, config.rightStick))
                config.mappings.forEach { (from, to) -> add(61 to bytes(from.code, to.code)) }
                add(53 to bytes(config.leftVibration, config.rightVibration))
                add(52 to byteArrayOf())
            }
            commands.forEachIndexed { index, (id, payload) ->
                active.ack(id, payload)
                mutableState.update { it.copy(progress = (index + 1f) / commands.size) }
            }
            active.ack(1, bytes(0))
            macroMode = false
            mutableState.update { it.copy(appliedAt = System.currentTimeMillis()) }
            true
        } finally {
            // Also restore normal mode if a command was rejected or the operation cancelled.
            withContext(NonCancellable) {
                if (macroMode && ble.link.value.phase == LinkPhase.READY) runCatching { withTimeout(5500) { active.ack(1, bytes(0)); macroMode = false } }
            }
        }
    } ?: false

    suspend fun startCalibration(kind: CalibrationKind) = runOperation(false) {
        check(!state.value.calibration.stopRequired)
        mutableState.update { it.copy(calibration = CalibrationState(kind, CalibrationPhase.STARTING, stopRequired = true)) }
        try {
            if (kind == CalibrationKind.SELF) connected().ack(65)
            else connected().send(120, bytes(1, kind.wireType))
            mutableState.update { old -> if (old.calibration.phase == CalibrationPhase.STARTING)
                old.copy(calibration = old.calibration.copy(phase = CalibrationPhase.ACTIVE)) else old }
            calibrationTimeout = scope.launch {
                delay(90000)
                calibrationTimeout = null
                if (state.value.calibration.stopRequired) {
                    stopCalibration(timedOut = true)
                    notice("Calibration timed out. Reconnect if the controller is still in calibration mode.", "A calibração expirou. Reconecte se o controle continuar em calibração.", true)
                }
            }
        } catch (error: Exception) {
            mutableState.update { it.copy(calibration = it.calibration.copy(phase = CalibrationPhase.FAILED)) }
            throw error
        }
    }
    suspend fun stopCalibration(timedOut: Boolean = false) = runOperation(false) {
        val old = state.value.calibration
        if (!old.stopRequired) return@runOperation
        calibrationTimeout?.cancel(); calibrationTimeout = null
        if (old.kind == CalibrationKind.SELF) connected().ack(66)
        else connected().send(120, bytes(2, old.kind.wireType))
        // A stop write is not proof of a successful calibration.
        mutableState.update { it.copy(calibration = it.calibration.copy(stopRequired = false, phase = when {
            timedOut -> CalibrationPhase.FAILED
            it.calibration.phase == CalibrationPhase.SUCCEEDED -> CalibrationPhase.SUCCEEDED
            else -> CalibrationPhase.CANCELLED
        })) }
    }
    suspend fun reset() = runOperation {
        check(!state.value.calibration.stopRequired)
        connected().ack(5, bytes(0))
        mutableState.update { it.copy(appliedAt = null) }
    }
    suspend fun disconnect() {
        if (state.value.calibration.stopRequired && ble.link.value.phase == LinkPhase.READY) stopCalibration()
        if (macroMode && ble.link.value.phase == LinkPhase.READY) {
            runCatching { withTimeout(5500) { session?.ack(1, bytes(0)) } }
        }
        macroMode = false
        ble.stopScan(); ble.disconnect()
    }
}
