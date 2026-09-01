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
    // v3.0 SS palette: neutral warm-gray ballast (was terracotta — read "dirt
    // desert", nothing like SS's urban gravel), darker ties, steel rails
    val GROUND = Color(0xb3a898ff.toInt())       // warm gray ballast
    val GROUND_FAR = Color(0xc2b7a6ff.toInt())   // lighter far ballast
    val GRASS = Color(0x5fbf4aff.toInt())        // vivid trackside grass
    val PATH_CREAM = Color(0xe0d2b4ff.toInt())   // concrete slab A
    val PATH_ORANGE = Color(0xcbba9cff.toInt())  // concrete slab B
    val SLEEPER = Color(0x59422fff.toInt())      // dark brown ties
    val RAIL = Color(0xece8deff.toInt())         // shiny silver rail head
    val RAIL_SIDE = Color(0x6e675eff.toInt())    // dark steel rail base

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
    lateinit var disc: Texture            // hard-edged circle (UI pips, wheels, dots)
    lateinit var hazeBand: Texture        // symmetric horizon haze (soft both edges)
    lateinit var previews: Array<Texture> // character card portraits

    // FaceBatch materials — textured pseudo-3D faces (renderer v2)
    lateinit var trainSides: Array<Texture>   // per-livery carriage side (windows/doors/wheels baked)
    lateinit var trainFronts: Array<Texture>  // per-livery lead-car front (windshield/lights baked)
    lateinit var trainRears: Array<Texture>   // per-livery carriage rear (taillights)
    lateinit var trainRoofTex: Texture        // tileable grey roof w/ vents
    lateinit var hazardTex: Texture           // yellow/black chevron plate (tileable U)
    lateinit var barrierRedTex: Texture       // v3.0: SS red/white barrier stripes
    lateinit var signTealTex: Texture         // slide-sign: teal board w/ white arrows
    lateinit var containerTex: Texture        // red container blockade
    lateinit var facades: Array<Texture>      // building facades w/ baked windows
    lateinit var glassTex: Texture            // glass tower facade

    // v4 true-3D world tiles (ModelBatch materials)
    lateinit var trackTex: Texture            // ballast + sleepers + 6 steel rails (tiles along z)
    lateinit var dirtTex: Texture             // side apron gravel/dirt
    lateinit var wallTex: Texture             // graffiti brick wall
    lateinit var tunnelTex: Texture           // grimy tunnel tile

    fun generate() {
        white = solid(4, 4, Color.WHITE)
        disc = radial(64, Color(1f, 1f, 1f, 1f), 0.86f) // solid core, 14% feather
        hazeBand = horizonHaze(8, 256)
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
        trainSides = Array(Palette.TRAIN_LIVERIES.size) { trainSide(it) }
        trainFronts = Array(Palette.TRAIN_LIVERIES.size) { trainFront(it) }
        trainRears = Array(Palette.TRAIN_LIVERIES.size) { trainRear(it) }
        trainRoofTex = trainRoof()
        hazardTex = hazardStripes()
        barrierRedTex = barrierStripes()
        signTealTex = signTeal()
        containerTex = containerBox()
        facades = arrayOf(
            facade(0xe8b27d, 0xd9985f, 11L), facade(0xc96b4a, 0xb85a4a, 23L),
            facade(0x9fc5c0, 0x8fb6d9, 37L), facade(0xe8d5a8, 0xc78a6a, 53L)
        )
        glassTex = glassTower()
        trackTex = trackTile()
        dirtTex = dirtTile()
        wallTex = wallTile()
        tunnelTex = tunnelTile()
    }

    fun dispose() {
        listOf(glow, softShadow, sky, fog, cloudA, cloudB, skylineFar, skylineNear, vignette, rainbowBurst, white, disc, hazeBand,
            trainRoofTex, hazardTex, signTealTex, containerTex, glassTex).forEach { it.dispose() }
        coinFrames.forEach { it.dispose() }
        powerIcons.forEach { it.dispose() }
        previews.forEach { it.dispose() }
        trainSides.forEach { it.dispose() }
        trainFronts.forEach { it.dispose() }
        trainRears.forEach { it.dispose() }
        facades.forEach { it.dispose() }
        trackTex.dispose(); dirtTex.dispose(); wallTex.dispose(); tunnelTex.dispose()
    }

    /** Ballast + wooden sleepers + steel rails — one tile = 7.5u wide × 3.5u deep. */
    private fun trackTile(): Texture {
        val s = 256
        val p = Pixmap(s, s, Pixmap.Format.RGBA8888)
        // ballast base
        p.setColor(0x8f8578ff.toInt()); p.fill()
        val rnd = java.util.Random(9L)
        for (i in 0 until 900) {
            val g = (0.75f + rnd.nextFloat() * 0.5f)
            p.setColor((0.56f * g).coerceAtMost(1f), (0.52f * g).coerceAtMost(1f), (0.47f * g).coerceAtMost(1f), 1f)
            p.fillRectangle(rnd.nextInt(s), rnd.nextInt(s), 2 + rnd.nextInt(3), 2 + rnd.nextInt(2))
        }
        // dark ties across every lane (3 lanes → centers at 1/6, 3/6, 5/6)
        val tieH = 34
        var ty = 60
        while (ty < s) {
            for (lane in 0 until 3) {
                val cx = s / 6 + lane * s / 3
                p.setColor(0x4a3a2aff.toInt()); p.fillRectangle(cx - 96, ty, 76, tieH)
                p.setColor(0x5c4834ff.toInt()); p.fillRectangle(cx - 96, ty, 76, 6)
                p.setColor(0x3a2d20ff.toInt()); p.fillRectangle(cx - 96, ty + tieH - 5, 76, 5)
            }
            ty += 128
        }
        // steel rails (2 per lane) with shine
        for (lane in 0 until 3) {
            val cx = s / 6 + lane * s / 3
            for (off in intArrayOf(-52, 52)) {
                val rx = cx + off - 5
                p.setColor(0x6a6f76ff.toInt()); p.fillRectangle(rx, 0, 10, s)
                p.setColor(0xd9dde2ff.toInt()); p.fillRectangle(rx + 2, 0, 3, s)
                p.setColor(0x9aa0a8ff.toInt()); p.fillRectangle(rx + 7, 0, 3, s)
            }
        }
        val t = Texture(p); p.dispose()
        t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat)
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        return t
    }

    /** Gravel/dirt side apron. */
    private fun dirtTile(): Texture {
        val s = 128
        val p = Pixmap(s, s, Pixmap.Format.RGBA8888)
        p.setColor(0x9b8a72ff.toInt()); p.fill()
        val rnd = java.util.Random(31L)
        for (i in 0 until 420) {
            val g = 0.7f + rnd.nextFloat() * 0.6f
            p.setColor((0.61f * g).coerceAtMost(1f), (0.54f * g).coerceAtMost(1f), (0.42f * g).coerceAtMost(1f), 1f)
            p.fillRectangle(rnd.nextInt(s), rnd.nextInt(s), 2 + rnd.nextInt(3), 2 + rnd.nextInt(2))
        }
        val t = Texture(p); p.dispose()
        t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat)
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        return t
    }

    /** Brick wall with painted graffiti tags. */
    private fun wallTile(): Texture {
        val w = 256; val h = 256
        val p = Pixmap(w, h, Pixmap.Format.RGBA8888)
        p.setColor(0xb08a62ff.toInt()); p.fill()
        // brick courses
        var y = 0
        var row = 0
        while (y < h) {
            val bh = 16
            var x = if (row % 2 == 0) 0 else -12
            while (x < w) {
                val g = 0.86f + java.util.Random((x * 31 + y * 17).toLong()).nextFloat() * 0.28f
                p.setColor((0.69f * g).coerceAtMost(1f), (0.54f * g).coerceAtMost(1f), (0.38f * g).coerceAtMost(1f), 1f)
                p.fillRectangle(x, y, 24, bh - 3)
                x += 26
            }
            y += bh; row++
        }
        // graffiti blobs + drips
        val rnd = java.util.Random(77L)
        val tags = intArrayOf(0x37b8a8ff.toInt(), 0xf28c1aff.toInt(), 0xd94a38ff.toInt(), 0x8a55c9ff.toInt(), 0x3e7bc0ff.toInt())
        for (i in 0 until 7) {
            val cx = rnd.nextInt(w); val cy = rnd.nextInt(h)
            val rw = 24 + rnd.nextInt(46); val rh = 12 + rnd.nextInt(26)
            p.setColor(tags[i % tags.size])
            p.fillRectangle(cx - rw / 2, cy - rh / 2, rw, rh)
            p.setColor(0xffffffff.toInt())
            p.fillRectangle(cx - rw / 2 + 4, cy - rh / 2 + 4, rw / 3, 4)
            // drip
            p.fillRectangle(cx + rnd.nextInt(rw) - rw / 2, cy + rh / 2, 3, 8 + rnd.nextInt(14))
        }
        // grime top/bottom
        p.setColor(0x00000030); p.fillRectangle(0, 0, w, 10)
        p.setColor(0x00000022); p.fillRectangle(0, h - 8, w, 8)
        val t = Texture(p); p.dispose()
        t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat)
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        return t
    }

    /** Grimy tunnel tiles with occasional lamp glow band. */
    private fun tunnelTile(): Texture {
        val s = 256
        val p = Pixmap(s, s, Pixmap.Format.RGBA8888)
        p.setColor(0x4c4a48ff.toInt()); p.fill()
        for (y in 0 until s step 32) {
            for (x in 0 until s step 32) {
                val g = 0.82f + java.util.Random((x * 13 + y * 7).toLong()).nextFloat() * 0.36f
                p.setColor((0.30f * g).coerceAtMost(1f), (0.29f * g).coerceAtMost(1f), (0.28f * g).coerceAtMost(1f), 1f)
                p.fillRectangle(x + 1, y + 1, 30, 30)
            }
        }
        val rnd = java.util.Random(19L)
        for (i in 0 until 60) {
            p.setColor(0x00000030)
            p.fillRectangle(rnd.nextInt(s), rnd.nextInt(s), 4 + rnd.nextInt(10), 3 + rnd.nextInt(6))
        }
        val t = Texture(p); p.dispose()
        t.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat)
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        return t
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
    /** Symmetric horizon haze: transparent → opaque center → transparent. */
    private fun horizonHaze(w: Int, h: Int): Texture {
        val p = Pixmap(w, h, Pixmap.Format.RGBA8888)
        val c = Palette.FOG
        for (y in 0 until h) {
            val t = abs(y / (h - 1f) * 2f - 1f)      // 0 at center, 1 at edges
            val a = (1f - t)
            val aa = a * a * (3f - 2f * a)            // smoothstep falloff
            p.setColor(c.r, c.g, c.b, aa)
            p.drawLine(0, y, w - 1, y)
        }
        val tex = Texture(p); p.dispose(); return tex
    }

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

    // ── SS-chibi character portraits (menu + shop) ─────────────────────
    // Front-facing big-head chibi with face, cap, hoodie, straps, sneakers.
    // Proportions: head ≈ 43% of total height (Subway Surfers DNA).
    private fun mul(c: Int, f: Float): Int {
        val col = Color(c)
        return Color(col.r * f, col.g * f, col.b * f, 1f).toIntBits()
    }

    /** Standing character portrait for shop cards (SS front-view chibi). */
    private fun characterPreview(ch: CharacterDef): Texture {
        val s = 320
        val p = Pixmap(s, s, Pixmap.Format.RGBA8888)
        val cx = s / 2f
        val OUT = 0x24316bff.toInt()

        // geometry (feet at y=308)
        val headR = 60f
        val headCY = 92f
        val shoulderY = 176f
        val hipY = 248f
        val footY = 308f

        fun circ(x: Float, y: Float, r: Float, col: Int) {
            p.setColor(OUT); p.fillCircle(x.toInt(), y.toInt(), (r + 4f).toInt())
            p.setColor(col); p.fillCircle(x.toInt(), y.toInt(), r.toInt())
        }
        fun rect(x: Float, y: Float, w: Float, h: Float, col: Int) {
            p.setColor(OUT); p.fillRectangle((x - 4f).toInt(), (y - 4f).toInt(), (w + 8f).toInt(), (h + 8f).toInt())
            p.setColor(col); p.fillRectangle(x.toInt(), y.toInt(), w.toInt(), h.toInt())
        }
        fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float, col: Int) {
            rect(x + r, y, w - r * 2, h, col)
            rect(x, y + r, w, h - r * 2, col)
            circ(x + r, y + r, r, col); circ(x + w - r, y + r, r, col)
            circ(x + r, y + h - r, r, col); circ(x + w - r, y + h - r, r, col)
        }
        fun bare(x: Int, y: Int, w: Int, h: Int, col: Int) { p.setColor(col); p.fillRectangle(x, y, w, h) }

        // LEGS + SNEAKERS (drawn first, torso overlaps hips)
        val legW = 26f
        for (side in intArrayOf(-1, 1)) {
            val lx = cx + side * 17f - legW / 2
            rect(lx, hipY, legW, footY - hipY - 10f, ch.pants)
            // knee patch hint
            bare((lx + 5f).toInt(), (hipY + 26f).toInt(), (legW - 10f).toInt(), 8, mul(ch.pants, 1.18f))
            // chunky sneaker: rounded + white sole + lace hint
            roundRect(cx + side * 17f - 20f, footY - 22f, 40f, 16f, 8f, ch.shoes)
            bare((cx + side * 17f - 20f).toInt(), (footY - 8f).toInt(), 40, 8, 0xf7f7f7ff.toInt())
            bare((cx + side * 17f - 8f).toInt(), (footY - 18f).toInt(), 16, 5, mul(ch.shoes, 0.75f))
        }

        // TORSO — hoodie with rounded shoulders
        roundRect(cx - 52f, shoulderY, 104f, hipY - shoulderY + 14f, 26f, ch.hoodie)
        // hoodie hem band
        bare((cx - 52f).toInt(), (hipY - 2f).toInt(), 104, 12, mul(ch.hoodie, 0.82f))
        // front pocket
        roundRect(cx - 30f, hipY - 14f, 60f, 26f, 9f, mul(ch.hoodie, 0.88f))
        // zipper
        bare((cx - 2f).toInt(), (shoulderY + 14f).toInt(), 4, 62, mul(ch.hoodie, 0.62f))
        bare((cx - 5f).toInt(), (shoulderY + 40f).toInt(), 10, 14, 0xffc93cff.toInt())
        // v3.0: undershirt collar hint (Jack's red tee under the hoodie)
        if (ch.hoodLining != 0) {
            p.setColor(ch.hoodLining)
            p.fillRectangle((cx - 14f).toInt(), (shoulderY + 6f).toInt(), 28, 14)
            p.setColor(OUT)
            p.fillRectangle((cx - 14f).toInt(), (shoulderY + 6f).toInt(), 3, 14)
            p.fillRectangle((cx + 11f).toInt(), (shoulderY + 6f).toInt(), 3, 14)
        }
        // v3.0: denim vest side panels (Jack's signature layer)
        if (ch.vest != 0) {
            roundRect(cx - 54f, shoulderY + 2f, 24f, hipY - shoulderY + 6f, 10f, ch.vest)
            roundRect(cx + 30f, shoulderY + 2f, 24f, hipY - shoulderY + 6f, 10f, ch.vest)
            p.setColor(mul(ch.vest, 0.82f))
            p.fillRectangle((cx - 36f).toInt(), (shoulderY + 8f).toInt(), 6, (hipY - shoulderY - 8f).toInt())
            p.fillRectangle((cx + 30f).toInt(), (shoulderY + 8f).toInt(), 6, (hipY - shoulderY - 8f).toInt())
        }

        // BACKPACK STRAPS over the shoulders + chest strap
        for (side in intArrayOf(-1, 1)) {
            roundRect(cx + side * 34f - 8f, shoulderY + 4f, 16f, hipY - shoulderY - 8f, 7f, mul(ch.backpack, 0.9f))
        }
        roundRect(cx - 26f, shoulderY + 30f, 52f, 10f, 5f, mul(ch.backpack, 1.1f))

        // ARMS — capsule sleeves + hands
        for (side in intArrayOf(-1, 1)) {
            val shoulderX = cx + side * 52f
            circ(shoulderX + side * 4f, shoulderY + 18f, 15f, ch.hoodie)
            circ(shoulderX + side * 12f, shoulderY + 44f, 13f, ch.hoodie)
            circ(shoulderX + side * 17f, shoulderY + 66f, 11f, mul(ch.hoodie, 0.92f))
            circ(shoulderX + side * 18f, shoulderY + 80f, 10f, ch.skin)
        }

        // HEAD — hair base, ears, then face plate
        for (side in intArrayOf(-1, 1)) circ(cx + side * (headR - 2f), headCY + 6f, 11f, ch.skin)
        circ(cx, headCY, headR, ch.hair)
        p.setColor(OUT); p.fillCircle(cx.toInt(), (headCY + 8f).toInt(), (headR - 6f + 4f).toInt())
        p.setColor(ch.skin); p.fillCircle(cx.toInt(), (headCY + 8f).toInt(), (headR - 6f).toInt())
        // hair fringe poking under the cap brim
        for (i in 0..5) {
            val fx = cx - 44f + i * 17.6f
            p.setColor(ch.hair); p.fillCircle(fx.toInt(), (headCY - 26f).toInt(), 9)
        }

        // FACE — big cartoon eyes, brows, nose, grin, blush
        for (side in intArrayOf(-1, 1)) {
            val ex = cx + side * 26f
            p.setColor(0xffffffff.toInt()); p.fillCircle(ex.toInt(), (headCY + 12f).toInt(), 15)
            p.setColor(0x5a3a1fff.toInt()); p.fillCircle(ex.toInt(), (headCY + 13f).toInt(), 10)
            p.setColor(0x24160aff.toInt()); p.fillCircle(ex.toInt(), (headCY + 14f).toInt(), 5)
            p.setColor(0xffffffff.toInt()); p.fillCircle((ex - 4f).toInt(), (headCY + 8f).toInt(), 4)
            bare((ex - 15f).toInt(), (headCY - 10f).toInt(), 30, 7, mul(ch.hair, 0.7f))
            p.setColor(0.95f, 0.55f, 0.5f, 0.4f); p.fillCircle((cx + side * 44f).toInt(), (headCY + 26f).toInt(), 8)
        }
        p.setColor(mul(ch.skin, 0.9f)); p.fillCircle(cx.toInt(), (headCY + 24f).toInt(), 5)
        // grin — crescent (dark circle masked by skin circle) + teeth
        p.setColor(0x5e2c1dff.toInt()); p.fillCircle(cx.toInt(), (headCY + 34f).toInt(), 14)
        p.setColor(ch.skin); p.fillCircle(cx.toInt(), (headCY + 39f).toInt(), 12)
        p.setColor(0xffffffff.toInt()); p.fillRectangle((cx - 8f).toInt(), (headCY + 26f).toInt(), 16, 5)

        // CAP — dome + brim band + top button + optional white front panel
        // (Jack: red dome w/ white panel — the recognizable Jake DNA)
        circ(cx, headCY - 34f, headR - 10, ch.cap)
        bare((cx - headR + 2f).toInt(), (headCY - 14f).toInt(), (headR * 2 - 4f).toInt(), 14, ch.cap)
        p.setColor(OUT); p.fillRectangle((cx - headR - 1f).toInt(), (headCY - 2f).toInt(), (headR * 2 + 2f).toInt(), 5)
        p.setColor(mul(ch.cap, 0.82f)); p.fillRectangle((cx - headR).toInt(), (headCY + 1f).toInt(), (headR * 2).toInt(), 7)
        p.setColor(mul(ch.cap, 1.12f)); p.fillCircle(cx.toInt(), (headCY - 80f).toInt(), 7)
        if (ch.capPanel != 0) {
            // white front panel oval on the dome (Pixmap has no fillEllipse —
            // approximate with a stack of shrinking scanline rects)
            p.setColor(ch.capPanel)
            for (i in 0..22) {
                val t = i / 22f
                val w = (34f * (1f - t * t)).toInt().coerceAtLeast(2)
                p.fillRectangle((cx - w).toInt(), (headCY - 62f + i * 1.6f).toInt().coerceAtMost((headCY - 30f).toInt()), w * 2, 2)
            }
        }
        // cap gloss
        p.setColor(1f, 1f, 1f, 0.22f); p.fillCircle((cx - 26f).toInt(), (headCY - 50f).toInt(), 12)

        return tex(p)
    }

    // ── FaceBatch material generators (textured pseudo-3D world) ────────

    /** Carriage side per livery: windows, door, white band, skirt, wheels, optional graffiti. */
    private fun trainSide(liveryIdx: Int): Texture {
        val w = 256; val h = 128
        val p = Pixmap(w, h, Pixmap.Format.RGBA8888)
        val liv = Palette.TRAIN_LIVERIES[liveryIdx]
        val body = Color(liv[0]); val skirt = Color(liv[1]); val band = Color(liv[2])
        // body
        p.setColor(body); p.fillRectangle(0, 0, w, h)
        // roof edge strip
        p.setColor(Palette.TRAIN_ROOF); p.fillRectangle(0, 0, w, 7)
        p.setColor(1f, 1f, 1f, 0.18f); p.fillRectangle(0, 7, w, 2)
        // window band: 3 windows + door on the right
        val winY = 22; val winH = 34
        for (i in 0 until 3) {
            val x = 14 + i * 68
            p.setColor(0x1c2740ff.toInt()); p.fillRectangle(x - 2, winY - 2, 52, winH + 4)
            p.setColor(0x22324cff.toInt()); p.fillRectangle(x, winY, 48, winH)
            p.setColor(0x9adcf0ff.toInt()); p.fillRectangle(x + 4, winY + 5, 12, 5)
            p.setColor(1f, 1f, 1f, 0.10f); p.fillRectangle(x, winY + winH - 8, 48, 8)
        }
        // door (right side) with center split
        p.setColor(skirt); p.fillRectangle(w - 34, 16, 30, 86)
        p.setColor(0f, 0f, 0f, 0.25f); p.fillRectangle(w - 21, 16, 2, 86)
        p.setColor(0x22324cff.toInt()); p.fillRectangle(w - 30, 22, 22, 26)
        // signature white band under the windows
        p.setColor(band); p.fillRectangle(0, 66, w - 36, 9)
        p.setColor(1f, 1f, 1f, 0.25f); p.fillRectangle(0, 66, w - 36, 3)
        // skirt
        p.setColor(skirt); p.fillRectangle(0, 104, w, 12)
        p.setColor(0f, 0f, 0f, 0.22f); p.fillRectangle(0, 112, w, 4)
        // wheels: bogies under body
        for (wx in intArrayOf(30, 62, 150, 182)) {
            p.setColor(0x1a1a20ff.toInt()); p.fillCircle(wx, 118, 9)
            p.setColor(0x4a4a54ff.toInt()); p.fillCircle(wx, 118, 5)
            p.setColor(0x8a8a94ff.toInt()); p.fillCircle(wx, 118, 2)
        }
        // graffiti on freight liveries (orange freight 1, violet 5)
        if (liveryIdx == 1 || liveryIdx == 5) {
            val rng = Random(100L + liveryIdx)
            val cols = intArrayOf(0xffd24aff.toInt(), 0x37b8a8ff.toInt(), 0xe2493bff.toInt(), 0xd8578aff.toInt(), 0x8ff2e2ff.toInt())
            for (g in 0 until 7) {
                val col = cols[rng.nextInt(cols.size)]
                val gx = 8 + rng.nextInt(w - 80)
                val gy = 86 + rng.nextInt(18)
                val gr = 4 + rng.nextInt(7)
                p.setColor(0x2b2622ff.toInt()); p.fillCircle(gx, gy, gr + 2)
                p.setColor(col); p.fillCircle(gx, gy, gr)
            }
            // spray tag underline
            p.setColor(0xffd24aff.toInt()); p.fillRectangle(40, 96, 90, 3)
        }
        return tex(p)
    }

    /** Lead-car front per livery: yellow cab, windshield, headlights, bumper. */
    private fun trainFront(liveryIdx: Int): Texture {
        val w = 128; val h = 128
        val p = Pixmap(w, h, Pixmap.Format.RGBA8888)
        val liv = Palette.TRAIN_LIVERIES[liveryIdx]
        // cab body (SS lead cars read yellow) with livery shoulder stripe
        p.setColor(Palette.TRAIN_FRONT); p.fillRectangle(0, 0, w, h)
        p.setColor(Color(liv[0])); p.fillRectangle(0, 0, w, 14)
        p.setColor(Palette.TRAIN_ROOF); p.fillRectangle(0, 0, w, 6)
        // windshield (rounded navy) + reflection streaks
        p.setColor(0x1c2740ff.toInt()); p.fillRectangle(14, 18, w - 28, 40)
        p.setColor(0x22324cff.toInt()); p.fillRectangle(16, 20, w - 32, 36)
        p.setColor(0x9adcf0ff.toInt()); p.fillRectangle(22, 26, 22, 7)
        p.setColor(1f, 1f, 1f, 0.25f); p.fillRectangle(52, 26, 10, 26)
        // destination panel
        p.setColor(0xfff2dcff.toInt()); p.fillRectangle(w / 2 - 14, 8, 28, 7)
        // headlights with warm halo
        for (hx in intArrayOf(24, w - 24)) {
            p.setColor(1f, 0.9f, 0.6f, 0.45f); p.fillCircle(hx, 88, 12)
            p.setColor(0xfff6d8ff.toInt()); p.fillCircle(hx, 88, 7)
            p.setColor(1f, 1f, 1f, 0.85f); p.fillCircle(hx - 2, 86, 3)
        }
        // livery band + skirt + bumper
        p.setColor(Color(liv[0])); p.fillRectangle(0, 70, w, 8)
        p.setColor(Color(liv[1])); p.fillRectangle(0, 104, w, 14)
        p.setColor(0x2b2622ff.toInt()); p.fillRectangle(0, 118, w, 10)
        p.setColor(0x1a1a20ff.toInt()); p.fillRectangle(w / 2 - 8, 118, 16, 10)
        return tex(p)
    }

    /** Carriage rear per livery: rear window, red taillights, livery band. */
    private fun trainRear(liveryIdx: Int): Texture {
        val w = 128; val h = 128
        val p = Pixmap(w, h, Pixmap.Format.RGBA8888)
        val liv = Palette.TRAIN_LIVERIES[liveryIdx]
        p.setColor(Color(liv[0])); p.fillRectangle(0, 0, w, h)
        p.setColor(Palette.TRAIN_ROOF); p.fillRectangle(0, 0, w, 6)
        // rear window
        p.setColor(0x1c2740ff.toInt()); p.fillRectangle(16, 18, w - 32, 38)
        p.setColor(0x22324cff.toInt()); p.fillRectangle(18, 20, w - 36, 34)
        // taillights
        for (hx in intArrayOf(24, w - 24)) {
            p.setColor(1f, 0.3f, 0.25f, 0.5f); p.fillCircle(hx, 86, 10)
            p.setColor(0xe23c3cff.toInt()); p.fillCircle(hx, 86, 6)
        }
        p.setColor(Color(liv[1])); p.fillRectangle(0, 104, w, 14)
        p.setColor(0x2b2622ff.toInt()); p.fillRectangle(0, 118, w, 10)
        return tex(p)
    }

    /** Tileable grey roof with vents and panel seams. */
    private fun trainRoof(): Texture {
        val w = 128; val h = 64
        val p = Pixmap(w, h, Pixmap.Format.RGBA8888)
        p.setColor(Palette.TRAIN_ROOF); p.fillRectangle(0, 0, w, h)
        p.setColor(0f, 0f, 0f, 0.10f)
        for (x in 0 until w step 32) p.fillRectangle(x, 0, 1, h)
        // vents
        for (vx in intArrayOf(24, 84)) {
            p.setColor(0x7a8088ff.toInt()); p.fillRectangle(vx, 18, 20, 28)
            p.setColor(0x5f646cff.toInt())
            for (i in 0 until 4) p.fillRectangle(vx + 3, 22 + i * 6, 14, 2)
        }
        // AC pod
        p.setColor(0x8a9098ff.toInt()); p.fillRectangle(56, 26, 18, 16)
        return tex(p)
    }

    /** Yellow/black chevron hazard plate — tileable horizontally. */
    private fun hazardStripes(): Texture {
        val s = 64
        val p = Pixmap(s, s, Pixmap.Format.RGBA8888)
        p.setColor(Palette.HAZARD_YELLOW); p.fillRectangle(0, 0, s, s)
        p.setColor(Palette.HAZARD_BLACK)
        // 45° stripes wrapping seamlessly
        var i = -s
        while (i < s * 2) {
            for (k in 0 until s) {
                val x = i + k
                if (x in 0 until s) p.drawPixel(x, k)
                if (x + 1 in 0 until s) p.drawPixel(x + 1, k)
            }
            i += 16
        }
        return tex(p)
    }

    /** v3.0: SS-style red/white 45° barrier stripes (jump/slide barriers). */
    private fun barrierStripes(): Texture {
        val s = 64
        val p = Pixmap(s, s, Pixmap.Format.RGBA8888)
        p.setColor(0xe83a30ff.toInt()); p.fillRectangle(0, 0, s, s)
        p.setColor(0xf6f2ecff.toInt())
        var i = -s
        while (i < s * 2) {
            for (k in 0 until s) {
                val x = i + k
                if (x in 0 until s) p.drawPixel(x, k)
                if (x + 1 in 0 until s) p.drawPixel(x + 1, k)
                if (x + 2 in 0 until s) p.drawPixel(x + 2, k)
                if (x + 3 in 0 until s) p.drawPixel(x + 3, k)
            }
            i += 18
        }
        return tex(p)
    }

    /** Teal slide-sign board with white down arrows. */
    private fun signTeal(): Texture {
        val w = 128; val h = 64
        val p = Pixmap(w, h, Pixmap.Format.RGBA8888)
        p.setColor(0x2fa08bff.toInt()); p.fillRectangle(0, 0, w, h)
        p.setColor(0x237a6aff.toInt()); p.fillRectangle(0, 0, w, 5); p.fillRectangle(0, h - 5, w, 5)
        p.setColor(0x8ff2e2ff.toInt())
        for (i in 0 until 3) {
            val ax = 24 + i * 40
            for (k in 0 until 10) {
                val yy = 20 + k * 3
                val half = 3 + k / 3
                p.fillRectangle(ax - half, yy, half * 2, 3)
            }
        }
        return tex(p)
    }

    /** Red shipping-container blockade: ridges, white stencil band, corner posts. */
    private fun containerBox(): Texture {
        val w = 128; val h = 128
        val p = Pixmap(w, h, Pixmap.Format.RGBA8888)
        p.setColor(Palette.CONTAINER_RED); p.fillRectangle(0, 0, w, h)
        p.setColor(0f, 0f, 0f, 0.16f)
        var x = 8
        while (x < w) { p.fillRectangle(x, 6, 3, h - 12); x += 16 }
        p.setColor(0xfff2dcff.toInt()); p.fillRectangle(0, 52, w, 22)
        p.setColor(0x2b2622ff.toInt())
        p.fillRectangle(14, 58, 26, 10); p.fillRectangle(52, 58, 26, 10); p.fillRectangle(90, 58, 22, 10)
        p.setColor(0xa8442fff.toInt()); p.fillRectangle(0, 0, 7, h); p.fillRectangle(w - 7, 0, 7, h)
        p.setColor(0f, 0f, 0f, 0.30f); p.fillRectangle(0, h - 8, w, 8)
        return tex(p)
    }

    /** Building facade: wall color + brick hint + window grid (some lit) + shopfront. */
    private fun facade(wall: Int, trim: Int, seed: Long): Texture {
        val w = 128; val h = 160
        val p = Pixmap(w, h, Pixmap.Format.RGBA8888)
        val rng = Random(seed)
        p.setColor(Color(wall shl 8 or 0xff)); p.fillRectangle(0, 0, w, h)
        // subtle brick courses
        p.setColor(0f, 0f, 0f, 0.05f)
        var y = 6
        while (y < h) { p.fillRectangle(0, y, w, 2); y += 12 }
        // windows 4×5
        val cols = 4; val rows = 5
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val x = 10 + c * 30; val wy = 10 + r * 26
                p.setColor(0x243043ff.toInt()); p.fillRectangle(x - 2, wy - 2, 22, 20)
                val lit = rng.nextFloat() < 0.30f
                p.setColor(if (lit) 0xffe9a8ff.toInt() else 0x2f3238ff.toInt())
                p.fillRectangle(x, wy, 18, 16)
                if (!lit) { p.setColor(0x9adcf0ff.toInt()); p.fillRectangle(x + 2, wy + 2, 5, 3) }
                // sill
                p.setColor(Color(trim shl 8 or 0xff)); p.fillRectangle(x - 3, wy + 18, 24, 3)
            }
        }
        // shopfront band at street level
        p.setColor(0x243043ff.toInt()); p.fillRectangle(0, h - 30, w, 30)
        p.setColor(0xffc93cff.toInt()); p.fillRectangle(0, h - 34, w, 5)
        p.setColor(0x9adcf0ff.toInt()); p.fillRectangle(10, h - 24, 44, 18)
        p.setColor(0x5e3a22ff.toInt()); p.fillRectangle(72, h - 24, 20, 24)
        p.setColor(0xf2e2c8ff.toInt()); p.fillCircle(82, h - 12, 2)
        return tex(p)
    }

    /** Glass skyscraper facade: gradient glass, mullions, diagonal sky reflections. */
    private fun glassTower(): Texture {
        val w = 128; val h = 192
        val p = Pixmap(w, h, Pixmap.Format.RGBA8888)
        for (yy in 0 until h) {
            val t = yy / (h - 1f)
            p.setColor(0x63a8d8ff.toInt().let { c ->
                val col = Color(c)
                Color(col.r + (0.55f - col.r) * t, col.g + (0.72f - col.g) * t, col.b + (0.88f - col.b) * t, 1f)
            })
            p.drawLine(0, yy, w - 1, yy)
        }
        p.setColor(0f, 0f, 0f, 0.22f)
        var x = 0
        while (x < w) { p.fillRectangle(x, 0, 2, h); x += 16 }
        var yy = 0
        while (yy < h) { p.fillRectangle(0, yy, w, 2); yy += 12 }
        // diagonal sky reflections
        p.setColor(1f, 1f, 1f, 0.16f)
        for (k in 0 until w + h step 2) {
            val px = k / 2; val py = h - k / 3 - 1
            if (py in 0 until h && px in 0 until w) p.drawPixel(px, py)
            if (py + 1 in 0 until h && px in 0 until w) p.drawPixel(px, py + 1)
        }
        return tex(p)
    }

    private fun tex(p: Pixmap): Texture {
        val t = Texture(p)
        // Linear filtering: smooth scaling everywhere (portraits, panels, coins)
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        p.dispose()
        return t
    }

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
