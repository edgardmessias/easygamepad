// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Amjad AAYD
// Request/response behaviour follows X20CTL x20ctl/client.py and desktop-dotnet LightingBleClient.cs (revision aac5461).
package br.dev.edgard.easygamepad.protocol.keylinker

import br.dev.edgard.easygamepad.protocol.GamepadTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

class KeyLinkerNotAuthorized(message: String) : IllegalStateException(message)

/**
 * Decides, frame by frame, what may be sent to a KeyLinker peripheral the user chose.
 * Identification questions are always allowed; further reads need an identity that [KeyLinkerAssessment.mayRead];
 * writes need [KeyLinkerAssessment.writeDecision] to be Allowed.
 */
class KeyLinkerGate {
    @Volatile var assessment: KeyLinkerAssessment? = null
    @Volatile var capabilities: KeyLinkerCapabilities? = null

    fun authorize(opcode: Int, payload: ByteArray) {
        val (kind, feature) = KeyLinkerEvidence.classify(opcode, payload)
        when (kind) {
            KeyLinkerEvidence.RequestKind.IDENTIFY -> Unit
            KeyLinkerEvidence.RequestKind.READ -> if (assessment?.mayRead != true)
                throw KeyLinkerNotAuthorized(assessment?.reason ?: "Controller not identified yet")
            KeyLinkerEvidence.RequestKind.WRITE -> {
                val current = assessment ?: throw KeyLinkerNotAuthorized("Controller not identified yet")
                val decision = feature?.let { current.writeDecision(it, capabilities) }
                    ?: throw KeyLinkerNotAuthorized("Opcode 0x%02X is not a recognised settings write".format(opcode))
                if (decision is KeyLinkerWriteDecision.Blocked) throw KeyLinkerNotAuthorized(decision.detail)
            }
        }
    }
}

/**
 * One KeyLinker exchange at a time over an established, subscribed GATT link.
 *
 * A reply is accepted only when it is a valid RESPONSE (0x19) frame whose CRC checks, whose
 * declared length equals the notification size and whose serial echoes the request.
 * Anything else (for example the 0x1F input stream the power query starts) is ignored.
 *
 * A request that times out returns null: silence is not a result. For writes this matches X20CTL's `_send`,
 * which carries on and lets the read-back decide whether the change landed ([KeyLinkerClient.apply]).
 * Every frame passes [authorize] before it is written, whichever method built it.
 */
class KeyLinkerSession(
    private val transport: GamepadTransport,
    scope: CoroutineScope,
    private val authorize: (opcode: Int, payload: ByteArray) -> Unit,
    private val timeoutMs: Long = 2500,
) {
    private val lock = Mutex()
    private data class Pending(val serial: Int, val reply: CompletableDeferred<ByteArray>)
    @Volatile private var pending: Pending? = null
    @Volatile var closed = false; private set
    private val mutableFrames = MutableSharedFlow<KeyLinkerFrame>(extraBufferCapacity = 64)
    /** Every decodable frame, for diagnostics. */
    val frames = mutableFrames.asSharedFlow()

    private val collector = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        transport.notifications.collect { raw -> accept(raw) }
    }

    internal fun accept(raw: ByteArray) {
        val frame = runCatching { KeyLinkerCodec.tryParse(raw) }.getOrNull() ?: return
        mutableFrames.tryEmit(frame)
        if (!frame.crcValid || frame.opcode != KeyLinkerOp.RESPONSE || frame.declaredLength != raw.size) return
        val waiting = pending ?: return
        if (frame.serial == waiting.serial) waiting.reply.complete(frame.payload)
    }

    /** Sends one already-built frame and waits for the reply with the same serial. */
    suspend fun exchange(packet: ByteArray, expectReply: Boolean = true): ByteArray? = lock.withLock {
        check(!closed) { "KeyLinker session closed" }
        val plain = KeyLinkerCodec.unscramble(packet)
        val opcode = plain[0].toInt() and 255
        val payload = plain.copyOfRange(4, plain.size - 1)
        authorize(opcode, payload)
        val reply = CompletableDeferred<ByteArray>()
        if (expectReply) pending = Pending(plain[2].toInt() and 255, reply)
        try {
            if (!expectReply) { transport.write(packet); return@withLock null }
            withTimeout(timeoutMs) { transport.write(packet); reply.await() }
        } catch (timeout: TimeoutCancellationException) {
            null
        } catch (cancel: CancellationException) {
            close(cancel); throw cancel
        } catch (error: Exception) {
            close(error); transport.abort(error); throw error
        } finally { pending = null }
    }

    fun close(cause: Throwable = IllegalStateException("Disconnected")) {
        closed = true
        pending?.reply?.completeExceptionally(cause)
        collector.cancel()
    }
}

