// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.dev.edgard.easygamepad.protocol.*

@Composable
fun X10ApplyOutcomeCard(
    requested: ControllerConfig?,
    result: X10ApplyResult,
    confirmedAt: Long?,
    readAt: Long?,
    onReadFromDevice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (requested == null) return
    val presentation = remember(requested, result) { x10ApplyPresentation(requested, result) }
    var expanded by rememberSaveable { mutableStateOf(presentation.kind != X10ApplyOutcomeKind.MATCHED) }
    val portuguese = LocalPortuguese.current
    fun fieldLabel(field: X10SettingField) = when (field) {
        X10SettingField.BUTTONS -> if (portuguese) "Botões" else "Buttons"
        X10SettingField.STICKS -> if (portuguese) "Analógicos" else "Sticks"
        X10SettingField.TRIGGERS -> if (portuguese) "Gatilhos" else "Triggers"
        X10SettingField.VIBRATION -> if (portuguese) "Vibração" else "Vibration"
        X10SettingField.TURBO -> "Turbo"
    }
    val title = when (presentation.kind) {
        X10ApplyOutcomeKind.MATCHED -> tr("Apply read-back matched", "Releitura conferiu com o envio")
        X10ApplyOutcomeKind.MISMATCH -> tr("Apply read-back differed", "Releitura diferiu do envio")
        X10ApplyOutcomeKind.UNCONFIRMED -> tr("Apply result unconfirmed", "Resultado do envio não confirmado")
    }
    val summary = when (presentation.kind) {
        X10ApplyOutcomeKind.MATCHED -> tr("Buttons, sticks, triggers, vibration and turbo match the controller read-back.",
            "Botões, analógicos, gatilhos, vibração e turbo conferem na releitura do controle.")
        X10ApplyOutcomeKind.MISMATCH -> tr("These groups differed: ${presentation.mismatchedFields.joinToString { fieldLabel(it) }}. Your draft was not changed.",
            "Estes grupos diferiram: ${presentation.mismatchedFields.joinToString { fieldLabel(it) }}. Seu rascunho não foi alterado.")
        X10ApplyOutcomeKind.UNCONFIRMED -> tr("Commands were accepted, but settings could not be read back: ${presentation.unconfirmedReason ?: ""}",
            "Os comandos foram aceitos, mas não foi possível reler os ajustes: ${presentation.unconfirmedReason ?: ""}")
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("Last apply to X10", "Última aplicação ao X10"), style = MaterialTheme.typography.titleSmall)
        InfoBanner(summary, error = presentation.kind != X10ApplyOutcomeKind.MATCHED)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) tr("Hide details", "Ocultar detalhes") else tr("Show details", "Ver detalhes"))
            }
        }
        AnimatedVisibility(expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (presentation.matchedFields.isNotEmpty() && presentation.kind == X10ApplyOutcomeKind.MISMATCH) {
                    Description(tr("Matched: ", "Conferiram: ") + presentation.matchedFields.joinToString { fieldLabel(it) })
                }
                presentation.fieldNotes.forEach { (field, note) ->
                    Text("${fieldLabel(field)}: $note", style = MaterialTheme.typography.bodySmall)
                }
                Description(tr("Macro sequences and persistence after power-off were not verified.", "As sequências de macro e a persistência após desligar não foram verificadas."))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onReadFromDevice) { Text(tr("Read from X10", "Trazer do X10")) }
                }
            }
        }
        val stamp = confirmedAt ?: readAt
        stamp?.let {
            val prefix = if (presentation.kind == X10ApplyOutcomeKind.MATCHED) tr("Matched at: ", "Conferido em: ")
            else tr("Read-back at: ", "Releitura em: ")
            Description(prefix + timeLabel(it))
        }
    }
}
