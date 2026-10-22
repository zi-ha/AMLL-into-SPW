package io.github.gaboron.amllspw.ui

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.skiaCanvas

// Save before drawing: equal save depths do not imply equal transforms or clips.
internal fun DrawScope.withIsolatedCanvas(draw: () -> Unit) {
    val context = drawContext
    val canvas = context.canvas
    val native = canvas.skiaCanvas
    val size = context.size
    val density = context.density
    val direction = context.layoutDirection
    val layer = context.graphicsLayer
    val depth = native.saveCount
    native.save()
    try {
        draw()
    } catch (error: Exception) {
        DrawFailure.report(error)
    } finally {
        val unbalanced = native.saveCount != depth + 1
        native.restoreToCount(depth)
        context.canvas = canvas
        context.size = size
        context.density = density
        context.layoutDirection = direction
        context.graphicsLayer = layer
        if (unbalanced) DrawFailure.report(IllegalStateException("画布 save/restore 不平衡"), "画布状态")
    }
}

internal object DrawFailure {
    private val reported = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val log = java.io.File(System.getProperty("user.home"), ".amll-spw-draw.txt")

    fun report(error: Throwable, kind: String = "歌词") {
        if (!reported.add("$kind:${error.javaClass.name}")) return
        System.err.println("AMLL: $kind 绘制异常")
        error.printStackTrace(System.err)
        // A drawing fault is swallowed so the frame survives, which also means nothing reaches the
        // host log. Keep one line per distinct fault on disk so it can still be diagnosed later.
        runCatching {
            if (log.length() > 256 * 1024) log.writeText("")
            log.appendText("===== ${java.time.LocalDateTime.now()} [$kind] ${error.javaClass.name}: ${error.message}\n")
            log.appendText(error.stackTraceToString() + "\n")
        }
    }
}
