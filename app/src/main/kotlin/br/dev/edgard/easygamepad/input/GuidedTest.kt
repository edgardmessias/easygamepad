// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.input

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Small guided-test session. It records Android input only after an explicit start,
 * and only for the bound controller and input id.
 *
 * Direction and trigger cutoffs mark that the user moved a control. They are not health scores.
 * Rest is one user-confirmed reading of the current axes, never an averaged drift measurement.
 * Rear paddles are recorded only when the user confirms that physical paddle.
 */
object GuidedSignals {
    const val BUTTON_A = 96
    const val BUTTON_B = 97
    const val BUTTON_X = 99
    const val BUTTON_Y = 100
    const val BUTTON_L1 = 102
    const val BUTTON_R1 = 103
    const val BUTTON_L2 = 104
    const val BUTTON_R2 = 105
    const val BUTTON_THUMBL = 106
    const val BUTTON_THUMBR = 107
    const val BUTTON_START = 108
    const val BUTTON_SELECT = 109
    const val DPAD_UP = 19
    const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22

    const val AXIS_X = 0
    const val AXIS_Y = 1
    const val AXIS_Z = 11
    const val AXIS_RX = 12
    const val AXIS_RY = 13
    const val AXIS_RZ = 14
    const val AXIS_HAT_X = 15
    const val AXIS_HAT_Y = 16
    const val AXIS_LTRIGGER = 17
    const val AXIS_RTRIGGER = 18
    const val AXIS_GAS = 22
    const val AXIS_BRAKE = 23

    /** Android sign: negative Y is up, matching the live hat handling in the tester. */
    const val DIRECTION = 0.70f
    const val RANGE = 0.85f
    const val CENTER = 0.25f
    const val TRIGGER_FULL = 0.95f
    const val TRIGGER_ARM = 0.15f
    const val DIGITAL_LOW = 0.20f
    const val DIGITAL_HIGH = 0.80f
    const val HAT = 0.50f
}

object GuidedCatalog {
    val buttons = listOf("A", "B", "X", "Y", "LB", "RB", "L3", "R3", "View", "Menu")
    val dpad = listOf("Up", "Down", "Left", "Right")
    val triggers = listOf("LT", "RT")
    val sticks = listOf("LS", "RS")
    val stickDirections = listOf(
        GuidedDirection.UP,
        GuidedDirection.DOWN,
        GuidedDirection.LEFT,
        GuidedDirection.RIGHT,
    )
}

enum class GuidedLayout { STANDARD, GENERIC }

enum class GuidedPhase { DISABLED, IDLE, ACTIVE, PAUSED, COMPLETED }

enum class GuidedGroup { BUTTON, SHOULDER, DPAD, TRIGGER, STICK, REST, REAR }

enum class GuidedRole { REQUIRED, OPTIONAL }

enum class GuidedMark { WAITING, OBSERVED, DIGITAL_ONLY, NOT_VERIFIED }

enum class GuidedDirection { UP, DOWN, LEFT, RIGHT }

enum class GuidedApplyStatus { MATCHED, MISMATCH, UNCONFIRMED }

data class GuidedBinding(
    val controllerId: String,
    val inputId: Int,
    /** User-facing model name. Callers must not put a MAC or descriptor here. */
    val modelLabel: String,
    /** User-facing input name and runtime id, without a descriptor. */
    val inputLabel: String,
    val synthetic: Boolean,
)

data class GuidedPlan(
    val layout: GuidedLayout,
    /** Physical rear labels, such as M1. Empty when the model has none. */
    val rearIds: List<String>,
)

data class GuidedSample(
    val controllerId: String,
    val inputId: Int,
    /** Monotonic id from the live input stream. The same id is ignored, so recomposition cannot stack samples. */
    val eventId: Int,
    val pressed: Set<Int>,
    val axes: Map<Int, Float>,
    val pressCounts: Map<Int, Int> = emptyMap(),
)

data class AxisPair(val x: Float?, val y: Float?)

data class TriggerObservation(
    val peak: Float? = null,
    val digital: Boolean = false,
    val intermediateSeen: Boolean = false,
) {
    val fullTravel: Boolean get() = intermediateSeen && peak != null && peak >= GuidedSignals.TRIGGER_FULL
}

data class StickObservation(
    val directions: Set<GuidedDirection> = emptySet(),
    val minX: Float? = null,
    val maxX: Float? = null,
    val minY: Float? = null,
    val maxY: Float? = null,
    val maxRadius: Float = 0f,
) {
    val rangeObserved: Boolean get() = maxRadius >= GuidedSignals.RANGE
}

