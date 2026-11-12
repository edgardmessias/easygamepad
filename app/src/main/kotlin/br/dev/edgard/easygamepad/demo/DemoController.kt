// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.demo

import br.dev.edgard.easygamepad.data.ControllerDevice
import br.dev.edgard.easygamepad.protocol.DeviceProfile
/** Fixed identity for the in-app X10 demo; this address is never opened on real Bluetooth. */
object DemoController {
    const val ID = "demo:easysmx-x10"
    const val ADDRESS = "02:00:5E:00:53:01"
    const val DISPLAY_NAME = "Demo X10"

    fun isDemoAddress(address: String?) = address.equals(ADDRESS, ignoreCase = true)
    fun isDevice(device: ControllerDevice?) = device?.id == ID || isDemoAddress(device?.address)

    fun device() = ControllerDevice(
        id = ID,
        name = DISPLAY_NAME,
        modelId = DeviceProfile.X10.id,
        address = ADDRESS,
        gatt = DeviceProfile.X10.gatt,
        automaticInput = true,
    )
}
