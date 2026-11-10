// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import br.dev.edgard.easygamepad.protocol.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject

private val Context.hubPreferences by preferencesDataStore("easy_gamepad")
data class SavedProfile(val id: String, val config: ControllerConfig)
data class HubPreferences(
    val language: String = "auto", val theme: String = "dark", val address: String = "",
    val gatt: GattProfile = GattProfile(), val profiles: List<SavedProfile> = emptyList(),
    val controllers: List<SavedController> = emptyList(),
    val inputNames: Map<String, String> = emptyMap(),
    val storageWarnings: List<StorageLoadWarning> = emptyList(),
    val storageIoError: Boolean = false,
    val workspaceDrafts: Map<String, ControllerDraftSnapshot> = emptyMap(),
)

class ProfileStore(context: Context) {
    private val store = context.hubPreferences
    private val controllersKey = stringPreferencesKey("controllers")
    private val controllersRootKey = stringPreferencesKey("controllers_unparsed_root")

    private fun MutablePreferences.mutateControllers(transform: (List<SavedController>) -> List<SavedController>) {
        val current = this[controllersKey] ?: this[controllersRootKey]
        val decoded = decodeControllersLenient(current ?: "[]")
        backupUnreadableControllersRoot(this, decoded)
        this[controllersKey] = rewriteControllersJson(current, transform)
    }
    private val storageIoError = MutableStateFlow(false)
    val preferences = combine(
        store.data.catch { error ->
            when (error) {
                is CancellationException -> throw error
                is java.io.IOException -> {
                    storageIoError.value = true
                    emit(emptyPreferences())
                }
                else -> throw error
            }
        },
        storageIoError,
    ) { values, ioError ->
        loadHubPreferences(
            values[stringPreferencesKey("language")],
            values[stringPreferencesKey("theme")],
            values[stringPreferencesKey("address")],
            values[stringPreferencesKey("service")],
            values[stringPreferencesKey("write")],
            values[stringPreferencesKey("notify")],
            values[stringPreferencesKey("profiles")],
            values[stringPreferencesKey("controllers")],
            values[stringPreferencesKey("inputNames")],
            values[stringPreferencesKey("workspace_drafts")],
            ioError,
        ).preferences
    }

