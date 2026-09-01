package com.dummysurfers.core.gfx

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.dummysurfers.core.entities.CharacterDef
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.random.Random

/**
 * Shared palette — Subway-Surfers-style bright warm daylight.
 * Vivid cyan sky, terracotta ground, vibrant trains, chunky periwinkle UI.
 * (See docs/DESIGN_BIBLE.md.)
 */
object Palette {
    // ── Sky & atmosphere ──
    val SKY_TOP = Color(0x3fb8f5ff.toInt())      // vivid cyan zenith
    val SKY_MID = Color(0x8fd8f8ff.toInt())      // light azure
    val SKY_LOW = Color(0xffe9c2ff.toInt())      // warm cream horizon
    val FOG = Color(0xffe4bcff.toInt())          // light warm haze

    // ── Ground ──
    val GROUND = Color(0xc97b5eff.toInt())       // terracotta ballast
    val GROUND_FAR = Color(0xd98d6aff.toInt())   // lighter far ballast
    val GRASS = Color(0x5fbf4aff.toInt())        // vivid trackside grass
    val PATH_CREAM = Color(0xf2d9a7ff.toInt())   // cream path patches
    val PATH_ORANGE = Color(0xe8a25cff.toInt())  // orange path patches
    val SLEEPER = Color(0x6b4a36ff.toInt())      // warm brown ties
    val RAIL = Color(0xe8e4daff.toInt())         // shiny silver rail head
    val RAIL_SIDE = Color(0xb4553aff.toInt())    // rust-orange rail base

    // ── Accents ──
    val GOLD = Color(0xffc93cff.toInt())
    val GOLD_DEEP = Color(0xe09b12ff.toInt())
    val HAZARD_YELLOW = Color(0xffc93cff.toInt())
    val HAZARD_BLACK = Color(0x2b2622ff.toInt())
    val CONTOUR_TEAL = Color(0x37b8a8ff.toInt())
    val CONTAINER_RED = Color(0xc4553eff.toInt())

    // ── UI (Subway Surfers chunky cartoon) ──
    val UI_PANEL = Color(0x7b84d6ff.toInt())     // periwinkle card
    val UI_PANEL_LIGHT = Color(0x9aa3e8ff.toInt()) // lighter card
    val UI_PANEL_DEEP = Color(0x4a529eff.toInt())  // inner slot
    val UI_NAVY = Color(0x2a3057ff.toInt())      // currency pills / tabs
    val UI_OUTLINE = Color(0x24316bff.toInt())   // text outline navy
    val UI_GOLD_BTN = Color(0xffc93cff.toInt())  // primary RUN button
    val UI_GOLD_BTN_DEEP = Color(0xd89a14ff.toInt())
    val UI_ORANGE = Color(0xff5a3cff.toInt())    // pause button
    val UI_GREEN = Color(0x3dbb5aff.toInt())     // secondary/confirm
    val UI_ACCENT = Color(0xffc93cff.toInt())    // legacy alias = gold
    val UI_ACCENT2 = Color(0x37b8a8ff.toInt())   // teal accent
    val UI_TEXT = Color(0xffffffffff.toInt())
    val UI_MUTED = Color(0xc9cff2ff.toInt())     // light periwinkle
    val DANGER = Color(0xef4444ff.toInt())

    // body/shade/band — bright SS metro + graffiti freight
    val TRAIN_LIVERIES = arrayOf(
        intArrayOf(0x3e7bc0ff.toInt(), 0x2c5e96ff.toInt(), 0xffffffff.toInt()), // blue metro, white band
        intArrayOf(0xf2a63bff.toInt(), 0xd8841fff.toInt(), 0xfff3d6ff.toInt()), // orange graffiti freight
        intArrayOf(0x43b45cff.toInt(), 0x2e8a44ff.toInt(), 0xeaf7dcff.toInt()), // green metro
        intArrayOf(0xd94a38ff.toInt(), 0xa83326ff.toInt(), 0xffe2c8ff.toInt()), // red express
        intArrayOf(0xf7d23eff.toInt(), 0xdbae1dff.toInt(), 0x3a3f6bff.toInt()), // yellow metro, navy band
        intArrayOf(0x8a55c9ff.toInt(), 0x6a3da3ff.toInt(), 0xf2e2ffff.toInt())  // violet graffiti
    )
    val TRAIN_ROOF = Color(0x9aa0a8ff.toInt())
    val TRAIN_FRONT = Color(0xf7d23eff.toInt())

