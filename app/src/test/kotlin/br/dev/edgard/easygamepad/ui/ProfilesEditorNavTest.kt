// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import org.junit.Assert.*
import org.junit.Test

class ProfilesEditorNavTest {
    @Test fun sectionCountMatchesX10EditorTabs() {
        assertEquals(6, PROFILE_EDITOR_SECTION_COUNT)
    }
}
