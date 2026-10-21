package io.github.gaboron.amllspw.ui

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

/** One shared variable font resource; Compose retains system fallback for missing glyphs. */
internal object MiSansFont {
    private const val RESOURCE = "/fonts/MiSansVF.ttf"
    private val bytes: ByteArray by lazy {
        checkNotNull(MiSansFont::class.java.getResourceAsStream(RESOURCE)) {
            "Missing bundled lyric font: $RESOURCE"
        }.use { it.readBytes() }
    }

    @OptIn(ExperimentalTextApi::class)
    fun family(weight: FontWeight): FontFamily = FontFamily(Font(
        identity = "io.github.gaboron.amllspw$RESOURCE#wght=${weight.weight}",
        data = bytes,
        weight = weight,
        // Matching weight alone does not select a variable font's actual outline instance.
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    ))
}
