// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import androidx.compose.runtime.Composable
import br.dev.edgard.easygamepad.protocol.ControllerConfig
import br.dev.edgard.easygamepad.protocol.ControllerSettings
import br.dev.edgard.easygamepad.protocol.X10ApplyReadBackDiagnostics
import br.dev.edgard.easygamepad.protocol.X10ButtonsApplyMismatch
import br.dev.edgard.easygamepad.protocol.X10SettingField

@Composable
fun x10UnconfirmedReasonForUi(reason: String?): String = when (reason) {
    X10ApplyReadBackDiagnostics.TIMED_OUT -> tr(
        "the read-back timed out",
        "a releitura expirou",
    )
    X10ApplyReadBackDiagnostics.FAILED -> tr(
        "the read-back did not complete",
        "a releitura não foi concluída",
    )
    null, "" -> tr(
        "the read-back did not complete",
        "a releitura não foi concluída",
    )
    else -> tr(
        "the read-back did not complete",
        "a releitura não foi concluída",
    )
}

@Composable
fun localizedButtonsMismatch(detail: X10ButtonsApplyMismatch): String = buildString {
    if (detail.rearMacrosDiffer) {
        val sentLabels = detail.sentRearMacroTriggers.joinToString { it.label }
        val sentRear = if (sentLabels.isEmpty()) tr("none", "nenhuma") else sentLabels
        append(
            tr(
                "M1/M2 macros sent: $sentRear. ",
                "Macros M1/M2 enviadas: $sentRear. ",
            ),
        )
        val readLabels = detail.readRearMacroTriggers.joinToString { it.label }
        val readRear = if (readLabels.isEmpty()) tr("none", "nenhuma") else readLabels
        append(tr("Read back: $readRear. ", "Na releitura: $readRear. "))
        if (detail.changedRearSequences.isNotEmpty()) {
            val paddles = detail.changedRearSequences.joinToString { it.label }
            append(tr("Different sequence or timing: $paddles. ", "Sequência ou tempos diferentes: $paddles. "))
        }
        if (detail.changedRearRepeat.isNotEmpty()) {
            val paddles = detail.changedRearRepeat.joinToString { it.label }
            append(tr("Different repeat setting: $paddles. ", "Repetição diferente: $paddles. "))
        }
    }
    if (detail.disabledDiffer) {
        val sentDisabledLabels = detail.sentDisabled.joinToString { it.label }
        val sentDisabled = if (sentDisabledLabels.isEmpty()) tr("none", "nenhum") else sentDisabledLabels
        append(tr("Disabled paddles sent: $sentDisabled. ", "Paddles desativados enviados: $sentDisabled. "))
        val readDisabledLabels = detail.readDisabled.joinToString { it.label }
        val readDisabled = if (readDisabledLabels.isEmpty()) tr("none", "nenhum") else readDisabledLabels
        append(tr("Read back: $readDisabled. ", "Na releitura: $readDisabled. "))
    }
    if (detail.sentMappingDiffs.isNotEmpty()) {
        append(tr("Sent: ", "Enviado: "))
        append(detail.sentMappingDiffs.joinToString { (from, to) -> "${from.label}→${to.label}" })
        append(". ")
    }
    if (detail.readMappingDiffs.isNotEmpty()) {
        append(tr("Read back: ", "Na releitura: "))
        append(detail.readMappingDiffs.joinToString { (from, to) -> "${from.label}→${to.label}" })
        append(". ")
    }
    if (isEmpty()) {
        append(tr("Button mapping differs.", "O mapeamento de botões difere."))
    }
}.trim()

@Composable
fun localizedMismatchFieldNote(
    field: X10SettingField,
    requested: ControllerConfig,
    settings: ControllerSettings,
    buttonsMismatch: X10ButtonsApplyMismatch?,
): String = when (field) {
    X10SettingField.BUTTONS -> localizedButtonsMismatch(buttonsMismatch ?: X10ButtonsApplyMismatch(
        rearMacrosDiffer = false,
        sentRearMacroTriggers = emptyList(),
        readRearMacroTriggers = emptyList(),
        disabledDiffer = false,
        sentDisabled = emptySet(),
        readDisabled = emptySet(),
        sentMappingDiffs = emptyList(),
        readMappingDiffs = emptyList(),
    ))
    X10SettingField.STICKS -> tr(
        "Left ${requested.leftStick.min}/${requested.leftStick.max} vs read ${settings.leftStick.min}/${settings.leftStick.max}; " +
            "right ${requested.rightStick.min}/${requested.rightStick.max} vs read ${settings.rightStick.min}/${settings.rightStick.max}",
        "Esquerdo ${requested.leftStick.min}/${requested.leftStick.max} vs releitura ${settings.leftStick.min}/${settings.leftStick.max}; " +
            "direito ${requested.rightStick.min}/${requested.rightStick.max} vs releitura ${settings.rightStick.min}/${settings.rightStick.max}",
    )
    X10SettingField.TRIGGERS -> tr(
        "LT ${requested.leftTrigger.min}–${requested.leftTrigger.max} vs read ${settings.leftTrigger.min}–${settings.leftTrigger.max}; " +
            "RT ${requested.rightTrigger.min}–${requested.rightTrigger.max} vs read ${settings.rightTrigger.min}–${settings.rightTrigger.max}",
        "LT ${requested.leftTrigger.min}–${requested.leftTrigger.max} vs releitura ${settings.leftTrigger.min}–${settings.leftTrigger.max}; " +
            "RT ${requested.rightTrigger.min}–${requested.rightTrigger.max} vs releitura ${settings.rightTrigger.min}–${settings.rightTrigger.max}",
    )
    X10SettingField.VIBRATION -> tr(
        "Motors sent ${requested.leftVibration}%/${requested.rightVibration}% vs read ${settings.leftVibration}%/${settings.rightVibration}%",
        "Motores enviados ${requested.leftVibration}%/${requested.rightVibration}% vs releitura ${settings.leftVibration}%/${settings.rightVibration}%",
    )
    X10SettingField.TURBO -> tr(
        "Turbo rules differ (the device may reorder entries).",
        "As regras de turbo diferem (o controle pode reordenar entradas).",
    )
}
