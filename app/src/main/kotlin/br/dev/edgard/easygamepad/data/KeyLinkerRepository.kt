// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Amjad AAYD
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.ble.BleClient
import br.dev.edgard.easygamepad.ble.LinkPhase
import br.dev.edgard.easygamepad.protocol.ConfigurationProtocol
import br.dev.edgard.easygamepad.protocol.keylinker.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class KeyLinkerPhase { IDLE, IDENTIFYING, IDENTIFIED, NO_REPLY, READING }

/**
 * A write shown for review before it is sent. [build] makes the frames again with the live session's
 * counters when the user confirms; nothing is sent while [decision] is Blocked.
 */
class KeyLinkerPlanPreview(val plan: KeyLinkerWritePlan, val decision: KeyLinkerWriteDecision, val build: (KeyLinkerSerials) -> KeyLinkerWritePlan) {
    val allowed get() = decision is KeyLinkerWriteDecision.Allowed
    val packetsHex get() = plan.packets.map { packet -> packet.joinToString(" ") { "%02X".format(it.toInt() and 255) } }
}

data class KeyLinkerState(
    val phase: KeyLinkerPhase = KeyLinkerPhase.IDLE,
    val address: String? = null,
    val identification: KeyLinkerIdentification? = null,
    val battery: KeyLinkerBattery? = null,
    val batteryAt: Long? = null,
    val settings: KeyLinkerSettings? = null,
    val progress: Float = 0f,
    val busy: Boolean = false,
    val preview: KeyLinkerPlanPreview? = null,
) {
    val assessment get() = identification?.assessment
    val capabilities get() = identification?.capabilities
    /** Model id the controller reported, when it matches a known product. */
    val identifiedModelId get() = assessment?.known?.modelId
    val mayRead get() = assessment?.mayRead == true
}

/**
 * Drives a KeyLinker (EasySMX X20 / X20 Pro) configuration link. It acts only on links that were
 * opened for [ConfigurationProtocol.KEYLINKER], which happens only after the user chose the
 * controller and the KeyLinker GATT service was found. KRLY links are left to [ControllerRepository].
 *
 * Identification (0x91, 0x93, 0xB0) is always allowed; further reads require a proven product
 * identity. A write is shown for review first and sent only when the user confirms and X20CTL's hardware
 * evidence allows it (see [KeyLinkerAssessment.writeDecision]); it is then confirmed by reading it back.
 */
class KeyLinkerRepository(private val ble: BleClient, private val scope: CoroutineScope, private val notify: (Notice) -> Unit) {
    private val mutableState = MutableStateFlow(KeyLinkerState())
    val state = mutableState.asStateFlow()
    private var gate: KeyLinkerGate? = null
    private var session: KeyLinkerSession? = null
    private var client: KeyLinkerClient? = null
    private var linkJob: Job? = null
    private val exclusive = Mutex()
    private val planSerials = KeyLinkerSerials()

    init {
        scope.launch {
            ble.link.map { link -> link.address.takeIf { link.phase == LinkPhase.READY && link.protocol == ConfigurationProtocol.KEYLINKER } }
                .distinctUntilChanged().collect { address ->
                    linkJob?.cancel(); linkJob = null
                    session?.close(); session = null; client = null; gate = null
                    if (address == null) { mutableState.value = KeyLinkerState(); return@collect }
                    val newGate = KeyLinkerGate()
                    val newSession = KeyLinkerSession(ble, scope, newGate::authorize)
                    gate = newGate; session = newSession; client = KeyLinkerClient(newSession)
                    mutableState.value = KeyLinkerState(KeyLinkerPhase.IDENTIFYING, address)
                    linkJob = scope.launch { identifyThenPoll() }
                }
        }
    }

    private suspend fun identifyThenPoll() {
        val identified = exclusive.withLock { guarded { identify() } } ?: false
        if (!identified || !state.value.mayRead) return
        refreshBattery()
        readSettings(quiet = true)
        while (currentCoroutineContext().isActive) { delay(180_000); refreshBattery() }
    }

    private suspend fun identify(): Boolean {
        val active = checkNotNull(client)
        val result = active.identify()
        gate?.assessment = result.assessment
        gate?.capabilities = result.capabilities
        mutableState.update { it.copy(phase = if (result.identity == null) KeyLinkerPhase.NO_REPLY else KeyLinkerPhase.IDENTIFIED, identification = result) }
        ble.log("KEYLINKER", result.assessment?.reason ?: "No identity reply")
        return result.identity != null
    }