data class RestObservation(
    val leftX: Float?,
    val leftY: Float?,
    val rightX: Float?,
    val rightY: Float?,
    val leftTrigger: Float?,
    val rightTrigger: Float?,
) {
    val anyFinite: Boolean
        get() = listOf(leftX, leftY, rightX, rightY, leftTrigger, rightTrigger).any { it != null }
}

data class GuidedApplyNote(
    val status: GuidedApplyStatus,
    val fields: List<String> = emptyList(),
)

data class GuidedRow(
    val id: String,
    val group: GuidedGroup,
    val role: GuidedRole,
    val observed: Boolean,
    val mark: GuidedMark,
)

data class GuidedProgress(
    val observedRequired: Int,
    val totalRequired: Int,
    val observedOptional: Int,
    val totalOptional: Int,
) {
    val fraction: Float
        get() = when {
            totalRequired > 0 -> observedRequired.toFloat() / totalRequired.toFloat()
            totalOptional > 0 -> observedOptional.toFloat() / totalOptional.toFloat()
            else -> 0f
        }
}

data class GuidedRestResult(val session: GuidedSession, val captured: Boolean)

data class GuidedSession(
    val phase: GuidedPhase,
    val binding: GuidedBinding?,
    val plan: GuidedPlan,
    val buttons: Set<String> = emptySet(),
    val dpad: Set<String> = emptySet(),
    val triggers: Map<String, TriggerObservation> = emptyMap(),
    val sticks: Map<String, StickObservation> = emptyMap(),
    val rest: RestObservation? = null,
    val rearConfirmed: Set<String> = emptySet(),
    val stoppedEarly: Boolean = false,
    val lastEventId: Int? = null,
    val previousPressed: Set<Int> = emptySet(),
    val previousPressCounts: Map<Int, Int> = emptyMap(),
    val previousLeft: AxisPair = AxisPair(null, null),
    val previousRight: AxisPair = AxisPair(null, null),
    val previousHat: AxisPair = AxisPair(null, null),
    val leftReady: Boolean = false,
    val rightReady: Boolean = false,
    val hatReady: Boolean = false,
    val leftTriggerArmedHigh: Boolean = false,
    val rightTriggerArmedHigh: Boolean = false,
    val previousLeftTrigger: Float? = null,
    val previousRightTrigger: Float? = null,
)

object GuidedTest {
    fun disabled(): GuidedSession = GuidedSession(
        phase = GuidedPhase.DISABLED,
        binding = null,
        plan = GuidedPlan(GuidedLayout.GENERIC, emptyList()),
    )

    fun idle(binding: GuidedBinding, plan: GuidedPlan): GuidedSession =
        GuidedSession(phase = GuidedPhase.IDLE, binding = binding, plan = plan)

    fun align(session: GuidedSession, binding: GuidedBinding?, plan: GuidedPlan): GuidedSession {
        if (binding == null) {
            return if (session.phase == GuidedPhase.DISABLED && session.binding == null) session else disabled()
        }
        val sameDevice = session.binding?.controllerId == binding.controllerId &&
            session.binding.inputId == binding.inputId &&
            session.plan == plan &&
            session.phase != GuidedPhase.DISABLED
        if (!sameDevice) return idle(binding, plan)
        return if (session.binding == binding) session else session.copy(binding = binding)
    }

    /** Clears this device's observations and starts. The sample is a baseline and is not counted. */
    fun begin(session: GuidedSession, sample: GuidedSample): GuidedSession {
        if (session.binding == null || session.phase == GuidedPhase.DISABLED || !matches(session, sample)) return session
        val cleared = session.copy(
            phase = GuidedPhase.ACTIVE,
            buttons = emptySet(),
            dpad = emptySet(),
            triggers = emptyMap(),
            sticks = emptyMap(),
            rest = null,
            rearConfirmed = emptySet(),
            stoppedEarly = false,
        )
        return reseed(cleared, sample)
    }

    fun pause(session: GuidedSession): GuidedSession =
        if (session.phase == GuidedPhase.ACTIVE) session.copy(phase = GuidedPhase.PAUSED) else session

    /** Resume without counting controls that are already held. */
    fun resume(session: GuidedSession, sample: GuidedSample): GuidedSession {
        if (session.phase != GuidedPhase.PAUSED || !matches(session, sample)) return session
        return reseed(session, sample).copy(phase = GuidedPhase.ACTIVE)
    }

