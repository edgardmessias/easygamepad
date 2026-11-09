// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Amjad AAYD
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.ble.BleClient
import br.dev.edgard.easygamepad.ble.LinkPhase
import br.dev.edgard.easygamepad.ble.LinkState
import br.dev.edgard.easygamepad.protocol.ConfigurationProtocol
import br.dev.edgard.easygamepad.protocol.GamepadTransport
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
class KeyLinkerRepository internal constructor(
    private val transport: GamepadTransport,
    private val link: StateFlow<LinkState>,
    private val scope: CoroutineScope,
    private val log: (String, String) -> Unit,
    private val notify: (Notice) -> Unit,
) {
    constructor(ble: BleClient, scope: CoroutineScope, notify: (Notice) -> Unit) : this(ble, ble.link, scope, ble::log, notify)

    private val mutableState = MutableStateFlow(KeyLinkerState())
    val state = mutableState.asStateFlow()
    private class Connection(val address: String, val gate: KeyLinkerGate, val session: KeyLinkerSession) {
        val client = KeyLinkerClient(session)
    }
    private var connection: Connection? = null
    private var previewConnection: Connection? = null
    private var linkJob: Job? = null
    private val exclusive = Mutex()
    private val planSerials = KeyLinkerSerials()

    init {
        scope.launch {
            link.map { link -> link.address.takeIf { link.phase == LinkPhase.READY && link.protocol == ConfigurationProtocol.KEYLINKER } }
                .distinctUntilChanged().collect { address ->
                    linkJob?.cancel(); linkJob = null
                    val previous = connection
                    connection = null; previewConnection = null
                    previous?.session?.close()
                    if (address == null) { mutableState.value = KeyLinkerState(); return@collect }
                    val newGate = KeyLinkerGate()
                    lateinit var active: Connection
                    val boundTransport = object : GamepadTransport {
                        override val notifications = transport.notifications.filter { isCurrent(active) }
                        override suspend fun write(frame: ByteArray) {
                            check(isCurrent(active)) { "KeyLinker connection changed" }
                            transport.write(frame)
                        }
                        override fun abort(cause: Throwable) {
                            if (isCurrent(active)) transport.abort(cause)
                        }
                    }
                    active = Connection(address, newGate, KeyLinkerSession(boundTransport, scope, newGate::authorize))
                    connection = active
                    mutableState.value = KeyLinkerState(KeyLinkerPhase.IDENTIFYING, address, busy = true)
                    linkJob = scope.launch { identifyThenPoll(active) }
                }
        }
    }

    private fun isCurrent(active: Connection) = connection === active && link.value.let {
        it.phase == LinkPhase.READY && it.protocol == ConfigurationProtocol.KEYLINKER && it.address == active.address
    }

    private fun update(active: Connection, transform: (KeyLinkerState) -> KeyLinkerState) {
        if (isCurrent(active)) mutableState.update(transform)
    }

    private suspend fun identifyThenPoll(active: Connection) {
        val identified = exclusive.withLock { guarded(active) { identify(active) } } ?: false
        if (!identified || !isCurrent(active) || !state.value.mayRead) return
        refreshBattery(active)
        readSettings(active, quiet = true)
        while (currentCoroutineContext().isActive) { delay(180_000); refreshBattery(active) }
    }

    private suspend fun identify(active: Connection): Boolean {
        val result = active.client.identify()
        if (!isCurrent(active)) return false
        active.gate.assessment = result.assessment
        active.gate.capabilities = result.capabilities
        update(active) { it.copy(phase = if (result.identity == null) KeyLinkerPhase.NO_REPLY else KeyLinkerPhase.IDENTIFIED, identification = result) }
        log("KEYLINKER", result.assessment?.reason ?: "No identity reply")
        return result.identity != null
    }

    /** Runs one exchange series and turns failures into a notice. A transport error drops the link. */
    private suspend fun <T> guarded(active: Connection, block: suspend () -> T): T? {
        if (!isCurrent(active)) return null
        update(active) { it.copy(busy = true) }
        return try { block() }
        catch (cancel: CancellationException) { throw cancel }
        catch (refused: KeyLinkerNotAuthorized) {
            if (isCurrent(active)) {
                log("KEYLINKER", "Refused locally: ${refused.message}")
                notify(Notice("Not sent: ${refused.message}", "Não enviado: ${refused.message}", true))
            }
            null
        } catch (error: Exception) {
            if (isCurrent(active)) {
                log("ERROR", error.message ?: error.javaClass.simpleName)
                notify(Notice("The controller did not answer. Check the connection and try again.", "O controle não respondeu. Verifique a conexão e tente novamente.", true))
            }
            null
        } finally { update(active) { it.copy(busy = false) } }
    }

    fun refreshBatteryAsync() {
        val active = connection ?: return
        scope.launch { refreshBattery(active) }
    }
    suspend fun refreshBattery() {
        refreshBattery(connection ?: return)
    }
    private suspend fun refreshBattery(active: Connection) {
        if (!isCurrent(active) || !state.value.mayRead) return
        exclusive.withLock {
            guarded(active) { active.client.battery() }?.let { gauge -> update(active) { it.copy(battery = gauge, batteryAt = System.currentTimeMillis()) } }
        }
    }

    fun readSettingsAsync() {
        val active = connection ?: return
        scope.launch { readSettings(active, quiet = false) }
    }
    suspend fun readSettings(quiet: Boolean) {
        readSettings(connection ?: return, quiet)
    }
    private suspend fun readSettings(active: Connection, quiet: Boolean) {
        if (!isCurrent(active)) return
        if (!state.value.mayRead) {
            notify(Notice("This controller was identified only. Settings are read from verified X20 and X20 Pro identities.",
                "Este controle foi apenas identificado. Os ajustes são lidos de identidades verificadas do X20 e X20 Pro.", true))
            return
        }
        exclusive.withLock {
            if (!isCurrent(active)) return@withLock
            update(active) { it.copy(phase = KeyLinkerPhase.READING, progress = 0f) }
            val settings = guarded(active) { active.client.readSettings(active.gate.capabilities) { p -> update(active) { it.copy(progress = p) } } }
            update(active) { it.copy(phase = KeyLinkerPhase.IDENTIFIED, settings = settings ?: it.settings, progress = 1f) }
            if (isCurrent(active) && settings != null && !quiet) notify(Notice("Read from the controller. Nothing was changed.", "Lido do controle. Nada foi alterado."))
        }
    }

    /** Builds the frames a write would send and records why it is blocked. Never sends anything. */
    fun preview(feature: KeyLinkerFeature, build: (KeyLinkerSerials) -> KeyLinkerWritePlan) {
        val active = connection?.takeIf(::isCurrent) ?: return
        val current = state.value
        val decision = current.assessment?.writeDecision(feature, current.capabilities)
            ?: KeyLinkerWriteDecision.Blocked(KeyLinkerWriteDecision.Reason.NOT_IDENTIFIED, "Controller not identified yet.")
        val plan = runCatching { build(planSerials) }.getOrElse { error ->
            notify(Notice("Invalid value: ${error.message}", "Valor inválido: ${error.message}", true)); return
        }
        previewConnection = active
        update(active) { it.copy(preview = KeyLinkerPlanPreview(plan, decision, build)) }
    }
    fun clearPreview() { previewConnection = null; mutableState.update { it.copy(preview = null) } }

    fun sendPreviewAsync() {
        val active = previewConnection?.takeIf(::isCurrent) ?: return
        val preview = state.value.preview?.takeIf { it.allowed } ?: return
        clearPreview()
        scope.launch { sendPreview(active, preview) }
    }

    /** Sends the reviewed write the X20CTL way (re-read, send, read back), then reads every setting again. */
    private suspend fun sendPreview(active: Connection, preview: KeyLinkerPlanPreview) {
        val result = exclusive.withLock {
            guarded(active) {
                val plan = preview.build(active.client.serials)
                log("KEYLINKER", "Writing ${plan.summary} (${plan.packets.size} frame(s))")
                active.client.apply(plan).also { log("KEYLINKER", "Write result: $it") }
            }
        } ?: return
        if (!isCurrent(active)) return
        notify(when (result) {
            KeyLinkerWriteResult.Confirmed -> Notice("Saved to the controller and confirmed by reading it back.", "Salvo no controle e confirmado pela releitura.")
            is KeyLinkerWriteResult.Mismatch -> Notice("The controller now reports ${result.readBack}, not what was sent. Check it before trying again.",
                "O controle agora informa ${result.readBack}, não o que foi enviado. Confira antes de tentar de novo.", true)
            KeyLinkerWriteResult.Stale -> Notice("This setting changed on the controller, or could not be read, since you opened it. Nothing was sent.",
                "Este ajuste mudou no controle, ou não pôde ser lido, desde que você o abriu. Nada foi enviado.", true)
            KeyLinkerWriteResult.Unconfirmed -> Notice("Sent, but the controller did not answer the check. Reading the settings again.",
                "Enviado, mas o controle não respondeu à conferência. Lendo os ajustes de novo.", true)
        })
        readSettings(active, quiet = true)
    }
}
