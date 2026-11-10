// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.input

/** Bounded per-stick history for the live tester. */
object StickTrails {
    const val LIMIT = 120

    fun append(trail: List<Pair<Float, Float>>, point: Pair<Float, Float>, limit: Int = LIMIT): List<Pair<Float, Float>> =
        (trail + point).takeLast(limit)
}
