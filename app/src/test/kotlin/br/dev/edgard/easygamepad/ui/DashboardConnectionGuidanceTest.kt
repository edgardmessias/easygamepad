// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardConnectionGuidanceTest {
    @Test fun connectPlacementHidesWhenLinked() {
        assertEquals(DashboardConnectPlacement.HIDDEN, dashboardConnectPlacement(twoPane = false, showConnectCard = false))
    }

    @Test fun connectPlacementMovesToPrimaryOnCompact() {
        assertEquals(DashboardConnectPlacement.PRIMARY_COMPACT, dashboardConnectPlacement(twoPane = false, showConnectCard = true))
    }

    @Test fun connectPlacementStaysInSecondaryOnTwoPane() {
        assertEquals(DashboardConnectPlacement.SECONDARY, dashboardConnectPlacement(twoPane = true, showConnectCard = true))
    }

}
