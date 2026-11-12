// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.protocol

enum class X10ApplyOutcomeKind { MATCHED, MISMATCH, UNCONFIRMED }

/** Stable diagnostic tokens for read-back failures; UI maps these to localized text. */
object X10ApplyReadBackDiagnostics {
    const val TIMED_OUT = "Settings read-back timed out"
    const val FAILED = "Settings read-back failed"
}

/** Structured rear / face button differences for localized UI. */
data class X10ButtonsApplyMismatch(
    val rearMacrosDiffer: Boolean,
    val sentRearMacroTriggers: List<PadKey>,
    val readRearMacroTriggers: List<PadKey>,
    val disabledDiffer: Boolean,
    val sentDisabled: Set<PadKey>,
    val readDisabled: Set<PadKey>,
    val sentMappingDiffs: List<Pair<PadKey, PadKey>>,
    val readMappingDiffs: List<Pair<PadKey, PadKey>>,
    val changedRearSequences: Set<PadKey> = emptySet(),
    val changedRearRepeat: Set<PadKey> = emptySet(),
)

/**
 * Pure read-back summary for apply UI.
 * Full read-back (queries 119, 80, 121) compares face mappings and M1/M2 slots when it completes.
 * Power-cycle persistence is never marked verified.
 */
data class X10ApplyPresentation(
    val kind: X10ApplyOutcomeKind,
    val mismatchedFields: Set<X10SettingField> = emptySet(),
    val matchedFields: Set<X10SettingField> = emptySet(),
    /** English diagnostics for logs and protocol tests. */
    val fieldNotes: Map<X10SettingField, String> = emptyMap(),
    val unconfirmedReason: String? = null,
    /** Upload included M1/M2 macros, rear mappings, or disabled rear paddles. */
    val rearConfigSent: Boolean = false,
    /** Apply ended with a completed settings read-back (matched or mismatched). */
    val readBackCompleted: Boolean = false,
    /** Sent rear settings were compared in a completed read-back. */
    val macrosChecked: Boolean = false,
    val persistenceChecked: Boolean = false,
    val buttonsMismatch: X10ButtonsApplyMismatch? = null,
    val readBackSettings: ControllerSettings? = null,
)

private val readableFields = X10SettingField.entries.toSet()

fun ControllerConfig.rearConfigIncludedInApply(): Boolean =
    macros.any { it.trigger in DeviceProfile.X10.rearButtons } ||
        disabledRearPaddles.isNotEmpty() ||
        mappings.keys.any { it in DeviceProfile.X10.rearButtons }

fun x10ApplyPresentation(requested: ControllerConfig, result: X10ApplyResult): X10ApplyPresentation {
    val rearSent = requested.rearConfigIncludedInApply()
    return when (result) {
        is X10ApplyResult.Matched -> X10ApplyPresentation(
            kind = X10ApplyOutcomeKind.MATCHED,
            matchedFields = readableFields,
            rearConfigSent = rearSent,
            readBackCompleted = true,
            macrosChecked = rearSent,
            readBackSettings = result.settings,
        )
        is X10ApplyResult.Mismatch -> {
            val mismatched = result.fields
            val buttonsDetail = if (X10SettingField.BUTTONS in mismatched) {
                buttonsMismatchDetail(requested, result.settings)
            } else null
            X10ApplyPresentation(
                kind = X10ApplyOutcomeKind.MISMATCH,
                mismatchedFields = mismatched,
                matchedFields = readableFields - mismatched,
                fieldNotes = mismatched.associateWith { field -> mismatchNote(requested, result.settings, field) },
                rearConfigSent = rearSent,
                readBackCompleted = true,
                macrosChecked = rearSent,
                buttonsMismatch = buttonsDetail,
                readBackSettings = result.settings,
            )
        }
        is X10ApplyResult.Unconfirmed -> X10ApplyPresentation(
            kind = X10ApplyOutcomeKind.UNCONFIRMED,
            unconfirmedReason = result.reason,
            rearConfigSent = rearSent,
            readBackCompleted = false,
            macrosChecked = false,
        )
    }
}

