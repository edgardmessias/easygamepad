// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Edgard
package br.dev.edgard.easygamepad.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Material window width classes: compact < 600 dp, medium < 840 dp, expanded otherwise. */
enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

/**
 * How the current window should be laid out. Phones in portrait stay exactly as before;
 * landscape phones, foldables and tablets get a navigation rail and, when there is room,
 * two side-by-side panes.
 */
@Immutable data class WindowLayout(val width: WidthClass = WidthClass.COMPACT, val shortHeight: Boolean = false) {
    /** Side rail instead of a bottom bar: any wide window, and short (landscape phone) windows. */
    val rail: Boolean get() = width != WidthClass.COMPACT || shortHeight
    /** Split screen content into two scrolling panes. */
    val twoPane: Boolean get() = width == WidthClass.EXPANDED || (width == WidthClass.MEDIUM && shortHeight)
    /** Tablet-sized windows show the controller artwork larger; short windows keep it compact. */
    fun stageHeight(default: Dp): Dp = if (twoPane && !shortHeight) maxOf(default, 280.dp) else default

    companion object {
        fun of(width: Dp, height: Dp) = WindowLayout(
            width = when { width < 600.dp -> WidthClass.COMPACT; width < 840.dp -> WidthClass.MEDIUM; else -> WidthClass.EXPANDED },
            shortHeight = height < 480.dp)
    }
}

val LocalWindowLayout = staticCompositionLocalOf { WindowLayout() }

/** Screen titles shrink on short landscape windows so the content below stays visible. */
@Composable fun screenTitleStyle() = MaterialTheme.typography.headlineLarge.let { if (LocalWindowLayout.current.shortHeight) it.copy(fontSize = 22.sp) else it }

/** Measures the window once at the root and exposes it to every screen. */
@Composable fun ProvideWindowLayout(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalWindowLayout provides WindowLayout.of(maxWidth, maxHeight), content = content)
    }
}

/**
 * A screen with a primary and a secondary part. On compact windows (and when [secondary] is
 * null) both stack in one centred column, exactly like [ScreenColumn]. On wide windows they sit
 * side by side, each scrolling on its own, so the controller stays visible while you read details.
 */
@Composable fun AdaptivePanes(
    primaryScroll: ScrollState = rememberScrollState(),
    secondary: (@Composable ColumnScope.() -> Unit)?,
    primary: @Composable ColumnScope.() -> Unit,
) {
    if (!LocalWindowLayout.current.twoPane || secondary == null) {
        ScreenColumn(scrollState = primaryScroll) { primary(); secondary?.invoke(this) }
        return
    }
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.widthIn(max = 1280.dp).fillMaxSize().align(Alignment.TopCenter).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Pane(Modifier.weight(1f), primaryScroll, primary)
            Pane(Modifier.weight(1f), rememberScrollState(), secondary)
        }
    }
}

@Composable private fun Pane(modifier: Modifier, scroll: ScrollState, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxHeight().verticalScroll(scroll).padding(horizontal = 6.dp).padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}
