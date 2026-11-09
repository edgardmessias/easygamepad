// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.dev.edgard.easygamepad.HubViewModel
import br.dev.edgard.easygamepad.input.InputState
import br.dev.edgard.easygamepad.protocol.PadKey
import kotlin.math.abs
import kotlin.math.sqrt
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable fun TesterScreen(model: HubViewModel) {
    val state by model.input.collectAsStateWithLifecycle()
    val controller by model.selectedDevice.collectAsStateWithLifecycle()
    val devices by model.devices.collectAsStateWithLifecycle()
    var rear by rememberSaveable { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    val keys = listOf(KeyEvent.KEYCODE_BUTTON_A to "A", KeyEvent.KEYCODE_BUTTON_B to "B", KeyEvent.KEYCODE_BUTTON_X to "X", KeyEvent.KEYCODE_BUTTON_Y to "Y",
        KeyEvent.KEYCODE_BUTTON_L1 to "LB", KeyEvent.KEYCODE_BUTTON_R1 to "RB", KeyEvent.KEYCODE_BUTTON_L2 to "LT", KeyEvent.KEYCODE_BUTTON_R2 to "RT",
        KeyEvent.KEYCODE_BUTTON_THUMBL to "L3", KeyEvent.KEYCODE_BUTTON_THUMBR to "R3", KeyEvent.KEYCODE_BUTTON_SELECT to "View", KeyEvent.KEYCODE_BUTTON_START to "Menu",
        KeyEvent.KEYCODE_DPAD_UP to "↑", KeyEvent.KEYCODE_DPAD_DOWN to "↓", KeyEvent.KEYCODE_DPAD_LEFT to "←", KeyEvent.KEYCODE_DPAD_RIGHT to "→")
    val hasInput = state.selectedId != null
    AdaptivePanes(secondary = {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StickPlot(tr("Left stick", "Analógico esquerdo"), state.axes[MotionEvent.AXIS_X] ?: 0f, state.axes[MotionEvent.AXIS_Y] ?: 0f, state.samples, Modifier.weight(1f))
            StickPlot(tr("Right stick", "Analógico direito"), state.axes[MotionEvent.AXIS_Z] ?: state.axes[MotionEvent.AXIS_RX] ?: 0f, state.axes[MotionEvent.AXIS_RZ] ?: state.axes[MotionEvent.AXIS_RY] ?: 0f, state.rightSamples, Modifier.weight(1f))
        }
        ShouldersPanel(state)
        HubCard(Modifier.fillMaxWidth()) {
            Text(tr("Button check", "Verificação de botões"), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (keys + state.tested.filter { code -> keys.none { it.first == code } }.sorted().map {
                    it to KeyEvent.keyCodeToString(it).removePrefix("KEYCODE_").removePrefix("BUTTON_")
                }).forEach { (code, label) ->
                    val pressed = code in state.pressed || when (code) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> (state.axes[MotionEvent.AXIS_HAT_X] ?: 0f) < -.5f
                        KeyEvent.KEYCODE_DPAD_RIGHT -> (state.axes[MotionEvent.AXIS_HAT_X] ?: 0f) > .5f
                        KeyEvent.KEYCODE_DPAD_UP -> (state.axes[MotionEvent.AXIS_HAT_Y] ?: 0f) < -.5f
                        KeyEvent.KEYCODE_DPAD_DOWN -> (state.axes[MotionEvent.AXIS_HAT_Y] ?: 0f) > .5f
                        KeyEvent.KEYCODE_BUTTON_L2 -> triggerAxis(state, MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_BRAKE, KeyEvent.KEYCODE_BUTTON_L2) > .01f
                        KeyEvent.KEYCODE_BUTTON_R2 -> triggerAxis(state, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS, KeyEvent.KEYCODE_BUTTON_R2) > .01f
                        else -> false
                    }
                    Surface(shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, if (code in state.tested) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant), color = if (pressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) {
                        Text(label, Modifier.padding(horizontal = 13.dp, vertical = 10.dp), color = if (pressed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Description(tr("Outlined buttons have already been tested. Rear buttons light up with the function assigned to them.", "A borda destacada marca os botões já testados. Os botões traseiros acendem junto com a função atribuída a eles."))
            OutlinedButton(onClick = { model.app.input.reset() }) { Text(tr("Clear test", "Limpar teste")) }
        }
        if (state.axes.isNotEmpty()) TextButton(onClick = { details = !details }) {
            Text(if (details) tr("Hide technical details", "Ocultar detalhes técnicos") else tr("Technical details", "Detalhes técnicos"))
        }
        if (details && state.axes.isNotEmpty()) HubCard(Modifier.fillMaxWidth()) {
            Text(tr("Raw axes", "Eixos brutos"), style = MaterialTheme.typography.titleMedium)
            state.axes.toSortedMap().forEach { (axis, value) ->
                Row { Mono(MotionEvent.axisToString(axis).removePrefix("AXIS_"), Modifier.weight(1f)); Mono(String.format(Locale.US, "%+.4f", value)) }
            }
            Description(tr("Values are shown without a software dead zone so you can inspect drift. The tester does not inject or remap inputs in other apps.", "Valores exibidos sem zona morta por software para inspecionar drift. O teste não injeta nem remapeia entradas em outros apps."))
        }
        if (details) state.pads.firstOrNull { it.id == state.selectedId }?.let { pad -> Mono("VID %04X  ·  PID %04X".format(pad.vendor, pad.product)) }
    }) {
        ScreenHeading(tr("Live controller", "Controle ao vivo"), tr("Every move, on screen.", "Cada movimento na tela."))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(if (hasInput) tr("Game connected", "Jogo conectado") else tr("Waiting for controller", "Aguardando controle"), hasInput)
            StatusPill(tr("${state.tested.size} buttons tested", "${state.tested.size} botões testados"), state.tested.isNotEmpty(), color = MaterialTheme.colorScheme.secondary)
        }
        if (!hasInput) {
            InfoBanner(tr("Turn on your controller and connect it through Bluetooth or USB. Then choose it below to start testing.", "Ligue o controle e conecte por Bluetooth ou USB. Depois, escolha abaixo para começar o teste."))
            OutlinedButton(onClick = { model.deviceSheet.value = true }) { Text(tr("Choose controller to test", "Escolher controle para testar")) }
            state.pads.forEach { pad ->
                OutlinedButton(onClick = {
                    if (controller?.address != null) model.linkGameplay(pad.id)
                    else devices.firstOrNull { it.inputId == pad.id }?.let(model::chooseDevice)
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Use ", "Usar ") + (devices.firstOrNull { it.inputId == pad.id }?.name ?: pad.name) + " · #${pad.id}")
                }
            }
        }
        HubCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(controller?.name ?: tr("Controller preview", "Visual do controle"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { model.app.input.reset() }, enabled = hasInput) { Text(tr("Clear", "Limpar")) }
            }
            if (controller?.artwork != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !rear, onClick = { rear = false }, label = { Text(tr("Front", "Frente")) })
                FilterChip(selected = rear, onClick = { rear = true }, label = { Text(tr("Rear paddles", "Botões traseiros")) })
            }
            ControllerStage(Modifier.fillMaxWidth().height(LocalWindowLayout.current.stageHeight(185.dp)), controller?.artwork, state, rear)
            Description(tr("Press a button or move a stick to see it light up.", "Pressione um botão ou mova um analógico para ver a resposta."))
        }
        if (controller?.rearButtons?.isNotEmpty() == true && rear) HubCard(Modifier.fillMaxWidth()) {
            Text(tr("Identify rear paddles", "Identificar botões traseiros"), style = MaterialTheme.typography.titleMedium)
            val paddles = controller?.rearButtons.orEmpty().joinToString(", ") { it.label }
            Description(tr("Choose a rear button ($paddles), then press it. The app will remember its function until you clear this test.",
                "Escolha um botão traseiro ($paddles) e pressione. O app lembra a função dele até você limpar este teste."))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                controller?.rearButtons.orEmpty().forEach { paddle ->
                    val code = state.paddleBindings[paddle]
                    FilterChip(selected = state.learningPaddle == paddle || code in state.pressed,
                        enabled = hasInput,
                        onClick = { rear = true; model.app.input.identifyPaddle(paddle) },
                        label = { Text(paddle.label + (code?.let { " · " + KeyEvent.keyCodeToString(it).removePrefix("KEYCODE_").removePrefix("BUTTON_") } ?: " +")) })
                }
            }
            state.learningPaddle?.let { paddle ->
                Text(tr("Press ", "Pressione ") + paddle.label + tr(" now…", " agora…"), color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = { model.app.input.identifyPaddle(null) }) { Text(tr("Cancel identification", "Cancelar identificação")) }
            }
            Description(tr("If M1 repeats A, both light up together. This identifies the button for testing and keeps its current function.",
                "Se M1 repete A, os dois acendem juntos. A identificação serve para o teste e mantém a função atual do botão."))
        }
    }
}
internal fun triggerAxis(state: InputState, primary: Int, fallback: Int, key: Int): Float {
    val analog = listOfNotNull(state.axes[primary], state.axes[fallback]).maxOrNull()
    return (analog ?: if (key in state.pressed) 1f else 0f).coerceIn(0f, 1f)
}

@Composable private fun ShouldersPanel(state: InputState) {
    HubCard(Modifier.fillMaxWidth()) {
        Text(tr("Top buttons & triggers", "Botões superiores e gatilhos"), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TriggerMeter("LB", "LT", KeyEvent.KEYCODE_BUTTON_L1, triggerAxis(state, MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_BRAKE, KeyEvent.KEYCODE_BUTTON_L2), state, Modifier.weight(1f))
            TriggerMeter("RB", "RT", KeyEvent.KEYCODE_BUTTON_R1, triggerAxis(state, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS, KeyEvent.KEYCODE_BUTTON_R2), state, Modifier.weight(1f))
        }
        Description(tr("Press LB and RB, then squeeze LT and RT slowly. If a trigger is in digital mode, it will jump straight to 100%.", "Pressione LB e RB e aperte LT e RT devagar. Se um gatilho estiver no modo digital, ele vai direto para 100%."))
    }
}
@Composable private fun TriggerMeter(bumper: String, trigger: String, key: Int, value: Float, state: InputState, modifier: Modifier) {
    val pressed = key in state.pressed
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(10.dp), color = if (pressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (pressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(bumper, Modifier.weight(1f), color = if (pressed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    Text(if (pressed) tr("On", "Ativo") else tr("Off", "Solto"), style = MaterialTheme.typography.labelSmall, color = if (pressed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(trigger, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.titleLarge,
                    color = if (value > .01f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LinearProgressIndicator(progress = { value }, modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surface, drawStopIndicator = {})
        }
    }
}
@Composable private fun StickPlot(title: String, x: Float, y: Float, trail: List<Pair<Float, Float>>, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val info = "$title X ${String.format(Locale.US, "%.3f", x)} Y ${String.format(Locale.US, "%.3f", y)}"
    HubCard(modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium, minLines = 2, maxLines = 2)
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f).semantics { contentDescription = info }) {
            val radius = size.minDimension * .44f
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(outline, radius, center, style = Stroke(1.dp.toPx()))
            drawCircle(outline, radius / 2, center, style = Stroke(1.dp.toPx()))
            drawLine(outline, center - Offset(radius, 0f), center + Offset(radius, 0f))
            drawLine(outline, center - Offset(0f, radius), center + Offset(0f, radius))
            trail.forEachIndexed { index, sample -> drawCircle(color.copy(alpha = .08f + index.toFloat() / 180f), 1.8.dp.toPx(), center + Offset(sample.first * radius, sample.second * radius)) }
            drawLine(color.copy(alpha = .5f), center, center + Offset(x * radius, y * radius), 1.5.dp.toPx())
            drawCircle(color, 5.dp.toPx(), center + Offset(x.coerceIn(-1f, 1f) * radius, y.coerceIn(-1f, 1f) * radius))
        }
        Mono(String.format(Locale.US, "X %+.3f", x)); Mono(String.format(Locale.US, "Y %+.3f", y))
        Description(tr("Offset ", "Desvio ") + String.format(Locale.US, "%.1f%%", sqrt(x * x + y * y) * 100))
    }
}
