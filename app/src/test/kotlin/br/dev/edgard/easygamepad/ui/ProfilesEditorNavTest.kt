// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

class ProfilesEditorNavTest {
    @Test fun bottomInsetGrowsWithFontScaleAndShortHeight() {
        assertEquals(128.dp, profilesEditorBottomInset(shortHeight = false, fontScale = 1f))
        assertEquals(112.dp, profilesEditorBottomInset(shortHeight = true, fontScale = 1f))
        assertEquals(144.dp, profilesEditorBottomInset(shortHeight = false, fontScale = 1.35f))
        assertEquals(168.dp, profilesEditorBottomInset(shortHeight = true, fontScale = 2f))
    }

    @Test fun sectionCountMatchesX10EditorTabs() {
        assertEquals(6, PROFILE_EDITOR_SECTION_COUNT)
    }
}
