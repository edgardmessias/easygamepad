// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.protocol.X10ApplyResult
import br.dev.edgard.easygamepad.protocol.X10SettingField

fun x10ApplyNotice(result: X10ApplyResult): Notice = when (result) {
    is X10ApplyResult.Matched -> Notice(
        "Buttons, sticks, triggers, vibration and turbo match the read-back. Macro sequences and persistence after power-off were not checked.",
        "Botões, analógicos, gatilhos, vibração e turbo conferem na releitura. As sequências de macro e a persistência após desligar não foram verificadas.",
        kind = NoticeKind.OUTCOME,
    )
    is X10ApplyResult.Mismatch -> {
        val names = result.fields.map { field -> when (field) {
            X10SettingField.BUTTONS -> "buttons" to "botões"
            X10SettingField.STICKS -> "sticks" to "analógicos"
            X10SettingField.TRIGGERS -> "triggers" to "gatilhos"
            X10SettingField.VIBRATION -> "vibration" to "vibração"
            X10SettingField.TURBO -> "turbo" to "turbo"
        } }
        Notice("Read-back differs from the profile: ${names.joinToString { it.first }}. Read the controller settings before trying again.",
            "A releitura difere do perfil: ${names.joinToString { it.second }}. Leia os ajustes do controle antes de tentar novamente.", true)
    }
    is X10ApplyResult.Unconfirmed -> Notice(
        "Commands were accepted, but settings could not be read back. The result is unconfirmed. Reconnect and read the controller settings.",
        "Os comandos foram aceitos, mas não foi possível reler os ajustes. O resultado não foi confirmado. Reconecte e leia os ajustes do controle.", true)
}
