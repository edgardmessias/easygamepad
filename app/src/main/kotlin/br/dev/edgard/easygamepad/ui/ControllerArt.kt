// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
// Copyright (C) 2026 Amjad AAYD
package br.dev.edgard.easygamepad.ui

import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.MaterialTheme
import androidx.core.graphics.PathParser
import br.dev.edgard.easygamepad.R
import br.dev.edgard.easygamepad.data.ControllerArtwork
import br.dev.edgard.easygamepad.data.ControllerArtworkCatalog
import br.dev.edgard.easygamepad.input.InputState
import br.dev.edgard.easygamepad.protocol.PadKey
import org.json.JSONObject

private data class ArtRegion(val key: PadKey, val bounds: Rect, val path: Path)
private data class StickLayer(val center: Offset, val travel: Float, val radius: Float)
private data class TriggerLayer(val key: PadKey, val bounds: Rect)
private data class ArtLayout(val front: List<ArtRegion>, val rear: List<ArtRegion>, val sticks: List<StickLayer>, val triggers: List<TriggerLayer>)

/** Asset geometry from x20ctl (control-shapes.json + native-layers.json); see THIRD_PARTY.md. */
private fun readLayout(json: String): ArtLayout {
    val root = JSONObject(json)
    val shapes = root.getJSONObject("shapes")
    fun regions(group: String): List<ArtRegion> {
        val rows = shapes.optJSONObject(group) ?: return emptyList()
        return rows.keys().asSequence().mapNotNull { name ->
            val key = when (name) {
                "DPAD_UP" -> PadKey.UP; "DPAD_DOWN" -> PadKey.DOWN
                "DPAD_LEFT" -> PadKey.LEFT; "DPAD_RIGHT" -> PadKey.RIGHT
                else -> runCatching { PadKey.valueOf(name) }.getOrNull()
            } ?: return@mapNotNull null
            val row = rows.getJSONObject(name)
            val b = row.getJSONArray("bounds")
            val x = b.getDouble(0).toFloat(); val y = b.getDouble(1).toFloat()
            ArtRegion(key, Rect(x, y, x + b.getDouble(2).toFloat(), y + b.getDouble(3).toFloat()),
                requireNotNull(PathParser.createPathFromPathData(row.getString("path"))).asComposePath())
        }.toList()
    }
    val layers = root.getJSONObject("layers")
    val sticks = listOf("left", "right").map { side ->
        val row = layers.getJSONObject("sticks").getJSONObject(side)
        val c = row.getJSONArray("center")
        StickLayer(Offset(c.getDouble(0).toFloat(), c.getDouble(1).toFloat()),
            row.getDouble("travel").toFloat(), row.getDouble("capRadius").toFloat())
    }
    val triggers = layers.optJSONObject("triggers")?.let { rows ->
        listOf(PadKey.LT, PadKey.RT).mapNotNull { key ->
            val b = rows.optJSONObject(key.name)?.optJSONArray("bounds") ?: return@mapNotNull null
            TriggerLayer(key, Rect(b.getDouble(0).toFloat(), b.getDouble(1).toFloat(), b.getDouble(2).toFloat(), b.getDouble(3).toFloat()))
        }
    }.orEmpty()
    val clicks = sticks.mapIndexed { index, stick ->
        val r = stick.radius
        ArtRegion(if (index == 0) PadKey.LS else PadKey.RS,
            Rect(stick.center - Offset(r, r), stick.center + Offset(r, r)),
            Path().apply { addOval(Rect(stick.center - Offset(r, r), stick.center + Offset(r, r))) })
    }
    return ArtLayout(regions("front") + clicks, regions("back") + regions("macros"), sticks, triggers)
}

private val androidKeys = mapOf(
    PadKey.A to KeyEvent.KEYCODE_BUTTON_A, PadKey.B to KeyEvent.KEYCODE_BUTTON_B,
    PadKey.X to KeyEvent.KEYCODE_BUTTON_X, PadKey.Y to KeyEvent.KEYCODE_BUTTON_Y,
    PadKey.LB to KeyEvent.KEYCODE_BUTTON_L1, PadKey.RB to KeyEvent.KEYCODE_BUTTON_R1,
    PadKey.LT to KeyEvent.KEYCODE_BUTTON_L2, PadKey.RT to KeyEvent.KEYCODE_BUTTON_R2,
    PadKey.LS to KeyEvent.KEYCODE_BUTTON_THUMBL, PadKey.RS to KeyEvent.KEYCODE_BUTTON_THUMBR,
    PadKey.SELECT to KeyEvent.KEYCODE_BUTTON_SELECT, PadKey.START to KeyEvent.KEYCODE_BUTTON_START,
    PadKey.UP to KeyEvent.KEYCODE_DPAD_UP, PadKey.DOWN to KeyEvent.KEYCODE_DPAD_DOWN,
    PadKey.LEFT to KeyEvent.KEYCODE_DPAD_LEFT, PadKey.RIGHT to KeyEvent.KEYCODE_DPAD_RIGHT,
)