    /** Runs one exchange series and turns failures into a notice. A transport error drops the link. */
    private suspend fun <T> guarded(block: suspend () -> T): T? {
        mutableState.update { it.copy(busy = true) }
        return try { block() }
        catch (cancel: CancellationException) { throw cancel }
        catch (refused: KeyLinkerNotAuthorized) {
            ble.log("KEYLINKER", "Refused locally: ${refused.message}")
            notify(Notice("Not sent: ${refused.message}", "Não enviado: ${refused.message}", true)); null
        } catch (error: Exception) {
            ble.log("ERROR", error.message ?: error.javaClass.simpleName)
            notify(Notice("The controller did not answer. Check the connection and try again.", "O controle não respondeu. Verifique a conexão e tente novamente.", true)); null
        } finally { mutableState.update { it.copy(busy = false) } }
    }

    fun refreshBatteryAsync() { scope.launch { refreshBattery() } }
    suspend fun refreshBattery() {
        if (!state.value.mayRead) return
        exclusive.withLock {
            guarded { checkNotNull(client).battery() }?.let { gauge -> mutableState.update { it.copy(battery = gauge, batteryAt = System.currentTimeMillis()) } }
        }
    }

    fun readSettingsAsync() { scope.launch { readSettings(quiet = false) } }
    suspend fun readSettings(quiet: Boolean) {
        if (!state.value.mayRead) {
            notify(Notice("This controller was identified only. Settings are read from verified X20 and X20 Pro identities.",
                "Este controle foi apenas identificado. Os ajustes são lidos de identidades verificadas do X20 e X20 Pro.", true))
            return
        }
        exclusive.withLock {
            mutableState.update { it.copy(phase = KeyLinkerPhase.READING, progress = 0f) }
            val settings = guarded { checkNotNull(client).readSettings(state.value.capabilities) { p -> mutableState.update { it.copy(progress = p) } } }
            mutableState.update { it.copy(phase = KeyLinkerPhase.IDENTIFIED, settings = settings ?: it.settings, progress = 1f) }
            if (settings != null && !quiet) notify(Notice("Read from the controller. Nothing was changed.", "Lido do controle. Nada foi alterado."))
        }
    }

    /** Builds the frames a write would send and records why it is blocked. Never sends anything. */
    fun preview(feature: KeyLinkerFeature, build: (KeyLinkerSerials) -> KeyLinkerWritePlan) {
        val current = state.value
        val decision = current.assessment?.writeDecision(feature, current.capabilities)
            ?: KeyLinkerWriteDecision.Blocked(KeyLinkerWriteDecision.Reason.NOT_IDENTIFIED, "Controller not identified yet.")
        val plan = runCatching { build(planSerials) }.getOrElse { error ->
            notify(Notice("Invalid value: ${error.message}", "Valor inválido: ${error.message}", true)); return
        }
        mutableState.update { it.copy(preview = KeyLinkerPlanPreview(plan, decision, build)) }
    }
    fun clearPreview() { mutableState.update { it.copy(preview = null) } }

    fun sendPreviewAsync() { scope.launch { sendPreview() } }

    /** Sends the reviewed write the X20CTL way (re-read, send, read back), then reads every setting again. */
    private suspend fun sendPreview() {
        val preview = state.value.preview?.takeIf { it.allowed } ?: return
        mutableState.update { it.copy(preview = null) }
        val result = exclusive.withLock {
            guarded {
                val active = checkNotNull(client)
                val plan = preview.build(active.serials)
                ble.log("KEYLINKER", "Writing ${plan.summary} (${plan.packets.size} frame(s))")
                active.apply(plan).also { ble.log("KEYLINKER", "Write result: $it") }
            }
        } ?: return
        notify(when (result) {
            KeyLinkerWriteResult.Confirmed -> Notice("Saved to the controller and confirmed by reading it back.", "Salvo no controle e confirmado pela releitura.")
            is KeyLinkerWriteResult.Mismatch -> Notice("The controller now reports ${result.readBack}, not what was sent. Check it before trying again.",
                "O controle agora informa ${result.readBack}, não o que foi enviado. Confira antes de tentar de novo.", true)
            KeyLinkerWriteResult.Stale -> Notice("This setting changed on the controller, or could not be read, since you opened it. Nothing was sent.",
                "Este ajuste mudou no controle, ou não pôde ser lido, desde que você o abriu. Nada foi enviado.", true)
            KeyLinkerWriteResult.Unconfirmed -> Notice("Sent, but the controller did not answer the check. Reading the settings again.",
                "Enviado, mas o controle não respondeu à conferência. Lendo os ajustes de novo.", true)
        })
        readSettings(quiet = true)
    }
}
