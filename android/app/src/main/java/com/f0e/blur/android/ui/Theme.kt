package com.f0e.blur.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun BlurTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = Color(0xFF8AB4F8),
            onPrimary = Color(0xFF0A1929),
            background = Color(0xFF0D1117),
            onBackground = Color(0xFFE6EDF3),
            surface = Color(0xFF161B22),
            onSurface = Color(0xFFE6EDF3),
            surfaceVariant = Color(0xFF21262D),
            onSurfaceVariant = Color(0xFF9DA7B3)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF1A73E8),
            background = Color(0xFFF6F8FA),
            onBackground = Color(0xFF1F2328),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF1F2328),
            surfaceVariant = Color(0xFFEFF2F5),
            onSurfaceVariant = Color(0xFF57606A)
        )
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
