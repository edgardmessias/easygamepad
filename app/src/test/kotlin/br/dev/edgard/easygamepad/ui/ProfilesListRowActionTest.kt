// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfilesListRowActionTest {
    @Test fun linkedProfileRowShowsEdit() {
        assertEquals(ProfilesListRowAction.EDIT, profilesListRowAction("id-1", "id-1"))
    }

    @Test fun otherSavedRowsShowLoad() {
        assertEquals(ProfilesListRowAction.LOAD, profilesListRowAction("id-1", "id-2"))
        assertEquals(ProfilesListRowAction.LOAD, profilesListRowAction(null, "id-2"))
    }
}