private fun customMappings(values: Map<PadKey, PadKey>) = values.filterNot { (from, to) -> from in PadKey.outputs && from == to }

private fun buttonsMismatchDetail(requested: ControllerConfig, settings: ControllerSettings): X10ButtonsApplyMismatch {
    val sent = customMappings(requested.mappings)
    val read = customMappings(settings.mappings)
    val sentRearMacros = requested.macros.filter { it.trigger in DeviceProfile.X10.rearButtons }
    val readRearMacros = settings.macros.filter { it.trigger in DeviceProfile.X10.rearButtons }
    val sentByTrigger = sentRearMacros.associateBy { it.trigger }
    val readByTrigger = readRearMacros.associateBy { it.trigger }
    val commonTriggers = sentByTrigger.keys.intersect(readByTrigger.keys)
    return X10ButtonsApplyMismatch(
        rearMacrosDiffer = sentByTrigger != readByTrigger,
        sentRearMacroTriggers = sentRearMacros.map { it.trigger },
        readRearMacroTriggers = readRearMacros.map { it.trigger },
        disabledDiffer = requested.disabledRearPaddles != settings.disabledRearPaddles,
        sentDisabled = requested.disabledRearPaddles,
        readDisabled = settings.disabledRearPaddles,
        sentMappingDiffs = sent.filterKeys { it !in read || read[it] != sent[it] }.map { (from, to) -> from to to },
        readMappingDiffs = read.filterKeys { it !in sent || sent[it] != read[it] }.map { (from, to) -> from to to },
        changedRearSequences = commonTriggers.filter {
            sentByTrigger.getValue(it).steps != readByTrigger.getValue(it).steps
        }.toSet(),
        changedRearRepeat = commonTriggers.filter {
            sentByTrigger.getValue(it).repeat != readByTrigger.getValue(it).repeat
        }.toSet(),
    )
}

private fun mismatchNote(requested: ControllerConfig, settings: ControllerSettings, field: X10SettingField): String = when (field) {
    X10SettingField.BUTTONS -> {
        val detail = buttonsMismatchDetail(requested, settings)
        buildString {
            if (detail.rearMacrosDiffer) {
                append("macros M1/M2 sent=${detail.sentRearMacroTriggers.size} read=${detail.readRearMacroTriggers.size}; ")
            }
            if (detail.disabledDiffer) {
                append("cleared paddles sent=${detail.sentDisabled.joinToString { it.label }} ")
                append("read=${detail.readDisabled.joinToString { it.label }}; ")
            }
            val onlySent = detail.sentMappingDiffs.map { (from, to) -> "${from.label}→${to.label}" }
            val onlyRead = detail.readMappingDiffs.map { (from, to) -> "${from.label}→${to.label}" }
            if (onlySent.isNotEmpty()) append("sent: ${onlySent.joinToString()}; ")
            if (onlyRead.isNotEmpty()) append("read: ${onlyRead.joinToString()}")
            if (isEmpty()) append("button map differs")
        }
    }
    X10SettingField.STICKS -> "L ${requested.leftStick.min}/${requested.leftStick.max} vs ${settings.leftStick.min}/${settings.leftStick.max}; " +
        "R ${requested.rightStick.min}/${requested.rightStick.max} vs ${settings.rightStick.min}/${settings.rightStick.max}"
    X10SettingField.TRIGGERS -> "LT ${requested.leftTrigger.min}-${requested.leftTrigger.max} vs ${settings.leftTrigger.min}-${settings.leftTrigger.max}; " +
        "RT ${requested.rightTrigger.min}-${requested.rightTrigger.max} vs ${settings.rightTrigger.min}-${settings.rightTrigger.max}"
    X10SettingField.VIBRATION -> "motors ${requested.leftVibration}%/${requested.rightVibration}% vs ${settings.leftVibration}%/${settings.rightVibration}%"
    X10SettingField.TURBO -> "turbo rules differ (order may change on device)"
}
