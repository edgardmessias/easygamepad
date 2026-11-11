// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import br.dev.edgard.easygamepad.protocol.*

@Composable
fun MacroEditor(macros: List<Macro>, change: (List<Macro>) -> Unit) {
    var picker by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (macros.isEmpty()) {
            InfoBanner(tr("Start with a simple sequence, like A → B, assigned to M1.", "Comece com uma sequência simples, como A → B, atribuída ao M1."))
        }
        macros.forEachIndexed { index, macro ->
            fun update(updated: Macro) = change(macros.toMutableList().also { it[index] = updated })
            HubCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(macro.trigger.label, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { change(macros.filterIndexed { i, _ -> i != index }) }) {
                        Text(tr("Remove paddle", "Remover botão"))
                    }
                }
                val summary = macro.sequenceSummary()
                if (summary.isNotEmpty()) {
                    Description(summary)
                } else {
                    Description(tr("Empty sequence — add a step or remove this paddle from the profile before applying.",
                        "Sequência vazia — adicione uma etapa ou remova este botão do perfil antes de aplicar."))
                }
                ToggleRow(tr("Repeat sequence", "Repetir sequência"), macro.repeat) { update(macro.copy(repeat = it)) }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 420.dp)) {
                    itemsIndexed(macro.steps, key = { stepIndex, _ -> "$index-$stepIndex" }) { stepIndex, step ->
                        MacroStepCard(
                            stepIndex = stepIndex,
                            step = step,
                            stepCount = macro.steps.size,
                            onPickKey = { picker = index to stepIndex },
                            onMoveUp = { update(macro.moveStep(stepIndex, -1)) },
                            onMoveDown = { update(macro.moveStep(stepIndex, 1)) },
                            onDuplicate = { runCatching { update(macro.duplicateStepAt(stepIndex)) } },
                            onRemove = { update(macro.removeStepAt(stepIndex)) },
                            onHoldChange = { value -> update(macro.copy(steps = macro.steps.toMutableList().also { it[stepIndex] = step.copy(holdMs = value) })) },
                            onWaitChange = { value -> update(macro.copy(steps = macro.steps.toMutableList().also { it[stepIndex] = step.copy(delayMs = value) })) },
                        )
                    }
                }
                OutlinedButton(
                    onClick = { update(macro.copy(steps = macro.steps + MacroStep())) },
                    enabled = macro.steps.size < MACRO_STEP_LIMIT,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(tr("Add step", "Adicionar etapa")) }
            }
        }
        val available = listOf(PadKey.M1, PadKey.M2).filter { key -> macros.none { it.trigger == key } }
        if (available.isNotEmpty()) {
            Eyebrow(tr("Add a paddle sequence", "Adicionar sequência traseira"))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                available.forEach { key ->
                    OutlinedButton(onClick = { change(macros + Macro(key, steps = listOf(MacroStep()))) }) {
                        Text("+ ${key.label}")
                    }
                }
            }
        }
        ProfileHelp(
            tr("How macros work", "Como funcionam as macros"),
            tr(
                "Build a sequence for a rear paddle. Each step presses a button, holds it, then waits. Up to 32 steps per paddle.",
                "Crie uma sequência para um botão traseiro. Cada etapa pressiona um botão, segura e espera. Até 32 etapas por botão.",
            ),
        )
        ProfileHelp(
            tr("Removing macros", "Remover macros"),
            tr(
                "Removing a macro from the profile omits its upload. That is not a delete command on the controller.",
                "Remover uma macro do perfil omite o envio. Isso não é um comando de exclusão no controle.",
            ),
        )
    }
    picker?.let { (macroIndex, stepIndex) ->
        KeyPicker(tr("Step button", "Botão da etapa"), PadKey.outputs, { picker = null }) { key ->
            val macro = macros[macroIndex]
            change(
                macros.toMutableList().also {
                    it[macroIndex] = macro.copy(steps = macro.steps.toMutableList().also { steps -> steps[stepIndex] = steps[stepIndex].copy(key = key) })
                },
            )
            picker = null
        }
    }
}

