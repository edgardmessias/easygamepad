// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

enum class NoticeKind { SUCCESS, ERROR, OUTCOME }

data class Notice(
    val english: String,
    val portuguese: String,
    val kind: NoticeKind = NoticeKind.SUCCESS,
    /** Local phone operations stay visible while the configuration link is offline. */
    val survivesDisconnect: Boolean = false,
) {
    constructor(english: String, portuguese: String, error: Boolean, survivesDisconnect: Boolean = false) : this(
        english,
        portuguese,
        if (error) NoticeKind.ERROR else NoticeKind.SUCCESS,
        survivesDisconnect,
    )

    val error get() = kind == NoticeKind.ERROR
}
