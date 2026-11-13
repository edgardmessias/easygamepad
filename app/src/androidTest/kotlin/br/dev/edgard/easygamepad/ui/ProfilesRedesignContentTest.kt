// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.dev.edgard.easygamepad.data.SavedProfile
import br.dev.edgard.easygamepad.protocol.ControllerConfig
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfilesRedesignContentTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun draftRowOpensEditorAndOffersImportAction() {
        var edits = 0
        composeRule.setContent {
            HubTheme("light", "en") {
                ProfilesListScreen(
                    profiles = emptyList(),
                    editingId = null,
                    draftName = "X10",
                    draftStatusLabel = "Ready to edit",
                    showDraftRow = true,
                    canReadFromDevice = false,
                    readDisabledReason = "Connect first",
                    onEditDraft = { edits++ },
                    onEditSaved = {},
                    onLoad = {},
                    onApply = {},
                    onDelete = {},
                    onExport = {},
                    onReadFromController = {},
                    onImport = {},
                )
            }
        }
        composeRule.onNodeWithText("Ready to edit").assertIsDisplayed()
        composeRule.onNodeWithText("Import profile from a file").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Edit current draft: X10").performClick()
        assertEquals(1, edits)
    }

    @Test fun rowActionsApplyDeleteAndExportWithoutOpeningEditor() {
        val saved = SavedProfile("profile-1", ControllerConfig(name = "Living room"))
        var applied: SavedProfile? = null
        var deleted: SavedProfile? = null
        var exported: SavedProfile? = null
        var edits = 0
        composeRule.setContent {
            HubTheme("light", "en") {
                ProfilesListScreen(
                    profiles = listOf(saved),
                    editingId = saved.id,
                    draftName = "Living room",
                    draftStatusLabel = "Saved copy",
                    showDraftRow = false,
                    canReadFromDevice = true,
                    readDisabledReason = null,
                    onEditDraft = {},
                    onEditSaved = { edits++ },
                    onLoad = {},
                    onApply = { applied = it },
                    onDelete = { deleted = it },
                    onExport = { exported = it },
                    onReadFromController = {},
                    onImport = {},
                )
            }
        }
        composeRule.onNodeWithText("Editing this copy").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Apply to X10: Living room").performClick()
        composeRule.onNodeWithContentDescription("Delete profile: Living room").performClick()
        composeRule.onNodeWithContentDescription("More actions for profile: Living room").performClick()
        composeRule.onNodeWithText("Export profile").performClick()
        assertEquals(saved, applied)
        assertEquals(saved, deleted)
        assertEquals(saved, exported)
        assertEquals(0, edits)
        composeRule.onNodeWithContentDescription("Edit profile: Living room").performClick()
        assertEquals(1, edits)
    }

    @Test fun otherProfileRowLoadsOnTapAndRequestsDeletionOnLongPress() {
        val saved = SavedProfile("profile-2", ControllerConfig(name = "Racing"))
        var loaded: SavedProfile? = null
        var deleted: SavedProfile? = null
        var loads = 0
        composeRule.setContent {
            HubTheme("light", "en") {
                ProfilesListScreen(
                    profiles = listOf(saved),
                    editingId = "profile-1",
                    draftName = "Living room",
                    draftStatusLabel = "Saved copy",
                    showDraftRow = false,
                    canReadFromDevice = false,
                    readDisabledReason = "Connect first",
                    onEditDraft = {},
                    onEditSaved = {},
                    onLoad = { loaded = it; loads++ },
                    onApply = {},
                    onDelete = { deleted = it },
                    onExport = {},
                    onReadFromController = {},
                    onImport = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Edit profile: Racing").performClick()
        assertEquals(saved, loaded)
        assertEquals(1, loads)
        composeRule.onNodeWithContentDescription("Edit profile: Racing").performTouchInput { longClick() }
        assertEquals(saved, deleted)
        assertEquals(1, loads)
    }

    @Test fun compactActionsRespectAvailability() {
        var saves = 0
        composeRule.setContent {
            HubTheme("light", "en") {
                ProfileEditorBottomBar(false, true, false, null, "Connect first", { saves++ }, {})
            }
        }
        composeRule.onNodeWithText("Save profile").performClick()
        composeRule.onNodeWithText("Apply to X10").assertIsNotEnabled()
        assertEquals(1, saves)
    }

    @Test fun sectionNavigationSupportsAdjacentAndDirectSelection() {
        var selected by mutableIntStateOf(0)
        val labels = listOf("Buttons", "Sticks", "Triggers", "Macros", "Turbo", "Vibration")
        composeRule.setContent {
            HubTheme("light", "en") {
                ProfileEditorSectionNav(labels, selected, { selected = it }, Modifier.fillMaxWidth())
            }
        }
        composeRule.onNodeWithContentDescription("Previous section").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Next section").performClick()
        assertEquals(1, selected)
        composeRule.onNodeWithContentDescription("Choose section: Sticks, 2/6").performClick()
        composeRule.onNodeWithText("Vibration").performClick()
        assertEquals(5, selected)
        composeRule.onNodeWithContentDescription("Next section").assertIsNotEnabled()
    }
}
