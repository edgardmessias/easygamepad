// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Amjad AAYD
// Evidence table ported from X20CTL x20ctl/validation.py (GPL-3.0-or-later) at revision aac5461.
package br.dev.edgard.easygamepad.protocol.keylinker

enum class KeyLinkerFeature { REMAPS, STICK_CURVES, TRIGGER_CURVES, VIBRATION, IDLE_TIMEOUT, MACROS, TURBO, LIGHTING, BATTERY, CALIBRATE, FACTORY_RESET }

/** Four facts that fail separately: the pad answers a read, acknowledges a write, reads back, and keeps it after power-off. */
data class KeyLinkerProof(val read: Boolean = false, val write: Boolean = false, val readback: Boolean = false,
    val persistence: Boolean = false, val note: String = "")

data class KnownKeyLinkerModel(
    val modelId: String, val product: String, val vid: String, val pid: String,
    val modes: Set<Int>, val firmware: Set<String>, val updaterCode: String, val macroSlots: List<String>,
    /** Evidence recorded by X20CTL on Windows. It is not evidence for the Android transport. */
    val x20ctlProof: Map<KeyLinkerFeature, KeyLinkerProof>,
)

/** How far an identified KeyLinker controller may be trusted. Matches on the reported identity only. */
enum class KeyLinkerClassification {
    /** Product, operating mode and firmware all match a hardware-validated record. */
    VERIFIED,
    /** Known product and mode, firmware never tested. */
    KNOWN_MODEL_UNVALIDATED_FIRMWARE,
    /** Known product in an operating mode never tested; the mode can change the protocol, so identification only. */
    KNOWN_MODEL_UNVALIDATED_MODE,
    /** Speaks KeyLinker but its identity matches no record; identified and shown, nothing else. */
    UNKNOWN_MODEL,
}

sealed interface KeyLinkerWriteDecision {
    data object Allowed : KeyLinkerWriteDecision
    data class Blocked(val reason: Reason, val detail: String) : KeyLinkerWriteDecision
    enum class Reason { NOT_IDENTIFIED, UNKNOWN_MODEL, UNVALIDATED_MODE, NOT_EXPOSED, NO_HARDWARE_PROOF, NO_READBACK_PROOF, DISRUPTIVE }
}

data class KeyLinkerAssessment(val classification: KeyLinkerClassification, val known: KnownKeyLinkerModel?, val reason: String) {
    /** Reads beyond identification go only to proven products, as in X20CTL. */
    val mayRead get() = known != null && classification != KeyLinkerClassification.KNOWN_MODEL_UNVALIDATED_MODE &&
        classification != KeyLinkerClassification.UNKNOWN_MODEL

    fun proof(feature: KeyLinkerFeature) = known?.x20ctlProof?.get(feature) ?: KeyLinkerProof()

    /**
     * Whether EasyGamepad may send a settings write. Mirrors X20CTL's `Assessment.may_write`: the product and
     * operating mode must match a hardware record (firmware may differ, as X20CTL's "known_product" allows), the
     * capability record must expose the feature, and X20CTL must have recorded the write *and* a read-back on
     * real hardware, because EasyGamepad confirms every write by reading the record back
     * ([KeyLinkerClient.apply]). Calibration and factory reset stay disabled here even though X20CTL allows them.
     */
    fun writeDecision(feature: KeyLinkerFeature, capabilities: KeyLinkerCapabilities?): KeyLinkerWriteDecision {
        fun blocked(reason: KeyLinkerWriteDecision.Reason, detail: String) = KeyLinkerWriteDecision.Blocked(reason, detail)
        if (feature in KeyLinkerEvidence.DISRUPTIVE) return blocked(KeyLinkerWriteDecision.Reason.DISRUPTIVE,
            "Calibration and factory reset stay disabled in EasyGamepad.")
        val known = known ?: return blocked(KeyLinkerWriteDecision.Reason.UNKNOWN_MODEL, reason)
        if (classification == KeyLinkerClassification.KNOWN_MODEL_UNVALIDATED_MODE) return blocked(KeyLinkerWriteDecision.Reason.UNVALIDATED_MODE, reason)
        if (capabilities != null && !KeyLinkerEvidence.exposed(feature, capabilities)) return blocked(KeyLinkerWriteDecision.Reason.NOT_EXPOSED,
            "The controller's capability record does not expose this setting.")
        if (!proof(feature).write) return blocked(KeyLinkerWriteDecision.Reason.NO_HARDWARE_PROOF,
            "No X20CTL hardware record proves this write on the ${known.product}.")
        if (!proof(feature).readback) return blocked(KeyLinkerWriteDecision.Reason.NO_READBACK_PROOF,
            "X20CTL has no read-back record for this write on the ${known.product}, so EasyGamepad could not confirm it.")
        return KeyLinkerWriteDecision.Allowed
    }
}

