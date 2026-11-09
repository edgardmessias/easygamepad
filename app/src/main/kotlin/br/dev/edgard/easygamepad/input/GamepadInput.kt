// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
@file:Suppress("DEPRECATION")
package br.dev.edgard.easygamepad.input

import android.content.Context
import android.hardware.input.InputManager
import android.os.VibrationEffect
import android.os.Build
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import br.dev.edgard.easygamepad.protocol.PadKey
import kotlin.math.roundToInt

data class InputPad(val id: Int, val name: String, val vendor: Int, val product: Int, val canVibrate: Boolean, val descriptor: String = "",
    val batteryPercent: Int? = null, val batteryCharging: Boolean = false)
data class InputState(
    val pads: List<InputPad> = emptyList(), val selectedId: Int? = null,
    val pressed: Set<Int> = emptySet(), val tested: Set<Int> = emptySet(),
    val axes: Map<Int, Float> = emptyMap(), val samples: List<Pair<Float, Float>> = emptyList(),
    val events: Int = 0,
    val paddleBindings: Map<PadKey, Int> = emptyMap(), val learningPaddle: PadKey? = null,
    val rightSamples: List<Pair<Float, Float>> = emptyList(),
)
class GamepadInput(context: Context) : InputManager.InputDeviceListener {
    private val manager = context.getSystemService(InputManager::class.java)
    private val sessions = InputSessions()
    val state = sessions.state
    init { manager.registerInputDeviceListener(this, null); refresh() }
    private fun isPad(device: InputDevice): Boolean {
        val source = device.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            device.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        // Some fingerprint readers advertise JOYSTICK. Require actual gamepad controls.
        val hasSticks = device.getMotionRange(MotionEvent.AXIS_X) != null && device.getMotionRange(MotionEvent.AXIS_Y) != null
        val external = if (Build.VERSION.SDK_INT >= 29) device.isExternal else device.vendorId != 0 || device.productId != 0
        val externalButtons = external && device.hasKeys(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B).any { it }
        return source && !device.isVirtual && (hasSticks || externalButtons)
    }
    private fun refresh() {
        val pads = manager.inputDeviceIds.map { manager.getInputDevice(it) }.filterNotNull().filter { isPad(it) }.map {
            val battery = if (Build.VERSION.SDK_INT >= 31) runCatching {
                val state = it.batteryState
                val capacity = state.takeIf { value -> value.isPresent }?.capacity
                val percent = capacity?.takeIf { value -> value.isFinite() && value in 0f..1f }?.let { value -> (value * 100).roundToInt() }
                percent to (state.status == android.hardware.BatteryState.STATUS_CHARGING)
            }.getOrNull() else null
            InputPad(it.id, it.name, it.vendorId, it.productId, it.vibrator.hasVibrator(), it.descriptor,
                battery?.first, battery?.second == true)
        }
        sessions.refresh(pads)
    }
    fun select(id: Int?) = sessions.select(id)
    fun refreshDevices() = refresh()
    fun reset() = sessions.reset()
    fun clearPressed() = sessions.clearPressed()
    fun identifyPaddle(paddle: PadKey?) {
        require(paddle == null || paddle.code in 1..6)
        state.value.selectedId?.let { sessions.update(it, state.value.copy(learningPaddle = paddle, pressed = emptySet())) }
    }
    fun key(event: KeyEvent): Boolean {
        val pad = manager.getInputDevice(event.deviceId) ?: return false
        if (!isPad(pad)) return false
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return false
        val old = sessions.snapshot(event.deviceId)
        val learning = old.learningPaddle.takeIf { event.deviceId == state.value.selectedId && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 }
        sessions.update(event.deviceId, old.copy(
            pressed = if (event.action == KeyEvent.ACTION_DOWN) old.pressed + event.keyCode else old.pressed - event.keyCode,
            tested = old.tested + event.keyCode, events = old.events + 1,
            paddleBindings = if (learning != null) old.paddleBindings + (learning to event.keyCode) else old.paddleBindings,
            learningPaddle = if (learning != null) null else old.learningPaddle,
        ))
        return true
    }
    fun motion(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK) return false
        val pad = manager.getInputDevice(event.deviceId) ?: return false
        if (!isPad(pad)) return false
        val axes = pad.motionRanges.filter { it.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK }.associate { it.axis to event.getAxisValue(it.axis) }
        val hats = buildSet {
            if ((axes[MotionEvent.AXIS_HAT_X] ?: 0f) < -.5f) add(KeyEvent.KEYCODE_DPAD_LEFT)
            if ((axes[MotionEvent.AXIS_HAT_X] ?: 0f) > .5f) add(KeyEvent.KEYCODE_DPAD_RIGHT)
            if ((axes[MotionEvent.AXIS_HAT_Y] ?: 0f) < -.5f) add(KeyEvent.KEYCODE_DPAD_UP)
            if ((axes[MotionEvent.AXIS_HAT_Y] ?: 0f) > .5f) add(KeyEvent.KEYCODE_DPAD_DOWN)
        }
        val old = sessions.snapshot(event.deviceId)
        sessions.update(event.deviceId, old.copy(axes = axes, events = old.events + 1, tested = old.tested + hats,
            samples = (old.samples + ((axes[MotionEvent.AXIS_X] ?: 0f) to (axes[MotionEvent.AXIS_Y] ?: 0f))).takeLast(120),
            rightSamples = (old.rightSamples + ((axes[MotionEvent.AXIS_Z] ?: axes[MotionEvent.AXIS_RX] ?: 0f) to
                (axes[MotionEvent.AXIS_RZ] ?: axes[MotionEvent.AXIS_RY] ?: 0f))).takeLast(120)))
        return true
    }
    fun rumble(): Boolean {
        val pad = state.value.selectedId?.let { manager.getInputDevice(it) } ?: return false
        if (!pad.vibrator.hasVibrator()) return false
        pad.vibrator.vibrate(VibrationEffect.createOneShot(250, VibrationEffect.DEFAULT_AMPLITUDE))
        return true
    }
    override fun onInputDeviceAdded(deviceId: Int) = refresh()
    override fun onInputDeviceRemoved(deviceId: Int) = refresh()
    override fun onInputDeviceChanged(deviceId: Int) = refresh()
}
