/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settingslib.spa.framework.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.RippleTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * The Material 3 Theme for Settings.
 */
@Composable
fun SettingsTheme(content: @Composable () -> Unit) {
    val isDarkTheme = isSystemInDarkTheme()
    val settingsColorScheme = settingsColorScheme(isDarkTheme)
    val colorScheme = materialColorScheme(isDarkTheme).copy(
        background = settingsColorScheme.background,
    )

    MaterialTheme(colorScheme = colorScheme, typography = rememberSettingsTypography()) {
        CompositionLocalProvider(
            LocalColorScheme provides settingsColorScheme(isDarkTheme),
            LocalRippleTheme provides SettingsRippleTheme,
        ) {
            content()
        }
    }
}

object SettingsTheme {
    val colorScheme: SettingsColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalColorScheme.current
}

private object SettingsRippleTheme : RippleTheme {
    @Composable
    override fun defaultColor() = MaterialTheme.colorScheme.onSurface

    @Composable
    override fun rippleAlpha() = RippleAlpha
}

/** Alpha levels for all content. */
private val RippleAlpha = RippleAlpha(
    pressedAlpha = 0.48f,
    focusedAlpha = 0.48f,
    draggedAlpha = 0.32f,
    hoveredAlpha = 0.16f,
)