/** How far a control is pressed, 0..1. Triggers are analog; everything else is on or off. */
private fun pressure(key: PadKey, input: InputState?): Float {
    if (input == null) return 0f
    if (androidKeys[key] in input.pressed) return 1f
    val on = when (key) {
        PadKey.LEFT -> (input.axes[MotionEvent.AXIS_HAT_X] ?: 0f) < -.5f
        PadKey.RIGHT -> (input.axes[MotionEvent.AXIS_HAT_X] ?: 0f) > .5f
        PadKey.UP -> (input.axes[MotionEvent.AXIS_HAT_Y] ?: 0f) < -.5f
        PadKey.DOWN -> (input.axes[MotionEvent.AXIS_HAT_Y] ?: 0f) > .5f
        PadKey.LT -> return triggerAxis(input, MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_BRAKE, KeyEvent.KEYCODE_BUTTON_L2)
        PadKey.RT -> return triggerAxis(input, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS, KeyEvent.KEYCODE_BUTTON_R2)
        PadKey.M1, PadKey.M2, PadKey.M3, PadKey.M4, PadKey.M5, PadKey.M6 -> input.paddleBindings[key]?.let { it in input.pressed } == true
        else -> false
    }
    return if (on) 1f else 0f
}

private fun drawables(artwork: ControllerArtwork) = when (artwork.drawablePrefix) {
    "x20" -> listOf(R.drawable.x20_front_body, R.drawable.x20_rear, R.drawable.x20_stick_left, R.drawable.x20_stick_right)
    "x20pro" -> listOf(R.drawable.x20pro_front_body, R.drawable.x20pro_rear, R.drawable.x20pro_stick_left, R.drawable.x20pro_stick_right)
    else -> listOf(R.drawable.x10_front_body, R.drawable.x10_rear, R.drawable.x10_stick_left, R.drawable.x10_stick_right)
}

