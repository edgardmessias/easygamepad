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
) {
    val restoredMappings get() = mappings.filter { (from, to) -> from in PadKey.outputs && from == to }
    val assignedMappings get() = mappings - restoredMappings.keys
}

fun ControllerConfig.x10UploadPlan(): X10UploadPlan {
    validate()
    val commands = buildList<Pair<Int, ByteArray>> {
        macros.forEach { macro -> macro.steps.indices.forEach { add(58 to KrlyCodec.macroStep(macro, it)) } }
        turbos.forEach { add(55 to bytes(it.key.code, if (it.automatic) 1 else 0, it.rate)) }
        add(57 to bytes(leftTrigger.min, leftTrigger.max, rightTrigger.min, rightTrigger.max))
        add(62 to KrlyCodec.sticks(leftStick, rightStick))
        mappings.forEach { (from, to) -> add(61 to bytes(from.code, to.code)) }
        add(53 to bytes(leftVibration, rightVibration))
        add(52 to byteArrayOf())
    }
    return X10UploadPlan(commands, mappings.toMap(), macros.map { it.trigger }, turbos.map { it.key },
        (PadKey.outputs + DeviceProfile.X10.rearButtons).filter { it !in mappings })
}
