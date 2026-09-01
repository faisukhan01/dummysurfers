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
        boostOn: Boolean,
        boardOn: Boolean
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
                5 -> this.player(player, character, shakeX, shakeY, invulnBlink, shieldOn, boostOn, boardOn)
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

    // ── Player — SS-style chibi runner, seen from behind ───────────────
    private val pc1 = Color()
    private val pc2 = Color()
    private val pc3 = Color()

    private fun player(p: Player, ch: CharacterDef, sx: Float, sy: Float, blink: Boolean, shieldOn: Boolean, boostOn: Boolean, boardOn: Boolean) {
        val x = proj.screenX(p.x, 0f) + sx
        val groundY = proj.groundY(0f) + sy
        val u = proj.ppu

        // soft blob shadow
        val shadowScale = Mathz.clamp01(1f - p.jumpY / 5f)
        sr.setColor(0f, 0f, 0f, 0.32f * shadowScale)
        sr.ellipse(x - u * 0.44f * shadowScale, groundY - 4f, u * 0.88f * shadowScale, u * 0.15f * shadowScale + 2f)

        if (blink && (p.invulnTimer > 0f) && ((p.invulnTimer * 10f).toInt() % 2 == 0)) return

        val bodyH = u * GameConfig.PLAYER_HEIGHT
        val squash = 1f - p.squash * 0.16f
        val isJump = p.state == PlayerState.JUMPING
        val isSlide = p.state == PlayerState.SLIDING
        val isDead = p.state == PlayerState.DEAD

        val cx = x
        val by = groundY - p.jumpY * u

        // hoverboard under the feet — SS 2nd-chance machine
        if (boardOn) {
            val boardY = by - u * 0.05f
            sr.setColor(0.22f, 0.72f, 0.66f, 0.30f)
            sr.ellipse(cx, boardY - u * 0.055f, u * 0.46f, u * 0.085f)
            sr.setColor(0.16f, 0.19f, 0.34f, 1f)
            sr.rect(cx - u * 0.36f, boardY, u * 0.72f, u * 0.075f)
            sr.circle(cx - u * 0.36f, boardY + u * 0.037f, u * 0.037f)
            sr.circle(cx + u * 0.36f, boardY + u * 0.037f, u * 0.037f)
            sr.setColor(Palette.GOLD)
            sr.rect(cx - u * 0.36f, boardY + u * 0.026f, u * 0.72f, u * 0.022f)
        }

        if (isSlide) {
            drawRoll(p, ch, cx, by, u, squash)
            return
        }

        val OUT = Palette.UI_OUTLINE
        val g = 0.020f * u
        val hoodie = pc1.set(ch.hoodie)
        val hoodieDark = pc2.set(ch.hoodie).mul(0.86f)
        val pants = pc3.set(ch.pants)
        val shoes = Color(ch.shoes)
        val skin = Color(ch.skin)
        val hair = Color(ch.hair)
        val cap = Color(ch.cap)
        val capDark = Color(ch.cap).mul(0.82f)
        val pack = Color(ch.backpack)
        val packDark = Color(ch.backpack).mul(0.8f)

        val H = bodyH * squash
        val headR = 0.30f * u
        val headCY = H - headR * 1.02f
        val shoulderY = H * 0.52f
        val hipY = H * 0.30f

        // transform: lean / death spin around the feet
        val rotDeg = if (isDead) p.deathSpin else p.lean * 0.5f
        sr.identity()
        sr.translate(cx, by, 0f)
        if (rotDeg != 0f) sr.rotate(0f, 0f, 1f, rotDeg)

        val swing = if (isJump) 0f else sin(p.runPhase)
        val swing2 = if (isJump) 0f else sin(p.runPhase + PI.toFloat())

        // leg kinematics: hip → knee → foot with knee bend
        val legW = 0.115f * u
        val thighL = H * 0.19f
        val shinL = H * 0.19f
        fun leg(side: Float, phase: Float) {
            val hipX = side * 0.115f * u
            val a: Float
            val bend: Float
            if (isJump) { a = 1.25f; bend = 2.1f } else { a = phase * 0.95f; bend = Mathz.clamp01(-phase) * 1.5f }
            val kx = hipX + sin(a) * thighL; val ky = hipY - cos(a) * thighL
            val a2 = a - bend
            val fx = kx + sin(a2) * shinL; val fy = ky - cos(a2) * shinL
            // thigh + shin (outline underlay then fill)
            sr.setColor(OUT); sr.rectLine(hipX, hipY, kx, ky, legW + 0.05f * u); sr.rectLine(kx, ky, fx, fy, legW * 0.85f + 0.05f * u)
            sr.setColor(pants); sr.rectLine(hipX, hipY, kx, ky, legW); sr.rectLine(kx, ky, fx, fy, legW * 0.85f)
            // chunky sneaker with white sole + heel tab
            sr.setColor(OUT); sr.ellipse(fx + 0.04f * u, fy - 0.015f * u, 0.145f * u, 0.085f * u)
            sr.setColor(shoes); sr.ellipse(fx + 0.04f * u, fy - 0.005f * u, 0.125f * u, 0.065f * u)
            sr.setColor(0xf7f7f7ff.toInt()); sr.ellipse(fx + 0.04f * u, fy - 0.038f * u, 0.115f * u, 0.032f * u)
        }
        leg(-1f, swing)
        leg(1f, swing2)

        // arms: shoulder → elbow → hand, opposite phase to same-side leg
        val armW = 0.105f * u
        val upArm = H * 0.16f
        val loArm = H * 0.15f
        fun arm(side: Float, phase: Float) {
            val shX = side * 0.24f * u; val shY = shoulderY + 0.02f * u
            val a = if (isJump) -2.35f else -phase * 0.9f
            val ex = shX + sin(a) * upArm; val ey = shY - cos(a) * upArm
            val a2 = a + side * 0.6f
            val hx = ex + sin(a2) * loArm; val hy = ey - cos(a2) * loArm
            sr.setColor(OUT); sr.rectLine(shX, shY, ex, ey, armW + 0.05f * u); sr.rectLine(ex, ey, hx, hy, armW * 0.85f + 0.05f * u)
            sr.setColor(hoodieDark); sr.rectLine(shX, shY, ex, ey, armW); sr.rectLine(ex, ey, hx, hy, armW * 0.85f)
            // cuff + hand
            sr.setColor(OUT); sr.circle(hx, hy, 0.062f * u)
            sr.setColor(skin); sr.circle(hx, hy, 0.05f * u)
        }
        arm(1f, swing)
        arm(-1f, swing2)

        // ── OUTLINE PASS (torso + backpack + hood + head) ──
        sr.setColor(OUT)
        sr.rect(-0.27f * u - g, hipY - 0.05f * u - g, 0.54f * u + 2 * g, shoulderY - hipY + 0.14f * u + 2 * g)
        sr.circle(0f, shoulderY + 0.02f * u, 0.24f * u + g)
        sr.rect(-0.205f * u - g, hipY - 0.08f * u - g, 0.41f * u + 2 * g, shoulderY - hipY + 0.20f * u + 2 * g)
        sr.circle(-0.10f * u, shoulderY + 0.05f * u, 0.095f * u + g)
        sr.circle(0.10f * u, shoulderY + 0.05f * u, 0.095f * u + g)
        sr.circle(0f, headCY, headR + g)

        // ── FILL PASS ──
        // torso hoodie (rounded shoulders)
        sr.setColor(hoodie)
        sr.rect(-0.27f * u, hipY - 0.05f * u, 0.54f * u, shoulderY - hipY + 0.14f * u)
        sr.circle(0f, shoulderY + 0.02f * u, 0.24f * u)
        // hem band
        sr.setColor(hoodieDark)
        sr.rect(-0.27f * u, hipY - 0.05f * u, 0.54f * u, 0.05f * u)
        // backpack on the back
        sr.setColor(pack)
        sr.rect(-0.205f * u, hipY - 0.08f * u, 0.41f * u, shoulderY - hipY + 0.20f * u)
        // pack top flap + zipper line + side pockets
        sr.setColor(packDark)
        sr.rect(-0.205f * u, shoulderY + 0.02f * u, 0.41f * u, 0.05f * u)
        sr.rect(-0.012f * u, hipY, 0.024f * u, shoulderY - hipY + 0.06f * u)
        sr.setColor(Palette.GOLD)
        sr.circle(0f, shoulderY + 0.09f * u, 0.018f * u)
        // straps over the shoulders
        sr.setColor(packDark)
        sr.rect(-0.155f * u, shoulderY - 0.02f * u, 0.07f * u, 0.10f * u)
        sr.rect(0.085f * u, shoulderY - 0.02f * u, 0.07f * u, 0.10f * u)
        // hood bunch at the neck
        sr.setColor(hoodieDark)
        sr.circle(-0.10f * u, shoulderY + 0.05f * u, 0.095f * u)
        sr.circle(0.10f * u, shoulderY + 0.05f * u, 0.095f * u)
        // head: hair base + fringe tips
        sr.setColor(hair)
        sr.circle(0f, headCY, headR)
        sr.circle(-0.14f * u, headCY - headR * 0.42f, 0.07f * u)
        sr.circle(0.14f * u, headCY - headR * 0.42f, 0.07f * u)
        // ears
        sr.setColor(OUT); sr.circle(-headR * 0.96f, headCY - 0.01f * u, 0.085f * u); sr.circle(headR * 0.96f, headCY - 0.01f * u, 0.085f * u)
        sr.setColor(skin); sr.circle(-headR * 0.96f, headCY - 0.01f * u, 0.07f * u); sr.circle(headR * 0.96f, headCY - 0.01f * u, 0.07f * u)
        // backwards cap: dome + brim band across the back + adjuster strap
        sr.setColor(cap)
        sr.circle(0f, headCY + headR * 0.38f, headR * 0.90f)
        sr.rect(-headR * 0.93f, headCY + headR * 0.18f, headR * 1.86f, headR * 0.42f)
        sr.setColor(capDark)
        sr.rect(-headR * 0.95f, headCY + headR * 0.06f, headR * 1.9f, headR * 0.14f) // brim edge
        sr.rect(-headR * 0.34f, headCY - headR * 0.12f, headR * 0.68f, headR * 0.17f) // adjuster strap
        sr.setColor(OUT)
        sr.circle(0f, headCY - headR * 0.035f, 0.028f * u) // snap hole
        // cap gloss
        sr.setColor(1f, 1f, 1f, 0.20f)
        sr.circle(-headR * 0.32f, headCY + headR * 0.62f, 0.055f * u)

        sr.identity()

        // shield bubble (rim + glass)
        if (shieldOn) {
            sr.setColor(0.5f, 1f, 0.95f, 0.6f)
            sr.circle(cx, by + bodyH * 0.5f, bodyH * 0.62f)
            sr.setColor(0.3f, 0.9f, 0.85f, 0.22f)
            sr.circle(cx, by + bodyH * 0.5f, bodyH * 0.58f)
        }
    }

    /** SLIDE = curled somersault roll (SS-style), backpack facing the camera. */
    private fun drawRoll(p: Player, ch: CharacterDef, cx: Float, by: Float, u: Float, squash: Float) {
        val r = 0.30f * u * squash
        val cy = by + r * 1.02f
        val spin = -(p.runPhase * 57.3f) % 360f
        val OUT = Palette.UI_OUTLINE
        val g = 0.020f * u

        sr.identity()
        sr.translate(cx, cy, 0f)
        sr.rotate(0f, 0f, 1f, spin)

        // ball silhouette + hoodie ball
        sr.setColor(OUT); sr.circle(0f, 0f, r + g)
        sr.setColor(pc1.set(ch.hoodie)); sr.circle(0f, 0f, r)
        // backpack panel facing the camera (rounded)
        sr.setColor(OUT)
        sr.rect(-r * 0.52f - g, -r * 0.52f - g, r * 1.04f + 2 * g, r * 0.86f + 2 * g)
        sr.circle(-r * 0.52f, 0f, r * 0.26f + g); sr.circle(r * 0.52f, 0f, r * 0.26f + g)
        sr.setColor(pc2.set(ch.backpack))
        sr.rect(-r * 0.52f, -r * 0.52f, r * 1.04f, r * 0.86f)
        sr.circle(-r * 0.52f, 0f, r * 0.26f); sr.circle(r * 0.52f, 0f, r * 0.26f)
        sr.setColor(Color(ch.backpack).mul(0.8f))
        sr.rect(-r * 0.52f, -r * 0.12f, r * 1.04f, r * 0.14f) // flap
        sr.setColor(Palette.GOLD); sr.circle(0f, r * 0.30f, r * 0.07f) // buckle
        // tucked sneakers
        sr.setColor(OUT); sr.circle(r * 0.60f, -r * 0.52f, r * 0.26f + g); sr.circle(-r * 0.60f, -r * 0.52f, r * 0.26f + g)
        sr.setColor(Color(ch.shoes)); sr.circle(r * 0.60f, -r * 0.52f, r * 0.22f); sr.circle(-r * 0.60f, -r * 0.52f, r * 0.22f)
        // cap dome on the top of the roll
        sr.setColor(OUT); sr.circle(0f, r * 0.66f, r * 0.40f + g)
        sr.setColor(Color(ch.cap)); sr.circle(0f, r * 0.66f, r * 0.36f)
        sr.setColor(Color(ch.cap).mul(0.82f)); sr.rect(-r * 0.42f, r * 0.44f, r * 0.84f, r * 0.12f)

        sr.identity()
    }

    // ── Chaser (security guard) — chibi, seen from behind ──────────────
    private fun chaser(c: Chaser, sx: Float, sy: Float) {
        val z = GameConfig.CHASER_Z
        val s = proj.scale(z)
        val x = proj.screenX(playerCharOffsetX(), z) + sx
        val groundY = proj.groundY(z) + sy
        val u = proj.ppu * s

        sr.setColor(0f, 0f, 0f, 0.3f)
        sr.ellipse(x - u * 0.45f, groundY - 5f, u * 0.9f, u * 0.15f)

        val OUT = Palette.UI_OUTLINE
        val g = 0.020f * u
        val uniform = Color(0x36486aff.toInt())
        val uniformDark = Color(0x2a3752ff.toInt())
        val pants = Color(0x2b3440ff.toInt())

        val H = u * GameConfig.PLAYER_HEIGHT
        val headR = 0.27f * u
        val headCY = H - headR * 1.02f
        val shoulderY = H * 0.52f
        val hipY = H * 0.30f

        sr.identity()
        sr.translate(x, groundY, 0f)

        val swing = sin(c.runPhase)
        val swing2 = sin(c.runPhase + PI.toFloat())

        val legW = 0.11f * u
        val thighL = H * 0.19f
        val shinL = H * 0.19f
        fun leg(side: Float, phase: Float) {
            val hipX = side * 0.115f * u
            val a = phase * 0.95f
            val bend = Mathz.clamp01(-phase) * 1.4f
            val kx = hipX + sin(a) * thighL; val ky = hipY - cos(a) * thighL
            val a2 = a - bend
            val fx = kx + sin(a2) * shinL; val fy = ky - cos(a2) * shinL
            sr.setColor(OUT); sr.rectLine(hipX, hipY, kx, ky, legW + 0.05f * u); sr.rectLine(kx, ky, fx, fy, legW * 0.85f + 0.05f * u)
            sr.setColor(pants); sr.rectLine(hipX, hipY, kx, ky, legW); sr.rectLine(kx, ky, fx, fy, legW * 0.85f)
            sr.setColor(OUT); sr.ellipse(fx + 0.04f * u, fy - 0.015f * u, 0.14f * u, 0.085f * u)
            sr.setColor(0x1d2530ff.toInt()); sr.ellipse(fx + 0.04f * u, fy - 0.005f * u, 0.12f * u, 0.065f * u)
        }
        leg(-1f, swing)
        leg(1f, swing2)

        val armW = 0.105f * u
        val upArm = H * 0.16f
        val loArm = H * 0.15f
        fun arm(side: Float, phase: Float) {
            val shX = side * 0.24f * u; val shY = shoulderY + 0.02f * u
            val a = -phase * 0.9f
            val ex = shX + sin(a) * upArm; val ey = shY - cos(a) * upArm
            val a2 = a + side * 0.6f
            val hx = ex + sin(a2) * loArm; val hy = ey - cos(a2) * loArm
            sr.setColor(OUT); sr.rectLine(shX, shY, ex, ey, armW + 0.05f * u); sr.rectLine(ex, ey, hx, hy, armW * 0.85f + 0.05f * u)
            sr.setColor(uniformDark); sr.rectLine(shX, shY, ex, ey, armW); sr.rectLine(ex, ey, hx, hy, armW * 0.85f)
            sr.setColor(OUT); sr.circle(hx, hy, 0.06f * u)
            sr.setColor(Color(0xd9975fff.toInt())); sr.circle(hx, hy, 0.048f * u)
        }
        arm(1f, swing)
        arm(-1f, swing2)

        // torso + head outline
        sr.setColor(OUT)
        sr.rect(-0.27f * u - g, hipY - 0.05f * u - g, 0.54f * u + 2 * g, shoulderY - hipY + 0.14f * u + 2 * g)
        sr.circle(0f, shoulderY + 0.02f * u, 0.24f * u + g)
        sr.circle(0f, headCY, headR + g)
        // uniform
        sr.setColor(uniform)
        sr.rect(-0.27f * u, hipY - 0.05f * u, 0.54f * u, shoulderY - hipY + 0.14f * u)
        sr.circle(0f, shoulderY + 0.02f * u, 0.24f * u)
        // belt
        sr.setColor(0x1d2530ff.toInt())
        sr.rect(-0.27f * u, hipY + 0.02f * u, 0.54f * u, 0.05f * u)
        sr.setColor(Palette.GOLD); sr.rect(0.02f * u, hipY + 0.03f * u, 0.05f * u, 0.03f * u) // buckle
        // head + skin
        sr.setColor(Color(0xd9975fff.toInt()))
        sr.circle(0f, headCY, headR)
        // police cap: dome + brim + gold badge
        sr.setColor(0x22304aff.toInt())
        sr.circle(0f, headCY + headR * 0.38f, headR * 0.90f)
        sr.rect(-headR * 0.95f, headCY + headR * 0.16f, headR * 1.9f, headR * 0.24f)
        sr.setColor(0x1a2436ff.toInt())
        sr.rect(-headR * 0.97f, headCY + headR * 0.04f, headR * 1.94f, headR * 0.14f)
        sr.setColor(Palette.GOLD)
        sr.circle(0f, headCY + headR * 0.50f, 0.045f * u)

        // waving baton in the trailing hand
        val bx = 0.34f * u + swing2 * 0.06f * u
        sr.setColor(OUT); sr.rectLine(bx, shoulderY + 0.05f * u, bx + 0.06f * u, shoulderY + 0.38f * u, 0.075f * u)
        sr.setColor(0x2b2118ff.toInt()); sr.rectLine(bx, shoulderY + 0.05f * u, bx + 0.06f * u, shoulderY + 0.38f * u, 0.055f * u)

        sr.identity()
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
