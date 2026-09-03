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
    // v5.1: warm sand-brown ballast — v3.0's warm-GRAY read washed-out/pale in
    // gameplay shots; SS gravel carries clear warmth. Not desert-terracotta
    // (the v3.0 lesson stands), just chroma back in the sand family.
    val GROUND = Color(0xbfa07aff.toInt())       // warm sand-brown ballast
    val GROUND_FAR = Color(0xd0b48cff.toInt())   // lighter far ballast
    val GRASS = Color(0x5fbf4aff.toInt())        // vivid trackside grass
    val PATH_CREAM = Color(0xe8dab2ff.toInt())   // concrete slab A
    val PATH_ORANGE = Color(0xd6bc8eff.toInt())  // concrete slab B
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
    val UI_DIM = Color(0x141830ff.toInt())       // v4.5 overlay backdrop dim (behind game-over/pause cards)
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

    // body/shade/band — 20-c: crisp saturated metro liveries (real-SS chroma:
    // the old set read muted/grey in gameplay shots). Clean tri-tone sets:
    // vivid body + deep shade + clean band.
    val TRAIN_LIVERIES = arrayOf(
        intArrayOf(0x2f86e8ff.toInt(), 0x1a55a6ff.toInt(), 0xffffffff.toInt()), // blue metro, white band
        intArrayOf(0xff9c2aff.toInt(), 0xd9700eff.toInt(), 0xfff1d0ff.toInt()), // orange graffiti freight
        intArrayOf(0x2fc558ff.toInt(), 0x1a8a3aff.toInt(), 0xf4fbe4ff.toInt()), // green metro, cream band
        intArrayOf(0xf24030ff.toInt(), 0xac2014ff.toInt(), 0xffe2c4ff.toInt()), // red express
        intArrayOf(0xffd21cff.toInt(), 0xe3a70aff.toInt(), 0x2c3f7dff.toInt()), // yellow metro, navy band
        intArrayOf(0x9a5cdcff.toInt(), 0x7034adff.toInt(), 0xf4e4ffff.toInt()), // violet graffiti
        intArrayOf(0x18b7a8ff.toInt(), 0x0d7f74ff.toInt(), 0xfff6e0ff.toInt()), // teal harbor line
        intArrayOf(0x9aa4b4ff.toInt(), 0x4e5763ff.toInt(), 0xffd23eff.toInt())  // graphite night express, gold band
    )
    val TRAIN_ROOF = Color(0xb2b7bfff.toInt())
    val TRAIN_FRONT = Color(0xffd21cff.toInt())

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
    lateinit var edgeVignette: Texture       // v4.5 transparent center / dark EDGES (danger + boost pulses)
    lateinit var rainbowBurst: Texture // conic rainbow for NEW BEST celebration
    lateinit var coinFrames: Array<Texture>
    lateinit var powerIcons: Array<Texture> // magnet,x2,shield,boost,superjump
    lateinit var navIcons: Array<Texture>   // v4.5 menu tab glyphs: person,bag,tasks,gear
    lateinit var trophy: Texture           // v4.7 gold trophy on the MISSION COMPLETE banner
    lateinit var panelNine: NinePatch
    lateinit var buttonNine: NinePatch
    lateinit var circleNine: NinePatch     // v5.3 SS round buttons (pause/gear/menu floaters)
    lateinit var play: Texture             // v5.3 white play triangle (RUN/RESUME glyphs)
    lateinit var white: Texture
    lateinit var disc: Texture            // hard-edged circle (UI pips, wheels, dots)
    lateinit var hazeBand: Texture        // symmetric horizon haze (soft both edges)
    lateinit var jetFlame: Texture        // v4.1 warm thruster glow under jetpack flyers
    lateinit var warmGlow: Texture        // v4.6 warm lamp halo + additive floor light pools
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

    // ── v5.4 STARTUP IMMUNITY ────────────────────────────────────────────
    // One bad Pixmap/glyph used to throw inside generate() → create() died →
    // the app closed instantly, every launch, on that device (no dialog, no
    // log the player could reach). Every texture now generates through a
    // guard: a failing texture is replaced by a tiny white substitute and
    // the failure is counted + logged. The game boots with visual glitches
    // instead of not booting at all.
    var genErrors = 0             // number of substituted textures
    var generated = false         // true after a successful full/partial generate() (safe dispose)
    var anyFatal = false          // true if even the guard's substitute Texture couldn't be created

    private fun subTex(): Texture = Texture(4, 4, Pixmap.Format.RGBA8888)

    private fun tex(name: String, c: () -> Texture): Texture =
        try { c() } catch (t: Throwable) {
            genErrors++
            com.badlogic.gdx.Gdx.app.error("DS-Tex", "texture '$name' failed — white substitute", t)
            try { subTex() } catch (_: Throwable) { anyFatal = true; throw t }
        }

    private fun texArray(name: String, count: Int, c: (Int) -> Texture): Array<Texture> =
        Array(count) { i -> tex("$name#$i") { c(i) } }

    private fun texNine(name: String, c: () -> NinePatch): NinePatch =
        try { c() } catch (t: Throwable) {
            genErrors++
            com.badlogic.gdx.Gdx.app.error("DS-Tex", "ninepatch '$name' failed — white substitute", t)
            try { NinePatch(subTex(), 1, 1, 1, 1) } catch (_: Throwable) { anyFatal = true; throw t }
        }

    fun generate() {
        genErrors = 0; anyFatal = false
        white = tex("white") { solid(4, 4, Color.WHITE) }
        disc = tex("disc") { radial(64, Color(1f, 1f, 1f, 1f), 0.86f) } // solid core, 14% feather
        hazeBand = tex("hazeBand") { horizonHaze(8, 256) }
        jetFlame = tex("jetFlame") { radial(128, Color(1f, 0.62f, 0.2f, 0.95f), 0.12f) }
        warmGlow = tex("warmGlow") { radial(128, Color(1f, 0.80f, 0.44f, 0.95f), 0.02f) }
        glow = tex("glow") { radial(128, Color(1f, 1f, 1f, 1f), 0f) }
        softShadow = tex("softShadow") { radial(128, Color(0f, 0f, 0f, 0.55f), 0.25f) }
        sky = tex("sky") { verticalGradient(8, 512, Palette.SKY_TOP, Palette.SKY_MID, Palette.SKY_LOW) }
        fog = tex("fog") { verticalGradientFade(8, 256, Palette.FOG) }
        cloudA = tex("cloudA") { cloud(260, 90, 42L) }
        cloudB = tex("cloudB") { cloud(200, 70, 77L) }
        // SS-style distant city: soft blue-violet haze silhouettes
        skylineFar = tex("skylineFar") { skyline(1024, 190, 5L, dark = 0xaebbe8, alpha = 0.8f, dense = false) }
        skylineNear = tex("skylineNear") { skyline(1024, 240, 11L, dark = 0x8b9cdd, alpha = 0.9f, dense = true) }
        vignette = tex("vignette") { radial(256, Color(0f, 0f, 0f, 0.5f), 0.72f) }
        edgeVignette = tex("edgeVignette") { edgeVignette(256) }
        rainbowBurst = tex("rainbowBurst") { burst(512) }
        coinFrames = texArray("coin", 10) { coin(72, it, 10) }
        powerIcons = texArray("powerIcon", 6) { arrayOf(magnetIcon(), starIcon(), shieldIcon(), boltIcon(), springIcon(), rocketIcon())[it] }
        navIcons = texArray("navIcon", 4) { arrayOf(navPerson(), navBag(), navTasks(), navGear())[it] }
        trophy = tex("trophy") { navTrophy() }
        previews = texArray("preview", CharacterDef.ALL.size) { characterPreview(CharacterDef.ALL[it]) }
        panelNine = texNine("panelNine") { roundedNine(64, 18, Color(1f, 1f, 1f, 1f), border = false) }
        buttonNine = texNine("buttonNine") { roundedNine(64, 18, Color(1f, 1f, 1f, 1f), border = true) }
        circleNine = texNine("circleNine") { circleNine() }
        play = tex("play") { playGlyph() }
        trainSides = texArray("trainSide", Palette.TRAIN_LIVERIES.size) { trainSide(it) }
        trainFronts = texArray("trainFront", Palette.TRAIN_LIVERIES.size) { trainFront(it) }
        trainRears = texArray("trainRear", Palette.TRAIN_LIVERIES.size) { trainRear(it) }
        trainRoofTex = tex("trainRoof") { trainRoof() }
        hazardTex = tex("hazard") { hazardStripes() }
        barrierRedTex = tex("barrierRed") { barrierStripes() }
        signTealTex = tex("signTeal") { signTeal() }
        containerTex = tex("container") { containerBox() }
        facades = texArray("facade", 4) { arrayOf(
            facade(0xe8b27d, 0xd9985f, 11L), facade(0xc96b4a, 0xb85a4a, 23L),
            facade(0x9fc5c0, 0x8fb6d9, 37L), facade(0xe8d5a8, 0xc78a6a, 53L)
        )[it] }
        glassTex = tex("glass") { glassTower() }
        trackTex = tex("track") { trackTile() }
        dirtTex = tex("dirt") { dirtTile() }
        wallTex = tex("wall") { wallTile() }
        tunnelTex = tex("tunnel") { tunnelTile() }
        if (genErrors > 0) com.badlogic.gdx.Gdx.app.log("DS-Tex", "generate finished with $genErrors substituted texture(s)")
        generated = true
    }

    /** v5.4: safe to call even if generate() never completed (retry path) —
     *  uninitialized lateinits are skipped, disposed fields can't double-free
     *  because callers reset [generated] afterwards. */
    fun dispose() {
        if (!generated) return
        try {
            listOf(glow, softShadow, sky, fog, cloudA, cloudB, skylineFar, skylineNear, vignette, edgeVignette, rainbowBurst, white, disc, hazeBand, jetFlame, warmGlow,
                trainRoofTex, hazardTex, signTealTex, containerTex, glassTex).forEach { it.dispose() }
            coinFrames.forEach { it.dispose() }
            powerIcons.forEach { it.dispose() }
            navIcons.forEach { it.dispose() }
            trophy.dispose()
            play.dispose()
            previews.forEach { it.dispose() }
            trainSides.forEach { it.dispose() }
            trainFronts.forEach { it.dispose() }
            trainRears.forEach { it.dispose() }
            facades.forEach { it.dispose() }
            trackTex.dispose(); dirtTex.dispose(); wallTex.dispose(); tunnelTex.dispose()
        } catch (t: Throwable) {
            com.badlogic.gdx.Gdx.app.error("DS-Tex", "dispose partial failure (ignored)", t)
        }
        generated = false
    }

    /** Ballast + wooden sleepers + steel rails — one tile = 10.6u wide × 3.5u
     *  deep, geometry-matched to the 3D track strip (v4.4): lane centers at
     *  ±2.5u (LANE_WIDTH), rails at ±0.88u per lane, tie every 1.75u. The old
     *  tile was authored for a 3-lanes-per-tile layout at 1.42 repeats — rails
     *  landed at ±4.66u where no lane exists. */
    private fun trackTile(): Texture {
        val s = 256
        val p = Pixmap(s, s, Pixmap.Format.RGBA8888)
        // ballast base
        p.setColor(0x8f8578ff.toInt()); p.fill()
        val rnd = java.util.Random(9L)
        // v4.4: finer, subtler speckle — the old 2-5px chips magnified into
        // cow-print blotches at 0.04u/texel
        for (i in 0 until 1500) {
            val g = (0.84f + rnd.nextFloat() * 0.32f)
            p.setColor((0.56f * g).coerceAtMost(1f), (0.52f * g).coerceAtMost(1f), (0.47f * g).coerceAtMost(1f), 1f)
            p.fillRectangle(rnd.nextInt(s), rnd.nextInt(s), 1 + rnd.nextInt(2), 1 + rnd.nextInt(2))
        }
        val pxU = s / 10.6f // 24.15 px per world unit across
        // dark ties across every lane (lane centers at -2.5, 0, +2.5)
        val tieH = 33
        var ty = 60
        while (ty < s) {
            for (lane in -1..1) {
                val cx = (s / 2f + lane * 2.5f * pxU)
                val x0 = (cx - 1.05f * pxU).toInt()
                val tw = (2.1f * pxU).toInt()
                p.setColor(0x4a3a2aff.toInt()); p.fillRectangle(x0, ty, tw, tieH)
                p.setColor(0x5c4834ff.toInt()); p.fillRectangle(x0, ty, tw, 6)
                p.setColor(0x3a2d20ff.toInt()); p.fillRectangle(x0, ty + tieH - 5, tw, 5)
            }
            ty += 128
        }
        // steel rails (2 per lane at ±0.88u) with shine
        for (lane in -1..1) {
            for (off in floatArrayOf(-0.88f, 0.88f)) {
                val rx = (s / 2f + (lane * 2.5f + off) * pxU).toInt() - 3
                p.setColor(0x6a6f76ff.toInt()); p.fillRectangle(rx, 0, 6, s)
                p.setColor(0xd9dde2ff.toInt()); p.fillRectangle(rx + 1, 0, 2, s)
                p.setColor(0x9aa0a8ff.toInt()); p.fillRectangle(rx + 4, 0, 2, s)
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
        // v4.4: finer speckle (old 2-5px chips magnified into blotches)
        for (i in 0 until 700) {
            val g = 0.82f + rnd.nextFloat() * 0.36f
            p.setColor((0.61f * g).coerceAtMost(1f), (0.54f * g).coerceAtMost(1f), (0.42f * g).coerceAtMost(1f), 1f)
            p.fillRectangle(rnd.nextInt(s), rnd.nextInt(s), 1 + rnd.nextInt(2), 1 + rnd.nextInt(2))
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

    /** v4.5 inverse radial: fully transparent through 62% of the radius, then a
     *  smooth darkening to the corners — a real EDGE vignette. The old danger/
     *  boost pulses reused the center-dark radial and read as a giant grey EGG
     *  smeared over the whole screen whenever the guard stayed close (DS_CHASE QA). */
    private fun edgeVignette(size: Int): Texture {
        val p = Pixmap(size, size, Pixmap.Format.RGBA8888)
        val half = size / 2f
        for (y in 0 until size) for (x in 0 until size) {
            val dx = (x - half) / half
            val dy = (y - half) / half
            // corners are at d≈1.41 — normalize so the frame edges sit at ~0.86
            val d = kotlin.math.sqrt(dx * dx + dy * dy) / 1.41f * 2f
            val a = ((d - 0.62f) / 0.38f).coerceIn(0f, 1f)
            val aa = a * a * (3f - 2f * a)
            p.setColor(0f, 0f, 0f, aa)
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

    // ── v4.5 menu tab glyphs (flat white, drawn on the navy tab tiles) ────
    private fun navPerson(): Texture {
        val s = 96
        val p = iconBase(s)
        p.setColor(Color.WHITE)
        p.fillCircle(s / 2, (s * 0.32f).toInt(), (s * 0.18f).toInt())
        fillEllipse(p, s / 2f, s * 0.82f, s * 0.3f, s * 0.22f)
        return tex(p)
    }

    private fun navBag(): Texture {
        val s = 96
        val p = iconBase(s)
        p.setColor(Color.WHITE)
        // shopping-bag handle arch + body
        p.fillRectangle((s * 0.36f).toInt(), (s * 0.28f).toInt(), (s * 0.07f).toInt(), (s * 0.16f).toInt())
        p.fillRectangle((s * 0.57f).toInt(), (s * 0.28f).toInt(), (s * 0.07f).toInt(), (s * 0.16f).toInt())
        p.fillRectangle((s * 0.36f).toInt(), (s * 0.28f).toInt(), (s * 0.28f).toInt(), (s * 0.06f).toInt())
        p.fillRectangle((s * 0.24f).toInt(), (s * 0.42f).toInt(), (s * 0.52f).toInt(), (s * 0.42f).toInt())
        return tex(p)
    }

    private fun navTasks(): Texture {
        val s = 96
        val p = iconBase(s)
        p.setColor(Color.WHITE)
        // clipboard: clip + board + navy text lines
        p.fillRectangle((s * 0.4f).toInt(), (s * 0.14f).toInt(), (s * 0.2f).toInt(), (s * 0.1f).toInt())
        p.fillRectangle((s * 0.26f).toInt(), (s * 0.2f).toInt(), (s * 0.48f).toInt(), (s * 0.62f).toInt())
        p.setColor(Palette.UI_NAVY)
        p.fillRectangle((s * 0.34f).toInt(), (s * 0.34f).toInt(), (s * 0.32f).toInt(), (s * 0.06f).toInt())
        p.fillRectangle((s * 0.34f).toInt(), (s * 0.48f).toInt(), (s * 0.32f).toInt(), (s * 0.06f).toInt())
        p.fillRectangle((s * 0.34f).toInt(), (s * 0.62f).toInt(), (s * 0.2f).toInt(), (s * 0.06f).toInt())
        return tex(p)
    }

    // v4.7 gold trophy — cup bowl + handles + stem + base, flat gold on alpha
    private fun navTrophy(): Texture {
        val s = 96
        val p = iconBase(s)
        val gold = Color(0xffc93aff.toInt())
        val goldDim = Color(0xd9a12bff.toInt())
        // handles: rings left/right (outer gold circle, punched transparent center)
        p.setColor(gold)
        p.fillCircle((s * 0.185f).toInt(), (s * 0.36f).toInt(), (s * 0.115f).toInt())
        p.fillCircle((s * 0.815f).toInt(), (s * 0.36f).toInt(), (s * 0.115f).toInt())
        p.setBlending(Pixmap.Blending.None); p.setColor(0f, 0f, 0f, 0f)
        p.fillCircle((s * 0.185f).toInt(), (s * 0.36f).toInt(), (s * 0.055f).toInt())
        p.fillCircle((s * 0.815f).toInt(), (s * 0.36f).toInt(), (s * 0.055f).toInt())
        p.setBlending(Pixmap.Blending.SourceOver)
        // bowl: wide ellipse + lower lip
        p.setColor(gold)
        fillEllipse(p, s * 0.5f, s * 0.34f, s * 0.27f, s * 0.21f)
        p.fillRectangle((s * 0.23f).toInt(), (s * 0.34f).toInt(), (s * 0.54f).toInt(), (s * 0.1f).toInt())
        // shine sliver on the bowl
        p.setColor(Color(1f, 1f, 1f, 0.5f))
        fillEllipse(p, s * 0.4f, s * 0.27f, s * 0.05f, s * 0.07f)
        // stem + base
        p.setColor(goldDim)
        p.fillRectangle((s * 0.44f).toInt(), (s * 0.52f).toInt(), (s * 0.12f).toInt(), (s * 0.14f).toInt())
        p.fillRectangle((s * 0.32f).toInt(), (s * 0.66f).toInt(), (s * 0.36f).toInt(), (s * 0.09f).toInt())
        p.setColor(gold)
        p.fillRectangle((s * 0.32f).toInt(), (s * 0.66f).toInt(), (s * 0.36f).toInt(), (s * 0.035f).toInt())
        return tex(p)
    }

    private fun navGear(): Texture {
        val s = 96
        val p = iconBase(s)
        p.setColor(Color.WHITE)
        p.fillCircle(s / 2, s / 2, (s * 0.2f).toInt())
        for (i in 0 until 8) {
            val ang = i * PI.toFloat() / 4f
            p.fillCircle((s / 2f + cos(ang) * s * 0.3f).toInt(), (s / 2f + kotlin.math.sin(ang) * s * 0.3f).toInt(), (s * 0.1f).toInt())
        }
        // punch a transparent axle hole through the middle
        p.setBlending(Pixmap.Blending.None)
        p.setColor(0f, 0f, 0f, 0f)
        p.fillCircle(s / 2, s / 2, (s * 0.09f).toInt())
        p.setBlending(Pixmap.Blending.SourceOver)
        return tex(p)
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

    /** v4.1: SUPER JUMP icon — gold spring coil with a bounce arc (rocket freed for JETPACK). */
    private fun springIcon(): Texture {
        val s = 96
        val p = iconBase(s)
        val gold = Palette.GOLD
        val cx = s / 2f
        // coil: 3 stacked ellipse bars
        p.setColor(gold)
        for (i in 0 until 3) {
            val y = s * (0.44f + i * 0.14f)
            fillEllipse(p, cx, y, s * 0.26f, s * 0.055f)
        }
        // top cap (the jumper's feet plate)
        p.setColor(Color.WHITE)
        p.fillRectangle((s * 0.3f).toInt(), (s * 0.3f).toInt(), (s * 0.4f).toInt(), (s * 0.09f).toInt())
        // bounce arc + dot
        p.setColor(Color(0x9adcf0ff.toInt()))
        for (a in 0 until 180) {
            val ang = a * PI.toFloat() / 180f
            val x = cx - kotlin.math.cos(ang) * s * 0.3f
            val y = s * 0.82f - kotlin.math.sin(ang) * s * 0.22f
            p.fillCircle(x.toInt(), y.toInt(), 2)
        }
        p.setColor(gold)
        p.fillCircle(cx.toInt(), (s * 0.16f).toInt(), (s * 0.07f).toInt())
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

    /** Rounded-rect fill (Pixmap has no rounded API — compose rects + circles). */
    private fun fillRoundRect(p: Pixmap, x: Int, y: Int, w: Int, h: Int, r: Int) {
        p.fillRectangle(x + r, y, w - r * 2, h)
        p.fillRectangle(x, y + r, w, h - r * 2)
        p.fillCircle(x + r, y + r, r)
        p.fillCircle(x + w - r, y + r, r)
        p.fillCircle(x + r, y + h - r, r)
        p.fillCircle(x + w - r, y + h - r, r)
    }

    /**
     * v5.3 SS-JELLY BUTTON — real Subway Surfers buttons are jelly: dark navy
     * outline all around, saturated fill, bright gloss band on top, chunky
     * darker bottom lip that sells the 3D press. Everything is baked as
     * white/alpha over white so the per-draw tint colors the whole shape.
     * (The old ninepatch was a flat slab with a hairline gloss — buttons read
     * as sticky notes, not candy.)
     */
    private fun roundedNine(size: Int, radius: Int, @Suppress("UNUSED_PARAMETER") color: Color, border: Boolean): NinePatch {
        val p = Pixmap(size, size, Pixmap.Format.RGBA8888)
        val r = radius
        if (border) {
            // outline ring — navy, tint-multiplies dark on any fill color
            p.setColor(0.10f, 0.12f, 0.27f, 1f)
            fillRoundRect(p, 1, 1, size - 2, size - 2, r)
            // base fill (white → tinted per draw)
            p.setColor(1f, 1f, 1f, 1f)
            fillRoundRect(p, 5, 5, size - 10, size - 10, (r - 4).coerceAtLeast(6))
            // chunky bottom lip — darker band hugging the inside bottom
            p.setColor(0f, 0f, 0f, 0.22f)
            fillRoundRect(p, 5, size - 16, size - 10, 11, 6)
            // top gloss band
            p.setColor(1f, 1f, 1f, 0.34f)
            fillRoundRect(p, 9, 8, size - 18, 9, 5)
            // gloss corner dot (candy sparkle)
            p.setColor(1f, 1f, 1f, 0.5f)
            p.fillCircle(15, 12, 3)
        } else {
            // panels: soft frosted card — white base, faint top sheen,
            // whisper of bottom shade (tinted periwinkle/navy per draw)
            p.setColor(1f, 1f, 1f, 1f)
            fillRoundRect(p, 1, 1, size - 2, size - 2, r)
            p.setColor(1f, 1f, 1f, 0.18f)
            fillRoundRect(p, 4, 3, size - 8, 8, 4)
            p.setColor(0f, 0f, 0f, 0.10f)
            fillRoundRect(p, 3, size - 11, size - 6, 8, 4)
        }
        val t = Texture(p); p.dispose()
        val m = radius + 3
        return NinePatch(t, m, m, m, m)
    }

    /**
     * v5.3 SS ROUND BUTTON — jelly circle for pause/gear/menu floaters:
     * navy ring, white fill (tinted), bottom lip via a lower dark disc that
     * only peeks under the fill disc, round gloss dot top-center.
     */
    private fun circleNine(): NinePatch {
        val s = 64
        val p = Pixmap(s, s, Pixmap.Format.RGBA8888)
        val c = s / 2f
        // outline ring
        p.setColor(0.10f, 0.12f, 0.27f, 1f)
        p.fillCircle(c.toInt(), c.toInt(), s / 2 - 1)
        // bottom lip disc (drawn first, only its bottom sliver stays visible)
        p.setColor(0f, 0f, 0f, 0.26f)
        p.fillCircle(c.toInt(), (c + 3).toInt(), s / 2 - 5)
        // base fill
        p.setColor(1f, 1f, 1f, 1f)
        p.fillCircle(c.toInt(), c.toInt(), s / 2 - 5)
        // gloss band (a rounded strip that stays inside the disc silhouette)
        p.setColor(1f, 1f, 1f, 0.34f)
        fillRoundRect(p, 20, 10, 24, 8, 4)
        val t = Texture(p); p.dispose()
        return NinePatch(t, 22, 22, 22, 22)
    }

    /** v5.3 white play triangle for RUN / RESUME jelly buttons. */
    private fun playGlyph(): Texture {
        val s = 64
        val p = Pixmap(s, s, Pixmap.Format.RGBA8888)
        p.setColor(1f, 1f, 1f, 1f)
        p.fillTriangle(16, 8, 16, 56, 54, 32)
        val t = Texture(p); p.dispose()
        return t
    }

    // ── SS-chibi character portraits (menu + shop) ─────────────────────
    // Front-facing big-head chibi with face, cap, hoodie, straps, sneakers.
    // Proportions: head ≈ 43% of total height (Subway Surfers DNA).
    private fun mul(c: Int, f: Float): Int {
        val col = Color(c)
        // v4.5 FIX: same ABGR/RGBA round-trip pitfall as Character3D.mul —
        // Color.toIntBits() puts ALPHA in the high byte; Color(int) reads RED
        // from it. Pack RGBA8888 explicitly so preview shades stay true.
        fun ch(v: Float) = ((v * f).coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
        return (ch(col.r) shl 24) or (ch(col.g) shl 16) or (ch(col.b) shl 8) or (c and 0xFF)
    }

    /**
     * v5.2 SS-CHIBI PORTRAIT REBUILD — the v4.3 painter read as a mushroom
     * robot (hair circle behind the face plate, balloon dome, stick arms).
     * New anatomy, real Subway-Surfers DNA:
     *   • ONE big skin face circle (no hair disc sandwich) — head ≈ 45% height
     *   • cap = clipped dome + edge strap + seam + back brim tips + button
     *   • scalloped hair fringe peeking under the cap edge
     *   • big oval eyes w/ iris+pupil+double glints, brows, nose, open grin
     *     w/ teeth + tongue, blush
     *   • hoodie w/ drawstrings + pocket, pack straps, capsule arms, chunky
     *     white-soled sneakers, grounded shadow
     */
    private fun characterPreview(ch: CharacterDef): Texture {
        val s = 360
        val p = Pixmap(s, s, Pixmap.Format.RGBA8888)
        val cx = s / 2f
        val OUT = 0x24316bff.toInt()

        // geometry (feet at y=344)
        val headR = 86f
        val headCY = 138f
        val shoulderY = 226f
        val hipY = 296f
        val footY = 344f
        val capEdge = headCY - 16f // where the dome is cut flat above the brows

        fun circ(x: Float, y: Float, r: Float, col: Int) {
            p.setColor(OUT); p.fillCircle(x.toInt(), y.toInt(), (r + 3.5f).toInt())
            p.setColor(col); p.fillCircle(x.toInt(), y.toInt(), r.toInt())
        }
        fun rect(x: Float, y: Float, w: Float, h: Float, col: Int) {
            p.setColor(OUT); p.fillRectangle((x - 3.5f).toInt(), (y - 3.5f).toInt(), (w + 7f).toInt(), (h + 7f).toInt())
            p.setColor(col); p.fillRectangle(x.toInt(), y.toInt(), w.toInt(), h.toInt())
        }
        fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float, col: Int) {
            rect(x + r, y, w - r * 2, h, col)
            rect(x, y + r, w, h - r * 2, col)
            circ(x + r, y + r, r, col); circ(x + w - r, y + r, r, col)
            circ(x + r, y + h - r, r, col); circ(x + w - r, y + h - r, r, col)
        }
        fun bare(x: Int, y: Int, w: Int, h: Int, col: Int) { p.setColor(col); p.fillRectangle(x, y, w, h) }
        fun softCol(col: Int, a: Float) { val c = Color(col); p.setColor(c.r, c.g, c.b, a) }

        // grounding shadow inside the texture — the hero never floats
        softCol(0x1c2440ff.toInt(), 0.30f)
        var si = 0
        while (si < 12) {
            val t = si / 11f
            val w = (88f * kotlin.math.sqrt(1f - (t - 0.5f) * (t - 0.5f) * 4f)).toInt().coerceAtLeast(4)
            p.fillRectangle((cx - w).toInt(), (footY - 12f + si * 2f).toInt(), w * 2, 2)
            si++
        }

        // ── LEGS — jeans w/ knee hint + cuff ────────────────────────────
        val legW = 33f
        for (side in intArrayOf(-1, 1)) {
            val lx = cx + side * 21f - legW / 2
            roundRect(lx, hipY - 6f, legW, footY - hipY - 8f, 10f, ch.pants)
            bare((lx + 7f).toInt(), (hipY + 24f).toInt(), (legW - 14f).toInt(), 8, mul(ch.pants, 1.22f))
            bare((lx + 3f).toInt(), (footY - 34f).toInt(), (legW - 6f).toInt(), 6, mul(ch.pants, 0.78f))
        }

        // ── SNEAKERS — chunky, white mid-sole + toe cap + lace band ────
        for (side in intArrayOf(-1, 1)) {
            val sx = cx + side * 21f
            roundRect(sx - 27f, footY - 30f, 54f, 22f, 10f, ch.shoes)
            // white mid-sole + dark out-sole line
            bare((sx - 27f).toInt(), (footY - 12f).toInt(), 54, 8, 0xf2f3f5ff.toInt())
            p.setColor(OUT); p.fillRectangle((sx - 29f).toInt(), (footY - 4f).toInt(), 58, 4)
            // toe cap (white rounded patch at the inner-front)
            circ(sx + side * 10f, footY - 16f, 11f, 0xf2f3f5ff.toInt())
            // lace band
            bare((sx - 13f).toInt(), (footY - 26f).toInt(), 26, 6, mul(ch.shoes, 0.68f))
            // heel tab
            bare((sx - 3f).toInt(), (footY - 33f).toInt(), 6, 6, mul(ch.shoes, 0.8f))
        }

        // ── TORSO — hoodie w/ pocket, drawstrings, straps ──────────────
        roundRect(cx - 58f, shoulderY, 116f, hipY - shoulderY + 20f, 26f, ch.hoodie)
        bare((cx - 54f).toInt(), (hipY + 8f).toInt(), 108, 8, mul(ch.hoodie, 0.8f)) // hem
        // front kangaroo pocket
        roundRect(cx - 34f, hipY - 8f, 68f, 30f, 11f, mul(ch.hoodie, 0.88f))
        bare((cx - 34f).toInt(), (hipY + 5f).toInt(), 68, 3, mul(ch.hoodie, 0.62f))
        // undershirt collar hint
        if (ch.hoodLining != 0) {
            softCol(ch.hoodLining, 1f)
            p.fillRectangle((cx - 16f).toInt(), (shoulderY + 6f).toInt(), 32, 14)
            p.setColor(OUT)
            p.fillRectangle((cx - 16f).toInt(), (shoulderY + 6f).toInt(), 3, 14)
            p.fillRectangle((cx + 13f).toInt(), (shoulderY + 6f).toInt(), 3, 14)
            // white drawstrings w/ knot dots (the thumbnail signature)
            p.setColor(0xf5f5f0ff.toInt())
            p.fillRectangle((cx - 8).toInt(), (shoulderY + 18f).toInt(), 4, 24)
            p.fillRectangle((cx + 4).toInt(), (shoulderY + 18f).toInt(), 4, 24)
            p.fillCircle((cx - 6f).toInt(), (shoulderY + 44f).toInt(), 3)
            p.fillCircle((cx + 6f).toInt(), (shoulderY + 44f).toInt(), 3)
        }
        // denim vest side stripes INSIDE the silhouette
        if (ch.vest != 0) {
            p.setColor(mul(ch.vest, 1.02f))
            p.fillRectangle((cx - 52f).toInt(), (shoulderY + 12f).toInt(), 14, (hipY - shoulderY - 2f).toInt())
            p.fillRectangle((cx + 38f).toInt(), (shoulderY + 12f).toInt(), 14, (hipY - shoulderY - 2f).toInt())
            p.setColor(mul(ch.vest, 0.76f))
            p.fillRectangle((cx - 39f).toInt(), (shoulderY + 12f).toInt(), 4, (hipY - shoulderY - 2f).toInt())
            p.fillRectangle((cx + 35f).toInt(), (shoulderY + 12f).toInt(), 4, (hipY - shoulderY - 2f).toInt())
        }
        // backpack straps over the chest + gold buckles
        for (side in intArrayOf(-1, 1)) {
            p.setColor(mul(ch.backpack, 0.9f))
            p.fillRectangle((cx + side * 35f - 7f).toInt(), (shoulderY + 8f).toInt(), 14, (hipY - shoulderY - 8f).toInt())
            p.setColor(mul(ch.backpack, 1.1f))
            p.fillRectangle((cx + side * 35f - 7f).toInt(), (shoulderY + 8f).toInt(), 4, (hipY - shoulderY - 8f).toInt())
            p.setColor(ch.accent)
            p.fillRectangle((cx + side * 35f - 8f).toInt(), (shoulderY + 14f).toInt(), 16, 6)
        }

        // ── ARMS — capsule sleeves + skin hands ────────────────────────
        for (side in intArrayOf(-1, 1)) {
            val sx2 = cx + side * 62f
            roundRect(sx2 - 16f, shoulderY + 4f, 32f, 62f, 16f, ch.hoodie)
            bare((sx2 - 14f).toInt(), (shoulderY + 56f).toInt(), 28, 8, mul(ch.hoodie, 0.7f))
            circ(sx2, shoulderY + 76f, 13f, ch.skin)
        }

        // ── HEAD — ears first, then the big face circle ────────────────
        for (side in intArrayOf(-1, 1)) circ(cx + side * (headR - 3f), headCY + 14f, 13f, ch.skin)
        circ(cx, headCY, headR, ch.skin)
        // soft cheek shading (very subtle inner crescent)
        softCol(0x000000ff.toInt(), 0.05f)
        p.fillCircle(cx.toInt(), (headCY + 14f).toInt(), (headR - 6).toInt())

        // ── CAP — scanline-clipped dome + strap + seams + brim tips ────
        // (Pixmap has no clip API — the dome is a circle cut flat at capEdge
        // by drawing it as per-row spans, which also gives crisp navy edges)
        val domeCY = capEdge - 22f
        val domeTop = (domeCY - headR).toInt()
        fun domeRow(yy: Int, col: Int, margin: Float) {
            val dy = yy - domeCY
            val hw = kotlin.math.sqrt(headR * headR - dy * dy)
            p.setColor(col)
            p.fillRectangle((cx - hw - margin).toInt(), yy, ((hw + margin) * 2f).toInt(), 1)
        }
        var yy = domeTop
        while (yy <= capEdge.toInt()) { domeRow(yy, OUT, 3.5f); yy++ }
        yy = domeTop
        while (yy <= capEdge.toInt()) { domeRow(yy, ch.cap, 0f); yy++ }
        // seam stitch lines on the dome
        p.setColor(mul(ch.cap, 0.8f))
        p.fillRectangle((cx - 24).toInt(), domeTop + 18, 3, 58)
        p.fillRectangle((cx + 21).toInt(), domeTop + 18, 3, 58)
        // hair fringe scallops peeking under the cap edge (strap covers tops)
        // v5.2.1: the old side "brim tip" discs are GONE — they read as weird
        // handlebar blobs at the cap's edges in every menu screenshot
        for (i in 0..6) {
            val fx = cx - 57f + i * 19f
            p.setColor(OUT); p.fillCircle(fx.toInt(), (capEdge + 3f).toInt(), 11)
            p.setColor(ch.hair); p.fillCircle(fx.toInt(), (capEdge + 4f).toInt(), 8)
        }
        // edge strap band hugging the cap edge + center buckle
        roundRect(cx - 56f, capEdge - 13f, 112f, 15f, 7f, mul(ch.cap, 0.85f))
        p.setColor(mul(ch.cap, 0.6f))
        p.fillRectangle((cx - 56).toInt(), (capEdge - 3f).toInt(), 112, 3)
        p.setColor(ch.accent)
        p.fillRectangle((cx - 8f).toInt(), (capEdge - 10f).toInt(), 16, 9)
        p.setColor(mul(ch.accent, 0.6f))
        p.fillRectangle((cx - 2f).toInt(), (capEdge - 8f).toInt(), 3, 5)
        // top button
        circ(cx, capEdge - headR - 14f, 7f, mul(ch.cap, 1.14f))
        if (ch.capPanel != 0) {
            // v5.2.1: front badge = clean rounded-square patch (the old white
            // circle read as a balloon egg); tiny accent dot keeps it lively
            roundRect(cx - 17f, capEdge - 92f, 34f, 26f, 7f, ch.capPanel)
            p.setColor(mul(ch.capPanel, 0.72f))
            p.fillRectangle((cx - 17f).toInt(), (capEdge - 72f).toInt(), 34, 3)
            p.setColor(mul(ch.capPanel, 0.55f))
            p.fillCircle(cx.toInt(), (capEdge - 79f).toInt(), 5)
        }

        // ── FACE — SS-grade cartoon features ───────────────────────────
        // v5.2.1 eye retune: closer together (±33→±31), bigger centered iris
        // and ONE big glint + one tiny (the double mid-size glints read as
        // wonky googly eyes in the menu screenshot)
        val eyeY = headCY + 18f
        for (side in intArrayOf(-1, 1)) {
            val ex = cx + side * 31f
            // brow — friendly arch built from three overlapping discs
            p.setColor(mul(ch.hair, 0.72f))
            p.fillCircle((ex - 12f).toInt(), (capEdge + 12f).toInt(), 5)
            p.fillCircle(ex.toInt(), (capEdge + 8f).toInt(), 6)
            p.fillCircle((ex + 12f).toInt(), (capEdge + 12f).toInt(), 5)
            p.fillRectangle((ex - 12f).toInt(), (capEdge + 9f).toInt(), 24, 6)
            // eye: white oval → iris → pupil → big + tiny glint
            p.setColor(0xffffffff.toInt())
            fillEllipse(p, ex, eyeY, 16.5f, 18.5f)
            p.setColor(0x5a3a1fff.toInt()); fillEllipse(p, ex, eyeY + 1f, 12f, 13f)
            p.setColor(0x24160aff.toInt()); fillEllipse(p, ex, eyeY + 2f, 6.5f, 7.5f)
            p.setColor(0xffffffff.toInt())
            fillEllipse(p, ex - 5f, eyeY - 4f, 5f, 5f)
            fillEllipse(p, ex + 5f, eyeY + 6f, 2.2f, 2.2f)
            // blush
            p.setColor(0.95f, 0.55f, 0.5f, 0.38f)
            p.fillCircle((cx + side * 60f).toInt(), (eyeY + 22f).toInt(), 9)
        }
        // nose
        p.setColor(mul(ch.skin, 0.88f))
        p.fillCircle(cx.toInt(), (eyeY + 16f).toInt(), 5)
        // open grin: dark mouth, white teeth, pink tongue (teeth narrowed so
        // the band no longer reads as a grid across the smile)
        p.setColor(0x5e2c1dff.toInt())
        fillEllipse(p, cx, eyeY + 32f, 15f, 12f)
        p.setColor(0x7a3a26ff.toInt())
        fillEllipse(p, cx, eyeY + 36f, 10f, 6f)
        p.setColor(0xffffffff.toInt())
        p.fillRectangle((cx - 8f).toInt(), (eyeY + 21f).toInt(), 16, 6)
        p.setColor(0xe8836fff.toInt())
        fillEllipse(p, cx, eyeY + 38f, 7f, 4f)
        p.setColor(OUT)
        p.fillRectangle((cx - 9f).toInt(), (eyeY + 20f).toInt(), 18, 2)

        // ── signature accessories on the cards ─────────────────────────
        if (ch.accessory == 2) {
            // VOLT cap goggles: strap across the dome + teal lens + white glint
            p.setColor(OUT); p.fillRectangle((cx - 60f).toInt(), (capEdge - 66f).toInt(), 120, 13)
            p.setColor(0x2ec4d9ff.toInt()); p.fillRectangle((cx - 56f).toInt(), (capEdge - 64f).toInt(), 112, 9)
            circ(cx + 36f, capEdge - 42f, 16f, 0x2ec4d9ff.toInt())
            p.setColor(0xdff8fbff.toInt()); p.fillCircle((cx + 36f).toInt(), (capEdge - 47f).toInt(), 5)
        } else if (ch.accessory == 3) {
            // NOVA headphones: band tab over the button + teal cups on the ears
            p.setColor(OUT); p.fillRectangle((cx - 10f).toInt(), (capEdge - headR - 16f).toInt(), 20, 12)
            p.setColor(0xb48ce0ff.toInt()); p.fillRectangle((cx - 7f).toInt(), (capEdge - headR - 13f).toInt(), 14, 6)
            circ(cx - headR + 10f, headCY + 12f, 18f, 0x25a89aff.toInt())
            circ(cx + headR - 10f, headCY + 12f, 18f, 0x25a89aff.toInt())
            p.setColor(0xb48ce0ff.toInt())
            p.fillCircle((cx - headR + 10f).toInt(), (headCY + 12f).toInt(), 9)
            p.fillCircle((cx + headR - 10f).toInt(), (headCY + 12f).toInt(), 9)
        } else if (ch.accessory == 1) {
            // BLAZE spray can tucked in the right hand
            val canX = cx + 62f; val canY = shoulderY + 58f
            roundRect(canX - 9f, canY - 16f, 18f, 30f, 5f, ch.accent)
            bare((canX - 9f).toInt(), (canY - 4f).toInt(), 18, 4, mul(ch.accent, 0.6f))
            bare((canX - 5f).toInt(), (canY - 21f).toInt(), 10, 5, 0xc9ced6ff.toInt())
            bare((canX - 2f).toInt(), (canY - 25f).toInt(), 4, 4, 0x22262cff.toInt())
        }

        return tex(p)
    }

    // ── FaceBatch material generators (textured pseudo-3D world) ────────

    /**
     * Carriage side per livery — 20-c SS-fidelity rebuild:
     * 4 BIG rounded navy windows w/ diagonal sky reflections + white glints,
     * a distinct passenger door with split line + tall slots between window
     * groups, clean white livery band, panel seams + rivet dots, chunky dark
     * bogies, and hazard-edge + graffiti on the freight liveries only.
     * NOTE (FaceBatch.faceSide): texture LEFT column = FAR end of the car.
     */
    private fun trainSide(liveryIdx: Int): Texture {
        val w = 256; val h = 128
        val p = Pixmap(w, h, Pixmap.Format.RGBA8888)
        val liv = Palette.TRAIN_LIVERIES[liveryIdx]
        val body = Color(liv[0]); val skirt = Color(liv[1]); val band = Color(liv[2])
        val freight = liveryIdx == 1 || liveryIdx == 5
        val GLASS = 0x14294dff.toInt()
        val GLASS_OUT = 0x0d1b33ff.toInt()

        // body
        p.setColor(body); p.fillRectangle(0, 0, w, h)
        // sun on the upper shoulder
        p.setColor(1f, 1f, 1f, 0.13f); p.fillRectangle(0, 9, w, 7)

        // roof edge strip + glint + shadow crease
        p.setColor(Palette.TRAIN_ROOF); p.fillRectangle(0, 0, w, 8)
        p.setColor(1f, 1f, 1f, 0.35f); p.fillRectangle(0, 0, w, 2)
        p.setColor(0f, 0f, 0f, 0.22f); p.fillRectangle(0, 8, w, 2)

        // panel seams + rivet dots
        p.setColor(0f, 0f, 0f, 0.10f)
        p.fillRectangle(2, 10, 1, 92); p.fillRectangle(w - 3, 10, 1, 92)
        p.fillRectangle(126, 10, 1, 92)
        p.setColor(0f, 0f, 0f, 0.15f)
        var rvx = 10
        while (rvx < w - 6) { p.fillRectangle(rvx, 11, 2, 2); rvx += 18 }

        // 4 big rounded windows, two per side of the centered door
        val winY = 16; val winH = 42; val winW = 36
        val winXs = intArrayOf(12, 58, 162, 208)
        for (x0 in winXs) {
            p.setColor(GLASS_OUT)
            fillRoundRect(p, x0 - 3, winY - 3, winW + 6, winH + 6, 9)
            p.setColor(GLASS)
            fillRoundRect(p, x0, winY, winW, winH, 7)
            // diagonal sky-blue reflection streak (bottom-left → top-right)
            p.setColor(0x9adcf0ff.toInt())
            var i = 0
            while (i < 11) { p.fillRectangle(x0 + 4 + i, winY + 26 - i, 3, 9); i++ }
            p.setColor(0xbfe8ffff.toInt())
            i = 0
            while (i < 6) { p.fillRectangle(x0 + 22 + i, winY + 32 - i, 2, 6); i++ }
            // white glint + lower shade
            p.setColor(1f, 1f, 1f, 0.85f)
            fillRoundRect(p, x0 + 4, winY + 4, 9, 5, 2)
            p.setColor(0f, 0f, 0f, 0.20f)
            p.fillRectangle(x0 + 2, winY + winH - 8, winW - 4, 6)
        }

        // passenger door: tall block, center split, two tall slots
        p.setColor(skirt)
        fillRoundRect(p, 110, 14, 36, 86, 7)
        p.setColor(0f, 0f, 0f, 0.28f); p.fillRectangle(127, 16, 2, 82)
        p.setColor(GLASS_OUT)
        fillRoundRect(p, 116, 22, 10, 32, 4)
        fillRoundRect(p, 130, 22, 10, 32, 4)
        p.setColor(GLASS)
        fillRoundRect(p, 117, 23, 8, 30, 3)
        fillRoundRect(p, 131, 23, 8, 30, 3)
        p.setColor(0x9adcf0ff.toInt())
        p.fillRectangle(118, 40, 6, 3); p.fillRectangle(132, 40, 6, 3)
        p.setColor(1f, 1f, 1f, 0.28f)
        p.fillRectangle(116, 22, 10, 3); p.fillRectangle(130, 22, 10, 3)
        p.setColor(0f, 0f, 0f, 0.22f)
        p.fillRectangle(112, 94, 32, 4)

        // signature livery band across the full side (under the windows)
        p.setColor(band); p.fillRectangle(0, 64, w, 11)
        p.setColor(1f, 1f, 1f, 0.40f); p.fillRectangle(0, 64, w, 3)
        p.setColor(0f, 0f, 0f, 0.12f); p.fillRectangle(0, 73, w, 2)

        // skirt + crease
        p.setColor(skirt); p.fillRectangle(0, 100, w, 20)
        p.setColor(0f, 0f, 0f, 0.25f); p.fillRectangle(0, 100, w, 2)
        p.setColor(0f, 0f, 0f, 0.32f); p.fillRectangle(0, 124, w, 4)

        // freight only: yellow/black hazard edge on the skirt
        if (freight) {
            p.setColor(Palette.HAZARD_YELLOW); p.fillRectangle(0, 103, w, 9)
            p.setColor(Palette.HAZARD_BLACK)
            var hx = -10
            while (hx < w + 10) {
                for (k in 0 until 9) {
                    val xx = hx + k
                    if (xx in 0 until w) { p.drawPixel(xx, 103 + k); p.drawPixel(xx, 104 + k); p.drawPixel(xx, 105 + k) }
                }
                hx += 14
            }
        }

        // chunky dark bogies + wheels
        p.setColor(0x171920ff.toInt())
        fillRoundRect(p, 22, 108, 52, 16, 5)
        fillRoundRect(p, 146, 108, 52, 16, 5)
        for (wx in intArrayOf(30, 62, 154, 186)) {
            p.setColor(0x101218ff.toInt()); p.fillCircle(wx, 119, 8)
            p.setColor(0x59616cff.toInt()); p.fillCircle(wx, 119, 5)
            p.setColor(0x9aa2acff.toInt()); p.fillCircle(wx, 119, 2)
        }

        // graffiti on freight liveries (orange freight 1, violet 5)
        if (freight) {
            val rng = Random(100L + liveryIdx)
            val cols = intArrayOf(0xffd24aff.toInt(), 0x37b8a8ff.toInt(), 0xe2493bff.toInt(), 0xd8578aff.toInt(), 0x8ff2e2ff.toInt())
            for (g in 0 until 7) {
                val col = cols[rng.nextInt(cols.size)]
                val gx = 8 + rng.nextInt(w - 80)
                val gy = 78 + rng.nextInt(16)
                val gr = 4 + rng.nextInt(7)
                p.setColor(0x2b2622ff.toInt()); p.fillCircle(gx, gy, gr + 2)
                p.setColor(col); p.fillCircle(gx, gy, gr)
            }
            // spray tag underline
            p.setColor(0xffd24aff.toInt()); p.fillRectangle(40, 92, 90, 3)
        }
        return tex(p)
    }

    /** Lead-car front — 20-c: big rounded windscreen, destination board,
     *  red-white livery stripe, round headlights w/ warm glow halos, dark bumper. */
    private fun trainFront(liveryIdx: Int): Texture {
        val w = 128; val h = 128
        val p = Pixmap(w, h, Pixmap.Format.RGBA8888)
        val liv = Palette.TRAIN_LIVERIES[liveryIdx]
        // cab body (SS lead cars read bright yellow)
        p.setColor(Palette.TRAIN_FRONT); p.fillRectangle(0, 0, w, h)
        p.setColor(1f, 1f, 1f, 0.14f); p.fillRectangle(0, 9, w, 6)
        p.setColor(Palette.TRAIN_ROOF); p.fillRectangle(0, 0, w, 8)
        p.setColor(1f, 1f, 1f, 0.35f); p.fillRectangle(0, 0, w, 2)
        p.setColor(0f, 0f, 0f, 0.20f); p.fillRectangle(0, 8, w, 2)

        // destination board (dark navy + cream dashes)
        p.setColor(0x101c30ff.toInt())
        fillRoundRect(p, w / 2 - 22, 11, 44, 13, 4)
        p.setColor(0xfff2dcff.toInt())
        p.fillRectangle(w / 2 - 15, 15, 9, 5); p.fillRectangle(w / 2 - 3, 15, 9, 5); p.fillRectangle(w / 2 + 9, 15, 5, 5)

        // big rounded windscreen + reflections
        p.setColor(0x0d1b33ff.toInt())
        fillRoundRect(p, 12, 28, 104, 44, 11)
        p.setColor(0x16304eff.toInt())
        fillRoundRect(p, 15, 31, 98, 38, 9)
        p.setColor(0x9adcf0ff.toInt())
        var i = 0
        while (i < 14) { p.fillRectangle(22 + i, 62 - i, 4, 10); i++ }
        p.setColor(0xbfe8ffff.toInt())
        i = 0
        while (i < 7) { p.fillRectangle(58 + i, 66 - i, 3, 7); i++ }
        p.setColor(1f, 1f, 1f, 0.85f)
        fillRoundRect(p, 20, 34, 12, 6, 3)
        p.setColor(0f, 0f, 0f, 0.22f)
        p.fillRectangle(18, 60, 92, 7)

        // red-white livery stripe under the windscreen
        p.setColor(Color(liv[0])); p.fillRectangle(0, 74, w, 3)
        p.setColor(0xe8402eff.toInt()); p.fillRectangle(0, 77, w, 8)
        p.setColor(0xffffffff.toInt()); p.fillRectangle(0, 80, w, 2)

        // round headlights with warm glow halos
        for (hx in intArrayOf(24, w - 24)) {
            p.setColor(1f, 0.90f, 0.55f, 0.32f); p.fillCircle(hx, 96, 16)
            p.setColor(0x33383fff.toInt()); p.fillCircle(hx, 96, 10)
            p.setColor(0xfff3c4ff.toInt()); p.fillCircle(hx, 96, 8)
            p.setColor(1f, 1f, 1f, 0.9f); p.fillCircle(hx - 2, 94, 3)
        }

        // skirt + dark bumper + coupler
        p.setColor(Color(liv[1])); p.fillRectangle(0, 106, w, 10)
        p.setColor(0f, 0f, 0f, 0.25f); p.fillRectangle(0, 106, w, 2)
        p.setColor(0x272b33ff.toInt())
        fillRoundRect(p, 20, 116, 88, 9, 4)
        p.setColor(0x171a20ff.toInt()); p.fillRectangle(0, 124, w, 4)
        p.setColor(0x101218ff.toInt()); p.fillRectangle(w / 2 - 7, 118, 14, 7)
        return tex(p)
    }

    /** Carriage rear — 20-c: rounded rear window, glowing red taillights,
     *  livery band, skirt, bumper (matches the rebuilt side/front language). */
    private fun trainRear(liveryIdx: Int): Texture {
        val w = 128; val h = 128
        val p = Pixmap(w, h, Pixmap.Format.RGBA8888)
        val liv = Palette.TRAIN_LIVERIES[liveryIdx]
        p.setColor(Color(liv[0])); p.fillRectangle(0, 0, w, h)
        p.setColor(1f, 1f, 1f, 0.12f); p.fillRectangle(0, 9, w, 6)
        p.setColor(Palette.TRAIN_ROOF); p.fillRectangle(0, 0, w, 8)
        p.setColor(1f, 1f, 1f, 0.35f); p.fillRectangle(0, 0, w, 2)
        p.setColor(0f, 0f, 0f, 0.20f); p.fillRectangle(0, 8, w, 2)

        // rounded rear window + reflection
        p.setColor(0x0d1b33ff.toInt())
        fillRoundRect(p, 14, 24, 100, 40, 10)
        p.setColor(0x16304eff.toInt())
        fillRoundRect(p, 17, 27, 94, 34, 8)
        p.setColor(0x9adcf0ff.toInt())
        var i = 0
        while (i < 12) { p.fillRectangle(24 + i, 54 - i, 3, 9); i++ }
        p.setColor(1f, 1f, 1f, 0.8f)
        fillRoundRect(p, 21, 30, 10, 5, 2)
        p.setColor(0f, 0f, 0f, 0.22f)
        p.fillRectangle(20, 52, 88, 7)

        // livery band
        p.setColor(Color(liv[2])); p.fillRectangle(0, 70, w, 10)
        p.setColor(1f, 1f, 1f, 0.35f); p.fillRectangle(0, 70, w, 3)

        // red taillights with warm red halos
        for (hx in intArrayOf(26, w - 26)) {
            p.setColor(1f, 0.32f, 0.26f, 0.40f); p.fillCircle(hx, 94, 13)
            p.setColor(0x8f2620ff.toInt()); p.fillCircle(hx, 94, 9)
            p.setColor(0xff5a4aff.toInt()); p.fillCircle(hx, 94, 6)
            p.setColor(1f, 0.85f, 0.8f, 0.85f); p.fillCircle(hx - 2, 92, 2)
        }

        p.setColor(Color(liv[1])); p.fillRectangle(0, 106, w, 10)
        p.setColor(0f, 0f, 0f, 0.25f); p.fillRectangle(0, 106, w, 2)
        p.setColor(0x272b33ff.toInt())
        fillRoundRect(p, 20, 116, 88, 9, 4)
        p.setColor(0x171a20ff.toInt()); p.fillRectangle(0, 124, w, 4)
        return tex(p)
    }

    /** Roof — 20-c: light grey deck, 2 raised AC units w/ vent ribs, center
     *  pod, panel seams + sun glint. FaceBatch.faceTop: image TOP row = FAR
     *  edge of the roof, bottom row = NEAR edge. */
    private fun trainRoof(): Texture {
        val w = 128; val h = 64
        val p = Pixmap(w, h, Pixmap.Format.RGBA8888)
        p.setColor(Palette.TRAIN_ROOF); p.fillRectangle(0, 0, w, h)
        // panel seams
        p.setColor(0f, 0f, 0f, 0.10f)
        for (x in 0 until w step 32) p.fillRectangle(x, 0, 1, h)
        p.setColor(1f, 1f, 1f, 0.18f); p.fillRectangle(0, 0, w, 3)
        p.setColor(0f, 0f, 0f, 0.12f); p.fillRectangle(0, h - 3, w, 3)
        // raised AC units (shadow outline + lighter deck + vent ribs + front lip)
        for (vx in intArrayOf(14, 82)) {
            p.setColor(0x767c85ff.toInt()); fillRoundRect(p, vx - 2, 12, 36, 32, 5)
            p.setColor(0xc6cbd2ff.toInt()); fillRoundRect(p, vx, 14, 32, 28, 4)
            p.setColor(0x8f959dff.toInt())
            for (i in 0 until 4) p.fillRectangle(vx + 4, 20 + i * 6, 24, 2)
            p.setColor(0x70767eff.toInt()); p.fillRectangle(vx, 38, 32, 4)
            p.setColor(1f, 1f, 1f, 0.30f); p.fillRectangle(vx, 14, 32, 3)
        }
        // center pod
        p.setColor(0x8a9098ff.toInt()); fillRoundRect(p, 54, 18, 20, 24, 4)
        p.setColor(0xb4bac2ff.toInt()); fillRoundRect(p, 56, 20, 16, 20, 3)
        p.setColor(0x7a8088ff.toInt())
        for (i in 0 until 3) p.fillRectangle(58, 25 + i * 6, 12, 2)
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
