// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad

import android.app.Application
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import br.dev.edgard.easygamepad.ble.BleClient
import br.dev.edgard.easygamepad.data.ControllerRepository
import br.dev.edgard.easygamepad.data.KeyLinkerRepository
import br.dev.edgard.easygamepad.data.ProfileStore
import br.dev.edgard.easygamepad.demo.ConfigurationLinkHub
import br.dev.edgard.easygamepad.demo.DemoAdbCommands
import br.dev.edgard.easygamepad.demo.DemoAdbReceiver
import br.dev.edgard.easygamepad.demo.DemoBridge
import br.dev.edgard.easygamepad.demo.DemoConfigurationClient
import br.dev.edgard.easygamepad.input.GamepadInput
import br.dev.edgard.easygamepad.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class HubApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val ble by lazy { BleClient(this, scope) }
    val demo by lazy { DemoConfigurationClient(scope) }
    val configuration by lazy { ConfigurationLinkHub(ble, demo, scope) }
    val repository by lazy { ControllerRepository(configuration, scope) }
    val keyLinker by lazy { KeyLinkerRepository(ble, scope, repository::notice) }
    val store by lazy { ProfileStore(this) }
    val input by lazy { GamepadInput(this) }
    val demoBridge by lazy { DemoBridge(this) }
    private val demoAdbReceiver = DemoAdbReceiver()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) registerDemoAdbReceiver()
    }

    private fun registerDemoAdbReceiver() {
        ContextCompat.registerReceiver(this, demoAdbReceiver, IntentFilter(DemoAdbCommands.ACTION), ContextCompat.RECEIVER_EXPORTED)
    }
}
