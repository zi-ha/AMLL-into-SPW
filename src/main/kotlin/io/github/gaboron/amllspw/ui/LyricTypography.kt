package io.github.gaboron.amllspw.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal enum class LyricFontWeight(val value: String, weight: Int) {
    // Preference keys stay stable; values follow MiSans VF's named instances (wght 150-700).
    EXTRA_LIGHT("200", 200),
    LIGHT("300", 250),
    REGULAR("400", 330),
    MEDIUM("500", 380),
    BOLD("700", 630),
    HEAVY("900", 700);

    val fontWeight = FontWeight(weight)
    val family: FontFamily by lazy { MiSansFont.family(fontWeight) }

    companion object {
        val DEFAULT = MEDIUM
        fun fromPreference(value: String) = entries.firstOrNull { it.value == value } ?: DEFAULT
    }
}

/** Typography shared by main lyrics, background vocals, translations, romanization and ruby. */
internal data class LyricTypography(
    val weight: LyricFontWeight = LyricFontWeight.DEFAULT,
    val fontSizeSp: Int? = null,
) {
    fun fontSize(viewportWidthDp: Float): Float =
        fontSizeSp?.toFloat() ?: (viewportWidthDp / 13.5f).coerceIn(24f, 42f)

    fun textStyle(sizeSp: Float) = TextStyle(
        fontFamily = weight.family,
        fontWeight = weight.fontWeight,
        fontSynthesis = FontSynthesis.None,
        fontSize = sizeSp.sp,
    )

    companion object {
        private val sizes = setOf(18, 20, 22, 24, 26, 28, 30, 32, 36, 40, 44, 48, 56, 64, 72)

        fun fromPreferences(weight: String, size: String) = LyricTypography(
            weight = LyricFontWeight.fromPreference(weight),
            fontSizeSp = size.toIntOrNull()?.takeIf { it in sizes },
        )
    }
}
