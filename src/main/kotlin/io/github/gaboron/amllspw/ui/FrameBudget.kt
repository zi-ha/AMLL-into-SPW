package io.github.gaboron.amllspw.ui

/**
 * The player animates the lyric column while it swaps tracks, so the column width differs on every
 * frame of that transition. Re-flowing the whole document per frame — one text measure plus a glyph
 * box lookup per character — costs far more than a frame and keeps the page from settling.
 *
 * Only re-flow when the drift is large enough to be visible; the rows are drawn with the geometry
 * they were measured for, so a few pixels of movement never makes the text shimmer.
 */
internal object LayoutBudget {
    fun widthChanged(previous: Int, current: Int): Boolean {
        if (previous <= 0) return true
        return kotlin.math.abs(current - previous) > maxOf(6, (previous * .012f).toInt())
    }

    fun fontSizeChanged(previous: Float, current: Float): Boolean {
        if (previous <= 0f) return true
        return kotlin.math.abs(current - previous) > .6f
    }
}
