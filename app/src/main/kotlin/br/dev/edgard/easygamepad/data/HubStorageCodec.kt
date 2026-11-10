// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.protocol.DeviceProfile
import br.dev.edgard.easygamepad.protocol.GattProfile
import br.dev.edgard.easygamepad.protocol.ProfileExportCodec
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import org.json.JSONArray
import org.json.JSONObject

enum class StorageCollectionKind { PROFILES, CONTROLLERS, INPUT_NAMES }

data class StorageLoadWarning(
    val kind: StorageCollectionKind,
    val skippedEntries: Int = 0,
    val rootUnreadable: Boolean = false,
)

data class DecodedCollection<T>(
    val items: List<T>,
    val warnings: List<StorageLoadWarning> = emptyList(),
    /** Skipped JSON values rewritten verbatim on save (objects, numbers, strings, arrays, null). */
    val preservedSlots: List<PreservedJsonSlot> = emptyList(),
    /** Entire collection JSON when the root is unreadable; kept until a structured rewrite succeeds. */
    val preservedRootJson: String? = null,
)

data class HubPreferencesSnapshot(
    val preferences: HubPreferences,
    val warnings: List<StorageLoadWarning> = emptyList(),
    val storageIoError: Boolean = false,
)

fun loadHubPreferences(
    language: String?,
    theme: String?,
    address: String?,
    service: String?,
    write: String?,
    notify: String?,
    profilesJson: String?,
    controllersJson: String?,
    inputNamesJson: String?,
    workspaceDraftsJson: String? = null,
    storageIoError: Boolean = false,
): HubPreferencesSnapshot {
    val gatt = GattProfile(service ?: GattProfile().service, write ?: GattProfile().write, notify ?: GattProfile().notify)
    val legacyAddress = address.orEmpty()
    val profiles = decodeProfilesLenient(profilesJson ?: "[]")
    val (inputMap, inputWarnings) = decodeInputNamesLenient(inputNamesJson ?: "{}")
    val controllers = when {
        controllersJson != null -> decodeControllersLenient(controllersJson)
        legacyAddress.isNotBlank() -> DecodedCollection(
            listOf(SavedController(legacyAddress, gatt = gatt, modelId = DeviceProfile.X10.id)),
        )
        else -> DecodedCollection(emptyList())
    }
    val workspaceDrafts = decodeWorkspaceDraftsLenient(workspaceDraftsJson ?: "{}")
    val warnings = profiles.warnings + controllers.warnings + inputWarnings
    val prefs = HubPreferences(
        language = language ?: "auto",
        theme = theme ?: "dark",
        address = legacyAddress,
        gatt = gatt,
        profiles = profiles.items,
        controllers = controllers.items,
        inputNames = inputMap,
        storageWarnings = warnings,
        storageIoError = storageIoError,
        workspaceDrafts = workspaceDrafts,
    )
    return HubPreferencesSnapshot(prefs, warnings, storageIoError)
}

fun decodeProfilesLenient(json: String): DecodedCollection<SavedProfile> = try {
    val array = JSONArray(json)
    val items = mutableListOf<SavedProfile>()
    val preserved = mutableListOf<PreservedJsonSlot>()
    var skipped = 0
    for (index in 0 until array.length()) {
        try {
            val row = array.getJSONObject(index)
            items += SavedProfile(
                row.getString("id"),
                ProfileExportCodec.decodeConfig(row.getJSONObject("config")).also { it.validate() },
            )
        } catch (_: Exception) {
            skipped++
            preserved += PreservedJsonSlot.capture(array, index)
        }
    }
    val warnings = buildList {
        if (skipped > 0) add(StorageLoadWarning(StorageCollectionKind.PROFILES, skippedEntries = skipped))
    }
    DecodedCollection(items, warnings, preservedSlots = preserved)
} catch (_: Exception) {
    DecodedCollection(
        emptyList(),
        listOf(StorageLoadWarning(StorageCollectionKind.PROFILES, rootUnreadable = true)),
        preservedRootJson = json,
    )
}

const val MAX_STORED_CONTROLLERS = 30

fun decodeControllersLenient(json: String): DecodedCollection<SavedController> = try {
    val array = JSONArray(json)
    val items = mutableListOf<SavedController>()
    val preserved = mutableListOf<PreservedJsonSlot>()
    var skipped = 0
    for (index in 0 until array.length()) {
        try {
            val row = array.getJSONObject(index)
            val modelId = if (row.isNull("model")) DeviceProfile.X10.id else row.getString("model")
            items += SavedController(
                row.getString("address"),
                row.getString("name"),
                modelId,
                if (row.isNull("input")) null else row.getString("input"),
                GattProfile(row.getString("service"), row.getString("write"), row.getString("notify")),
            )
        } catch (_: Exception) {
            skipped++
            preserved += PreservedJsonSlot.capture(array, index)
        }
    }
    require(items.size <= MAX_STORED_CONTROLLERS)
    val warnings = buildList {
        if (skipped > 0) add(StorageLoadWarning(StorageCollectionKind.CONTROLLERS, skippedEntries = skipped))
    }
    DecodedCollection(items, warnings, preservedSlots = preserved)
} catch (_: Exception) {
    DecodedCollection(
        emptyList(),
        listOf(StorageLoadWarning(StorageCollectionKind.CONTROLLERS, rootUnreadable = true)),
        preservedRootJson = json,
    )
}

