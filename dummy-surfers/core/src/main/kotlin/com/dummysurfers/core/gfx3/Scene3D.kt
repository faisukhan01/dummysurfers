package com.dummysurfers.core.gfx3

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import kotlin.math.sin

/**
 * True-3D subway world: perspective chase camera, scrolling track decor,
 * bright SS sky, rails/sleepers/grass, buildings, lamps, trees.
 */
class Scene3D {

    val cam = PerspectiveCamera(0f, 4.6f, -7.6f)
    val batch = ModelBatch()
    val env = Environment()
    private val camTarget = Vector3()
    private val projV = Vector3()

    /** One scrolling world object; wraps around a fixed span. */
    private class Scroller(val inst: ModelInstance, var z: Float, val span: Float, val x: Float, val y: Float) {
        fun update(d: Float) {
            z -= d
            if (z < -14f) z += span
            inst.transform.`val`[Matrix4.M03] = z
        }
    }

    private val statics = ArrayList<ModelInstance>()
    private val scrollers = ArrayList<Scroller>()
    private val span = 22f * 8f

    private var rngState = 987654321
    private fun rand(): Float {
        rngState = (rngState * 1103515245 + 12345) and 0x7fffffff
        return rngState / 0x7fffffff.toFloat()
    }

    lateinit var white: Texture; private set

    fun create() {
        // 1x1 white texture for sky/quad drawing
        val p = Pixmap(4, 4, Pixmap.Format.RGBA8888)
        p.setColor(1f, 1f, 1f, 1f); p.fill()
        white = Assets3D.regTex(p)

        cam.fieldOfView = 62f
        cam.near = 0.3f
        cam.far = 180f
        cam.lookAt(0f, 1.6f, 14f)
        cam.update()

        env.set(ColorAttribute(ColorAttribute.AmbientLight, 0.75f, 0.75f, 0.8f, 1f))
        val sun = DirectionalLight().set(0.8f, 0.77f, 0.68f, -0.35f, -1f, 0.45f)
        env.set(DirectionalLightsAttribute().apply { lights.add(sun) })

        buildStatics()
        buildDecor()
    }

    private fun inst(model: Model, x: Float, y: Float, z: Float, sx: Float = 1f, sy: Float = 1f, sz: Float = 1f): ModelInstance {
        val i = ModelInstance(model)
        i.transform.setToTranslationAndScaling(x, y, z, sx, sy, sz)
        return i
    }

    private fun diff(i: ModelInstance, c: Color) {
        i.materials.first().set(ColorAttribute.createDiffuse(c))
    }

    private fun buildStatics() {
        // track bed ballast strip + shoulders
        statics.add(inst(Assets3D.cube, 0f, -0.25f, 70f, 8.6f, 0.5f, 260f).also { diff(it, Assets3D.BALLAST) })
        statics.add(inst(Assets3D.cube, -3.5f, -0.27f, 70f, 1.6f, 0.5f, 260f).also { diff(it, Color(0xb8a284ff.toInt())) })
        statics.add(inst(Assets3D.cube, 3.5f, -0.27f, 70f, 1.6f, 0.5f, 260f).also { diff(it, Color(0xb8a284ff.toInt())) })
        // grass plains
        statics.add(inst(Assets3D.cube, -23f, -0.4f, 70f, 34f, 0.6f, 260f).also { diff(it, Assets3D.GRASS) })
        statics.add(inst(Assets3D.cube, 23f, -0.4f, 70f, 34f, 0.6f, 260f).also { diff(it, Assets3D.GRASS) })
        // rails: 2 per lane, continuous
        for (lane in -1..1) {
            val lx = lane * 2.2f
            statics.add(inst(Assets3D.cube, lx - 0.55f, 0.055f, 70f, 0.09f, 0.11f, 260f).also { diff(it, Assets3D.RAIL_STEEL) })
            statics.add(inst(Assets3D.cube, lx + 0.55f, 0.055f, 70f, 0.09f, 0.11f, 260f).also { diff(it, Assets3D.RAIL_STEEL) })
        }
    }

    private fun scroll(model: Model, x: Float, y: Float, z: Float, sx: Float = 1f, sy: Float = 1f, sz: Float = 1f, tint: Color? = null) {
        val i = inst(model, x, y, z, sx, sy, sz)
        if (tint != null) diff(i, tint)
        scrollers.add(Scroller(i, z, span, x, y))
    }

