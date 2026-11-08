// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Amjad AAYD
package br.dev.edgard.easygamepad.protocol.keylinker

import br.dev.edgard.easygamepad.protocol.DeviceProfile
import org.junit.Assert.*
import org.junit.Test

class KeyLinkerEvidenceTest {
    private val caps = KeyLinkerCapabilities.parse(KeyLinkerBody(10, byteArrayOf(3, 3, 3, 0, 0x0F, 1, 0, 2, 0xC0.toByte(), 0)))

    @Test fun classificationFollowsTheRecordedIdentities() {
        assertEquals(KeyLinkerClassification.VERIFIED, KeyLinkerEvidence.assess(KeyLinkerIdentity("1013", "2009", "1.23", 2)).classification)
        assertEquals(KeyLinkerClassification.VERIFIED, KeyLinkerEvidence.assess(KeyLinkerIdentity("1020", "2006", "1.44", 6)).classification)
        val newFirmware = KeyLinkerEvidence.assess(KeyLinkerIdentity("1013", "2009", "1.30", 2))
        assertEquals(KeyLinkerClassification.KNOWN_MODEL_UNVALIDATED_FIRMWARE, newFirmware.classification)
        assertTrue(newFirmware.mayRead)
        val otherMode = KeyLinkerEvidence.assess(KeyLinkerIdentity("1020", "2006", "1.44", 3))
        assertEquals(KeyLinkerClassification.KNOWN_MODEL_UNVALIDATED_MODE, otherMode.classification)
        assertFalse(otherMode.mayRead)
        assertFalse(KeyLinkerEvidence.assess(KeyLinkerIdentity("1013", "2009", "1.23")).mayRead) // short record: mode unknown
        val unknown = KeyLinkerEvidence.assess(KeyLinkerIdentity("1099", "2001", "1.00", 2))
        assertEquals(KeyLinkerClassification.UNKNOWN_MODEL, unknown.classification)
        assertNull(unknown.known); assertFalse(unknown.mayRead)
    }

    @Test fun writesFollowX20ctlHardwareProof() {
        val x20 = KeyLinkerEvidence.assess(KeyLinkerIdentity("1013", "2009", "1.23", 2))
        val newFirmware = KeyLinkerEvidence.assess(KeyLinkerIdentity("1013", "2009", "1.30", 2))
        val proven = listOf(KeyLinkerFeature.REMAPS, KeyLinkerFeature.STICK_CURVES, KeyLinkerFeature.TRIGGER_CURVES,
            KeyLinkerFeature.VIBRATION, KeyLinkerFeature.IDLE_TIMEOUT, KeyLinkerFeature.MACROS)
        for (feature in proven) {
            assertEquals("$feature", KeyLinkerWriteDecision.Allowed, x20.writeDecision(feature, caps))
            assertEquals("$feature", KeyLinkerWriteDecision.Allowed, newFirmware.writeDecision(feature, caps))
        }
        fun reason(f: KeyLinkerFeature) = (x20.writeDecision(f, caps) as KeyLinkerWriteDecision.Blocked).reason
        assertEquals(KeyLinkerWriteDecision.Reason.DISRUPTIVE, reason(KeyLinkerFeature.FACTORY_RESET))
        assertEquals(KeyLinkerWriteDecision.Reason.DISRUPTIVE, reason(KeyLinkerFeature.CALIBRATE))
        assertEquals(KeyLinkerWriteDecision.Reason.NOT_EXPOSED, reason(KeyLinkerFeature.LIGHTING))
        val unknown = KeyLinkerEvidence.assess(KeyLinkerIdentity("1099", "2001", "1.00", 2))
        assertEquals(KeyLinkerWriteDecision.Reason.UNKNOWN_MODEL, (unknown.writeDecision(KeyLinkerFeature.REMAPS, caps) as KeyLinkerWriteDecision.Blocked).reason)
        val otherMode = KeyLinkerEvidence.assess(KeyLinkerIdentity("1020", "2006", "1.44", 3))
        assertEquals(KeyLinkerWriteDecision.Reason.UNVALIDATED_MODE, (otherMode.writeDecision(KeyLinkerFeature.REMAPS, caps) as KeyLinkerWriteDecision.Blocked).reason)
    }

