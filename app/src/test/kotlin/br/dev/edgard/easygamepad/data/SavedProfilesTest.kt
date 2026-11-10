// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.protocol.ControllerConfig
import org.junit.Assert.*
import org.junit.Test

class SavedProfilesTest {
    private fun row(id: String) = SavedProfile(id, ControllerConfig(name = id))

    @Test fun updateRetainsIdAndCount() {
        val existing = listOf(row("a"), row("b"))
        val updated = mergeSavedProfile(existing, row("a").copy(config = ControllerConfig(name = "renamed")))
        assertEquals(2, updated.size)
        assertEquals("renamed", updated.first { it.id == "a" }.config.name)
    }

    @Test fun saveAsNewIncreasesCountUntilLimit() {
        val full = List(MAX_SAVED_PROFILES) { row("p$it") }
        assertFalse(canAddSavedProfile(full))
        try {
            mergeSavedProfile(full, row("new"))
            fail("expected limit")
        } catch (_: IllegalArgumentException) {
        }
        val withRoom = full.drop(1)
        assertEquals(MAX_SAVED_PROFILES, mergeSavedProfile(withRoom, row("new")).size)
    }

    @Test fun newCopyGetsDistinctId() {
        val merged = mergeSavedProfile(listOf(row("a")), row("b"))
        assertEquals(setOf("a", "b"), merged.map { it.id }.toSet())
    }
}
