// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.protocol

/** Values returned by QueryMacroInfoReq (119), not editor defaults. */
data class ControllerSettings(
    val leftTrigger: TriggerSettings,
    val rightTrigger: TriggerSettings,
    val leftStick: StickSettings,
    val rightStick: StickSettings,
    val leftVibration: Int,
    val rightVibration: Int,
    val turbos: List<Turbo>,
    val mappings: Map<PadKey, PadKey> = emptyMap(),
    val macros: List<Macro> = emptyList(),
    val disabledRearPaddles: Set<PadKey> = emptySet(),
) {
    fun mergeInto(draft: ControllerConfig): ControllerConfig {
        val rear = DeviceProfile.X10.rearButtons.toSet()
        val keepMacros = draft.macros.filter { it.trigger !in rear }
        return draft.copy(
            mappings = mappings, leftTrigger = leftTrigger, rightTrigger = rightTrigger,
            leftStick = leftStick, rightStick = rightStick,
            leftVibration = leftVibration, rightVibration = rightVibration, turbos = turbos,
            macros = keepMacros + macros,
            disabledRearPaddles = disabledRearPaddles,
        )
    }

    companion object {
        fun decode(content: ByteArray): ControllerSettings {
            var offset = 0
            fun section(): ByteArray {
                require(offset < content.size) { "Missing settings section" }
                val length = content[offset++].u8()
                require(offset + length <= content.size) { "Truncated settings section" }
                return content.copyOfRange(offset, offset + length).also { offset += length }
            }
            val turbo = section(); val triggers = section(); val sticks = section(); val motors = section()
            require(offset == content.size && turbo.size % 3 == 0 && triggers.size == 4 && sticks.size == 9 && motors.size == 2)
            require(sticks[2].u8() in 0..1 && sticks[3].u8() in 0..1 && sticks[6].u8() in 0..1 && sticks[7].u8() in 0..1)
            // Swapping complete sticks / D-pad is outside the current editor model.
            require(sticks[8].u8() == 0) { "Controller uses an unsupported stick swap" }
            val turbos = turbo.toList().chunked(3).map { values ->
                require(values[1].u8() in 0..1)
                Turbo(requireNotNull(PadKey.from(values[0].u8())), values[1].u8() == 1, values[2].u8())
            }
            val settings = ControllerSettings(
                TriggerSettings(triggers[0].u8(), triggers[1].u8()), TriggerSettings(triggers[2].u8(), triggers[3].u8()),
                StickSettings(sticks[0].u8(), sticks[1].u8(), sticks[2].u8() == 1, sticks[3].u8() == 1),
                StickSettings(sticks[4].u8(), sticks[5].u8(), sticks[6].u8() == 1, sticks[7].u8() == 1),
                motors[0].u8(), motors[1].u8(), turbos,
            )
            settings.mergeInto(ControllerConfig()).validate() // Validate the read values, independently of an unfinished local draft.
            return settings
        }
    }
}

/** Query 119 adds a selector before the total/current page pair. */
class SettingsPages {
    private var next: Int? = null
    private var total: Int? = null
    private var content = byteArrayOf()
    fun accept(raw: ByteArray): ControllerSettings? {
        require(raw.size >= 6 && raw[0].u8() == raw.size && raw[1].u8() == 119 && raw[2].u8() == 2)
        val pages = raw[3].u8(); val page = raw[4].u8()
        require(pages in 0..64 && page <= pages &&
            (if (next == null) page in 0..1 else page == next) && (total == null || total == pages))
        total = pages; next = page + 1
        content += raw.copyOfRange(5, raw.size - 1)
        require(content.size <= 4096)
        return if (page == pages) ControllerSettings.decode(content) else null
    }
}
