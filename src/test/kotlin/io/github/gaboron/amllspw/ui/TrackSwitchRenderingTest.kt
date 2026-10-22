package io.github.gaboron.amllspw.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.gaboron.amllspw.lyrics.LyricDocument
import io.github.gaboron.amllspw.lyrics.LyricLine
import io.github.gaboron.amllspw.lyrics.LyricWord
import org.jetbrains.skia.Bitmap
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackSwitchRenderingTest {
    @Test
    fun rapidSwitchesAndResizesKeepLyricsAndHostSiblingsVisible() {
        var presentation by mutableStateOf(track(0))
        var options by mutableStateOf(RenderOptions())
        val backdrop = Color(0xff233149)
        val transport = Color(0xff268060)
        val scene = ImageComposeScene(960, 540) {
            Box(Modifier.fillMaxSize().background(backdrop)) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxSize())
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        LyricsScene(presentation, options, Modifier.fillMaxSize(), {}) { item, sceneOptions ->
                            NativeLyricsSurface(item, sceneOptions, Modifier.fillMaxSize(), { 2_000L }, {}, true, { true })
                        }
                    }
                }
                Canvas(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(12.dp)) {
                    drawRect(transport)
                }
            }
        }
        var frame = 0L
        var width = 960
        var height = 540
        fun render(expectText: Boolean = false, snapshot: Boolean = false) {
            scene.render(frame++ * 16_666_667L).use { image ->
                Bitmap.makeFromImage(image).use { bitmap ->
                    assertEquals(backdrop.toArgb(), bitmap.getColor(2, 2))
                    assertEquals(backdrop.toArgb(), bitmap.getColor(width / 4, height / 2))
                    assertEquals(transport.toArgb(), bitmap.getColor(width - 2, height - 2))
                    if (expectText) {
                        assertTrue((20 until height - 20).any { y ->
                            (width / 2 + 20 until width - 20).any { x ->
                                val pixel = bitmap.getColor(x, y)
                                (pixel and 255) > 180 && ((pixel ushr 8) and 255) > 180 && ((pixel ushr 16) and 255) > 180
                            }
                        }, "Lyrics must remain visibly rendered after switching")
                    }
                }
                if (snapshot) {
                    val file = File("build/reports/rendering/track-switch.png")
                    file.parentFile.mkdirs()
                    image.encodeToData()!!.use { file.writeBytes(it.bytes) }
                }
            }
        }
        try {
            repeat(30) { render() }
            render(expectText = true)
            repeat(40) { index ->
                presentation = LyricsPresentation(trackKey = "$index", loading = true)
                render()
                presentation = if (index % 5 == 0) LyricsPresentation(trackKey = "$index") else track(index)
                options = options.copy(reducedMotion = index % 3 == 0)
                width = if (index % 2 == 0) 800 else 960
                height = if (index % 2 == 0) 480 else 540
                scene.constraints = Constraints.fixed(width, height)
                repeat(3) { render() }
            }
            presentation = track(41)
            options = RenderOptions()
            repeat(30) { render() }
            render(expectText = true, snapshot = true)
        } finally {
            scene.close()
        }
    }

    private fun track(index: Int) = LyricsPresentation(trackKey = "$index", document = LyricDocument(
        List(6) { row ->
            val start = row * 4_000L
            LyricLine(start, start + 4_000, listOf(
                LyricWord("切歌测试 ", start, start + 1_000),
                LyricWord("动态歌词 $index", start + 1_000, start + 4_000)
            ), translation = "Track switch rendering", group = row)
        }, LyricDocument.Origin.TTML
    ))
}
