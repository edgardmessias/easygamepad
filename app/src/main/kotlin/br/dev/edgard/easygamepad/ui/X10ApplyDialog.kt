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

@Composable
fun X10ApplyDialog(
    config: ControllerConfig,
    ready: Boolean,
    simulated: Boolean = false,
    dismiss: () -> Unit,
    apply: () -> Unit,
) {
    val valid = remember(config) { config.profileIsValid() }
    val plan = remember(config, valid) { if (valid) config.x10UploadPlan() else null }
    val issues = remember(config) { config.profileValidationIssues() }
    val macroByTrigger = remember(config) { config.macros.associateBy { it.trigger } }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(tr("Apply this setup?", "Aplicar esta configuração?")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(config.name, style = MaterialTheme.typography.titleMedium)
                if (!valid) {
                    Text(
                        issues.firstOrNull()?.localizedMessage()
                            ?: tr("Fix profile errors before applying.", "Corrija os erros do perfil antes de aplicar."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (plan != null) {
                    Text(
                        if (simulated) {
                            tr(
                                "Your configured settings will be sent to the Demo X10 simulator, not to physical hardware.",
                                "Os ajustes configurados serão enviados ao simulador Demo X10, não a hardware físico.",
                            )
                        } else {
                            tr(
                                "Your configured settings in this profile will be sent to the X10.",
                                "Os ajustes que você configurou neste perfil serão enviados ao X10.",
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (!ready) {
                        Text(
                            if (simulated) {
                                tr(
                                    "Connect the Demo X10 simulator in configuration mode to apply.",
                                    "Conecte o simulador Demo X10 no modo de configuração para aplicar.",
                                )
                            } else {
                                tr(
                                    "Connect your X10 in configuration mode to apply.",
                                    "Conecte seu X10 no modo de configuração para aplicar.",
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    DeviceProfile.X10.rearButtons.forEach { paddle ->
                        x10RearPaddleSummaryLine(paddle, config, plan, macroByTrigger)?.let { line ->
                            val cleared = paddle in plan.clearedPaddles
                            Text(
                                line,
                                style = if (cleared) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                                color = if (cleared) MaterialTheme.colorScheme.error else LocalContentColor.current,
                            )
                        }
                    }
                    if (plan.omittedMappings.isNotEmpty()) {
                        Text(
                            tr(
                                "Buttons and paddles you did not configure here stay as they are on the controller.",
                                "Botões e paddles que você não configurou aqui permanecem como estão no controle.",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = apply, enabled = ready && valid) { Text(tr("Apply", "Aplicar")) }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(tr("Cancel", "Cancelar")) } },
    )
}

@Composable
private fun x10RearPaddleSummaryLine(
    paddle: PadKey,
    config: ControllerConfig,
    plan: X10UploadPlan,
    macroByTrigger: Map<PadKey, Macro>,
): String? {
    if (paddle in plan.clearedPaddles) {
        return tr("${paddle.label}: disabled", "${paddle.label}: desativado")
    }
    val macro = macroByTrigger[paddle]
    if (macro != null && macro.steps.isNotEmpty()) {
        return "${paddle.label}: ${tr("Macro", "Macro")}"
    }
    if (paddle in config.mappings && paddle !in config.disabledRearPaddles && macro == null) {
        val target = config.mappings.getValue(paddle)
        return "${paddle.label} → ${target.label}"
    }
    return null
}
