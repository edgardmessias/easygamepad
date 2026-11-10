// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

/** Consumer-facing labels shared across screens so English and Portuguese stay aligned. */
object ConsumerCopy {
    fun savedProfileQuota(count: Int, max: Int = 30, portuguese: Boolean): String =
        if (portuguese) "Perfis salvos · $count/$max" else "Saved profiles · $count/$max"

    fun storageSkippedWarning(skipped: Int, unreadable: Boolean, portuguese: Boolean): String = when {
        unreadable && skipped > 0 -> if (portuguese) {
            "Parte dos dados salvos não pôde ser lida ($skipped entradas ignoradas). Exporte perfis válidos antes de corrigir o armazenamento."
        } else {
            "Some saved data could not be read ($skipped entries skipped). Export valid profiles before fixing storage."
        }
        unreadable -> if (portuguese) {
            "Um bloco de dados salvos não pôde ser lido. As entradas válidas continuam visíveis; os bytes corrompidos não foram regravados."
        } else {
            "A saved data block could not be read. Valid entries are still shown; broken bytes were not rewritten."
        }
        else -> if (portuguese) {
            "$skipped entradas salvas foram ignoradas por estarem inválidas. O restante está intacto."
        } else {
            "$skipped saved entries were skipped because they are invalid. The rest is intact."
        }
    }

    fun gameplayStatus(connected: Boolean, portuguese: Boolean): String = when {
        portuguese && connected -> "Jogo: conectado"
        portuguese -> "Jogo: offline"
        connected -> "Gameplay: connected"
        else -> "Gameplay: offline"
    }

    fun configurationStatus(connected: Boolean, portuguese: Boolean): String = when {
        portuguese && connected -> "Configuração do app: conectada"
        portuguese -> "Configuração do app: offline"
        connected -> "App configuration: connected"
        else -> "App configuration: offline"
    }

    fun chooserSummary(gameplayConnected: Boolean, configurationConnected: Boolean, portuguese: Boolean): String =
        "${gameplayStatus(gameplayConnected, portuguese)}, ${configurationStatus(configurationConnected, portuguese)}"

    fun buttonsTestedCount(count: Int, portuguese: Boolean): String =
        if (portuguese) "$count botões testados" else "$count buttons tested"

    fun readFromX10Action(portuguese: Boolean): String =
        if (portuguese) "Ler do X10" else "Read from X10"

    fun applyToX10Action(portuguese: Boolean): String =
        if (portuguese) "Aplicar ao X10" else "Apply to X10"

    fun saveProfileAction(portuguese: Boolean): String =
        if (portuguese) "Salvar perfil" else "Save profile"

    fun updateSavedCopyAction(portuguese: Boolean): String =
        if (portuguese) "Atualizar salvo" else "Update saved"
}
