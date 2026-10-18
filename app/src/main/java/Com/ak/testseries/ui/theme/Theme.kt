package Com.ak.testseries.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import Com.ak.testseries.viewmodel.AppTheme

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    secondary = DarkSecondary,
    tertiary = DarkTertiary,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = DarkText,
    onSurface = DarkText
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    secondary = LightSecondary,
    tertiary = LightTertiary,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = LightText,
    onSurface = LightText
)

private val YellowColorScheme = lightColorScheme(
    primary = YellowPrimary,
    secondary = YellowSecondary,
    tertiary = YellowTertiary,
    background = YellowBackground,
    surface = YellowSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = YellowText,
    onSurface = YellowText
)

private val JeeColorScheme = lightColorScheme(
    primary = JeePrimary,
    secondary = JeeSecondary,
    tertiary = JeeTertiary,
    background = JeeBackground,
    surface = JeeSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = JeeText,
    onSurface = JeeText
)

private val BlueColorScheme = darkColorScheme(
    primary = BluePrimary,
    secondary = BlueSecondary,
    tertiary = BlueTertiary,
    background = BlueBackground,
    surface = BlueSurface,
    surfaceVariant = BlueSurfaceVariant,
    primaryContainer = BluePrimaryContainer,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = BlueText,
    onSurface = BlueText,
    onSurfaceVariant = BlueText
)

@Composable
fun MyApplicationTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    targetExam: String = "NEET",
    isSystemDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when (appTheme) {
                AppTheme.SYSTEM -> if (isSystemDark) DarkColorScheme else if (targetExam == "JEE") JeeColorScheme else LightColorScheme
        AppTheme.DARK -> DarkColorScheme
        AppTheme.LIGHT -> if (targetExam == "JEE") JeeColorScheme else LightColorScheme
        AppTheme.YELLOW -> YellowColorScheme
        AppTheme.BLUE -> BlueColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
