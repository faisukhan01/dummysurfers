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
    private val birdC = Color()
    var menuDim = 0f // extra darkening when menus are open
    var time = 0f    // set from game each frame (bird flap phase)

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
        drawCloud(TextureGen.cloudA, t * 8f + 40f, vh * 0.86f, 1f, 0.85f, shakeX)
        drawCloud(TextureGen.cloudA, t * 5f + 520f, vh * 0.92f, 0.8f, 0.7f, shakeX)
        drawCloud(TextureGen.cloudB, t * 12f + 240f, vh * 0.78f, 0.9f, 0.55f, shakeX)
        drawCloud(TextureGen.cloudB, t * 9f + 640f, vh * 0.83f, 0.7f, 0.5f, shakeX)

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
        val horizon = proj.horizonY + sy
        val baseY = proj.baseY + sy
        val halfTrack = GameConfig.LANE_WIDTH * 1.5f

        // vivid grass shoulders (SS bright green)
        DecoRenderer.fogged(proj, tmpC, Palette.GRASS, 20f)
        sr.setColor(tmpC)
        sr.rect(0f, horizon, vw, baseY - horizon)

        // track ballast (converging trapezoid, warm terracotta)
        val farScale = proj.scale(GameConfig.VIEW_DISTANCE)
        val nearHalfPx = halfTrack * proj.ppu * proj.scale(0f)
        val farHalfPx = (halfTrack + 6f) * proj.ppu * farScale
        val cx = vw / 2f + sx
        DecoRenderer.fogged(proj, tmpC, Palette.GROUND, 40f)
        sr.setColor(tmpC)
        sr.triangle(cx - nearHalfPx, baseY, cx + nearHalfPx, baseY, cx + farHalfPx, horizon)
        sr.triangle(cx - nearHalfPx, baseY, cx + farHalfPx, horizon, cx - farHalfPx, horizon)

        // SS path patches: alternating cream/orange blocks rushing past
        run {
            val blockLen = 4.2f
            var z = distance % (blockLen * 2f)
            var idx = ((distance / blockLen).toInt()).coerceAtLeast(0)
            while (z < GameConfig.VIEW_DISTANCE) {
                val z1 = (z + blockLen).coerceAtMost(GameConfig.VIEW_DISTANCE)
                val col = if (idx % 2 == 0) Palette.PATH_CREAM else Palette.PATH_ORANGE
                DecoRenderer.fogged(proj, tmpC, col, (z + z1) / 2f)
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

        // sleepers rushing past (primary speed cue)
        val spacing = GameConfig.SLEEPER_SPACING
        var z = distance % spacing
        while (z < GameConfig.VIEW_DISTANCE) {
            val s = proj.scale(z)
            val y = proj.groundY(z) + sy
            val w = halfTrack * proj.ppu * s
            val h = 2.2f + 9f * s
            DecoRenderer.fogged(proj, tmpC, Palette.SLEEPER, z)
            sr.setColor(tmpC)
            sr.rect(cx - w, y, w * 2f, h)
            tmpC.a = 0.25f
            sr.setColor(tmpC)
            sr.rect(cx - w, y + h * 0.6f, w * 2f, h * 0.4f)
            z += spacing
        }

        // 3 tracks x 2 rails: rust base + silver head (SS look)
        val railOffsets = floatArrayOf(-0.88f, 0.88f)
        var z0 = 0f
        while (z0 < GameConfig.VIEW_DISTANCE) {
            val z1 = (z0 + 5f).coerceAtMost(GameConfig.VIEW_DISTANCE)
            for (lane in -1..1) {
                for (o in railOffsets) {
                    val wx = lane * GameConfig.LANE_WIDTH + o
                    val x0 = proj.screenX(wx, z0) + sx
                    val x1 = proj.screenX(wx, z1) + sx
                    val y0 = proj.groundY(z0) + sy
                    val y1 = proj.groundY(z1) + sy
                    // rust base (wider)
                    val bw0 = 0.2f * proj.ppu * proj.scale(z0)
                    val bw1 = 0.2f * proj.ppu * proj.scale(z1)
                    DecoRenderer.fogged(proj, tmpC, Palette.RAIL_SIDE, z0)
                    sr.setColor(tmpC)
                    sr.triangle(x0 - bw0, y0, x0 + bw0, y0, x1 + bw1, y1)
                    sr.triangle(x0 - bw0, y0, x1 + bw1, y1, x1 - bw1, y1)
                    // silver head
                    val w0 = 0.09f * proj.ppu * proj.scale(z0)
                    val w1 = 0.09f * proj.ppu * proj.scale(z1)
                    DecoRenderer.fogged(proj, tmpC, Palette.RAIL, z0)
                    sr.setColor(tmpC)
                    sr.triangle(x0 - w0, y0, x0 + w0, y0, x1 + w1, y1)
                    sr.triangle(x0 - w0, y0, x1 + w1, y1, x1 - w1, y1)
                }
            }
            z0 = z1
        }
    }
}
