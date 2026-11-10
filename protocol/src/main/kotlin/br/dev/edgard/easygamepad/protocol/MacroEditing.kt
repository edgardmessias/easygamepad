// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.protocol

const val MACRO_STEP_LIMIT = 32

/** Compact preview for UI and logs; timings are the millisecond values stored in the profile. */
fun Macro.sequenceSummary(): String = when {
    steps.isEmpty() -> ""
    else -> steps.joinToString(separator = " → ") { step -> "${step.key.label} ${step.holdMs}/${step.delayMs}ms" }
}

fun Macro.duplicateStepAt(index: Int): Macro {
    require(index in steps.indices) { "Step index out of range" }
    require(steps.size < MACRO_STEP_LIMIT) { "Macro step limit reached" }
    val copy = steps[index]
    return copy(steps = steps.toMutableList().apply { add(index + 1, copy) })
}

fun Macro.moveStep(from: Int, by: Int): Macro {
    val to = from + by
    require(from in steps.indices && to in steps.indices) { "Cannot move step" }
    val next = steps.toMutableList()
    val item = next.removeAt(from)
    next.add(to, item)
    return copy(steps = next)
}

fun Macro.removeStepAt(index: Int): Macro = copy(steps = steps.filterIndexed { i, _ -> i != index })
