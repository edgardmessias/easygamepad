// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.protocol.ControllerConfig

const val WORKSPACE_DRAFT_LIMIT = 8
const val WORKSPACE_JSON_LIMIT = 200_000

data class ControllerDraftSnapshot(
    val config: ControllerConfig,
    val baseline: ControllerConfig,
    val linkedSavedProfileId: String? = null,
) {
    val dirty: Boolean get() = config != baseline
}

data class DraftSwitchResult(
    val config: ControllerConfig,
    val linkedSavedProfileId: String?,
)

/** Per-controller editor workspace; identity is the stable device id, not the display name. */
class ControllerDrafts(private val maxEntries: Int = WORKSPACE_DRAFT_LIMIT) {
    private val workspaces = LinkedHashMap<String, ControllerDraftSnapshot>()

    fun switch(
        from: String?,
        to: String?,
        current: ControllerConfig,
        fromSavedProfileId: String?,
        transferSetup: Boolean = false,
    ): DraftSwitchResult {
        if (from != null) {
            remember(from, current, fromSavedProfileId)
        }
        if (transferSetup && to != null) {
            remember(to, current, fromSavedProfileId)
        }
        if (to == null) return DraftSwitchResult(ControllerConfig(), null)
        val snap = workspaces[to]
        return DraftSwitchResult(snap?.config ?: ControllerConfig(), snap?.linkedSavedProfileId)
    }

    fun hasWorkspace(id: String): Boolean = workspaces.containsKey(id)

    fun remember(id: String, config: ControllerConfig, linkedSavedProfileId: String?) {
        val baseline = workspaces[id]?.baseline ?: config
        put(id, ControllerDraftSnapshot(config, baseline, linkedSavedProfileId))
    }

    fun adoptSavedBaseline(id: String, savedConfig: ControllerConfig, profileId: String) {
        val live = workspaces[id]?.config ?: savedConfig
        put(id, ControllerDraftSnapshot(live, savedConfig, profileId))
    }

    fun clearSavedProfileLinkEverywhere(profileId: String) {
        workspaces.keys.toList().forEach { key ->
            workspaces[key]?.takeIf { it.linkedSavedProfileId == profileId }?.let {
                put(key, it.copy(linkedSavedProfileId = null))
            }
        }
    }

    /** Applies a successful save to a workspace without disturbing a newer live draft. */
    fun completeSave(workspaceKey: String, savedConfig: ControllerConfig, profileId: String, liveConfig: ControllerConfig?) {
        val current = workspaces[workspaceKey]?.config ?: liveConfig ?: savedConfig
        val live = liveConfig ?: current
        put(
            workspaceKey,
            ControllerDraftSnapshot(
                config = live,
                baseline = savedConfig,
                linkedSavedProfileId = profileId,
            ),
        )
    }

    fun adoptBaseline(id: String?, config: ControllerConfig, linkedSavedProfileId: String? = null) {
        if (id == null) return
        put(id, ControllerDraftSnapshot(config, config, linkedSavedProfileId))
    }

    fun markSaved(id: String?, config: ControllerConfig, profileId: String) {
        if (id == null) return
        put(id, ControllerDraftSnapshot(config, config, profileId))
    }

    fun clearSavedLink(id: String?) {
        if (id == null) return
        workspaces[id]?.let { put(id, it.copy(linkedSavedProfileId = null)) }
    }

    fun isDirty(id: String?, config: ControllerConfig): Boolean {
        val snap = id?.let(workspaces::get)
        return when {
            snap != null -> config != snap.baseline
            else -> config != ControllerConfig()
        }
    }

    fun linkedSavedProfile(id: String?): String? = id?.let { workspaces[it]?.linkedSavedProfileId }

    fun export(): Map<String, ControllerDraftSnapshot> = workspaces.toMap()

    fun restore(map: Map<String, ControllerDraftSnapshot>) {
        workspaces.clear()
        map.entries.take(maxEntries).forEach { (id, snap) -> workspaces[id] = snap }
    }

    private fun put(id: String, snap: ControllerDraftSnapshot) {
        workspaces.remove(id)
        workspaces[id] = snap
        while (workspaces.size > maxEntries) {
            val eldest = workspaces.keys.first()
            workspaces.remove(eldest)
        }
    }
}

enum class DraftStatusKind { NEW, EDITING_SAVED, UNSAVED_CHANGES, CLEAN }

fun draftStatusKind(dirty: Boolean, linkedSavedProfileId: String?): DraftStatusKind = when {
    dirty && linkedSavedProfileId != null -> DraftStatusKind.UNSAVED_CHANGES
    dirty -> DraftStatusKind.NEW
    linkedSavedProfileId != null -> DraftStatusKind.EDITING_SAVED
    else -> DraftStatusKind.CLEAN
}
