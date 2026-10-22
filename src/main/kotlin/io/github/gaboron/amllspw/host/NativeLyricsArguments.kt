package io.github.gaboron.amllspw.host

import androidx.compose.ui.Modifier

/** Only layout is reused; lyric-dependent host callbacks are not a playback clock. */
internal data class NativeLyricsArguments(val modifier: Modifier) {
    companion object {
        fun read(arguments: List<Any?>, parameters: List<String>): NativeLyricsArguments {
            val index = parameters.indices.single { parameters[it] == Modifier::class.java.name }
            return NativeLyricsArguments(arguments[index] as Modifier)
        }
    }
}