object KeyLinkerEvidence {
    private val SETTING = KeyLinkerProof(read = true, write = true, readback = true,
        note = "Round trip on an owner's controller with X20CTL; survival across power-off not recorded.")

    val X20 = KnownKeyLinkerModel("easysmx-x20", "EasySMX X20", "1013", "2009", setOf(2), setOf("1.23"), "2091",
        listOf("M1", "M2", "M3", "M4"), mapOf(
            KeyLinkerFeature.REMAPS to SETTING, KeyLinkerFeature.STICK_CURVES to SETTING, KeyLinkerFeature.TRIGGER_CURVES to SETTING,
            KeyLinkerFeature.VIBRATION to SETTING, KeyLinkerFeature.IDLE_TIMEOUT to SETTING,
            KeyLinkerFeature.MACROS to KeyLinkerProof(true, true, true, note = "M4 round trip with chords and per-step timings."),
            KeyLinkerFeature.BATTERY to KeyLinkerProof(read = true, note = "Power query; the pad is returned to normal mode after."),
            KeyLinkerFeature.CALIBRATE to KeyLinkerProof(write = true, note = "Accepted; sticks unchanged; gyro effect unverified."),
            KeyLinkerFeature.FACTORY_RESET to KeyLinkerProof(write = true, note = "Generation 2 payload."),
        ))
    val X20_PRO = KnownKeyLinkerModel("easysmx-x20-pro", "EasySMX X20 Pro", "1020", "2006", setOf(6), setOf("1.44"), "2395",
        listOf("M1", "M2", "M3", "M4", "M5", "M6"), mapOf(
            KeyLinkerFeature.REMAPS to SETTING, KeyLinkerFeature.STICK_CURVES to SETTING, KeyLinkerFeature.TRIGGER_CURVES to SETTING,
            KeyLinkerFeature.VIBRATION to SETTING, KeyLinkerFeature.IDLE_TIMEOUT to SETTING,
            KeyLinkerFeature.MACROS to KeyLinkerProof(true, true, true, note = "M1-M6 round trip with chords and per-step timings."),
            KeyLinkerFeature.BATTERY to KeyLinkerProof(read = true, note = "Power query; the pad is returned to normal mode after."),
            KeyLinkerFeature.TURBO to KeyLinkerProof(true, true, true, note = "Listed in X20CTL validation; record layout not decoded here."),
            KeyLinkerFeature.LIGHTING to KeyLinkerProof(read = true, note = "X20CTL verifies only the first colour triplet in Solid mode; not in validation.py."),
        ))
    val KNOWN = listOf(X20, X20_PRO)

    val DISRUPTIVE = setOf(KeyLinkerFeature.CALIBRATE, KeyLinkerFeature.FACTORY_RESET)

    /** Sent to any controller that exposes the KeyLinker service after the user chose it: how the official app learns what it is. */
    val IDENTIFY_OPCODES = setOf(KeyLinkerOp.READ_NAME, KeyLinkerOp.READ_VID_PID_VERSION, KeyLinkerOp.HOST_GUID, KeyLinkerOp.HOST_MENU)
    val POWER_PAYLOAD = byteArrayOf(0x02, 0xDF.toByte(), 0xAB.toByte())

    fun model(id: String?) = KNOWN.firstOrNull { it.modelId == id }

