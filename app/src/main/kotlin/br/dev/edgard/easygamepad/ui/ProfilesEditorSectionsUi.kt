// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.dev.edgard.easygamepad.ble.LinkPhase
import br.dev.edgard.easygamepad.protocol.ControllerConfig
import br.dev.edgard.easygamepad.protocol.DeviceProfile
import br.dev.edgard.easygamepad.protocol.PadKey
import br.dev.edgard.easygamepad.protocol.StickSettings
import br.dev.edgard.easygamepad.protocol.TriggerSettings

/** Stack stick/trigger columns when large type or the editor column is narrow. */
fun profileEditorDualColumnStacked(fontScale: Float, maxWidthDp: Float): Boolean =
    fontScale >= 1.3f || maxWidthDp < 360f

val profileEditorFooterClearance = 80.dp

fun profileEditorButtonListKeys(mappings: Map<PadKey, PadKey>): List<PadKey> =
    (PadKey.outputs + listOf(PadKey.M1, PadKey.M2) + listOf(PadKey.M3, PadKey.M4).filter { it in mappings })

/** Compact X10 stage so button map and list fit above the action bar on typical phones. */
fun profileEditorControllerStageModifier(shortHeight: Boolean): Modifier =
    if (shortHeight) Modifier.fillMaxWidth().height(168.dp)
    else Modifier.fillMaxWidth().heightIn(max = 168.dp).aspectRatio(2.05f)

@Composable
fun EditorConfigurationChannelRow(
    phase: LinkPhase,
    linkError: String?,
    switching: Boolean,
    operationBusy: Boolean,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!profileEditorConfigurationRowVisible(phase, linkError)) return
    val portuguese = LocalPortuguese.current
    val status = profileEditorConfigurationChannelStatus(phase, linkError, portuguese) ?: return
    val connectEnabled = profileEditorConnectEnabled(phase, switching, operationBusy)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            status,
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
        if (connectEnabled) {
            val connectLabel = tr("Connect", "Conectar")
            TextButton(
                onClick = onConnect,
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = connectLabel },
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) { Text(connectLabel) }
        }
    }
}

@Composable
private fun EditorSectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
fun SticksPairEditor(
    left: StickSettings,
    right: StickSettings,
    onLeft: (StickSettings) -> Unit,
    onRight: (StickSettings) -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    EditorSectionCard {
        Text(
            tr("Movement → response", "Movimento → resposta"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stacked = profileEditorDualColumnStacked(fontScale, maxWidth.value)
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactStickColumn(Modifier.fillMaxWidth(), tr("Left stick", "Analógico esquerdo"), left, onLeft)
                    CompactStickColumn(Modifier.fillMaxWidth(), tr("Right stick", "Analógico direito"), right, onRight)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactStickColumn(Modifier.weight(1f), tr("Left stick", "Analógico esquerdo"), left, onLeft)
                    CompactStickColumn(Modifier.weight(1f), tr("Right stick", "Analógico direito"), right, onRight)
                }
            }
        }
    }
}

@Composable
fun TriggersPairEditor(
    left: TriggerSettings,
    right: TriggerSettings,
    onLeft: (TriggerSettings) -> Unit,
    onRight: (TriggerSettings) -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    EditorSectionCard {
        Text(
            tr("Trigger pull → output", "Puxar gatilho → saída"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stacked = profileEditorDualColumnStacked(fontScale, maxWidth.value)
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactTriggerColumn(Modifier.fillMaxWidth(), "LT", left, onLeft)
                    CompactTriggerColumn(Modifier.fillMaxWidth(), "RT", right, onRight)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactTriggerColumn(Modifier.weight(1f), "LT", left, onLeft)
                    CompactTriggerColumn(Modifier.weight(1f), "RT", right, onRight)
                }
            }
        }
    }
}

@Composable
private fun CompactStickColumn(modifier: Modifier, title: String, value: StickSettings, change: (StickSettings) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        MiniResponseCurve(value.min, value.max, Modifier.fillMaxWidth().height(40.dp))
        CompactResponseSliders(value.min, value.max) { min, max -> change(value.copy(min = min, max = max)) }
        StickReverseToggleRow(
            stickTitle = title,
            visibleTitle = tr("Reverse L/R", "Inverter E/D"),
            axis = AccessibilitySemantics.StickReverseAxis.LeftRight,
            checked = value.invertX,
            onChecked = { change(value.copy(invertX = it)) },
        )
        StickReverseToggleRow(
            stickTitle = title,
            visibleTitle = tr("Reverse U/D", "Inverter C/B"),
            axis = AccessibilitySemantics.StickReverseAxis.UpDown,
            checked = value.invertY,
            onChecked = { change(value.copy(invertY = it)) },
        )
    }
}

