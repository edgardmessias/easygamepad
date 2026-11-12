// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package br.dev.edgard.easygamepad.demo

import br.dev.edgard.easygamepad.ble.BleClient
import br.dev.edgard.easygamepad.ble.LinkPhase
import br.dev.edgard.easygamepad.ble.LinkState
import br.dev.edgard.easygamepad.protocol.ConfigurationProtocol
import br.dev.edgard.easygamepad.protocol.GattProfile
import br.dev.edgard.easygamepad.protocol.GamepadTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Routes configuration traffic to either [BleClient] or [DemoConfigurationClient].
 * Only one route is active; demo never shares a session with a real controller address.
 */
class ConfigurationLinkHub(
    val ble: BleClient,
    val demo: DemoConfigurationClient,
    scope: CoroutineScope,
) : GamepadTransport {
    private enum class Route { NONE, BLE, DEMO }

    private val route = MutableStateFlow(Route.NONE)

    val link: StateFlow<LinkState> = combine(route, ble.link, demo.link) { active, bleLink, demoLink ->
        when (active) {
            Route.DEMO -> demoLink
            Route.BLE -> bleLink
            Route.NONE -> LinkState()
        }
    }.stateIn(scope, SharingStarted.Eagerly, LinkState())

    val usesDemo: Boolean get() = route.value == Route.DEMO

    override val notifications = route.flatMapLatest { active ->
        when (active) {
            Route.BLE -> ble.notifications
            Route.DEMO -> demo.notifications
            Route.NONE -> emptyFlow()
        }
    }

    override suspend fun write(frame: ByteArray) = activeTransport().write(frame)

    override fun abort(cause: Throwable) = activeTransport().abort(cause)

    fun log(direction: String, message: String) = when (route.value) {
        Route.DEMO -> demo.log(direction, message)
        else -> ble.log(direction, message)
    }

    fun connectBle(address: String, profile: GattProfile, protocol: ConfigurationProtocol, displayName: String) {
        route.value = Route.BLE
        demo.disconnect()
        ble.connect(address, profile, protocol, displayName)
    }

    fun connectDemo() {
        route.value = Route.DEMO
        ble.stopScan()
        ble.disconnect()
        demo.connect()
    }

    fun disconnectAll() {
        demo.disconnect()
        ble.stopScan()
        ble.disconnect()
        route.value = Route.NONE
    }

    /** Closes only the in-process demo route without touching an open BLE configuration session. */
    fun disconnectDemo() {
        demo.disconnect()
        if (route.value == Route.DEMO) route.value = Route.NONE
    }

    private fun activeTransport(): GamepadTransport = when (route.value) {
        Route.DEMO -> demo
        Route.BLE -> ble
        Route.NONE -> error("No configuration link is open")
    }
}