    fun assess(identity: KeyLinkerIdentity): KeyLinkerAssessment {
        val known = KNOWN.firstOrNull { it.vid == identity.vid && it.pid == identity.pid }
            ?: return KeyLinkerAssessment(KeyLinkerClassification.UNKNOWN_MODEL, null,
                "Unrecognised KeyLinker controller (${identity.vid}:${identity.pid}, firmware ${identity.firmware}, mode ${identity.mode ?: "?"}). Identified only; nothing else is sent.")
        if (identity.mode !in known.modes) return KeyLinkerAssessment(KeyLinkerClassification.KNOWN_MODEL_UNVALIDATED_MODE, known,
            "${known.product} in operating mode ${identity.mode ?: "unknown"}, which has not been tested. Identification only.")
        if (identity.firmware !in known.firmware) return KeyLinkerAssessment(KeyLinkerClassification.KNOWN_MODEL_UNVALIDATED_FIRMWARE, known,
            "${known.product} on firmware ${identity.firmware}; proven on ${known.firmware.sorted().joinToString()}. Settings can be read.")
        return KeyLinkerAssessment(KeyLinkerClassification.VERIFIED, known, "${known.product}, firmware ${identity.firmware}, mode ${identity.mode}: matches the hardware record.")
    }

    fun exposed(feature: KeyLinkerFeature, caps: KeyLinkerCapabilities) = when (feature) {
        KeyLinkerFeature.REMAPS -> caps.changeKey != 0
        KeyLinkerFeature.STICK_CURVES -> caps.sticks != 0
        KeyLinkerFeature.TRIGGER_CURVES -> caps.triggers != 0
        KeyLinkerFeature.VIBRATION, KeyLinkerFeature.IDLE_TIMEOUT -> caps.motors != 0
        KeyLinkerFeature.MACROS -> caps.macros != 0
        KeyLinkerFeature.TURBO -> caps.turbo != 0
        KeyLinkerFeature.LIGHTING -> caps.lighting != 0
        KeyLinkerFeature.BATTERY, KeyLinkerFeature.CALIBRATE, KeyLinkerFeature.FACTORY_RESET -> true
    }

    enum class RequestKind { IDENTIFY, READ, WRITE }

    /** Classifies one outgoing plain frame, as X20CTL's request_feature does. */
    fun classify(opcode: Int, payload: ByteArray): Pair<RequestKind, KeyLinkerFeature?> = when {
        opcode in IDENTIFY_OPCODES -> RequestKind.IDENTIFY to null
        (opcode == KeyLinkerOp.SET_MODE || opcode == KeyLinkerOp.NORMAL_MODE) && payload.contentEquals(POWER_PAYLOAD) -> RequestKind.READ to KeyLinkerFeature.BATTERY
        opcode == KeyLinkerOp.SET_MODE -> RequestKind.WRITE to KeyLinkerFeature.CALIBRATE
        opcode == KeyLinkerOp.RECOVER -> RequestKind.WRITE to KeyLinkerFeature.FACTORY_RESET
        opcode == KeyLinkerOp.WRITE_STICK -> RequestKind.WRITE to KeyLinkerFeature.STICK_CURVES
        opcode == KeyLinkerOp.WRITE_TRIGGER -> RequestKind.WRITE to KeyLinkerFeature.TRIGGER_CURVES
        opcode == KeyLinkerOp.WRITE_MOTOR -> RequestKind.WRITE to KeyLinkerFeature.VIBRATION
        opcode == KeyLinkerOp.WRITE_TURBO -> RequestKind.WRITE to KeyLinkerFeature.TURBO
        opcode == KeyLinkerOp.WRITE_MACRO -> RequestKind.WRITE to KeyLinkerFeature.MACROS
        opcode == KeyLinkerOp.WRITE_CHANGEKEY -> RequestKind.WRITE to KeyLinkerFeature.REMAPS
        opcode == KeyLinkerOp.WRITE_LIGHTING -> RequestKind.WRITE to KeyLinkerFeature.LIGHTING
        opcode in 0xB1..0xB7 -> RequestKind.READ to null
        else -> RequestKind.WRITE to null
    }
}
