// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.input

import android.view.KeyEvent
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedTestTest {
    private val mac = "AA:BB:CC:DD:EE:FF"
    private val binding = GuidedBinding("dev-1", 3, "EasySMX X10", "Wireless Controller · #3", synthetic = false)
    private val other = binding.copy(controllerId = "dev-2", inputId = 9, modelLabel = "Other pad")
    private val standard = GuidedPlan(GuidedLayout.STANDARD, listOf("M1", "M2"))
    private val generic = GuidedPlan(GuidedLayout.GENERIC, emptyList())

    @Test fun signalConstantsMatchAndroid() {
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, GuidedSignals.BUTTON_A)
        assertEquals(KeyEvent.KEYCODE_BUTTON_B, GuidedSignals.BUTTON_B)
        assertEquals(KeyEvent.KEYCODE_BUTTON_X, GuidedSignals.BUTTON_X)
        assertEquals(KeyEvent.KEYCODE_BUTTON_Y, GuidedSignals.BUTTON_Y)
        assertEquals(KeyEvent.KEYCODE_BUTTON_L1, GuidedSignals.BUTTON_L1)
        assertEquals(KeyEvent.KEYCODE_BUTTON_R1, GuidedSignals.BUTTON_R1)
        assertEquals(KeyEvent.KEYCODE_BUTTON_L2, GuidedSignals.BUTTON_L2)
        assertEquals(KeyEvent.KEYCODE_BUTTON_R2, GuidedSignals.BUTTON_R2)
        assertEquals(KeyEvent.KEYCODE_BUTTON_THUMBL, GuidedSignals.BUTTON_THUMBL)
        assertEquals(KeyEvent.KEYCODE_BUTTON_THUMBR, GuidedSignals.BUTTON_THUMBR)
        assertEquals(KeyEvent.KEYCODE_BUTTON_START, GuidedSignals.BUTTON_START)
        assertEquals(KeyEvent.KEYCODE_BUTTON_SELECT, GuidedSignals.BUTTON_SELECT)
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, GuidedSignals.DPAD_UP)
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, GuidedSignals.DPAD_DOWN)
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, GuidedSignals.DPAD_LEFT)
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, GuidedSignals.DPAD_RIGHT)
        assertEquals(MotionEvent.AXIS_X, GuidedSignals.AXIS_X)
        assertEquals(MotionEvent.AXIS_Y, GuidedSignals.AXIS_Y)
        assertEquals(MotionEvent.AXIS_Z, GuidedSignals.AXIS_Z)
        assertEquals(MotionEvent.AXIS_RX, GuidedSignals.AXIS_RX)
        assertEquals(MotionEvent.AXIS_RY, GuidedSignals.AXIS_RY)
        assertEquals(MotionEvent.AXIS_RZ, GuidedSignals.AXIS_RZ)
        assertEquals(MotionEvent.AXIS_HAT_X, GuidedSignals.AXIS_HAT_X)
        assertEquals(MotionEvent.AXIS_HAT_Y, GuidedSignals.AXIS_HAT_Y)
        assertEquals(MotionEvent.AXIS_LTRIGGER, GuidedSignals.AXIS_LTRIGGER)
        assertEquals(MotionEvent.AXIS_RTRIGGER, GuidedSignals.AXIS_RTRIGGER)
        assertEquals(MotionEvent.AXIS_GAS, GuidedSignals.AXIS_GAS)
        assertEquals(MotionEvent.AXIS_BRAKE, GuidedSignals.AXIS_BRAKE)
    }

    @Test fun pressesBeforeStartAndAHeldBaselineDoNotCount() {
        val idle = GuidedTest.idle(binding, standard)
        val ignored = GuidedTest.ingest(idle, sample(4, pressed = setOf(GuidedSignals.BUTTON_A)))
        assertSame(idle, ignored)
        assertTrue(ignored.buttons.isEmpty())
        val started = GuidedTest.begin(idle, sample(4, pressed = setOf(GuidedSignals.BUTTON_A)))
        assertEquals(GuidedPhase.ACTIVE, started.phase)
        assertTrue(started.buttons.isEmpty())
        val stillHeld = GuidedTest.ingest(started, sample(5, pressed = setOf(GuidedSignals.BUTTON_A)))
        assertTrue(stillHeld.buttons.isEmpty())
        val released = GuidedTest.ingest(stillHeld, sample(6))
        val again = GuidedTest.ingest(released, sample(7, pressed = setOf(GuidedSignals.BUTTON_A)))
        assertEquals(setOf("A"), again.buttons)
    }

    @Test fun otherControllerOrInputDoesNotLeakAndResetClearsThisSession() {
        var session = GuidedTest.begin(GuidedTest.idle(binding, standard), sample(1))
        session = GuidedTest.ingest(session, sample(2, pressed = setOf(GuidedSignals.BUTTON_B)))
        val foreign = GuidedTest.ingest(
            session,
            sample(3, pressed = setOf(GuidedSignals.BUTTON_X), controllerId = other.controllerId, inputId = other.inputId),
        )
        assertSame(session, foreign)
        val otherInput = GuidedTest.ingest(session, sample(3, pressed = setOf(GuidedSignals.BUTTON_Y), inputId = 8))
        assertSame(session, otherInput)
        assertEquals(setOf("B"), session.buttons)
        val reset = GuidedTest.reset(session)
        assertEquals(GuidedPhase.IDLE, reset.phase)
        assertTrue(reset.buttons.isEmpty())
        assertNull(reset.rest)
        val after = GuidedTest.ingest(reset, sample(4, pressed = setOf(GuidedSignals.BUTTON_B)))
        assertTrue(after.buttons.isEmpty())
        val switched = GuidedTest.align(session, other, standard)
        assertEquals(GuidedPhase.IDLE, switched.phase)
        assertEquals(other.controllerId, switched.binding?.controllerId)
        assertTrue(switched.buttons.isEmpty())
        val removed = GuidedTest.align(session, null, standard)
        assertEquals(GuidedTest.disabled(), removed)
        assertTrue(removed.buttons.isEmpty())
        assertSame(removed, GuidedTest.align(removed, null, standard))
    }

    @Test fun quickTapCountsAfterStartAndPauseBaselineExcludesPausedTaps() {
        val a = GuidedSignals.BUTTON_A
        val baseline = sample(1).copy(pressCounts = mapOf(a to 4))
        val started = GuidedTest.begin(GuidedTest.idle(binding, standard), baseline)
        assertTrue(GuidedTest.ingest(started, baseline).buttons.isEmpty())
        // The UI sees the released state after a full down/up pair between frames.
        val tapped = GuidedTest.ingest(started, sample(3).copy(pressCounts = mapOf(a to 5)))
        assertEquals(setOf("A"), tapped.buttons)
        val pausedTap = sample(5).copy(pressCounts = mapOf(a to 6, GuidedSignals.BUTTON_B to 1))
        val resumed = GuidedTest.resume(GuidedTest.pause(tapped), pausedTap)
        assertFalse("B" in GuidedTest.ingest(resumed, pausedTap.copy(eventId = 6)).buttons)
    }

    @Test fun keyCountersIncrementOncePerPressAndResetWithLiveInput() {
        val a = GuidedSignals.BUTTON_A
        val empty = InputState()
        val first = empty.pressCountsAfter(a, true)
        assertEquals(1, first[a])
        val held = empty.copy(pressed = setOf(a), pressCounts = first)
        assertEquals(first, held.pressCountsAfter(a, true))
        assertEquals(first, held.pressCountsAfter(a, false))
        assertEquals(2, held.copy(pressed = emptySet()).pressCountsAfter(a, true)[a])
        assertTrue(InputState().pressCounts.isEmpty())
    }

    @Test fun directionsAccumulateWithoutDuplicateSamplesAndSticksStayIndependent() {
        var session = GuidedTest.begin(GuidedTest.idle(binding, standard), sample(1, axes = centeredAxes()))
        session = GuidedTest.ingest(session, sample(2, axes = centeredAxes() + mapOf(GuidedSignals.AXIS_X to -0.8f)))
        session = GuidedTest.ingest(session, sample(3, axes = centeredAxes() + mapOf(GuidedSignals.AXIS_Y to -0.9f)))
        assertEquals(setOf(GuidedDirection.LEFT, GuidedDirection.UP), session.sticks["LS"]?.directions)
        assertTrue(session.sticks["RS"]?.directions.orEmpty().isEmpty())
        val duplicate = GuidedTest.ingest(
            session,
            sample(3, axes = centeredAxes() + mapOf(GuidedSignals.AXIS_X to 0.95f, GuidedSignals.AXIS_Z to 0.9f)),
        )
        assertSame(session, duplicate)
        session = GuidedTest.ingest(
            session,
            sample(4, axes = centeredAxes() + mapOf(GuidedSignals.AXIS_X to 0.95f, GuidedSignals.AXIS_Y to 0.95f)),
        )
        val left = session.sticks.getValue("LS")
        assertTrue(GuidedDirection.RIGHT in left.directions && GuidedDirection.DOWN in left.directions)
        assertTrue(left.rangeObserved)
        session = GuidedTest.ingest(
            session,
            sample(5, axes = mapOf(GuidedSignals.AXIS_Z to -0.9f, GuidedSignals.AXIS_RZ to 0.2f)),
        )
        assertEquals(setOf(GuidedDirection.LEFT), session.sticks["RS"]?.directions)
        assertFalse(GuidedDirection.LEFT in session.sticks["LS"]!!.directions && session.sticks["LS"]!!.directions.size == 1)
    }

    @Test fun rightStickPrefersZAndRzBeforeFallbacks() {
        val started = GuidedTest.begin(GuidedTest.idle(binding, standard), sample(1, axes = centeredAxes()))
        val fallback = GuidedTest.ingest(
            started,
            sample(2, axes = mapOf(GuidedSignals.AXIS_RX to -0.9f, GuidedSignals.AXIS_RY to 0.9f)),
        )
        assertEquals(setOf(GuidedDirection.LEFT, GuidedDirection.DOWN), fallback.sticks["RS"]?.directions)
        val preferred = GuidedTest.ingest(
            started,
            sample(2, axes = mapOf(GuidedSignals.AXIS_Z to 0f, GuidedSignals.AXIS_RX to -0.9f, GuidedSignals.AXIS_RZ to 0f, GuidedSignals.AXIS_RY to 0.9f)),
        )
        assertTrue(preferred.sticks["RS"]?.directions.orEmpty().isEmpty())
    }

    @Test fun triggerAliasesAndDigitalPressesStayIndependent() {
        val started = GuidedTest.begin(GuidedTest.idle(binding, standard), sample(1))
        val brake = GuidedTest.ingest(started, sample(2, axes = mapOf(GuidedSignals.AXIS_BRAKE to 1f)))
        assertEquals(1f, brake.triggers.getValue("LT").peak)
        assertFalse(brake.triggers.getValue("LT").fullTravel)
        assertNull(brake.triggers["RT"])
        val gas = GuidedTest.ingest(started, sample(2, axes = mapOf(GuidedSignals.AXIS_GAS to 1f)))
        assertEquals(1f, gas.triggers.getValue("RT").peak)
        assertFalse(gas.triggers.getValue("RT").fullTravel)
        assertNull(gas.triggers["LT"])
        val digital = GuidedTest.ingest(started, sample(2, pressed = setOf(GuidedSignals.BUTTON_L2)))
        assertTrue(digital.triggers.getValue("LT").digital)
        assertFalse(digital.triggers.getValue("LT").fullTravel)
        val aliased = GuidedTest.ingest(
            started,
            sample(2, axes = mapOf(GuidedSignals.AXIS_LTRIGGER to Float.NaN, GuidedSignals.AXIS_BRAKE to 1f)),
        )
        assertEquals(1f, aliased.triggers.getValue("LT").peak)
        val partial = GuidedTest.ingest(started, sample(2, axes = mapOf(GuidedSignals.AXIS_LTRIGGER to 0.1f)))
        assertFalse(partial.triggers.getValue("LT").fullTravel)
        val jump = GuidedTest.ingest(partial, sample(3, axes = mapOf(GuidedSignals.AXIS_LTRIGGER to 1f)))
        assertTrue(jump.triggers.getValue("LT").digital)
        assertFalse(jump.triggers.getValue("LT").fullTravel)
        val gradual = GuidedTest.ingest(
            GuidedTest.ingest(partial, sample(3, axes = mapOf(GuidedSignals.AXIS_LTRIGGER to 0.5f))),
            sample(4, axes = mapOf(GuidedSignals.AXIS_BRAKE to 1f)),
        )
        assertTrue(gradual.triggers.getValue("LT").fullTravel)
        assertNull(gradual.triggers["RT"])
        val held = GuidedTest.begin(GuidedTest.idle(binding, standard), sample(1, axes = mapOf(GuidedSignals.AXIS_LTRIGGER to 1f)))
        val stillHeld = GuidedTest.ingest(held, sample(2, axes = mapOf(GuidedSignals.AXIS_LTRIGGER to 1f)))
        assertNull(stillHeld.triggers["LT"])
        val released = GuidedTest.ingest(stillHeld, sample(3, axes = mapOf(GuidedSignals.AXIS_LTRIGGER to 0f)))
        val resqueezed = GuidedTest.ingest(released, sample(4, axes = mapOf(GuidedSignals.AXIS_LTRIGGER to 1f)))
        assertFalse(resqueezed.triggers.getValue("LT").fullTravel)
        assertEquals(GuidedMark.DIGITAL_ONLY, GuidedTest.rows(resqueezed).first { it.id == "LT" }.mark)
        val hat = GuidedTest.ingest(started, sample(2, axes = mapOf(GuidedSignals.AXIS_HAT_Y to -1f)))
        assertTrue("Up" in hat.dpad)
        assertFalse("Down" in hat.dpad)
    }

    @Test fun nonFiniteAxesDoNotThrowOrCountAsMovementOrRest() {
        val started = GuidedTest.begin(GuidedTest.idle(binding, standard), sample(1, axes = centeredAxes()))
        val moved = GuidedTest.ingest(
            started,
            sample(2, axes = mapOf(GuidedSignals.AXIS_X to Float.NaN, GuidedSignals.AXIS_Y to Float.POSITIVE_INFINITY, GuidedSignals.AXIS_LTRIGGER to Float.NEGATIVE_INFINITY)),
        )
        assertTrue(moved.sticks["LS"]?.directions.orEmpty().isEmpty())
        assertTrue((moved.sticks["LS"]?.maxRadius ?: 0f) == 0f)
        assertNull(moved.triggers["LT"])
        val rest = GuidedTest.captureRest(moved, sample(2, axes = mapOf(GuidedSignals.AXIS_X to Float.NaN)))
        assertFalse(rest.captured)
        assertNull(rest.session.rest)
    }

    @Test fun heldStickAtStartDoesNotCountUntilItReturnsAndMovesAgain() {
        val held = sample(1, axes = mapOf(GuidedSignals.AXIS_X to -1f, GuidedSignals.AXIS_Y to 0f))
        var session = GuidedTest.begin(GuidedTest.idle(binding, standard), held)
        session = GuidedTest.ingest(session, sample(2, axes = mapOf(GuidedSignals.AXIS_X to -1f, GuidedSignals.AXIS_Y to 0f)))
        assertTrue(session.sticks["LS"]?.directions.orEmpty().isEmpty())
        session = GuidedTest.ingest(session, sample(3, axes = centeredAxes()))
        session = GuidedTest.ingest(session, sample(4, axes = mapOf(GuidedSignals.AXIS_X to -0.8f, GuidedSignals.AXIS_Y to 0f)))
        assertEquals(setOf(GuidedDirection.LEFT), session.sticks["LS"]?.directions)
        var flicked = GuidedTest.begin(GuidedTest.idle(binding, standard), held)
        flicked = GuidedTest.ingest(flicked, sample(2, pressed = setOf(GuidedSignals.BUTTON_A)))
        assertTrue(flicked.sticks["LS"]?.directions.orEmpty().isEmpty())
        flicked = GuidedTest.ingest(flicked, sample(3, axes = mapOf(GuidedSignals.AXIS_X to 0.9f, GuidedSignals.AXIS_Y to 0f)))
        assertEquals(setOf(GuidedDirection.RIGHT), flicked.sticks["LS"]?.directions)
    }

    @Test fun pausedSamplesAreIgnoredAndRearNeedsExplicitConfirmation() {
        var session = GuidedTest.begin(GuidedTest.idle(binding, standard), sample(1))
        session = GuidedTest.pause(session)
        val paused = GuidedTest.ingest(session, sample(2, pressed = setOf(GuidedSignals.BUTTON_A, 188)))
        assertSame(session, paused)
        session = GuidedTest.resume(session, sample(2, pressed = setOf(GuidedSignals.BUTTON_A)))
        session = GuidedTest.ingest(session, sample(3, pressed = setOf(GuidedSignals.BUTTON_A)))
        assertTrue(session.buttons.isEmpty())
        session = GuidedTest.ingest(session, sample(4))
        session = GuidedTest.ingest(session, sample(5, pressed = setOf(GuidedSignals.BUTTON_A, 188)))
        assertEquals(setOf("A"), session.buttons)
        assertTrue(session.rearConfirmed.isEmpty())
        assertSame(session, GuidedTest.confirmRear(session, "A"))
        val confirmed = GuidedTest.confirmRear(session, "M1")
        assertEquals(setOf("M1"), confirmed.rearConfirmed)
        assertFalse(GuidedTest.rows(confirmed).first { it.id == "rear:M2" }.observed)
        assertTrue(GuidedTest.rows(confirmed).first { it.id == "rear:M1" }.observed)
        assertEquals(GuidedRole.OPTIONAL, GuidedTest.rows(confirmed).first { it.id == "rear:M1" }.role)
    }

    @Test fun genericChecklistIsOptionalAndDoesNotAutoComplete() {
        var session = GuidedTest.begin(GuidedTest.idle(binding.copy(modelLabel = "Generic pad"), generic), sample(1))
        session = GuidedTest.ingest(session, sample(2, pressed = setOf(GuidedSignals.BUTTON_A)))
        assertEquals(GuidedPhase.ACTIVE, session.phase)
        assertTrue(GuidedTest.rows(session).none { it.role == GuidedRole.REQUIRED })
        assertTrue(GuidedTest.rows(session).none { it.group == GuidedGroup.REAR })
        val finished = GuidedTest.finish(session)
        assertEquals(GuidedPhase.COMPLETED, finished.phase)
        assertFalse(finished.stoppedEarly)
        val early = GuidedTest.finish(GuidedTest.begin(GuidedTest.idle(binding, standard), sample(1)))
        assertTrue(early.stoppedEarly)
    }

    @Test fun backwardsEventIdDoesNotImportTheSampleAsANewPress() {
        val started = GuidedTest.begin(GuidedTest.idle(binding, standard), sample(5))
        val rewound = GuidedTest.ingest(started, sample(1, pressed = setOf(GuidedSignals.BUTTON_A)))
        assertTrue(rewound.buttons.isEmpty())
        val still = GuidedTest.ingest(rewound, sample(2, pressed = setOf(GuidedSignals.BUTTON_A)))
        assertTrue(still.buttons.isEmpty())
        val pressed = GuidedTest.ingest(GuidedTest.ingest(still, sample(3)), sample(4, pressed = setOf(GuidedSignals.BUTTON_A)))
        assertEquals(setOf("A"), pressed.buttons)
    }

    @Test fun reportIsTruthfulPartialAndStripsIdentifiers() {
        val demo = binding.copy(
            controllerId = "ble:$mac",
            modelLabel = "Demo X10 $mac",
            inputLabel = "Pad $mac · #3",
            synthetic = true,
        )
        var session = GuidedTest.begin(GuidedTest.idle(demo, standard), sample(1, axes = centeredAxes(), controllerId = demo.controllerId))
        session = GuidedTest.ingest(session, sample(2, pressed = setOf(GuidedSignals.BUTTON_A), controllerId = demo.controllerId))
        val captured = GuidedTest.captureRest(
            session,
            sample(3, axes = centeredAxes() + mapOf(GuidedSignals.AXIS_X to 0.25f, GuidedSignals.AXIS_BRAKE to 0f), controllerId = demo.controllerId),
        )
        assertTrue(captured.captured)
        val text = GuidedReport.render(captured.session, header(), portuguese = false, apply = null)
        assertTrue(text.contains("EasyGamepad 1.1.0 (9)"))
        assertTrue(text.contains("Android 14 (SDK 34)"))
        assertTrue(text.contains("SYNTHETIC DEMO"))
        assertTrue(text.contains("in progress"))
        assertTrue(text.contains("Buttons observed: A"))
        assertTrue(text.contains("+0.250"))
        assertTrue(text.contains("Resting offsets"))
        assertFalse(text.contains(mac))
        assertFalse(text.contains("ble:"))
        assertFalse(text.contains("latency"))
        assertFalse(text.contains("average"))
        assertFalse(text.contains("drift"))
        val partial = GuidedReport.render(GuidedTest.finish(captured.session), header(), portuguese = true)
        assertTrue(partial.contains("Estado: parcial"))
        assertTrue(partial.contains("DEMO SINTÉTICO"))
        assertTrue(partial.contains("Desvios em repouso"))
        assertFalse(partial.contains(mac))
        assertTrue(partial.contains("não verificado"))
    }

    @Test fun applyNoteStaysWithTheMatchingLinkAndOutOfTheReportOtherwise() {
        val note = GuidedTest.applyNote(false, mac, true, mac.lowercase(), GuidedApplyStatus.MISMATCH, listOf("buttons", "triggers"))
        assertEquals(GuidedApplyStatus.MISMATCH, note?.status)
        assertFalse(note.toString().contains(mac))
        assertNull(GuidedTest.applyNote(true, mac, true, mac, GuidedApplyStatus.MATCHED))
        assertNull(GuidedTest.applyNote(false, mac, true, "11:22:33:44:55:66", GuidedApplyStatus.MATCHED))
        assertNull(GuidedTest.applyNote(false, null, true, mac, GuidedApplyStatus.MATCHED))
        assertNull(GuidedTest.applyNote(false, mac, false, mac, GuidedApplyStatus.UNCONFIRMED))
        val session = GuidedTest.begin(GuidedTest.idle(binding, standard), sample(1))
        val matched = GuidedReport.render(session, header(), false, GuidedApplyNote(GuidedApplyStatus.MATCHED))
        assertTrue(matched.contains("X10 read-back: Matched"))
        assertTrue(matched.contains("turned off was not checked"))
        assertFalse(matched.contains(mac))
        val mismatch = GuidedReport.render(session, header(), true, note)
        assertTrue(mismatch.contains("Difere (botões, gatilhos)"))
        assertTrue(mismatch.contains("Não foi verificado"))
        val unconfirmed = GuidedReport.render(session, header(), false, GuidedApplyNote(GuidedApplyStatus.UNCONFIRMED, listOf("secret $mac")))
        assertTrue(unconfirmed.contains("Unconfirmed"))
        assertFalse(unconfirmed.contains(mac))
        assertFalse(unconfirmed.contains("secret"))
    }

    @Test fun fullStandardChecklistCompletesOnlyAfterRest() {
        var session = GuidedTest.begin(GuidedTest.idle(binding, standard), sample(1, axes = centeredAxes()))
        session = session.copy(
            buttons = GuidedCatalog.buttons.toSet(),
            dpad = GuidedCatalog.dpad.toSet(),
            triggers = mapOf("LT" to TriggerObservation(1f, intermediateSeen = true), "RT" to TriggerObservation(1f, intermediateSeen = true)),
            sticks = mapOf("LS" to fullStick(), "RS" to fullStick()),
        )
        assertEquals(GuidedPhase.ACTIVE, session.phase)
        val done = GuidedTest.captureRest(session, sample(2, axes = centeredAxes()))
        assertTrue(done.captured)
        assertEquals(GuidedPhase.COMPLETED, done.session.phase)
        assertFalse(done.session.stoppedEarly)
        val progress = GuidedTest.progress(done.session)
        assertEquals(progress.totalRequired, progress.observedRequired)
        assertTrue(progress.fraction == 1f)
    }

    private fun header() = GuidedReportHeader("1.1.0", "9", "14", "34")

    private fun sample(
        eventId: Int,
        pressed: Set<Int> = emptySet(),
        axes: Map<Int, Float> = emptyMap(),
        controllerId: String = binding.controllerId,
        inputId: Int = binding.inputId,
    ) = GuidedSample(controllerId, inputId, eventId, pressed, axes)

    private fun centeredAxes() = mapOf(
        GuidedSignals.AXIS_X to 0f,
        GuidedSignals.AXIS_Y to 0f,
        GuidedSignals.AXIS_Z to 0f,
        GuidedSignals.AXIS_RZ to 0f,
    )

    private fun fullStick() = StickObservation(
        directions = GuidedCatalog.stickDirections.toSet(),
        minX = -1f,
        maxX = 1f,
        minY = -1f,
        maxY = 1f,
        maxRadius = 1f,
    )
}
