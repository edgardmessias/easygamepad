// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.protocol

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

enum class X10SettingField { BUTTONS, STICKS, TRIGGERS, VIBRATION, TURBO }

/** Only fields exposed by queries 119 and 80 can be checked; macros and persistence are outside this result. */
sealed interface X10ApplyResult {
    data class Matched(val settings: ControllerSettings) : X10ApplyResult
    data class Mismatch(val settings: ControllerSettings, val fields: Set<X10SettingField>) : X10ApplyResult
    data class Unconfirmed(val reason: String) : X10ApplyResult
}

/** Compare effective settings: the X10 can omit unchanged button mappings and reorder turbo entries. */
fun ControllerSettings.differences(config: ControllerConfig): Set<X10SettingField> = buildSet {
    fun customMappings(values: Map<PadKey, PadKey>) = values.filterNot { (from, to) -> from in PadKey.outputs && from == to }
    if (customMappings(mappings) != customMappings(config.mappings)) add(X10SettingField.BUTTONS)
    if (leftStick != config.leftStick || rightStick != config.rightStick) add(X10SettingField.STICKS)
    if (leftTrigger != config.leftTrigger || rightTrigger != config.rightTrigger) add(X10SettingField.TRIGGERS)
    if (leftVibration != config.leftVibration || rightVibration != config.rightVibration) add(X10SettingField.VIBRATION)
    if (turbos.toSet() != config.turbos.toSet()) add(X10SettingField.TURBO)
}

/** KRLY profile upload followed by a complete, independent settings and mapping read. */
class X10ProfileClient(private val session: CommandSession) {
    suspend fun readSettings(progress: (Float) -> Unit = {}): ControllerSettings {
        val pages = SettingsPages()
        var settings: ControllerSettings? = null
        session.request(119, bytes(2, 1)) { raw -> pages.accept(raw).also { settings = it } != null }
        progress(.6f)
        val mappings = MappingPages()
        var outputs: Map<PadKey, PadKey>? = null
        session.request(80, bytes(1)) { raw -> mappings.accept(raw).also { outputs = it } != null }
        return requireNotNull(settings).copy(mappings = requireNotNull(outputs)).also { progress(1f) }
    }

    suspend fun apply(config: ControllerConfig, uploadMode: (Boolean) -> Unit = {}, progress: (Float) -> Unit = {}): X10ApplyResult {
        config.validate()
        var modeEntered = false
        fun setMode(entered: Boolean) { modeEntered = entered; uploadMode(entered) }
        try {
            session.ack(1, bytes(16))
            setMode(true)
            session.ack(54, bytes(0, 0))
            val commands = buildList<Pair<Int, ByteArray>> {
                config.macros.forEach { macro -> macro.steps.indices.forEach { add(58 to KrlyCodec.macroStep(macro, it)) } }
                config.turbos.forEach { add(55 to bytes(it.key.code, if (it.automatic) 1 else 0, it.rate)) }
                add(57 to bytes(config.leftTrigger.min, config.leftTrigger.max, config.rightTrigger.min, config.rightTrigger.max))
                add(62 to KrlyCodec.sticks(config.leftStick, config.rightStick))
                config.mappings.forEach { (from, to) -> add(61 to bytes(from.code, to.code)) }
                add(53 to bytes(config.leftVibration, config.rightVibration))
                add(52 to byteArrayOf())
            }
            commands.forEachIndexed { index, (id, payload) ->
                session.ack(id, payload)
                progress(.75f * (index + 1f) / commands.size)
            }
            session.ack(1, bytes(0))
            setMode(false)
            return try {
                val settings = readSettings { progress(.75f + .25f * it) }
                val fields = settings.differences(config)
                if (fields.isEmpty()) X10ApplyResult.Matched(settings) else X10ApplyResult.Mismatch(settings, fields)
            } catch (timeout: TimeoutCancellationException) {
                X10ApplyResult.Unconfirmed("Settings read-back timed out")
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                X10ApplyResult.Unconfirmed(error.message ?: "Settings read-back failed")
            }
        } finally {
            withContext(NonCancellable) {
                if (modeEntered && !session.isClosed) runCatching {
                    withTimeout(5500) { session.ack(1, bytes(0)); setMode(false) }
                }
            }
        }
    }
}
