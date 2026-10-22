package io.github.gaboron.amllspw.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import io.github.gaboron.amllspw.host.NativeLyricsArguments
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class NativeLyricsArgumentsTest {
    @Test
    fun takesTheRendererModifierFromItsSignature() {
        val modifier = Modifier.fillMaxSize()
        for (index in 0..7) {
            val parameters = MutableList(8) { "java.lang.Object" }
            val arguments = MutableList<Any?>(8) { null }
            parameters[index] = Modifier::class.java.name
            arguments[index] = modifier
            assertSame(modifier, NativeLyricsArguments.read(arguments, parameters).modifier)
        }
    }

    @Test
    fun rejectsMissingOrAmbiguousModifierSlots() {
        assertFailsWith<NoSuchElementException> {
            NativeLyricsArguments.read(emptyList(), emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            NativeLyricsArguments.read(listOf(Modifier, Modifier), List(2) { Modifier::class.java.name })
        }
    }
}
