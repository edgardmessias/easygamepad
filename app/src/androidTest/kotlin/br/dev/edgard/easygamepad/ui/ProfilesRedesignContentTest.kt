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

    @Test fun savedLibraryShowsEmptyStateAndBackAction() {
        var backs = 0
        composeRule.setContent {
            HubTheme("light", "en") {
                SavedProfilesScreen(emptyList(), null, { backs++ }, {}, {})
            }
        }
        composeRule.onNodeWithText("No saved copies yet", substring = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back to editor").performClick()
        assertEquals(1, backs)
    }

    @Test fun savedLibraryMarksEditingCopyAndOffersLoadAndDelete() {
        val saved = SavedProfile("profile-1", ControllerConfig(name = "Living room"))
        var loaded: SavedProfile? = null
        var deleted: SavedProfile? = null
        composeRule.setContent {
            HubTheme("light", "en") {
                SavedProfilesScreen(listOf(saved), saved.id, {}, { loaded = it }, { deleted = it })
            }
        }
        composeRule.onNodeWithText("Editing this copy").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Load profile: Living room").performClick()
        composeRule.onNodeWithContentDescription("Delete profile: Living room").performClick()
        assertEquals(saved, loaded)
        assertEquals(saved, deleted)
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
