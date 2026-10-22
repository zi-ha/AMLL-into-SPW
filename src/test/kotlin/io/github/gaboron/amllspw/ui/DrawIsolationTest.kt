package io.github.gaboron.amllspw.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class DrawIsolationTest {
    @Test
    fun restoresTransformAndClipWithoutAnExtraSave() = verifyHostDrawing { native ->
        withIsolatedCanvas {
            native.translate(100f, 100f)
            native.clipRect(Rect.makeWH(1f, 1f))
        }
    }

    @Test
    fun restoresLeakedLayersAfterDrawingThrows() = verifyHostDrawing { native ->
        withIsolatedCanvas {
            native.saveLayer(Rect.makeWH(32f, 32f), null)
            native.save()
            native.scale(.2f, .2f)
            native.clipRect(Rect.makeWH(1f, 1f))
            error("injected lyric draw failure")
        }
    }

    @Test
    fun restoresTheSharedComposeContextAfterFailure() = verifyHostDrawing { _ ->
        Bitmap().use { other ->
            other.allocN32Pixels(8, 8)
            Canvas(other).use { otherCanvas ->
                withIsolatedCanvas {
                    drawContext.canvas = otherCanvas.asComposeCanvas()
                    drawContext.size = Size(1f, 1f)
                    drawContext.density = Density(3f)
                    drawContext.layoutDirection = LayoutDirection.Rtl
                    error("injected context failure")
                }
            }
        }
    }

    @Test
    fun restoresStateButDoesNotSwallowFatalErrors() = verifyHostDrawing { native ->
        assertFailsWith<AssertionError> {
            withIsolatedCanvas {
                native.scale(.2f, .2f)
                throw AssertionError("injected fatal failure")
            }
        }
    }

    private fun verifyHostDrawing(draw: DrawScope.(Canvas) -> Unit) {
        Bitmap().use { bitmap ->
            bitmap.allocN32Pixels(64, 64)
            bitmap.erase(Color.White.toArgb())
            Canvas(bitmap).use { native ->
                native.translate(6f, 8f)
                native.clipRect(Rect.makeWH(32f, 32f))
                val canvas = native.asComposeCanvas()
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(64f, 64f)) {
                    val depth = native.saveCount
                    draw(native)
                    assertEquals(depth, native.saveCount)
                    assertSame(canvas, drawContext.canvas)
                    assertEquals(Size(64f, 64f), size)
                    assertEquals(1f, density)
                    assertEquals(LayoutDirection.Ltr, layoutDirection)
                    drawRect(Color.Green)
                }
                assertEquals(Color.Green.toArgb(), bitmap.getColor(7, 9))
                assertEquals(Color.Green.toArgb(), bitmap.getColor(36, 38))
                assertEquals(Color.White.toArgb(), bitmap.getColor(3, 3))
                assertEquals(Color.White.toArgb(), bitmap.getColor(40, 40))
            }
        }
    }
}
