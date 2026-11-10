// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

const val PROFILE_EDITOR_SECTION_COUNT = 6

/** Reserve space for the pinned Save/Apply bar; scales with font and short landscape windows. */
fun profilesEditorBottomInset(shortHeight: Boolean, fontScale: Float): Dp {
    val bar = when {
        fontScale >= 1.5f -> 168.dp
        fontScale >= 1.3f -> 144.dp
        shortHeight -> 112.dp
        else -> 128.dp
    }
    return bar
}

@Composable
fun ProfileEditorSectionNav(
    sectionLabels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = sectionLabels.take(PROFILE_EDITOR_SECTION_COUNT)
    val active = labels.getOrElse(selectedIndex.coerceIn(0, labels.lastIndex.coerceAtLeast(0))) { labels.first() }
    val chipScroll = rememberScrollState()
    val bringRequesters = remember(labels.size) { List(labels.size) { BringIntoViewRequester() } }
    val scope = rememberCoroutineScope()
    LaunchedEffect(selectedIndex) {
        bringRequesters.getOrNull(selectedIndex)?.let { requester ->
            scope.launch { requester.bringIntoView() }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Section", "Seção"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(active, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        Row(Modifier.horizontalScroll(chipScroll), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val currentPrefix = tr("Current section: ", "Seção atual: ")
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(index) },
                    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier
                        .bringIntoViewRequester(bringRequesters[index])
                        .then(if (selected) Modifier.semantics { contentDescription = currentPrefix + label } else Modifier),
                )
            }
        }
    }
}
