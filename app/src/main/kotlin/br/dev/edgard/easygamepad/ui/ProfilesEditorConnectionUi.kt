// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import br.dev.edgard.easygamepad.ble.LinkPhase
import br.dev.edgard.easygamepad.data.ConsumerCopy

/** Profile editor channel row labels and connect guards (no BLE changes). */
fun profileEditorConfigurationChannelStatus(phase: LinkPhase, linkError: String?, portuguese: Boolean): String? {
    if (phase == LinkPhase.READY) return null
    if (linkError != null && phase == LinkPhase.DISCONNECTED) {
        return if (portuguese) "Configuração do app: erro de conexão" else "App configuration: connection error"
    }
    return when (phase) {
        LinkPhase.DISCONNECTED -> ConsumerCopy.configurationStatus(false, portuguese)
        LinkPhase.CONNECTING ->
            if (portuguese) "Configuração do app: conectando…" else "App configuration: connecting…"
        LinkPhase.DISCOVERING ->
            if (portuguese) "Configuração do app: descobrindo serviços…" else "App configuration: discovering services…"
        LinkPhase.SUBSCRIBING ->
            if (portuguese) "Configuração do app: preparando canal…" else "App configuration: preparing channel…"
        LinkPhase.READY -> ConsumerCopy.configurationStatus(true, portuguese)
    }
}

fun profileEditorConnectEnabled(phase: LinkPhase, switching: Boolean, operationBusy: Boolean): Boolean =
    phase == LinkPhase.DISCONNECTED && !switching && !operationBusy

fun profileEditorConfigurationRowVisible(phase: LinkPhase, linkError: String?): Boolean =
    phase != LinkPhase.READY || linkError != null
