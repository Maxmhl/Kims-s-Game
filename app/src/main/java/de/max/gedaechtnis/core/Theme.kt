package de.max.gedaechtnis.core

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import de.max.gedaechtnis.R
import de.max.gedaechtnis.kim.engine.ObjColor

object AppColors {
    val Background = Color(0xFF0E1115)
    val Surface = Color(0xFF161B21)
    val SurfaceNav = Color(0xFF12161B)
    val Outline = Color(0xFF2B343E)
    val TextPrimary = Color(0xFFE9EEF2)
    val TextSecondary = Color(0xFFA3AEB9)
    val KimAccent = Color(0xFFF5B041)
    val OnKimAccent = Color(0xFF14100A)
    val LeitnerAccent = Color(0xFF5BC8DC)
    val OnLeitnerAccent = Color(0xFF0B1A1E)
    val LeitnerBoxMuted = Color(0xFF2F5E68)
    val Success = Color(0xFF6BD49A)
    val Error = Color(0xFFF07167)

    /** Objektfarben im Raster. */
    fun obj(c: ObjColor): Color = when (c) {
        ObjColor.AMBER -> KimAccent
        ObjColor.CYAN -> LeitnerAccent
        ObjColor.WHITE -> TextPrimary
        ObjColor.CORAL -> Error
    }
}

object AppFonts {
    val SpaceGrotesk = FontFamily(Font(R.font.space_grotesk_bold, FontWeight.Bold))
    val PlexSans = FontFamily(
        Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
        Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
        Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
    )
    val PlexMono = FontFamily(Font(R.font.ibm_plex_mono_medium, FontWeight.Medium))
}

/** Textstile aus Kapitel 6: Überschriften 26/24/20/19/17/16, Fließtext 16–12, Mono 44–18. */
object AppText {
    private fun heading(size: Int) = TextStyle(fontFamily = AppFonts.SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = size.sp, color = AppColors.TextPrimary)
    private fun body(size: Int, w: FontWeight = FontWeight.Normal) = TextStyle(fontFamily = AppFonts.PlexSans, fontWeight = w, fontSize = size.sp, color = AppColors.TextPrimary)
    private fun mono(size: Int) = TextStyle(fontFamily = AppFonts.PlexMono, fontWeight = FontWeight.Medium, fontSize = size.sp, color = AppColors.TextPrimary)

    val H26 = heading(26)
    val H24 = heading(24)
    val H20 = heading(20)
    val H19 = heading(19)
    val H17 = heading(17)
    val H16 = heading(16)

    val Body16 = body(16)
    val Body15 = body(15)
    val Body14 = body(14)
    val Body13 = body(13)
    val Body12 = body(12)
    val Medium14 = body(14, FontWeight.Medium)
    val Semi16 = body(16, FontWeight.SemiBold)
    val Semi14 = body(14, FontWeight.SemiBold)

    val Mono44 = mono(44)
    val Mono36 = mono(36)
    val Mono34 = mono(34)
    val Mono24 = mono(24)
    val Mono22 = mono(22)
    val Mono18 = mono(18)
    val Mono14 = mono(14)
    val Mono12 = mono(12)

    /** Abschnittslabels (MODUS, STUFE, SPIELE): Plex Sans 500, Großbuchstaben, Laufweite +0,08 em, 13 sp. */
    val SectionLabel = TextStyle(
        fontFamily = AppFonts.PlexSans, fontWeight = FontWeight.Medium, fontSize = 13.sp,
        letterSpacing = 0.08.em, color = AppColors.TextSecondary,
    )
}

private val DarkScheme = darkColorScheme(
    primary = AppColors.KimAccent,
    onPrimary = AppColors.OnKimAccent,
    secondary = AppColors.LeitnerAccent,
    onSecondary = AppColors.OnLeitnerAccent,
    background = AppColors.Background,
    onBackground = AppColors.TextPrimary,
    surface = AppColors.Surface,
    onSurface = AppColors.TextPrimary,
    surfaceVariant = AppColors.Surface,
    onSurfaceVariant = AppColors.TextSecondary,
    outline = AppColors.Outline,
    outlineVariant = AppColors.Outline,
    error = AppColors.Error,
    surfaceContainer = AppColors.Surface,
    surfaceContainerHigh = AppColors.Surface,
    surfaceContainerHighest = AppColors.Surface,
)

private val AppTypography = Typography(
    headlineLarge = AppText.H26, headlineMedium = AppText.H24, headlineSmall = AppText.H20,
    titleLarge = AppText.H19, titleMedium = AppText.H17, titleSmall = AppText.H16,
    bodyLarge = AppText.Body16, bodyMedium = AppText.Body14, bodySmall = AppText.Body12,
    labelLarge = AppText.Semi14, labelMedium = AppText.Body13, labelSmall = AppText.Body12,
)

/** Nur ein dunkles Theme (kein Hell-Modus in Version 1). */
@Composable
fun GedaechtnisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, typography = AppTypography, content = content)
}
