@file:OptIn(com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi::class)
package io.github.gaboron.amllspw.host

import com.xuncorp.spw.workshop.api.WorkshopApi
import com.xuncorp.spw.workshop.api.config.ConfigHelper
import io.github.gaboron.amllspw.ui.LyricTypography
import io.github.gaboron.amllspw.ui.RenderOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.function.Consumer
import kotlin.math.roundToInt

internal class AmllPreferences : AutoCloseable {
    private val manager = WorkshopApi.manager.createConfigManager()
    private val mutable = MutableStateFlow(decode(manager.getConfig(FILE)))
    val options = mutable.asStateFlow()
    private val listener = Consumer<ConfigHelper> { mutable.value = decode(it) }
    init {
        migrateWeight(manager.getConfig(FILE))
        manager.addConfigChangeListener(FILE, listener)
    }
    override fun close() {
        manager.removeConfigChangeListener(listener)
    }
    private fun decode(c: ConfigHelper) = RenderOptions(
        blur = c.get("blur", true), scale = c.get("scale", true), wordLift = c.get("word_lift", true),
        glow = c.get("glow", true),
        spring = c.get("spring", true), hidePassed = c.get("hide_passed", false),
        footer = c.get("show_footer", true),
        showSource = c.get("show_source", false),
        romanization = c.get("romanization", true), reducedMotion = c.get("reduced_motion", false),
        coloredLyrics = c.get("colored_lyrics", false),
        typography = LyricTypography.fromPreferences(weight(c), c.get("font_size", "auto")),
    )
    // The weight list stores integer strings ("900"), while slider-era configs stored floats (900.0).
    private fun weight(c: ConfigHelper): Int {
        val value = when (val raw = rawWeight(c)) {
            is Number -> raw.toFloat()
            is String -> raw.toFloatOrNull()
            else -> null
        }
        return value?.roundToInt()?.let(LyricTypography::normalizeWeight) ?: LyricTypography.DEFAULT_WEIGHT
    }
    // A mismatched type makes ConfigHelper return the fallback, so each attempt uses an out-of-range sentinel.
    private fun rawWeight(c: ConfigHelper): Any? {
        val decimal = runCatching { c.get("font_weight", MISSING.toDouble()) }.getOrNull()
        if (decimal != null && decimal != MISSING.toDouble()) return decimal
        val single = runCatching { c.get("font_weight", MISSING.toFloat()) }.getOrNull()
        if (single != null && single != MISSING.toFloat()) return single
        val text = runCatching { c.get("font_weight", MISSING.toString()) }.getOrNull()
        if (text != null && text != MISSING.toString()) return text
        return null
    }
    // Rewrite slider-era floats and off-step values so the list opens on a valid entry.
    private fun migrateWeight(c: ConfigHelper) {
        val raw = rawWeight(c) ?: return
        val normalized = weight(c).toString()
        if (raw is String && raw == normalized) return
        runCatching {
            c.set("font_weight", normalized)
            c.save()
        }
    }
    companion object {
        private const val FILE = "amll.json"
        private const val MISSING = -1
    }
}
