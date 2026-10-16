package app.lifetime.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// A warm, quiet palette: sand and sage on near-black, or on paper in light mode.
private val Sand = Color(0xFFE8C9A0)
private val SandDeep = Color(0xFF8A6A43)
private val Sage = Color(0xFFA8C3B0)
private val SageDeep = Color(0xFF4F6B58)

private val DarkColors = darkColorScheme(
    primary = Sand,
    onPrimary = Color(0xFF2B2014),
    primaryContainer = Color(0xFF3A2E20),
    onPrimaryContainer = Sand,
    secondary = Sage,
    onSecondary = Color(0xFF16261B),
    background = Color(0xFF101114),
    onBackground = Color(0xFFECE7E1),
    surface = Color(0xFF101114),
    onSurface = Color(0xFFECE7E1),
    surfaceVariant = Color(0xFF22252B),
    onSurfaceVariant = Color(0xFFA9A49D),
    surfaceContainer = Color(0xFF1A1C21),
    surfaceContainerHigh = Color(0xFF22252B),
    surfaceContainerHighest = Color(0xFF2A2D33),
    outlineVariant = Color(0xFF34373D),
    error = Color(0xFFF2B8B5),
)

private val LightColors = lightColorScheme(
    primary = SandDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF3E3CC),
    onPrimaryContainer = Color(0xFF3A2E20),
    secondary = SageDeep,
    onSecondary = Color.White,
    background = Color(0xFFF7F4EF),
    onBackground = Color(0xFF1E1C19),
    surface = Color(0xFFF7F4EF),
    onSurface = Color(0xFF1E1C19),
    surfaceVariant = Color(0xFFEAE5DD),
    onSurfaceVariant = Color(0xFF6A645C),
    surfaceContainer = Color(0xFFF0ECE5),
    surfaceContainerHigh = Color(0xFFEAE5DD),
    surfaceContainerHighest = Color(0xFFE3DDD4),
    outlineVariant = Color(0xFFD8D1C6),
)

private val base = Typography()

/** Serif for dates and headings gives the timeline a journal feel; sans for everything else. */
private val LifetimeTypography = base.copy(
    displaySmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 46.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
)

@Composable
fun LifetimeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = LifetimeTypography,
        content = content,
    )
}
