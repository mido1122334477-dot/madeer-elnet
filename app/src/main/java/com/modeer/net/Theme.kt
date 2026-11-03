package com.modeer.net

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// نفس ألوان النسخة التجريبية: أخضر مزرق هادي، وأبيض رمادي فاتح.
private val Light = lightColorScheme(
    primary = Color(0xFF0B6E66),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8EEEB),
    onPrimaryContainer = Color(0xFF0B6E66),
    secondaryContainer = Color(0xFFD8EEEB),
    onSecondaryContainer = Color(0xFF0B6E66),
    tertiary = Color(0xFFA06A00),
    tertiaryContainer = Color(0xFFF7EBC8),
    onTertiaryContainer = Color(0xFFA06A00),
    background = Color(0xFFEEF3F2),
    onBackground = Color(0xFF13201F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF13201F),
    surfaceVariant = Color(0xFFDCE6E4),
    onSurfaceVariant = Color(0xFF5B6E6C),
    surfaceContainer = Color(0xFFFFFFFF),
    outline = Color(0xFFB6C8C5),
    error = Color(0xFFB4470F),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBE5D8),
    onErrorContainer = Color(0xFFB4470F),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF3DBBAE),
    onPrimary = Color(0xFF06211E),
    primaryContainer = Color(0xFF1B3A37),
    onPrimaryContainer = Color(0xFF3DBBAE),
    secondaryContainer = Color(0xFF1B3A37),
    onSecondaryContainer = Color(0xFF3DBBAE),
    tertiary = Color(0xFFE5B84A),
    tertiaryContainer = Color(0xFF3A3012),
    onTertiaryContainer = Color(0xFFE5B84A),
    background = Color(0xFF0E1A19),
    onBackground = Color(0xFFE6F0EE),
    surface = Color(0xFF162625),
    onSurface = Color(0xFFE6F0EE),
    surfaceVariant = Color(0xFF233A37),
    onSurfaceVariant = Color(0xFF93AAA6),
    surfaceContainer = Color(0xFF162625),
    outline = Color(0xFF3B5650),
    error = Color(0xFFF0955F),
    onError = Color(0xFF3A1A08),
    errorContainer = Color(0xFF3A2418),
    onErrorContainer = Color(0xFFF0955F),
)

@Composable
fun MadeerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}
