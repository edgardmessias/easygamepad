// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilitySemanticsTest {
    @Test
    fun statusStateIsAnnouncedWithoutRelyingOnColor() {
        assertEquals("Active", AccessibilitySemantics.statusState(active = true, portuguese = false))
        assertEquals("Inativo", AccessibilitySemantics.statusState(active = false, portuguese = true))
    }

    @Test
    fun vibrationSliderAnnouncesPreviewWithoutStartingHardware() {
        val label = AccessibilitySemantics.vibrationSliderLabel("Left motor", 40, "%", previewing = true, portuguese = false)
        assertTrue(label.contains("Left motor"))
        assertTrue(label.contains("vibration preview active"))
    }

    @Test
    fun applyDisabledReasonsExplainNextStep() {
        val busy = ScreenUiFixtures.profilesBusyOnReady()
        assertEquals("Wait for the current controller operation.", AccessibilitySemantics.profilesApplyDisabledReason(busy, false))
        val cal = ScreenUiFixtures.profilesCalibrationStop()
        assertTrue(AccessibilitySemantics.profilesApplyDisabledReason(cal, false)!!.contains("calibration"))
        val ready = ScreenUiFixtures.profilesReadyToApply()
        assertNull(AccessibilitySemantics.profilesApplyDisabledReason(ready, false))
    }

    @Test
    fun saveDisabledReasonMentionsValidation() {
        val invalid = ScreenUiFixtures.profilesReadyToApply().copy(draftValid = false)
        assertEquals("Fix invalid profile values.", AccessibilitySemantics.profilesSaveDisabledReason(invalid, false))
    }

    @Test
    fun readStaysAvailableWhenDraftInvalidButApplyIsBlocked() {
        val invalid = ScreenUiFixtures.profilesReadyToApply().copy(draftValid = false)
        assertNull(AccessibilitySemantics.profilesReadDisabledReason(invalid, false))
        assertTrue(AccessibilitySemantics.profilesApplyDisabledReason(invalid, false)!!.contains("applying"))
    }

    @Test
    fun dashboardConnectReasonExplainsBlockingState() {
        assertTrue(AccessibilitySemantics.dashboardConnectDisabledReason(switching = true, connecting = false, false)!!.contains("switch"))
        assertTrue(AccessibilitySemantics.dashboardConnectDisabledReason(switching = false, connecting = true, true)!!.contains("andamento"))
        assertNull(AccessibilitySemantics.dashboardConnectDisabledReason(false, false, false))
    }

    @Test
    fun controllerSwitcherMarksCurrentSelection() {
        assertTrue(AccessibilitySemantics.controllerSwitcherLabel("Sala X10", selected = true, false).contains("current"))
        assertTrue(AccessibilitySemantics.controllerSwitcherLabel("Sala X10", selected = false, true).contains("Alternar"))
    }

    @Test
    fun metricSummaryIncludesDetailForScreenReaders() {
        val summary = AccessibilitySemantics.metricTileSummary("Battery", "85%", "Tap to refresh")
        assertTrue(summary.contains("85%"))
        assertTrue(summary.contains("Tap to refresh"))
    }

    @Test
    fun batteryRefreshBlockedUntilConfigurationLinkReady() {
        val device = ScreenUiFixtures.savedX10Disconnected
        val offline = br.dev.edgard.easygamepad.data.ControllerStatus(false, false, false, null, false)
        assertEquals(false, AccessibilitySemantics.dashboardBatteryRefreshEnabled(device, offline))
        val ready = offline.copy(configurationConnected = true)
        assertEquals(true, AccessibilitySemantics.dashboardBatteryRefreshEnabled(device, ready))
    }

    @Test
    fun batteryRefreshViaGameplayWhenNoConfigurationLink() {
        val device = ScreenUiFixtures.genericGameplay
        val unknown = br.dev.edgard.easygamepad.data.ControllerStatus(true, false, false, null, false)
        assertEquals(true, AccessibilitySemantics.dashboardBatteryRefreshEnabled(device, unknown))
        val idle = unknown.copy(gameplayConnected = false)
        assertEquals(false, AccessibilitySemantics.dashboardBatteryRefreshEnabled(device, idle))
    }

    @Test
    fun dashboardBatteryCopyReflectsChargeState() {
        val device = ScreenUiFixtures.savedX10Disconnected
        val reading = br.dev.edgard.easygamepad.data.ControllerStatus(true, true, false, 72, true)
        assertEquals("72%", AccessibilitySemantics.dashboardBatteryValue(reading))
        assertEquals(0.72f, AccessibilitySemantics.dashboardBatteryProgress(reading))
        assertTrue(AccessibilitySemantics.dashboardBatteryDetail(device, reading, true, false).contains("Charging"))
        val offline = reading.copy(batteryPercent = null, charging = false, configurationConnected = false, gameplayConnected = false)
        assertTrue(AccessibilitySemantics.dashboardBatteryDetail(device, offline, false, true).contains("Conecte"))
    }

    @Test
    fun dashboardSeparatesGameAndAppChannels() {
        val gameplayOnly = br.dev.edgard.easygamepad.data.ControllerStatus(true, false, false, null, false)
        assertEquals("On", AccessibilitySemantics.dashboardGameValue(gameplayOnly, false))
        assertEquals("Off", AccessibilitySemantics.dashboardAppValue(gameplayOnly, false))
        assertTrue(
            AccessibilitySemantics.dashboardAppDetail(gameplayOnly, false, connectEnabled = true)
                .contains("Tap to connect"),
        )
        val appReady = gameplayOnly.copy(configurationConnected = true, batteryPercent = 50)
        assertEquals("On", AccessibilitySemantics.dashboardAppValue(appReady, false))
        assertEquals(true, AccessibilitySemantics.dashboardAppLinkEnabled(gameplayOnly))
        assertEquals(false, AccessibilitySemantics.dashboardAppLinkEnabled(appReady))
        val idle = br.dev.edgard.easygamepad.data.ControllerStatus(false, false, false, null, false)
        assertTrue(
            AccessibilitySemantics.dashboardAppDetail(idle, true, connectEnabled = true)
                .contains("Toque para conectar"),
        )
        val linking = idle.copy(connecting = true)
        assertEquals("Connecting…", AccessibilitySemantics.dashboardAppValue(linking, false))
    }

    @Test
    fun dashboardProfilesDetailUsesQuotaWithoutSyncClaims() {
        assertTrue(AccessibilitySemantics.dashboardProfilesDetail(1, false).contains("1/30"))
        assertTrue(AccessibilitySemantics.dashboardProfilesDetail(0, true).contains("primeiro"))
        assertEquals("3", AccessibilitySemantics.dashboardProfilesValue(3))
    }

    @Test
    fun dashboardChannelNamesQMacroSeparatelyFromKeyLinker() {
        assertEquals("QMacro", AccessibilitySemantics.dashboardChannelLabel(ScreenUiFixtures.savedX10Disconnected))
        val x20 = ScreenUiFixtures.savedX10Disconnected.copy(
            modelId = br.dev.edgard.easygamepad.protocol.DeviceProfile.X20.id,
        )
        assertEquals("KeyLinker", AccessibilitySemantics.dashboardChannelLabel(x20))
        assertEquals("Connect", AccessibilitySemantics.dashboardConnectCue(false))
        assertEquals("Conectar", AccessibilitySemantics.dashboardConnectCue(true))
    }
}