    private suspend fun editStore(block: suspend (MutablePreferences) -> Unit) {
        try {
            store.edit(block)
            storageIoError.value = false
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: java.io.IOException) {
            storageIoError.value = true
            throw StoragePersistenceException(error)
        }
    }
    suspend fun language(tag: String) { require(tag in listOf("auto", "en", "pt-BR")); editStore { it[stringPreferencesKey("language")] = tag } }
    /** Migrate the old in-app choice once; afterwards Android's language picker is authoritative. */
    suspend fun reconcileSystemLanguage(systemTag: String): String {
        var language = "auto"
        editStore { values ->
            val initialized = values[booleanPreferencesKey("systemLocaleInitialized")] ?: false
            language = when {
                systemTag.startsWith("pt") -> "pt-BR"
                systemTag.isNotBlank() -> "en"
                initialized -> "auto"
                else -> values[stringPreferencesKey("language")] ?: "auto"
            }
            values[stringPreferencesKey("language")] = language
            values[booleanPreferencesKey("systemLocaleInitialized")] = true
        }
        return language
    }
    suspend fun theme(theme: String) { require(theme in listOf("dark", "light", "system")); editStore { it[stringPreferencesKey("theme")] = theme } }
    /**
     * Remembers a verified configuration address. [modelId] is stored in the existing `controllers`
     * JSON `model` field. The legacy single-controller keys (`service`, `write`, `notify`) keep
     * describing the X10/KRLY channel only, so a KeyLinker controller never overwrites them.
     */
    suspend fun device(address: String, gatt: GattProfile, inputDescriptor: String? = null, modelId: String? = null) { editStore {
        if (address.isNotBlank()) {
            it.mutateControllers { saved ->
                val old = saved.firstOrNull { row -> row.address == address }
                val model = DeviceProfile.find(modelId ?: old?.modelId) ?: DeviceProfile.X10
                val controller = (old ?: SavedController(address, name = model.name, modelId = model.id)).copy(
                    gatt = gatt,
                    inputDescriptor = inputDescriptor ?: old?.inputDescriptor,
                    modelId = model.id,
                )
                val remaining = saved.filterNot { row -> row.address == address }.map { row ->
                    if (inputDescriptor != null && row.inputDescriptor == inputDescriptor) row.copy(inputDescriptor = null) else row
                }
                remaining + controller
            }
        }
        it[stringPreferencesKey("address")] = address
        val krly = DeviceProfile.find(modelId)?.configurationProtocol != ConfigurationProtocol.KEYLINKER
        if (krly) {
            it[stringPreferencesKey("service")] = gatt.service
            it[stringPreferencesKey("write")] = gatt.write
            it[stringPreferencesKey("notify")] = gatt.notify
        }
    } }
    /** Corrects the saved model after the controller identified itself, on the user's request. */
    suspend fun model(address: String, modelId: String) { editStore { values ->
        val model = requireNotNull(DeviceProfile.find(modelId))
        val current = values[controllersKey] ?: values[controllersRootKey]
        require(decodeControllersLenient(current ?: "[]").items.any { it.address == address })
        values.mutateControllers { rows ->
            rows.map {
                if (it.address != address) it
                else it.copy(modelId = model.id, gatt = model.gatt, name = if (it.name == DeviceProfile.find(it.modelId)?.name) model.name else it.name)
            }
        }
    } }
    suspend fun linkInput(address: String, descriptor: String?) { editStore { values ->
        values.mutateControllers { saved ->
            saved.map {
                when {
                    it.address == address -> it.copy(inputDescriptor = descriptor)
                    descriptor != null && it.inputDescriptor == descriptor -> it.copy(inputDescriptor = null)
                    else -> it
                }
            }
        }
    } }
    suspend fun rename(address: String, name: String) { editStore { values ->
        val rows = values[controllersKey]?.let { decodeControllersLenient(it).items }
            ?: values[controllersRootKey]?.let { decodeControllersLenient(it).items }
            ?: values[stringPreferencesKey("address")]?.takeIf { it.isNotBlank() }?.let {
                listOf(
                    SavedController(
                        address,
                        gatt = GattProfile(
                            values[stringPreferencesKey("service")] ?: GattProfile().service,
                            values[stringPreferencesKey("write")] ?: GattProfile().write,
                            values[stringPreferencesKey("notify")] ?: GattProfile().notify,
                        ),
                    ),
                )
            }.orEmpty()
        values.mutateControllers { renameController(rows, address, name) }
    } }
    suspend fun renameInput(descriptor: String, name: String) {
        require(descriptor.isNotBlank())
        val checked = controllerName(name)
        editStore { values ->
            val names = JSONObject(values[stringPreferencesKey("inputNames")] ?: "{}")
            names.put(descriptor, checked)
            values[stringPreferencesKey("inputNames")] = names.toString()
        }
    }
    suspend fun save(profile: SavedProfile) {
        profile.config.validate()
        editStore {
            val current = it[stringPreferencesKey("profiles")] ?: it[stringPreferencesKey("profiles_unparsed_root")]
            val decoded = decodeProfilesLenient(current ?: "[]")
            backupUnreadableProfilesRoot(it, decoded)
            it[stringPreferencesKey("profiles")] = rewriteProfilesJson(current) { mergeSavedProfile(it, profile) }
        }
    }
    suspend fun delete(id: String) {
        editStore {
            val current = it[stringPreferencesKey("profiles")] ?: it[stringPreferencesKey("profiles_unparsed_root")]
            val decoded = decodeProfilesLenient(current ?: "[]")
            backupUnreadableProfilesRoot(it, decoded)
            it[stringPreferencesKey("profiles")] = rewriteProfilesJson(current) { rows -> rows.filterNot { p -> p.id == id } }
        }
    }

    suspend fun persistWorkspaceDrafts(workspaces: Map<String, ControllerDraftSnapshot>) {
        editStore {
            it[stringPreferencesKey("workspace_drafts")] = encodeWorkspaceDrafts(workspaces)
        }
    }

    companion object {
        fun export(config: ControllerConfig): String = ProfileExportCodec.export(config)
        fun import(json: String): ControllerConfig = ProfileExportCodec.import(json)
    }
}
