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

    private val layout = GlyphLayout()

    fun create() {
        // SS-style fonts: chunky comic display (Luckiest Guy) + rounded sport
        // body (Fugaz One), navy outline + soft shadow (see docs/DESIGN_BIBLE.md)
        val display = FreeTypeFontGenerator(com.badlogic.gdx.Gdx.files.internal("fonts/LuckiestGuy-Regular.ttf"))
        val body = FreeTypeFontGenerator(com.badlogic.gdx.Gdx.files.internal("fonts/FugazOne-Regular.ttf"))
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
        fontHuge = display.generateFont(param(52, border = 5, shadow = 4))
        fontLarge = display.generateFont(param(34, border = 3))
        fontMed = display.generateFont(param(24, border = 3))
        fontSmall = body.generateFont(param(18, border = 2, shadow = 2))
        fontTiny = body.generateFont(param(14, shadow = 2))
        display.dispose()
        body.dispose()
    }

    fun dispose() {
        fontHuge.dispose(); fontLarge.dispose(); fontMed.dispose()
        fontSmall.dispose(); fontTiny.dispose()
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

    fun coinIcon(batch: SpriteBatch, x: Float, y: Float, size: Float) {
        batch.draw(TextureGen.coinFrames[0], x, y, size, size)
    }
}