    fun reset(session: GuidedSession): GuidedSession {
        val binding = session.binding ?: return disabled()
        if (session.phase == GuidedPhase.DISABLED) return disabled()
        return idle(binding, session.plan)
    }

    fun finish(session: GuidedSession): GuidedSession {
        if (session.phase != GuidedPhase.ACTIVE && session.phase != GuidedPhase.PAUSED) return session
        val early = session.plan.layout == GuidedLayout.STANDARD && !allRequired(session)
        return session.copy(phase = GuidedPhase.COMPLETED, stoppedEarly = early)
    }

    fun ingest(session: GuidedSession, sample: GuidedSample?): GuidedSession {
        if (sample == null || session.phase != GuidedPhase.ACTIVE || session.binding == null) return session
        if (!matches(session, sample)) return session
        if (sample.eventId == session.lastEventId) return session
        if (session.lastEventId != null && sample.eventId < session.lastEventId) return reseed(session, sample)
        return completeIfReady(applySample(session, sample))
    }

    fun captureRest(session: GuidedSession, sample: GuidedSample): GuidedRestResult {
        if (session.phase != GuidedPhase.ACTIVE || !matches(session, sample)) return GuidedRestResult(session, false)
        val rest = readRest(sample.axes)
        if (!rest.anyFinite) return GuidedRestResult(session, false)
        return GuidedRestResult(completeIfReady(session.copy(rest = rest)), true)
    }

    fun confirmRear(session: GuidedSession, rearId: String): GuidedSession {
        if (session.phase != GuidedPhase.ACTIVE) return session
        if (rearId !in session.plan.rearIds || rearId in session.rearConfirmed) return session
        return session.copy(rearConfirmed = session.rearConfirmed + rearId)
    }

    /**
     * Last X10 read-back, only when this selected controller is the ready KRLY link.
     * The addresses are compared and then dropped. A synthetic demo never keeps a note.
     */
    fun applyNote(
        synthetic: Boolean,
        deviceAddress: String?,
        linkReady: Boolean,
        linkAddress: String?,
        status: GuidedApplyStatus?,
        fields: List<String> = emptyList(),
    ): GuidedApplyNote? {
        if (synthetic || status == null || !linkReady) return null
        if (deviceAddress.isNullOrBlank() || linkAddress.isNullOrBlank()) return null
        if (!deviceAddress.equals(linkAddress, ignoreCase = true)) return null
        return GuidedApplyNote(status, fields)
    }

    fun rows(session: GuidedSession): List<GuidedRow> {
        val required = session.plan.layout == GuidedLayout.STANDARD
        val role = if (required) GuidedRole.REQUIRED else GuidedRole.OPTIONAL
        val buttons = GuidedCatalog.buttons.map { id ->
            val group = if (id == "LB" || id == "RB") GuidedGroup.SHOULDER else GuidedGroup.BUTTON
            row(id, group, role, id in session.buttons, if (id in session.buttons) GuidedMark.OBSERVED else GuidedMark.WAITING)
        }
        val dpad = GuidedCatalog.dpad.map { id ->
            row(id, GuidedGroup.DPAD, role, id in session.dpad, if (id in session.dpad) GuidedMark.OBSERVED else GuidedMark.WAITING)
        }
        val triggers = GuidedCatalog.triggers.map { id ->
            val fact = session.triggers[id]
            val mark = when {
                fact?.fullTravel == true -> GuidedMark.OBSERVED
                fact?.digital == true || (fact?.peak ?: 0f) >= GuidedSignals.TRIGGER_FULL -> GuidedMark.DIGITAL_ONLY
                else -> GuidedMark.WAITING
            }
            row(id, GuidedGroup.TRIGGER, role, fact?.fullTravel == true, mark)
        }
        val sticks = GuidedCatalog.sticks.flatMap { stick ->
            val fact = session.sticks[stick]
            GuidedCatalog.stickDirections.map { direction ->
                val id = stickDirectionId(stick, direction)
                val seen = fact?.directions?.contains(direction) == true
                row(id, GuidedGroup.STICK, role, seen, if (seen) GuidedMark.OBSERVED else GuidedMark.WAITING)
            } + run {
                val seen = fact?.rangeObserved == true
                val id = stickRangeId(stick)
                row(id, GuidedGroup.STICK, role, seen, if (seen) GuidedMark.OBSERVED else GuidedMark.WAITING)
            }
        }
        val restSeen = session.rest?.anyFinite == true
        val rest = row("rest", GuidedGroup.REST, role, restSeen, if (restSeen) GuidedMark.OBSERVED else GuidedMark.WAITING)
        val rear = session.plan.rearIds.map { id ->
            val seen = id in session.rearConfirmed
            row("rear:$id", GuidedGroup.REAR, GuidedRole.OPTIONAL, seen, if (seen) GuidedMark.OBSERVED else GuidedMark.NOT_VERIFIED)
        }
        return buttons + dpad + triggers + sticks + rest + rear
    }

