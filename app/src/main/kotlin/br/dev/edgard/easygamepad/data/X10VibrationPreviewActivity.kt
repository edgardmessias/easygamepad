// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.data

import br.dev.edgard.easygamepad.protocol.X10Motor

/** UI-only state for live motor preview while a slider is held on the X10 link. */
data class X10VibrationPreviewActivity(val active: X10Motor? = null) {
    fun busy(motor: X10Motor) = active == motor
}
