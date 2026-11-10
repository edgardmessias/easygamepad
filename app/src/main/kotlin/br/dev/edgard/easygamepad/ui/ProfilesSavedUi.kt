// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp

@Composable
fun ProfileEditorBottomBar(
    hint: String?,
    showHint: Boolean,
    editingSavedCopy: Boolean,
    canSave: Boolean,
    canApply: Boolean,
    canSaveNewCopy: Boolean,
    onUpdateSaved: () -> Unit,
    onSaveAsNew: () -> Unit,
    onSaveNew: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
    onMeasuredHeightPx: (Int) -> Unit = {},
) {
    Surface(
        modifier
            .fillMaxWidth()
            .onSizeChanged { onMeasuredHeightPx(it.height) },
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = ScreenLayout.bottomBarElevation,
    ) {
        Column(Modifier.padding(horizontal = ScreenLayout.horizontalPadding, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showHint && hint != null) {
                Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (editingSavedCopy) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onUpdateSaved, enabled = canSave, modifier = Modifier.weight(1f)) {
                        Text(tr("Update saved", "Atualizar salvo"))
                    }
                    OutlinedButton(onClick = onSaveAsNew, enabled = canSave && canSaveNewCopy, modifier = Modifier.weight(1f)) {
                        Text(tr("Save as new", "Salvar como novo"))
                    }
                }
            } else {
                OutlinedButton(onClick = onSaveNew, enabled = canSave && canSaveNewCopy, modifier = Modifier.fillMaxWidth()) {
                    Text(tr("Save profile", "Salvar perfil"))
                }
            }
            Button(onClick = onApply, enabled = canApply, modifier = Modifier.fillMaxWidth()) {
                Text(tr("Apply to X10", "Aplicar ao X10"))
            }
        }
    }
}