    fun progress(session: GuidedSession): GuidedProgress {
        val items = rows(session)
        val required = items.filter { it.role == GuidedRole.REQUIRED }
        val optional = items.filter { it.role == GuidedRole.OPTIONAL }
        return GuidedProgress(
            observedRequired = required.count { it.observed },
            totalRequired = required.size,
            observedOptional = optional.count { it.observed },
            totalOptional = optional.size,
        )
    }

    fun stickDirectionId(stick: String, direction: GuidedDirection): String = "$stick-${direction.name.lowercase()}"

    fun stickRangeId(stick: String): String = "$stick-range"
}

private fun row(id: String, group: GuidedGroup, role: GuidedRole, observed: Boolean, mark: GuidedMark) =
    GuidedRow(id, group, role, observed, mark)

private fun matches(session: GuidedSession, sample: GuidedSample): Boolean {
    val binding = session.binding ?: return false
    return sample.controllerId == binding.controllerId && sample.inputId == binding.inputId
}

private fun reseed(session: GuidedSession, sample: GuidedSample): GuidedSession {
    val left = leftPoint(sample.axes)
    val right = rightPoint(sample.axes)
    val hat = hatPoint(sample.axes)
    val leftTrigger = leftTrigger(sample.axes)
    val rightTrigger = rightTrigger(sample.axes)
    return session.copy(
        lastEventId = sample.eventId,
        previousPressed = sample.pressed,
        previousPressCounts = sample.pressCounts,
        previousLeft = left,
        previousRight = right,
        previousHat = hat,
        leftReady = centered(left),
        rightReady = centered(right),
        hatReady = hatCentered(hat),
        leftTriggerArmedHigh = leftTrigger != null && leftTrigger >= GuidedSignals.TRIGGER_ARM,
        rightTriggerArmedHigh = rightTrigger != null && rightTrigger >= GuidedSignals.TRIGGER_ARM,
        previousLeftTrigger = leftTrigger,
        previousRightTrigger = rightTrigger,
    )
}

private data class TriggerStep(val observation: TriggerObservation, val armedHigh: Boolean)

private fun applySample(session: GuidedSession, sample: GuidedSample): GuidedSession {
    val down = (sample.pressed - session.previousPressed) + sample.pressCounts.filter { (key, count) ->
        count > (session.previousPressCounts[key] ?: 0)
    }.keys
    var buttons = session.buttons
    var dpad = session.dpad
    down.forEach { code ->
        buttonId(code)?.let { buttons = buttons + it }
        dpadKeyId(code)?.let { dpad = dpad + it }
    }
    val leftTrigger = leftTrigger(sample.axes)
    val rightTrigger = rightTrigger(sample.axes)
    val leftStep = stepTrigger(
        session.triggers["LT"],
        leftTrigger,
        session.leftTriggerArmedHigh,
        GuidedSignals.BUTTON_L2 in down,
        session.previousLeftTrigger,
    )
    val rightStep = stepTrigger(
        session.triggers["RT"],
        rightTrigger,
        session.rightTriggerArmedHigh,
        GuidedSignals.BUTTON_R2 in down,
        session.previousRightTrigger,
    )
    var triggers = session.triggers
    if (leftStep.observation.peak != null || leftStep.observation.digital) triggers = triggers + ("LT" to leftStep.observation)
    if (rightStep.observation.peak != null || rightStep.observation.digital) triggers = triggers + ("RT" to rightStep.observation)

    var sticks = session.sticks
    val left = leftPoint(sample.axes)
    var leftReady = session.leftReady
    var previousLeft = session.previousLeft
    if (hasReading(left)) {
        if (leftReady || directions(left) != directions(previousLeft) || centered(left)) {
            leftReady = true
            val entered = directions(left) - directions(previousLeft)
            sticks = sticks + ("LS" to accumulate(sticks["LS"] ?: StickObservation(), left, entered))
        }
        previousLeft = left
    }
    val right = rightPoint(sample.axes)
    var rightReady = session.rightReady
    var previousRight = session.previousRight
    if (hasReading(right)) {
        if (rightReady || directions(right) != directions(previousRight) || centered(right)) {
            rightReady = true
            val entered = directions(right) - directions(previousRight)
            sticks = sticks + ("RS" to accumulate(sticks["RS"] ?: StickObservation(), right, entered))
        }
        previousRight = right
    }
    val hat = hatPoint(sample.axes)
    var hatReady = session.hatReady
    var previousHat = session.previousHat
    if (hasReading(hat)) {
        if (hatReady || hatIds(hat) != hatIds(previousHat) || hatCentered(hat)) {
            hatReady = true
            dpad = dpad + (hatIds(hat) - hatIds(previousHat))
        }
        previousHat = hat
    }
    return session.copy(
        buttons = buttons,
        dpad = dpad,
        triggers = triggers,
        sticks = sticks,
        lastEventId = sample.eventId,
        previousPressed = sample.pressed,
        previousPressCounts = sample.pressCounts,
        previousLeft = previousLeft,
        previousRight = previousRight,
        previousHat = previousHat,
        leftReady = leftReady,
        rightReady = rightReady,
        hatReady = hatReady,
        leftTriggerArmedHigh = leftStep.armedHigh,
        rightTriggerArmedHigh = rightStep.armedHigh,
        previousLeftTrigger = leftTrigger ?: session.previousLeftTrigger,
        previousRightTrigger = rightTrigger ?: session.previousRightTrigger,
    )
}