    private fun buildDecor() {
        for (i in 0 until 22) {
            val z = i * 8f
            // sleepers (the speed cue)
            for (lane in -1..1) {
                val lx = lane * 2.2f
                scroll(Assets3D.cube, lx, 0.005f, z, 1.7f, 0.07f, 0.34f, Assets3D.SLEEPER)
                scroll(Assets3D.cube, lx, 0.005f, z + 2.7f, 1.7f, 0.07f, 0.34f, Color(0x815f42ff.toInt()))
                scroll(Assets3D.cube, lx, 0.005f, z + 5.4f, 1.7f, 0.07f, 0.34f, Assets3D.SLEEPER)
            }
            // buildings both sides
            val bL = Assets3D.buildings[(rand() * Assets3D.buildings.size).toInt()]
            val bR = Assets3D.buildings[(rand() * Assets3D.buildings.size).toInt()]
            scroll(bL, -9.5f - rand() * 6f, 0f, z + rand() * 4f)
            scroll(bR, 9.5f + rand() * 6f, 0f, z + rand() * 4f)
            // lamps
            if (i % 2 == 0) {
                scroll(Assets3D.lamppost, -5.7f, 0f, z + 2f)
                scroll(Assets3D.lamppost, 5.7f, 0f, z + 2f)
            }
            // trees + fences
            if (rand() < 0.6f) scroll(Assets3D.tree, -6.3f - rand() * 2.5f, 0f, z + rand() * 6f)
            if (rand() < 0.6f) scroll(Assets3D.tree, 6.3f + rand() * 2.5f, 0f, z + rand() * 6f)
            if (rand() < 0.45f) scroll(Assets3D.fence, -5.1f, 0.55f, z + 4f, 1f, 1f, 1f, Color(0x9fa8b2ff.toInt()))
            if (rand() < 0.45f) scroll(Assets3D.fence, 5.1f, 0.55f, z + 4f, 1f, 1f, 1f, Color(0x9fa8b2ff.toInt()))
            // tunnel portal decor
            if (i % 11 == 5) scroll(Assets3D.tunnelRing, 0f, 0f, z)
        }
    }

    /** Advance world scroll by [d] meters. */
    fun update(d: Float) {
        for (s in scrollers) s.update(d)
    }

    fun render(batch2d: SpriteBatch, time: Float, playerX: Float, camLift: Float) {
        // ── 2D sky backdrop (fills window, 3D draws over it) ──
        val sw = Gdx.graphics.width.toFloat()
        val sh = Gdx.graphics.height.toFloat()
        batch2d.begin()
        batch2d.disableBlending()
        batch2d.setColor(Assets3D.SKY_TOP)
        batch2d.draw(white, 0f, sh * 0.52f, sw, sh * 0.48f)
        batch2d.setColor(Assets3D.SKY_LOW)
        batch2d.draw(white, 0f, sh * 0.40f, sw, sh * 0.13f)
        batch2d.setColor(1f, 0.95f, 0.85f, 1f)
        batch2d.draw(white, 0f, sh * 0.375f, sw, sh * 0.03f)
        // sun
        batch2d.setColor(1f, 0.97f, 0.78f, 1f)
        batch2d.draw(white, sw * 0.74f, sh * 0.80f, 150f, 150f)
        batch2d.setColor(1f, 1f, 0.88f, 0.55f)
        batch2d.draw(white, sw * 0.72f, sh * 0.78f, 210f, 210f)
        // clouds
        batch2d.setColor(1f, 1f, 1f, 0.9f)
        for (i in 0 until 5) {
            val cx = ((i * 397f + time * (8f + i * 2.5f)) % (sw + 320f)) - 160f
            val cy = sh * (0.58f + (i % 3) * 0.1f)
            val cs = 60f + (i % 3) * 36f
            batch2d.draw(white, cx, cy, cs * 2.3f, cs * 0.5f)
            batch2d.draw(white, cx + cs * 0.55f, cy + cs * 0.2f, cs * 1.3f, cs * 0.45f)
        }
        batch2d.enableBlending()
        batch2d.end()

        // ── chase camera ──
        val camX = playerX * 0.42f
        val camY = 4.6f + camLift
        cam.position.set(
            cam.position.x + (camX - cam.position.x) * 0.16f,
            cam.position.y + (camY - cam.position.y) * 0.12f,
            -7.6f
        )
        camTarget.set(playerX * 0.6f, 1.7f + camLift * 0.85f, 14f)
        cam.lookAt(camTarget)
        cam.update()

        batch.begin(cam)
        for (i in statics) batch.render(i, env)
        for (s in scrollers) batch.render(s.inst, env)
        batch.end()
    }

    /** Project world (x,y,z) → window pixels for 2D particles. out = [x, y]. */
    fun projectTo(x: Float, y: Float, z: Float, out: FloatArray) {
        projV.set(x, y, z)
        cam.project(projV)
        out[0] = projV.x
        out[1] = projV.y
    }

    fun dispose() {
        batch.dispose()
    }
}
