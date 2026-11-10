// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test fun autoDetectsPortugueseAndFallsBackToEnglish() {
        assertEquals("pt-BR", AppLanguage.resolve("auto", listOf("pt-PT")))
        assertEquals("en", AppLanguage.resolve("auto", listOf("ja-JP")))
        assertEquals("en", AppLanguage.resolve("auto", emptyList()))
        assertEquals("pt-BR", AppLanguage.resolve("auto", listOf("es", "pt-BR")))
        assertEquals("en", AppLanguage.resolve("auto", listOf("en-US", "pt-BR")))
    }
    @Test fun manualLanguageTakesPrecedenceOverDevice() {
        assertEquals("en", AppLanguage.resolve("en", listOf("pt-BR")))
        assertEquals("pt-BR", AppLanguage.resolve("pt-BR", listOf("en")))
    }

    @Test fun unsupportedLocaleFallsBackToEnglishWithoutMigratingPreference() {
        assertEquals("en", AppLanguage.resolve("auto", listOf("de-DE", "fr-FR")))
        assertEquals("en", AppLanguage.resolve("auto", listOf("zh-CN")))
    }
}
