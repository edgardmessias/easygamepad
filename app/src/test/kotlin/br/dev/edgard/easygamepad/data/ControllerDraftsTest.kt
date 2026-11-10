// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.protocol.ControllerConfig
import org.junit.Assert.*
import org.junit.Test

class ControllerDraftsTest {
    @Test fun twoControllersKeepIndependentEditsWhenSwitchingBackAndForth() {
        val drafts = ControllerDrafts()
        val first = ControllerConfig(name = "Sala", leftVibration = 25)
        assertEquals(ControllerConfig(), drafts.switch("a", "b", first, null).config)
        val second = ControllerConfig(name = "Jogador 2", leftVibration = 90)
        assertEquals(first, drafts.switch("b", "a", second, null).config)
        assertEquals(second, drafts.switch("a", "b", first, null).config)
    }

    @Test fun discoveringAnotherAddressNeverCopiesThePreviousControllersDraft() {
        assertEquals(ControllerConfig(), ControllerDrafts().switch("a", "new-address", ControllerConfig(name = "A"), null).config)
    }

    @Test fun verifiedNewControllerReceivesItsSetupDraft() {
        val setup = ControllerConfig(name = "Novo")
        val drafts = ControllerDrafts()
        assertEquals(setup, drafts.switch("setup", "verified-address", setup, null, transferSetup = true).config)
        assertEquals(setup, drafts.switch("other", "verified-address", ControllerConfig(), null).config)
    }

    @Test fun unknownOrNoSelectionStartsWithDefaults() {
        val drafts = ControllerDrafts()
        assertEquals(ControllerConfig(), drafts.switch(null, "new", ControllerConfig(name = "Unassigned"), null).config)
        assertEquals(ControllerConfig(), drafts.switch("new", null, ControllerConfig(name = "Edited"), null).config)
    }
}
