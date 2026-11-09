// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.ble.LinkPhase
import br.dev.edgard.easygamepad.ble.LinkState
import br.dev.edgard.easygamepad.protocol.*
import br.dev.edgard.easygamepad.protocol.keylinker.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class KeyLinkerRepositoryTest {
    private class Pad : GamepadTransport {
        val link = MutableStateFlow(LinkState())
        private val incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
        override val notifications = incoming.asSharedFlow()
        val writes = mutableListOf<Pair<String?, Int>>()
        var holdMotorReads = false
        var holdReadbackAfterWrite = false
        var holdIdentity = false
        var aborts = 0
        private var pendingMotorSerial: Int? = null
        private var motor = bytes(8, 76, 204, 0, 0, 255, 255, 255, 255)

        fun connect(address: String) { link.value = LinkState(LinkPhase.READY, address, protocol = ConfigurationProtocol.KEYLINKER) }
        override suspend fun write(frame: ByteArray) {
            val request = KeyLinkerCodec.parse(frame)
            writes += link.value.address to request.opcode
            val reply = when (request.opcode) {
                KeyLinkerOp.READ_VID_PID_VERSION -> if (holdIdentity) null else bytes(7, 0x10, 0x13, 0x20, 9, 1, 0x23, 0x52)
                KeyLinkerOp.HOST_GUID -> bytes(0)
                KeyLinkerOp.HOST_MENU -> bytes(10, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0)
                KeyLinkerOp.SET_MODE -> bytes(4, 0xDF, 0xAA, 0, 0x90)
                KeyLinkerOp.HOST_MOTOR -> if (holdMotorReads) { pendingMotorSerial = request.serial; null } else motor
                KeyLinkerOp.WRITE_MOTOR -> { motor = request.payload; holdMotorReads = holdReadbackAfterWrite; bytes(0) }
                else -> null
            }
            if (reply != null) incoming.emit(KeyLinkerCodec.build(KeyLinkerOp.RESPONSE, reply, request.serial, nonce = 0))
        }
        fun releaseMotorRead() {
            holdMotorReads = false
            val serial = requireNotNull(pendingMotorSerial)
            pendingMotorSerial = null
            check(incoming.tryEmit(KeyLinkerCodec.build(KeyLinkerOp.RESPONSE, motor, serial, nonce = 0)))
        }
        override fun abort(cause: Throwable) { aborts++; link.value = LinkState() }
    }

    @Test fun aQueuedWriteAndRefreshNeverMigrateToANewConnection() = runTest {
        val pad = Pad()
        val notices = mutableListOf<Notice>()
        val repository = KeyLinkerRepository(pad, pad.link, backgroundScope, { _, _ -> }, notices::add)
        pad.connect("first"); runCurrent()
        val original = requireNotNull(repository.state.value.settings?.motor?.valueOrNull)
        repository.preview(KeyLinkerFeature.VIBRATION) { serials ->
            KeyLinkerClient.planMotor(original.withEdits(leftPercent = 60), serials, KeyLinkerFeature.VIBRATION, "60%", original)
        }
        pad.holdMotorReads = true
        repository.readSettingsAsync(); runCurrent()
        assertTrue(repository.state.value.busy)
        repository.sendPreviewAsync()
        repository.refreshBatteryAsync()
        runCurrent()
        val writesBeforeReconnect = pad.writes.size
        pad.holdIdentity = true
        pad.connect("second"); runCurrent()

        assertEquals("second", repository.state.value.address)
        assertEquals(KeyLinkerPhase.IDENTIFYING, repository.state.value.phase)
        assertTrue(repository.state.value.busy)
        assertNull(repository.state.value.settings); assertNull(repository.state.value.preview)
        assertEquals(listOf("second" to KeyLinkerOp.READ_VID_PID_VERSION), pad.writes.drop(writesBeforeReconnect))
        assertEquals(0, pad.aborts)
        assertTrue(notices.isEmpty())
    }

    @Test fun motorWriteStaysBusyUntilItsReadbackCompletes() = runTest {
        val pad = Pad()
        val repository = KeyLinkerRepository(pad, pad.link, backgroundScope, { _, _ -> }, {})
        pad.connect("first"); runCurrent()
        val original = requireNotNull(repository.state.value.settings?.motor?.valueOrNull)
        repository.preview(KeyLinkerFeature.VIBRATION) { serials ->
            KeyLinkerClient.planMotor(original.withEdits(leftPercent = 60), serials, KeyLinkerFeature.VIBRATION, "60%", original)
        }
        pad.holdReadbackAfterWrite = true
        repository.sendPreviewAsync(); runCurrent()
        assertTrue(repository.state.value.busy)
        assertEquals(30, repository.state.value.settings?.motor?.valueOrNull?.leftPercent)
        pad.releaseMotorRead(); runCurrent()
        assertFalse(repository.state.value.busy)
        assertEquals(60, repository.state.value.settings?.motor?.valueOrNull?.leftPercent)
        assertEquals(80, repository.state.value.settings?.motor?.valueOrNull?.rightPercent)
        assertEquals(1, pad.writes.count { it.second == KeyLinkerOp.WRITE_MOTOR })
    }
}
