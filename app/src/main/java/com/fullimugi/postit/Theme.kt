package com.fullimugi.postit

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Fredoka = FontFamily(
    Font(R.font.fredoka_regular, FontWeight.Normal),
    Font(R.font.fredoka_medium, FontWeight.Medium),
    Font(R.font.fredoka_semibold, FontWeight.SemiBold),
    Font(R.font.fredoka_bold, FontWeight.Bold),
)
val Caveat = FontFamily(Font(R.font.caveat_bold, FontWeight.Bold))
val Hand = FontFamily(Font(R.font.patrick_hand, FontWeight.Normal))

/** Sticky-note colors. Each tag or list always gets the same one. */
object Sticky {
    private val light = listOf(
        Color(0xFFFFE27A), // lemon
        Color(0xFFFFB8CB), // bubblegum
        Color(0xFFAEE8C8), // mint
        Color(0xFFA9D5FF), // sky
        Color(0xFFFFCA9E), // peach
        Color(0xFFD3C2FF), // lavender
    )
    private val dark = listOf(
        Color(0xFF6B5A1E),
        Color(0xFF6E3445),
        Color(0xFF2F5E47),
        Color(0xFF2F4E6E),
        Color(0xFF6E4527),
        Color(0xFF4C3C78),
    )
    /** Saturated versions for dots, rings and accents. */
    private val bold = listOf(
        Color(0xFFF2B705),
        Color(0xFFFF5C8A),
        Color(0xFF2EC27E),
        Color(0xFF3B8EEA),
        Color(0xFFFF8A3D),
        Color(0xFF8B5CF6),
    )

    private fun index(key: String?): Int = if (key.isNullOrEmpty()) 0 else Math.floorMod(key.lowercase().hashCode(), light.size)

    fun paper(key: String?, darkMode: Boolean): Color = (if (darkMode) dark else light)[index(key)]
    fun ink(darkMode: Boolean): Color = if (darkMode) Color(0xFFF6EEDC) else Color(0xFF2A2420)
    fun bold(key: String?): Color = bold[index(key)]

    /** Which color a note wears: its first tag, else its list, else lemon. */
    fun keyFor(item: Item): String? = item.tags.firstOrNull() ?: item.listName ?: if (item.remindAt != null) "reminders" else null
}

val Tomato = Color(0xFFFF5A36)

private val LightScheme = lightColorScheme(
    primary = Tomato,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE27A),
    onPrimaryContainer = Color(0xFF2A2420),
    secondary = Color(0xFF2A2420),
    onSecondary = Color(0xFFFFF7E8),
    secondaryContainer = Color(0xFFFFE27A),
    onSecondaryContainer = Color(0xFF2A2420),
    tertiary = Color(0xFF3B8EEA),
    tertiaryContainer = Color(0xFFFFD8C7),
    onTertiaryContainer = Color(0xFF4A1A08),
    background = Color(0xFFFBF4E6),
    onBackground = Color(0xFF2A2420),
    surface = Color(0xFFFBF4E6),
    onSurface = Color(0xFF2A2420),
    surfaceVariant = Color(0xFFF1E7D3),
    onSurfaceVariant = Color(0xFF7A6E60),
    surfaceContainer = Color(0xFFF5ECDA),
    surfaceContainerHigh = Color(0xFFFFFBF2),
    surfaceContainerHighest = Color(0xFFEFE4CF),
    surfaceContainerLow = Color(0xFFF8F0E0),
    outline = Color(0xFFB9AB96),
    outlineVariant = Color(0xFFE3D6BF),
    error = Color(0xFFD7263D),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFF7A59),
    onPrimary = Color(0xFF2A0E05),
    primaryContainer = Color(0xFF6B5A1E),
    onPrimaryContainer = Color(0xFFFFF1C7),
    secondary = Color(0xFFF6EEDC),
    onSecondary = Color(0xFF1C1915),
    secondaryContainer = Color(0xFF6B5A1E),
    onSecondaryContainer = Color(0xFFFFF1C7),
    tertiary = Color(0xFF7DB8FF),
    tertiaryContainer = Color(0xFF5A2E1E),
    onTertiaryContainer = Color(0xFFFFDCCD),
    background = Color(0xFF1C1915),
    onBackground = Color(0xFFF6EEDC),
    surface = Color(0xFF1C1915),
    onSurface = Color(0xFFF6EEDC),
    surfaceVariant = Color(0xFF2E2A24),
    onSurfaceVariant = Color(0xFFB5A994),
    surfaceContainer = Color(0xFF26221D),
    surfaceContainerHigh = Color(0xFF2E2A24),
    surfaceContainerHighest = Color(0xFF38332C),
    surfaceContainerLow = Color(0xFF221F1A),
    outline = Color(0xFF6F6556),
    outlineVariant = Color(0xFF3F3931),
    error = Color(0xFFFF6B7D),
)

private fun type(): Typography {
    val base = Typography()
    fun TextStyle.f() = copy(fontFamily = Fredoka)
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Caveat),
        displayMedium = base.displayMedium.copy(fontFamily = Caveat),
        displaySmall = base.displaySmall.copy(fontFamily = Caveat),
        headlineLarge = base.headlineLarge.copy(fontFamily = Caveat, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = Caveat, fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontFamily = Caveat, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.f(),
        bodyMedium = base.bodyMedium.f(),
        bodySmall = base.bodySmall.f(),
        labelLarge = base.labelLarge.copy(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontFamily = Fredoka, fontWeight = FontWeight.Medium),
        labelSmall = base.labelSmall.copy(fontFamily = Fredoka, fontWeight = FontWeight.Medium),
    )
}

/** Handwritten style for what you wrote. */
val NoteText = TextStyle(fontFamily = Hand, fontSize = 19.sp, lineHeight = 23.sp)

private val PlayfulShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

object Look {
    val dark: Boolean
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.background.let { it.red + it.green + it.blue < 1.2f }
}

@Composable
fun PostItTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        typography = type(),
        shapes = PlayfulShapes,
        content = content,
    )
}
