package nl.constantdynamics.dagelijks.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.constantdynamics.dagelijks.R

/** The seventies palette. */
object RetroColors {
    val Mint = Color(0xFF4ECFB5)
    val MintLight = Color(0xFF6ED9C3)
    val MintDark = Color(0xFF2FA892)
    val Coral = Color(0xFFEF4F4A)
    val CoralDark = Color(0xFFC93934)
    val Mustard = Color(0xFFFFD966)
    val MustardDark = Color(0xFFE9B93B)
    val Navy = Color(0xFF1E3A6E)
    val NavyDeep = Color(0xFF12254A)
    val Cream = Color(0xFFF5EBDD)
    val CreamDark = Color(0xFFE5D6BF)

    /** Behind the camera image, where the interface stays out of the way. */
    val CameraBackground = Color(0xFF0E1B36)
}

/** All three fonts are bundled with the app (open licences); nothing is downloaded. */
object RetroFonts {
    /** Fat seventies display letters, for titles. */
    val Display = FontFamily(Font(R.font.shrikhand))

    /** Script accent, for small labels on ribbons. */
    val Script = FontFamily(Font(R.font.pacifico))

    /** Readable body text; one variable font file provides every weight. */
    val Body = FontFamily(
        workSans(FontWeight.Normal),
        workSans(FontWeight.Medium),
        workSans(FontWeight.SemiBold),
        workSans(FontWeight.Bold),
        workSans(FontWeight.ExtraBold),
    )

    private fun workSans(weight: FontWeight) = Font(
        resId = R.font.work_sans,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )
}

object RetroType {
    val script = TextStyle(fontFamily = RetroFonts.Script, fontSize = 18.sp, lineHeight = 24.sp)
    val scriptLarge = TextStyle(fontFamily = RetroFonts.Script, fontSize = 24.sp, lineHeight = 32.sp)
    val button = TextStyle(
        fontFamily = RetroFonts.Body,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.4.sp,
    )
}

private val colorScheme = lightColorScheme(
    primary = RetroColors.Coral,
    onPrimary = RetroColors.Cream,
    primaryContainer = RetroColors.Mustard,
    onPrimaryContainer = RetroColors.Navy,
    secondary = RetroColors.Navy,
    onSecondary = RetroColors.Cream,
    secondaryContainer = RetroColors.Mustard,
    onSecondaryContainer = RetroColors.Navy,
    tertiary = RetroColors.MintDark,
    onTertiary = RetroColors.Cream,
    background = RetroColors.Mint,
    onBackground = RetroColors.Navy,
    surface = RetroColors.Cream,
    onSurface = RetroColors.Navy,
    surfaceVariant = RetroColors.CreamDark,
    onSurfaceVariant = RetroColors.Navy,
    surfaceContainer = RetroColors.Cream,
    surfaceContainerHigh = RetroColors.Cream,
    surfaceContainerHighest = RetroColors.CreamDark,
    surfaceContainerLow = RetroColors.Cream,
    surfaceContainerLowest = RetroColors.Cream,
    outline = RetroColors.Navy,
    outlineVariant = RetroColors.Navy.copy(alpha = 0.35f),
    error = RetroColors.CoralDark,
    onError = RetroColors.Cream,
    scrim = RetroColors.NavyDeep,
)

private val body = TextStyle(fontFamily = RetroFonts.Body, color = RetroColors.Navy)

private val typography = Typography(
    displayLarge = TextStyle(color = RetroColors.Navy, fontFamily = RetroFonts.Display, fontSize = 52.sp, lineHeight = 58.sp),
    displayMedium = TextStyle(color = RetroColors.Navy, fontFamily = RetroFonts.Display, fontSize = 40.sp, lineHeight = 46.sp),
    displaySmall = TextStyle(color = RetroColors.Navy, fontFamily = RetroFonts.Display, fontSize = 32.sp, lineHeight = 38.sp),
    headlineLarge = TextStyle(color = RetroColors.Navy, fontFamily = RetroFonts.Display, fontSize = 28.sp, lineHeight = 34.sp),
    headlineMedium = TextStyle(color = RetroColors.Navy, fontFamily = RetroFonts.Display, fontSize = 24.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(color = RetroColors.Navy, fontFamily = RetroFonts.Display, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = body.copy(fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = body.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    titleSmall = body.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = body.copy(fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = body.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = body.copy(fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = body.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = body.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 17.sp),
    labelSmall = body.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 15.sp),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun DagelijksTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme, typography = typography, shapes = shapes) {
        CompositionLocalProvider(LocalContentColor provides RetroColors.Navy, content = content)
    }
}
