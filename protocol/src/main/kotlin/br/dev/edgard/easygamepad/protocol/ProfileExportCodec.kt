// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.protocol

import org.json.JSONArray
import org.json.JSONObject

/** JSON profile import/export for EasyGamepad; unknown config keys are ignored on read. */
object ProfileExportCodec {
    const val FORMAT = "easygamepad-profile"
    const val VERSION = 1
    const val MAX_BYTES = 100_000

    private val x10ImportSources = PadKey.outputs + listOf(PadKey.M1, PadKey.M2, PadKey.M3, PadKey.M4)

    fun export(config: ControllerConfig): String = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("device", DeviceProfile.X10.id)
        .put("config", encodeConfig(config))
        .toString(2)

    fun import(json: String): ControllerConfig {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Profile file is too large" }
        val root = JSONObject(json)
        require(root.getString("format") == FORMAT) { "Unknown profile format" }
        require(root.getInt("version") == VERSION) { "Unsupported profile version" }
        require(root.getString("device") == DeviceProfile.X10.id) { "Profile is for another device" }
        return decodeConfig(root.getJSONObject("config")).also { it.validate() }
    }

    fun encodeConfig(config: ControllerConfig): JSONObject {
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

    fun decodeConfig(root: JSONObject): ControllerConfig {
        fun stick(key: String): StickSettings {
            val s = root.getJSONObject(key)
            return StickSettings(s.getInt("min"), s.getInt("max"), s.getBoolean("invertX"), s.getBoolean("invertY"))
        }
        fun trigger(key: String): TriggerSettings {
            val s = root.getJSONObject(key)
            return TriggerSettings(s.getInt("min"), s.getInt("max"))
        }
        val map = root.getJSONObject("mappings")
        val mappings = map.keys().asSequence().associate { keyName ->
            val source = PadKey.valueOf(keyName)
            require(source in x10ImportSources) { "Unsupported mapping source: $keyName" }
            val target = PadKey.valueOf(map.getString(keyName))
            require(target in PadKey.outputs) { "Invalid mapping target: ${map.getString(keyName)}" }
            source to target
        }
        val macros = root.getJSONArray("macros")
        val turbos = root.getJSONArray("turbos")
        require(macros.length() <= 4 && turbos.length() <= 16)
        return ControllerConfig(
            root.getString("name"),
            mappings,
            stick("leftStick"), stick("rightStick"), trigger("leftTrigger"), trigger("rightTrigger"),
            root.getInt("leftVibration"), root.getInt("rightVibration"),
            List(macros.length()) { i ->
                val m = macros.getJSONObject(i)
                val steps = m.getJSONArray("steps")
                require(steps.length() <= 32)
                Macro(PadKey.valueOf(m.getString("trigger")), m.getBoolean("repeat"), List(steps.length()) { j ->
                    val s = steps.getJSONObject(j)
                    MacroStep(PadKey.valueOf(s.getString("key")), s.getInt("holdMs"), s.getInt("delayMs"))
                })
            },
            List(turbos.length()) { i ->
                val t = turbos.getJSONObject(i)
                Turbo(PadKey.valueOf(t.getString("key")), t.getBoolean("automatic"), t.getInt("rate"))
            },
        )
    }
}
