// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import br.dev.edgard.easygamepad.protocol.*
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
)

class ProfileStore(context: Context) {
    private val store = context.hubPreferences
    val preferences = store.data.catch { error ->
        if (error is java.io.IOException) emit(emptyPreferences()) else throw error
    }.map { values ->
        HubPreferences(
            language = values[stringPreferencesKey("language")] ?: "auto",
            theme = values[stringPreferencesKey("theme")] ?: "dark",
            address = values[stringPreferencesKey("address")] ?: "",
            gatt = GattProfile(values[stringPreferencesKey("service")] ?: GattProfile().service,
                values[stringPreferencesKey("write")] ?: GattProfile().write,
                values[stringPreferencesKey("notify")] ?: GattProfile().notify),
            profiles = runCatching { decodeProfiles(values[stringPreferencesKey("profiles")] ?: "[]") }.getOrDefault(emptyList()),
            inputNames = runCatching {
                val names = JSONObject(values[stringPreferencesKey("inputNames")] ?: "{}")
                names.keys().asSequence().associateWith { names.getString(it) }
            }.getOrDefault(emptyMap()),
            controllers = runCatching {
                val legacyAddress = values[stringPreferencesKey("address")].orEmpty()
                val json = values[stringPreferencesKey("controllers")]
                if (json != null) decodeControllers(json) else if (legacyAddress.isNotBlank()) listOf(SavedController(legacyAddress,
                    gatt = GattProfile(values[stringPreferencesKey("service")] ?: GattProfile().service,
                        values[stringPreferencesKey("write")] ?: GattProfile().write, values[stringPreferencesKey("notify")] ?: GattProfile().notify))) else emptyList()
            }.getOrDefault(emptyList()),
        )
    }
    suspend fun language(tag: String) { require(tag in listOf("auto", "en", "pt-BR")); store.edit { it[stringPreferencesKey("language")] = tag } }
    /** Migrate the old in-app choice once; afterwards Android's language picker is authoritative. */
    suspend fun reconcileSystemLanguage(systemTag: String): String {
        var language = "auto"
        store.edit { values ->
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
    suspend fun theme(theme: String) { require(theme in listOf("dark", "light", "system")); store.edit { it[stringPreferencesKey("theme")] = theme } }
    /**
     * Remembers a verified configuration address. [modelId] is stored in the existing `controllers`
     * JSON `model` field. The legacy single-controller keys (`service`, `write`, `notify`) keep
     * describing the X10/KRLY channel only, so a KeyLinker controller never overwrites them.
     */
    suspend fun device(address: String, gatt: GattProfile, inputDescriptor: String? = null, modelId: String? = null) { store.edit {
        val previousAddress = it[stringPreferencesKey("address")].orEmpty()
        val saved = it[stringPreferencesKey("controllers")]?.let(::decodeControllers)
            ?: if (previousAddress.isNotBlank()) listOf(SavedController(previousAddress)) else emptyList()
        if (address.isNotBlank()) {
            val old = saved.firstOrNull { row -> row.address == address }
            val model = DeviceProfile.find(modelId ?: old?.modelId) ?: DeviceProfile.X10
            val controller = (old ?: SavedController(address, name = model.name, modelId = model.id)).copy(gatt = gatt,
                inputDescriptor = inputDescriptor ?: old?.inputDescriptor, modelId = model.id)
            val remaining = saved.filterNot { row -> row.address == address }.map { row ->
                if (inputDescriptor != null && row.inputDescriptor == inputDescriptor) row.copy(inputDescriptor = null) else row
            }
            it[stringPreferencesKey("controllers")] = encodeControllers(remaining + controller)
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
    suspend fun model(address: String, modelId: String) { store.edit { values ->
        val model = requireNotNull(DeviceProfile.find(modelId))
        val rows = values[stringPreferencesKey("controllers")]?.let(::decodeControllers).orEmpty()
        require(rows.any { it.address == address })
        values[stringPreferencesKey("controllers")] = encodeControllers(rows.map {
            if (it.address != address) it
            else it.copy(modelId = model.id, gatt = model.gatt, name = if (it.name == DeviceProfile.find(it.modelId)?.name) model.name else it.name)
        })
    } }
    suspend fun linkInput(address: String, descriptor: String?) { store.edit { values ->
        val saved = values[stringPreferencesKey("controllers")]?.let(::decodeControllers).orEmpty()
        values[stringPreferencesKey("controllers")] = encodeControllers(saved.map {
            when { it.address == address -> it.copy(inputDescriptor = descriptor)
                descriptor != null && it.inputDescriptor == descriptor -> it.copy(inputDescriptor = null)
                else -> it }
        })
    } }
    suspend fun rename(address: String, name: String) { store.edit { values ->
        val rows = values[stringPreferencesKey("controllers")]?.let(::decodeControllers)
            ?: values[stringPreferencesKey("address")]?.takeIf { it.isNotBlank() }?.let { address ->
                listOf(SavedController(address, gatt = GattProfile(
                    values[stringPreferencesKey("service")] ?: GattProfile().service,
                    values[stringPreferencesKey("write")] ?: GattProfile().write,
                    values[stringPreferencesKey("notify")] ?: GattProfile().notify)))
            }.orEmpty()
        values[stringPreferencesKey("controllers")] = encodeControllers(renameController(rows, address, name))
    } }
    suspend fun renameInput(descriptor: String, name: String) {
        require(descriptor.isNotBlank())
        val checked = controllerName(name)
        store.edit { values ->
            val names = JSONObject(values[stringPreferencesKey("inputNames")] ?: "{}")
            names.put(descriptor, checked)
            values[stringPreferencesKey("inputNames")] = names.toString()
        }
    }
    suspend fun save(profile: SavedProfile) { profile.config.validate(); store.edit {
        val current = decodeProfiles(it[stringPreferencesKey("profiles")] ?: "[]").filterNot { p -> p.id == profile.id }
        require(current.size < 30)
        it[stringPreferencesKey("profiles")] = encodeProfiles(current + profile)
    } }
    suspend fun delete(id: String) { store.edit {
        it[stringPreferencesKey("profiles")] = encodeProfiles(decodeProfiles(it[stringPreferencesKey("profiles")] ?: "[]").filterNot { p -> p.id == id })
    } }

    companion object {
        private fun encodeControllers(rows: List<SavedController>) = JSONArray().also { array -> rows.forEach { row ->
            array.put(JSONObject().put("address", row.address).put("name", row.name).put("model", row.modelId)
                .put("input", row.inputDescriptor ?: JSONObject.NULL).put("service", row.gatt.service)
                .put("write", row.gatt.write).put("notify", row.gatt.notify))
        } }.toString()
        private fun decodeControllers(json: String): List<SavedController> {
            val array = JSONArray(json); require(array.length() <= 30)
            return List(array.length()) { index -> val row = array.getJSONObject(index)
                SavedController(row.getString("address"), row.getString("name"), row.getString("model"),
                    if (row.isNull("input")) null else row.getString("input"),
                    GattProfile(row.getString("service"), row.getString("write"), row.getString("notify"))) }
        }
        fun export(config: ControllerConfig): String = JSONObject().put("format", "easygamepad-profile").put("version", 1)
            .put("device", DeviceProfile.X10.id).put("config", encode(config)).toString(2)
        fun import(json: String): ControllerConfig {
            require(json.length <= 100_000)
            val root = JSONObject(json)
            val fmt = root.getString("format")
            require(
                fmt == "easygamepad-profile" &&
                    root.getInt("version") == 1 &&
                    root.getString("device") == DeviceProfile.X10.id,
            )
            return decode(root.getJSONObject("config")).also { it.validate() }
        }
        private fun encode(config: ControllerConfig): JSONObject {
            fun stick(s: StickSettings) = JSONObject().put("min", s.min).put("max", s.max).put("invertX", s.invertX).put("invertY", s.invertY)
            fun trigger(t: TriggerSettings) = JSONObject().put("min", t.min).put("max", t.max)
            return JSONObject().put("name", config.name)
                .put("mappings", JSONObject().also { map -> config.mappings.forEach { (a, b) -> map.put(a.name, b.name) } })
                .put("leftStick", stick(config.leftStick)).put("rightStick", stick(config.rightStick))
                .put("leftTrigger", trigger(config.leftTrigger)).put("rightTrigger", trigger(config.rightTrigger))
                .put("leftVibration", config.leftVibration).put("rightVibration", config.rightVibration)
                .put("macros", JSONArray().also { array -> config.macros.forEach { macro -> array.put(JSONObject()
                    .put("trigger", macro.trigger.name).put("repeat", macro.repeat).put("steps", JSONArray().also { steps ->
                        macro.steps.forEach { steps.put(JSONObject().put("key", it.key.name).put("holdMs", it.holdMs).put("delayMs", it.delayMs)) }
                    })) } })
                .put("turbos", JSONArray().also { array -> config.turbos.forEach { array.put(JSONObject().put("key", it.key.name).put("automatic", it.automatic).put("rate", it.rate)) } })
        }
        private fun decode(root: JSONObject): ControllerConfig {
            fun stick(key: String): StickSettings { val s = root.getJSONObject(key); return StickSettings(s.getInt("min"), s.getInt("max"), s.getBoolean("invertX"), s.getBoolean("invertY")) }
            fun trigger(key: String): TriggerSettings { val s = root.getJSONObject(key); return TriggerSettings(s.getInt("min"), s.getInt("max")) }
            val map = root.getJSONObject("mappings")
            val macros = root.getJSONArray("macros")
            val turbos = root.getJSONArray("turbos")
            require(macros.length() <= 4 && turbos.length() <= 16)
            return ControllerConfig(root.getString("name"), map.keys().asSequence().associate { PadKey.valueOf(it) to PadKey.valueOf(map.getString(it)) },
                stick("leftStick"), stick("rightStick"), trigger("leftTrigger"), trigger("rightTrigger"),
                root.getInt("leftVibration"), root.getInt("rightVibration"),
                List(macros.length()) { i -> val m = macros.getJSONObject(i); val steps = m.getJSONArray("steps"); require(steps.length() <= 32)
                    Macro(PadKey.valueOf(m.getString("trigger")), m.getBoolean("repeat"), List(steps.length()) { j ->
                        val s = steps.getJSONObject(j); MacroStep(PadKey.valueOf(s.getString("key")), s.getInt("holdMs"), s.getInt("delayMs")) }) },
                List(turbos.length()) { i -> val t = turbos.getJSONObject(i); Turbo(PadKey.valueOf(t.getString("key")), t.getBoolean("automatic"), t.getInt("rate")) })
        }
        private fun encodeProfiles(profiles: List<SavedProfile>) = JSONArray().also { array -> profiles.forEach { array.put(JSONObject().put("id", it.id).put("config", encode(it.config))) } }.toString()
        private fun decodeProfiles(json: String): List<SavedProfile> {
            val array = JSONArray(json)
            return List(array.length()) { i -> val row = array.getJSONObject(i); SavedProfile(row.getString("id"), decode(row.getJSONObject("config")).also { it.validate() }) }
        }
    }
}