private fun stepTrigger(
    current: TriggerObservation?,
    analog: Float?,
    armedHigh: Boolean,
    keyEdge: Boolean,
    previous: Float?,
): TriggerStep {
    var observation = current ?: TriggerObservation()
    var armed = armedHigh
    if (keyEdge) observation = observation.copy(digital = true)
    if (armed) {
        if (analog != null && analog < GuidedSignals.TRIGGER_ARM) armed = false
    } else if (analog != null) {
        if (previous != null && previous < GuidedSignals.DIGITAL_LOW && analog >= GuidedSignals.DIGITAL_HIGH) {
            observation = observation.copy(digital = true)
        }
        val peak = max(observation.peak ?: analog, analog)
        observation = observation.copy(
            peak = peak,
            intermediateSeen = observation.intermediateSeen || analog in GuidedSignals.DIGITAL_LOW..GuidedSignals.DIGITAL_HIGH,
        )
    }
    return TriggerStep(observation, armed)
}

private fun completeIfReady(session: GuidedSession): GuidedSession {
    if (session.phase != GuidedPhase.ACTIVE || session.plan.layout != GuidedLayout.STANDARD) return session
    if (!allRequired(session)) return session
    return session.copy(phase = GuidedPhase.COMPLETED, stoppedEarly = false)
}

private fun allRequired(session: GuidedSession): Boolean {
    if (!GuidedCatalog.buttons.all { it in session.buttons }) return false
    if (!GuidedCatalog.dpad.all { it in session.dpad }) return false
    if (session.triggers["LT"]?.fullTravel != true || session.triggers["RT"]?.fullTravel != true) return false
    if (!stickComplete(session.sticks["LS"]) || !stickComplete(session.sticks["RS"])) return false
    return session.rest?.anyFinite == true
}

private fun stickComplete(stick: StickObservation?): Boolean {
    if (stick == null || !stick.rangeObserved) return false
    return GuidedCatalog.stickDirections.all { it in stick.directions }
}

private fun buttonId(code: Int): String? = when (code) {
    GuidedSignals.BUTTON_A -> "A"
    GuidedSignals.BUTTON_B -> "B"
    GuidedSignals.BUTTON_X -> "X"
    GuidedSignals.BUTTON_Y -> "Y"
    GuidedSignals.BUTTON_L1 -> "LB"
    GuidedSignals.BUTTON_R1 -> "RB"
    GuidedSignals.BUTTON_THUMBL -> "L3"
    GuidedSignals.BUTTON_THUMBR -> "R3"
    GuidedSignals.BUTTON_SELECT -> "View"
    GuidedSignals.BUTTON_START -> "Menu"
    else -> null
}

