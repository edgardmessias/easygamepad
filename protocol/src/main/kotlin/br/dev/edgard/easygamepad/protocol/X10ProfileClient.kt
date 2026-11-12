// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.protocol

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

enum class X10SettingField { BUTTONS, STICKS, TRIGGERS, VIBRATION, TURBO }

/** Result of apply with read-back over queries 119, 80 and paddle macro slots (121). */
sealed interface X10ApplyResult {
    data class Matched(val settings: ControllerSettings) : X10ApplyResult
    data class Mismatch(val settings: ControllerSettings, val fields: Set<X10SettingField>) : X10ApplyResult
    data class Unconfirmed(val reason: String) : X10ApplyResult
}

/** Compare effective settings: the X10 can omit unchanged button mappings and reorder turbo entries. */
fun ControllerSettings.differences(config: ControllerConfig): Set<X10SettingField> = buildSet {
    fun customMappings(values: Map<PadKey, PadKey>) = values.filterNot { (from, to) -> from in PadKey.outputs && from == to }
    val rearButtons = DeviceProfile.X10.rearButtons.toSet()
    val deviceRear = mappings.filterKeys { it in rearButtons }
    val draftRear = config.mappings.filterKeys { it in rearButtons }
    val faceDevice = customMappings(mappings.filterKeys { it !in rearButtons })
    val faceDraft = customMappings(config.mappings.filterKeys { it !in rearButtons })
    if (faceDevice != faceDraft || deviceRear != draftRear) add(X10SettingField.BUTTONS)
    val rearTriggers = rearButtons.toSet()
    val deviceRearMacros = macros.filter { it.trigger in rearTriggers }.associateBy { it.trigger }
    val draftRearMacros = config.macros.filter { it.trigger in rearTriggers }.associateBy { it.trigger }
    if (deviceRearMacros != draftRearMacros) add(X10SettingField.BUTTONS)
    if (disabledRearPaddles != config.disabledRearPaddles) add(X10SettingField.BUTTONS)
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
        progress(.5f)
        val mappingPages = MappingPages()
        var faceMappings: Map<PadKey, PadKey>? = null
        session.request(80, bytes(1)) { raw -> mappingPages.accept(raw).also { faceMappings = it } != null }
        progress(.85f)
        val macroPages = MacroSubPages()
        var macroBlob: ByteArray? = null
        session.request(121, bytes(1, 1)) { raw -> macroPages.accept(raw).also { macroBlob = it } != null }
        val slots = macroBlob?.let { X10PaddleMacroCodec.parseContent(it) }.orEmpty()
        val merged = X10PaddleMacroCodec.mergeFromSlots(faceMappings.orEmpty(), slots)
        return requireNotNull(settings).copy(
            mappings = merged.mappings,
            macros = merged.macros,
            disabledRearPaddles = merged.disabledRearPaddles,
        ).also { progress(1f) }
    }

    suspend fun apply(config: ControllerConfig, uploadMode: (Boolean) -> Unit = {}, progress: (Float) -> Unit = {}): X10ApplyResult {
        val plan = config.x10UploadPlan()
        var modeEntered = false
        fun setMode(entered: Boolean) { modeEntered = entered; uploadMode(entered) }
        try {
            session.ack(1, bytes(16))
            setMode(true)
            session.ack(54, bytes(0, 0))
            plan.commands.forEachIndexed { index, (id, payload) ->
                session.ack(id, payload)
                progress(.75f * (index + 1f) / plan.commands.size)
            }
            session.ack(1, bytes(0))
            setMode(false)
            return try {
                val settings = readSettings { progress(.75f + .25f * it) }
                val fields = settings.differences(config)
                if (fields.isEmpty()) X10ApplyResult.Matched(settings) else X10ApplyResult.Mismatch(settings, fields)
            } catch (timeout: TimeoutCancellationException) {
                X10ApplyResult.Unconfirmed(X10ApplyReadBackDiagnostics.TIMED_OUT)
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                X10ApplyResult.Unconfirmed(error.message ?: X10ApplyReadBackDiagnostics.FAILED)
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
