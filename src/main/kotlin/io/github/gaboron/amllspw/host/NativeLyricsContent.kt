package io.github.gaboron.amllspw.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.gaboron.amllspw.ui.InstrumentalPoetrySurface
import io.github.gaboron.amllspw.ui.LyricFooterContent
import io.github.gaboron.amllspw.ui.NativeLyricsSurface

/** Host adapter only: the drawing layer receives values and callbacks, never Workshop API objects. */
@Composable
internal fun NativeLyricsContent(runtime: AmllRuntime, modifier: Modifier, showSecondary: Boolean) {
    val presentation by runtime.presentation.collectAsState()
    val options by runtime.preferences.options.collectAsState()
    // The renderer's modifier belongs to this slot, not to a second full-page sibling.
    // No crossfade here on purpose: this slot already sits inside the host's own AnimatedContent.
    // A second transition made the host's size animation chase a moving target, and the page
    // background kept collapsing to a corner block until that animation gave up.
    Box(modifier.fillMaxSize()) {
        val poem = presentation.poem
        if (presentation.isInstrumental && poem != null) {
            if (options.instrumentalPoetry) {
                val footer = remember(options.footer, presentation.footer) {
                    if (options.footer) LyricFooterContent(contributions = presentation.footer) else LyricFooterContent()
                }
                InstrumentalPoetrySurface(poem, options, presentation.lyricColor, Modifier.fillMaxSize(), footer) {
                    runtime.nextPoem(presentation.trackKey)
                }
            }
        } else {
            // A departing track keeps its last playhead instead of jumping to zero during the fade.
            val lastPosition = remember(presentation.trackKey) { longArrayOf(0L) }
            NativeLyricsSurface(presentation, options.copy(background = false), Modifier.fillMaxSize(), {
                // The host's onTime callback freezes when its own lyric document is empty (e.g. raw TTML).
                lastPosition[0] = runtime.clock.now(presentation.trackKey)
                lastPosition[0]
            }, { runtime.seekTo(presentation.trackKey, it) }, showSecondary,
                { runtime.clock.playingState(presentation.trackKey) },
                { runtime.clock.seekGeneration })
        }
    }
}
