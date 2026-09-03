package com.dummysurfers.core.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.Align
import com.dummysurfers.core.gfx.Palette
import com.dummysurfers.core.gfx.TextureGen

/** Fonts + widget drawing primitives shared by all screens. */
class UiTheme {
    lateinit var fontHuge: BitmapFont   // 48 — title
    lateinit var fontLarge: BitmapFont  // 32 — score
    lateinit var fontMed: BitmapFont    // 24 — buttons
    lateinit var fontSmall: BitmapFont  // 16 — labels
    lateinit var fontTiny: BitmapFont   // 8 — fine print

    /** v5.4: true when freetype failed and the default engine font is in use. */
    var fontsFallback = false; private set

    private val layout = GlyphLayout()

    fun create() {
        // SS-style fonts: chunky comic display (Luckiest Guy) + rounded sport
        // body (Fugaz One), navy outline + soft shadow (see docs/DESIGN_BIBLE.md)
        //
        // v5.4 STARTUP IMMUNITY: gdx-freetype is a NATIVE library — on a device
        // with a broken/quirky freetype (or a font glyph edge case) generateFont
        // used to throw inside create() → the app closed instantly at launch,
        // every launch. Each font now falls back to the libgdx built-in engine
        // font (scaled); the game boots with plainer text instead of dying.
        var display: FreeTypeFontGenerator? = null
        var body: FreeTypeFontGenerator? = null
        try {
            display = FreeTypeFontGenerator(com.badlogic.gdx.Gdx.files.internal("fonts/LuckiestGuy-Regular.ttf"))
            body = FreeTypeFontGenerator(com.badlogic.gdx.Gdx.files.internal("fonts/FugazOne-Regular.ttf"))
        } catch (t: Throwable) {
            com.badlogic.gdx.Gdx.app.error("DS-Theme", "FreeType generator unavailable — default fonts", t)
            fontsFallback = true
        }
        fun param(size: Int, border: Int = 0, shadow: Int = 3): FreeTypeFontGenerator.FreeTypeFontParameter {
            val p = FreeTypeFontGenerator.FreeTypeFontParameter()
            p.size = size
            p.color = Color.WHITE
            p.borderWidth = border.toFloat()
            p.borderColor = Palette.UI_OUTLINE
            p.shadowOffsetX = shadow
            p.shadowOffsetY = shadow
            p.shadowColor = Color(0x24316b88.toInt())
            p.minFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear
            p.magFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear
            p.characters = FreeTypeFontGenerator.DEFAULT_CHARS + "←→↑↓×★•|/+-–—…!?,.%&()'\"«»"
            return p
        }
        fontHuge = genFont(display, param(52, border = 5, shadow = 4), 3.4f)
        fontLarge = genFont(display, param(34, border = 3), 2.2f)
        fontMed = genFont(display, param(24, border = 3), 1.6f)
        fontSmall = genFont(body, param(18, border = 2, shadow = 2), 1.2f)
        fontTiny = genFont(body, param(14, shadow = 2), 0.95f)
        try { display?.dispose() } catch (_: Throwable) {}
        try { body?.dispose() } catch (_: Throwable) {}
    }

    /** Generate one freetype font; on failure fall back to the engine font. */
    private fun genFont(g: FreeTypeFontGenerator?, p: FreeTypeFontGenerator.FreeTypeFontParameter, fallbackScale: Float): BitmapFont {
        if (g != null) {
            try { return g.generateFont(p) } catch (t: Throwable) {
                com.badlogic.gdx.Gdx.app.error("DS-Theme", "font size ${p.size} failed — default font fallback", t)
                fontsFallback = true
            }
        }
        return try {
            BitmapFont().apply { data.setScale(fallbackScale) }
        } catch (t: Throwable) {
            // Even the engine font failed (GL totally broken) — rethrow and let
            // the game's SafeMode take over.
            throw t
        }
    }

    /** Last-resort: rebuild ALL fonts from the engine default (retry path). */
    fun fallbackFonts() {
        fontsFallback = true
        val scales = floatArrayOf(3.4f, 2.2f, 1.6f, 1.2f, 0.95f)
        val olds = try { arrayOf(fontHuge, fontLarge, fontMed, fontSmall, fontTiny) } catch (_: Throwable) { null }
        val news = scales.map { s -> BitmapFont().apply { data.setScale(s) } }
        // only swap once every replacement exists — never leave lateinit unset
        fontHuge = news[0]; fontLarge = news[1]; fontMed = news[2]; fontSmall = news[3]; fontTiny = news[4]
        olds?.forEach { try { it.dispose() } catch (_: Throwable) {} }
    }

    fun dispose() {
        // v5.4: safe on partially-initialized themes (retry path)
        val fonts = try { arrayOf(fontHuge, fontLarge, fontMed, fontSmall, fontTiny) } catch (_: Throwable) { return }
        fonts.forEach { try { it.dispose() } catch (_: Throwable) {} }
    }

    // ── Text with baked shadow ─────────────────────────────────────────
    fun text(
        batch: SpriteBatch, font: BitmapFont, s: String, x: Float, y: Float,
        color: Color = Palette.UI_TEXT, align: Int = Align.left, targetWidth: Float = 0f
    ): GlyphLayout {
        font.setColor(color)
        return if (targetWidth > 0f) {
            font.draw(batch, s, x, y, targetWidth, align, true)
        } else {
            font.draw(batch, s, x, y, targetWidth, align, false)
        }
    }

