// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.input.InputPad
import br.dev.edgard.easygamepad.protocol.DeviceProfile
import br.dev.edgard.easygamepad.protocol.GattProfile
import br.dev.edgard.easygamepad.protocol.DeviceCapability
import br.dev.edgard.easygamepad.protocol.ConfigurationProtocol

data class SavedController(
    val address: String,
    val name: String = "EasySMX X10",
    val modelId: String = DeviceProfile.X10.id,
    val inputDescriptor: String? = null,
    val gatt: GattProfile = GattProfile(),
)

data class ControllerDevice(
    val id: String,
    val name: String,
    val modelId: String? = null,
    val address: String? = null,
    val inputId: Int? = null,
    val inputDescriptor: String? = null,
    val gatt: GattProfile = GattProfile(),
    val automaticInput: Boolean = false,
    val inputBatteryPercent: Int? = null,
    val inputBatteryCharging: Boolean = false,
) {
    val isX10 get() = modelId == DeviceProfile.X10.id
    val profile get() = DeviceProfile.find(modelId)
    val capabilities get() = profile?.capabilities ?: setOf(DeviceCapability.INPUT_TEST)
    fun supports(capability: DeviceCapability) = capability in capabilities
    /** KRLY (QMacro) configuration: the X10 profile editor, tools and Apply. */
    val supportsConfiguration get() = supports(DeviceCapability.KRLY_CONFIGURATION)
    /** KeyLinker configuration: X20 / X20 Pro identification and read-only settings. */
    val supportsKeyLinker get() = supports(DeviceCapability.KEYLINKER_CONFIGURATION)
    /** Has a separate BLE configuration link the app may open after the user chose it. */
    val hasConfigurationLink get() = supportsConfiguration || supportsKeyLinker
    val configurationProtocol: ConfigurationProtocol? get() = profile?.configurationProtocol
    val usesX10Art get() = supports(DeviceCapability.X10_ART)
    /** Genuine product artwork for this model, or null for the generic drawing. */
    val artwork get() = ControllerArtworkCatalog.forModel(modelId)
    /** Product name for the subtitle when the user renamed the controller. */
    val productName get() = profile?.name
    val rearButtons get() = profile?.rearButtons.orEmpty()
    val shortId get() = address?.takeLast(5) ?: inputId?.let { "#$it" }.orEmpty()
}

/** HID identity is explicitly linked to BLE identity by the user, never inferred from an Xbox name. */
fun controllerDevices(saved: List<SavedController>, pads: List<InputPad>, runtimeLinks: Map<String, Int> = emptyMap(),
    inputNames: Map<String, String> = emptyMap(), sessionNames: Map<String, String> = emptyMap()): List<ControllerDevice> {
    val result = saved.map { controller ->
        val candidates = pads.filter { it.descriptor == controller.inputDescriptor }
        val useOnlyInput = saved.size == 1 && pads.size == 1 && controller.inputDescriptor == null && !runtimeLinks.containsKey(controller.address)
        val pad = if (runtimeLinks.containsKey(controller.address)) candidates.firstOrNull { it.id == runtimeLinks[controller.address] }
            else candidates.singleOrNull() ?: if (useOnlyInput) pads.single() else null
        ControllerDevice("ble:${controller.address}", controller.name, controller.modelId, controller.address,
            pad?.id, controller.inputDescriptor, controller.gatt, automaticInput = useOnlyInput,
            inputBatteryPercent = pad?.batteryPercent, inputBatteryCharging = pad?.batteryCharging == true)
    }
    val linked = result.mapNotNull { it.inputId }.toSet()
    return result + pads.filter { it.id !in linked }.map { pad ->
        val unique = pad.descriptor.isNotBlank() && pads.count { it.descriptor == pad.descriptor } == 1
        val name = sessionNames["${pad.id}:${pad.descriptor}"] ?: inputNames[pad.descriptor].takeIf { unique } ?: pad.name
        ControllerDevice("hid:${pad.id}", name, inputId = pad.id, inputDescriptor = pad.descriptor,
            inputBatteryPercent = pad.batteryPercent, inputBatteryCharging = pad.batteryCharging)
    }
}

internal fun controllerName(value: String): String = value.trim().also {
    require(it.isNotBlank() && it.length <= 40 && it.none(Char::isISOControl))
}

internal fun renameController(rows: List<SavedController>, address: String, value: String): List<SavedController> {
    val name = controllerName(value)
    require(rows.any { it.address == address })
    return rows.map { if (it.address == address) it.copy(name = name) else it }
}