/** A value read from the controller, kept apart from "not exposed" and "no usable reply". */
sealed interface Reading<out T> {
    data class Value<T>(val value: T) : Reading<T>
    /** The capability record says this is not configurable over KeyLinker. */
    data object Unsupported : Reading<Nothing>
    /** Asked, but no complete or decodable reply arrived. */
    data class Unreadable(val reason: String) : Reading<Nothing>
    /** Not asked: the controller's identity does not permit reads. */
    data object NotRequested : Reading<Nothing>
    val valueOrNull: T? get() = (this as? Value<T>)?.value
}

data class KeyLinkerIdentification(
    val identity: KeyLinkerIdentity?,
    val guid: KeyLinkerHostGuid?,
    val capabilities: KeyLinkerCapabilities?,
    val assessment: KeyLinkerAssessment?,
    val findings: List<String>,
)

data class KeyLinkerRemaps(val sources: List<Int>, val sourcesFromPad: Boolean, val targets: Map<Int, Int>)

data class KeyLinkerSettings(
    val sticks: Reading<List<KeyLinkerCurve>>,
    val triggers: Reading<List<KeyLinkerCurve>>,
    val motor: Reading<KeyLinkerMotorRecord>,
    val remaps: Reading<KeyLinkerRemaps>,
    val macros: Reading<Map<String, KeyLinkerMacro?>>,
    val turboRaw: Reading<String>,
    val lighting: Reading<List<KeyLinkerLightingEntry>>,
    val readAt: Long,
)

/**
 * What a write should leave on the controller, and what it was edited from. [KeyLinkerClient.apply] re-reads the
 * record before sending (refusing if it changed since the edit began) and after (confirming the change landed),
 * as X20CTL's set_curves, set_remapping and set_vibration do.
 */
sealed interface KeyLinkerExpectation {
    data class Curves(val sticks: Boolean, val before: List<KeyLinkerCurve>?, val after: List<KeyLinkerCurve>) : KeyLinkerExpectation
    data class Motor(val before: KeyLinkerMotorRecord?, val after: KeyLinkerMotorRecord) : KeyLinkerExpectation
    /** Maps of changed sources only, as [KeyLinkerRemap.parse] reports them. */
    data class Remaps(val sources: List<Int>, val before: Map<Int, Int>?, val after: Map<Int, Int>) : KeyLinkerExpectation
    /** A null or empty macro clears the slot. */
    data class Macro(val slotIndex: Int, val after: KeyLinkerMacro?) : KeyLinkerExpectation
}

/** The frames a write sends and what it should leave behind. Building a plan never sends anything. */
data class KeyLinkerWritePlan(val feature: KeyLinkerFeature, val packets: List<ByteArray>, val summary: String, val expected: KeyLinkerExpectation)

sealed interface KeyLinkerWriteResult {
    /** Read back and equal to what was written. */
    data object Confirmed : KeyLinkerWriteResult
    /** The controller now reports something else, described by [readBack]. */
    data class Mismatch(val readBack: String) : KeyLinkerWriteResult
    /** The record changed since it was read for editing; nothing was sent. */
    data object Stale : KeyLinkerWriteResult
    /** No usable reply when reading the record back; the outcome is unknown. */
    data object Unconfirmed : KeyLinkerWriteResult
}

