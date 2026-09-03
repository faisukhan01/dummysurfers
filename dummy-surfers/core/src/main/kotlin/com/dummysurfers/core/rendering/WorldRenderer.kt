package com.dummysurfers.core.rendering

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.dummysurfers.core.camera.Projection
import com.dummysurfers.core.config.GameConfig
import com.dummysurfers.core.gfx.Palette
import com.dummysurfers.core.gfx.TextureGen
import com.dummysurfers.core.utils.Mathz
import kotlin.math.sin

/**
 * Draws the pseudo-3D world backdrop: 7-layer parallax sky/skyline, ground,
 * rails, sleepers, horizon fog, tunnel darkness. Entities + decorations are
 * z-sorted and drawn by [EntityRenderer] (decos via [DecoRenderer]).
 */
class WorldRenderer(
    private val proj: Projection,
    private val batch: SpriteBatch,
    private val sr: ShapeRenderer
) {
    private val tmpC = Color()
    private val tmpC2 = Color()
    private val birdC = Color()
    var menuDim = 0f // extra darkening when menus are open
    var time = 0f    // set from game each frame (bird flap phase)

    // ── 20-c ballast speckle field — precomputed ONCE (z, wx, shade), zero
    // per-frame allocation: scrolled by the run distance and wrapped by modulo
    private val SPECK_COUNT = 200
    private val SPECK_SPAN = GameConfig.VIEW_DISTANCE + 8f
    private val speckZ = FloatArray(SPECK_COUNT)
    private val speckX = FloatArray(SPECK_COUNT)
    private val speckS = FloatArray(SPECK_COUNT)

    init {
        val rng = java.util.Random(20260903L)
        val halfTrack = GameConfig.LANE_WIDTH * 1.5f
        for (i in 0 until SPECK_COUNT) {
            speckZ[i] = -8f + rng.nextFloat() * SPECK_SPAN
            // sprinkle the gravel shoulders just outside the path slabs
            val edge = halfTrack - 0.7f
            speckX[i] = (edge + 0.05f + rng.nextFloat() * 1.1f) * (if (rng.nextBoolean()) 1f else -1f)
            speckS[i] = rng.nextFloat()
        }
    }

    fun render(
        distance: Float,
        speed: Float,
        shakeX: Float,
        shakeY: Float,
        tunnelDarkness: Float
    ) {
        val vw = proj.vw
        val vh = proj.vh

        // ── Layer 1-3: sky, sun, clouds (slowest parallax) ─────────────
        batch.begin()
        batch.setColor(1f, 1f, 1f, 1f)
        batch.draw(TextureGen.sky, shakeX, proj.horizonY + shakeY, vw, vh - proj.horizonY)
        val sunX = vw * 0.68f - proj.camX * 6f
        batch.setColor(1f, 0.92f, 0.7f, 0.5f)
        batch.draw(TextureGen.glow, sunX - 130f, proj.horizonY + 40f - 130f, 260f, 260f)
        batch.setColor(1f, 0.98f, 0.88f, 0.95f)
        batch.draw(TextureGen.glow, sunX - 42f, proj.horizonY + 40f - 42f, 84f, 84f)
        val t = distance * 0.02f
        // 20-c: bigger, puffier clouds (new textures are 340x120/280x100)
        drawCloud(TextureGen.cloudA, t * 8f + 40f, vh * 0.88f, 1.15f, 0.92f, shakeX)
        drawCloud(TextureGen.cloudA, t * 5f + 520f, vh * 0.93f, 0.9f, 0.75f, shakeX)
        drawCloud(TextureGen.cloudB, t * 12f + 240f, vh * 0.80f, 1.0f, 0.62f, shakeX)
        drawCloud(TextureGen.cloudB, t * 9f + 640f, vh * 0.84f, 0.8f, 0.55f, shakeX)

        // ── Layer 2-3: skyline silhouettes (tiled parallax) ────────────
        drawTiled(TextureGen.skylineFar, distance * 1.6f, proj.horizonY - 6f, 0.9f, shakeX, shakeY)
        drawTiled(TextureGen.skylineNear, distance * 5.2f, proj.horizonY - 14f, 1f, shakeX, shakeY)
        batch.end()

        // ── Birds gliding over the skyline ─────────────────────────────
        sr.begin(ShapeRenderer.ShapeType.Filled)
        drawBirds(distance)
        drawGround(distance, shakeX, shakeY)
        sr.end()

        // ── Horizon fog band over far track ────────────────────────────
        batch.begin()
        batch.setColor(1f, 1f, 1f, 1f)
        val fogH = vh * 0.15f
        batch.draw(TextureGen.fog, shakeX, proj.horizonY - fogH * 0.5f + shakeY, vw, fogH)
        batch.end()

        // ── Tunnel darkness overlay (warm, SS-style) ────────────────────
        if (tunnelDarkness > 0.01f) {
            Gdx.gl.glEnable(GL20.GL_BLEND)
            sr.begin(ShapeRenderer.ShapeType.Filled)
            tmpC.set(0.10f, 0.07f, 0.13f, tunnelDarkness * 0.6f)
            sr.setColor(tmpC)
            sr.rect(0f, 0f, vw, vh)
            sr.end()
            Gdx.gl.glDisable(GL20.GL_BLEND)
        }

        // ── Menu dim (light periwinkle wash — world stays bright) ──────
        if (menuDim > 0.01f) {
            Gdx.gl.glEnable(GL20.GL_BLEND)
            sr.begin(ShapeRenderer.ShapeType.Filled)
            tmpC.set(0.12f, 0.14f, 0.34f, menuDim * 0.42f)
            sr.setColor(tmpC)
            sr.rect(0f, 0f, vw, vh)
            sr.end()
            Gdx.gl.glDisable(GL20.GL_BLEND)
        }
    }

    private fun drawCloud(tex: Texture, scroll: Float, y: Float, scale: Float, alpha: Float, sx: Float) {
        val vw = proj.vw
        val w = tex.width * scale
        var x = ((scroll % (vw + w)) - w)
        batch.setColor(1f, 1f, 1f, alpha)
        batch.draw(tex, x + sx, y, w, tex.height * scale)
        batch.draw(tex, x + vw + w + sx, y, w, tex.height * scale)
        batch.setColor(1f, 1f, 1f, 1f)
    }

    private fun drawTiled(tex: Texture, scroll: Float, bottomY: Float, alpha: Float, sx: Float, sy: Float) {
        val vw = proj.vw
        val scale = vw / tex.width * 1.6f
        val w = tex.width * scale
        val h = tex.height * scale
        var off = scroll % w
        if (off < 0) off += w
        batch.setColor(1f, 1f, 1f, alpha)
        var x = -off
        while (x < vw) {
            batch.draw(tex, x + sx, bottomY + sy, w, h)
            x += w
        }
        batch.setColor(1f, 1f, 1f, 1f)
    }

    /** Small flock of birds drifting with the parallax, wings flapping. */
    private fun drawBirds(distance: Float) {
        val vw = proj.vw
        birdC.set(0x2b3a3eff).also { it.a = 0.9f }
        sr.setColor(birdC)
        for (i in 0 until 5) {
            val seed = i * 137
            val speed = 14f + (seed % 7) * 3f
            val span = vw + 160f
            var bx = (distance * speed + seed * 97f) % span
            if (bx < 0f) bx += span
            bx -= 80f
            val by = proj.horizonY + 30f + (seed % 5) * 34f + kotlin.math.sin(time * 1.3f + i) * 8f
            val flap = kotlin.math.sin(time * 9f + i * 1.7f)
            val w = 7f + (seed % 3) * 2f
            // two wings as thin triangles meeting at the body
            sr.triangle(bx, by, bx - w, by + flap * w * 0.5f, bx - w * 0.3f, by)
            sr.triangle(bx, by, bx + w, by + flap * w * 0.5f, bx + w * 0.3f, by)
        }
    }

    // ── Ground plane: grass shoulders, terracotta ballast, path patches,
    //    sleepers, SS rust+silver rails ─────────────────────────────────
    private fun drawGround(distance: Float, sx: Float, sy: Float) {
        val vw = proj.vw
        val vh = proj.vh
        val horizon = proj.horizonY + sy
        val baseY = proj.baseY + sy
        val halfTrack = GameConfig.LANE_WIDTH * 1.5f
        val nearZ = -7f // draw past the player to the bottom edge of the screen

        // vivid grass shoulders (SS bright green) — everything below the horizon
        DecoRenderer.fogged(proj, tmpC, Palette.GRASS, 20f)
        sr.setColor(tmpC)
        sr.rect(0f, -2f, vw, horizon + 2f)

        // 20-c: scrolling mow-stripes on the grass (alternating lighter bands —
        // the old flat green slab read as painted plastic)
        run {
            val bandLen = 3.6f
            var gz = distance % (bandLen * 2f)
            var idx = (distance / bandLen).toInt()
            while (gz < GameConfig.VIEW_DISTANCE) {
                val gz1 = gz + bandLen
                val yN = proj.groundY(gz.coerceAtLeast(-7f)) + sy
                val yF = proj.groundY(gz1) + sy
                if (idx % 2 == 0 && yF < yN) {
                    DecoRenderer.fogged(proj, tmpC2, Palette.GRASS, (gz + gz1) * 0.5f)
                    tmpC2.mul(1.09f).a = 0.55f
                    sr.setColor(tmpC2)
                    sr.rect(0f, yF, vw, yN - yF)
                }
                gz = gz1; idx++
            }
        }

        // track ballast (converging trapezoid, warm terracotta) — near edge
        // extends below the player line so no void shows under the camera
        val farScale = proj.scale(GameConfig.VIEW_DISTANCE)
        val nearHalfPx = halfTrack * proj.ppu * proj.scale(nearZ)
        val farHalfPx = (halfTrack + 6f) * proj.ppu * farScale
        val cx = vw / 2f + sx
        val nearY = proj.groundY(nearZ) + sy
        DecoRenderer.fogged(proj, tmpC, Palette.GROUND, 40f)
        sr.setColor(tmpC)
        sr.triangle(cx - nearHalfPx, nearY, cx + nearHalfPx, nearY, cx + farHalfPx, horizon)
        sr.triangle(cx - nearHalfPx, nearY, cx + farHalfPx, horizon, cx - farHalfPx, horizon)
        // full-width safety fill under the trapezoid (screen bottom)
        if (nearY < vh) {
            sr.rect(0f, -2f, vw, nearY + 2f)
        }

        // 20-c: gravel speckle — tiny precomputed chips scrolled with distance,
        // wrapped by modulo, skipped when too small/far to read (no clutter at
        // the horizon, no per-frame allocation)
        run {
            for (i in 0 until SPECK_COUNT) {
                var rel = (speckZ[i] - distance) % SPECK_SPAN
                if (rel < 0f) rel += SPECK_SPAN
                rel += -8f
                if (rel < 0.2f) continue
                val sc = proj.scale(rel)
                if (sc < 0.18f) continue
                val px = proj.screenX(speckX[i], rel) + sx
                val py = proj.groundY(rel) + sy
                val sz = 0.11f * proj.ppu * sc
                val fade = 1f - proj.fog(rel) * 0.75f
                if (speckS[i] < 0.5f) tmpC.set(0x5a4832ff.toInt()) else tmpC.set(0xf0e0b8ff.toInt())
                tmpC.a = 0.75f * fade
                sr.setColor(tmpC)
                sr.rect(px, py, sz, sz * 0.85f)
            }
        }

        // SS path patches: alternating cream/orange blocks rushing past
        run {
            val blockLen = 4.2f
            var z = nearZ + ((distance + nearZ * -1f) % (blockLen * 2f))
            var idx = ((distance / blockLen).toInt()).coerceAtLeast(0)
            while (z < GameConfig.VIEW_DISTANCE) {
                val z1 = (z + blockLen).coerceAtMost(GameConfig.VIEW_DISTANCE)
                val col = if (idx % 2 == 0) Palette.PATH_CREAM else Palette.PATH_ORANGE
                DecoRenderer.fogged(proj, tmpC, col, (z + z1).coerceAtLeast(0f) / 2f)
                sr.setColor(tmpC)
                val halfIn = (halfTrack - 0.7f) * proj.ppu * proj.scale(z)
                val halfOut = (halfTrack - 0.7f) * proj.ppu * proj.scale(z1)
                val y0 = proj.groundY(z) + sy
                val y1 = proj.groundY(z1) + sy
                if (y1 < y0) {
                    sr.triangle(cx - halfIn, y0, cx + halfIn, y0, cx + halfOut, y1)
                    sr.triangle(cx - halfIn, y0, cx + halfOut, y1, cx - halfOut, y1)
                }
                z += blockLen; idx++
            }
        }

        // 20-c: subtle rubber/oil wear streak down each lane center (long thin
        // translucent band — breaks up the sterile lane symmetry)
        run {
            var lz = 0.8f
            while (lz < 40f) {
                val lz1 = (lz + 4.5f).coerceAtMost(40f)
                tmpC2.set(0x1e160eff.toInt())
                tmpC2.a = 0.16f * (1f - proj.fog((lz + lz1) * 0.5f))
                sr.setColor(tmpC2)
                for (lane in -1..1) {
                    val wx = lane * GameConfig.LANE_WIDTH
                    val xa0 = proj.screenX(wx - 0.13f, lz) + sx
                    val xb0 = proj.screenX(wx + 0.13f, lz) + sx
                    val xa1 = proj.screenX(wx - 0.09f, lz1) + sx
                    val xb1 = proj.screenX(wx + 0.09f, lz1) + sx
                    val y0 = proj.groundY(lz) + sy
                    val y1 = proj.groundY(lz1) + sy
                    sr.triangle(xa0, y0, xb0, y0, xb1, y1)
                    sr.triangle(xa0, y0, xb1, y1, xa1, y1)
                }
                lz = lz1
            }
        }

        // sleepers rushing past (primary speed cue) — from just behind the
        // player (closer than that they explode in size and swamp the screen)
        val spacing = GameConfig.SLEEPER_SPACING
        var z = distance % spacing + nearZ
        val sleeperMinZ = -2.6f
        if (z < sleeperMinZ) z += spacing * ((sleeperMinZ - z) / spacing).toInt().coerceAtLeast(0) + spacing
        while (z < GameConfig.VIEW_DISTANCE) {
            val s = proj.scale(z)
            val y = proj.groundY(z) + sy
            val w = halfTrack * proj.ppu * s
            val h = 2.2f + 9f * s
            DecoRenderer.fogged(proj, tmpC, Palette.SLEEPER, z.coerceAtLeast(0f))
            sr.setColor(tmpC)
            sr.rect(cx - w, y, w * 2f, h)
            // 20-c: lit top edge (redder brown + highlight sells chunky wood)
            tmpC2.set(1f, 0.92f, 0.78f, 0.30f)
            sr.setColor(tmpC2)
            sr.rect(cx - w, y, w * 2f, h * 0.34f)
            tmpC.a = 0.28f
            sr.setColor(tmpC)
            sr.rect(cx - w, y + h * 0.62f, w * 2f, h * 0.38f)
            z += spacing
        }

        // 3 tracks x 2 rails: dark steel base + near-white head (SS look) — full span
        val railOffsets = floatArrayOf(-0.88f, 0.88f)
        var z0 = nearZ
        while (z0 < GameConfig.VIEW_DISTANCE) {
            val z1 = (z0 + 5f).coerceAtMost(GameConfig.VIEW_DISTANCE)
            for (lane in -1..1) {
                for (o in railOffsets) {
                    val wx = lane * GameConfig.LANE_WIDTH + o
                    val x0 = proj.screenX(wx, z0) + sx
                    val x1 = proj.screenX(wx, z1) + sx
                    val y0 = proj.groundY(z0) + sy
                    val y1 = proj.groundY(z1) + sy
                    // dark steel base (wider) — 20-d: slimmed so rails read as steel, not ribbons
                    val bw0 = 0.21f * proj.ppu * proj.scale(z0)
                    val bw1 = 0.21f * proj.ppu * proj.scale(z1)
                    DecoRenderer.fogged(proj, tmpC, Palette.RAIL_SIDE, z0.coerceAtLeast(0f))
                    sr.setColor(tmpC)
                    sr.triangle(x0 - bw0, y0, x0 + bw0, y0, x1 + bw1, y1)
                    sr.triangle(x0 - bw0, y0, x1 + bw1, y1, x1 - bw1, y1)
                    // warm steel head
                    val w0 = 0.095f * proj.ppu * proj.scale(z0)
                    val w1 = 0.095f * proj.ppu * proj.scale(z1)
                    DecoRenderer.fogged(proj, tmpC, Palette.RAIL, z0.coerceAtLeast(0f))
                    sr.setColor(tmpC)
                    sr.triangle(x0 - w0, y0, x0 + w0, y0, x1 + w1, y1)
                    sr.triangle(x0 - w0, y0, x1 + w1, y1, x1 - w1, y1)
                }
            }
            z0 = z1
        }
    }
}
