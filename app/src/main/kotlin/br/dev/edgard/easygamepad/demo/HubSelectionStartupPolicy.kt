// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.demo

/**
 * Startup coordination between persisted selection restore, welcome auto-pick, and debug ADB.
 *
 * Until [selectionRestoreComplete] is true, [selectedId] == null is not a stable welcome screen;
 * ADB connect must not auto-select the demo while restore is still pending.
 */
object HubSelectionStartupPolicy {
    fun shouldApplyRestoredSelection(
        restoreStartedRevision: Long,
        currentRevision: Long,
        currentSelectedId: String?,
    ): Boolean =
        restoreStartedRevision == currentRevision && currentSelectedId == null

    fun shouldAutoPickFirstDevice(selectionRestoreComplete: Boolean, selectedId: String?, hasRows: Boolean): Boolean =
        selectionRestoreComplete && selectedId == null && hasRows

    fun mayApplyLinkReadySelection(selectionRestoreComplete: Boolean, selectedId: String?): Boolean =
        selectionRestoreComplete || selectedId != null
}
