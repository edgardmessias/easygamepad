// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Shared screen chrome: headings, scroll columns, actions, and status blocks. */
object ScreenLayout {
    val contentMaxWidth = 720.dp
    val horizontalPadding = 18.dp
    val sectionSpacing = 16.dp
    val bottomBarElevation = 8.dp
}

@Composable fun ScreenColumn(modifier: Modifier = Modifier, scrollState: ScrollState = rememberScrollState(), content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().then(modifier)) {
        Column(Modifier.widthIn(max = ScreenLayout.contentMaxWidth).fillMaxWidth().align(Alignment.TopCenter).verticalScroll(scrollState)
            .padding(horizontal = ScreenLayout.horizontalPadding).padding(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(ScreenLayout.sectionSpacing), content = content)
    }
}

@Composable fun ScreenHeading(eyebrow: String, title: String, description: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Eyebrow(eyebrow, color = MaterialTheme.colorScheme.primary)
        Text(title, style = screenTitleStyle())
        if (description != null) Description(description)
    }
}

@Composable fun ScreenCardHeader(
    glyph: Glyph,
    title: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    iconSize: Dp = 22.dp,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HubIcon(glyph, Modifier.size(iconSize), tint)
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable fun ActionCard(icon: Glyph, title: String, body: String, color: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = RoundedCornerShape(14.dp), color = color.copy(alpha = .12f)) { HubIcon(icon, Modifier.padding(13.dp).size(23.dp), color) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Description(body)
            }
            HubIcon(Glyph.ARROW, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable fun InfoBanner(text: String, error: Boolean = false) {
    val container = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val ink = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Surface(shape = RoundedCornerShape(18.dp), color = container) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            HubIcon(Glyph.INFO, Modifier.size(19.dp), ink)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = ink)
        }
    }
}

@Composable fun OperationOutcome(
    title: String,
    message: String,
    error: Boolean,
    timestamp: Long? = null,
    timestampPrefix: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        InfoBanner(message, error)
        if (timestamp != null && timestampPrefix != null) Description(timestampPrefix + timeLabel(timestamp))
    }
}

@Composable fun ScreenBottomActions(
    hint: String?,
    showHint: Boolean,
    secondaryLabel: String,
    primaryLabel: String,
    onSecondary: () -> Unit,
    onPrimary: () -> Unit,
    secondaryEnabled: Boolean,
    primaryEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shadowElevation = ScreenLayout.bottomBarElevation) {
        Column(Modifier.padding(horizontal = ScreenLayout.horizontalPadding, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (showHint && hint != null) {
                Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onSecondary, enabled = secondaryEnabled, modifier = Modifier.weight(1f)) { Text(secondaryLabel) }
                Button(onClick = onPrimary, enabled = primaryEnabled, modifier = Modifier.weight(1f)) { Text(primaryLabel) }
            }
        }
    }
}

@Composable fun ScreenLinkStatus(
    active: Boolean,
    activeLabel: String,
    inactiveLabel: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.tertiary,
) {
    StatusPill(if (active) activeLabel else inactiveLabel, active, modifier = modifier, color = color)
}

@Composable fun ScreenConnectCard(
    title: String,
    description: String,
    connecting: Boolean,
    connectEnabled: Boolean,
    connectLabel: String,
    connectingLabel: String,
    onConnect: () -> Unit,
    connectDisabledReason: String? = null,
) {
    HubCard(Modifier.fillMaxWidth().animateContentSize()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Description(description)
        AnimatedVisibility(connecting, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Button(
            onClick = onConnect,
            enabled = connectEnabled && !connecting,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                connectDisabledReason?.let { stateDescription = it }
            },
        ) {
            Text(if (connecting) connectingLabel else connectLabel)
        }
    }
}

@Composable fun timeLabel(time: Long): String {
    val locale = if (LocalPortuguese.current) Locale.forLanguageTag("pt-BR") else Locale.ENGLISH
    return DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(time))
}
