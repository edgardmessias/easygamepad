// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ble

/**
 * Scan-derived device identity cache used by [BleClient.resolveConnectDevice].
 * Only real LE scan callbacks populate this cache — never UI seed lists.
 */
object BleConnectCache {
    /** How long a [android.bluetooth.le.ScanResult]-sourced [android.bluetooth.BluetoothDevice] stays preferred. */
    const val FRESH_SCAN_OBSERVATION_MS = 30_000L

    /** Address-filtered discovery when the cache expired (preserves address type for GATT connect). */
    const val ADDRESS_DISCOVERY_TIMEOUT_MS = 6_000L

    fun isFreshObservation(observedAtElapsedMs: Long, nowElapsedMs: Long, windowMs: Long = FRESH_SCAN_OBSERVATION_MS): Boolean =
        nowElapsedMs - observedAtElapsedMs < windowMs
}

/** Merge a UI seed or scan row without dropping bonded state or candidate highlighting. */
internal fun mergeFoundDevice(existing: FoundDevice?, incoming: FoundDevice): FoundDevice = when {
    existing == null -> incoming
    incoming.candidate && !existing.candidate -> incoming.copy(bonded = existing.bonded || incoming.bonded)
    incoming.name != incoming.address && existing.name == existing.address ->
        incoming.copy(bonded = existing.bonded || incoming.bonded)
    else -> existing.copy(
        bonded = existing.bonded || incoming.bonded,
        rssi = incoming.rssi ?: existing.rssi,
        candidate = existing.candidate || incoming.candidate,
    )
}
