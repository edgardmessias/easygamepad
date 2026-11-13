// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.dev.edgard.easygamepad.data.ConsumerCopy
import br.dev.edgard.easygamepad.data.MAX_SAVED_PROFILES
import br.dev.edgard.easygamepad.data.SavedProfile
import br.dev.edgard.easygamepad.protocol.ControllerConfig

enum class ProfilesListRowAction { EDIT, LOAD }

fun profilesListRowAction(editingId: String?, profileId: String): ProfilesListRowAction =
    if (editingId != null && editingId == profileId) ProfilesListRowAction.EDIT else ProfilesListRowAction.LOAD

/** Snapshot sent when applying from the saved list (never the live editor draft). */
fun savedProfileApplySnapshot(profile: SavedProfile): ControllerConfig = profile.config

@Composable
fun ProfilesListScreen(
    profiles: List<SavedProfile>,
    editingId: String?,
    draftName: String,
    draftStatusLabel: String,
    showDraftRow: Boolean,
    canReadFromDevice: Boolean,
    readDisabledReason: String?,
    configurationChannelStatus: String? = null,
    configurationConnectEnabled: Boolean = false,
    showConfigurationChannelRow: Boolean = false,
    onConnectConfiguration: () -> Unit = {},
    onEditDraft: () -> Unit,
    onEditSaved: (SavedProfile) -> Unit,
    onLoad: (SavedProfile) -> Unit,
    onApply: (SavedProfile) -> Unit,
    onDelete: (SavedProfile) -> Unit,
    onExport: (SavedProfile) -> Unit,
    onReadFromController: () -> Unit,
    onImport: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = ScreenLayout.horizontalPadding, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Saved profiles", "Perfis salvos"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(
                tr("${profiles.size} of $MAX_SAVED_PROFILES saved", "${profiles.size} de $MAX_SAVED_PROFILES salvos"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                tr("Choose a profile to edit.", "Escolha um perfil para editar."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showConfigurationChannelRow && configurationChannelStatus != null) {
                ProfilesListConfigurationChannelRow(
                    status = configurationChannelStatus,
                    connectEnabled = configurationConnectEnabled,
                    onConnect = onConnectConfiguration,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            if (showDraftRow) {
                item(key = "draft-row") {
                    ProfilesDraftRow(draftName, draftStatusLabel, onEditDraft)
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = ScreenLayout.horizontalPadding),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
            if (profiles.isEmpty() && !showDraftRow) {
                item {
                    Box(Modifier.fillMaxWidth().padding(ScreenLayout.horizontalPadding, 24.dp), contentAlignment = Alignment.Center) {
                        InfoBanner(
                            tr(
                                "No saved copies yet. Save your draft to keep it on this phone.",
                                "Nenhuma cópia salva ainda. Salve seu rascunho para guardá-lo neste celular.",
                            ),
                        )
                    }
                }
            } else {
                items(profiles, key = { it.id }) { profile ->
                    ProfilesSavedRow(
                        profile = profile,
                        editing = profile.id == editingId,
                        onPrimary = {
                            when (profilesListRowAction(editingId, profile.id)) {
                                ProfilesListRowAction.EDIT -> onEditSaved(profile)
                                ProfilesListRowAction.LOAD -> onLoad(profile)
                            }
                        },
                        onApply = { onApply(profile) },
                        onDelete = { onDelete(profile) },
                        onExport = { onExport(profile) },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = ScreenLayout.horizontalPadding),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
        val readLabel = ConsumerCopy.readFromX10Action(LocalPortuguese.current)
        val importLabel = tr("Import profile from a file", "Importar perfil de um arquivo")
        Column(Modifier.padding(horizontal = ScreenLayout.horizontalPadding, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = onReadFromController,
                enabled = canReadFromDevice,
                modifier = Modifier.heightIn(min = 48.dp).semantics {
                    readDisabledReason?.let { stateDescription = it }
                    contentDescription = readLabel
                },
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                HubIcon(Glyph.DOWNLOAD, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(readLabel)
            }
            TextButton(
                onClick = onImport,
                modifier = Modifier.heightIn(min = 48.dp).semantics {
                    contentDescription = importLabel
                },
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                HubIcon(Glyph.SHARE, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(importLabel)
            }
        }
    }
}

@Composable
private fun ProfilesDraftRow(name: String, statusLabel: String, onEdit: () -> Unit) {
    val editLabel = tr("Edit current draft: ", "Editar rascunho atual: ") + name
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            .clickable(onClickLabel = editLabel, role = Role.Button, onClick = onEdit)
            .semantics {
                contentDescription = editLabel
                stateDescription = statusLabel
            }
            .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(40.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(statusLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HubIcon(Glyph.EDIT, Modifier.padding(12.dp).size(20.dp), MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ProfilesSavedRow(
    profile: SavedProfile,
    editing: Boolean,
    onPrimary: () -> Unit,
    onApply: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    var menuVisible by remember { mutableStateOf(false) }
    val name = profile.config.name
    val editLabel = tr("Edit profile: ", "Editar perfil: ") + name
    val applyLabel = ConsumerCopy.applyToX10Action(LocalPortuguese.current) + ": " + name
    val deleteLabel = tr("Delete profile: ", "Excluir perfil: ") + name
    val editingLabel = tr("Editing this copy", "Editando esta cópia")
    val rowBg = if (editing) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.background
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(rowBg)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = editLabel,
                onClick = onPrimary,
                onLongClickLabel = deleteLabel,
                onLongClick = onDelete,
            )
            .semantics {
                contentDescription = editLabel
                if (editing) stateDescription = editingLabel
            }
            .padding(start = if (editing) 12.dp else ScreenLayout.horizontalPadding, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (editing) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Column(Modifier.weight(1f).padding(start = if (editing) 8.dp else 0.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (editing) {
                Text(
                    editingLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(
            onClick = onApply,
            modifier = Modifier.heightIn(min = 48.dp).semantics {
                contentDescription = applyLabel
            },
        ) {
            Text(tr("Apply", "Aplicar"))
        }
        IconButton(onClick = onDelete, modifier = Modifier.semantics { contentDescription = deleteLabel }) {
            HubIcon(Glyph.TRASH, Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            val moreLabel = tr("More actions for profile: ", "Mais ações do perfil: ") + name
            IconButton(
                onClick = { menuVisible = true },
                modifier = Modifier.semantics { contentDescription = moreLabel },
            ) { Text("⋮", style = MaterialTheme.typography.titleLarge) }
            DropdownMenu(expanded = menuVisible, onDismissRequest = { menuVisible = false }) {
                DropdownMenuItem(
                    text = { Text(tr("Export profile", "Exportar perfil")) },
                    onClick = { menuVisible = false; onExport() },
                )
            }
        }
    }
}
