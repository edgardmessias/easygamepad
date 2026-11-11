// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.dev.edgard.easygamepad.data.ConsumerCopy
import br.dev.edgard.easygamepad.data.MAX_SAVED_PROFILES
import br.dev.edgard.easygamepad.data.SavedProfile

@Composable
fun SavedProfilesScreen(
    profiles: List<SavedProfile>,
    editingId: String?,
    onBack: () -> Unit,
    onLoad: (SavedProfile) -> Unit,
    onDelete: (SavedProfile) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        val backLabel = tr("Back to editor", "Voltar ao editor")
        Row(
            Modifier.fillMaxWidth().padding(horizontal = ScreenLayout.horizontalPadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = backLabel }) {
                HubIcon(Glyph.ARROW, Modifier.size(20.dp).rotate(180f), MaterialTheme.colorScheme.onSurface)
            }
            Column(Modifier.weight(1f)) {
                Text(tr("Saved profiles", "Perfis salvos"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text(tr("${profiles.size} of $MAX_SAVED_PROFILES saved", "${profiles.size} de $MAX_SAVED_PROFILES salvos"), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (profiles.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(ScreenLayout.horizontalPadding), contentAlignment = Alignment.Center) {
                InfoBanner(tr("No saved copies yet. Save your draft to keep it on this phone.",
                    "Nenhuma cópia salva ainda. Salve seu rascunho para guardá-lo neste celular."))
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = ScreenLayout.horizontalPadding, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(profiles, key = { it.id }) { profile ->
                    val selected = profile.id == editingId
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(profile.config.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (selected) Text(tr("Editing this copy", "Editando esta cópia"), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            val loadLabel = tr("Load profile: ", "Carregar perfil: ") + profile.config.name
                            TextButton(onClick = { onLoad(profile) }, modifier = Modifier.heightIn(min = 48.dp).semantics {
                                contentDescription = loadLabel
                            }) { Text(tr("Load", "Carregar")) }
                            val deleteLabel = tr("Delete profile", "Excluir perfil") + ": " + profile.config.name
                            IconButton(onClick = { onDelete(profile) }, modifier = Modifier.semantics { contentDescription = deleteLabel }) {
                                HubIcon(Glyph.TRASH, Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

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
