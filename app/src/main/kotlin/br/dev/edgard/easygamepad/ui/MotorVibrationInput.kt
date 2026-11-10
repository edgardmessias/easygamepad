// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

/** Mirrors [ValueSlider] motor wiring: numeric fields use [onDraftChange] only; sliders also call [onSliderPreview]. */
object MotorVibrationInput {
    fun applyNumericChange(value: Int, onDraftChange: (Int) -> Unit, onSliderPreview: (Int) -> Unit) {
        onDraftChange(value)
    }

    fun applySliderChange(value: Int, onDraftChange: (Int) -> Unit, onSliderPreview: (Int) -> Unit) {
        onSliderPreview(value)
    }
}
