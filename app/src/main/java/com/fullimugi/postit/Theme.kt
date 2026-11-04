package com.fullimugi.postit

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Light = lightColorScheme(
    primary = Color(0xFF8A5A00),
    primaryContainer = Color(0xFFFFE08A),
    onPrimaryContainer = Color(0xFF2B1D00),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFF2C14E),
    primaryContainer = Color(0xFF5A4A1C),
    onPrimaryContainer = Color(0xFFFFEFC4),
)

/** Uses your phone's Material You colors when available (your Fold8 has them). */
@Composable
fun PostItTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(ctx)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
