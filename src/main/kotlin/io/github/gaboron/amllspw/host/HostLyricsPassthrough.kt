package io.github.gaboron.amllspw.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.currentComposer
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi
import com.xuncorp.spw.workshop.api.hook.UiHookCall
import io.github.gaboron.amllspw.ui.DrawFailure
import java.lang.reflect.Method

/** The host subtree stays composed and measured; it simply contributes no pixels. */
private val Invisible = Modifier.graphicsLayer { alpha = 0f }

/**
 * Runs the host's own line renderer and draws our layer beside it in the same slot.
 *
 * Replacing the renderer outright removed the `BoxWithConstraints` subtree the player page measures
 * its background panel from: the panel collapsed to a cover-sized block and the lyrics vanished
 * until the page was rebuilt. Calling the original with an invisible modifier keeps every
 * measurement and side effect identical to the unmodified host — only the text pixels are dropped.
 *
 * [original] is resolved once in [AmllPlugin.start]; the plugin refuses to start when it is missing,
 * so this slot can never end up drawing nothing at all.
 */
@OptIn(InternalComposeApi::class, UnstableSpwWorkshopApi::class)
@Composable
internal fun HostLyricsPassthrough(
    call: UiHookCall,
    renderer: NativeLyricsTarget.Renderer,
    original: Method,
    runtime: AmllRuntime,
) {
    val index = renderer.parameters.indexOf(Modifier::class.java.name)
    val hostModifier = if (index >= 0) call.arguments.getOrNull(index) as? Modifier ?: Modifier else Modifier
    // The host modifier is applied exactly once, by this box. Handing it to the original as well
    // would apply the container's padding twice and shift the whole column.
    Box(hostModifier.fillMaxSize()) {
        val arguments = ArrayList<Any?>(call.arguments.size + 3)
        arguments.addAll(call.arguments)
        if (index in arguments.indices) arguments[index] = Invisible
        arguments += currentComposer
        // Every argument is passed explicitly and none comes from a default, so all change flags
        // are set and the default mask stays empty.
        arguments += -1
        arguments += 0
        runCatching { original.invoke(null, *arguments.toTypedArray()) }
            // Drawing our layer anyway is safe here: the replacement consumed the host call, so a
            // failed invoke means nothing was drawn at all rather than two texts overlapping.
            .onFailure { DrawFailure.report(it, "宿主透传") }
        NativeLyricsContent(runtime, Modifier, true)
    }
}
