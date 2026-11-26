package nl.constantdynamics.dagelijks.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import nl.constantdynamics.dagelijks.R

/**
 * The palette of the "Nachtfilm" design: a deep ink ground, warm white text, amber for
 * what should catch the eye, and one hot colour for the single action that matters.
 */
object AppColors {
    /** The ground of every screen, the camera included. */
    val Ground = Color(0xFF10131B)

    /** Cards and round buttons: only slightly lighter than the ground. */
    val Surface = Color(0xFF191D28)

    /** Something lying on a card: an input field, an empty photo frame. */
    val Raised = Color(0xFF232837)

    /** Thin dividing lines and the edge of a card. */
    val Hairline = Color(0xFF2A3040)

    /** The edge of buttons and labels that are not filled. */
    val Outline = Color(0xFF5C6379)

    val Text = Color(0xFFF3EBDD)
    val TextMuted = Color(0xFFA8A396)

    /** Titles, the "done" mark, the slider: what should catch the eye. */
    val Amber = Color(0xFFF0B043)

    /** A dark amber fill behind amber text. */
    val AmberTint = Color(0xFF3A342D)

    /** Reserved for the shutter and the one action that counts at that moment. */
    val Coral = Color(0xFFF0553A)

    /** Text and pictograms on an amber or coral fill. */
    val OnAccent = Color(0xFF10131B)

    /** The unfilled part of a slider or progress bar. */
    val Track = Color(0xFF394055)
}

/** Both fonts are bundled with the app (open licences); nothing is downloaded. */
object AppFonts {
    /** Wide, light letters for titles, like film credits. */
    val Display = FontFamily(
        unbounded(FontWeight.Light),
        unbounded(FontWeight.Normal),
        unbounded(FontWeight.Medium),
    )

    /** Everything else. One variable font file provides every weight. */
    val Body = FontFamily(
        outfit(FontWeight.Normal),
        outfit(FontWeight.Medium),
        outfit(FontWeight.SemiBold),
        outfit(FontWeight.Bold),
    )

    private fun unbounded(weight: FontWeight) = Font(
        resId = R.font.unbounded,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

    private fun outfit(weight: FontWeight) = Font(
        resId = R.font.outfit,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )
}

object AppType {
    /** The app's name on the start screen. */
    val appTitle = TextStyle(
        fontFamily = AppFonts.Display,
        fontWeight = FontWeight.Light,
        fontSize = 52.sp,
        lineHeight = 58.sp,
        letterSpacing = (-0.015).em,
    )

    /** The title of every other screen. */
    val screenTitle = TextStyle(
        fontFamily = AppFonts.Display,
        fontWeight = FontWeight.Normal,
        fontSize = 21.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.005).em,
    )

    /** Small spaced capitals above a group of things: "NOVEMBER 2026". */
    val overline = TextStyle(
        fontFamily = AppFonts.Body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.em,
    )

    /** The line above the app's name. */
    val tagline = overline.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.24.em)

    val button = TextStyle(
        fontFamily = AppFonts.Body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.01.em,
    )
}

private val colorScheme = darkColorScheme(
    primary = AppColors.Coral,
    onPrimary = AppColors.OnAccent,
    primaryContainer = AppColors.AmberTint,
    onPrimaryContainer = AppColors.Amber,
    secondary = AppColors.Amber,
    onSecondary = AppColors.OnAccent,
    secondaryContainer = AppColors.AmberTint,
    onSecondaryContainer = AppColors.Amber,
    tertiary = AppColors.Amber,
    onTertiary = AppColors.OnAccent,
    background = AppColors.Ground,
    onBackground = AppColors.Text,
    surface = AppColors.Surface,
    onSurface = AppColors.Text,
    surfaceVariant = AppColors.Raised,
    onSurfaceVariant = AppColors.TextMuted,
    surfaceContainerLowest = AppColors.Ground,
    surfaceContainerLow = AppColors.Surface,
    surfaceContainer = AppColors.Surface,
    surfaceContainerHigh = AppColors.Surface,
    surfaceContainerHighest = AppColors.Raised,
    outline = AppColors.Outline,
    outlineVariant = AppColors.Hairline,
    error = AppColors.Coral,
    onError = AppColors.OnAccent,
    scrim = Color.Black,
)

private val body = TextStyle(fontFamily = AppFonts.Body, color = AppColors.Text)
private val display = TextStyle(fontFamily = AppFonts.Display, color = AppColors.Text)

private val typography = Typography(
    displayLarge = display.copy(fontWeight = FontWeight.Light, fontSize = 52.sp, lineHeight = 58.sp),
    displayMedium = display.copy(fontWeight = FontWeight.Light, fontSize = 40.sp, lineHeight = 46.sp),
    displaySmall = display.copy(fontWeight = FontWeight.Light, fontSize = 30.sp, lineHeight = 36.sp),
    headlineLarge = display.copy(fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 30.sp),
    headlineMedium = display.copy(fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 26.sp),
    headlineSmall = display.copy(fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 23.sp),
    titleLarge = body.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = body.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 23.sp),
    titleSmall = body.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = body.copy(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = body.copy(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = body.copy(fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = body.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = body.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 17.sp),
    labelSmall = body.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun DagelijksTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme, typography = typography, shapes = shapes) {
        CompositionLocalProvider(LocalContentColor provides AppColors.Text, content = content)
    }
}
