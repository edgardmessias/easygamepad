// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.protocol.ControllerConfig

/** Decides how an asynchronous profile save may touch the active editor without stale cross-talk. */
object SaveCompletionPolicy {
    fun shouldLinkSavedProfile(
        binding: EditorSaveBinding,
        currentGeneration: Long,
        activeWorkspace: String,
        currentLinkedProfileId: String?,
        savedProfileId: String,
        newCopy: Boolean,
    ): Boolean {
        if (!binding.matchesActive(currentGeneration, activeWorkspace)) return false
        if (newCopy) return true
        return binding.linkedProfileId == savedProfileId &&
            binding.mayAdoptSavedLink(currentGeneration, activeWorkspace, currentLinkedProfileId)
    }

    fun liveConfigForCompletion(
        binding: EditorSaveBinding,
        currentGeneration: Long,
        activeWorkspace: String,
        currentDraft: ControllerConfig,
    ): ControllerConfig? =
        if (binding.matchesActive(currentGeneration, activeWorkspace)) currentDraft else null
}
