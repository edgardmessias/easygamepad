// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.input

import org.junit.Assert.assertEquals
import org.junit.Test

class StickTrailsTest {
    @Test
    fun highRateInputStaysWithinDefaultLimit() {
        var trail = emptyList<Pair<Float, Float>>()
        repeat(500) { index ->
            trail = StickTrails.append(trail, index * 0.001f to 0f)
        }
        assertEquals(StickTrails.LIMIT, trail.size)
        assertEquals(380 * 0.001f, trail.first().first, 0.0001f)
    }
}
