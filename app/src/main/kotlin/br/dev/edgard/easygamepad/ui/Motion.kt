// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Amjad AAYD
package br.dev.edgard.easygamepad.ui

import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Motion tokens shared by the screens, so transitions feel like one system. */
object Motion {
    const val SHORT = 140
    const val MEDIUM = 260
    const val LONG = 420
}

/**
 * True when the person turned animations off (Developer options or Accessibility "Remove animations").
 * Decorative motion is skipped; state changes still happen instantly.
 */
@Composable fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
}

@Composable fun <T> motionTween(durationMs: Int = Motion.MEDIUM): AnimationSpec<T> =
    if (rememberReducedMotion()) snap() else tween(durationMs)

/** Responsive but not twitchy: follows live input closely without jitter. */
@Composable fun <T> inputSpring(): AnimationSpec<T> =
    if (rememberReducedMotion()) snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
