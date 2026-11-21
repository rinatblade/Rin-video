package com.rin.video

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

data class RinTheme(
    val name: String,
    val dark: Boolean,
    val bg1: Color,
    val bg2: Color,
    val surface: Color,
    val primary: Color,
    val accent: Color,
    val text: Color,
)

private fun t(name: String, dark: Boolean, b1: Long, b2: Long, s: Long, p: Long, a: Long, tx: Long) =
    RinTheme(name, dark, Color(b1), Color(b2), Color(s), Color(p), Color(a), Color(tx))

val RinThemes: List<RinTheme> = listOf(
    t("Сумерки", true, 0xFF0F1020, 0xFF1E1B4B, 0xFF26244F, 0xFF8B5CF6, 0xFFEC4899, 0xFFF5F3FF),
    t("Океан", true, 0xFF04121F, 0xFF0B3954, 0xFF10324A, 0xFF22D3EE, 0xFF38BDF8, 0xFFE0F7FF),
    t("Закат", true, 0xFF2A0F1D, 0xFF5B1E3A, 0xFF4A1A33, 0xFFFB923C, 0xFFF43F5E, 0xFFFFF1E6),
    t("Лес", true, 0xFF07140E, 0xFF123524, 0xFF173D2A, 0xFF4ADE80, 0xFFA3E635, 0xFFEAFBEF),
    t("Малина", true, 0xFF1A0510, 0xFF4C0F2E, 0xFF5A1438, 0xFFF472B6, 0xFFFB7185, 0xFFFFEEF6),
    t("Графит", true, 0xFF0B0B0D, 0xFF1F2024, 0xFF2A2B31, 0xFFE5E7EB, 0xFF94A3B8, 0xFFF8FAFC),
    t("Неон", true, 0xFF05010F, 0xFF1A0536, 0xFF2A0B52, 0xFF00F5D4, 0xFFF72585, 0xFFF2E9FF),
    t("Кофе", true, 0xFF1C1210, 0xFF3B2A22, 0xFF4A362B, 0xFFD6A25E, 0xFFE9C79B, 0xFFFFF4E8),
    t("Космос", true, 0xFF02020A, 0xFF0B1033, 0xFF141A4A, 0xFF7C83FF, 0xFFFFD166, 0xFFEDEEFF),
    t("Изумруд", true, 0xFF031A17, 0xFF0B4A40, 0xFF0F5C50, 0xFF2DD4BF, 0xFFFDE047, 0xFFE6FFFA),
    t("Полночь", true, 0xFF000000, 0xFF111111, 0xFF1A1A1A, 0xFFFF3D71, 0xFFFFC107, 0xFFFFFFFF),
    t("Рассвет", false, 0xFFFFF7ED, 0xFFFFE4D6, 0xFFFFFFFF, 0xFFF97316, 0xFFEC4899, 0xFF3B1F14),
    t("Лаванда", false, 0xFFF5F3FF, 0xFFE9E1FF, 0xFFFFFFFF, 0xFF7C3AED, 0xFFC084FC, 0xFF241A45),
    t("Мята", false, 0xFFF0FDF9, 0xFFD5F5EA, 0xFFFFFFFF, 0xFF10B981, 0xFF06B6D4, 0xFF0F3D33),
    t("Небо", false, 0xFFEFF8FF, 0xFFD6EBFF, 0xFFFFFFFF, 0xFF2563EB, 0xFF38BDF8, 0xFF10264A),
    t("Персик", false, 0xFFFFF5F5, 0xFFFFE0E0, 0xFFFFFFFF, 0xFFFB7185, 0xFFFDBA74, 0xFF4A1F28),
    t("Лимонад", false, 0xFFFEFCE8, 0xFFFFF7B0, 0xFFFFFFFF, 0xFFCA8A04, 0xFF84CC16, 0xFF3A3205),
    t("Сакура", false, 0xFFFFF1F7, 0xFFFFD9E8, 0xFFFFFFFF, 0xFFDB2777, 0xFFF9A8D4, 0xFF4A1230),
    t("Бумага", false, 0xFFFAF7F2, 0xFFEDE6DA, 0xFFFFFFFF, 0xFF8B5E3C, 0xFFD97706, 0xFF2B2118),
    t("Арктика", false, 0xFFF1F5F9, 0xFFDCE6F0, 0xFFFFFFFF, 0xFF0EA5E9, 0xFF6366F1, 0xFF0F172A),
)

