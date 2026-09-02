package com.dummysurfers.core.gfx

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.dummysurfers.core.camera.Projection

/**
 * FaceBatch — textured pseudo-3D face renderer.
 *
 * Draws arbitrary projected quads (the front/side/top faces of world-space
 * boxes) through [SpriteBatch]'s raw-vertex API, so every face batches with
 * its texture, per-face shading and distance fog in a single pass.
 *
 * This is what upgrades the flat ShapeRenderer boxes into Subway-Surfers-style
 * textured geometry: train carriages with window bands, hazard barriers,
 * container blockades and building facades.
 */
class FaceBatch(private val proj: Projection, private val batch: SpriteBatch) {

    private val verts = FloatArray(20)

    /** Screen offset (camera shake), applied by the entity renderer each frame. */
    var ox = 0f
    var oy = 0f

    val drawing: Boolean get() = batch.isDrawing

    /** Screen-space quad; (tl, bl, br, tr), tint is per-vertex packed color parts. */
    fun quad(
        tex: Texture,
        tlx: Float, tly: Float,
        blx: Float, bly: Float,
        brx: Float, bry: Float,
        trx: Float, try_: Float,
        r: Float, g: Float, b: Float, a: Float,
        u0: Float, v0: Float, u1: Float, v1: Float
    ) {
        if (!batch.isDrawing) return
        val c = Color.toFloatBits(r, g, b, a)
        // vertex order expected by SpriteBatch raw draw: BL, TL, TR, BR
        verts[0] = blx; verts[1] = bly; verts[2] = c; verts[3] = u0; verts[4] = v1
        verts[5] = tlx; verts[6] = tly; verts[7] = c; verts[8] = u0; verts[9] = v0
        verts[10] = trx; verts[11] = try_; verts[12] = c; verts[13] = u1; verts[14] = v0
        verts[15] = brx; verts[16] = bry; verts[17] = c; verts[18] = u1; verts[19] = v1
        batch.draw(tex, verts, 0, 20)
    }

    /** Quad with a packed tint color (Color instance). */
    fun quadTinted(
        tex: Texture,
        tlx: Float, tly: Float, blx: Float, bly: Float, brx: Float, bry: Float, trx: Float, try_: Float,
        tint: Color, u0: Float = 0f, v0: Float = 0f, u1: Float = 1f, v1: Float = 1f
    ) = quad(tex, tlx, tly, blx, bly, brx, bry, trx, try_, tint.r, tint.g, tint.b, tint.a, u0, v0, u1, v1)

    // ── World-space faces ─────────────────────────────────────────────

    /**
     * Front face at world depth [z], spanning world x [wxLo]..[wxHi],
     * from height [yLo] to [yHi] above the ground.
     */
    fun faceFront(
        tex: Texture, z: Float, wxLo: Float, wxHi: Float, yLo: Float, yHi: Float,
        tint: Color, u0: Float = 0f, v0: Float = 0f, u1: Float = 1f, v1: Float = 1f
    ) {
        val s = proj.scale(z)
        val xl = proj.screenX(wxLo, z) + ox
        val xr = proj.screenX(wxHi, z) + ox
        val yb = proj.groundY(z) - yLo * proj.ppu * s + oy
        val yt = proj.groundY(z) - yHi * proj.ppu * s + oy
        if (xr - xl < 0.4f || yb - yt < 0.4f) return
        quad(tex, xl, yt, xl, yb, xr, yb, xr, yt, tint.r, tint.g, tint.b, tint.a, u0, v0, u1, v1)
    }

    /**
     * Side face at world x = [wx], between depths [zNear] (close) and [zFar].
     * The texture runs full-width across the face; long faces should be
     * subdivided per segment (train cars do this) to keep UVs linear-looking.
     */
    fun faceSide(
        tex: Texture, wx: Float, zNear: Float, zFar: Float, yLo: Float, yHi: Float,
        tint: Color, u0: Float = 0f, v0: Float = 0f, u1: Float = 1f, v1: Float = 1f
    ) {
        val sN = proj.scale(zNear)
        val sF = proj.scale(zFar)
        val xN = proj.screenX(wx, zNear) + ox
        val xF = proj.screenX(wx, zFar) + ox
        val ybN = proj.groundY(zNear) - yLo * proj.ppu * sN + oy
        val ytN = proj.groundY(zNear) - yHi * proj.ppu * sN + oy
        val ybF = proj.groundY(zFar) - yLo * proj.ppu * sF + oy
        val ytF = proj.groundY(zFar) - yHi * proj.ppu * sF + oy
        // texture: near edge = left of region, far edge = right
        // TL = far-top, BL = far-bottom, BR = near-bottom, TR = near-top
        quad(tex, xF, ytF, xF, ybF, xN, ybN, xN, ytN, tint.r, tint.g, tint.b, tint.a, u0, v0, u1, v1)
    }

    /**
     * Top face at height [y] above ground, spanning world x [wxLo]..[wxHi]
     * and depths [zNear]..[zFar]. v0 = near edge, v1 = far edge.
     */
    fun faceTop(
        tex: Texture, y: Float, wxLo: Float, wxHi: Float, zNear: Float, zFar: Float,
        tint: Color, u0: Float = 0f, v0: Float = 0f, u1: Float = 1f, v1: Float = 1f
    ) {
        val sN = proj.scale(zNear)
        val sF = proj.scale(zFar)
        val xlN = proj.screenX(wxLo, zNear) + ox
        val xrN = proj.screenX(wxHi, zNear) + ox
        val xlF = proj.screenX(wxLo, zFar) + ox
        val xrF = proj.screenX(wxHi, zFar) + ox
        val yN = proj.groundY(zNear) - y * proj.ppu * sN + oy
        val yF = proj.groundY(zFar) - y * proj.ppu * sF + oy
        // TL = far-left, BL = far-right?? — map as trapezoid: far edge (v1) narrower? No:
        // TL = far-left(v1,u0), BL = far-right... keep: TL=far-left, BL=far-right? that twists.
        // Correct mapping: TL = far-left, BL = near-left, BR = near-right, TR = far-right.
        quad(tex, xlF, yF, xlN, yN, xrN, yN, xrF, yF, tint.r, tint.g, tint.b, tint.a, u0, v0, u1, v1)
    }

    /**
     * Full textured box: top, visible side (if camera off-axis), then front.
     * [shadeF]/[shadeS]/[shadeT] pre-multiplied tints (caller bakes fog).
     */
    fun box(
        wx: Float, halfW: Float, yLo: Float, yHi: Float, zNear: Float, zFar: Float,
        texFront: Texture, texSide: Texture, texTop: Texture,
        tintF: Color, tintS: Color, tintT: Color,
        sideOnLeft: Boolean
    ) {
        faceTop(texTop, yHi, wx - halfW, wx + halfW, zNear, zFar, tintT)
        if (sideOnLeft) {
            faceSide(texSide, wx - halfW, zNear, zFar, yLo, yHi, tintS)
        } else {
            faceSide(texSide, wx + halfW, zNear, zFar, yLo, yHi, tintS)
        }
        faceFront(texFront, zNear, wx - halfW, wx + halfW, yLo, yHi, tintF)
    }
}
