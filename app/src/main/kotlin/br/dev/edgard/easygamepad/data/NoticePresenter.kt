// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.ble.LinkPhase

/** Filters duplicate snackbars and drops stale success toasts after a disconnect. */
class NoticePresenter(
    private val dedupeWindowMs: Long = 2500,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private var suppressSuccess = false
    private var lastKey: String? = null
    private var lastShownAt = 0L

    fun onLinkPhase(phase: LinkPhase) {
        suppressSuccess = phase != LinkPhase.READY
    }

    fun present(notice: Notice, portuguese: Boolean): SnackbarMessage? {
        if (suppressSuccess && notice.kind == NoticeKind.SUCCESS && !notice.survivesDisconnect) return null
        val text = if (portuguese) notice.portuguese else notice.english
        val key = "${notice.kind}:$text"
        val now = clock()
        if (key == lastKey && now - lastShownAt < dedupeWindowMs) return null
        lastKey = key
        lastShownAt = now
        return SnackbarMessage(
            text = text,
            isError = notice.kind == NoticeKind.ERROR,
            longLived = notice.kind != NoticeKind.SUCCESS,
        )
    }
}

data class SnackbarMessage(val text: String, val isError: Boolean, val longLived: Boolean)