private fun dpadKeyId(code: Int): String? = when (code) {
    GuidedSignals.DPAD_UP -> "Up"
    GuidedSignals.DPAD_DOWN -> "Down"
    GuidedSignals.DPAD_LEFT -> "Left"
    GuidedSignals.DPAD_RIGHT -> "Right"
    else -> null
}

private fun finite(axes: Map<Int, Float>, axis: Int): Float? = axes[axis]?.takeIf { it.isFinite() }

private fun alias(axes: Map<Int, Float>, primary: Int, fallback: Int): Float? =
    finite(axes, primary) ?: finite(axes, fallback)

private fun leftPoint(axes: Map<Int, Float>) = AxisPair(finite(axes, GuidedSignals.AXIS_X), finite(axes, GuidedSignals.AXIS_Y))

/** Same priority as the live plot: Z before RX, RZ before RY. */
private fun rightPoint(axes: Map<Int, Float>) = AxisPair(
    alias(axes, GuidedSignals.AXIS_Z, GuidedSignals.AXIS_RX),
    alias(axes, GuidedSignals.AXIS_RZ, GuidedSignals.AXIS_RY),
)

private fun hatPoint(axes: Map<Int, Float>) = AxisPair(finite(axes, GuidedSignals.AXIS_HAT_X), finite(axes, GuidedSignals.AXIS_HAT_Y))

private fun leftTrigger(axes: Map<Int, Float>): Float? =
    listOfNotNull(finite(axes, GuidedSignals.AXIS_LTRIGGER), finite(axes, GuidedSignals.AXIS_BRAKE)).maxOrNull()

private fun rightTrigger(axes: Map<Int, Float>): Float? =
    listOfNotNull(finite(axes, GuidedSignals.AXIS_RTRIGGER), finite(axes, GuidedSignals.AXIS_GAS)).maxOrNull()

private fun readRest(axes: Map<Int, Float>): RestObservation {
    val left = leftPoint(axes)
    val right = rightPoint(axes)
    return RestObservation(left.x, left.y, right.x, right.y, leftTrigger(axes), rightTrigger(axes))
}

private fun directions(point: AxisPair): Set<GuidedDirection> = buildSet {
    point.x?.let { x ->
        if (x <= -GuidedSignals.DIRECTION) add(GuidedDirection.LEFT)
        if (x >= GuidedSignals.DIRECTION) add(GuidedDirection.RIGHT)
    }
    point.y?.let { y ->
        if (y <= -GuidedSignals.DIRECTION) add(GuidedDirection.UP)
        if (y >= GuidedSignals.DIRECTION) add(GuidedDirection.DOWN)
    }
}

private fun hatIds(point: AxisPair): Set<String> = buildSet {
    point.x?.let { x ->
        if (x <= -GuidedSignals.HAT) add("Left")
        if (x >= GuidedSignals.HAT) add("Right")
    }
    point.y?.let { y ->
        if (y <= -GuidedSignals.HAT) add("Up")
        if (y >= GuidedSignals.HAT) add("Down")
    }
}

private fun hasReading(point: AxisPair): Boolean = point.x != null || point.y != null

private fun centered(point: AxisPair): Boolean {
    if (!hasReading(point)) return false
    val x = point.x ?: 0f
    val y = point.y ?: 0f
    return abs(x) < GuidedSignals.CENTER && abs(y) < GuidedSignals.CENTER
}

private fun hatCentered(point: AxisPair): Boolean {
    if (!hasReading(point)) return false
    val x = point.x ?: 0f
    val y = point.y ?: 0f
    return abs(x) < GuidedSignals.HAT && abs(y) < GuidedSignals.HAT
}

private fun accumulate(stick: StickObservation, point: AxisPair, entered: Set<GuidedDirection>): StickObservation {
    val radius = if (point.x != null && point.y != null) hypot(point.x.toDouble(), point.y.toDouble()).toFloat() else null
    return stick.copy(
        directions = stick.directions + entered,
        minX = minFinite(stick.minX, point.x),
        maxX = maxFinite(stick.maxX, point.x),
        minY = minFinite(stick.minY, point.y),
        maxY = maxFinite(stick.maxY, point.y),
        maxRadius = if (radius != null) max(stick.maxRadius, radius) else stick.maxRadius,
    )
}

private fun minFinite(current: Float?, value: Float?): Float? = when {
    value == null -> current
    current == null -> value
    else -> min(current, value)
}

private fun maxFinite(current: Float?, value: Float?): Float? = when {
    value == null -> current
    current == null -> value
    else -> max(current, value)
}