data class RinFont(val file: String, val name: String)

/** Читает список шрифтов и оставляет только те, что реально лежат в assets (до 30 штук). */
fun loadFonts(ctx: Context): List<RinFont> {
    val present = try {
        ctx.assets.list("fonts")?.toSet() ?: emptySet()
    } catch (_: Exception) {
        emptySet()
    }
    val lines = try {
        ctx.assets.open("fonts/fonts.txt").bufferedReader().readLines()
    } catch (_: Exception) {
        emptyList()
    }
    return lines.mapNotNull { l ->
        val p = l.split("|")
        if (p.size >= 2 && p[0] in present) RinFont(p[0], p[1]) else null
    }.take(30)
}

fun familyFor(ctx: Context, f: RinFont): FontFamily =
    try {
        FontFamily(Font("fonts/${f.file}", ctx.assets))
    } catch (_: Exception) {
        FontFamily.Default
    }

fun typographyFor(f: FontFamily): Typography {
    val b = Typography()
    return Typography(
        displayLarge = b.displayLarge.copy(fontFamily = f),
        displayMedium = b.displayMedium.copy(fontFamily = f),
        displaySmall = b.displaySmall.copy(fontFamily = f),
        headlineLarge = b.headlineLarge.copy(fontFamily = f),
        headlineMedium = b.headlineMedium.copy(fontFamily = f),
        headlineSmall = b.headlineSmall.copy(fontFamily = f),
        titleLarge = b.titleLarge.copy(fontFamily = f),
        titleMedium = b.titleMedium.copy(fontFamily = f),
        titleSmall = b.titleSmall.copy(fontFamily = f),
        bodyLarge = b.bodyLarge.copy(fontFamily = f),
        bodyMedium = b.bodyMedium.copy(fontFamily = f),
        bodySmall = b.bodySmall.copy(fontFamily = f),
        labelLarge = b.labelLarge.copy(fontFamily = f),
        labelMedium = b.labelMedium.copy(fontFamily = f),
        labelSmall = b.labelSmall.copy(fontFamily = f),
    )
}

@Composable
fun RinMaterialTheme(theme: RinTheme, family: FontFamily, content: @Composable () -> Unit) {
    val onP = if (theme.primary.luminance() > 0.5f) Color(0xFF111111) else Color.White
    val onA = if (theme.accent.luminance() > 0.5f) Color(0xFF111111) else Color.White
    val container = lerp(theme.surface, theme.primary, 0.28f)
    val muted = theme.text.copy(alpha = 0.7f)
    val scheme = if (theme.dark) {
        darkColorScheme(
            primary = theme.primary, onPrimary = onP,
            secondary = theme.accent, onSecondary = onA,
            tertiary = theme.accent,
            background = theme.bg1, onBackground = theme.text,
            surface = theme.surface, onSurface = theme.text,
            surfaceVariant = theme.surface, onSurfaceVariant = muted,
            primaryContainer = container, onPrimaryContainer = theme.text,
            outline = muted,
        )
    } else {
        lightColorScheme(
            primary = theme.primary, onPrimary = onP,
            secondary = theme.accent, onSecondary = onA,
            tertiary = theme.accent,
            background = theme.bg1, onBackground = theme.text,
            surface = theme.surface, onSurface = theme.text,
            surfaceVariant = theme.surface, onSurfaceVariant = muted,
            primaryContainer = container, onPrimaryContainer = theme.text,
            outline = muted,
        )
    }
    MaterialTheme(colorScheme = scheme, typography = typographyFor(family), content = content)
}