    fun textWidth(font: BitmapFont, s: String): Float {
        layout.setText(font, s)
        return layout.width
    }

    private val npColor = Color()

    fun panel(batch: SpriteBatch, x: Float, y: Float, w: Float, h: Float, color: Color = Palette.UI_PANEL, alpha: Float = 1f) {
        val np = TextureGen.panelNine
        np.setColor(npColor.set(color.r, color.g, color.b, color.a * alpha))
        np.draw(batch, x, y, w, h)
    }

    fun button(batch: SpriteBatch, x: Float, y: Float, w: Float, h: Float, color: Color, pressed: Boolean) {
        val np = TextureGen.buttonNine
        val scale = if (pressed) 0.955f else 1f
        val cx = x + w / 2f
        val cy = y + h / 2f
        val sw = w * scale
        val sh = h * scale
        np.setColor(npColor.set(color.r, color.g, color.b, color.a))
        np.draw(batch, cx - sw / 2f, cy - sh / 2f, sw, sh)
        np.setColor(npColor.set(1f, 1f, 1f, 1f))
    }

    /**
     * v5.3 SS round jelly button (pause / gear / menu floaters). Pressed =
     * squash toward the finger like the real game.
     */
    fun circleButton(batch: SpriteBatch, x: Float, y: Float, size: Float, color: Color, pressed: Boolean) {
        val np = TextureGen.circleNine
        val scale = if (pressed) 0.92f else 1f
        val cx = x + size / 2f
        val cy = y + size / 2f
        val s = size * scale
        np.setColor(npColor.set(color.r, color.g, color.b, color.a))
        np.draw(batch, cx - s / 2f, cy - s / 2f, s, s)
        np.setColor(npColor.set(1f, 1f, 1f, 1f))
    }

    /**
     * v5.3 soft drop shadow under cards/buttons — the dark translucent
     * panel ninepatch, nudged down. SS floats every card on one of these;
     * without it cards look pasted onto the world.
     */
    fun cardShadow(batch: SpriteBatch, x: Float, y: Float, w: Float, h: Float) {
        val np = TextureGen.panelNine
        np.setColor(npColor.set(0.05f, 0.06f, 0.14f, 0.38f))
        np.draw(batch, x - 4f, y - 14f, w + 8f, h + 12f)
        np.setColor(npColor.set(1f, 1f, 1f, 1f))
    }

    /** v5.3 white play triangle glyph, tinted. */
    fun playIcon(batch: SpriteBatch, x: Float, y: Float, size: Float, color: Color = Color.WHITE) {
        batch.setColor(color.r, color.g, color.b, color.a)
        batch.draw(TextureGen.play, x, y, size, size)
        batch.setColor(1f, 1f, 1f, 1f)
    }

    fun progressBar(sr: ShapeRenderer, x: Float, y: Float, w: Float, h: Float, t: Float, color: Color) {
        sr.begin(ShapeRenderer.ShapeType.Filled)
        sr.setColor(1f, 1f, 1f, 0.95f)
        sr.rect(x - 2f, y - 2f, w + 4f, h + 4f)
        sr.setColor(Palette.UI_PANEL_DEEP)
        sr.rect(x, y, w, h)
        sr.setColor(color)
        sr.rect(x, y, w * t.coerceIn(0f, 1f), h)
        sr.end()
    }

    // ── Batch-safe primitives (v4.1) ───────────────────────────────────
    // The HUD draws between the game's batch.begin()/end(); ShapeRenderer
    // there gets OVERPAINTED when the batch flushes at end() — so UI shapes
    // must go through the batch (white/disc textures tinted per draw).

    fun rect(batch: SpriteBatch, x: Float, y: Float, w: Float, h: Float, color: Color, alpha: Float = 1f) {
        batch.setColor(color.r, color.g, color.b, color.a * alpha)
        batch.draw(TextureGen.white, x, y, w, h)
        batch.setColor(1f, 1f, 1f, 1f)
    }

    fun disc(batch: SpriteBatch, cx: Float, cy: Float, r: Float, color: Color, alpha: Float = 1f) {
        batch.setColor(color.r, color.g, color.b, color.a * alpha)
        batch.draw(TextureGen.disc, cx - r, cy - r, r * 2f, r * 2f)
        batch.setColor(1f, 1f, 1f, 1f)
    }

    /** Rounded pill via the button ninepatch — used for switch tracks, meters. */
    fun pill(batch: SpriteBatch, x: Float, y: Float, w: Float, h: Float, color: Color, alpha: Float = 1f) {
        val np = TextureGen.panelNine
        np.setColor(npColor.set(color.r, color.g, color.b, color.a * alpha))
        np.draw(batch, x, y, w, h)
        np.setColor(npColor.set(1f, 1f, 1f, 1f))
    }

    /** Batch progress bar (mission meters, board timer). */
    fun bar(batch: SpriteBatch, x: Float, y: Float, w: Float, h: Float, t: Float, color: Color) {
        pill(batch, x - 3f, y - 3f, w + 6f, h + 6f, Palette.UI_PANEL_DEEP)
        if (t > 0f) rect(batch, x, y, w * t.coerceIn(0f, 1f), h, color)
    }

    fun coinIcon(batch: SpriteBatch, x: Float, y: Float, size: Float) {
        // v5.2: defensive white tint — this is drawn right after ninepatch
        // slabs whose setColor() leak tints the coin into a dark blob
        batch.setColor(1f, 1f, 1f, 1f)
        batch.draw(TextureGen.coinFrames[0], x, y, size, size)
    }
}
