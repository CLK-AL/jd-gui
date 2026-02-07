/*
 * Copyright (c) 2008-2026 Emmanuel Dupuy & Tomer Bar-Shlomo.
 * This project is distributed under the GPLv3 license.
 * See LICENSE file for more information.
 */

package al.clk.gui.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// gui Color Palette
val JdBlue = Color(0xFF1976D2)
val JdBlueDark = Color(0xFF0D47A1)
val JdBlueLight = Color(0xFF64B5F6)
val JdOrange = Color(0xFFFF9800)
val JdGreen = Color(0xFF4CAF50)
val JdRed = Color(0xFFF44336)

// Light Theme Colors
private val LightColors = lightColorScheme(
    primary = JdBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F2FD),
    onPrimaryContainer = JdBlueDark,
    secondary = JdOrange,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFFFFF3E0),
    onSecondaryContainer = Color(0xFFE65100),
    tertiary = JdGreen,
    onTertiary = Color.White,
    error = JdRed,
    onError = Color.White,
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF212121),
    surface = Color.White,
    onSurface = Color(0xFF212121),
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF616161),
    outline = Color(0xFFBDBDBD)
)

// Dark Theme Colors
private val DarkColors = darkColorScheme(
    primary = JdBlueLight,
    onPrimary = Color.Black,
    primaryContainer = JdBlueDark,
    onPrimaryContainer = Color(0xFFBBDEFB),
    secondary = Color(0xFFFFB74D),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF5D4037),
    onSecondaryContainer = Color(0xFFFFE0B2),
    tertiary = Color(0xFF81C784),
    onTertiary = Color.Black,
    error = Color(0xFFEF5350),
    onError = Color.Black,
    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF2D2D2D),
    onSurfaceVariant = Color(0xFFB0B0B0),
    outline = Color(0xFF616161)
)

// Code Editor Colors
object CodeColors {
    val keyword = Color(0xFF0000FF)       // Blue - keywords
    val type = Color(0xFF008080)          // Teal - types
    val string = Color(0xFF008000)        // Green - strings
    val number = Color(0xFF098658)        // Dark green - numbers
    val comment = Color(0xFF808080)       // Gray - comments
    val annotation = Color(0xFF808000)    // Olive - annotations
    val operator = Color(0xFF000000)      // Black - operators
    val identifier = Color(0xFF000000)    // Black - identifiers
    val method = Color(0xFF795E26)        // Brown - method names
    val field = Color(0xFF001080)         // Dark blue - fields
    val constant = Color(0xFF0070C1)      // Light blue - constants
    val error = Color(0xFFFF0000)         // Red - errors

    // Dark theme variants
    val keywordDark = Color(0xFF569CD6)
    val typeDark = Color(0xFF4EC9B0)
    val stringDark = Color(0xFFCE9178)
    val numberDark = Color(0xFFB5CEA8)
    val commentDark = Color(0xFF6A9955)
    val annotationDark = Color(0xFFDCDCAA)
    val operatorDark = Color(0xFFD4D4D4)
    val identifierDark = Color(0xFF9CDCFE)
    val methodDark = Color(0xFFDCDCAA)
    val fieldDark = Color(0xFF9CDCFE)
    val constantDark = Color(0xFF4FC1FF)
    val errorDark = Color(0xFFF44747)
}

@Composable
fun JdGuiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

val Typography = Typography()