@Composable fun ControllerArt(modifier: Modifier = Modifier, input: InputState? = null, rear: Boolean = false,
    mappings: Map<PadKey, PadKey> = emptyMap(), onPick: ((PadKey) -> Unit)? = null,
    artwork: ControllerArtwork = ControllerArtworkCatalog.X10) {
    val context = LocalContext.current
    val layout = remember(context, artwork.layoutAsset) {
        context.assets.open(artwork.layoutAsset).bufferedReader().use { readLayout(it.readText()) }
    }
    val images = drawables(artwork)
    val body = ImageBitmap.imageResource(if (rear) images[1] else images[0])
    val leftCap = ImageBitmap.imageResource(images[2])
    val rightCap = ImageBitmap.imageResource(images[3])
    val accent = MaterialTheme.colorScheme.primary
    val ink = Color(0xFF101926)
    val regions = if (rear) layout.rear else layout.front
    val pick by rememberUpdatedState(onPick)
    val rearList = artwork.rearButtons.joinToString(", ") { it.label }
    val description = if (rear) artwork.productName + tr(", back. Rear buttons: ", ", traseira. Botões traseiros: ") + rearList + "."
        else artwork.productName + tr(", front.", ", frente.")
    val changeLabel = tr("Change button ", "Alterar botão ")

    // Sticks follow live input through a stiff spring: smooth, never lagging behind the hand.
    val lx by animateFloatAsState(input?.axes?.get(MotionEvent.AXIS_X) ?: 0f, inputSpring(), label = "lx")
    val ly by animateFloatAsState(input?.axes?.get(MotionEvent.AXIS_Y) ?: 0f, inputSpring(), label = "ly")
    val rx by animateFloatAsState(input?.axes?.get(MotionEvent.AXIS_Z) ?: input?.axes?.get(MotionEvent.AXIS_RX) ?: 0f, inputSpring(), label = "rx")
    val ry by animateFloatAsState(input?.axes?.get(MotionEvent.AXIS_RZ) ?: input?.axes?.get(MotionEvent.AXIS_RY) ?: 0f, inputSpring(), label = "ry")
    val home by animateFloatAsState(if (input?.pressed?.contains(KeyEvent.KEYCODE_BUTTON_MODE) == true) 1f else 0f, motionTween(Motion.SHORT), label = "home")
    // Each control fades its highlight in and out instead of flickering.
    val glow = (layout.front + layout.rear).map { it.key }.distinct().associateWith { padKey ->
        key(padKey) {
            animateFloatAsState(pressure(padKey, input),
                if (padKey == PadKey.LT || padKey == PadKey.RT) inputSpring() else motionTween(Motion.SHORT), label = "press").value
        }
    }

    val touches = if (onPick == null) Modifier else Modifier.pointerInput(rear, layout) {
        detectTapGestures { tap ->
            val factor = minOf(size.width / ControllerArtwork.SOURCE_WIDTH, size.height / ControllerArtwork.SOURCE_HEIGHT)
            val origin = Offset((size.width - ControllerArtwork.SOURCE_WIDTH * factor) / 2, (size.height - ControllerArtwork.SOURCE_HEIGHT * factor) / 2)
            val point = (tap - origin) / factor
            regions.filter { it.bounds.inflate(16f).contains(point) }
                .minByOrNull { (it.bounds.center - point).getDistanceSquared() }?.let { pick?.invoke(it.key) }
        }
    }
    Canvas(modifier.then(touches).semantics {
        contentDescription = description
        if (onPick != null) customActions = regions.map { region ->
            CustomAccessibilityAction(changeLabel + region.key.label) { onPick(region.key); true }
        }
    }) {
        val factor = minOf(size.width / ControllerArtwork.SOURCE_WIDTH, size.height / ControllerArtwork.SOURCE_HEIGHT)
        translate((size.width - ControllerArtwork.SOURCE_WIDTH * factor) / 2, (size.height - ControllerArtwork.SOURCE_HEIGHT * factor) / 2) {
            scale(factor, factor, Offset.Zero) {
                drawImage(body)
                if (!rear) listOf(leftCap to (lx to ly), rightCap to (rx to ry)).forEachIndexed { index, (cap, axis) ->
                    val travel = layout.sticks[index].travel
                    translate(axis.first.coerceIn(-1f, 1f) * travel, axis.second.coerceIn(-1f, 1f) * travel) { drawImage(cap) }
                }
                val center = artwork.homeCenter
                if (!rear && center != null && home > 0f) {
                    drawCircle(accent.copy(alpha = .65f * home), 53f, Offset(center.first, center.second))
                    drawCircle(accent.copy(alpha = home), 53f, Offset(center.first, center.second), style = Stroke(7f))
                }
                // Trigger travel: a fill that rises with the analog value, drawn over the trigger on the back view.
                if (rear) layout.triggers.forEach { trigger ->
                    val value = glow[trigger.key] ?: 0f
                    if (value > .005f) {
                        val b = trigger.bounds
                        val top = b.bottom - b.height * value
                        drawRoundRect(accent.copy(alpha = .35f), Offset(b.left, top), androidx.compose.ui.geometry.Size(b.width, b.bottom - top),
                            androidx.compose.ui.geometry.CornerRadius(18f))
                    }
                }
                regions.forEach { region ->
                    val key = region.key
                    val pressed = glow[key] ?: 0f
                    val learning = input?.learningPaddle == key
                    val changed = mappings[key]?.let { it != key } == true
                    if (pressed > .01f || learning || changed || onPick != null) {
                        val fill = maxOf(.65f * pressed, if (changed) .3f else if (onPick != null) .09f else 0f)
                        drawPath(region.path, accent.copy(alpha = fill))
                        drawPath(region.path, if (pressed > .5f || learning || changed) accent else Color.White.copy(alpha = .6f),
                            style = Stroke(3f + 4f * pressed))
                    }
                    if (rear && key in artwork.rearButtons || changed) {
                        val label = if (changed) "${key.label} → ${mappings[key]?.label}" else key.label
                        val c = region.bounds.center
                        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.White.toArgb(); textSize = 26f; textAlign = android.graphics.Paint.Align.CENTER
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                        val width = paint.measureText(label) + 30f
                        drawRoundRect(ink.copy(alpha = .92f), Offset(c.x - width / 2, c.y - 22f),
                            androidx.compose.ui.geometry.Size(width, 44f), androidx.compose.ui.geometry.CornerRadius(16f))
                        drawContext.canvas.nativeCanvas.drawText(label, c.x, c.y + 9f, paint)
                    }
                }
            }
        }
    }
}

/**
 * A small, memory-light rendering of the product photograph (front body plus stick caps, decoded at
 * quarter size) for lists and pickers. Falls back to the drawn generic gamepad.
 */
@Composable fun ArtworkThumbnail(artwork: ControllerArtwork?, modifier: Modifier = Modifier) {
    if (artwork == null) { GenericControllerArt(modifier); return }
    val resources = LocalResources.current
    val layers = remember(artwork.drawablePrefix) {
        val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
        drawables(artwork).let { listOf(it[0], it[2], it[3]) }.mapNotNull { id ->
            android.graphics.BitmapFactory.decodeResource(resources, id, options)?.asImageBitmap()
        }
    }
    Canvas(modifier.semantics { contentDescription = artwork.productName }) {
        val first = layers.firstOrNull() ?: return@Canvas
        val factor = minOf(size.width / first.width, size.height / first.height)
        translate((size.width - first.width * factor) / 2, (size.height - first.height * factor) / 2) {
            scale(factor, factor, Offset.Zero) { layers.forEach { drawImage(it) } }
        }
    }
}