    val BUILDING_COLORS = intArrayOf(
        0xe8b27dff.toInt(), 0xd9985fff.toInt(), 0xc96b4aff.toInt(), 0xb85a4aff.toInt(),
        0x9fc5c0ff.toInt(), 0x8fb6d9ff.toInt(), 0xe8d5a8ff.toInt(), 0xc78a6aff.toInt()
    )
}

/**
 * All sprites are generated procedurally via Pixmap at boot — zero external
 * art assets, fully original (spec section 34).
 */
object TextureGen {
    lateinit var glow: Texture
    lateinit var softShadow: Texture
    lateinit var sky: Texture
    lateinit var fog: Texture
    lateinit var cloudA: Texture
    lateinit var cloudB: Texture
    lateinit var skylineFar: Texture
    lateinit var skylineNear: Texture
    lateinit var vignette: Texture
    lateinit var rainbowBurst: Texture // conic rainbow for NEW BEST celebration
    lateinit var coinFrames: Array<Texture>
    lateinit var powerIcons: Array<Texture> // magnet,x2,shield,boost,superjump
    lateinit var panelNine: NinePatch
    lateinit var buttonNine: NinePatch
    lateinit var white: Texture
    lateinit var previews: Array<Texture> // character card portraits

    fun generate() {
        white = solid(4, 4, Color.WHITE)
        glow = radial(128, Color(1f, 1f, 1f, 1f), 0f)
        softShadow = radial(128, Color(0f, 0f, 0f, 0.55f), 0.25f)
        sky = verticalGradient(8, 512, Palette.SKY_TOP, Palette.SKY_MID, Palette.SKY_LOW)
        fog = verticalGradientFade(8, 256, Palette.FOG)
        cloudA = cloud(260, 90, 42L)
        cloudB = cloud(200, 70, 77L)
        // SS-style distant city: soft blue-violet haze silhouettes
        skylineFar = skyline(1024, 190, 5L, dark = 0xaebbe8, alpha = 0.8f, dense = false)
        skylineNear = skyline(1024, 240, 11L, dark = 0x8b9cdd, alpha = 0.9f, dense = true)
        vignette = radial(256, Color(0f, 0f, 0f, 0.5f), 0.72f)
        rainbowBurst = burst(512)
        coinFrames = Array(10) { coin(72, it, 10) }
        powerIcons = arrayOf(magnetIcon(), starIcon(), shieldIcon(), boltIcon(), rocketIcon())
        previews = Array(CharacterDef.ALL.size) { characterPreview(CharacterDef.ALL[it]) }
        panelNine = roundedNine(64, 18, Color(1f, 1f, 1f, 1f), border = false)
        buttonNine = roundedNine(64, 18, Color(1f, 1f, 1f, 1f), border = true)
    }

    fun dispose() {
        listOf(glow, softShadow, sky, fog, cloudA, cloudB, skylineFar, skylineNear, vignette, rainbowBurst, white).forEach { it.dispose() }
        coinFrames.forEach { it.dispose() }
        powerIcons.forEach { it.dispose() }
        previews.forEach { it.dispose() }
    }

    private fun solid(w: Int, h: Int, c: Color): Texture {
        val p = Pixmap(w, h, Pixmap.Format.RGBA8888)
        p.setColor(c); p.fill()
        val t = Texture(p); p.dispose(); return t
    }

    private fun radial(size: Int, c: Color, coreCut: Float): Texture {
        val p = Pixmap(size, size, Pixmap.Format.RGBA8888)
        val half = size / 2f
        for (y in 0 until size) for (x in 0 until size) {
            val dx = (x - half) / half
            val dy = (y - half) / half
            val d = kotlin.math.sqrt(dx * dx + dy * dy)
            val a = if (d <= coreCut) 0f else (1f - (d - coreCut) / (1f - coreCut)).coerceIn(0f, 1f)
            val aa = a * a * (3f - 2f * a)
            p.setColor(c.r, c.g, c.b, c.a * aa)
            p.drawPixel(x, y)
        }
        val t = Texture(p); p.dispose(); return t
    }

    private fun verticalGradient(w: Int, h: Int, vararg stops: Color): Texture {
        val p = Pixmap(w, h, Pixmap.Format.RGBA8888)
        val seg = (stops.size - 1).coerceAtLeast(1)
        for (y in 0 until h) {
            val t = y / (h - 1f) * seg
            val i = min(seg - 1, t.toInt())
            val f = t - i
            val a = stops[i]; val b = stops[i + 1]
            p.setColor(a.r + (b.r - a.r) * f, a.g + (b.g - a.g) * f, a.b + (b.b - a.b) * f, 1f)
            p.drawLine(0, y, w - 1, y)
        }
        val tex = Texture(p); p.dispose(); return tex
    }

