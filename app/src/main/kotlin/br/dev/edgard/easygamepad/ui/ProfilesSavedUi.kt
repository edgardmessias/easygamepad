// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.dev.edgard.easygamepad.data.ConsumerCopy

@Composable
fun ProfileEditorBottomBar(
    editingSavedCopy: Boolean,
    canSave: Boolean,
    canApply: Boolean,
    saveDisabledReason: String?,
    applyDisabledReason: String?,
    onSave: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shadowElevation = ScreenLayout.bottomBarElevation) {
        Row(Modifier.padding(horizontal = ScreenLayout.horizontalPadding, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onSave, enabled = canSave, modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                saveDisabledReason?.let { stateDescription = it }
            }) {
                Text(if (editingSavedCopy) ConsumerCopy.updateSavedCopyAction(LocalPortuguese.current) else ConsumerCopy.saveProfileAction(LocalPortuguese.current),
                    maxLines = 2)
            }
            Button(onClick = onApply, enabled = canApply, modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                applyDisabledReason?.let { stateDescription = it }
            }) {
                Text(ConsumerCopy.applyToX10Action(LocalPortuguese.current), maxLines = 2)
            }
        }
    }
}
