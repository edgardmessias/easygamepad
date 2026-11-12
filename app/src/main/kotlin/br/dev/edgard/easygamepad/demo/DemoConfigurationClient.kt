// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.demo

import br.dev.edgard.easygamepad.BuildConfig
import br.dev.edgard.easygamepad.ble.BleLog
import br.dev.edgard.easygamepad.ble.LinkPhase
import br.dev.edgard.easygamepad.ble.LinkState
import br.dev.edgard.easygamepad.protocol.ConfigurationProtocol
import br.dev.edgard.easygamepad.protocol.GamepadTransport
import br.dev.edgard.easygamepad.protocol.SimulatedX10KrlyPad
import br.dev.edgard.easygamepad.protocol.hex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DemoConfigurationClient(private val scope: CoroutineScope) : GamepadTransport {
    private val pad = SimulatedX10KrlyPad()
    private val mutableLink = MutableStateFlow(LinkState())
    val link = mutableLink.asStateFlow()
    private val mutableLogs = MutableStateFlow<List<BleLog>>(emptyList())
    val logs = mutableLogs.asStateFlow()

    override val notifications = pad.notifications

    val simulator: SimulatedX10KrlyPad get() = pad

    fun log(direction: String, message: String) {
        mutableLogs.update { (it + BleLog(direction = direction, message = message)).takeLast(200) }
        if (BuildConfig.DEBUG) android.util.Log.i("EasyGamepad-Demo", "$direction $message")
    }

    fun connect() {
        mutableLink.value = LinkState(
            LinkPhase.READY,
            DemoController.ADDRESS,
            "EasySMX X10 (demo)",
            protocol = ConfigurationProtocol.KRLY,
        )
        log("LINK", "Demo configuration link ready · ${DemoController.ADDRESS}")
    }

    fun disconnect() {
        mutableLink.value = LinkState()
        log("LINK", "Demo configuration link closed")
    }

    fun factoryReset() {
        pad.resetToDefaults()
        log("DEMO", "Simulator reset to defaults")
    }

    override suspend fun write(frame: ByteArray) {
        check(mutableLink.value.phase == LinkPhase.READY) { "Demo controller not connected" }
        log("TX", frame.hex())
        pad.onWrite(frame)
    }

    override fun abort(cause: Throwable) {
        log("ERROR", cause.message ?: "Demo session aborted")
        disconnect()
        mutableLink.update { it.copy(error = cause.message) }
    }

    fun schedule(block: suspend () -> Unit) {
        scope.launch { runCatching { block() }.onFailure { log("DEMO", it.message ?: it.javaClass.simpleName) } }
    }
}
