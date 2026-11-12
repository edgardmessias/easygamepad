// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import br.dev.edgard.easygamepad.data.EditorConfirmBinding
import br.dev.edgard.easygamepad.protocol.ControllerConfig
import br.dev.edgard.easygamepad.protocol.Macro
import br.dev.edgard.easygamepad.protocol.PadKey
import br.dev.edgard.easygamepad.protocol.rearHasMacro
import br.dev.edgard.easygamepad.protocol.rearPaddlesNeedingMacroConfirm

data class RearRemapOverMacroPending(
    val binding: EditorConfirmBinding,
    val source: PadKey,
    val target: PadKey,
)

data class RearMacroReplacePending(
    val binding: EditorConfirmBinding,
    val nextMacros: List<Macro>,
)

fun rearRemapOverMacroDialogStillOpen(
    draft: ControllerConfig,
    pending: RearRemapOverMacroPending,
    bindingMatches: Boolean,
): Boolean = bindingMatches && draft.rearHasMacro(pending.source)

fun rearMacroReplaceDialogConflict(
    draft: ControllerConfig,
    pending: RearMacroReplacePending,
    bindingMatches: Boolean,
): Pair<PadKey, PadKey>? {
    if (!bindingMatches) return null
    val paddle = draft.rearPaddlesNeedingMacroConfirm(pending.nextMacros).firstOrNull() ?: return null
    val mappedTo = draft.mappings[paddle] ?: return null
    return paddle to mappedTo
}

enum class ButtonsListOutputKind { Disabled, Mapping, Macro, NotSent }

fun ControllerConfig.buttonsListOutputKind(source: PadKey): ButtonsListOutputKind = when {
    source in disabledRearPaddles -> ButtonsListOutputKind.Disabled
    mappings[source] != null -> ButtonsListOutputKind.Mapping
    rearHasMacro(source) -> ButtonsListOutputKind.Macro
    else -> ButtonsListOutputKind.NotSent
}
