// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Amjad AAYD
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.protocol.DeviceProfile
import br.dev.edgard.easygamepad.protocol.PadKey

/**
 * Product artwork for a controller model. Images and layout geometry come unmodified from x20ctl
 * (see THIRD_PARTY.md); models without an entry use the drawn generic gamepad.
 *
 * @property drawablePrefix prefix of the `drawable-nodpi` resources: `_front_body`, `_rear`, `_stick_left`, `_stick_right`.
 * @property homeCenter Guide button centre in the 1536×1024 source space, when the artwork marks one.
 */
data class ControllerArtwork(
    val modelId: String,
    val productName: String,
    val layoutAsset: String,
    val drawablePrefix: String,
    val rearButtons: List<PadKey>,
    val homeCenter: Pair<Float, Float>? = null,
) {
    companion object {
        const val SOURCE_WIDTH = 1536f
        const val SOURCE_HEIGHT = 1024f
    }
}

object ControllerArtworkCatalog {
    val X10 = ControllerArtwork(DeviceProfile.X10.id, DeviceProfile.X10.name, "controllers/x10-layout.json", "x10",
        DeviceProfile.X10.rearButtons, homeCenter = 775f to 235f)
    val X20 = ControllerArtwork(DeviceProfile.X20.id, DeviceProfile.X20.name, "controllers/x20-layout.json", "x20",
        DeviceProfile.X20.rearButtons)
    val X20_PRO = ControllerArtwork(DeviceProfile.X20_PRO.id, DeviceProfile.X20_PRO.name, "controllers/x20-pro-layout.json", "x20pro",
        DeviceProfile.X20_PRO.rearButtons)
    val ALL = listOf(X10, X20, X20_PRO)

    /** Null means "draw the generic gamepad": never borrow another model's photograph. */
    fun forModel(modelId: String?): ControllerArtwork? = ALL.firstOrNull { it.modelId == modelId }
}
