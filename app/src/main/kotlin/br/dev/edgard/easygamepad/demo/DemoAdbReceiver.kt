// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.dev.edgard.easygamepad.HubApplication
import br.dev.edgard.easygamepad.ble.LinkPhase
import br.dev.edgard.easygamepad.input.GamepadInput
import kotlinx.coroutines.launch

/**
 * Handles developer ADB broadcasts while the demo controller is selected and connected.
 * Registered only in debug builds; lifecycle commands never bypass [DemoAdbCoordinator].
 */
class DemoAdbReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DemoAdbCommands.ACTION) return
        val app = context.applicationContext as? HubApplication ?: return
        val extras = buildMap {
            intent.getStringExtra("cmd")?.let { put("cmd", it) }
            intent.extras?.keySet()?.forEach { key ->
                if (key != "cmd") put(key, intent.extras?.get(key)?.toString())
            }
        }
        val parsed = DemoAdbCommands.parse(extras["cmd"], extras) ?: return
        when (parsed) {
            DemoAdbCommands.Parsed.Connect,
            DemoAdbCommands.Parsed.Disconnect,
            DemoAdbCommands.Parsed.FactoryReset,
            -> app.demoBridge.handle(parsed)
            else -> {
                if (!app.configuration.usesDemo) return
                if (app.configuration.link.value.phase != LinkPhase.READY) return
                if (!DemoController.isDemoAddress(app.configuration.link.value.address)) return
                app.demoBridge.handle(parsed)
            }
        }
    }
}

/** Applies parsed demo ADB commands on the main thread. */
class DemoBridge(private val app: HubApplication) {
    var coordinator: DemoAdbCoordinator? = null

    fun handle(command: DemoAdbCommands.Parsed) {
        when (command) {
            DemoAdbCommands.Parsed.Connect,
            DemoAdbCommands.Parsed.Disconnect,
            DemoAdbCommands.Parsed.FactoryReset,
            -> app.scope.launch {
                when (command) {
                    DemoAdbCommands.Parsed.Connect -> coordinator?.adbConnectDemo()
                    DemoAdbCommands.Parsed.Disconnect -> coordinator?.adbDisconnectDemo()
                    DemoAdbCommands.Parsed.FactoryReset -> coordinator?.adbFactoryReset()
                }
            }
            is DemoAdbCommands.Parsed.Press -> app.input.simulateKey(command.keyCode, command.down)
            is DemoAdbCommands.Parsed.Axis -> app.input.simulateAxes(DemoAdbCommands.axisValues(command.stick, command.x, command.y))
            is DemoAdbCommands.Parsed.Battery -> {
                command.percent?.let { app.configuration.demo.simulator.batteryPercent = it }
                command.charging?.let { app.configuration.demo.simulator.batteryCharging = it }
                command.low?.let { app.configuration.demo.simulator.batteryLow = it }
                if (app.configuration.usesDemo && app.configuration.link.value.phase == LinkPhase.READY) {
                    app.scope.launch { app.repository.refreshBattery() }
                }
            }
        }
    }

    fun ensureDemoInputSelected() {
        app.input.setDemoOverlay(true)
        app.input.select(GamepadInput.DEMO_PAD_ID)
    }
}
