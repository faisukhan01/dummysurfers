package com.dummysurfers.core.rendering

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.dummysurfers.core.camera.Projection
import com.dummysurfers.core.gfx.FaceBatch
import com.dummysurfers.core.gfx.Palette
import com.dummysurfers.core.gfx.TextureGen
import com.dummysurfers.core.world.Deco
import com.dummysurfers.core.world.DecoKind
import kotlin.math.abs
import kotlin.random.Random

/** Draws world decorations (buildings, poles, station props, tunnels...). */
object DecoRenderer {
    private val tmpC = Color()
    private val tmpC2 = Color()

    fun fogged(proj: Projection, out: Color, base: Color, z: Float): Color {
        val f = proj.fog(z) * 0.85f
        out.set(base)
        out.r += (Palette.FOG.r - out.r) * f
        out.g += (Palette.FOG.g - out.g) * f
        out.b += (Palette.FOG.b - out.b) * f
        return out
    }

    fun draw(d: Deco, sr: ShapeRenderer, proj: Projection, sx: Float, sy: Float) {
        when (d.kind) {
            DecoKind.BUILDING, DecoKind.SKYSCRAPER -> building(d, sr, proj, sx, sy)
            DecoKind.POLE -> pole(d, sr, proj, sx, sy)
            DecoKind.BILLBOARD -> billboard(d, sr, proj, sx, sy)
            DecoKind.LAMP -> lamp(d, sr, proj, sx, sy)
            DecoKind.TREE -> tree(d, sr, proj, sx, sy)
            DecoKind.BUSH -> bush(d, sr, proj, sx, sy)
            DecoKind.PLATFORM -> platform(d, sr, proj, sx, sy)
            DecoKind.SHELTER -> shelter(d, sr, proj, sx, sy)
            DecoKind.STATION_SIGN -> stationSign(d, sr, proj, sx, sy)
            DecoKind.BENCH -> bench(d, sr, proj, sx, sy)
            DecoKind.SIGNAL -> signal(d, sr, proj, sx, sy)
            DecoKind.GRAFFITI_WALL -> graffitiWall(d, sr, proj, sx, sy)
            DecoKind.BRIDGE_GIRDER -> bridgeGirder(d, sr, proj, sx, sy)
            DecoKind.TUNNEL_ARCH -> tunnelArch(d, sr, proj, sx, sy)
            DecoKind.TUNNEL_WALL -> tunnelWall(d, sr, proj, sx, sy)
            DecoKind.FENCE -> {}
            DecoKind.PASSENGER -> passenger(d, sr, proj, sx, sy)
        }
    }

