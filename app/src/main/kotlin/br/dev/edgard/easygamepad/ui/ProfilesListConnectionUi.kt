// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.dev.edgard.easygamepad.ble.LinkPhase

/** Saved-profiles landing: surface link state without treating READY+busy as offline. */
fun profilesListConfigurationRowVisible(
    supportsConfiguration: Boolean,
    editorUi: ProfilesEditorUiState,
    linkError: String?,
): Boolean {
    if (!supportsConfiguration) return false
    if (editorUi.configurationReady) return false
    if (editorUi.calibration.stopRequired || editorUi.busy) return true
    if (profileEditorConfigurationRowVisible(editorUi.linkPhase, linkError)) return true
    return editorUi.linkPhase == LinkPhase.READY && !editorUi.configurationLinkSelected
}

fun profilesListConfigurationChannelStatus(
    editorUi: ProfilesEditorUiState,
    linkError: String?,
    portuguese: Boolean,
): String? {
    if (editorUi.calibration.stopRequired) {
        return if (portuguese) "Finalize a calibração no controle." else "Finish calibration on the controller."
    }
    if (editorUi.busy) {
        return if (portuguese) "Aguarde a operação do controle." else "Wait for the controller operation."
    }
    profileEditorConfigurationChannelStatus(editorUi.linkPhase, linkError, portuguese)?.let { return it }
    if (editorUi.linkPhase == LinkPhase.READY && !editorUi.configurationLinkSelected) {
        return if (portuguese) "Conecte o controle selecionado no app." else "Connect the selected controller in the app."
    }
    return null
}

fun profilesListConnectEnabled(editorUi: ProfilesEditorUiState, switching: Boolean): Boolean =
    !editorUi.calibration.stopRequired && profileEditorConnectEnabled(editorUi.linkPhase, switching, editorUi.busy)

@Composable
fun ProfilesListConfigurationChannelRow(
    status: String,
    connectEnabled: Boolean,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connectLabel = tr("Connect", "Conectar")
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            status,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (connectEnabled) {
            TextButton(
                onClick = onConnect,
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = connectLabel },
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) { Text(connectLabel) }
        }
    }
}
