package io.github.gaboron.amllspw.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.text.drawText
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Paint
import kotlin.math.roundToInt
import org.jetbrains.skia.Rect as SkiaRect

/** Ordinary informational text: no lyric hit boxes, scaling, word masks or seek action. */
internal class LyricFooterPainter : AutoCloseable {
    private var closed = false
    private val blurPaints = (0..10).map { level -> Paint().apply {
        if (level > 0) ImageFilter.makeBlur(level * .5f, level * .5f, FilterTileMode.DECAL).use { imageFilter = it }
    } }

    fun DrawScope.paintFooter(layout: LyricFooterLayout?, motion: LyricFooterMotion, left: Float, color: Color) {
        if (layout == null || motion.y + layout.height < -60 || motion.y > size.height + 60) return
        val top = motion.y + layout.padding
        val bottom = top + layout.height
        val level = (motion.blur * 2).roundToInt().coerceIn(0, 10)
        // The gradient only fades rows that overlap the viewport edges. When nothing does and the
        // footer is not blurred, drawing straight into the host canvas skips a page-sized layer.
        if (level == 0 && top >= EDGE_FADE_TOP && bottom <= size.height - EDGE_FADE_BOTTOM) {
            for (row in layout.rows) {
                drawText(row.text, color = color, alpha = motion.opacity * row.opacity,
                    topLeft = Offset(left, top + row.top))
            }
            return
        }
        val native = drawContext.canvas.skiaCanvas
        // Bound the layer to the footer block so the mask cannot touch the lyrics beneath it.
        native.saveLayer(SkiaRect.makeLTRB(0f, top - EDGE_PAD, size.width, bottom + EDGE_PAD), blurPaints[level])
        try {
            for (row in layout.rows) {
                drawText(row.text, color = color, alpha = motion.opacity * row.opacity,
                    topLeft = Offset(left, top + row.top))
            }
            // Mask this layer only, so long footers can scroll through the edges without hiding
            // their entire text block or affecting the lyric layer beneath it.
            drawRect(Brush.verticalGradient(
                0f to Color.Transparent, (EDGE_FADE_TOP / size.height).coerceIn(0f, .4f) to Color.White,
                (1f - EDGE_FADE_BOTTOM / size.height).coerceIn(.6f, 1f) to Color.White, 1f to Color.Transparent,
            ), blendMode = BlendMode.DstIn)
        } finally { native.restore() }
    }

    override fun close() {
        // Compose can forget the same effect twice during a recomposition burst; closing a native
        // Paint twice throws "Object already closed" inside applyChanges and destroys the frame.
        if (closed) return
        closed = true
        blurPaints.forEach { it.close() }
    }

    private companion object {
        const val EDGE_FADE_TOP = 45f
        const val EDGE_FADE_BOTTOM = 70f
        const val EDGE_PAD = 40f
    }
}
