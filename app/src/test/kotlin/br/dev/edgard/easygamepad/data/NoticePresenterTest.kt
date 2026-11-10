// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.ble.LinkPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoticePresenterTest {
    @Test
    fun suppressesConnectionSuccessToastsWhileDisconnected() {
        val presenter = NoticePresenter()
        presenter.onLinkPhase(LinkPhase.DISCONNECTED)
        assertNull(presenter.present(Notice("Saved", "Salvo"), false))
        assertNotNull(presenter.present(Notice("Failed", "Falhou", true), false))
    }

    @Test
    fun showsLocalSuccessWhileDisconnected() {
        val presenter = NoticePresenter()
        presenter.onLinkPhase(LinkPhase.DISCONNECTED)
        assertNotNull(presenter.present(Notice("Profile saved on this phone", "Perfil salvo", survivesDisconnect = true), false))
    }

    @Test
    fun dedupesIdenticalMessagesWithinWindow() {
        var now = 0L
        val presenter = NoticePresenter(clock = { now })
        val notice = Notice("Confirmed by controller", "Confirmado pelo controle")
        assertNotNull(presenter.present(notice, false))
        now = 1000
        assertNull(presenter.present(notice, false))
        now = 3000
        assertNotNull(presenter.present(notice, false))
    }

    @Test
    fun outcomesUseLongLivedPresentation() {
        val presenter = NoticePresenter()
        val message = presenter.present(
            Notice("Outcome", "Resultado", kind = NoticeKind.OUTCOME),
            portuguese = false,
        )!!
        assertFalse(message.isError)
        assertTrue(message.longLived)
    }
}
