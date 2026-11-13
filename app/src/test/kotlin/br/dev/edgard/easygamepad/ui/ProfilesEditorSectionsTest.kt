// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import br.dev.edgard.easygamepad.protocol.PadKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilesEditorSectionsTest {
    @Test fun dualColumnStacksOnLargeFontOrNarrowWidth() {
        assertTrue(profileEditorDualColumnStacked(fontScale = 1.5f, maxWidthDp = 400f))
        assertTrue(profileEditorDualColumnStacked(fontScale = 1.0f, maxWidthDp = 320f))
        assertFalse(profileEditorDualColumnStacked(fontScale = 1.0f, maxWidthDp = 400f))
    }

    @Test fun buttonListIncludesOutputsAndMacroPaddles() {
        val keys = profileEditorButtonListKeys(mapOf(PadKey.A to PadKey.B, PadKey.M1 to PadKey.M1))
        assertTrue(PadKey.LB in keys)
        assertTrue(PadKey.M1 in keys)
        assertFalse(PadKey.M3 in keys)
    }
}
