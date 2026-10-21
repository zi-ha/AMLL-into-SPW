package io.github.gaboron.amllspw.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Typography shared by main lyrics, background vocals, translations, romanization and ruby. */
internal data class LyricTypography(
    val weight: FontWeight = FontWeight(DEFAULT_WEIGHT),
    val fontSizeSp: Int? = null,
) {
    fun fontSize(viewportWidthDp: Float): Float =
        fontSizeSp?.toFloat() ?: (viewportWidthDp / 13.5f).coerceIn(24f, 42f)

    fun textStyle(sizeSp: Float) = TextStyle(
        fontFamily = MiSansFont.family(weight),
        fontWeight = weight,
        fontSynthesis = FontSynthesis.None,
        fontSize = sizeSp.sp,
    )

    companion object {
        const val MIN_WEIGHT = 100
        const val MAX_WEIGHT = 900
        const val WEIGHT_STEP = 100
        const val DEFAULT_WEIGHT = 500
        private val sizes = setOf(18, 20, 22, 24, 26, 28, 30, 32, 36, 40, 44, 48, 56, 64, 72)

        fun normalizeWeight(weight: Int): Int {
            val clamped = weight.coerceIn(MIN_WEIGHT, MAX_WEIGHT)
            val steps = (clamped - MIN_WEIGHT + WEIGHT_STEP / 2) / WEIGHT_STEP
            return (MIN_WEIGHT + steps * WEIGHT_STEP).coerceIn(MIN_WEIGHT, MAX_WEIGHT)
        }

        fun fromPreferences(weight: Int, size: String) = LyricTypography(
            weight = FontWeight(normalizeWeight(weight)),
            fontSizeSp = size.toIntOrNull()?.takeIf { it in sizes },
        )
    }
}
