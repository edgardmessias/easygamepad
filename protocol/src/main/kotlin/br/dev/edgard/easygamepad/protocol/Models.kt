// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.protocol

data class GattProfile(
    val service: String = "0000ff00-0000-1000-8000-00805f9b34fb",
    val write: String = "0000ff01-0000-1000-8000-00805f9b34fb",
    val notify: String = "0000ff02-0000-1000-8000-00805f9b34fb",
)

enum class DeviceCapability { INPUT_TEST, KRLY_CONFIGURATION, BATTERY_QUERY, CALIBRATION, FIRMWARE_INFO, MACROS, TURBO,
    STICK_TUNING, TRIGGER_TUNING, VIBRATION_CONFIGURATION, REAR_BUTTONS, X10_ART, KEYLINKER_CONFIGURATION }

/** KRLY: EasySMX X10 (QMacro). KEYLINKER: EasySMX X20 / X20 Pro (KeyLinker app). Never mixed on one link. */
enum class ConfigurationProtocol { KRLY, KEYLINKER }

data class DeviceProfile(val id: String, val name: String, val gatt: GattProfile,
    val capabilities: Set<DeviceCapability> = setOf(DeviceCapability.INPUT_TEST),
    val configurationProtocol: ConfigurationProtocol? = null,
    val rearButtons: List<PadKey> = emptyList()) {
    companion object {
        val X10 = DeviceProfile("easysmx-x10", "EasySMX X10", GattProfile(), setOf(
            DeviceCapability.INPUT_TEST, DeviceCapability.KRLY_CONFIGURATION, DeviceCapability.BATTERY_QUERY,
            DeviceCapability.CALIBRATION, DeviceCapability.FIRMWARE_INFO, DeviceCapability.MACROS, DeviceCapability.TURBO,
            DeviceCapability.STICK_TUNING, DeviceCapability.TRIGGER_TUNING, DeviceCapability.VIBRATION_CONFIGURATION,
            DeviceCapability.REAR_BUTTONS, DeviceCapability.X10_ART),
            ConfigurationProtocol.KRLY, listOf(PadKey.M1, PadKey.M2))
        /** KeyLinker GATT service, write and notify characteristics (X20CTL protocol.py). */
        val KEYLINKER_GATT = GattProfile(
            "d7f010e0-660d-46e9-96c3-19c4148bdab5",
            "d7f010e1-660d-46e9-96c3-19c4148bdab5",
            "d7f010e2-660d-46e9-96c3-19c4148bdab5")
        private val KEYLINKER_CAPABILITIES = setOf(DeviceCapability.INPUT_TEST, DeviceCapability.KEYLINKER_CONFIGURATION,
            DeviceCapability.BATTERY_QUERY, DeviceCapability.FIRMWARE_INFO, DeviceCapability.MACROS,
            DeviceCapability.STICK_TUNING, DeviceCapability.TRIGGER_TUNING, DeviceCapability.VIBRATION_CONFIGURATION,
            DeviceCapability.REAR_BUTTONS)
        val X20 = DeviceProfile("easysmx-x20", "EasySMX X20", KEYLINKER_GATT, KEYLINKER_CAPABILITIES,
            ConfigurationProtocol.KEYLINKER, listOf(PadKey.M1, PadKey.M2, PadKey.M3, PadKey.M4))
        val X20_PRO = DeviceProfile("easysmx-x20-pro", "EasySMX X20 Pro", KEYLINKER_GATT, KEYLINKER_CAPABILITIES + DeviceCapability.TURBO,
            ConfigurationProtocol.KEYLINKER, listOf(PadKey.M1, PadKey.M2, PadKey.M3, PadKey.M4, PadKey.M5, PadKey.M6))
        val ALL = listOf(X10, X20, X20_PRO)
        fun find(id: String?) = ALL.firstOrNull { it.id == id }
    }
}

enum class PadKey(val code: Int, val label: String) {
    A(160, "A"), B(161, "B"), X(162, "X"), Y(163, "Y"),
    LB(164, "LB"), LT(165, "LT"), LS(166, "L3"),
    RB(167, "RB"), RT(168, "RT"), RS(169, "R3"),
    SELECT(170, "View −"), START(171, "Menu +"),
    UP(211, "↑"), DOWN(212, "↓"), LEFT(209, "←"), RIGHT(210, "→"),
    M1(1, "M1"), M2(2, "M2"), M3(3, "M3"), M4(4, "M4"), M5(5, "M5"), M6(6, "M6");
    companion object {
        fun from(code: Int) = entries.firstOrNull { it.code == code }
        val outputs get() = entries.filter { it.code >= 160 }
    }
}

data class Battery(val percent: Int, val charging: Boolean, val low: Boolean, val receivedAt: Long)
data class Firmware(val project: String, val protocol: String, val firmware: String, val hardware: String)
enum class CalibrationKind(val wireType: Int) { STICKS(1), TRIGGERS(2), SELF(0) }
enum class CalibrationPhase { IDLE, STARTING, ACTIVE, NEEDS_RETRY, SUCCEEDED, FAILED, CANCELLED }
data class CalibrationState(
    val kind: CalibrationKind = CalibrationKind.STICKS,
    val phase: CalibrationPhase = CalibrationPhase.IDLE,
    val result: Int? = null,
    val stopRequired: Boolean = false,
)
data class StickSettings(
    val min: Int = 0, val max: Int = 100,
    val invertX: Boolean = false, val invertY: Boolean = false,
) { fun validate() { require(min in 0..99 && max in 1..100 && min < max) } }
data class TriggerSettings(val min: Int = 0, val max: Int = 100) {
    fun validate() { require(min in 0..99 && max in 1..100 && min < max) }
}
data class MacroStep(val key: PadKey = PadKey.A, val holdMs: Int = 100, val delayMs: Int = 100) {
    fun validate() { require(key in PadKey.outputs); require(holdMs in 1..65535); require(delayMs in 0..65535) }
}
data class Macro(val trigger: PadKey = PadKey.M1, val repeat: Boolean = false, val steps: List<MacroStep> = emptyList()) {
    fun validate() {
        require(trigger.code in 1..4); require(steps.size in 1..32)
        steps.forEach { it.validate() }
    }
}
data class Turbo(val key: PadKey = PadKey.A, val automatic: Boolean = false, val rate: Int = 15) {
    fun validate() { require(key in PadKey.outputs); require(rate in 1..20) }
}
data class ControllerConfig(
    val name: String = "X10",
    val mappings: Map<PadKey, PadKey> = PadKey.outputs.associateWith { it },
    val leftStick: StickSettings = StickSettings(),
    val rightStick: StickSettings = StickSettings(),
    val leftTrigger: TriggerSettings = TriggerSettings(),
    val rightTrigger: TriggerSettings = TriggerSettings(),
    val leftVibration: Int = 70, val rightVibration: Int = 70,
    val macros: List<Macro> = emptyList(),
    val turbos: List<Turbo> = emptyList(),
) {
    fun validate() {
        require(name.isNotBlank() && name.length <= 40)
        leftStick.validate(); rightStick.validate(); leftTrigger.validate(); rightTrigger.validate()
        require(leftVibration in 0..100 && rightVibration in 0..100)
        require(mappings.values.all { it in PadKey.outputs })
        require(macros.map { it.trigger }.distinct().size == macros.size)
        require(turbos.map { it.key }.distinct().size == turbos.size)
        macros.forEach { it.validate() }; turbos.forEach { it.validate() }
    }
}