    /** Fog: opaque at bottom → transparent at top (drawn near horizon). */
    private fun verticalGradientFade(w: Int, h: Int, c: Color): Texture {
        val p = Pixmap(w, h, Pixmap.Format.RGBA8888)
        for (y in 0 until h) {
            val t = y / (h - 1f)
            val a = (1f - t) * (1f - t)
            p.setColor(c.r, c.g, c.b, a)
            p.drawLine(0, y, w - 1, y)
        }
        val tex = Texture(p); p.dispose(); return tex
    }

    private fun cloud(w: Int, h: Int, seed: Long): Texture {
        val p = Pixmap(w, h, Pixmap.Format.RGBA8888)
        val rng = Random(seed)
        p.setColor(1f, 1f, 1f, 0.9f)
        val puffs = 5 + rng.nextInt(3)
        for (i in 0 until puffs) {
            val cx = w * (0.18f + 0.64f * i / (puffs - 1f)) + rng.nextInt(20) - 10
            val cy = h * (0.45f + rng.nextFloat() * 0.18f)
            val r = h * (0.22f + rng.nextFloat() * 0.24f)
            p.fillCircle(cx.toInt(), (h - cy).toInt(), r.toInt())
        }
        val t = Texture(p); p.dispose(); return t
    }

    /** City silhouette strip, wraps horizontally for parallax scrolling. */
    private fun skyline(w: Int, h: Int, seed: Long, dark: Int, alpha: Float, dense: Boolean): Texture {
        val p = Pixmap(w, h, Pixmap.Format.RGBA8888)
        val c = Color(dark).apply { this.a = alpha }
        p.setColor(c)
        val rng = Random(seed)
        var x = 0
        while (x < w) {
            val bw = if (dense) 26 + rng.nextInt(40) else 40 + rng.nextInt(80)
            val bh = (if (dense) 0.35f else 0.2f) + rng.nextFloat() * (if (dense) 0.6f else 0.65f)
            val top = (h - bh * h).toInt()
            p.fillRectangle(x, top, min(bw, w - x), h - top)
            // antenna on some buildings
            if (rng.nextFloat() < 0.3f) p.fillRectangle(x + bw / 2, top - 14, 2, 14)
            // water towers / blocks
            if (rng.nextFloat() < 0.25f) p.fillRectangle(x + bw / 4, top - 8, bw / 3, 8)
            x += bw + rng.nextInt(12)
        }
        val t = Texture(p); p.dispose(); return t
    }

    /** Spinning gold coin, frame f of total (perspective squeeze). */
    private fun coin(size: Int, f: Int, total: Int): Texture {
        val p = Pixmap(size, size, Pixmap.Format.RGBA8888)
        val cx = size / 2f
        val cy = size / 2f
        val r = size * 0.42f
        val sx = abs(cos(2.0 * PI * f / total)).toFloat().coerceIn(0.12f, 1f)
        // rim
        p.setColor(Palette.GOLD_DEEP)
        fillEllipse(p, cx, cy, r * sx, r)
        // face
        val face = if (sx > 0.35f) Palette.GOLD else Palette.GOLD_DEEP
        p.setColor(face)
        fillEllipse(p, cx, cy, r * sx * 0.82f, r * 0.88f)
        // embossed filled star (front-facing frames only) — SS gold-coin look
        if (sx > 0.5f) {
            p.setColor(Palette.GOLD_DEEP)
            val sr2 = r * 0.46f
            val pts = ArrayList<Pair<Int, Int>>()
            for (i in 0 until 10) {
                val ang = (i * 36 - 90) * PI.toFloat() / 180f
                val rr = if (i % 2 == 0) sr2 else sr2 * 0.42f
                pts.add((cx + cos(ang) * rr * sx).toInt() to (cy + kotlin.math.sin(ang) * rr).toInt())
            }
            for (i in 1 until pts.size - 1) {
                p.fillTriangle(pts[0].first, pts[0].second, pts[i].first, pts[i].second, pts[i + 1].first, pts[i + 1].second)
            }
        }
        // top highlight
        if (sx > 0.3f) {
            p.setColor(1f, 1f, 0.85f, 0.85f)
            fillEllipse(p, cx - r * sx * 0.28f, cy - r * 0.4f, r * sx * 0.22f, r * 0.14f)
        }
        val t = Texture(p); p.dispose(); return t
    }

