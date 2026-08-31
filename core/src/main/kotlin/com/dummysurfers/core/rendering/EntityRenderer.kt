package com.dummysurfers.core.rendering

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.dummysurfers.core.camera.Projection
import com.dummysurfers.core.config.GameConfig
import com.dummysurfers.core.entities.Chaser
import com.dummysurfers.core.entities.Coin
import com.dummysurfers.core.entities.CharacterDef
import com.dummysurfers.core.entities.Obstacle
import com.dummysurfers.core.entities.ObstacleKind
import com.dummysurfers.core.entities.Player
import com.dummysurfers.core.state.PlayerState
import com.dummysurfers.core.entities.PowerUpPickup
import com.dummysurfers.core.entities.Train
import com.dummysurfers.core.gfx.Palette
import com.dummysurfers.core.gfx.TextureGen
import com.dummysurfers.core.particles.Particles
import com.dummysurfers.core.utils.Mathz
import com.dummysurfers.core.world.Deco
import com.dummysurfers.core.world.DecoKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** Unified z-sorted draw item. */
class DrawItem {
    var z = 0f
    var type = 0 // 0 deco,1 train,2 obstacle,3 coin,4 powerup,5 player,6 chaser
    var deco: Deco? = null
    var train: Train? = null
    var obstacle: Obstacle? = null
    var coin: Coin? = null
    var powerup: PowerUpPickup? = null
}

/**
 * Renders every gameplay entity with the shared pseudo-3D projection,
 * z-sorted back-to-front, interleaving ShapeRenderer and SpriteBatch
 * efficiently by switching only when the renderer type changes.
 */
