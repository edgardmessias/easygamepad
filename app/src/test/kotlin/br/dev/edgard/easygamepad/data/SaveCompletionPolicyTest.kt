// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.protocol.ControllerConfig
import org.junit.Assert.*
import org.junit.Test

class SaveCompletionPolicyTest {
    @Test fun staleGenerationDoesNotAdoptSavedLink() {
        val binding = EditorSaveBinding(3, "ble:aa", linkedProfileId = "profile-a")
        assertFalse(SaveCompletionPolicy.shouldLinkSavedProfile(binding, 4, "ble:aa", "profile-a", "profile-a", newCopy = false))
    }

    @Test fun workspaceChangeDoesNotAdoptSavedLink() {
        val binding = EditorSaveBinding(3, "ble:aa", linkedProfileId = null)
        assertFalse(SaveCompletionPolicy.shouldLinkSavedProfile(binding, 3, "ble:bb", null, "new-id", newCopy = true))
    }

    @Test fun newCopyLinksOnlyWhenEditorStillActive() {
        val binding = EditorSaveBinding(2, LOCAL_EDITOR_WORKSPACE_KEY, linkedProfileId = null)
        assertTrue(SaveCompletionPolicy.shouldLinkSavedProfile(binding, 2, LOCAL_EDITOR_WORKSPACE_KEY, null, "id-new", newCopy = true))
    }

    @Test fun completeSaveUpdatesBaselineWithoutReplacingNewerLiveDraft() {
        val drafts = ControllerDrafts()
        val workspace = "ble:aa"
        val saved = ControllerConfig(name = "Saved")
        val edited = ControllerConfig(name = "Edited")
        drafts.remember(workspace, edited, null)
        drafts.completeSave(workspace, saved, "profile-1", liveConfig = edited)
        assertEquals("Edited", drafts.export()[workspace]!!.config.name)
        assertEquals("Saved", drafts.export()[workspace]!!.baseline.name)
        assertEquals("profile-1", drafts.export()[workspace]!!.linkedSavedProfileId)
        assertTrue(drafts.isDirty(workspace, edited))
    }

    @Test fun completeSaveMarksCleanWhenLiveMatchesSavedSnapshot() {
        val drafts = ControllerDrafts()
        val workspace = LOCAL_EDITOR_WORKSPACE_KEY
        val saved = ControllerConfig(name = "On disk")
        drafts.remember(workspace, saved, null)
        drafts.completeSave(workspace, saved, "profile-1", liveConfig = saved)
        assertFalse(drafts.isDirty(workspace, saved))
    }

    @Test fun liveConfigForCompletionIsNullWhenEditorMovedOn() {
        val binding = EditorSaveBinding(1, "ble:aa", null)
        assertNull(
            SaveCompletionPolicy.liveConfigForCompletion(binding, 1, "ble:bb", ControllerConfig(name = "Live")),
        )
    }
}