    private fun fillEllipse(p: Pixmap, cx: Float, cy: Float, rx: Float, ry: Float) {
        val steps = 26
        for (i in 0 until steps) {
            val a1 = 2.0 * PI * i / steps
            val a2 = 2.0 * PI * (i + 1) / steps
            p.fillTriangle(
                cx.toInt(), cy.toInt(),
                (cx + cos(a1) * rx).toInt(), (cy + kotlin.math.sin(a1) * ry).toInt(),
                (cx + cos(a2) * rx).toInt(), (cy + kotlin.math.sin(a2) * ry).toInt()
            )
        }
    }

    private fun iconBase(size: Int): Pixmap {
        val p = Pixmap(size, size, Pixmap.Format.RGBA8888)
        return p
    }

    private fun magnetIcon(): Texture {
        val s = 96
        val p = iconBase(s)
        val red = Color(0xef4444ff.toInt())
        // U-shaped magnet
        p.setColor(red)
        for (a in 0 until 360) {
            val ang = a * PI.toFloat() / 180f
            if (ang < PI * 0.9f || ang > PI * 2.1f) continue
            val x = s / 2f + cos(ang) * s * 0.28f
            val y = s * 0.42f + kotlin.math.sin(ang) * s * 0.28f
            p.fillCircle(x.toInt(), y.toInt(), s / 10)
        }
        p.fillRectangle((s * 0.22f).toInt(), (s * 0.42f).toInt(), (s * 0.14f).toInt(), (s * 0.34f).toInt())
        p.fillRectangle((s * 0.64f).toInt(), (s * 0.42f).toInt(), (s * 0.14f).toInt(), (s * 0.34f).toInt())
        p.setColor(Color.WHITE)
        p.fillRectangle((s * 0.22f).toInt(), (s * 0.68f).toInt(), (s * 0.14f).toInt(), (s * 0.08f).toInt())
        p.fillRectangle((s * 0.64f).toInt(), (s * 0.68f).toInt(), (s * 0.14f).toInt(), (s * 0.08f).toInt())
        return tex(p)
    }

    private fun starIcon(): Texture {
        val s = 96
        val p = iconBase(s)
        p.setColor(Palette.GOLD)
        val cx = s / 2f; val cy = s / 2f
        val pts = ArrayList<Pair<Int, Int>>()
        for (i in 0 until 10) {
            val ang = (i * 36 - 90) * PI.toFloat() / 180f
            val r = if (i % 2 == 0) s * 0.38f else s * 0.16f
            pts.add((cx + cos(ang) * r).toInt() to (cy + kotlin.math.sin(ang) * r).toInt())
        }
        for (i in 1 until pts.size - 1) p.fillTriangle(pts[0].first, pts[0].second, pts[i].first, pts[i].second, pts[i + 1].first, pts[i + 1].second)
        p.setColor(Color(1f, 1f, 0.85f, 0.9f))
        p.fillCircle((cx - s * 0.08f).toInt(), (cy - s * 0.1f).toInt(), (s * 0.05f).toInt())
        return tex(p)
    }

    private fun shieldIcon(): Texture {
        val s = 96
        val p = iconBase(s)
        val teal = Color(0x2dd4bfff.toInt())
        p.setColor(teal)
        val cx = s / 2f
        p.fillTriangle((cx).toInt(), (s * 0.1f).toInt(), (s * 0.18f).toInt(), (s * 0.25f).toInt(), (s * 0.82f).toInt(), (s * 0.25f).toInt())
        p.fillRectangle((s * 0.18f).toInt(), (s * 0.25f).toInt(), (s * 0.64f).toInt(), (s * 0.4f).toInt())
        // taper bottom
        var i = 0
        while (i < 12) {
            val y = s * 0.65f + i * (s * 0.25f / 12f)
            val half = s * 0.32f * (1f - i / 12f)
            p.fillRectangle((cx - half).toInt(), y.toInt(), (half * 2f).toInt().coerceAtLeast(1), (s * 0.25f / 12f + 1).toInt())
            i++
        }
        p.setColor(Color(1f, 1f, 1f, 0.75f))
        p.fillRectangle((s * 0.26f).toInt(), (s * 0.3f).toInt(), (s * 0.08f).toInt(), (s * 0.32f).toInt())
        return tex(p)
    }

