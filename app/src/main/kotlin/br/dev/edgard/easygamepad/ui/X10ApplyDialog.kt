// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.dev.edgard.easygamepad.protocol.*

@Composable fun X10ApplyDialog(config: ControllerConfig, ready: Boolean, dismiss: () -> Unit, apply: () -> Unit) {
    val plan = remember(config) { config.x10UploadPlan() }
    fun keys(values: List<PadKey>) = values.joinToString { it.label }
    AlertDialog(onDismissRequest = dismiss,
        title = { Text(tr("Apply this setup?", "Aplicar esta configuração?")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(config.name, style = MaterialTheme.typography.titleMedium)
                Description(tr("Keep your X10 nearby while the app sends and reads back the settings.",
                    "Mantenha seu X10 por perto enquanto o app envia e relê os ajustes."))
                Text(tr("Button assignments sent: ${plan.assignedMappings.size}", "Atribuições de botões enviadas: ${plan.assignedMappings.size}"))
                plan.assignedMappings.forEach { (from, to) -> Text("${from.label} → ${to.label}") }
                Text(tr("Standard outputs restored: ${plan.restoredMappings.size}", "Funções padrão restauradas: ${plan.restoredMappings.size}"))
                if (plan.omittedMappings.isNotEmpty()) Description(tr("Assignments not sent: ", "Atribuições não enviadas: ") + keys(plan.omittedMappings))
                Text(tr("Macros sent: ", "Macros enviadas: ") + if (plan.macroTriggers.isEmpty()) tr("None", "Nenhuma") else keys(plan.macroTriggers))
                Text(tr("Turbo rules sent: ", "Regras de turbo enviadas: ") + if (plan.turboKeys.isEmpty()) tr("None", "Nenhuma") else keys(plan.turboKeys))
                Description(tr("Stick and trigger ranges, axis inversion and both motor intensities are also sent.",
                    "Faixas dos analógicos e gatilhos, inversão de eixos e intensidades dos dois motores também são enviadas."))
                InfoBanner(tr("Omitted entries are not erase requests. Existing onboard macros, turbo rules or assignments may remain. No deletion command is sent.",
                    "Entradas ausentes não são pedidos de exclusão. Macros, regras de turbo ou atribuições existentes podem permanecer no controle. Nenhum comando de exclusão é enviado."))
                Description(tr("Read-back can check buttons, sticks, triggers, vibration and turbo. Macro sequences and persistence after power-off are not checked.",
                    "A releitura pode conferir botões, analógicos, gatilhos, vibração e turbo. As sequências de macro e a persistência após desligar não são verificadas."))
            }
        },
        confirmButton = { TextButton(onClick = apply, enabled = ready) { Text(tr("Apply setup", "Aplicar configuração")) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(tr("Cancel", "Cancelar")) } })
}
