// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.protocol.ControllerConfig
import org.junit.Assert.*
import org.junit.Test

class DraftWorkspaceTest {
    private val drafts = ControllerDrafts()

    @Test fun switchReturnsIndependentWorkspacesPerControllerId() {
        val a = ControllerConfig(name = "A")
        val b = ControllerConfig(name = "B")
        drafts.switch("ble:1", "ble:2", a, null)
        val back = drafts.switch("ble:2", "ble:1", b, null)
        assertEquals("A", back.config.name)
        assertFalse(drafts.isDirty("ble:1", back.config))
    }

    @Test fun firstRememberAfterBaselineAdoptionStaysDirty() {
        drafts.adoptBaseline("ble:new", ControllerConfig(name = "Start"), null)
        drafts.remember("ble:new", ControllerConfig(name = "Edited"), null)
        assertTrue(drafts.isDirty("ble:new", ControllerConfig(name = "Edited")))
    }

    @Test fun dirtyAfterEditAgainstLoadedSavedBaseline() {
        drafts.adoptBaseline("ble:1", ControllerConfig(name = "Saved"), "profile-1")
        assertFalse(drafts.isDirty("ble:1", ControllerConfig(name = "Saved")))
        assertTrue(drafts.isDirty("ble:1", ControllerConfig(name = "Edited")))
    }

    @Test fun markSavedClearsDirtyWhileKeepingLink() {
        val config = ControllerConfig(name = "Live")
        drafts.adoptBaseline("ble:1", ControllerConfig(name = "Old"), "id")
        drafts.markSaved("ble:1", config, "id")
        assertFalse(drafts.isDirty("ble:1", config))
        assertEquals("id", drafts.linkedSavedProfile("ble:1"))
    }

    @Test fun displayNameDoesNotAffectIdentityKey() {
        val config = ControllerConfig(name = "Living room")
        drafts.remember("ble:aa", config, null)
        drafts.remember("ble:bb", config.copy(name = "Player 2"), null)
        val switched = drafts.switch("ble:aa", "ble:bb", config, null)
        assertEquals("Player 2", switched.config.name)
    }

    @Test fun workspaceRoundTripThroughCodec() {
        val snap = ControllerDraftSnapshot(ControllerConfig(name = "Draft"), ControllerConfig(name = "Base"), "p1")
        val json = encodeWorkspaceDrafts(mapOf("ble:1" to snap))
        val restored = decodeWorkspaceDraftsLenient(json)
        assertEquals("Draft", restored["ble:1"]!!.config.name)
        assertEquals("Base", restored["ble:1"]!!.baseline.name)
        assertTrue(restored["ble:1"]!!.dirty)
    }

    @Test fun draftStatusKindDistinguishesSavedAndUnsaved() {
        assertEquals(DraftStatusKind.UNSAVED_CHANGES, draftStatusKind(true, "id"))
        assertEquals(DraftStatusKind.EDITING_SAVED, draftStatusKind(false, "id"))
    }
}
