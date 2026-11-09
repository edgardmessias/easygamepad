// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad

import android.app.Application
import br.dev.edgard.easygamepad.ble.BleClient
import br.dev.edgard.easygamepad.data.ControllerRepository
import br.dev.edgard.easygamepad.data.KeyLinkerRepository
import br.dev.edgard.easygamepad.data.ProfileStore
import br.dev.edgard.easygamepad.input.GamepadInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class HubApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val ble by lazy { BleClient(this, scope) }
    val repository by lazy { ControllerRepository(ble, scope) }
    val keyLinker by lazy { KeyLinkerRepository(ble, scope) { repository.notice(it.english, it.portuguese, it.error) } }
    val store by lazy { ProfileStore(this) }
    val input by lazy { GamepadInput(this) }
}
