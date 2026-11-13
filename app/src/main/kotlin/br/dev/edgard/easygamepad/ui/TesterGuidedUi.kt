// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import br.dev.edgard.easygamepad.data.ControllerDevice
import br.dev.edgard.easygamepad.input.GuidedApplyNote
import br.dev.edgard.easygamepad.input.GuidedGroup
import br.dev.edgard.easygamepad.input.GuidedLayout
import br.dev.edgard.easygamepad.input.GuidedMark
import br.dev.edgard.easygamepad.input.GuidedPhase
import br.dev.edgard.easygamepad.input.GuidedPlan
import br.dev.edgard.easygamepad.input.GuidedReport
import br.dev.edgard.easygamepad.input.GuidedRole
import br.dev.edgard.easygamepad.input.GuidedRow
import br.dev.edgard.easygamepad.input.GuidedSample
import br.dev.edgard.easygamepad.input.GuidedBinding
import br.dev.edgard.easygamepad.input.GuidedSession
import br.dev.edgard.easygamepad.input.GuidedTest
import br.dev.edgard.easygamepad.input.InputState

fun guidedPlanFor(device: ControllerDevice?): GuidedPlan {
    val profile = device?.profile ?: return GuidedPlan(GuidedLayout.GENERIC, emptyList())
    return GuidedPlan(GuidedLayout.STANDARD, profile.rearButtons.map { it.label })
}

fun guidedBindingFor(
    device: ControllerDevice?,
    state: InputState,
    modelLabel: String,
    synthetic: Boolean,
): GuidedBinding? {
    val inputId = state.selectedId ?: return null
    if (device == null) return null
    val pad = state.pads.firstOrNull { it.id == inputId } ?: return null
    val inputLabel = if (pad.name.isNotBlank()) "${pad.name} · #$inputId" else "#$inputId"
    return GuidedBinding(
        controllerId = device.id,
        inputId = inputId,
        modelLabel = modelLabel.ifBlank { device.name },
        inputLabel = inputLabel,
        synthetic = synthetic,
    )
}