    private fun boltIcon(): Texture {
        val s = 96
        val p = iconBase(s)
        p.setColor(Color(0xa3e635ff.toInt()))
        val poly = arrayOf(
            (s * 0.58f).toInt() to (s * 0.08f).toInt(),
            (s * 0.26f).toInt() to (s * 0.55f).toInt(),
            (s * 0.47f).toInt() to (s * 0.55f).toInt(),
            (s * 0.38f).toInt() to (s * 0.92f).toInt(),
            (s * 0.74f).toInt() to (s * 0.42f).toInt(),
            (s * 0.52f).toInt() to (s * 0.42f).toInt(),
            (s * 0.68f).toInt() to (s * 0.08f).toInt()
        )
        for (i in 1 until poly.size - 1) p.fillTriangle(poly[0].first, poly[0].second, poly[i].first, poly[i].second, poly[i + 1].first, poly[i + 1].second)
        return tex(p)
    }

    private fun rocketIcon(): Texture {
        val s = 96
        val p = iconBase(s)
        val orange = Color(0xf97316ff.toInt())
        val cx = s / 2f
        // body
        p.setColor(orange)
        fillEllipse(p, cx, s * 0.42f, s * 0.16f, s * 0.3f)
        // window
        p.setColor(Color(0x9adcf0ff.toInt()))
        p.fillCircle(cx.toInt(), (s * 0.38f).toInt(), (s * 0.09f).toInt())
        // fins
        p.setColor(Color(0xc2410cff.toInt()))
        p.fillTriangle((s * 0.3f).toInt(), (s * 0.5f).toInt(), (s * 0.14f).toInt(), (s * 0.78f).toInt(), (s * 0.34f).toInt(), (s * 0.72f).toInt())
        p.fillTriangle((s * 0.7f).toInt(), (s * 0.5f).toInt(), (s * 0.86f).toInt(), (s * 0.78f).toInt(), (s * 0.66f).toInt(), (s * 0.72f).toInt())
        // flame
        p.setColor(Palette.GOLD)
        p.fillTriangle((s * 0.36f).toInt(), (s * 0.74f).toInt(), (s * 0.64f).toInt(), (s * 0.74f).toInt(), cx.toInt(), (s * 0.98f).toInt())
        return tex(p)
    }

    private fun roundedNine(size: Int, radius: Int, color: Color, border: Boolean): NinePatch {
        val p = Pixmap(size, size, Pixmap.Format.RGBA8888)
        p.setColor(color)
        p.fillRectangle(radius, 0, size - radius * 2, size)
        p.fillRectangle(0, radius, size, size - radius * 2)
        p.fillCircle(radius, radius, radius); p.fillCircle(size - radius, radius, radius)
        p.fillCircle(radius, size - radius, radius); p.fillCircle(size - radius, size - radius, radius)
        // chunky cartoon look: top gloss + bottom 3D lip (SS game buttons)
        p.setColor(1f, 1f, 1f, 0.26f)
        p.fillRectangle(radius, radius / 3, size - radius * 2, radius / 2)
        p.fillCircle(radius + radius / 2, radius / 2 + radius / 3, radius / 3)
        if (border) {
            p.setColor(0f, 0f, 0f, 0.32f)
            p.fillRectangle(radius, size - radius / 2, size - radius * 2, radius / 2)
            p.fillCircle(radius, size - radius / 2, radius / 2)
            p.fillCircle(size - radius, size - radius / 2, radius / 2)
        }
        val t = Texture(p); p.dispose()
        val m = radius + 2
        return NinePatch(t, m, m, m, m)
    }