    @Test fun requestsAreClassifiedLikeX20ctl() {
        assertEquals(KeyLinkerEvidence.RequestKind.IDENTIFY, KeyLinkerEvidence.classify(0x91, byteArrayOf()).first)
        assertEquals(KeyLinkerEvidence.RequestKind.READ to KeyLinkerFeature.BATTERY, KeyLinkerEvidence.classify(KeyLinkerOp.SET_MODE, KeyLinkerEvidence.POWER_PAYLOAD))
        assertEquals(KeyLinkerEvidence.RequestKind.WRITE to KeyLinkerFeature.CALIBRATE, KeyLinkerEvidence.classify(KeyLinkerOp.SET_MODE, byteArrayOf(1)))
        assertEquals(KeyLinkerEvidence.RequestKind.WRITE to KeyLinkerFeature.FACTORY_RESET, KeyLinkerEvidence.classify(KeyLinkerOp.RECOVER, byteArrayOf()))
        assertEquals(KeyLinkerEvidence.RequestKind.READ, KeyLinkerEvidence.classify(KeyLinkerOp.HOST_MACRO, byteArrayOf(0, 3)).first)
        assertEquals(KeyLinkerEvidence.RequestKind.WRITE to null, KeyLinkerEvidence.classify(0x7E, byteArrayOf()))
    }

    @Test fun gateAllowsProvenWritesAndRefusesTheRest() {
        val gate = KeyLinkerGate()
        gate.authorize(KeyLinkerOp.READ_VID_PID_VERSION, byteArrayOf())
        assertThrows(KeyLinkerNotAuthorized::class.java) { gate.authorize(KeyLinkerOp.HOST_MOTOR, byteArrayOf(0)) }
        gate.assessment = KeyLinkerEvidence.assess(KeyLinkerIdentity("1020", "2006", "1.44", 6)); gate.capabilities = caps
        gate.authorize(KeyLinkerOp.HOST_MOTOR, byteArrayOf(0))
        assertThrows(KeyLinkerNotAuthorized::class.java) { KeyLinkerGate().authorize(KeyLinkerOp.WRITE_MOTOR, byteArrayOf(8)) }
        gate.authorize(KeyLinkerOp.WRITE_MOTOR, byteArrayOf(8))
        assertThrows(KeyLinkerNotAuthorized::class.java) { gate.authorize(KeyLinkerOp.SET_MODE, byteArrayOf(1)) } // calibrate
        assertThrows(KeyLinkerNotAuthorized::class.java) { gate.authorize(KeyLinkerOp.RECOVER, byteArrayOf()) }
        assertThrows(KeyLinkerNotAuthorized::class.java) { gate.authorize(0x7E, byteArrayOf()) }
    }

    @Test fun modelsLineUpWithDeviceProfiles() {
        assertEquals(DeviceProfile.X20.id, KeyLinkerEvidence.X20.modelId)
        assertEquals(DeviceProfile.X20_PRO.id, KeyLinkerEvidence.X20_PRO.modelId)
        assertEquals(KeyLinkerEvidence.X20.macroSlots, DeviceProfile.X20.rearButtons.map { it.label })
        assertEquals(KeyLinkerEvidence.X20_PRO.macroSlots, DeviceProfile.X20_PRO.rearButtons.map { it.label })
        assertSame(DeviceProfile.X10, DeviceProfile.find("easysmx-x10"))
        assertSame(DeviceProfile.X20_PRO, DeviceProfile.find("easysmx-x20-pro"))
        assertNull(DeviceProfile.find("easysmx-x30"))
    }
}
