// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import br.dev.edgard.easygamepad.data.EditorConfirmBinding
import br.dev.edgard.easygamepad.data.EditorSaveBinding
import br.dev.edgard.easygamepad.data.LOCAL_EDITOR_WORKSPACE_KEY
import br.dev.edgard.easygamepad.protocol.ControllerConfig
import br.dev.edgard.easygamepad.protocol.Macro
import br.dev.edgard.easygamepad.protocol.MacroStep
import br.dev.edgard.easygamepad.protocol.PadKey
import org.junit.Assert.*
import org.junit.Test

class ProfilesPendingConfirmTest {
    private fun binding(epoch: Long = 1L) = EditorConfirmBinding(
        EditorSaveBinding(1L, LOCAL_EDITOR_WORKSPACE_KEY, null),
        epoch,
    )

    @Test fun rearMacroReplaceDialogDroppedWhenBindingEpochStale() {
        val draft = ControllerConfig(mappings = mapOf(PadKey.M1 to PadKey.Y))
        val pending = RearMacroReplacePending(
            binding(epoch = 0L),
            listOf(Macro(PadKey.M1, steps = listOf(MacroStep(PadKey.A)))),
        )
        assertNull(rearMacroReplaceDialogConflict(draft, pending, bindingMatches = false))
    }

    @Test fun rearMacroReplaceDialogDroppedWhenConflictDisappears() {
        val draft = ControllerConfig(
            macros = listOf(Macro(PadKey.M1, steps = listOf(MacroStep(PadKey.A)))),
        )
        val pending = RearMacroReplacePending(
            binding(),
            listOf(Macro(PadKey.M1, steps = listOf(MacroStep(PadKey.A)))),
        )
        assertNull(rearMacroReplaceDialogConflict(draft, pending, bindingMatches = true))
    }

    @Test fun rearMacroReplaceDialogSurvivesWhileMappingStillConflicts() {
        val draft = ControllerConfig(mappings = mapOf(PadKey.M1 to PadKey.Y))
        val next = listOf(Macro(PadKey.M1, steps = listOf(MacroStep(PadKey.A))))
        val pending = RearMacroReplacePending(binding(), next)
        assertEquals(PadKey.M1 to PadKey.Y, rearMacroReplaceDialogConflict(draft, pending, bindingMatches = true))
    }

    @Test fun staleConfirmDoesNotApplyWithoutLiveConflict() {
        val draftAfterRead = ControllerConfig(
            macros = listOf(Macro(PadKey.M1, steps = listOf(MacroStep(PadKey.B)))),
        )
        val stalePending = RearMacroReplacePending(
            binding(epoch = 2L),
            listOf(Macro(PadKey.M1, steps = listOf(MacroStep(PadKey.A)))),
        )
        assertNull(rearMacroReplaceDialogConflict(draftAfterRead, stalePending, bindingMatches = false))
        val wouldApply = rearMacroReplaceDialogConflict(
            draftAfterRead,
            stalePending,
            bindingMatches = true,
        ) != null
        assertFalse(wouldApply)
    }

    @Test fun rearRemapOverMacroDismissedWhenMacroRemovedFromDraft() {
        val draft = ControllerConfig(mappings = mapOf(PadKey.M1 to PadKey.A))
        val pending = RearRemapOverMacroPending(binding(), PadKey.M1, PadKey.Y)
        assertFalse(rearRemapOverMacroDialogStillOpen(draft, pending, bindingMatches = true))
    }

    @Test fun buttonsListShowsMacroBeforeNotSent() {
        val withMacro = ControllerConfig(
            macros = listOf(Macro(PadKey.M1, steps = listOf(MacroStep(PadKey.A)))),
        )
        assertEquals(ButtonsListOutputKind.Macro, withMacro.buttonsListOutputKind(PadKey.M1))
        assertEquals(ButtonsListOutputKind.NotSent, withMacro.buttonsListOutputKind(PadKey.A))
    }

    @Test fun buttonsListMappingWinsOverMacroWhenBothPresent() {
        val conflict = ControllerConfig(
            mappings = mapOf(PadKey.M1 to PadKey.Y),
            macros = listOf(Macro(PadKey.M1, steps = listOf(MacroStep(PadKey.A)))),
        )
        assertEquals(ButtonsListOutputKind.Mapping, conflict.buttonsListOutputKind(PadKey.M1))
    }
}
