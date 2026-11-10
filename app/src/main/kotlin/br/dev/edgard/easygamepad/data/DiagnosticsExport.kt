// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.ble.BleLog

object DiagnosticsSanitizer {
    private val mac = Regex("(?i)(?:[0-9a-f]{2}:){5}[0-9a-f]{2}")
    private val hexPayload = Regex("(?i)\\b(?:[0-9a-f]{2}\\s+){3,}[0-9a-f]{2}\\b")

    fun sanitizeLine(message: String, includeRawPayloads: Boolean): String {
        var line = message.replace(mac, "<controller>")
        if (!includeRawPayloads) line = hexPayload.replace(line, "<payload>")
        return line
    }

    fun buildExport(
        appVersion: String,
        androidVersion: String,
        logs: List<BleLog>,
        includeRawPayloads: Boolean,
        maxEntries: Int = 200,
    ): String {
        val header = buildString {
            appendLine("EasyGamepad $appVersion")
            appendLine("Android $androidVersion")
            appendLine("Raw payloads: ${if (includeRawPayloads) "yes" else "no"}")
        }
        val body = logs.takeLast(maxEntries).joinToString("\n") { entry ->
            val text = sanitizeLine(entry.message, includeRawPayloads)
            "${entry.time} ${entry.direction} $text"
        }
        return header + body
    }
}
