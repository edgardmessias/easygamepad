// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

/** Editor workspace plus draft revision captured when opening a destructive confirm dialog. */
data class EditorConfirmBinding(
    val editor: EditorSaveBinding,
    val draftEpoch: Long,
) {
    fun matchesActive(generation: Long, workspaceKey: String, linkedProfileId: String?, currentDraftEpoch: Long): Boolean =
        editor.matchesActive(generation, workspaceKey) &&
            editor.linkedProfileId == linkedProfileId &&
            draftEpoch == currentDraftEpoch
}
