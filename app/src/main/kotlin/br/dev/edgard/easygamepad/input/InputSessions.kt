// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.input

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Independent live input per Android device. All access is on the main thread. */
class InputSessions {
    private val mutableState = MutableStateFlow(InputState())
    val state = mutableState.asStateFlow()
    private val snapshots = mutableMapOf<Int, InputState>()
    private var selectionWasExplicit = false
    fun snapshot(id: Int) = snapshots[id] ?: InputState()
    fun update(id: Int, value: InputState) {
        snapshots[id] = value.copy(pads = emptyList(), selectedId = id)
        if (state.value.selectedId == id) mutableState.value = value.copy(pads = state.value.pads, selectedId = id)
    }
    fun refresh(pads: List<InputPad>) {
        snapshots.keys.retainAll(pads.map { it.id }.toSet())
        val selected = state.value.selectedId?.takeIf { id -> pads.any { it.id == id } }
            ?: if (!selectionWasExplicit) pads.firstOrNull()?.id else null
        mutableState.value = (selected?.let(::snapshot) ?: InputState()).copy(pads = pads, selectedId = selected)
    }
    fun select(id: Int?) {
        selectionWasExplicit = true
        val valid = id?.takeIf { wanted -> state.value.pads.any { it.id == wanted } }
        if (state.value.selectedId == valid) return
        state.value.selectedId?.let { old -> snapshots[old] = state.value.copy(pads = emptyList(), learningPaddle = null) }
        mutableState.value = (valid?.let(::snapshot) ?: InputState()).copy(pads = state.value.pads, selectedId = valid)
    }
    fun reset() { state.value.selectedId?.let { update(it, InputState()) } }
    fun clearPressed(padId: Int? = null) {
        if (padId == null) {
            snapshots.replaceAll { _, value -> value.copy(pressed = emptySet(), axes = emptyMap(), learningPaddle = null) }
            mutableState.value = state.value.copy(pressed = emptySet(), axes = emptyMap(), learningPaddle = null)
            return
        }
        snapshots[padId]?.let { snapshots[padId] = it.copy(pressed = emptySet(), axes = emptyMap(), learningPaddle = null) }
        if (state.value.selectedId == padId) {
            mutableState.value = state.value.copy(pressed = emptySet(), axes = emptyMap(), learningPaddle = null)
        }
    }
}
