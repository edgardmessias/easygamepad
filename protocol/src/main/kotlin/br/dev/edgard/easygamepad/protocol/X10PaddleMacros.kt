// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.protocol

/** One step in a paddle macro slot (cmd 121). */
data class PaddleMacroStep(val holdMs: Int, val delayMs: Int, val keys: List<Int>)

/** Decoded macro slot for rear triggers M1–M4. Byte 1 follows cmd 58: 0 = repeat, 1 = play once. */
data class PaddleMacroSlot(val trigger: PadKey, val repeat: Boolean = false, val steps: List<PaddleMacroStep>)

/** Merged face mappings, rear macros and cleared paddle slots from query 121. */
data class PaddleSlotMerge(
    val mappings: Map<PadKey, PadKey>,
    val macros: List<Macro>,
    val disabledRearPaddles: Set<PadKey> = emptySet(),
)

/** QueryMacroSubReq (121) uses page headers like mapping query 80. */
class MacroSubPages {
    private var expectedPage: Int? = null
    private var totalPages: Int? = null
    private var content = byteArrayOf()
    fun accept(raw: ByteArray): ByteArray? {
        require(raw.size >= 5 && raw[0].u8() == raw.size && raw[1].u8() == 121)
        val total = raw[2].u8(); val page = raw[3].u8()
        require(total in 0..64 && page <= total &&
            (if (expectedPage == null) page in 0..1 else page == expectedPage) &&
            (totalPages == null || totalPages == total))
        totalPages = total
        content += raw.copyOfRange(4, raw.size - 1)
        require(content.size <= 4096)
        expectedPage = page + 1
        if (page < total) return null
        return content
    }
}

private fun ControllerConfig.rearMacroTriggers(): Set<PadKey> {
    val rear = DeviceProfile.X10.rearButtons.toSet()
    return macros.map { it.trigger }.filter { it in rear }.toSet()
}

/** Rear paddles whose explicit remap [nextMacros] would clear (any rear macro slot with a mapping). */
fun ControllerConfig.rearPaddlesNeedingMacroConfirm(nextMacros: List<Macro>): Set<PadKey> {
    val rear = DeviceProfile.X10.rearButtons.toSet()
    val nextRear = nextMacros.map { it.trigger }.filter { it in rear }.toSet()
    return nextRear.filter { it in mappings }.toSet()
}

/** Rear-macro editor transitions: removed paddles become disabled; assigned paddles drop remap + disabled. */
fun ControllerConfig.withRearMacros(macros: List<Macro>): ControllerConfig {
    val rear = DeviceProfile.X10.rearButtons.toSet()
    val previousRear = rearMacroTriggers()
    val nextRear = macros.map { it.trigger }.filter { it in rear }.toSet()
    val removed = previousRear - nextRear
    return copy(
        macros = macros,
        disabledRearPaddles = (disabledRearPaddles + removed - nextRear),
        mappings = mappings.filterKeys { it !in nextRear },
    )
}

/** Assign a rear remap: drops macro and cleared-paddle state for that trigger. */
fun ControllerConfig.withRearRemap(source: PadKey, target: PadKey): ControllerConfig = copy(
    mappings = mappings + (source to target),
    macros = macros.filterNot { it.trigger == source },
    disabledRearPaddles = disabledRearPaddles - source,
)

fun ControllerConfig.withRearPaddleDisabled(source: PadKey): ControllerConfig = copy(
    mappings = mappings - source,
    macros = macros.filterNot { it.trigger == source },
    disabledRearPaddles = disabledRearPaddles + source,
)

fun ControllerConfig.rearHasMacro(source: PadKey): Boolean = macros.any { it.trigger == source }

object X10PaddleMacroCodec {
    private val rearTriggers = DeviceProfile.X10.rearButtons.toSet()

    /** Overwrites a paddle slot with no output keys (official empty childKeys list). */
    fun clearStep(trigger: PadKey): ByteArray {
        require(trigger.code in 1..4)
        return bytes(trigger.code, 1, 0, 0, 0, 0, 0)
    }

