// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.dev.edgard.easygamepad.ble.LinkPhase
import br.dev.edgard.easygamepad.data.ControllerDevice
import br.dev.edgard.easygamepad.protocol.DeviceProfile
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ControllerPickerContentTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun emptyDeviceListShowsPairingHint() {
        composeRule.setContent {
            ProvideWindowLayout {
                HubTheme("light", "en") {
                    ControllerPickerContent(ControllerPickerUiState(), ControllerPickerActions.noop)
                }
            }
        }
        composeRule.onNodeWithText("Pair a gamepad in Android settings", substring = true).assertIsDisplayed()
    }

    @Test fun switchBlockedShowsInfoBanner() {
        composeRule.setContent {
            ProvideWindowLayout {
                HubTheme("light", "en") {
                    ControllerPickerContent(
                        ControllerPickerUiState(
                            listOf(ControllerDevice("ble:1", "X10", DeviceProfile.X10.id, "aa:bb:cc:dd:ee:01")),
                            ControllerDevice("ble:1", "X10", DeviceProfile.X10.id, "aa:bb:cc:dd:ee:01"),
                            LinkPhase.READY, "aa:bb:cc:dd:ee:01", canSwitch = false,
                        ),
                        ControllerPickerActions.noop,
                    )
                }
            }
        }
        composeRule.onNodeWithText("Finish the current operation", substring = true).assertIsDisplayed()
    }
}
