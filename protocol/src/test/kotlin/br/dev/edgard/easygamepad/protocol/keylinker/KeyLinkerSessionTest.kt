// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Amjad AAYD
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package br.dev.edgard.easygamepad.protocol.keylinker

import br.dev.edgard.easygamepad.protocol.DeviceProfile
import br.dev.edgard.easygamepad.protocol.ConfigurationProtocol
import br.dev.edgard.easygamepad.protocol.GamepadTransport
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class KeyLinkerSessionTest {
    private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    /** A fake controller that answers with RESPONSE frames echoing the request serial. */
    private class FakePad(val answers: (opcode: Int, payload: ByteArray) -> ByteArray?) : GamepadTransport {
        val incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
        override val notifications = incoming.asSharedFlow()
        val sent = mutableListOf<KeyLinkerFrame>()
        var aborted = false
        override suspend fun write(frame: ByteArray) {
            val plain = KeyLinkerCodec.parse(frame).also { sent += it }
            val reply = answers(plain.opcode, plain.payload) ?: return
            incoming.emit(reply(plain.serial, reply))
        }
        override fun abort(cause: Throwable) { aborted = true }
        fun reply(serial: Int, payload: ByteArray, opcode: Int = KeyLinkerOp.RESPONSE) =
            KeyLinkerCodec.build(opcode, payload, serial, nonce = 0x2A)
    }

    private val allowAll: (Int, ByteArray) -> Unit = { _, _ -> }

    @Test fun repliesAreMatchedBySerialAndOpcode() = runTest {
        val pad = FakePad { _, _ -> null }
        val session = KeyLinkerSession(pad, backgroundScope, allowAll)
        val call = async { session.exchange(KeyLinkerCodec.build(0x91, serial = 5, nonce = 0)) }
        runCurrent()
        pad.incoming.emit(pad.reply(6, hex("0710132009012352"))) // wrong serial
        pad.incoming.emit(pad.reply(5, hex("0710132009012352"), opcode = 0x1F)) // input stream, not a reply
        pad.incoming.emit(byteArrayOf(1, 2, 3)) // not a frame
        runCurrent(); assertFalse(call.isCompleted)
        pad.incoming.emit(pad.reply(5, hex("0710132009012352"))); runCurrent()
        assertEquals("0710132009012352", call.await()!!.joinToString("") { "%02x".format(it) })
        session.close()
    }

    @Test fun corruptedReplyIsIgnored() = runTest {
        val pad = FakePad { _, _ -> null }
        val session = KeyLinkerSession(pad, backgroundScope, allowAll)
        val call = async { session.exchange(KeyLinkerCodec.build(0x91, serial = 1, nonce = 0)) }
        runCurrent()
        val bad = pad.reply(1, hex("07101320090123")).also { it[it.size - 1] = (it.last().toInt() xor 0x55).toByte() }
        pad.incoming.emit(bad); runCurrent(); assertFalse(call.isCompleted)
        advanceTimeBy(2600); runCurrent()
        assertNull(call.await()); assertFalse(session.closed)
        session.close()
    }

    @Test fun timeoutsAreSilenceAndKeepTheSession() = runTest {
        val pad = FakePad { _, _ -> null }
        val session = KeyLinkerSession(pad, backgroundScope, allowAll)
        val read = async { session.exchange(KeyLinkerCodec.build(KeyLinkerOp.HOST_MOTOR, byteArrayOf(0), 1, nonce = 0)) }
        runCurrent(); advanceTimeBy(2600); runCurrent()
        assertNull(read.await()); assertFalse(session.closed); assertFalse(pad.aborted)
        // X20CTL's _send also carries on after a silent write; the read-back decides (see KeyLinkerClient.apply).
        val write = async { session.exchange(KeyLinkerCodec.build(KeyLinkerOp.WRITE_MOTOR, byteArrayOf(1, 0), 0x81, nonce = 0)) }
        runCurrent(); advanceTimeBy(2600); runCurrent()
        assertNull(write.await()); assertFalse(session.closed); assertFalse(pad.aborted)
        session.close()
    }

    /** A pad that stores motor writes and reports them on the next read, like the X20 does. */
    private class StoringPad(var motor: ByteArray, val ignoreWrites: Boolean = false) {
        val pad = FakePad { opcode, payload ->
            when (opcode) {
                KeyLinkerOp.HOST_MOTOR -> motor
                KeyLinkerOp.WRITE_MOTOR -> { if (!ignoreWrites) motor = payload; byteArrayOf(0) }
                KeyLinkerOp.HOST_CHANGEKEY -> byteArrayOf(0)
                KeyLinkerOp.WRITE_CHANGEKEY -> byteArrayOf(0)
                else -> null
            }
        }
    }

    private fun x20Gate() = KeyLinkerGate().apply {
        assessment = KeyLinkerEvidence.assess(KeyLinkerIdentity("1013", "2009", "1.23", 2))
        capabilities = KeyLinkerCapabilities.parse(KeyLinkerBody.unwrap(hex("0a030303000f010002c000")))
    }

    @Test fun applySendsThenConfirmsByReadingBack() = runTest {
        val store = StoringPad(hex("084c4c0000c0d40100"))
        val client = KeyLinkerClient(KeyLinkerSession(store.pad, backgroundScope, x20Gate()::authorize))
        val before = KeyLinkerMotorRecord.parse(KeyLinkerBody.unwrap(store.motor))
        val plan = KeyLinkerClient.planMotor(before.withVibration(80), client.serials, KeyLinkerFeature.VIBRATION, "80%", before)
        assertEquals(KeyLinkerWriteResult.Confirmed, client.apply(plan))
        assertEquals(listOf(KeyLinkerOp.HOST_MOTOR, KeyLinkerOp.WRITE_MOTOR, KeyLinkerOp.HOST_MOTOR), store.pad.sent.map { it.opcode })
        assertEquals(80, KeyLinkerMotorRecord.parse(KeyLinkerBody.unwrap(store.motor)).leftPercent)
    }

    @Test fun applyRefusesAStaleEditAndReportsAMismatch() = runTest {
        val store = StoringPad(hex("084c4c0000c0d40100"), ignoreWrites = true)
        val client = KeyLinkerClient(KeyLinkerSession(store.pad, backgroundScope, x20Gate()::authorize))
        val current = KeyLinkerMotorRecord.parse(KeyLinkerBody.unwrap(store.motor))
        val older = current.withVibration(10)
        val stale = KeyLinkerClient.planMotor(older.withVibration(80), client.serials, KeyLinkerFeature.VIBRATION, "80%", older)
        assertEquals(KeyLinkerWriteResult.Stale, client.apply(stale))
        assertTrue(store.pad.sent.none { it.opcode == KeyLinkerOp.WRITE_MOTOR })
        val ignored = KeyLinkerClient.planMotor(current.withVibration(80), client.serials, KeyLinkerFeature.VIBRATION, "80%", current)
        assertTrue(client.apply(ignored) is KeyLinkerWriteResult.Mismatch)
    }

    @Test fun applyPausesBetweenChunksAndStillPassesTheGate() = runTest {
        val store = StoringPad(hex("084c4c0000c0d40100"))
        val gate = x20Gate()
        val client = KeyLinkerClient(KeyLinkerSession(store.pad, backgroundScope, gate::authorize))
        val plan = KeyLinkerClient.planRemaps(KeyLinkerKeyList.X20_CHANGEKEY_SOURCES, mapOf(1 to 2), client.serials, emptyMap())
        assertTrue(plan.packets.size > 1)
        val started = currentTime
        assertTrue(client.apply(plan) is KeyLinkerWriteResult.Mismatch) // this pad never stores remaps
        assertTrue(currentTime - started >= 250L * (plan.packets.size - 1))
        assertEquals(plan.packets.size, store.pad.sent.count { it.opcode == KeyLinkerOp.WRITE_CHANGEKEY })
        gate.assessment = KeyLinkerEvidence.assess(KeyLinkerIdentity("1020", "2006", "1.44", 3)) // unvalidated mode
        assertThrows(KeyLinkerNotAuthorized::class.java) { kotlinx.coroutines.runBlocking { client.apply(plan) } }
    }

    @Test fun unauthorizedFramesAreRefusedBeforeAnythingIsWritten() = runTest {
        val pad = FakePad { _, _ -> byteArrayOf(2, 0xDF.toByte(), 0xAA.toByte()) }
        val gate = KeyLinkerGate()
        val session = KeyLinkerSession(pad, backgroundScope, gate::authorize)
        assertThrows(KeyLinkerNotAuthorized::class.java) {
            kotlinx.coroutines.runBlocking { session.exchange(KeyLinkerCodec.build(KeyLinkerOp.HOST_STICK, byteArrayOf(0), 1, nonce = 0)) }
        }
        assertTrue(pad.sent.isEmpty())
        // Identification is always allowed once the user chose this KeyLinker peripheral.
        session.exchange(KeyLinkerCodec.build(KeyLinkerOp.READ_VID_PID_VERSION, serial = 2, nonce = 0))
        assertEquals(1, pad.sent.size)
        session.close()
    }

    @Test fun identifyAsksOnlyTheThreeReadOnlyQuestions() = runTest {
        val pad = FakePad { opcode, payload ->
            when (opcode) {
                KeyLinkerOp.READ_VID_PID_VERSION -> hex("0710132009012352")
                KeyLinkerOp.HOST_GUID -> if (payload[0].toInt() == 0) hex("12a1ca51da724cb6103658c05d7d4e") else hex("20917b00")
                KeyLinkerOp.HOST_MENU -> hex("0a030303000f010002c000")
                else -> null
            }
        }
        val gate = KeyLinkerGate()
        val client = KeyLinkerClient(KeyLinkerSession(pad, backgroundScope, gate::authorize))
        val result = client.identify()
        assertEquals(listOf(0x91, 0x93, 0x93, 0xB0), pad.sent.map { it.opcode })
        assertEquals(KeyLinkerClassification.VERIFIED, result.assessment?.classification)
        assertEquals("EasySMX X20", result.guid?.model)
        assertTrue(result.findings.any { it.startsWith("Macro slots match") })
    }

    @Test fun batteryAlwaysReturnsThePadToNormalMode() = runTest {
        val pad = FakePad { opcode, _ -> if (opcode == KeyLinkerOp.SET_MODE) hex("09dfaa6990e90000005e") else null }
        val gate = KeyLinkerGate().apply { assessment = KeyLinkerEvidence.assess(KeyLinkerIdentity("1013", "2009", "1.23", 2)) }
        val client = KeyLinkerClient(KeyLinkerSession(pad, backgroundScope, gate::authorize))
        assertEquals(KeyLinkerBattery(4, true, 0x90), client.battery())
        assertEquals(listOf(KeyLinkerOp.SET_MODE, KeyLinkerOp.NORMAL_MODE), pad.sent.map { it.opcode })
    }

    @Test fun readSettingsSeparatesUnsupportedFromUnreadable() = runTest {
        val pad = FakePad { opcode, payload ->
            when (opcode) {
                KeyLinkerOp.HOST_STICK -> hex("0e08085555aaaa0008085555aaaa00")
                KeyLinkerOp.HOST_MOTOR -> hex("084c4c0000c0d40100")
                KeyLinkerOp.HOST_MENU -> if (payload[1].toInt() == 3) hex("091001880d840b825d82") else hex("0b1212130d8401880b825d82")
                KeyLinkerOp.HOST_CHANGEKEY -> if (payload[0].toInt() == 0) hex("10") + ByteArray(14).also { it[0] = 2 } else hex("0000")
                KeyLinkerOp.HOST_MACRO -> if (payload[1].toInt() == 3) hex("0c000088100014008800000c00") else hex("00")
                else -> null // triggers silent
            }
        }
        val gate = KeyLinkerGate().apply { assessment = KeyLinkerEvidence.assess(KeyLinkerIdentity("1013", "2009", "1.23", 2)) }
        val client = KeyLinkerClient(KeyLinkerSession(pad, backgroundScope, gate::authorize))
        val caps = KeyLinkerCapabilities.parse(KeyLinkerBody.unwrap(hex("0a030303000f010002c000")))
        val settings = client.readSettings(caps)
        assertEquals("default", settings.sticks.valueOrNull?.first()?.presetName)
        assertTrue(settings.triggers is Reading.Unreadable)
        assertEquals(10, settings.motor.valueOrNull?.shutdownMinutes)
        assertEquals(Reading.Unsupported, settings.turboRaw)
        assertEquals(Reading.Unsupported, settings.lighting)
        assertEquals(mapOf(1 to 2), settings.remaps.valueOrNull?.targets)
        assertTrue(settings.remaps.valueOrNull!!.sourcesFromPad)
        val macros = settings.macros.valueOrNull!!
        assertNull(macros["M1"]); assertEquals("A:100/60", macros["M4"]?.describe())
        assertTrue(pad.sent.none { it.opcode in 0x31..0x37 || it.opcode == KeyLinkerOp.RECOVER })
    }

    @Test fun krlyAndKeyLinkerStayIsolated() {
        assertNotEquals(DeviceProfile.X10.gatt.service, DeviceProfile.X20.gatt.service)
        assertEquals(ConfigurationProtocol.KRLY, DeviceProfile.X10.configurationProtocol)
        assertEquals(ConfigurationProtocol.KEYLINKER, DeviceProfile.X20.configurationProtocol)
        assertEquals(ConfigurationProtocol.KEYLINKER, DeviceProfile.X20_PRO.configurationProtocol)
        assertEquals(KeyLinkerCodec.SERVICE, DeviceProfile.X20_PRO.gatt.service)
    }
}
