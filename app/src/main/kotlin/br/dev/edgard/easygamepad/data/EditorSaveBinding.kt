// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

/** Captures which editor/workspace initiated a deferred local save or import. */
data class EditorSaveBinding(
    val generation: Long,
    val workspaceKey: String,
    val linkedProfileId: String?,
) {
    fun matchesActive(currentGeneration: Long, currentWorkspace: String): Boolean =
        generation == currentGeneration && workspaceKey == currentWorkspace

    fun mayAdoptSavedLink(currentGeneration: Long, currentWorkspace: String, currentLinkedId: String?): Boolean =
        matchesActive(currentGeneration, currentWorkspace) &&
            (linkedProfileId == null || linkedProfileId == currentLinkedId)
}

const val LOCAL_EDITOR_WORKSPACE_KEY = "local-editor"
