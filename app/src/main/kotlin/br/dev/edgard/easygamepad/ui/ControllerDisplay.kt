// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import androidx.compose.runtime.Composable
import br.dev.edgard.easygamepad.data.ControllerDevice
import br.dev.edgard.easygamepad.demo.DemoController

@Composable
fun ControllerDevice.displayName(): String =
    if (id == DemoController.ID) tr(DemoController.DISPLAY_NAME, DemoController.DISPLAY_NAME) else name

@Composable
fun ControllerDevice.listSubtitle(): String =
    if (id == DemoController.ID) {
        tr("Simulated · no Bluetooth", "Simulado · sem Bluetooth")
    } else {
        (productName?.takeIf { it != displayName() }?.let { "$it · " } ?: "") + shortId
    }

@Composable
fun SimulatedControllerBanner() {
    InfoBanner(
        tr(
            "Simulated controller — profile changes stay in the app and are not written to hardware.",
            "Controle simulado — alterações de perfil ficam no app e não são gravadas no hardware.",
        ),
    )
}
