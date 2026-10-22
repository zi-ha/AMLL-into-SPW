package io.github.gaboron.amllspw.ui

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Skiko renders on its own thread while composition disposes nodes on the UI thread. Closing Skia
 * handles straight from `onDispose` therefore races the last frame of the departing node: the
 * renderer reaches a `Paint`/`Image` that already has `_ptr = 0`, the exception escapes into the
 * host's layer, and only the part of the page that was drawn before it survives — the corner block
 * the user sees, which heals after a second or two when the node tree is rebuilt.
 *
 * Retire instead of close: keep the handles for a grace period, then release them on a background
 * thread once no queued frame can still reference them.
 */
internal object SkiaRetire {
    private const val GRACE_MS = 2_000L
    private val queue = ConcurrentLinkedQueue<Pair<Long, AutoCloseable>>()

    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "amll-skia-retire").apply { isDaemon = true }
    }

    init {
        scheduler.scheduleWithFixedDelay({ drain() }, GRACE_MS, 400L, TimeUnit.MILLISECONDS)
    }

    fun retire(resource: AutoCloseable) {
        queue.add(System.currentTimeMillis() + GRACE_MS to resource)
    }

    private fun drain() {
        val now = System.currentTimeMillis()
        while (true) {
            val head = queue.peek() ?: return
            if (head.first > now) return
            queue.poll()
            runCatching { head.second.close() }
        }
    }
}
