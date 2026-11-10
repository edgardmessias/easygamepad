// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.protocol

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Individual motor selection checked on an owner's X10; no general motor bitmask is inferred. */
enum class X10Motor {
    LEFT, RIGHT;

    internal fun testPayload(intensity: Int): ByteArray {
        require(intensity in 0..100) { "Motor intensity must be between 0 and 100" }
        return when (this) {
            LEFT -> bytes(intensity, 0, 1)
            RIGHT -> bytes(0, intensity, 2)
        }
    }
}

data class X10VibrationTestResult(val settingsUnchanged: Boolean)

/** Live slider preview: hold one motor until an explicit stop, without profile read-back. */
class X10VibrationPreview(private val session: CommandSession) {
    suspend fun hold(motor: X10Motor, intensity: Int) {
        session.send(69, motor.testPayload(intensity))
    }

    suspend fun stop() {
        session.send(69, bytes(0, 0, 0))
    }
}

/**
 * Tests one motor with the selected 0-100 value without storing profile settings.
 * Physical checks established different perceived strengths, not calibrated force percentages.
 * The zero follow-up's role
 * is not isolated from a possible firmware timer, and stopping cannot be guaranteed on link loss.
 */
class X10VibrationTest(private val session: CommandSession) {
    suspend fun run(motor: X10Motor, intensity: Int): X10VibrationTestResult {
        val payload = motor.testPayload(intensity)
        val reader = X10ProfileClient(session)
        val before = reader.readSettings()
        try {
            session.ack(69, payload)
            delay(500)
        } finally {
            // Attempt the observed follow-up even if the first ACK was rejected or the wait cancelled.
            withContext(NonCancellable) {
                if (!session.isClosed) withTimeout(5500) { session.ack(69, bytes(0, 0, 0)) }
            }
        }
        val after = reader.readSettings()
        val baseline = before.mergeInto(ControllerConfig())
        return X10VibrationTestResult(after.differences(baseline).isEmpty())
    }
}
