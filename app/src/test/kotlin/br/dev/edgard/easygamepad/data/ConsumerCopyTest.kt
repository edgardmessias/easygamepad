// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsumerCopyTest {
    @Test
    fun profileQuotaFormatsCountsInBothLanguages() {
        assertEquals("Saved profiles · 3/30", ConsumerCopy.savedProfileQuota(3, portuguese = false))
        assertEquals("Perfis salvos · 12/30", ConsumerCopy.savedProfileQuota(12, portuguese = true))
    }

    @Test
    fun storageWarningsKeepPluralNumbers() {
        val en = ConsumerCopy.storageSkippedWarning(2, unreadable = true, portuguese = false)
        val pt = ConsumerCopy.storageSkippedWarning(2, unreadable = true, portuguese = true)
        assertTrue(en.contains("2 entries"))
        assertTrue(pt.contains("2 entradas"))
    }

    @Test
    fun gameplayAndConfigurationLabelsStayPairedInChooserSummary() {
        val en = ConsumerCopy.chooserSummary(gameplayConnected = true, configurationConnected = false, portuguese = false)
        assertEquals("Gameplay: connected, App configuration: offline", en)
        val pt = ConsumerCopy.chooserSummary(gameplayConnected = false, configurationConnected = true, portuguese = true)
        assertEquals("Jogo: offline, Configuração do app: conectada", pt)
    }

    @Test
    fun applySaveAndReadActionsHavePortugueseCounterparts() {
        assertEquals("Apply to X10", ConsumerCopy.applyToX10Action(false))
        assertEquals("Aplicar ao X10", ConsumerCopy.applyToX10Action(true))
        assertEquals("Read from X10", ConsumerCopy.readFromX10Action(false))
        assertEquals("Ler do X10", ConsumerCopy.readFromX10Action(true))
        assertTrue(ConsumerCopy.saveProfileAction(true).isNotBlank())
        assertTrue(ConsumerCopy.updateSavedCopyAction(false).isNotBlank())
    }
}
