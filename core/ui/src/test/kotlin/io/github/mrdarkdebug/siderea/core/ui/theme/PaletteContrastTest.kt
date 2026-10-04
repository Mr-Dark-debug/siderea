package io.github.mrdarkdebug.siderea.core.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteContrastTest {
    private val palettes = mapOf("amber" to NightAmberPalette, "red" to NightRedPalette)

    @Test
    fun `contrast of black on white is 21`() {
        assertEquals(21.0, Contrast.ratio(Color.Black, Color.White), 0.01)
    }

    @Test
    fun `contrast is symmetric`() {
        val a = Color(0xFF123456)
        val b = Color(0xFFABCDEF)
        assertEquals(Contrast.ratio(a, b), Contrast.ratio(b, a), 1e-9)
    }

    @Test
    fun `body text meets AA on every surface in every palette`() {
        palettes.forEach { (name, p) ->
            val surfaces = listOf(p.background, p.surface, p.surfaceRaised)
            surfaces.forEach { surface ->
                assertAtLeast(Contrast.AA_TEXT, p.onBackground, surface, "$name onBackground on $surface")
                assertAtLeast(Contrast.AA_TEXT, p.onSurfaceMuted, surface, "$name muted on $surface")
                assertAtLeast(Contrast.AA_TEXT, p.accent, surface, "$name accent on $surface")
                assertAtLeast(Contrast.AA_TEXT, p.danger, surface, "$name danger on $surface")
            }
        }
    }

    @Test
    fun `text on accent fills meets AA`() {
        palettes.forEach { (name, p) ->
            assertAtLeast(Contrast.AA_TEXT, p.onAccent, p.accent, "$name onAccent on accent")
            assertAtLeast(Contrast.AA_TEXT, p.accent, p.accentContainer, "$name accent on accentContainer")
        }
    }

    @Test
    fun `red palette contains no green or blue dominance`() {
        val p = NightRedPalette
        listOf(p.onBackground, p.onSurfaceMuted, p.accent, p.danger).forEach { c ->
            assertTrue("red palette colour must be red-dominant: $c", c.red > c.green && c.red > c.blue)
            // Keep the blue channel low so the screen emits as little short-wavelength light as possible.
            assertTrue("red palette colour has too much blue: $c", c.blue <= 0.6f)
        }
    }

    @Test
    fun `background is true black in every palette`() {
        palettes.values.forEach { assertEquals(Color.Black, it.background) }
    }

    private fun assertAtLeast(
        min: Double,
        fg: Color,
        bg: Color,
        what: String,
    ) {
        val ratio = Contrast.ratio(fg, bg)
        assertTrue("$what has contrast %.2f, needs %.1f".format(ratio, min), ratio >= min)
    }
}
