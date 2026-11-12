// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.ble.*
import br.dev.edgard.easygamepad.demo.ConfigurationLinkHub
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
    val applyResult: X10ApplyResult? = null,
    /** Config snapshot uploaded in the last apply attempt; kept separate from the live draft. */
    val lastAppliedSnapshot: ControllerConfig? = null,
)
class ControllerRepository internal constructor(
    private val transport: GamepadTransport,
    private val link: StateFlow<LinkState>,
    private val scope: CoroutineScope,
    private val log: (String, String) -> Unit,
    private val onDisconnect: suspend () -> Unit,
) {
    constructor(ble: BleClient, scope: CoroutineScope) : this(
        ble, ble.link, scope, ble::log,
        onDisconnect = { ble.stopScan(); ble.disconnect() },
    )

    constructor(hub: ConfigurationLinkHub, scope: CoroutineScope) : this(
        hub, hub.link, scope, hub::log,
        onDisconnect = { hub.disconnectAll() },
    )

    private class KrlyConnection(val address: String, val session: CommandSession)

    private val mutableState = MutableStateFlow(HardwareState())
    val state = mutableState.asStateFlow()
    private val mutableNotices = MutableSharedFlow<Notice>(extraBufferCapacity = 10)
    val notices = mutableNotices.asSharedFlow()
    private var connection: KrlyConnection? = null
    private var eventsJob: Job? = null
    private var pollingJob: Job? = null
    private var calibrationTimeout: Job? = null
    private var macroMode = false
    private val exclusive = Mutex()

    private fun isCurrent(active: KrlyConnection): Boolean = connection === active && link.value.let {
        it.phase == LinkPhase.READY && it.protocol == ConfigurationProtocol.KRLY && it.address == active.address
    }

    init {
        scope.launch {
            // KRLY commands go only to links opened for the X10 (QMacro). A KeyLinker link is never polled here.
            link.map { state ->
                state.address.takeIf { state.phase == LinkPhase.READY && state.protocol == ConfigurationProtocol.KRLY }
            }.distinctUntilChanged().collect { address ->
                eventsJob?.cancel()
                pollingJob?.cancel()
                calibrationTimeout?.cancel()
                connection?.session?.close()
                connection = null
                if (address == null) {
                    mutableState.update { old -> HardwareState(calibration = if (old.calibration.stopRequired)
                        old.calibration.copy(phase = CalibrationPhase.FAILED, stopRequired = false) else old.calibration) }
                    return@collect
                }
                lateinit var active: KrlyConnection
                val bound = ConnectionBoundTransport(transport) { isCurrent(active) }
                active = KrlyConnection(address, CommandSession(bound, scope))
                connection = active
                eventsJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    active.session.events.collect { frame ->
                        if (!isCurrent(active)) return@collect
                        KrlyCodec.battery(frame)?.let { battery -> mutableState.update { it.copy(battery = battery) } }
                        mutableState.update { it.copy(calibration = KrlyCodec.calibration(it.calibration, frame)) }
                    }
                }
                pollingJob = scope.launch {
                    if (isCurrent(active)) refreshBattery()
                    while (isActive) {
                        delay(180000)
                        if (isCurrent(active) && !mutableState.value.calibration.stopRequired) refreshBattery()
                    }
                }
            }
        }
    }
    fun notice(en: String, pt: String, error: Boolean = false, survivesDisconnect: Boolean = false) {
        mutableNotices.tryEmit(Notice(en, pt, error, survivesDisconnect))
    }
    fun notice(notice: Notice) { mutableNotices.tryEmit(notice) }
    private fun connected() = checkNotNull(connection?.session) { "Controller not connected" }
    private suspend fun withVibrationPreview(quiet: Boolean, block: suspend (CommandSession) -> Unit): Boolean {
        return try {
            exclusive.withLock {
                if (state.value.calibration.stopRequired) return@withLock false
                block(connected())
                true
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            log("VIBRATION", error.message ?: error.javaClass.simpleName)
            if (!quiet) notice("The controller did not confirm this operation. Check the connection and diagnostics.",
                "O controle não confirmou a operação. Verifique a conexão e o diagnóstico.", true)
            false
        }
    }
    suspend fun holdX10VibrationPreview(motor: X10Motor, intensity: Int): Boolean {
        log("VIBRATION", "Holding X10 motor $motor at $intensity")
        return withVibrationPreview(quiet = true) { X10VibrationPreview(it).hold(motor, intensity) }
    }
    suspend fun stopX10VibrationPreview(): Boolean {
        log("VIBRATION", "Stopping X10 motor preview")
        return withVibrationPreview(quiet = true) { X10VibrationPreview(it).stop() }
    }
    fun stopX10VibrationPreviewAsync() {
        scope.launch { stopX10VibrationPreview() }
    }
    private suspend fun <T> runOperation(showSuccess: Boolean = true, block: suspend () -> T): T? =
        runBoundOperation(showSuccess, binding = null, stillBound = { true }, block)

    private suspend fun <T> runBoundOperation(
        showSuccess: Boolean,
        binding: OperationBinding?,
        stillBound: (OperationBinding) -> Boolean,
        block: suspend () -> T,
    ): T? = exclusive.withLock {
        if (binding != null && !stillBound(binding)) {
            log("OP", "Skipped stale binding before start for ${binding.linkAddress}")
            return@withLock null
        }
        mutableState.update { it.copy(busy = true, progress = 0f) }
        try {
            val result = block()
            if (binding != null && !stillBound(binding)) {
                log("OP", "Dropped late result for ${binding.linkAddress}")
                return@withLock null
            }
            if (showSuccess) notice("Confirmed by controller", "Confirmado pelo controle")
            result
        } catch (timeout: TimeoutCancellationException) {
            notice("The controller response timed out. Reconnect to try again.", "O controle não respondeu a tempo. Reconecte para tentar novamente.", true)
            null
        } catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) {
            log("ERROR", error.message ?: error.javaClass.simpleName)
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
    suspend fun readSettings(
        binding: OperationBinding? = null,
        stillBound: (OperationBinding) -> Boolean = { true },
    ): ControllerSettings? = runBoundOperation(false, binding, stillBound) {
        check(!state.value.calibration.stopRequired)
        val active = connected()
        X10ProfileClient(active).readSettings { progress -> mutableState.update { it.copy(progress = progress) } }
    }?.also {
        mutableState.update { s -> s.copy(progress = 1f, settingsReadAt = System.currentTimeMillis()) }
        notice(
            "Read from X10: face mappings, M1/M2 settings, sticks, triggers, vibration and turbo.",
            "Lido do X10: mapeamentos da frente, ajustes M1/M2, analógicos, gatilhos, vibração e turbo.",
        )
    }
    suspend fun readVersions() = runOperation {
        check(!state.value.calibration.stopRequired)
        val firmware = requireNotNull(KrlyCodec.firmware(connected().request(132, bytes(1, 0))))
        mutableState.update { it.copy(firmware = firmware) }
    }
    suspend fun testVibration(motor: X10Motor, intensity: Int, quiet: Boolean = false): Boolean {
        var completed = false
        try {
            runOperation(false) {
                check(!state.value.calibration.stopRequired)
                val active = connected()
                log("VIBRATION", "Testing X10 motor $motor with value $intensity")
                val result = X10VibrationTest(active).run(motor, intensity)
                completed = true
                log("VIBRATION", "Motor $motor test exchanges completed; readable settings unchanged: ${result.settingsUnchanged}; physical result not checked")
                if (result.settingsUnchanged) {
                    if (!quiet) notice("Test sent to X10; readable settings are unchanged.",
                        "Teste enviado ao X10; os ajustes legíveis não mudaram.")
                } else notice("Readable settings changed during the test. Stop testing and read the controller settings.",
                    "Os ajustes legíveis mudaram durante o teste. Encerre os testes e leia os ajustes do controle.", true)
            }
            return completed
        } finally {
            if (!completed && !quiet) notice("The vibration test did not complete. If vibration continues, turn the controller off before reconnecting.",
                "O teste de vibração não foi concluído. Se continuar vibrando, desligue o controle antes de reconectar.", true)
        }
    }
    suspend fun apply(
        config: ControllerConfig,
        binding: OperationBinding? = null,
        stillBound: (OperationBinding) -> Boolean = { true },
    ): Boolean {
        mutableState.update { it.copy(appliedAt = null, applyResult = null, lastAppliedSnapshot = config, progress = 0f) }
        val result = runBoundOperation(false, binding, stillBound) {
            check(!state.value.calibration.stopRequired)
            config.validate()
            val active = connected()
            X10ProfileClient(active).apply(config, uploadMode = { macroMode = it }, progress = { progress ->
                mutableState.update { it.copy(progress = progress) }
            })
        } ?: return false
        val now = System.currentTimeMillis()
        mutableState.update { it.copy(applyResult = result, progress = 1f,
            appliedAt = now.takeIf { result is X10ApplyResult.Matched },
            settingsReadAt = if (result is X10ApplyResult.Unconfirmed) it.settingsReadAt else now) }
        log("READBACK", when (result) {
            is X10ApplyResult.Matched -> "X10 readable settings match read-back; power-cycle persistence not checked"
            is X10ApplyResult.Mismatch -> "X10 settings mismatch: ${result.fields.joinToString()}"
            is X10ApplyResult.Unconfirmed -> "X10 settings unconfirmed: ${result.reason}"
        })
        x10ApplyNotice(result).let { notice(it.english, it.portuguese, it.error) }
        return result is X10ApplyResult.Matched
    }

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
        if (state.value.calibration.stopRequired && link.value.phase == LinkPhase.READY) stopCalibration()
        if (macroMode && link.value.phase == LinkPhase.READY) {
            runCatching { withTimeout(5500) { connection?.session?.ack(1, bytes(0)) } }
        }
        macroMode = false
        onDisconnect()
    }
}
