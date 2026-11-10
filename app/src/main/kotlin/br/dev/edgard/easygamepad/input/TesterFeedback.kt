// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.input

/** Separates a live press from a remembered test without changing Android key codes. */
enum class InputChipState { IDLE, TESTED, LIVE }

fun inputChipState(code: Int, state: InputState, live: Boolean): InputChipState = when {
    live -> InputChipState.LIVE
    code in state.tested -> InputChipState.TESTED
    else -> InputChipState.IDLE
}

fun selectedPadLabel(state: InputState, preferPortuguese: Boolean): String? {
    val pad = state.pads.firstOrNull { it.id == state.selectedId } ?: return null
    val id = "#${state.selectedId}"
    return if (preferPortuguese) {
        "${pad.name} · entrada Android $id"
    } else {
        "${pad.name} · Android input $id"
    }
}
