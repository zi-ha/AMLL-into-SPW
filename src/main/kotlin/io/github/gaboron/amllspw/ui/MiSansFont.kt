package io.github.gaboron.amllspw.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import java.util.concurrent.ConcurrentHashMap

/**
 * Bundled MiSans variable fonts.
 *
 * Compose resolves one typeface per family, so coverage is extended by tagging runs of a script with
 * its own family instead of merging files: the supplements only carry their script (plus a weight
 * axis that ends at 700), while MiSans VF covers Latin, CJK, kana, Cyrillic, Greek and Vietnamese.
 *
 * All supplements share the same weight policy: follow the global setting, clamp to 150–700.
 */
internal object MiSansFont {
    private const val LATIN_RESOURCE = "/fonts/MiSansVF.ttf"
    private const val MIN_SUPPLEMENT_WEIGHT = 150
    private const val MAX_SUPPLEMENT_WEIGHT = 700

    private val supplements = listOf(
        // Precomposed syllables and compatibility jamo are what the Korean file actually carries;
        // conjoining jamo (U+1100–U+11FF) stay untagged so the system fallback still handles them.
        Supplement("korean", "/fonts/MiSansKoreanVF.ttf", 0xAC00..0xD7A3, 0x3130..0x318F),
        // Whole blocks are tagged: Thai, Tibetan, Devanagari, Khmer and the rest stack marks across
        // the run, so splitting a cluster between two families would break shaping.
        Supplement("thai", "/fonts/MiSansThaiVF.ttf", 0x0E00..0x0E7F),
        Supplement("tibetan", "/fonts/MiSansTibetanVF.ttf", 0x0F00..0x0FFF),
        Supplement("lao", "/fonts/MiSansLaoVF.ttf", 0x0E80..0x0EFF),
        Supplement("arabic", "/fonts/MiSansArabicVF.ttf", 0x0600..0x06FF),
        Supplement("devanagari", "/fonts/MiSansDevanagariVF.ttf", 0x0900..0x097F),
        Supplement("gurmukhi", "/fonts/MiSansGurmukhiVF.ttf", 0x0A00..0x0A7F),
        Supplement("myanmar", "/fonts/MiSansMyanmarVF.ttf", 0x1000..0x109F),
        Supplement("khmer", "/fonts/MiSansKhmerVF.ttf", 0x1780..0x17FF, 0x19E0..0x19FF),
    )

    private val latinBytes: ByteArray by lazy { resource(LATIN_RESOURCE) }
    private val families = ConcurrentHashMap<Int, FontFamily>()
    private val supplementFamilies = ConcurrentHashMap<String, FontFamily>()

    @OptIn(ExperimentalTextApi::class)
    fun family(weight: FontWeight): FontFamily = families.getOrPut(weight.weight) {
        FontFamily(Font(
            identity = "io.github.gaboron.amllspw$LATIN_RESOURCE#wght=${weight.weight}",
            data = latinBytes,
            weight = weight,
            // Matching weight alone does not select a variable font's actual outline instance.
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        ))
    }

    /**
     * Tags every run whose script is covered by a supplement (Hangul, Thai, Tibetan, Lao, Arabic,
     * Devanagari, Gurmukhi, Myanmar, Khmer) with that family, so those characters keep MiSans
     * outlines instead of falling back to a system font. Weights above 700 render at 700 because no
     * supplement has a heavier master.
     */
    fun annotated(text: String, style: TextStyle): AnnotatedString {
        val runs = ArrayList<Pair<Supplement, IntRange>>()
        supplements.forEach { supplement -> supplementRuns(text, supplement).forEach { runs += supplement to it } }
        if (runs.isEmpty()) return AnnotatedString(text)
        val weight = (style.fontWeight ?: FontWeight.Normal).weight
            .coerceIn(MIN_SUPPLEMENT_WEIGHT, MAX_SUPPLEMENT_WEIGHT)
        return buildAnnotatedString {
            append(text)
            runs.forEach { (supplement, range) ->
                addStyle(
                    SpanStyle(fontFamily = supplementFamily(supplement, weight), fontWeight = FontWeight(weight)),
                    range.first,
                    range.last + 1,
                )
            }
        }
    }

    @OptIn(ExperimentalTextApi::class)
    private fun supplementFamily(supplement: Supplement, weight: Int): FontFamily =
        supplementFamilies.getOrPut("${supplement.tag}#$weight") {
            FontFamily(Font(
                identity = "io.github.gaboron.amllspw${supplement.path}#wght=$weight",
                data = supplement.bytes,
                weight = FontWeight(weight),
                variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
            ))
        }

    private fun supplementRuns(text: String, supplement: Supplement): List<IntRange> {
        var runs: MutableList<IntRange>? = null
        var start = -1
        for (index in text.indices) {
            if (supplement.covers(text[index].code)) {
                if (start < 0) start = index
            } else if (start >= 0) {
                (runs ?: mutableListOf<IntRange>().also { runs = it }) += start until index
                start = -1
            }
        }
        if (start >= 0) (runs ?: mutableListOf<IntRange>().also { runs = it }) += start until text.length
        return runs ?: emptyList()
    }

    private class Supplement(val tag: String, val path: String, vararg ranges: IntRange) {
        private val ranges = ranges.toList()
        val bytes: ByteArray by lazy { resource(path) }
        fun covers(code: Int): Boolean = ranges.any { code in it }
    }

    private fun resource(path: String): ByteArray = checkNotNull(MiSansFont::class.java.getResourceAsStream(path)) {
        "Missing bundled lyric font: $path"
    }.use { it.readBytes() }
}
