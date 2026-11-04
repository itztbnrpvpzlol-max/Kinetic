package com.szkinetic

data class EffectEntry(val name: String, val category: String, val description: String)

data class EffectCategory(val name: String, val effects: List<EffectEntry>)

data class RenderEffect(val id: Int, val name: String, val category: String, val description: String)

object RenderEffects {
    // Only list effects that are connected to the live renderer.
    val all = listOf(
        RenderEffect(1, "Brightness", "Color Correction", "Adjusts overall luminance."),
        RenderEffect(2, "Contrast", "Color Correction", "Expands or compresses tonal contrast."),
        RenderEffect(3, "Saturation", "Color Correction", "Controls color intensity."),
        RenderEffect(4, "Invert", "Stylize", "Inverts the layer's RGB channels."),
        RenderEffect(5, "Grayscale", "Color Correction", "Mixes the image toward monochrome."),
        RenderEffect(6, "Sepia", "Stylize", "Applies a warm sepia treatment."),
        RenderEffect(7, "Posterize", "Stylize", "Reduces the number of tonal steps."),
        RenderEffect(8, "Vignette", "Stylize", "Darkens the edges of the layer."),
        RenderEffect(9, "Threshold", "Stylize", "Converts luminance to black or white."),
        RenderEffect(10, "Temperature", "Color Correction", "Shifts the image warmer or cooler."),
        RenderEffect(11, "Warm Tint", "Color Correction", "Adds a warm color tint."),
        RenderEffect(12, "Pixelate", "Stylize", "Creates a blocky pixel treatment."),
        RenderEffect(13, "Hue Shift", "Color Correction", "Rotates hue while preserving luminance."),
        RenderEffect(14, "Colorize", "Color Correction", "Blends the layer toward a warm color treatment."),
        RenderEffect(15, "Scanlines", "Stylize", "Adds animated display scanlines."),
        RenderEffect(16, "Chromatic Aberration", "Distortion & Warp", "Separates red and blue channels.")
    )

    fun byName(name: String): RenderEffect? = all.firstOrNull { it.name.equals(name, true) }
    fun byId(id: Int): RenderEffect? = all.firstOrNull { it.id == id }
}

object EffectCatalog {
    // The browser deliberately contains only implemented effects; no fake catalogue entries.
    val categories: List<EffectCategory> = RenderEffects.all
        .groupBy { it.category }
        .map { (name, effects) ->
            EffectCategory(name, effects.map { EffectEntry(it.name, it.category, it.description) })
        }

    val all: List<EffectEntry> = categories.flatMap { it.effects }
}
