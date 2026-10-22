package io.github.gaboron.amllspw.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.*
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Paint

internal class LyricLinePainter(private val measurerProvider: () -> TextMeasurer) : AutoCloseable {
    private var closed = false
    // Cache native resources instead of allocating blur layers/filters for every animation frame.
    private val blurPaints = (0..4).map { level -> Paint().apply {
        if (level > 0) ImageFilter.makeBlur(level * .6f, level * .6f, FilterTileMode.DECAL).use { imageFilter = it }
    } }
    // Alpha buckets for the viewport edge fade, so no Skia Paint is allocated per row per frame.
    private val edgePaints = (0..EDGE_STEPS).map { step -> Paint().apply { alpha = step * 255 / EDGE_STEPS } }
    private val words = WordHighlightPainter()

    fun edgePaint(alpha: Float): Paint =
        edgePaints[(alpha.coerceIn(0f, 1f) * EDGE_STEPS).toInt().coerceIn(0, EDGE_STEPS)]

    fun DrawScope.paintLine(layout: LineLayout, positionMs: Long, active: Boolean, distance: Int, manual: Boolean, options: RenderOptions, highlight: Float = if (active) 1f else 0f, color: Color = Color.White) {
        val native = drawContext.canvas.skiaCanvas
        val blur = if (active || manual || !options.blur || options.reducedMotion) 0 else distance.coerceIn(0, 4)
        if (blur > 0) native.saveLayer(layout.layerBounds, blurPaints[blur])
        try {
            translate(top = layout.rubyHeight) {
                if (!layout.line.hasWordTiming || highlight <= .001f) {
                    drawText(layout.main, color = color, alpha = .2f + .8f * highlight)
                } else {
                    for (region in layout.words) {
                        with(words) { paint(layout, region, positionMs, highlight, options, color) }
                    }
                }
                for (region in layout.words.filter { it.word.ruby.isNotEmpty() && options.romanization }) {
                    val rect = region.rects.firstOrNull() ?: continue
                    val style = layout.main.layoutInput.style
                    val ruby = measurerProvider().measure(AnnotatedString(region.word.ruby), options.typography.textStyle(style.fontSize.value * .38f))
                    drawText(ruby, color = color.copy(alpha = if (active) .9f else .4f),
                        topLeft = Offset(rect.center.x - ruby.size.width / 2, rect.top - ruby.size.height))
                }
                var auxiliaryY = layout.main.size.height + 6f
                for (text in layout.auxiliary) {
                    drawText(text, color = color.copy(alpha = .3f + .35f * highlight), topLeft = Offset(0f, auxiliaryY))
                    auxiliaryY += text.size.height + 5
                }
            }
        } finally { if (blur > 0) native.restore() }
    }

    fun clearTextCache() { words.clearTextCache() }
    override fun close() {
        if (closed) return
        closed = true
        words.close(); blurPaints.forEach { it.close() }; edgePaints.forEach { it.close() }
    }
    private companion object { const val EDGE_STEPS = 64 }
}