    private fun box3D(
        proj: Projection, sr: ShapeRenderer,
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

    /**
     * Textured building (renderer v2): facade texture w/ baked windows on the
     * front face, shaded side, rooftop cap. Replaces the flat ShapeRenderer
     * building for BUILDING / SKYSCRAPER deco kinds.
     */
    fun buildingTextured(d: Deco, fb: FaceBatch, proj: Projection, sx: Float, sy: Float) {
        val zBack = (d.z + d.w).coerceAtMost(72f)
        val zFront = d.z.coerceAtLeast(-8f)
        if (zBack - zFront < 0.2f) return
        fogged(proj, tmpC, Color(1f, 1f, 1f, 1f), (zFront + zBack) * 0.5f)
        val front = tmpC2.set(tmpC)
        val side = tmpC.cpy().mul(0.76f)
        val top = tmpC.cpy().mul(1.12f)
        val tex = if (d.kind == DecoKind.SKYSCRAPER) TextureGen.glassTex else TextureGen.facades[d.variant % TextureGen.facades.size]

        // rooftop slab + parapet / crown
        fb.faceTop(TextureGen.white, d.h, d.x - d.w / 2f, d.x + d.w / 2f, zFront, zBack, top)
        if (d.kind != DecoKind.SKYSCRAPER) {
            fb.faceFront(TextureGen.white, zFront, d.x - d.w / 2f, d.x + d.w / 2f, d.h, d.h + 0.14f, tmpC.cpy().mul(0.85f))
        } else {
            // glass tower crown: darker mechanical floor
            fb.faceFront(TextureGen.white, zFront, d.x - d.w / 2f, d.x + d.w / 2f, d.h, d.h + 0.5f, tmpC.cpy().mul(0.55f))
            fb.faceTop(TextureGen.white, d.h + 0.5f, d.x - d.w / 2f + 0.15f, d.x + d.w / 2f - 0.15f, zFront, zBack, tmpC.cpy().mul(0.5f))
        }

        // visible side face
        if (proj.camX < d.x - d.w / 2f) fb.faceSide(tex, d.x - d.w / 2f, zFront, zBack, 0f, d.h, side)
        else if (proj.camX > d.x + d.w / 2f) fb.faceSide(tex, d.x + d.w / 2f, zFront, zBack, 0f, d.h, side)

        // facade (front)
        fb.faceFront(tex, zFront, d.x - d.w / 2f, d.x + d.w / 2f, 0f, d.h, front)
    }

    private fun building(d: Deco, sr: ShapeRenderer, proj: Projection, sx: Float, sy: Float) {
        val base = tmpC2.set(Color(Palette.BUILDING_COLORS[d.variant % Palette.BUILDING_COLORS.size]))
        fogged(proj, tmpC, base, d.z)
        val front = Color(tmpC)
        val sideC = tmpC.cpy().mul(0.72f)
        val topC = tmpC.cpy().mul(1.15f)
        val zBack = (d.z + d.w).coerceAtMost(72f)
        box3D(proj, sr, d.x, d.w, d.h, d.z, zBack, front, sideC, topC, sx, sy)

        if (d.z < 60f) {
            val s = proj.scale(d.z)
            val xl = proj.screenX(d.x - d.w / 2f, d.z) + sx
            val xr = proj.screenX(d.x + d.w / 2f, d.z) + sx
            val yb = proj.groundY(d.z) + sy
            val yt = yb - d.h * proj.ppu * s
            val cols = (d.w * 0.9f).toInt().coerceIn(2, 5)
            val rows = (d.h * 1.1f).toInt().coerceIn(2, 9)
            val cw = (xr - xl) / cols
            val ch = (yb - yt) / rows
            var r = 0
            while (r < rows) {
                var c = 0
                while (c < cols) {
                    val lit = ((r * 7 + c * 13 + d.variant * 31) % 10) < (if (d.lit) 6 else 2)
                    fogged(proj, tmpC, if (lit) Color(0xffe9a8ff.toInt()) else Color(0x2f3238ff.toInt()), d.z)
                    sr.setColor(tmpC)
                    sr.rect(xl + cw * (c + 0.25f), yb - ch * (r + 0.85f), cw * 0.5f, ch * 0.55f)
                    c++
                }
                r++
            }
        }
    }

    private fun pole(d: Deco, sr: ShapeRenderer, proj: Projection, sx: Float, sy: Float) {
        val s = proj.scale(d.z)
        val x = proj.screenX(d.x, d.z) + sx
        val yb = proj.groundY(d.z) + sy
        val yt = yb - d.h * proj.ppu * s
        fogged(proj, tmpC, Color(0x3d4045ff.toInt()), d.z)
        sr.setColor(tmpC)
        sr.rect(x - 2.5f * s, yt, 5f * s, yb - yt)
        val armDir = -d.side
        sr.rect(x, yt + 4f * proj.ppu * s, armDir * 26f * s, 3f * s)
        sr.setColor(Palette.GOLD)
        sr.circle(x + armDir * 22f * s, yt + 4.1f * proj.ppu * s, 2.6f * s)
        sr.circle(x + armDir * 8f * s, yt + 4.1f * proj.ppu * s, 2.6f * s)
    }

    private fun billboard(d: Deco, sr: ShapeRenderer, proj: Projection, sx: Float, sy: Float) {
        val s = proj.scale(d.z)
        val x = proj.screenX(d.x, d.z) + sx
        val yb = proj.groundY(d.z) + sy
        val yt = yb - d.h * proj.ppu * s
        fogged(proj, tmpC, Color(0x555049ff.toInt()), d.z)
        sr.setColor(tmpC)
        sr.rect(x - d.w * 0.3f * proj.ppu * s, yt, 3f * s, d.h * 0.45f * proj.ppu * s)
        sr.rect(x + d.w * 0.3f * proj.ppu * s, yt, 3f * s, d.h * 0.45f * proj.ppu * s)
        val adColors = arrayOf(Color(0xe2493bff.toInt()), Color(0x2fa08bff.toInt()), Color(0xf2b03cff.toInt()), Color(0xd8578aff.toInt()))
        fogged(proj, tmpC, adColors[d.variant % 4], d.z)
        sr.setColor(tmpC)
        val bw = d.w * proj.ppu * s
        val bh = d.h * 0.55f * proj.ppu * s
        sr.rect(x - bw / 2, yt + d.h * 0.1f * proj.ppu * s, bw, bh)
        sr.setColor(1f, 1f, 1f, 0.85f)
        sr.rect(x - bw * 0.32f, yt + bh * 0.45f, bw * 0.16f, bh * 0.28f)
        sr.rect(x - bw * 0.05f, yt + bh * 0.45f, bw * 0.16f, bh * 0.28f)
        sr.rect(x + bw * 0.22f, yt + bh * 0.45f, bw * 0.16f, bh * 0.28f)
    }

    private fun lamp(d: Deco, sr: ShapeRenderer, proj: Projection, sx: Float, sy: Float) {
        val s = proj.scale(d.z)
        val x = proj.screenX(d.x, d.z) + sx
        val yb = proj.groundY(d.z) + sy
        val yt = yb - d.h * proj.ppu * s
        fogged(proj, tmpC, Color(0x4a4d52ff.toInt()), d.z)
        sr.setColor(tmpC)
        sr.rect(x - 2f * s, yt, 4f * s, yb - yt)
        sr.rect(x, yt + d.h * proj.ppu * s - 2f * s, -d.side * 16f * s, 3f * s)
        sr.setColor(Palette.GOLD)
        sr.circle(x - d.side * 16f * s, yt + d.h * proj.ppu * s - 4f * s, 3.4f * s)
    }

    private fun tree(d: Deco, sr: ShapeRenderer, proj: Projection, sx: Float, sy: Float) {
        val s = proj.scale(d.z)
        val x = proj.screenX(d.x, d.z) + sx
        val yb = proj.groundY(d.z) + sy
        fogged(proj, tmpC, Color(0x7a4f33ff.toInt()), d.z)
        sr.setColor(tmpC)
        sr.rect(x - 2.4f * s, yb - d.h * 0.45f * proj.ppu * s, 4.8f * s, d.h * 0.45f * proj.ppu * s)
        fogged(proj, tmpC, Color(0x3f8a4fff.toInt()), d.z)
        sr.setColor(tmpC)
        sr.circle(x, yb - d.h * 0.62f * proj.ppu * s, d.w * 0.5f * proj.ppu * s)
        sr.circle(x - d.w * 0.28f * proj.ppu * s, yb - d.h * 0.45f * proj.ppu * s, d.w * 0.34f * proj.ppu * s)
        sr.circle(x + d.w * 0.28f * proj.ppu * s, yb - d.h * 0.45f * proj.ppu * s, d.w * 0.34f * proj.ppu * s)
    }

    private fun bush(d: Deco, sr: ShapeRenderer, proj: Projection, sx: Float, sy: Float) {
        val s = proj.scale(d.z)
        val x = proj.screenX(d.x, d.z) + sx
        val yb = proj.groundY(d.z) + sy
        fogged(proj, tmpC, Color(0x4f9a4fff.toInt()), d.z)
        sr.setColor(tmpC)
        sr.circle(x, yb, d.w * 0.5f * proj.ppu * s)
    }

    private fun platform(d: Deco, sr: ShapeRenderer, proj: Projection, sx: Float, sy: Float) {
        val zBack = (d.z + d.w / 2f).coerceAtMost(72f)
        val zFront = (d.z - d.w / 2f).coerceAtLeast(-6f)
        fogged(proj, tmpC, Color(0x8a8178ff.toInt()), d.z)
        val side = tmpC.cpy().mul(0.8f)
        box3D(proj, sr, d.x, 3.4f, d.h, zFront, zBack, tmpC, side, tmpC.cpy().mul(1.2f), sx, sy)
        fogged(proj, tmpC, Color(0xf2c53cff.toInt()), d.z)
        sr.setColor(tmpC)
        val edgeX = d.x - d.side * 1.3f
        sr.triangle(
            proj.screenX(edgeX - 0.12f, zFront) + sx, proj.groundY(zFront) + sy,
            proj.screenX(edgeX + 0.12f, zFront) + sx, proj.groundY(zFront) + sy,
            proj.screenX(edgeX + 0.12f, zBack) + sx, proj.groundY(zBack) + sy
        )
        sr.triangle(
            proj.screenX(edgeX - 0.12f, zFront) + sx, proj.groundY(zFront) + sy,
            proj.screenX(edgeX + 0.12f, zBack) + sx, proj.groundY(zBack) + sy,
            proj.screenX(edgeX - 0.12f, zBack) + sx, proj.groundY(zBack) + sy
        )
    }

    private fun shelter(d: Deco, sr: ShapeRenderer, proj: Projection, sx: Float, sy: Float) {
        val s = proj.scale(d.z)
        val x = proj.screenX(d.x, d.z) + sx
        val yb = proj.groundY(d.z) + sy
        fogged(proj, tmpC, Color(0x4d5a5fff.toInt()), d.z)
        sr.setColor(tmpC)
        val halfW = d.w * 0.42f * proj.ppu * s * d.side
        sr.rect(x - halfW - 2f * s, yb - d.h * 0.8f * proj.ppu * s, 4f * s, d.h * 0.8f * proj.ppu * s)
        sr.rect(x + halfW - 2f * s, yb - d.h * 0.8f * proj.ppu * s, 4f * s, d.h * 0.8f * proj.ppu * s)
        fogged(proj, tmpC, Color(0x2fa08bff.toInt()), d.z)
        sr.setColor(tmpC)
        sr.rect(x - abs(halfW) - 8f * s, yb - d.h * proj.ppu * s, abs(halfW) * 2f + 16f * s, 7f * s + d.h * 0.2f * proj.ppu * s)
    }

    private fun stationSign(d: Deco, sr: ShapeRenderer, proj: Projection, sx: Float, sy: Float) {
        val s = proj.scale(d.z)
        val x = proj.screenX(d.x, d.z) + sx
        val yb = proj.groundY(d.z) + sy
        fogged(proj, tmpC, Color(0x3a4a5aff.toInt()), d.z)
        sr.setColor(tmpC)
        sr.rect(x - 2f * s, yb - d.h * proj.ppu * s, 4f * s, d.h * proj.ppu * s)
        fogged(proj, tmpC, Color(0x24507fff.toInt()), d.z)
        sr.setColor(tmpC)
        sr.rect(x - d.w * 0.6f * proj.ppu * s, yb - d.h * proj.ppu * s, d.w * 1.2f * proj.ppu * s, d.w * 0.5f * proj.ppu * s)
        sr.setColor(1f, 1f, 1f, 0.9f)
        sr.rect(x - d.w * 0.4f * proj.ppu * s, yb - d.h * 0.55f * proj.ppu * s, d.w * 0.8f * proj.ppu * s, d.w * 0.08f * proj.ppu * s)
    }

    private fun bench(d: Deco, sr: ShapeRenderer, proj: Projection, sx: Float, sy: Float) {
        val s = proj.scale(d.z)
        val x = proj.screenX(d.x, d.z) + sx
        val yb = proj.groundY(d.z) + sy
        fogged(proj, tmpC, Color(0x8a5f3cff.toInt()), d.z)
        sr.setColor(tmpC)
        sr.rect(x - d.w * 0.5f * proj.ppu * s, yb - 0.55f * proj.ppu * s, d.w * proj.ppu * s, 0.12f * proj.ppu * s)
        sr.rect(x - d.w * 0.5f * proj.ppu * s, yb - 0.95f * proj.ppu * s, d.w * proj.ppu * s, 0.1f * proj.ppu * s)
        sr.rect(x - d.w * 0.4f * proj.ppu * s, yb - 0.55f * proj.ppu * s, 0.1f * proj.ppu * s, 0.55f * proj.ppu * s)
        sr.rect(x + d.w * 0.3f * proj.ppu * s, yb - 0.55f * proj.ppu * s, 0.1f * proj.ppu * s, 0.55f * proj.ppu * s)
    }

    private fun signal(d: Deco, sr: ShapeRenderer, proj: Projection, sx: Float, sy: Float) {
        if (d.h <= 0f) return
        val s = proj.scale(d.z)
        val x = proj.screenX(d.x, d.z) + sx
        val yb = proj.groundY(d.z) + sy
        fogged(proj, tmpC, Color(0x33363bff.toInt()), d.z)
        sr.setColor(tmpC)
        sr.rect(x - 2.5f * s, yb - 2.2f * proj.ppu * s, 5f * s, 2.2f * proj.ppu * s)
        sr.setColor(if (d.variant == 0) Color(0xff5a4aff.toInt()) else Color(0x5ae07aff.toInt()))
        sr.circle(x, yb - 1.7f * proj.ppu * s, 4.5f * s)
    }

    private fun graffitiWall(d: Deco, sr: ShapeRenderer, proj: Projection, sx: Float, sy: Float) {
        val s = proj.scale(d.z)
        val x = proj.screenX(d.x, d.z) + sx
        val yb = proj.groundY(d.z) + sy
        val w = d.w * proj.ppu * s
        val h = d.h * proj.ppu * s
        fogged(proj, tmpC, Color(0x9a938aff.toInt()), d.z)
        sr.setColor(tmpC)
        sr.rect(x - w / 2, yb - h, w, h)
        val cols = arrayOf(Color(0xe2493bff.toInt()), Color(0x2fa08bff.toInt()), Color(0xf2b03cff.toInt()), Color(0xd8578aff.toInt()))
        val rng = Random((d.z * 31).toInt() + d.side)
        sr.setColor(fogged(proj, tmpC2, cols[d.variant % 4], d.z))
        var bx = x - w * 0.4f
        while (bx < x + w * 0.4f) {
            sr.circle(bx, yb - h * (0.3f + rng.nextFloat() * 0.4f), (0.18f + rng.nextFloat() * 0.3f) * h * 0.5f)
            bx += w * 0.18f
        }
    }

    private fun bridgeGirder(d: Deco, sr: ShapeRenderer, proj: Projection, sx: Float, sy: Float) {
        val zBack = (d.z + d.w / 2f).coerceAtMost(72f)
        val zFront = (d.z - d.w / 2f).coerceAtLeast(-8f)
        fogged(proj, tmpC, Color(0x6a5f52ff.toInt()), d.z)
        val side = tmpC.cpy().mul(0.75f)
        box3D(proj, sr, d.x, 0.5f, d.h, zFront, zBack, tmpC, side, tmpC.cpy().mul(1.1f), sx, sy)
        var z = zFront
        while (z < zBack && z < 68f) {
            val s = proj.scale(z)
            val px = proj.screenX(d.x, z) + sx
            val py = proj.groundY(z) + sy
            sr.setColor(fogged(proj, tmpC2, Color(0x8a7f6fff.toInt()), z))
            sr.rect(px - 2f * s, py - d.h * proj.ppu * s, 4f * s, d.h * proj.ppu * s)
            z += 2.2f
        }
    }

    private fun tunnelArch(d: Deco, sr: ShapeRenderer, proj: Projection, sx: Float, sy: Float) {
        val s = proj.scale(d.z)
        val cx = proj.vw / 2f + sx
        val yb = proj.groundY(d.z) + sy
        val h = d.h * proj.ppu * s
        val halfTrack = 2.5f * 1.5f * proj.ppu * s * 1.35f
        fogged(proj, tmpC, Color(0x4a4448ff.toInt()), d.z)
        sr.setColor(tmpC)
        sr.rect(cx - halfTrack - 14f * s, yb - h, 16f * s, h)
        sr.rect(cx + halfTrack - 2f * s, yb - h, 16f * s, h)
        sr.rect(cx - halfTrack - 14f * s, yb - 12f * s - h * 0.08f, halfTrack * 2f + 28f * s, 12f * s + h * 0.08f)
    }

    private fun tunnelWall(d: Deco, sr: ShapeRenderer, proj: Projection, sx: Float, sy: Float) {
        val zBack = (d.z + d.w / 2f).coerceAtMost(72f)
        val zFront = (d.z - d.w / 2f).coerceAtLeast(-10f)
        fogged(proj, tmpC, Color(0x50464aff.toInt()), d.z)
        val side = tmpC.cpy().mul(0.7f)
        box3D(proj, sr, d.x, 2.2f, d.h, zFront, zBack, tmpC, side, tmpC.cpy().mul(0.85f), sx, sy)
    }

    /** A commuter waiting on the platform — tiny silhouette with coat + head. */
    private fun passenger(d: Deco, sr: ShapeRenderer, proj: Projection, sx: Float, sy: Float) {
        val s = proj.scale(d.z)
        val x = proj.screenX(d.x, d.z) + sx
        val yb = proj.groundY(d.z) + sy
        val u = proj.ppu * s
        val coats = intArrayOf(0xb85a4aff.toInt(), 0x4a6a5aff.toInt(), 0x8a55c9ff.toInt(), 0xd9985fff.toInt())
        // legs
        fogged(proj, tmpC, Color(0x2e2a28ff.toInt()), d.z)
        sr.setColor(tmpC)
        sr.rect(x - 0.09f * u, yb - d.h * 0.42f * u, 0.08f * u, d.h * 0.42f * u)
        sr.rect(x + 0.02f * u, yb - d.h * 0.42f * u, 0.08f * u, d.h * 0.42f * u)
        // coat
        fogged(proj, tmpC, Color(coats[d.variant % 4]), d.z)
        sr.setColor(tmpC)
        sr.rect(x - 0.14f * u, yb - d.h * 0.92f * u, 0.28f * u, d.h * 0.52f * u)
        // head
        fogged(proj, tmpC, Color(0xd9a07aff.toInt()), d.z)
        sr.setColor(tmpC)
        sr.circle(x, yb - d.h * 0.98f * u, 0.11f * u)
    }
}