fun guidedSampleFor(controllerId: String?, state: InputState): GuidedSample? {
    val inputId = state.selectedId ?: return null
    if (controllerId.isNullOrBlank()) return null
    return GuidedSample(controllerId, inputId, state.events, state.pressed, state.axes, state.pressCounts)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TesterGuidedCard(
    session: GuidedSession,
    applyNote: GuidedApplyNote?,
    restRejected: Boolean,
    onBegin: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStartOver: () -> Unit,
    onFinish: () -> Unit,
    onRest: () -> Unit,
    onConfirmRear: (String) -> Unit,
    onShare: () -> Unit,
) {
    val phase = session.phase
    var expanded by rememberSaveable(session.binding?.controllerId, session.binding?.inputId) { mutableStateOf(false) }
    val started = phase == GuidedPhase.ACTIVE || phase == GuidedPhase.PAUSED || phase == GuidedPhase.COMPLETED
    val progress = GuidedTest.progress(session)
    val rows = GuidedTest.rows(session)
    val phaseLabel = guidedPhaseLabel(session)
    val hint = guidedHint(session)
    val progressText = guidedProgressText(session, progress.observedRequired, progress.totalRequired, progress.observedOptional)
    val shareReason = tr("Start the guided test before sharing.", "Inicie o teste guiado antes de compartilhar.")
    val expansionState = if (expanded) tr("Expanded", "Expandido") else tr("Collapsed", "Recolhido")
    HubCard(Modifier.fillMaxWidth().semantics { stateDescription = phaseLabel }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("Guided test", "Teste guiado"),
                modifier = Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.width(8.dp))
            Text(phaseLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Text(hint, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (session.binding?.synthetic == true && phase != GuidedPhase.DISABLED) {
            Text(
                tr("Synthetic demo input.", "Entrada de demonstração sintética."),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (phase != GuidedPhase.DISABLED && session.plan.layout == GuidedLayout.GENERIC) {
            Text(
                tr(
                    "Controls on this model are optional and may be unavailable.",
                    "Neste modelo os controles são opcionais e podem não existir.",
                ),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (started && (progress.totalRequired > 0 || progress.totalOptional > 0)) {
            Text(progressText, modifier = Modifier.fillMaxWidth())
            LinearProgressIndicator(
                progress = { progress.fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).semantics {
                    stateDescription = progressText
                },
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                drawStopIndicator = {},
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (phase) {
                GuidedPhase.DISABLED, GuidedPhase.IDLE -> ActionButton(tr("Start", "Iniciar"), phase == GuidedPhase.IDLE, true, hint, onBegin)
                GuidedPhase.ACTIVE -> {
                    ActionButton(tr("Record rest", "Registrar repouso"), true, true, null, onRest)
                    ActionButton(tr("Pause", "Pausar"), true, false, null, onPause)
                }
                GuidedPhase.PAUSED -> ActionButton(tr("Resume", "Retomar"), true, true, null, onResume)
                GuidedPhase.COMPLETED -> ActionButton(tr("Start over", "Recomeçar"), true, true, null, onStartOver)
            }
            if (started) ActionButton(tr("Share report", "Compartilhar relatório"), true, false, shareReason, onShare)
        }
        if (started) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = 48.dp).semantics {
                    stateDescription = expansionState
                }) {
                    Text(if (expanded) tr("Hide checklist", "Ocultar lista") else tr("View checklist", "Ver lista"))
                }
                if (phase != GuidedPhase.COMPLETED) TextButton(onClick = onFinish, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(tr("Finish", "Encerrar"))
                }
            }
        }
        if (restRejected) {
            Text(
                tr("Move the sticks to obtain a reading, release them, then record rest again.", "Mova os analógicos para obter uma leitura, solte-os e registre o repouso novamente."),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (started && expanded) {
            listOf(
                GuidedGroup.BUTTON to tr("Buttons", "Botões"),
                GuidedGroup.SHOULDER to tr("Shoulders", "Superiores"),
                GuidedGroup.DPAD to tr("D-pad", "Direcional"),
                GuidedGroup.TRIGGER to tr("Triggers", "Gatilhos"),
                GuidedGroup.STICK to tr("Sticks", "Analógicos"),
            ).forEach { (group, title) ->
                GroupLine(title, rows.filter { it.group == group })
            }
            Text(
                tr(
                    "Directions follow Android axes. Negative Y counts as up.",
                    "As direções seguem os eixos do Android. Y negativo conta como cima.",
                ),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (session.rest?.anyFinite == true) {
                    tr("Rest: current reading stored.", "Repouso: leitura atual armazenada.")
                } else {
                    tr("Rest: not captured.", "Repouso: não registrado.")
                },
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                tr(
                    "Release the sticks and triggers, then record the current reading.",
                    "Solte os analógicos e os gatilhos e registre a leitura atual.",
                ),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (session.plan.rearIds.isNotEmpty()) {
                Text(
                    tr(
                        "Confirm a rear paddle only after you press that physical paddle. A face button, even when the paddle is mapped to it, does not count.",
                        "Confirme um paddle traseiro só depois de pressioná-lo fisicamente. Um botão da frente, mesmo quando o paddle está mapeado nele, não conta.",
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                )
                session.plan.rearIds.forEach { id ->
                    val confirmed = id in session.rearConfirmed
                    val confirmationState = if (confirmed) tr("Confirmed by you", "Confirmado por você") else tr("Not verified", "Não verificado")
                    OutlinedButton(
                        onClick = { onConfirmRear(id) },
                        enabled = phase == GuidedPhase.ACTIVE && !confirmed,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                            stateDescription = confirmationState
                        },
                    ) {
                        Text(if (confirmed) tr("$id confirmed", "$id confirmado") else tr("Confirm $id press", "Confirmar pressionamento de $id"))
                    }
                }
            }
            applyNote?.let { note ->
                Text(
                    GuidedReport.applySummary(note, LocalPortuguese.current),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (phase != GuidedPhase.COMPLETED) TextButton(onClick = onStartOver, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(tr("Start over", "Recomeçar"))
            }
        }
    }
}

@Composable
private fun GroupLine(title: String, rows: List<GuidedRow>) {
    if (rows.isEmpty()) return
    val portuguese = LocalPortuguese.current
    val seen = rows.filter { it.observed }.joinToString { itemText(it, portuguese) }.ifEmpty { tr("none", "nenhum") }
    val waiting = rows.filter { !it.observed }.joinToString { itemText(it, portuguese) }.ifEmpty { tr("none", "nenhum") }
    val optional = rows.all { it.role == GuidedRole.OPTIONAL }
    val prefix = if (optional) tr("$title, optional", "$title, opcional") else title
    Text(
        tr("$prefix · seen $seen · waiting $waiting", "$prefix · vistos $seen · faltam $waiting"),
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
    )
}

private fun itemText(row: GuidedRow, portuguese: Boolean): String {
    val name = GuidedReport.itemLabel(row.id, portuguese)
    return if (row.mark == GuidedMark.DIGITAL_ONLY) "$name (" + (if (portuguese) "toque ou salto" else "press or step") + ")" else name
}

@Composable
private fun ActionButton(
    label: String,
    enabled: Boolean,
    primary: Boolean,
    disabledReason: String?,
    onClick: () -> Unit,
) {
    val modifier = Modifier.heightIn(min = 48.dp).semantics {
        if (!enabled && disabledReason != null) stateDescription = disabledReason
    }
    if (primary) {
        Button(onClick = onClick, enabled = enabled, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) { Text(label) }
    }
}

@Composable
private fun guidedPhaseLabel(session: GuidedSession): String = when (session.phase) {
    GuidedPhase.DISABLED -> tr("No input", "Sem entrada")
    GuidedPhase.IDLE -> tr("Not started", "Não iniciado")
    GuidedPhase.ACTIVE -> tr("In progress", "Em andamento")
    GuidedPhase.PAUSED -> tr("Paused", "Pausado")
    GuidedPhase.COMPLETED -> if (session.stoppedEarly) tr("Partial", "Parcial") else tr("Completed", "Concluído")
}

@Composable
private fun guidedHint(session: GuidedSession): String = when (session.phase) {
    GuidedPhase.DISABLED -> tr(
        "Choose a controller with Android input. The guided test does not start by itself.",
        "Escolha um controle com entrada Android. O teste guiado não começa sozinho.",
    )
    GuidedPhase.IDLE -> tr(
        "Start a new checklist for buttons, sticks and triggers. Only inputs after Start count.",
        "Inicie uma lista de botões, analógicos e gatilhos. Só as entradas após Iniciar contam.",
    )
    GuidedPhase.ACTIVE -> tr(
        "Press each button, move both sticks in every direction and squeeze each trigger slowly. Release everything before recording rest.",
        "Pressione cada botão, mova os dois analógicos em todas as direções e aperte os gatilhos devagar. Solte tudo antes de registrar o repouso.",
    )
    GuidedPhase.PAUSED -> tr(
        "Paused. Input is not recorded until you resume.",
        "Pausado. A entrada não é registrada até você retomar.",
    )
    GuidedPhase.COMPLETED -> if (session.stoppedEarly) {
        tr(
            "Finished before every required observation.",
            "Encerrado antes de todas as observações obrigatórias.",
        )
    } else {
        tr(
            "Checklist finished for this session. This does not grade the hardware.",
            "Lista encerrada nesta sessão. Isto não classifica o hardware.",
        )
    }
}

@Composable
private fun guidedProgressText(session: GuidedSession, observed: Int, total: Int, optional: Int): String =
    if (session.plan.layout == GuidedLayout.STANDARD && total > 0) {
        tr("$observed of $total required observations", "$observed de $total observações obrigatórias")
    } else {
        tr("$optional optional observations", "$optional observações opcionais")
    }