    /** Standing character portrait for shop cards. */
    private fun characterPreview(ch: CharacterDef): Texture {
        val s = 160
        val p = Pixmap(s, s, Pixmap.Format.RGBA8888)
        val cx = s / 2f
        val u = s * 0.36f
        val by = s * 0.12f
        p.setColor(Color(ch.pants))
        p.fillRectangle((cx - u * 0.17f).toInt(), (by + u * 0.42f).toInt(), (u * 0.14f).toInt().coerceAtLeast(1), (u * 0.44f).toInt())
        p.fillRectangle((cx + u * 0.03f).toInt(), (by + u * 0.42f).toInt(), (u * 0.14f).toInt().coerceAtLeast(1), (u * 0.44f).toInt())
        p.setColor(Color(ch.shoes))
        p.fillRectangle((cx - u * 0.2f).toInt(), by.toInt(), (u * 0.2f).toInt().coerceAtLeast(1), (u * 0.09f).toInt().coerceAtLeast(1))
        p.fillRectangle((cx + u * 0.01f).toInt(), by.toInt(), (u * 0.2f).toInt().coerceAtLeast(1), (u * 0.09f).toInt().coerceAtLeast(1))
        p.setColor(Color(ch.backpack))
        p.fillRectangle((cx - u * 0.22f).toInt(), (by + u * 0.45f).toInt(), (u * 0.44f).toInt(), (u * 0.38f).toInt())
        p.setColor(Color(ch.accent))
        p.fillRectangle((cx - u * 0.16f).toInt(), (by + u * 0.72f).toInt(), (u * 0.32f).toInt(), (u * 0.08f).toInt().coerceAtLeast(1))
        p.setColor(Color(ch.hoodie))
        p.fillRectangle((cx - u * 0.27f).toInt(), (by + u * 0.42f).toInt(), (u * 0.54f).toInt(), (u * 0.42f).toInt())
        p.fillCircle(cx.toInt(), (by + u * 0.86f).toInt(), (u * 0.1f).toInt().coerceAtLeast(1))
        p.setColor(Color(ch.hoodie).mul(0.85f))
        p.fillRectangle((cx - u * 0.39f).toInt(), (by + u * 0.46f).toInt(), (u * 0.11f).toInt().coerceAtLeast(1), (u * 0.32f).toInt())
        p.fillRectangle((cx + u * 0.28f).toInt(), (by + u * 0.46f).toInt(), (u * 0.11f).toInt().coerceAtLeast(1), (u * 0.32f).toInt())
        p.setColor(Color(ch.skin))
        p.fillCircle((cx - u * 0.335f).toInt(), (by + u * 0.44f).toInt(), (u * 0.06f).toInt().coerceAtLeast(1))
        p.fillCircle((cx + u * 0.335f).toInt(), (by + u * 0.44f).toInt(), (u * 0.06f).toInt().coerceAtLeast(1))
        p.setColor(Color(ch.skin))
        p.fillCircle(cx.toInt(), (by + u * 1.03f).toInt(), (u * 0.17f).toInt().coerceAtLeast(2))
        p.setColor(Color(ch.cap))
        p.fillCircle(cx.toInt(), (by + u * 1.08f).toInt(), (u * 0.17f).toInt().coerceAtLeast(2))
        p.setColor(Color(ch.accent))
        p.fillRectangle((cx - u * 0.06f).toInt(), (by + u * 1.2f).toInt(), (u * 0.12f).toInt().coerceAtLeast(1), (u * 0.05f).toInt().coerceAtLeast(1))
        return tex(p)
    }

    private fun tex(p: Pixmap): Texture { val t = Texture(p); p.dispose(); return t }

    /** SS "New High Score" backdrop: 4 conic rainbow wedges + warm core. */
    private fun burst(size: Int): Texture {
        val p = Pixmap(size, size, Pixmap.Format.RGBA8888)
        val half = size / 2f
        val wedges = intArrayOf(0xe23c3cff.toInt(), 0xf28c1aff.toInt(), 0xffd23eff.toInt(), 0x4fbf4fff.toInt())
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = (x - half) / half
                val dy = (y - half) / half
                val d = kotlin.math.sqrt(dx * dx + dy * dy)
                if (d > 1f) continue
                var ang = kotlin.math.atan2(dy, dx) / (2f * PI.toFloat()) + 1.0f
                ang = (ang % 1f + 1f) % 1f
                val seg = (ang * 4f)
                val i = seg.toInt() % 4
                val f = seg - seg.toInt()
                val a = wedges[i]
                val b = wedges[(i + 1) % 4]
                val blend = f * f * (3f - 2f * f)
                fun ch(v: Int, shift: Int): Float =
                    (((v shr shift) and 0xff) / 255f) * (1f - blend) + (((b shr shift) and 0xff) / 255f) * blend
                val core = (d * d * d).coerceIn(0f, 1f)
                p.setColor(ch(a, 16), ch(a, 8), ch(a, 0), 1f)
                p.drawPixel(x, y)
                if (core > 0f && x % 2 == 0 && y % 2 == 0) {
                    // lighten center toward warm white
                    p.setColor(
                        (ch(a, 16) + (1f - ch(a, 16)) * core * 0.85f),
                        (ch(a, 8) + (1f - ch(a, 8)) * core * 0.85f),
                        (ch(a, 0) + (1f - ch(a, 0)) * core * 0.85f), 1f
                    )
                    p.drawPixel(x, y)
                }
            }
        }
        val t = Texture(p); p.dispose(); return t
    }
}