class EntityRenderer(
    private val proj: Projection,
    private val batch: SpriteBatch,
    private val sr: ShapeRenderer,
    private val fontLarge: BitmapFont,
    private val fontSmall: BitmapFont
) {
    private val tmpC = Color()
    private val tmpC2 = Color()
    private val layout = GlyphLayout()
    private val items = Array(700) { DrawItem() }
    private var itemCount = 0

    private fun item(): DrawItem {
        val it = items[itemCount % items.size]
        itemCount++
        return it
    }

    // ── Main entry ─────────────────────────────────────────────────────
    fun render(
        world: com.dummysurfers.core.world.WorldGenerator,
        spawner: com.dummysurfers.core.systems.Spawner,
        player: Player,
        chaser: Chaser,
        character: CharacterDef,
        shakeX: Float,
        shakeY: Float,
        particlesFx: Particles,
        invulnBlink: Boolean,
        shieldOn: Boolean,
        boostOn: Boolean
    ) {
        itemCount = 0
        for (d in world.decos) {
            if (d.z > GameConfig.VIEW_DISTANCE + 6f || d.z < -14f) continue
            val it = item(); it.z = d.z; it.type = 0; it.deco = d
        }
        for (t in spawner.trains) { val it = item(); it.z = t.z; it.type = 1; it.train = t }
        for (o in spawner.obstacles) { val it = item(); it.z = o.z; it.type = 2; it.obstacle = o }
        for (c in spawner.coins) { if (!c.collected && c.z < GameConfig.VIEW_DISTANCE) { val it = item(); it.z = c.z; it.type = 3; it.coin = c } }
        for (p in spawner.powerups) { if (!p.taken) { val it = item(); it.z = p.z; it.type = 4; it.powerup = p } }
        if (chaser.active) { val it = item(); it.z = GameConfig.CHASER_Z; it.type = 6 }
        { val it = item(); it.z = 0f; it.type = 5 }

        // far → near
        val order = (0 until itemCount).sortedByDescending { items[it].z }
        for (idx in order) {
            val it = items[idx]
            val wantBatch = it.type == 3 || it.type == 4
            setMode(wantBatch)
            when (it.type) {
                0 -> DecoRenderer.draw(it.deco!!, sr, proj, shakeX, shakeY)
                1 -> train(it.train!!, shakeX, shakeY)
                2 -> obstacle(it.obstacle!!, shakeX, shakeY)
                3 -> coin(it.coin!!, shakeX, shakeY)
                4 -> powerup(it.powerup!!, shakeX, shakeY)
                5 -> this.player(player, character, shakeX, shakeY, invulnBlink, shieldOn, boostOn)
                6 -> this.chaser(chaser, shakeX, shakeY)
            }
        }
        // particle shapes (ShapeRenderer), then floating texts (SpriteBatch)
        setMode(false)
        particlesFx.render(sr)
        setMode(true)
        drawFloatingTexts(particlesFx, shakeX, shakeY)
        sr.end()
        batch.end()
        began = false
    }

    private var modeBatch = false
    private var began = false

    private fun setMode(batchMode: Boolean) {
        if (batchMode == modeBatch && began) return
        if (began) {
            if (modeBatch) batch.end() else sr.end()
        }
        modeBatch = batchMode
        if (modeBatch) {
            batch.begin()
        } else {
            sr.begin(ShapeRenderer.ShapeType.Filled)
        }
        began = true
    }

    fun fogged(out: Color, base: Color, z: Float): Color {
        val f = proj.fog(z) * 0.85f
        out.set(base)
        out.r += (Palette.FOG.r - out.r) * f
        out.g += (Palette.FOG.g - out.g) * f
        out.b += (Palette.FOG.b - out.b) * f
        return out
    }

    // ── 3D box helper (matches WorldRenderer.box3D) ────────────────────
    private fun box3D(
        wx: Float, w: Float, h: Float, zFront: Float, zBack: Float,
        front: Color, side: Color, top: Color, sx: Float, sy: Float
    ) {
        val sF = proj.scale(zFront)
        val sB = proj.scale(zBack)
        val xlF = proj.screenX(wx - w / 2f, zFront) + sx
        val xrF = proj.screenX(wx + w / 2f, zFront) + sx
        val ybF = proj.groundY(zFront) + sy
        val ytF = ybF - h * proj.ppu * sF
        val xlB = proj.screenX(wx - w / 2f, zBack) + sx
        val xrB = proj.screenX(wx + w / 2f, zBack) + sx
        val ybB = proj.groundY(zBack) + sy
        val ytB = ybB - h * proj.ppu * sB

        sr.setColor(top)
        sr.triangle(xlF, ytF, xrF, ytF, xrB, ytB)
        sr.triangle(xlF, ytF, xrB, ytB, xlB, ytB)
        val camWx = proj.camX
        if (camWx < wx - w / 2f) {
            sr.setColor(side)
            sr.triangle(xlF, ytF, xlB, ytB, xlB, ybB)
            sr.triangle(xlF, ytF, xlB, ybB, xlF, ybF)
        } else if (camWx > wx + w / 2f) {
            sr.setColor(side)
            sr.triangle(xrF, ytF, xrB, ytB, xrB, ybB)
            sr.triangle(xrF, ytF, xrB, ybB, xrF, ybF)
        }
        sr.setColor(front)
        sr.rect(xlF, ytF, xrF - xlF, ybF - ytF)
    }

    // ── Trains ─────────────────────────────────────────────────────────
    private fun train(t: Train, sx: Float, sy: Float) {
        val livery = Palette.TRAIN_LIVERIES[t.livery % Palette.TRAIN_LIVERIES.size]
        val base = tmpC2.set(Color(livery[0]))
        val carLen = GameConfig.TRAIN_CAR_LENGTH
        val w = GameConfig.TRAIN_WIDTH
        val h = GameConfig.TRAIN_HEIGHT

        for (car in t.cars - 1 downTo 0) {
            val zF = t.z - car * carLen
            val zB = zF - carLen + 0.55f // coupling gap
            if (zB > GameConfig.VIEW_DISTANCE + 4f || zF < -10f) continue
            val zFrontC = zF.coerceAtLeast(-9f)
            fogged(tmpC, base, zF)
            // SS look: bright yellow cab on the leading car, livery body behind
            val isLeadCar = car == 0
            val sideC = tmpC.cpy().mul(0.72f)
            val roofC = Color(Palette.TRAIN_ROOF); fogged(roofC, Palette.TRAIN_ROOF, zF)
            val topC = roofC
            val front = if (isLeadCar) {
                val fc = Color(Palette.TRAIN_FRONT); fogged(fc, Palette.TRAIN_FRONT, zF); fc
            } else Color(tmpC)

            // undercarriage shadow
            val sF = proj.scale(zFrontC)
            sr.setColor(0f, 0f, 0f, 0.3f)
            val shW = w * proj.ppu * sF * 0.6f
            for (lane in t.lanes) {
                val lx = proj.screenX(lane * GameConfig.LANE_WIDTH.toFloat(), zFrontC) + sx
                sr.rect(lx - shW / 2, proj.groundY(zFrontC) + sy - 2f, shW, 5f * sF + 2f)
            }

            for (lane in t.lanes) {
                val wx = lane * GameConfig.LANE_WIDTH.toFloat()
                box3D(wx, w, h, zFrontC, zB.coerceAtLeast(-9f), front, sideC, topC, sx, sy)
                trainDetails(t, wx, zFrontC, zB, w, h, livery, isLeadCar, sx, sy)
            }
        }
    }

    private fun trainDetails(t: Train, wx: Float, zF: Float, zB: Float, w: Float, h: Float, livery: IntArray, isLeadCar: Boolean, sx: Float, sy: Float) {
        if (zF > GameConfig.VIEW_DISTANCE * 0.92f || zF < -8f) return
        val s = proj.scale(zF)
        val xl = proj.screenX(wx - w / 2f, zF) + sx
        val xr = proj.screenX(wx + w / 2f, zF) + sx
        val yb = proj.groundY(zF) + sy
        val yt = yb - h * proj.ppu * s

        // signature SS white band along the upper body (front face + roofline)
        sr.setColor(fogged(tmpC, Color(livery[2]), zF))
        sr.rect(xl, yb - h * 0.72f * proj.ppu * s, xr - xl, h * 0.1f * proj.ppu * s)
        // darker skirt along the bottom
        sr.setColor(fogged(tmpC, Color(livery[1]), zF))
        sr.rect(xl, yb - h * 0.22f * proj.ppu * s, xr - xl, h * 0.1f * proj.ppu * s)

        // front face details on the leading car
        val isFront = isLeadCar && abs(zF - t.z) < 0.1f
        if (isFront) {
            // windshield — dark navy w/ light-blue reflection streak
            sr.setColor(fogged(tmpC, Color(0x1e2a4aff.toInt()), zF))
            sr.rect(xl + (xr - xl) * 0.15f, yb - h * 0.82f * proj.ppu * s, (xr - xl) * 0.7f, h * 0.3f * proj.ppu * s)
            sr.setColor(fogged(tmpC, Color(0x9adcf0ff.toInt()), zF))
            sr.rect(xl + (xr - xl) * 0.2f, yb - h * 0.6f * proj.ppu * s, (xr - xl) * 0.22f, h * 0.05f * proj.ppu * s)
            // headlights
            val glowCol = if (t.kind == 2) 1f else 0.75f
            sr.setColor(fogged(tmpC, Color(1f, 0.95f, 0.7f, 1f), zF))
            sr.circle(xl + (xr - xl) * 0.16f, yb - h * 0.3f * proj.ppu * s, 4.5f * s)
            sr.circle(xl + (xr - xl) * 0.84f, yb - h * 0.3f * proj.ppu * s, 4.5f * s)
            if (t.kind == 2) {
                // approaching: headlight beam glow
                sr.setColor(1f, 0.95f, 0.75f, 0.16f)
                sr.circle(xl + (xr - xl) * 0.5f, yb - h * 0.4f * proj.ppu * s, (xr - xl) * 0.75f)
            }
            // number plate
            sr.setColor(fogged(tmpC, Color(0xfff2dcff.toInt()), zF))
            sr.rect(xl + (xr - xl) * 0.42f, yb - h * 0.5f * proj.ppu * s, (xr - xl) * 0.16f, h * 0.09f * proj.ppu * s)
        } else {
            // side windows (visible on side face) — dark navy + reflections
            val camWx = proj.camX
            val leftSide = camWx < wx - w / 2f
            val sxE = proj.screenX(if (leftSide) wx - w / 2f else wx + w / 2f, zF) + sx
            val sxE2 = proj.screenX(if (leftSide) wx - w / 2f else wx + w / 2f, zB) + sx
            val ybE = proj.groundY(zF) + sy
            val ytE = ybE - h * proj.ppu * s
            sr.setColor(fogged(tmpC, Color(0x22324cff.toInt()), zF))
            val winN = 4
            for (i in 0 until winN) {
                val ft0 = (i + 0.15f) / winN
                val ft1 = (i + 0.85f) / winN
                val wx0 = sxE + (sxE2 - sxE) * ft0
                val wx1 = sxE + (sxE2 - sxE) * ft1
                val wy0 = ybE - h * 0.78f * proj.ppu * s + (proj.groundY(zB) + sy - h * proj.ppu * proj.scale(zB) - (ybE - h * 0.78f * proj.ppu * s)) * ft0
                sr.rect(wx0.coerceAtLeast(wx1), wy0, abs(wx1 - wx0), h * 0.24f * proj.ppu * s)
            }
            // door
            sr.setColor(fogged(tmpC, Color(livery[1]).mul(0.9f), zF))
            val dx = sxE + (sxE2 - sxE) * 0.02f
            sr.rect(dx.coerceAtLeast(sxE2 * 0.99f + sxE * 0.01f), ybE - h * 0.62f * proj.ppu * s, abs(sxE2 - sxE) * 0.12f, h * 0.5f * proj.ppu * s)
        }

        // graffiti pieces (original street art): fat rounded blobs + outline
        if (t.seed % 3 == 0 && !isFront) {
            val rng = Random(t.seed + (zF * 10).toInt())
            val cols = arrayOf(Color(0xffd24aff.toInt()), Color(0x37b8a8ff.toInt()), Color(0xe2493bff.toInt()), Color(0xd8578aff.toInt()))
            val n = 2 + rng.nextInt(2)
            for (g in 0 until n) {
                val col = cols[rng.nextInt(cols.size)]
                val gx = xl + (xr - xl) * (0.15f + rng.nextFloat() * 0.6f)
                val gy = yb - h * (0.3f + rng.nextFloat() * 0.3f) * proj.ppu * s
                val gr = (3.5f + rng.nextFloat() * 5f) * s
                sr.setColor(fogged(tmpC, Color(0x2b2622ff.toInt()), zF))
                sr.circle(gx, gy, gr + 1.8f * s)
                sr.setColor(fogged(tmpC, col, zF))
                sr.circle(gx, gy, gr)
            }
        }
    }

    // ── Obstacles ──────────────────────────────────────────────────────
    private fun obstacle(o: Obstacle, sx: Float, sy: Float) {
        val s = proj.scale(o.z)
        if (o.z > GameConfig.VIEW_DISTANCE || o.z < -8f) return
        when (o.kind) {
            ObstacleKind.LOW_BARRIER -> lowBarrier(o, sx, sy, s)
            ObstacleKind.HIGH_BARRIER -> highBarrier(o, sx, sy, s)
            ObstacleKind.GATE -> gate(sx, sy, s)
            ObstacleKind.FENCE_FULL -> fenceFull(sx, sy, s)
            ObstacleKind.BLOCKADE -> blockade(o, sx, sy, s)
        }
    }

    private fun stripeBoard(xl: Float, xr: Float, y: Float, hpx: Float, variant: Int, yellowBlack: Boolean = true) {
        // SS hazard: yellow/black chevrons; blockade uses red/white
        val n = 6
        val w = xr - xl
        for (i in 0 until n) {
            val segW = w / n
            val even = (i + variant) % 2 == 0
            sr.setColor(
                when {
                    yellowBlack && even -> Palette.HAZARD_YELLOW
                    yellowBlack -> Palette.HAZARD_BLACK
                    even -> Color(0xe2493bff.toInt())
                    else -> Color(0xfff2dcff.toInt())
                }
            )
            sr.rect(xl + i * segW, y, segW + 0.5f, hpx)
        }
    }

    private fun lowBarrier(o: Obstacle, sx: Float, sy: Float, s: Float) {
        val wx = o.lane * GameConfig.LANE_WIDTH.toFloat()
        val xl = proj.screenX(wx - 1.0f, o.z) + sx
        val xr = proj.screenX(wx + 1.0f, o.z) + sx
        val yb = proj.groundY(o.z) + sy
        val hpx = 0.95f * proj.ppu * s
        // legs
        sr.setColor(fogged(tmpC, Color(0x555049ff.toInt()), o.z))
        sr.rect(xl + (xr - xl) * 0.08f, yb - hpx, 5f * s, hpx * 0.55f)
        sr.rect(xr - (xr - xl) * 0.08f - 5f * s, yb - hpx, 5f * s, hpx * 0.55f)
        // striped board
        stripeBoard(xl, xr, yb - hpx, hpx * 0.45f, o.variant)
        sr.setColor(fogged(tmpC, Color(0x2b2b2bff.toInt()), o.z))
        sr.rect(xl, yb - hpx, xr - xl, 2.2f * s)
    }

    private fun highBarrier(o: Obstacle, sx: Float, sy: Float, s: Float) {
        val wx = o.lane * GameConfig.LANE_WIDTH.toFloat()
        val xl = proj.screenX(wx - 1.1f, o.z) + sx
        val xr = proj.screenX(wx + 1.1f, o.z) + sx
        val yb = proj.groundY(o.z) + sy
        // supports from ground to 1.15u (slide gap below)
        sr.setColor(fogged(tmpC, Color(0x555049ff.toInt()), o.z))
        sr.rect(xl, yb - 1.2f * proj.ppu * s, 6f * s, 1.2f * proj.ppu * s)
        sr.rect(xr - 6f * s, yb - 1.2f * proj.ppu * s, 6f * s, 1.2f * proj.ppu * s)
        // overhead signboard 1.2u..2.2u — teal = slide action (cool color)
        val boardY = yb - 2.3f * proj.ppu * s
        val boardH = 1.05f * proj.ppu * s
        sr.setColor(fogged(tmpC, Color(0x2fa08bff.toInt()), o.z))
        sr.rect(xl, boardY, xr - xl, boardH)
        sr.setColor(fogged(tmpC, Color(0x8ff2e2ff.toInt()), o.z))
        // down-arrow marks (slide cue)
        val cx = (xl + xr) / 2
        val n = 3
        for (i in 0 until n) {
            val ax = xl + (xr - xl) * (0.3f + i * 0.2f)
            sr.rect(ax - 2.5f * s, boardY + boardH * 0.3f, 5f * s, boardH * 0.4f)
            sr.rect(ax - 6f * s, boardY + boardH * 0.22f, 12f * s, 3.5f * s)
        }
        sr.setColor(0f, 0f, 0f, 0.25f)
        sr.rect(xl, boardY, xr - xl, 2.5f * s)
    }

    private fun gate(sx: Float, sy: Float, s: Float) {
        val xl = proj.screenX(-GameConfig.LANE_WIDTH * 1.5f - 0.4f, GameConfig.MIN_PATTERN_GAP.coerceAtMost(10f)) + sx
        val z = proj.camX // unused guard
        val zNow = proj.groundY(0f)
        val xL = proj.screenX(-GameConfig.LANE_WIDTH * 1.5f - 0.5f, 10f) + sx
        val xR = proj.screenX(GameConfig.LANE_WIDTH * 1.5f + 0.5f, 10f) + sx
        val yb = proj.groundY(10f) + sy
        // posts
        sr.setColor(fogged(tmpC, Color(0x555049ff.toInt()), 10f))
        sr.rect(xL - 7f * s, yb - 2.4f * proj.ppu * proj.scale(10f), 14f * s, 2.4f * proj.ppu * proj.scale(10f))
        sr.rect(xR - 7f * s, yb - 2.4f * proj.ppu * proj.scale(10f), 14f * s, 2.4f * proj.ppu * proj.scale(10f))
        val boardY = yb - 2.35f * proj.ppu * proj.scale(10f)
        val boardH = 1.1f * proj.ppu * proj.scale(10f)
        sr.setColor(fogged(tmpC, Color(0x2fa08bff.toInt()), 10f))
        sr.rect(xL - 7f * s, boardY, (xR - xL) + 14f * s, boardH)
        sr.setColor(fogged(tmpC, Color(0x8ff2e2ff.toInt()), 10f))
        var i = 0
        val n = 8
        while (i < n) {
            val ax = xL + (xR - xL) * (0.08f + i * 0.12f)
            sr.rect(ax - 2.2f * s, boardY + boardH * 0.28f, 4.4f * s, boardH * 0.42f)
            sr.rect(ax - 5.5f * s, boardY + boardH * 0.2f, 11f * s, 3f * s)
            i++
        }
    }

    private fun fenceFull(sx: Float, sy: Float, s: Float) {
        val xL = proj.screenX(-GameConfig.LANE_WIDTH * 1.5f - 0.5f, 10f) + sx
        val xR = proj.screenX(GameConfig.LANE_WIDTH * 1.5f + 0.5f, 10f) + sx
        val yb = proj.groundY(10f) + sy
        val hpx = 0.95f * proj.ppu * proj.scale(10f)
        sr.setColor(fogged(tmpC, Color(0x555049ff.toInt()), 10f))
        sr.rect(xL, yb - hpx, 6f * s, hpx)
        sr.rect(xR - 6f * s, yb - hpx, 6f * s, hpx)
        sr.setColor(fogged(tmpC, Color(0xf2b03cff.toInt()), 10f))
        sr.rect(xL, yb - hpx, xR - xL, hpx * 0.5f)
        sr.setColor(fogged(tmpC, Color(0xfff2dcff.toInt()), 10f))
        sr.rect(xL, yb - hpx * 0.5f, xR - xL, hpx * 0.18f)
    }

    private fun blockade(o: Obstacle, sx: Float, sy: Float, s: Float, redWhite: Boolean = false) {
        val wx = o.lane * GameConfig.LANE_WIDTH.toFloat()
        box3D(
            wx, 2.0f, 2.4f, o.z, o.z + 1.2f,
            fogged(tmpC, Palette.CONTAINER_RED, o.z),
            fogged(tmpC, Palette.CONTAINER_RED, o.z).mul(0.72f),
            fogged(tmpC, Palette.CONTAINER_RED, o.z).mul(1.15f), sx, sy
        )
        val xl = proj.screenX(wx - 1f, o.z) + sx
        val xr = proj.screenX(wx + 1f, o.z) + sx
        val yb = proj.groundY(o.z) + sy
        stripeBoard(xl, xr, yb - 2.1f * proj.ppu * s, 0.5f * proj.ppu * s, o.variant, yellowBlack = !redWhite)
    }

    // ── Coins & power-ups (SpriteBatch) ────────────────────────────────
    private fun coin(c: Coin, sx: Float, sy: Float) {
        if (c.z > GameConfig.VIEW_DISTANCE || c.z < -5f) return
        val s = proj.scale(c.z)
        val x = proj.screenX(c.x, c.z) + sx
        val y = proj.groundY(c.z) + sy - c.y * proj.ppu * s
        c.phase += 0.16f
        val frame = ((c.phase % 1f) * 10f).toInt() % 10
        val bob = sin(c.phase * 6.28f) * 2.5f * s
        val size = 30f * s
        // glow
        batch.setColor(1f, 0.85f, 0.35f, 0.35f * s.coerceIn(0f, 1f))
        batch.draw(TextureGen.glow, x - size * 0.95f, y - size * 0.95f + bob, size * 1.9f, size * 1.9f)
        batch.setColor(1f, 1f, 1f, 1f)
        batch.draw(TextureGen.coinFrames[frame], x - size / 2, y - size / 2 + bob, size, size)
    }

    private fun powerup(p: PowerUpPickup, sx: Float, sy: Float) {
        if (p.z > GameConfig.VIEW_DISTANCE || p.z < -5f) return
        val s = proj.scale(p.z)
        val x = proj.screenX(p.lane * GameConfig.LANE_WIDTH.toFloat(), p.z) + sx
        p.phase += 0.016f
        val bob = sin(p.phase * 2.4f) * 6f * s
        val y = proj.groundY(p.z) + sy - (1.35f * proj.ppu * s) + bob
        val size = 52f * s
        val color = powerColor(p.type)
        // pulsing ring + glow + icon
        batch.setColor(color.r, color.g, color.b, 0.5f)
        batch.draw(TextureGen.glow, x - size, y - size + size / 2, size * 2, size * 2)
        batch.setColor(1f, 1f, 1f, 0.95f)
        TextureGen.panelNine.draw(batch, x - size / 2, y - size / 2 + size / 2, size, size)
        batch.setColor(1f, 1f, 1f, 1f)
        batch.draw(TextureGen.powerIcons[p.type], x - size * 0.34f, y - size * 0.34f + size / 2, size * 0.68f, size * 0.68f)
        // floating ring
        sr.circle(x, y + size / 2, size * 0.72f)
    }

    private fun powerColor(type: Int): Color = when (type) {
        0 -> Color(0xef4444ff.toInt())
        1 -> Color(0xf59e0bff.toInt())
        2 -> Color(0x2dd4bfff.toInt())
        3 -> Color(0xa3e635ff.toInt())
        else -> Color(0xf97316ff.toInt())
    }

    // ── Player (procedural runner, seen from behind) ───────────────────
    private fun player(p: Player, ch: CharacterDef, sx: Float, sy: Float, blink: Boolean, shieldOn: Boolean, boostOn: Boolean) {
        val s = 1f
        val x = proj.screenX(p.x, 0f) + sx
        val groundY = proj.groundY(0f) + sy
        val u = proj.ppu // pixels per unit at z=0

        // shadow
        val shadowScale = Mathz.clamp01(1f - p.jumpY / 5f)
        sr.setColor(0f, 0f, 0f, 0.35f * shadowScale)
        sr.ellipse(x - u * 0.42f * shadowScale, groundY - 4f, u * 0.84f * shadowScale, u * 0.16f * shadowScale + 2f)

        if (blink && (p.invulnTimer > 0f) && ((p.invulnTimer * 10f).toInt() % 2 == 0)) return

        val bodyH = u * GameConfig.PLAYER_HEIGHT
        val squash = 1f - p.squash * 0.16f
        val isJump = p.state == PlayerState.JUMPING
        val isSlide = p.state == PlayerState.SLIDING
        val isDead = p.state == PlayerState.DEAD

        val cx = x
        val by = groundY - p.jumpY * u

        if (isSlide) {
            drawSlidePose(p, ch, cx, by, u)
            return
        }

        sr.setColor(Color.WHITE)
        val rotate = if (isDead) p.deathSpin else p.lean * 0.5f
        // all shapes around a pivot at feet; use manual rotation via cos/sin offsets
        val rotRad = rotate * PI.toFloat() / 180f
        fun rot(px: Float, py: Float, ox: Float, oy: Float): Pair<Float, Float> {
            val dx = px - ox; val dy = py - oy
            return (ox + dx * cos(rotRad) - dy * sin(rotRad)) to (oy + dx * sin(rotRad) + dy * cos(rotRad))
        }

        val swing = if (isJump) 0f else sin(p.runPhase)
        val swing2 = if (isJump) 0f else sin(p.runPhase + PI.toFloat())
        val hipY = by + bodyH * 0.42f * squash
        val shoulderY = by + bodyH * 0.78f * squash
        val headY = by + bodyH * 0.92f * squash

        // legs (runner shorts + sneakers)
        val legColor = tmpC.set(Color(ch.pants))
        sr.setColor(legColor)
        val footLY = by + max(0f, swing) * bodyH * 0.12f
        val footRY = by + max(0f, swing2) * bodyH * 0.12f
        // left leg
        sr.rect(cx - u * 0.16f, footLY, u * 0.13f, hipY - footLY)
        sr.rect(cx + u * 0.04f, footRY, u * 0.13f, hipY - footRY)
        // sneakers (accent color)
        sr.setColor(Color(ch.shoes))
        sr.rect(cx - u * 0.19f, footLY - u * 0.05f, u * 0.19f, u * 0.08f)
        sr.rect(cx + u * 0.02f, footRY - u * 0.05f, u * 0.19f, u * 0.08f)

        // backpack (seen from behind)
        sr.setColor(Color(ch.backpack))
        val bw = u * 0.42f
        sr.rect(cx - bw / 2, hipY - u * 0.02f, bw, shoulderY - hipY + u * 0.1f)
        sr.setColor(Color(ch.accent))
        sr.rect(cx - bw / 2 + u * 0.06f, shoulderY - u * 0.16f, bw - u * 0.12f, u * 0.1f)

        // torso hoodie
        sr.setColor(Color(ch.hoodie))
        sr.rect(cx - u * 0.26f, hipY, u * 0.52f, shoulderY - hipY + u * 0.04f)
        // hood bump
        sr.circle(cx, shoulderY + u * 0.02f, u * 0.1f)

        // arms pumping
        val armColor = tmpC.set(Color(ch.hoodie)).mul(0.85f)
        sr.setColor(armColor)
        val armSwing = if (isJump) -0.3f else swing * 0.5f
        val armSwing2 = if (isJump) -0.3f else swing2 * 0.5f
        sr.rect(cx - u * 0.38f, shoulderY - u * 0.1f + armSwing * u * 0.16f, u * 0.11f, bodyH * 0.26f)
        sr.rect(cx + u * 0.27f, shoulderY - u * 0.1f + armSwing2 * u * 0.16f, u * 0.11f, bodyH * 0.26f)
        // hands
        sr.setColor(Color(ch.skin))
        sr.circle(cx - u * 0.325f, shoulderY - u * 0.12f + armSwing * u * 0.16f, u * 0.055f)
        sr.circle(cx + u * 0.325f, shoulderY - u * 0.12f + armSwing2 * u * 0.16f, u * 0.055f)

        // head + backward cap
        sr.setColor(Color(ch.skin))
        sr.circle(cx, headY, u * 0.17f)
        sr.setColor(Color(ch.cap))
        sr.circle(cx, headY + u * 0.06f, u * 0.17f)
        sr.rect(cx - u * 0.17f, headY + u * 0.03f, u * 0.34f, u * 0.07f)
        sr.setColor(Color(ch.accent))
        sr.rect(cx - u * 0.06f, headY + u * 0.16f, u * 0.12f, u * 0.05f)

        // shield bubble (double circle = rim + glass)
        if (shieldOn) {
            sr.setColor(0.5f, 1f, 0.95f, 0.6f)
            sr.circle(cx, by + bodyH * 0.5f, bodyH * 0.62f)
            sr.setColor(0.3f, 0.9f, 0.85f, 0.22f)
            sr.circle(cx, by + bodyH * 0.5f, bodyH * 0.58f)
        }
    }

    private fun drawSlidePose(p: Player, ch: CharacterDef, cx: Float, by: Float, u: Float) {
        // low crouch: body horizontal, legs forward
        val bodyY = by + u * 0.28f
        sr.setColor(Color(ch.pants))
        sr.rect(cx - u * 0.1f, by, u * 0.42f, u * 0.16f)
        sr.setColor(Color(ch.shoes))
        sr.rect(cx + u * 0.26f, by - u * 0.02f, u * 0.14f, u * 0.1f)
        sr.setColor(Color(ch.backpack))
        sr.rect(cx - u * 0.42f, bodyY - u * 0.12f, u * 0.26f, u * 0.3f)
        sr.setColor(Color(ch.hoodie))
        sr.rect(cx - u * 0.2f, bodyY - u * 0.16f, u * 0.42f, u * 0.32f)
        sr.setColor(Color(ch.skin))
        sr.circle(cx + u * 0.3f, bodyY - u * 0.02f, u * 0.14f)
        sr.setColor(Color(ch.cap))
        sr.circle(cx + u * 0.36f, bodyY + u * 0.05f, u * 0.13f)
    }

    // ── Chaser (security guard) ────────────────────────────────────────
    private fun chaser(c: Chaser, sx: Float, sy: Float) {
        val z = GameConfig.CHASER_Z
        val s = proj.scale(z) // >1 (behind player = closer to camera)
        val x = proj.screenX(playerCharOffsetX(), z) + sx
        val groundY = proj.groundY(z) + sy
        val u = proj.ppu * s

        sr.setColor(0f, 0f, 0f, 0.3f)
        sr.ellipse(x - u * 0.45f, groundY - 5f, u * 0.9f, u * 0.15f)

        val bodyH = u * GameConfig.PLAYER_HEIGHT
        val swing = sin(c.runPhase)
        val swing2 = sin(c.runPhase + PI.toFloat())
        val hipY = groundY + bodyH * 0.42f
        val shoulderY = groundY + bodyH * 0.78f
        val headY = groundY + bodyH * 0.94f

        // legs
        sr.setColor(Color(0x2b3440ff.toInt()))
        sr.rect(x - u * 0.16f, groundY + max(0f, swing) * bodyH * 0.1f, u * 0.14f, hipY - groundY)
        sr.rect(x + u * 0.03f, groundY + max(0f, swing2) * bodyH * 0.1f, u * 0.14f, hipY - groundY)
        // torso navy uniform
        sr.setColor(Color(0x36486aff.toInt()))
        sr.rect(x - u * 0.28f, hipY, u * 0.56f, shoulderY - hipY + u * 0.04f)
        // belt
        sr.setColor(Color(0x1d2530ff.toInt()))
        sr.rect(x - u * 0.28f, hipY + u * 0.04f, u * 0.56f, u * 0.07f)
        // arms — one wields baton
        sr.setColor(Color(0x36486aff.toInt()))
        sr.rect(x - u * 0.4f, shoulderY - u * 0.1f + swing * u * 0.14f, u * 0.11f, bodyH * 0.26f)
        sr.rect(x + u * 0.29f, shoulderY - u * 0.1f + swing2 * u * 0.14f, u * 0.11f, bodyH * 0.26f)
        // baton
        sr.setColor(Color(0x2b2118ff.toInt()))
        sr.rect(x + u * 0.38f, shoulderY + swing2 * u * 0.14f + u * 0.1f, u * 0.05f, u * 0.34f)
        // head + cap
        sr.setColor(Color(0xd9975fff.toInt()))
        sr.circle(x, headY, u * 0.16f)
        sr.setColor(Color(0x22304aff.toInt()))
        sr.circle(x, headY + u * 0.05f, u * 0.16f)
        sr.rect(x - u * 0.2f, headY + u * 0.02f, u * 0.4f, u * 0.06f)
        // angry brow (visible from behind? show side badge instead)
        sr.setColor(Palette.GOLD)
        sr.circle(x + u * 0.18f, shoulderY + u * 0.18f, u * 0.035f)
    }

    private fun playerCharOffsetX(): Float {
        // chaser tracks slightly behind the player's x (set by game each frame)
        return chaserX
    }

    var chaserX = 0f

    // ── Floating score texts (SpriteBatch) ─────────────────────────────
    private fun drawFloatingTexts(p: Particles, sx: Float, sy: Float) {
        p.eachText { t ->
            val alpha = Mathz.clamp01(t.life / t.maxLife * 1.6f)
            val font = if (t.size > 22f) fontLarge else fontSmall
            font.setColor(t.color.r, t.color.g, t.color.b, alpha)
            font.data.setScale(t.size / 32f)
            layout.setText(font, t.text)
            font.draw(batch, t.text, t.x - layout.width / 2 + sx, t.y + sy)
        }
        fontSmall.setColor(1f, 1f, 1f, 1f)
        fontLarge.setColor(1f, 1f, 1f, 1f)
        fontSmall.data.setScale(1f)
        fontLarge.data.setScale(1f)
    }
}
