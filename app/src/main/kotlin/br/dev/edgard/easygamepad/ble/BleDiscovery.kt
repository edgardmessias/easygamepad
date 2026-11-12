// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ble

import android.bluetooth.le.ScanRecord
import java.util.Locale
import java.util.UUID

/**
 * BLE discovery heuristics informed by reverse-engineered official behavior (see
 * [docs/ble-official-scan-handoff.md](../../../docs/ble-official-scan-handoff.md)).
 * Advertised names are used for sorting and highlighting only — never for auto-connect
 * or protocol selection.
 */
object BleDiscovery {
    /** GATT configuration service on the X10 (QMacro / KRLY); not typically advertised in scan data. */
    val KRLY_GATT_SERVICE: UUID = UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb")

    private val CONFIGURATION_NAME_HINTS = listOf(
        "qmacro", "keylinker", "x10", "x20", "easysmx", "smx",
    )

    /** True when the visible name plausibly refers to a configuration BLE link (not gameplay HID). */
    fun configurationChannelName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val lower = name.trim().lowercase(Locale.US)
        return CONFIGURATION_NAME_HINTS.any { lower.contains(it) }
    }

    /**
     * Higher sorts earlier in the connection sheet. QMacro is prioritized because the X10
     * configuration link advertises under that name (official patch notes & field logcat).
     */
    fun configurationNamePriority(name: String?): Int {
        if (name.isNullOrBlank()) return 0
        val lower = name.trim().lowercase(Locale.US)
        return when {
            lower == "qmacro" || lower.contains("keylinker") -> 3
            lower.contains("x10") || lower.contains("easysmx") -> 2
            lower.contains("x20") || lower.contains("smx") -> 1
            else -> 0
        }
    }

    /**
     * Optional advertisement hint only. Many X10 units do not include service UUIDs in ADV packets;
     * do not use this to filter scan callbacks.
     */
    fun advertisesKrlyService(record: ScanRecord?): Boolean {
        val uuids = record?.serviceUuids ?: return false
        return uuids.any { it.uuid == KRLY_GATT_SERVICE }
    }
}
