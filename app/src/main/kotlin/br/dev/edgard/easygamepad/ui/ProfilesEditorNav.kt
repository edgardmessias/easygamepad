// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

const val PROFILE_EDITOR_SECTION_COUNT = 6

@Composable
fun ProfileEditorSectionNav(
    sectionLabels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = sectionLabels.take(PROFILE_EDITOR_SECTION_COUNT)
    if (labels.isEmpty()) return
    val current = selectedIndex.coerceIn(labels.indices)
    var menuVisible by remember { mutableStateOf(false) }
    val previousLabel = tr("Previous section", "Seção anterior")
    val nextLabel = tr("Next section", "Próxima seção")
    val chooseLabel = tr("Choose section: ", "Escolher seção: ") + labels[current] + ", ${current + 1}/${labels.size}"

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onSelect(current - 1) }, enabled = current > 0,
            modifier = Modifier.semantics { contentDescription = previousLabel }) {
            HubIcon(Glyph.ARROW, Modifier.size(20.dp).rotate(180f),
                if (current > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f))
        }
        Box(Modifier.weight(1f)) {
            TextButton(onClick = { menuVisible = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                contentDescription = chooseLabel
            }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                Text(labels[current], modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall)
                Text("${current + 1}/${labels.size}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                HubIcon(Glyph.ARROW, Modifier.size(14.dp).rotate(90f), MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menuVisible, onDismissRequest = { menuVisible = false }) {
                labels.forEachIndexed { index, label ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        trailingIcon = if (index == current) ({ HubIcon(Glyph.CHECK, Modifier.size(18.dp), MaterialTheme.colorScheme.primary) }) else null,
                        onClick = { menuVisible = false; onSelect(index) },
                    )
                }
            }
        }
        IconButton(onClick = { onSelect(current + 1) }, enabled = current < labels.lastIndex,
            modifier = Modifier.semantics { contentDescription = nextLabel }) {
            HubIcon(Glyph.ARROW, Modifier.size(20.dp),
                if (current < labels.lastIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f))
        }
    }
}