@Composable
private fun StickReverseToggleRow(
    stickTitle: String,
    visibleTitle: String,
    axis: AccessibilitySemantics.StickReverseAxis,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    val portuguese = LocalPortuguese.current
    val toggleLabel = AccessibilitySemantics.stickReverseToggleLabel(stickTitle, axis, portuguese)
    val state = AccessibilitySemantics.statusState(checked, portuguese)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(visibleTitle, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
        Switch(
            checked,
            onChecked,
            modifier = Modifier.semantics {
                contentDescription = toggleLabel
                stateDescription = state
            },
        )
    }
}

@Composable
fun ProfileButtonMappingList(
    draft: ControllerConfig,
    onPick: (PadKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keys = profileEditorButtonListKeys(draft.mappings)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        keys.forEach { source ->
            val target = draft.mappings[source]
            val outputKind = draft.buttonsListOutputKind(source)
            val outputLabel = when (outputKind) {
                ButtonsListOutputKind.Disabled -> tr("Disabled", "Desativado")
                ButtonsListOutputKind.Mapping -> requireNotNull(target).label
                ButtonsListOutputKind.Macro -> tr("Macro", "Macro")
                ButtonsListOutputKind.NotSent -> if (source in DeviceProfile.X10.rearButtons) {
                    tr("Unchanged", "Inalterado")
                } else {
                    tr("Not sent", "Não enviado")
                }
            }
            val rowLabel = tr("Button ", "Botão ") + source.label + ", " + outputLabel
            val outputColor = when (outputKind) {
                ButtonsListOutputKind.Disabled -> MaterialTheme.colorScheme.error
                ButtonsListOutputKind.Mapping ->
                    if (target != source) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                ButtonsListOutputKind.Macro -> MaterialTheme.colorScheme.primary
                ButtonsListOutputKind.NotSent -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Surface(
                onClick = { onPick(source) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = rowLabel },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(source.label, fontWeight = FontWeight.SemiBold, modifier = Modifier.widthIn(min = 36.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("→", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(outputLabel, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium, color = outputColor)
                }
            }
        }
    }
}

@Composable
private fun CompactTriggerColumn(modifier: Modifier, label: String, value: TriggerSettings, change: (TriggerSettings) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        MiniResponseCurve(value.min, value.max, Modifier.fillMaxWidth().height(40.dp))
        CompactResponseSliders(value.min, value.max) { min, max -> change(TriggerSettings(min, max)) }
    }
}

@Composable
private fun CompactStickBlock(title: String, value: StickSettings, change: (StickSettings) -> Unit) {
    CompactStickColumn(Modifier.fillMaxWidth(), title, value, change)
}

@Composable
private fun CompactTriggerBlock(label: String, value: TriggerSettings, change: (TriggerSettings) -> Unit) {
    CompactTriggerColumn(Modifier.fillMaxWidth(), label, value, change)
}

@Composable
private fun CompactResponseSliders(min: Int, max: Int, change: (Int, Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ValueSlider(tr("Ignore movement up to", "Ignorar movimentos até"), min, 0..(max - 1), "%", exactInput = true) { change(it, max) }
        ValueSlider(tr("Reach maximum at", "Atingir o máximo em"), max, (min + 1)..100, "%", exactInput = true) { change(min, it) }
    }
}

@Composable
fun CompactStickEditor(title: String, value: StickSettings, change: (StickSettings) -> Unit) {
    EditorSectionCard { CompactStickBlock(title, value, change) }
}

@Composable
fun CompactTriggerEditor(label: String, value: TriggerSettings, change: (TriggerSettings) -> Unit) {
    EditorSectionCard { CompactTriggerBlock(label, value, change) }
}

@Composable
fun ResponseSliders(min: Int, max: Int, change: (Int, Int) -> Unit) = CompactResponseSliders(min, max, change)

@Composable
fun MiniResponseCurve(min: Int, max: Int, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val label = tr("Response: ignore the first $min%, reach full output at $max%.", "Resposta: ignora os primeiros $min% e atinge o máximo em $max%.")
    Canvas(modifier.semantics { contentDescription = label }) {
        val inset = 4.dp.toPx()
        val w = size.width - 2 * inset
        val h = size.height - 2 * inset
        for (step in 0..2) {
            val t = step / 2f
            drawLine(grid, Offset(inset + w * t, inset), Offset(inset + w * t, inset + h), strokeWidth = 1f)
            drawLine(grid, Offset(inset, inset + h * t), Offset(inset + w, inset + h * t), strokeWidth = 1f)
        }
        val path = Path().apply {
            moveTo(inset, inset + h)
            lineTo(inset + w * min / 100f, inset + h)
            lineTo(inset + w * max / 100f, inset)
            lineTo(inset + w, inset)
        }
        drawPath(path, color, style = Stroke(1.5.dp.toPx()))
        drawCircle(color, 3.dp.toPx(), Offset(inset + w * min / 100f, inset + h))
        drawCircle(color, 3.dp.toPx(), Offset(inset + w * max / 100f, inset))
    }
}
