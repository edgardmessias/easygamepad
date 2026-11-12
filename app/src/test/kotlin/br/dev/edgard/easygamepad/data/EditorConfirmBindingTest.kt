// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

import org.junit.Assert.*
import org.junit.Test

class EditorConfirmBindingTest {
    @Test fun matchesActiveRequiresGenerationWorkspaceLinkAndEpoch() {
        val editor = EditorSaveBinding(3L, "ble:aa", linkedProfileId = "p1")
        val captured = EditorConfirmBinding(editor, draftEpoch = 5L)
        assertTrue(captured.matchesActive(3L, "ble:aa", "p1", 5L))
        assertFalse(captured.matchesActive(4L, "ble:aa", "p1", 5L))
        assertFalse(captured.matchesActive(3L, "ble:bb", "p1", 5L))
        assertFalse(captured.matchesActive(3L, "ble:aa", "p2", 5L))
        assertFalse(captured.matchesActive(3L, "ble:aa", "p1", 6L))
    }
}