@Composable
private fun MacroStepCard(
    stepIndex: Int,
    step: MacroStep,
    stepCount: Int,
    onPickKey: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit,
    onRemove: () -> Unit,
    onHoldChange: (Int) -> Unit,
    onWaitChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Mono("%02d".format(stepIndex + 1))
            OutlinedButton(onClick = onPickKey) { Text(step.key.label) }
            Spacer(Modifier.weight(1f))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onMoveUp, enabled = stepIndex > 0) { Text(tr("Move up", "Subir")) }
            TextButton(onClick = onMoveDown, enabled = stepIndex < stepCount - 1) { Text(tr("Move down", "Descer")) }
            TextButton(onClick = onDuplicate, enabled = stepCount < MACRO_STEP_LIMIT) { Text(tr("Duplicate", "Duplicar")) }
            val removeLabel = tr("Remove step", "Remover etapa")
            TextButton(onClick = onRemove) {
                Text(removeLabel, color = MaterialTheme.colorScheme.error)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MacroNumberField(tr("Hold (ms)", "Pressionar (ms)"), step.holdMs, 1..65535, Modifier.weight(1f), onHoldChange)
            MacroNumberField(tr("Wait (ms)", "Esperar (ms)"), step.delayMs, 0..65535, Modifier.weight(1f), onWaitChange)
        }
    }
}

@Composable
private fun MacroNumberField(label: String, value: Int, range: IntRange, modifier: Modifier, change: (Int) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var text by remember(value) { mutableStateOf(value.toString()) }
    LaunchedEffect(value) { if (!focused) text = value.toString() }
    OutlinedTextField(
        text,
        { candidate ->
            if (candidate.length > 5) return@OutlinedTextField
            if (candidate.isEmpty() || candidate.all(Char::isDigit)) {
                text = candidate
                candidate.toIntOrNull()?.takeIf { it in range }?.let(change)
            }
        },
        label = { Text(label) },
        singleLine = true,
        isError = text.isNotEmpty() && text.toIntOrNull() !in range,
        modifier = modifier.onFocusChanged {
            focused = it.isFocused
            if (!it.isFocused) text = value.toString()
        },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
    )
}

@Composable
fun TurboEditor(turbos: List<Turbo>, change: (List<Turbo>) -> Unit) {
    var picker by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        turbos.forEachIndexed { index, turbo ->
            fun update(value: Turbo) = change(turbos.toMutableList().also { it[index] = value })
            HubCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(turbo.key.label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { change(turbos.filterIndexed { i, _ -> i != index }) }) {
                        Text(tr("Remove from profile", "Remover do perfil"))
                    }
                }
                ValueSlider(
                    tr("Turbo rate (1–20)", "Taxa de turbo (1–20)"),
                    turbo.rate,
                    1..20,
                    exactInput = true,
                ) { update(turbo.copy(rate = it)) }
                Description(tr("Higher values send a larger rate byte to the controller firmware.", "Valores maiores enviam um byte de taxa maior ao firmware do controle."))
                ToggleRow(tr("Automatic turbo", "Turbo automático"), turbo.automatic) { update(turbo.copy(automatic = it)) }
            }
        }
        OutlinedButton(onClick = { picker = true }, enabled = turbos.size < PadKey.outputs.size, modifier = Modifier.fillMaxWidth()) {
            Text(tr("Add turbo button", "Adicionar botão turbo"))
        }
        ProfileHelp(
            tr("How turbo works", "Como funciona o turbo"),
            tr(
                "Turbo repeats a face button using the rate value stored on the X10 (1–20). It is the same number sent in KRLY command 55, not a measured presses-per-second value.",
                "O turbo repete um botão frontal com o valor de taxa gravado no X10 (1–20). É o mesmo número enviado no comando KRLY 55, não uma medição de pressionamentos por segundo.",
            ),
        )
        Description(
            tr(
                "Removing turbo here may leave the previous turbo setting on your controller. Get the settings from X10 after applying to check.",
                "Remover o turbo aqui pode manter o ajuste anterior no controle. Use Trazer ajustes do X10 depois de aplicar para conferir.",
            ),
        )
    }
    if (picker) {
        KeyPicker(tr("Turbo button", "Botão turbo"), PadKey.outputs.filter { key -> turbos.none { it.key == key } }, { picker = false }) { key ->
            change(turbos + Turbo(key))
            picker = false
        }
    }
}
