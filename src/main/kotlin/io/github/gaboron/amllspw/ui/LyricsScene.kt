package io.github.gaboron.amllspw.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/** Keeps the outgoing document/poem alive while switching between the two native surfaces. */
@Composable
@OptIn(ExperimentalAnimationApi::class)
internal fun LyricsScene(presentation: LyricsPresentation, options: RenderOptions, modifier: Modifier,
    onNextPoem: (String) -> Unit, lyrics: @Composable (LyricsPresentation, RenderOptions) -> Unit) {
    val transition = updateTransition(presentation, label = "lyrics scene")
    transition.Crossfade(modifier = modifier, animationSpec = tween(if (options.reducedMotion) 0 else 320),
        contentKey = {
            it.trackKey to when {
                it.loading -> "loading"
                it.isInstrumental && it.poem != null -> "instrumental"
                else -> "lyrics"
            }
        }) { scene ->
        // Two surfaces share the screen while the host usually animates its own page too.
        // The departing one drops its per-line blur layers: it is fading to zero anyway, so the
        // saved offscreen layers cannot be missed, and the incoming surface keeps its exact look.
        val sceneOptions = if (options.reducedMotion || scene == transition.targetState) options else options.copy(blur = false)
        val poem = scene.poem
        if (scene.isInstrumental && poem != null) {
            if (!options.instrumentalPoetry) {
                // Switched off: keep the slot empty instead of leaving the previous poem or placeholder text.
                Box(Modifier.fillMaxSize())
            } else {
                // Instrumental pages have no lyric source to cite; only third-party contributions remain.
                val footer = remember(options.footer, scene.footer) {
                    if (options.footer) LyricFooterContent(contributions = scene.footer) else LyricFooterContent()
                }
                InstrumentalPoetrySurface(poem, options, scene.lyricColor, Modifier.fillMaxSize(), footer) { onNextPoem(scene.trackKey) }
            }
        } else {
            lyrics(scene, sceneOptions)
        }
    }
}
