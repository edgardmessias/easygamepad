// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

import org.json.JSONArray

/** A skipped JSON array element preserved verbatim for later rewrite (any JSON type). */
data class PreservedJsonSlot(val value: Any) {
    companion object {
        fun capture(array: JSONArray, index: Int): PreservedJsonSlot = PreservedJsonSlot(array.get(index))
    }
}