fun decodeInputNamesLenient(json: String): Pair<Map<String, String>, List<StorageLoadWarning>> = try {
    val names = JSONObject(json)
    val items = names.keys().asSequence().mapNotNull { key ->
        runCatching { key to names.getString(key) }.getOrNull()
    }.toMap()
    val skipped = names.length() - items.size
    val warnings = if (skipped > 0) listOf(StorageLoadWarning(StorageCollectionKind.INPUT_NAMES, skippedEntries = skipped)) else emptyList()
    items to warnings
} catch (_: Exception) {
    emptyMap<String, String>() to listOf(StorageLoadWarning(StorageCollectionKind.INPUT_NAMES, rootUnreadable = true))
}

fun encodeProfiles(profiles: List<SavedProfile>, preservedSlots: List<PreservedJsonSlot> = emptyList()): String = JSONArray().also { array ->
    preservedSlots.forEach { array.put(it.value) }
    profiles.forEach { array.put(JSONObject().put("id", it.id).put("config", ProfileExportCodec.encodeConfig(it.config))) }
}.toString()

fun encodeControllers(rows: List<SavedController>, preservedSlots: List<PreservedJsonSlot> = emptyList()): String = JSONArray().also { array ->
    preservedSlots.forEach { array.put(it.value) }
    rows.forEach { row ->
        array.put(
            JSONObject()
                .put("address", row.address)
                .put("name", row.name)
                .put("model", row.modelId)
                .put("input", row.inputDescriptor ?: JSONObject.NULL)
                .put("service", row.gatt.service)
                .put("write", row.gatt.write)
                .put("notify", row.gatt.notify),
        )
    }
}.toString()

fun encodeWorkspaceDrafts(workspaces: Map<String, ControllerDraftSnapshot>): String {
    val root = JSONObject()
    workspaces.entries.take(WORKSPACE_DRAFT_LIMIT).forEach { (id, snap) ->
        root.put(
            id,
            JSONObject()
                .put("config", ProfileExportCodec.encodeConfig(snap.config))
                .put("baseline", ProfileExportCodec.encodeConfig(snap.baseline))
                .put("savedProfileId", snap.linkedSavedProfileId ?: JSONObject.NULL),
        )
    }
    val json = root.toString()
    require(json.toByteArray(Charsets.UTF_8).size <= WORKSPACE_JSON_LIMIT) { "Workspace drafts exceed size limit" }
    return json
}

fun rewriteProfilesJson(current: String?, transform: (List<SavedProfile>) -> List<SavedProfile>): String {
    val decoded = decodeProfilesLenient(current ?: "[]")
    return encodeProfiles(transform(decoded.items), decoded.preservedSlots)
}

fun rewriteControllersJson(current: String?, transform: (List<SavedController>) -> List<SavedController>): String {
    val decoded = decodeControllersLenient(current ?: "[]")
    require(transform(decoded.items).size <= MAX_STORED_CONTROLLERS)
    return encodeControllers(transform(decoded.items), decoded.preservedSlots)
}

fun backupUnreadableProfilesRoot(prefs: MutablePreferences, decoded: DecodedCollection<SavedProfile>) {
    val backup = stringPreferencesKey("profiles_unparsed_root")
    decoded.preservedRootJson?.let { prefs[backup] = it }
}

fun backupUnreadableControllersRoot(prefs: MutablePreferences, decoded: DecodedCollection<SavedController>) {
    val backup = stringPreferencesKey("controllers_unparsed_root")
    decoded.preservedRootJson?.let { prefs[backup] = it }
}

fun decodeWorkspaceDraftsLenient(json: String): Map<String, ControllerDraftSnapshot> = try {
    val root = JSONObject(json)
    buildMap {
        root.keys().forEach { id ->
            runCatching {
                val row = root.getJSONObject(id)
                val config = ProfileExportCodec.decodeConfig(row.getJSONObject("config"))
                val baseline = ProfileExportCodec.decodeConfig(row.getJSONObject("baseline"))
                val linked = if (row.isNull("savedProfileId")) null else row.getString("savedProfileId")
                put(id, ControllerDraftSnapshot(config, baseline, linked))
            }
        }
    }.entries.take(WORKSPACE_DRAFT_LIMIT).associate { it.toPair() }
} catch (_: Exception) {
    emptyMap()
}

class StoragePersistenceException(cause: Throwable) : Exception(cause)
