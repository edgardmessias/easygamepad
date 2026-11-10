// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import br.dev.edgard.easygamepad.ble.LinkPhase
import br.dev.edgard.easygamepad.protocol.CalibrationState

/** Screen-reader copy kept off composables so it can be unit-tested without a device. */
object AccessibilitySemantics {
    fun statusState(active: Boolean, portuguese: Boolean): String = when {
        active && portuguese -> "Ativo"
        active -> "Active"
        portuguese -> "Inativo"
        else -> "Inactive"
    }

    fun vibrationSliderLabel(label: String, value: Int, suffix: String, previewing: Boolean, portuguese: Boolean): String {
        val base = "$label, $value$suffix"
        if (!previewing) return base
        return base + if (portuguese) ", pré-visualização de vibração ativa" else ", vibration preview active"
    }

    fun profilesReadDisabledReason(state: ProfilesEditorUiState, portuguese: Boolean): String? = when {
        state.canReadFromDevice -> null
        state.calibration.stopRequired -> if (portuguese) "Finalize a calibração antes de ler." else "Finish calibration before reading."
        state.busy -> if (portuguese) "Aguarde a operação atual do controle." else "Wait for the current controller operation."
        state.linkPhase != LinkPhase.READY -> if (portuguese) "Conecte o app ao controle primeiro." else "Connect the app to the controller first."
        else -> if (portuguese) "Leitura indisponível." else "Reading unavailable."
    }

    fun profilesApplyDisabledReason(state: ProfilesEditorUiState, portuguese: Boolean): String? = when {
        state.canApplyToDevice -> null
        !state.draftValid -> if (portuguese) "Corrija o perfil antes de aplicar ao controle." else "Fix the profile before applying to the controller."
        state.calibration.stopRequired -> if (portuguese) "Finalize a calibração antes de aplicar." else "Finish calibration before applying."
        state.busy -> if (portuguese) "Aguarde a operação atual do controle." else "Wait for the current controller operation."
        state.linkPhase != LinkPhase.READY -> if (portuguese) "Conecte o app ao controle primeiro." else "Connect the app to the controller first."
        else -> if (portuguese) "Ação indisponível." else "Action unavailable."
    }

    fun profilesSaveDisabledReason(state: ProfilesEditorUiState, portuguese: Boolean): String? = when {
        state.canSaveProfile -> null
        else -> if (portuguese) "Corrija os valores inválidos do perfil." else "Fix invalid profile values."
    }
}