/** KeyLinker driver for one session. Every frame, read or write, still passes the session's gate. */
class KeyLinkerClient(private val session: KeyLinkerSession, val serials: KeyLinkerSerials = KeyLinkerSerials(),
                      private val chunkPauseMs: Long = 250) {
    suspend fun query(opcode: Int, payload: ByteArray = byteArrayOf()): ByteArray? =
        session.exchange(KeyLinkerCodec.build(opcode, payload, serials.nextSerial()))

    /** Query and follow continuation positions while the record is incomplete (at most 8, as X20CTL). */
    suspend fun readBody(opcode: Int, payload: ByteArray): KeyLinkerBody? {
        val first = query(opcode, payload) ?: return null
        if (first.isEmpty()) return null
        var body = KeyLinkerBody.unwrap(first)
        var index = 0
        while (!body.complete && body.data.size < body.declared && index < 8) {
            index++
            val next = if (payload.size >= 2) byteArrayOf(index.toByte()) + payload.copyOfRange(1, payload.size) else byteArrayOf(index.toByte())
            val more = query(opcode, next) ?: break
            body = KeyLinkerBody(body.declared, body.data + more)
        }
        return body
    }

    /** The official app's opening questions: identity, updater id, capability record. Nothing is written. */
    suspend fun identify(): KeyLinkerIdentification {
        val identity = query(KeyLinkerOp.READ_VID_PID_VERSION)?.let { runCatching { KeyLinkerIdentity.parse(it) }.getOrNull() }
        val guid = readBody(KeyLinkerOp.HOST_GUID, byteArrayOf(0))?.let { runCatching { KeyLinkerHostGuid.parse(it) }.getOrNull() }
        val caps = readBody(KeyLinkerOp.HOST_MENU, byteArrayOf(0, 1))?.let { runCatching { KeyLinkerCapabilities.parse(it) }.getOrNull() }
        val assessment = identity?.let(KeyLinkerEvidence::assess)
        return KeyLinkerIdentification(identity, guid, caps, assessment, findings(identity, guid, caps, assessment))
    }

    /** Power query. It starts the 0x1F input stream, so NORMAL_MODE is always sent afterwards. */
    suspend fun battery(): KeyLinkerBattery? {
        try {
            val reply = session.exchange(KeyLinkerCodec.build(KeyLinkerOp.SET_MODE, KeyLinkerEvidence.POWER_PAYLOAD, serials.nextSerial())) ?: return null
            return runCatching { KeyLinkerBattery.parse(KeyLinkerBody.unwrap(reply)) }.getOrNull()
        } finally {
            if (!session.closed) runCatching {
                session.exchange(KeyLinkerCodec.build(KeyLinkerOp.NORMAL_MODE, KeyLinkerEvidence.POWER_PAYLOAD, serials.nextSerial()), expectReply = false)
            }
        }
    }

    suspend fun readSettings(caps: KeyLinkerCapabilities?, progress: (Float) -> Unit = {}): KeyLinkerSettings {
        suspend fun <T> read(exposed: Boolean, block: suspend () -> T?): Reading<T> = when {
            caps == null -> Reading.Unreadable("No capability record")
            !exposed -> Reading.Unsupported
            else -> try { block()?.let { Reading.Value(it) } ?: Reading.Unreadable("No complete reply") }
                catch (error: IllegalArgumentException) { Reading.Unreadable(error.message ?: "Undecodable reply") }
        }
        val sticks = read(caps?.sticks != 0) { readBody(KeyLinkerOp.HOST_STICK, byteArrayOf(0))?.let { KeyLinkerCurve.parseRecord(it, KeyLinkerCurve.STICK_MAX_PROGRESS) } }
        progress(.15f)
        val triggers = read(caps?.triggers != 0) { readBody(KeyLinkerOp.HOST_TRIGGER, byteArrayOf(0))?.let { KeyLinkerCurve.parseRecord(it, KeyLinkerCurve.TRIGGER_MAX_PROGRESS) } }
        progress(.3f)
        val motor = read(caps?.motors != 0) { readBody(KeyLinkerOp.HOST_MOTOR, byteArrayOf(0))?.let(KeyLinkerMotorRecord::parse) }
        progress(.45f)
        val remaps = read(caps?.changeKey != 0) {
            val listed = readBody(KeyLinkerOp.HOST_MENU, byteArrayOf(0, 3))?.let { runCatching { KeyLinkerKeyList.decode(it.data) }.getOrNull() }
            val sources = listed ?: KeyLinkerKeyList.X20_CHANGEKEY_SOURCES
            readBody(KeyLinkerOp.HOST_CHANGEKEY, byteArrayOf(0))?.let { body ->
                KeyLinkerRemaps(sources, listed != null, if (body.declared == 0) emptyMap() else KeyLinkerRemap.parse(sources, body.data))
            }
        }
        progress(.6f)
        val macros = read(caps?.macros != 0) { readMacros(requireNotNull(caps)) }
        progress(.85f)
        val turbo = read(caps?.turbo != 0) { readBody(KeyLinkerOp.HOST_TURBO, byteArrayOf(0))?.takeIf { it.complete }?.data?.joinToString("") { "%02x".format(it) } }
        val lighting = read(caps?.lighting != 0) { readBody(KeyLinkerOp.HOST_LIGHTING, byteArrayOf(0))?.let(KeyLinkerLightingEntry::parseRecord) }
        progress(1f)
        return KeyLinkerSettings(sticks, triggers, motor, remaps, macros, turbo, lighting, System.currentTimeMillis())
    }

    /**
     * Follows X20CTL client.read_macro, whose M1-M6 round trips are recorded in validation.py: HOST_MACRO
     * `[position, slot]`, header counted in bytes. X20CTL's C# reader notes that asking for the macro
     * limits (B0 kind 6) first makes the header count entries instead; this reader never asks kind 6.
     */
    private suspend fun readMacros(caps: KeyLinkerCapabilities): Map<String, KeyLinkerMacro?> {
        val layout = readBody(KeyLinkerOp.HOST_MENU, byteArrayOf(0, 5))?.let { runCatching { KeyLinkerMaskLayout.from(KeyLinkerKeyList.decode(it.data)) }.getOrNull() }
            ?: KeyLinkerMaskLayout.X20
        lastLayout = layout
        return caps.macroSlots.associateWith { slot -> readMacroSlot(KeyLinkerCapabilities.SLOT_NAMES.indexOf(slot)) }
    }

    private suspend fun readMacroSlot(index: Int): KeyLinkerMacro? {
        val slot = KeyLinkerCapabilities.SLOT_NAMES[index]
        val first = query(KeyLinkerOp.HOST_MACRO, byteArrayOf(0, index.toByte())) ?: throw IllegalArgumentException("No reply from $slot")
        require(first.isNotEmpty())
        val header = first[0].toInt() and 255
        if (header == 0) return null
        val expected = header + 1
        var data = first
        var position = 1
        while (data.size < expected && position < 16) {
            data += query(KeyLinkerOp.HOST_MACRO, byteArrayOf(position.toByte(), index.toByte())) ?: break
            position++
        }
        require(data.size >= expected && header >= 2 && (header - 2) % KeyLinkerMacroStep.SIZE == 0) { "Incomplete macro record in $slot" }
        return KeyLinkerMacro.parse(data.copyOf(expected))
    }

    /** Distinguishes "the slot is empty" from "no usable reply" (null). */
    private data class Slot(val macro: KeyLinkerMacro?)

    /** Reads the record a write touches, in the expectation's shape. Null when no usable reply came. */
    private suspend fun current(expected: KeyLinkerExpectation): Any? = try {
        when (expected) {
            is KeyLinkerExpectation.Curves -> readBody(if (expected.sticks) KeyLinkerOp.HOST_STICK else KeyLinkerOp.HOST_TRIGGER, byteArrayOf(0))
                ?.let { KeyLinkerCurve.parseRecord(it, if (expected.sticks) KeyLinkerCurve.STICK_MAX_PROGRESS else KeyLinkerCurve.TRIGGER_MAX_PROGRESS) }
            is KeyLinkerExpectation.Motor -> readBody(KeyLinkerOp.HOST_MOTOR, byteArrayOf(0))?.let(KeyLinkerMotorRecord::parse)
            is KeyLinkerExpectation.Remaps -> readBody(KeyLinkerOp.HOST_CHANGEKEY, byteArrayOf(0))
                ?.let { body -> if (body.declared == 0) emptyMap() else KeyLinkerRemap.parse(expected.sources, body.data) }
            is KeyLinkerExpectation.Macro -> Slot(readMacroSlot(expected.slotIndex))
        }
    } catch (error: IllegalArgumentException) { null }

    /**
     * Sends a write the gate allows, the way X20CTL does: re-read the record and stop if it changed since it was
     * read for editing; send each frame, pausing between chunks; then read the record back and compare.
     */
    suspend fun apply(plan: KeyLinkerWritePlan): KeyLinkerWriteResult {
        val expected = plan.expected
        val before: Any? = when (expected) {
            is KeyLinkerExpectation.Curves -> expected.before
            is KeyLinkerExpectation.Motor -> expected.before
            is KeyLinkerExpectation.Remaps -> expected.before
            is KeyLinkerExpectation.Macro -> null
        }
        if (before != null && current(expected) != before) return KeyLinkerWriteResult.Stale
        plan.packets.forEachIndexed { index, packet ->
            if (index > 0) delay(chunkPauseMs)
            session.exchange(packet)
        }
        val after = current(expected) ?: return KeyLinkerWriteResult.Unconfirmed
        val wanted: Any = when (expected) {
            is KeyLinkerExpectation.Curves -> expected.after
            is KeyLinkerExpectation.Motor -> expected.after
            is KeyLinkerExpectation.Remaps -> expected.after
            is KeyLinkerExpectation.Macro -> Slot(expected.after?.takeIf { it.steps.isNotEmpty() })
        }
        // Macros compare their steps and loop only: the pad may fill the trigger byte itself.
        val same = if (after is Slot && wanted is Slot) after.macro?.steps == wanted.macro?.steps && after.macro?.loopIntervalMs == wanted.macro?.loopIntervalMs
            else after == wanted
        return if (same) KeyLinkerWriteResult.Confirmed else KeyLinkerWriteResult.Mismatch(describe(after))
    }

    private fun describe(value: Any): String = when (value) {
        is Slot -> value.macro?.describe(lastLayout) ?: "empty"
        is KeyLinkerMotorRecord -> "${value.leftPercent}% / ${value.rightPercent}%" +
            (runCatching { value.shutdownMinutes }.getOrNull()?.let { ", idle $it min" } ?: "")
        is List<*> -> value.filterIsInstance<KeyLinkerCurve>().joinToString("; ") { "${it.innerDeadzone}-${it.outerDeadzone} ${it.point1} ${it.point2}" }
        is Map<*, *> -> value.entries.joinToString { "${KeyLinkerKey.label(it.key as Int)}→${KeyLinkerKey.label(it.value as Int)}" }.ifEmpty { "no remaps" }
        else -> value.toString()
    }

    /** The macro mask layout reported by the last [readSettings], or the X20 layout. */
    var lastLayout: KeyLinkerMaskLayout = KeyLinkerMaskLayout.X20; private set

    companion object {
        fun findings(identity: KeyLinkerIdentity?, guid: KeyLinkerHostGuid?, caps: KeyLinkerCapabilities?, assessment: KeyLinkerAssessment?) = buildList {
            val known = assessment?.known
            if (identity == null) add("No identity reply (0x91). Silence is not a result; reconnect and try again.")
            if (known != null && guid != null) add(if (guid.modelCode == known.updaterCode) "The updater id names the same model (${guid.model ?: guid.modelCode})."
                else "The updater id says ${guid.model ?: guid.modelCode}, not ${known.product}.")
            if (known != null && caps != null) add(if (caps.macroSlots == known.macroSlots) "Macro slots match the recorded ${known.product}: ${caps.macroSlots.joinToString()}."
                else "Macro slots differ from the recorded ${known.product}: ${caps.macroSlots.ifEmpty { listOf("none") }.joinToString()}.")
            if (identity != null && guid != null && identity.firmware != guid.version) add("Firmware reads ${identity.firmware} from the identity reply and ${guid.version} from the updater id.")
        }

        /** Builds the exact frames X20CTL sends for a motor-record write (vibration and idle timeout share it). */
        fun planMotor(record: KeyLinkerMotorRecord, serials: KeyLinkerSerials, feature: KeyLinkerFeature, summary: String,
                      before: KeyLinkerMotorRecord? = null, nonce: () -> Int = { kotlin.random.Random.nextInt(256) }): KeyLinkerWritePlan {
            val payload = record.payload()
            val serial = KeyLinkerCodec.saveButtonSerial(0, serials.nextSave())
            return KeyLinkerWritePlan(feature, listOf(KeyLinkerCodec.build(KeyLinkerOp.WRITE_MOTOR, payload, serial,
                KeyLinkerCodec.hostLength(payload.size, serials.nextHost()), nonce())), summary, KeyLinkerExpectation.Motor(before, record))
        }

        fun planCurves(sticks: Boolean, channels: List<KeyLinkerCurve>, serials: KeyLinkerSerials, before: List<KeyLinkerCurve>? = null,
                       nonce: () -> Int = { kotlin.random.Random.nextInt(256) }): KeyLinkerWritePlan {
            val payload = KeyLinkerCurve.buildRecord(channels)
            val serial = KeyLinkerCodec.saveButtonSerial(0, serials.nextSave())
            return KeyLinkerWritePlan(if (sticks) KeyLinkerFeature.STICK_CURVES else KeyLinkerFeature.TRIGGER_CURVES,
                listOf(KeyLinkerCodec.build(if (sticks) KeyLinkerOp.WRITE_STICK else KeyLinkerOp.WRITE_TRIGGER, payload, serial,
                    KeyLinkerCodec.hostLength(payload.size, serials.nextHost()), nonce())), if (sticks) "Stick curves" else "Trigger curves",
                KeyLinkerExpectation.Curves(sticks, before, channels))
        }

        fun planRemaps(sources: List<Int>, changes: Map<Int, Int>, serials: KeyLinkerSerials, before: Map<Int, Int>? = null,
                       nonce: () -> Int = { kotlin.random.Random.nextInt(256) }): KeyLinkerWritePlan {
            require(changes.keys.none { key -> KeyLinkerKey.TARGET_ONLY.any { it.code == key } }) { "View and Menu are targets only on this controller" }
            val payload = KeyLinkerRemap.buildPayload(sources, sources.map { changes[it] ?: it })
            val packets = (payload.indices step KeyLinkerRemap.CHUNK_SIZE).mapIndexed { chunk, offset ->
                val piece = payload.copyOfRange(offset, minOf(offset + KeyLinkerRemap.CHUNK_SIZE, payload.size))
                KeyLinkerCodec.build(KeyLinkerOp.WRITE_CHANGEKEY, piece, KeyLinkerCodec.saveButtonSerial(chunk, serials.nextSave()),
                    KeyLinkerCodec.hostLength(piece.size, serials.nextHost()), nonce())
            }
            val after = sources.associateWith { changes[it] ?: it }.filter { (from, to) -> from != to }
            return KeyLinkerWritePlan(KeyLinkerFeature.REMAPS, packets, "Button remapping", KeyLinkerExpectation.Remaps(sources, before, after))
        }

        /** Macro writes put the slot in the length field's top bits and the chunk index in the serial (writeHostMacroData). */
        fun planMacro(slotIndex: Int, macro: KeyLinkerMacro?, serials: KeyLinkerSerials, nonce: () -> Int = { kotlin.random.Random.nextInt(256) }): KeyLinkerWritePlan {
            require(slotIndex in 0..7)
            val payload = macro?.payload() ?: KeyLinkerMacro.CLEAR
            val chunks = (payload.size + KeyLinkerMacro.CHUNK_SIZE - 1) / KeyLinkerMacro.CHUNK_SIZE
            require(chunks <= KeyLinkerMacro.MAX_CHUNKS) { "Macro too long" }
            val start = serials.nextSave()
            val packets = (0 until chunks).map { chunk ->
                val piece = payload.copyOfRange(chunk * KeyLinkerMacro.CHUNK_SIZE, minOf((chunk + 1) * KeyLinkerMacro.CHUNK_SIZE, payload.size))
                KeyLinkerCodec.build(KeyLinkerOp.WRITE_MACRO, piece, KeyLinkerCodec.saveButtonSerial(chunk, start + chunk),
                    ((slotIndex and 7) shl 5) or ((piece.size + 5) and 0x1F), nonce())
            }
            return KeyLinkerWritePlan(KeyLinkerFeature.MACROS, packets, "Macro ${KeyLinkerCapabilities.SLOT_NAMES[slotIndex]}",
                KeyLinkerExpectation.Macro(slotIndex, macro))
        }
    }
}
