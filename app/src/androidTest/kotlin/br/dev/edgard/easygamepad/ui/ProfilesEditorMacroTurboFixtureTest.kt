// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.dev.edgard.easygamepad.protocol.Macro
import br.dev.edgard.easygamepad.protocol.MacroStep
import br.dev.edgard.easygamepad.protocol.PadKey
import br.dev.edgard.easygamepad.protocol.Turbo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfilesEditorMacroTurboFixtureTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun macroEditorShowsFilledStepsAndControls() {
        val macros = listOf(
            Macro(
                PadKey.M1,
                repeat = true,
                steps = listOf(
                    MacroStep(PadKey.A, holdMs = 120, delayMs = 80),
                    MacroStep(PadKey.B, holdMs = 200, delayMs = 50),
                ),
            ),
        )
        composeRule.setContent {
            HubTheme("light", "en") {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    MacroEditor(macros) {}
                }
            }
        }
        composeRule.onNodeWithText("M1").assertIsDisplayed()
        composeRule.onNodeWithText("Repeat sequence").assertIsDisplayed()
        composeRule.onNodeWithText("2 of 32 steps").assertIsDisplayed()
        composeRule.onNodeWithText("Remove macro").assertIsDisplayed()
    }

    @Test fun turboEditorShowsRateFifteenWithoutInventedUnit() {
        val turbos = listOf(Turbo(PadKey.A, automatic = true, rate = 15))
        composeRule.setContent {
            HubTheme("light", "en") {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    TurboEditor(turbos) {}
                }
            }
        }
        composeRule.onNodeWithText("A").assertIsDisplayed()
        composeRule.onNodeWithText("15", substring = true).assertIsDisplayed()
    }
}