    fun parseContent(content: ByteArray): List<PaddleMacroSlot> = buildList {
        var offset = 0
        var guard = 0
        while (offset < content.size && guard++ < 512) {
            val parsed = parseSlot(content, offset) ?: run {
                offset = (offset + 1).coerceAtMost(content.size)
                continue
            }
            if (parsed.nextOffset <= offset) {
                offset = (offset + 7).coerceAtMost(content.size)
                continue
            }
            if (parsed.trigger.code in 1..4) {
                add(PaddleMacroSlot(parsed.trigger, parsed.repeat, parsed.steps))
            }
            offset = parsed.nextOffset
        }
    }

    internal fun mergeFromSlots(faceMappings: Map<PadKey, PadKey>, slots: List<PaddleMacroSlot>): PaddleSlotMerge {
        val faceOnly = faceMappings.filterKeys { it !in rearTriggers }
        val rearMappings = mutableMapOf<PadKey, PadKey>()
        val rearMacros = mutableListOf<Macro>()
        val disabled = mutableSetOf<PadKey>()
        for (slot in slots) {
            if (slot.trigger !in rearTriggers) continue
            when {
                slot.steps.isEmpty() -> disabled += slot.trigger
                slot.steps.size == 1 && slot.steps[0].isIdle -> disabled += slot.trigger
                slot.steps.size == 1 && slot.steps[0].isPassthrough() -> {
                    val step = slot.steps[0]
                    val key = PadKey.from(step.keys.single()) ?: continue
                    rearMappings[slot.trigger] = key
                }
                else -> rearMacros += slot.toMacro()
            }
        }
        return PaddleSlotMerge(faceOnly + rearMappings, rearMacros, disabled)
    }

    private fun parseSlot(content: ByteArray, start: Int): ParsedSlot? {
        if (start + 7 > content.size) return null
        val trigger = PadKey.from(content[start].u8()) ?: return null
        val repeat = content[start + 1].u8() == 0
        val stepCount = content[start + 6].u8()
        var offset = start + 7
        val headerEnd = start + 7
        val steps = buildList {
            repeat(stepCount) {
                val stepStart = offset
                if (offset + 6 > content.size) {
                    offset = content.size
                    return@buildList
                }
                offset++ // step index
                val hold = (content[offset].u8() shl 8) or content[offset + 1].u8()
                offset += 2
                val delay = (content[offset].u8() shl 8) or content[offset + 1].u8()
                offset += 2
                val keyCount = content[offset++].u8()
                val keys = when {
                    keyCount == 0 -> emptyList()
                    keyCount > 8 -> {
                        offset = (stepStart + 6).coerceAtMost(content.size)
                        listOf(keyCount)
                    }
                    offset + keyCount <= content.size -> List(keyCount) { content[offset++].u8() }
                    else -> {
                        offset = (stepStart + 6).coerceAtMost(content.size)
                        listOf(content[(stepStart + 5).coerceAtMost(content.lastIndex)].u8())
                    }
                }
                add(PaddleMacroStep(hold, delay, keys))
                if (offset <= stepStart + 6) offset = (stepStart + 6).coerceAtMost(content.size)
            }
        }
        val nextOffset = offset.coerceAtLeast(headerEnd).coerceAtMost(content.size)
        return ParsedSlot(trigger, repeat, steps, nextOffset)
    }

    private data class ParsedSlot(
        val trigger: PadKey,
        val repeat: Boolean,
        val steps: List<PaddleMacroStep>,
        val nextOffset: Int,
    )
}

private val PaddleMacroStep.isIdle get() = keys.isEmpty()

/** Passthrough uses hold 0; firmware often packs delay/keyCount into the same bytes (delay 0–1). */
private fun PaddleMacroStep.isPassthrough() =
    holdMs == 0 && delayMs in 0..1 && keys.size == 1 && PadKey.from(keys.single()) in PadKey.outputs

private fun PaddleMacroSlot.toMacro() = Macro(
    trigger = trigger,
    repeat = repeat,
    steps = steps.flatMap { step ->
        step.keys.mapNotNull { code -> PadKey.from(code) }.map { key ->
            MacroStep(key, step.holdMs.coerceAtLeast(1), step.delayMs)
        }
    },
)
