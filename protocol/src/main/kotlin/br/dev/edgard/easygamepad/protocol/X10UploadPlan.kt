// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.protocol

/** Exactly the settings writes reviewed and sent; omitted entries never generate an erase command. */
data class X10UploadPlan(
    val commands: List<Pair<Int, ByteArray>>,
    val mappings: Map<PadKey, PadKey>,
    val macroTriggers: List<PadKey>,
    val turboKeys: List<PadKey>,
    val omittedMappings: List<PadKey>,
    val clearedPaddles: List<PadKey>,
) {
    val restoredMappings get() = mappings.filter { (from, to) -> from in PadKey.outputs && from == to }
    val assignedMappings get() = mappings - restoredMappings.keys
}

fun ControllerConfig.x10UploadPlan(): X10UploadPlan {
    validate()
    val rearButtons = DeviceProfile.X10.rearButtons.toSet()
    val macroByTrigger = macros.associateBy { it.trigger }
    val writes = mappings.filter { (from, to) -> from != to || from in PadKey.outputs }
    val rearMappings = writes.filterKeys { it in rearButtons && it !in macroByTrigger }
    val cleared = disabledRearPaddles.filter { it !in macroByTrigger && it !in rearMappings }
    val commands = buildList<Pair<Int, ByteArray>> {
        macros.filter { it.trigger !in rearButtons || it.steps.isNotEmpty() }.forEach { macro ->
            macro.steps.indices.forEach { add(58 to KrlyCodec.macroStep(macro, it)) }
        }
        rearButtons.forEach { rear ->
            when {
                rear in macroByTrigger && macroByTrigger[rear]!!.steps.isEmpty() ->
                    add(58 to X10PaddleMacroCodec.clearStep(rear))
                rear in rearMappings ->
                    add(58 to KrlyCodec.rearPaddlePassthrough(rear, rearMappings.getValue(rear)))
                rear in cleared ->
                    add(58 to X10PaddleMacroCodec.clearStep(rear))
            }
        }
        turbos.forEach { add(55 to bytes(it.key.code, if (it.automatic) 1 else 0, it.rate)) }
        add(57 to bytes(leftTrigger.min, leftTrigger.max, rightTrigger.min, rightTrigger.max))
        add(62 to KrlyCodec.sticks(leftStick, rightStick))
        writes.filter { (from, _) -> from !in rearButtons }.forEach { (from, to) ->
            add(61 to bytes(from.code, to.code))
        }
        add(53 to bytes(leftVibration, rightVibration))
        add(52 to byteArrayOf())
    }
    val uploadedMacros = macros.map { it.trigger }.distinct()
    val omitted = (PadKey.outputs + rearButtons).filter { paddle ->
        paddle !in mappings && paddle !in cleared && paddle !in macroByTrigger
    }
    return X10UploadPlan(commands, mappings.toMap(), uploadedMacros, turbos.map { it.key }, omitted, cleared)
}
