// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MotorValueInputTest {
    @Test fun numericFieldUsesDraftPathOnly() {
        var draftCalls = 0
        var previewCalls = 0
        MotorVibrationInput.applyNumericChange(42, onDraftChange = { draftCalls++ }, onSliderPreview = { previewCalls++ })
        assertEquals(1, draftCalls)
        assertEquals(0, previewCalls)
    }

    @Test fun sliderUsesPreviewPath() {
        var draftCalls = 0
        var previewCalls = 0
        MotorVibrationInput.applySliderChange(10, onDraftChange = { draftCalls++ }, onSliderPreview = { previewCalls++ })
        assertEquals(1, previewCalls)
        assertEquals(0, draftCalls)
    }
}
