// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.protocol

enum class X10ApplyOutcomeKind { MATCHED, MISMATCH, UNCONFIRMED }

/** Pure read-back summary; macros and power-cycle persistence are never marked verified. */
data class X10ApplyPresentation(
    val kind: X10ApplyOutcomeKind,
    val mismatchedFields: Set<X10SettingField> = emptySet(),
    val matchedFields: Set<X10SettingField> = emptySet(),
    val fieldNotes: Map<X10SettingField, String> = emptyMap(),
    val unconfirmedReason: String? = null,
    val macrosChecked: Boolean = false,
    val persistenceChecked: Boolean = false,
)

private val readableFields = X10SettingField.entries.toSet()

fun x10ApplyPresentation(requested: ControllerConfig, result: X10ApplyResult): X10ApplyPresentation = when (result) {
    is X10ApplyResult.Matched -> X10ApplyPresentation(
        kind = X10ApplyOutcomeKind.MATCHED,
        matchedFields = readableFields,
    )
    is X10ApplyResult.Mismatch -> {
        val mismatched = result.fields
        X10ApplyPresentation(
            kind = X10ApplyOutcomeKind.MISMATCH,
            mismatchedFields = mismatched,
            matchedFields = readableFields - mismatched,
            fieldNotes = mismatched.associateWith { field -> mismatchNote(requested, result.settings, field) },
        )
    }
    is X10ApplyResult.Unconfirmed -> X10ApplyPresentation(
        kind = X10ApplyOutcomeKind.UNCONFIRMED,
        unconfirmedReason = result.reason,
    )
}

private fun customMappings(values: Map<PadKey, PadKey>) = values.filterNot { (from, to) -> from in PadKey.outputs && from == to }

private fun mismatchNote(requested: ControllerConfig, settings: ControllerSettings, field: X10SettingField): String = when (field) {
    X10SettingField.BUTTONS -> {
        val sent = customMappings(requested.mappings)
        val read = customMappings(settings.mappings)
        val onlySent = sent.filterKeys { it !in read || read[it] != sent[it] }.map { (from, to) -> "${from.label}→${to.label}" }
        val onlyRead = read.filterKeys { it !in sent || sent[it] != read[it] }.map { (from, to) -> "${from.label}→${to.label}" }
        buildString {
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
