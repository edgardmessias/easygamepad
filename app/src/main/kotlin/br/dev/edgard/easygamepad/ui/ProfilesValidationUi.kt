// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.dev.edgard.easygamepad.protocol.ProfileIssueKind
import br.dev.edgard.easygamepad.protocol.ProfileValidationIssue

@Composable fun ProfileValidationIssue.localizedMessage(): String = when (kind) {
    ProfileIssueKind.BLANK_NAME -> tr("Enter a profile name.", "Informe um nome para o perfil.")
    ProfileIssueKind.NAME_TOO_LONG -> tr("Profile name must be 40 characters or fewer.", "O nome do perfil deve ter até 40 caracteres.")
    ProfileIssueKind.STICK_RANGE -> tr("Stick response range is invalid: minimum must stay below maximum (1–100%).", "Faixa de resposta do analógico inválida: o mínimo deve ficar abaixo do máximo (1–100%).")
    ProfileIssueKind.TRIGGER_RANGE -> tr("Trigger range is invalid: minimum must stay below maximum.", "Faixa do gatilho inválida: o mínimo deve ficar abaixo do máximo.")
    ProfileIssueKind.VIBRATION_RANGE -> tr("Motor intensity must be between 0% and 100%.", "A intensidade do motor deve ficar entre 0% e 100%.")
    ProfileIssueKind.DUPLICATE_MACRO_TRIGGER -> tr("Each rear macro must use a different paddle (M1–M4).", "Cada macro traseira precisa de um botão diferente (M1–M4).")
    ProfileIssueKind.MACRO_NO_STEPS -> tr("Macro ${index?.plus(1) ?: ""} needs at least one step before saving or applying.", "A macro ${index?.plus(1) ?: ""} precisa de pelo menos uma etapa antes de salvar ou aplicar.")
    ProfileIssueKind.MACRO_TOO_MANY_STEPS -> tr("Macro ${index?.plus(1) ?: ""} exceeds the 32-step limit.", "A macro ${index?.plus(1) ?: ""} passa do limite de 32 etapas.")
    ProfileIssueKind.MACRO_STEP -> tr("Check macro step timings (hold 1–65535 ms, wait 0–65535 ms) and button keys.", "Confira os tempos da macro (pressionar 1–65535 ms, esperar 0–65535 ms) e os botões.")
    ProfileIssueKind.DUPLICATE_TURBO_KEY -> tr("Each turbo rule must target a different button.", "Cada regra de turbo deve usar um botão diferente.")
    ProfileIssueKind.TURBO_RATE -> tr("Turbo rate must be between 1 and 20 presses per second.", "A taxa de turbo deve ficar entre 1 e 20 pressionamentos por segundo.")
    ProfileIssueKind.INVALID_MAPPING -> tr("A button assignment points to an unsupported output.", "Uma atribuição aponta para uma função não suportada.")
    ProfileIssueKind.DISABLED_REAR_CONFLICT ->
        tr("A disabled paddle cannot also have a mapping or macro. Disable the paddle again or remove the macro.", "Um paddle desativado não pode ter mapeamento ou macro. Desative de novo o paddle ou remova a macro.")
}

@Composable fun ProfileValidationBanner(issues: List<ProfileValidationIssue>, modifier: Modifier = Modifier) {
    if (issues.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("Fix before saving or applying", "Corrija antes de salvar ou aplicar"), style = MaterialTheme.typography.titleSmall)
        issues.take(3).forEach { issue -> InfoBanner(issue.localizedMessage(), error = true) }
        if (issues.size > 3) Description(tr("+${issues.size - 3} more issue(s)", "+${issues.size - 3} outro(s) problema(s)"))
    }
}

@Composable fun profileSaveBlockedReason(issues: List<ProfileValidationIssue>): String? =
    issues.firstOrNull()?.localizedMessage()
