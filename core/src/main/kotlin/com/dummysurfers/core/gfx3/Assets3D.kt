package com.dummysurfers.core.gfx3

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3

/**
 * Procedural 3D asset factory — every model and texture is generated in code.
 * Subway-Surfers-bright palette, zero external assets.
 */
object Assets3D {

    lateinit var cube: Model; private set          // unit cube (scaled per instance)
    lateinit var coin: Model; private set
    lateinit var powerCube: Model; private set
    lateinit var ramp: Model; private set
    lateinit var buildings: Array<Model>; private set
    lateinit var trainCars: Array<Model>; private set
    lateinit var trainFront: Model; private set
    lateinit var hazardBarrier: Model; private set
    lateinit var gate: Model; private set
    lateinit var blockade: Model; private set
    lateinit var lamppost: Model; private set
    lateinit var fence: Model; private set
    lateinit var tree: Model; private set
    lateinit var tunnelRing: Model; private set

    private val builder = ModelBuilder()
    private val textures = ArrayList<Texture>()

    // ── SS-bright palette ───────────────────────────────────────────────
    val SKY_TOP = Color(0x39b7f5ff.toInt())
    val SKY_LOW = Color(0xbfe9fbff.toInt())
    val BALLAST = Color(0xc9b391ff.toInt())
    val GRASS = Color(0x67c24dff.toInt())
    val RAIL_STEEL = Color(0x9aa3adff.toInt())
    val SLEEPER = Color(0x8a6a4bff.toInt())
    val ROOF_GREY = Color(0xb9c0c9ff.toInt())
    val CAB_YELLOW = Color(0xffc93cff.toInt())
    val HAZARD_YELLOW = Color(0xffd23cff.toInt())

    // train liveries (SS style: saturated body + white band)
    val LIVERIES = intArrayOf(0x2f8fe8, 0xf5883c, 0x3dbb5a, 0xe8442f, 0xffc93c, 0x8a5ae8)

    fun create() {
        cube = boxModel(Color.WHITE)
        coin = buildCoin()
        powerCube = boxModel(Color.WHITE)
        ramp = buildRamp()

        val bTex = arrayOf(
            windowTexture(0xf2d8b8ff.toInt()),
            windowTexture(0xe8b08aff.toInt()),
            windowTexture(0xd9e2eaff.toInt())
        )
        buildings = Array(6) { i ->
            boxTexModel(2.6f + (i % 3) * 0.5f, 6f + i * 2.2f, 2.8f, bTex[i % 3])
        }

        trainCars = Array(6) { i ->
            trainCarModel(trainSideTexture(LIVERIES[i]))
        }
        trainFront = trainFrontModel()

        hazardBarrier = boxTexModel(2.05f, 0.85f, 0.28f, hazardTexture())
        gate = buildGate()
        blockade = boxTexModel(2.05f, 2.4f, 1.1f, containerTexture())
        lamppost = buildLamppost()
        fence = boxModel(Color(0x9fa8b2ff.toInt()))
        tree = buildTree()
        tunnelRing = buildTunnelRing()
    }

    fun dispose() {
        for (t in textures) t.dispose()
        textures.clear()
    }

    // ── helpers ─────────────────────────────────────────────────────────
    /** ModelInstance with its OWN material copy tinted [color]. */
    fun tinted(model: Model, color: Color): ModelInstance {
        val inst = ModelInstance(model)
        val mat = Material(ColorAttribute.createDiffuse(color), ColorAttribute.createSpecular(0.35f, 0.35f, 0.35f, 1f), FloatAttribute.createShininess(0.25f))
        inst.materials.clear()
        inst.materials.add(mat)
        return inst
    }

    fun regTex(p: Pixmap): Texture {
        val t = Texture(p)
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        textures.add(t)
        p.dispose()
        return t
    }

    fun texMat(tex: Texture): Material =
        Material(TextureAttribute.createDiffuse(tex), ColorAttribute.createSpecular(0.25f, 0.25f, 0.25f, 1f), FloatAttribute.createShininess(0.15f))

    fun boxModel(color: Color): Model {
        builder.begin()
        builder.node()
        builder.part("b", 4, posNor().toLong(),
            Material(ColorAttribute.createDiffuse(color), ColorAttribute.createSpecular(0.3f, 0.3f, 0.3f, 1f), FloatAttribute.createShininess(0.2f)))
            .box(1f, 1f, 1f)
        return builder.end()
    }

    private fun boxTexModel(w: Float, h: Float, d: Float, tex: Texture): Model {
        builder.begin()
        builder.node()
        builder.part("b", 4, (posNor() or VertexAttributes.Usage.TextureCoordinates).toLong(), texMat(tex))
            .box(w, h, d)
        return builder.end()
    }

    private fun posNor() = VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal

    // ── procedural textures ─────────────────────────────────────────────
    /** Building facade: wall color + grid of windows, some lit. */
    fun windowTexture(wall: Int): Texture {
        val W = 128; val H = 128
        val p = Pixmap(W, H, Pixmap.Format.RGBA8888)
        p.setColor(wall); p.fill()
        val cols = 4; val rows = 5
        val cw = W / cols; val ch = H / rows
        var seed = 7
        for (r in 0 until rows) for (c in 0 until cols) {
            seed = seed * 31 + r * 7 + c * 13
            val lit = (seed / 17) % 5 == 0
            p.setColor(if (lit) Color(0xfff3c2ff.toInt()) else Color(0x5a7d9aff.toInt()))
            p.fillRectangle(c * cw + 6, r * ch + 6, cw - 12, ch - 12)
        }
        return regTex(p)
    }

    /** Yellow/black hazard chevrons for low barriers. */
    fun hazardTexture(): Texture {
        val W = 128; val H = 64
        val p = Pixmap(W, H, Pixmap.Format.RGBA8888)
        p.setColor(HAZARD_YELLOW); p.fill()
        p.setColor(Color(0x22262eff.toInt()))
        var x = -H
        while (x < W + H) {
            for (i in 0 until 14) p.drawLine(x + i, 0, x + H + i, H)
            x += 32
        }
        p.setColor(0x8f979fff.toInt())
        p.fillRectangle(0, 0, W, 6)
        p.fillRectangle(0, H - 6, W, 6)
        return regTex(p)
    }

    /** Train side: livery body + white band + navy windows. */
    fun trainSideTexture(livery: Int): Texture {
        val W = 256; val H = 96
        val p = Pixmap(W, H, Pixmap.Format.RGBA8888)
        p.setColor(Color(livery.shl(8) or 0xff)); p.fill()
        p.setColor(1f, 1f, 1f, 1f)
        p.fillRectangle(0, 30, W, 22)
        p.setColor(0x2a3057ff.toInt())
        var x = 14
        while (x < W - 40) {
            p.fillRectangle(x, 10, 34, 18)
            p.setColor(0x9fdcf8ff.toInt())
            p.fillRectangle(x + 4, 13, 10, 5)
            p.setColor(0x2a3057ff.toInt())
            x += 46
        }
        p.setColor(0x3a3f47ff.toInt())
        p.fillRectangle(0, H - 12, W, 12)
        return regTex(p)
    }

    /** Freight container corrugation. */
    fun containerTexture(): Texture {
        val W = 128; val H = 128
        val p = Pixmap(W, H, Pixmap.Format.RGBA8888)
        p.setColor(Color(0xe8442fff.toInt())); p.fill()
        p.setColor(0.82f, 0.28f, 0.2f, 1f)
        var x = 0
        while (x < W) {
            p.fillRectangle(x, 0, 8, H)
            x += 20
        }
        return regTex(p)
    }

    // ── composite models ────────────────────────────────────────────────
    /** Metro car: textured body + grey roof + dark chassis. */
    private fun trainCarModel(sideTex: Texture): Model {
        val mb = ModelBuilder()
        mb.begin()
        run {
            val n = mb.node(); n.id = "body"; n.translation.set(0f, 1.25f, 0f)
            mb.part("body", 4, (posNor() or VertexAttributes.Usage.TextureCoordinates).toLong(), texMat(sideTex))
                .box(2.05f, 2.0f, 6.2f)
        }
        run {
            val n = mb.node(); n.id = "roof"; n.translation.set(0f, 2.42f, 0f)
            mb.part("roof", 4, posNor().toLong(), Material(ColorAttribute.createDiffuse(ROOF_GREY)))
                .box(1.85f, 0.3f, 5.9f)
        }
        run {
            val n = mb.node(); n.id = "chassis"; n.translation.set(0f, 0.22f, 0f)
            mb.part("chassis", 4, posNor().toLong(), Material(ColorAttribute.createDiffuse(Color(0x3a3f47ff.toInt()))))
                .box(1.7f, 0.44f, 5.8f)
        }
        return mb.end()
    }

    /** Train lead car face: yellow cab + windshield. */
    private fun trainFrontModel(): Model {
        val mb = ModelBuilder()
        mb.begin()
        run {
            val n = mb.node(); n.translation.set(0f, 1.25f, 0f)
            mb.part("body", 4, posNor().toLong(), Material(ColorAttribute.createDiffuse(CAB_YELLOW)))
                .box(2.05f, 2.0f, 0.3f)
        }
        run {
            val n = mb.node(); n.translation.set(0f, 1.75f, 0.18f)
            mb.part("glass", 4, posNor().toLong(), Material(ColorAttribute.createDiffuse(Color(0x2a3057ff.toInt()))))
                .box(1.7f, 0.7f, 0.06f)
        }
        return mb.end()
    }

    /** Wedge ramp: rises from y=0 at front (z=0) to roof height at back (z=-3.6). */
    private fun buildRamp(): Model {
        val mb = ModelBuilder()
        mb.begin()
        val H = 2.35f; val L = 3.6f; val W = 2.05f
        val mat = Material(ColorAttribute.createDiffuse(Color(0xd9d2c5ff.toInt())))
        mb.node().id = "ramp"
        val mpb = mb.part("ramp", 4, posNor().toLong(), mat)
        val p = arrayOf(
            Vector3(-W / 2, 0f, 0f), Vector3(W / 2, 0f, 0f),
            Vector3(W / 2, 0f, -L), Vector3(-W / 2, 0f, -L),
            Vector3(-W / 2, H, -L), Vector3(W / 2, H, -L)
        )
        val slopeN = Vector3(0f, L, H).nor()
        mpb.rect(p[0], p[1], p[5], p[4], slopeN)
        mpb.rect(p[1], p[2], p[5], Vector3(W / 2, H, -L), Vector3(1f, 0f, 0f))
        mpb.rect(p[3], p[0], p[4], Vector3(-W / 2, H, -L), Vector3(-1f, 0f, 0f))
        mpb.rect(p[4], p[5], p[2], p[3], Vector3(0f, 0f, -1f))
        return mb.end()
    }

    /** Overhead gate: two posts + top sign panel (roll under). */
    private fun buildGate(): Model {
        val mb = ModelBuilder()
        mb.begin()
        val steel = Material(ColorAttribute.createDiffuse(Color(0x6f7a86ff.toInt())))
        val sign = Material(ColorAttribute.createDiffuse(Color(0xff8c3cff.toInt())))
        run {
            val n = mb.node(); n.translation.set(-1.05f, 1.05f, 0f)
            mb.part("pl", 4, posNor().toLong(), steel).box(0.14f, 2.1f, 0.14f)
        }
        run {
            val n = mb.node(); n.translation.set(1.05f, 1.05f, 0f)
            mb.part("pr", 4, posNor().toLong(), steel).box(0.14f, 2.1f, 0.14f)
        }
        run {
            val n = mb.node(); n.translation.set(0f, 1.95f, 0f)
            mb.part("top", 4, posNor().toLong(), sign).box(2.3f, 0.8f, 0.18f)
        }
        return mb.end()
    }

    private fun buildLamppost(): Model {
        val mb = ModelBuilder()
        mb.begin()
        val steel = Material(ColorAttribute.createDiffuse(Color(0x5a626bff.toInt())))
        val lamp = Material(ColorAttribute.createDiffuse(Color(0xfff0b8ff.toInt())))
        run {
            val n = mb.node(); n.translation.set(0f, 2.6f, 0f)
            mb.part("pole", 4, posNor().toLong(), steel).box(0.12f, 5.2f, 0.12f)
        }
        run {
            val n = mb.node(); n.translation.set(0.45f, 5.1f, 0f)
            mb.part("arm", 4, posNor().toLong(), steel).box(1f, 0.1f, 0.1f)
        }
        run {
            val n = mb.node(); n.translation.set(0.9f, 4.95f, 0f)
            mb.part("bulb", 4, posNor().toLong(), lamp).box(0.3f, 0.16f, 0.22f)
        }
        return mb.end()
    }

    private fun buildTree(): Model {
        val mb = ModelBuilder()
        mb.begin()
        run {
            val n = mb.node(); n.translation.set(0f, 0.7f, 0f)
            mb.part("trunk", 4, posNor().toLong(), Material(ColorAttribute.createDiffuse(Color(0x7a5a3aff.toInt()))))
                .box(0.24f, 1.4f, 0.24f)
        }
        run {
            val n = mb.node(); n.translation.set(0f, 2f, 0f)
            mb.part("leaves", 4, posNor().toLong(), Material(ColorAttribute.createDiffuse(Color(0x3da84fff.toInt()))))
                .box(1.7f, 1.7f, 1.7f)
        }
        run {
            val n = mb.node(); n.translation.set(0f, 3.1f, 0f)
            mb.part("leaves2", 4, posNor().toLong(), Material(ColorAttribute.createDiffuse(Color(0x4dbb5fff.toInt()))))
                .box(1.1f, 1f, 1.1f)
        }
        return mb.end()
    }

    /** Tunnel arch ring (portal). */
    private fun buildTunnelRing(): Model {
        val mb = ModelBuilder()
        mb.begin()
        val concrete = Material(ColorAttribute.createDiffuse(Color(0xb0a898ff.toInt())))
        run {
            val n = mb.node(); n.translation.set(0f, 3f, 0f)
            mb.part("top", 4, posNor().toLong(), concrete).box(9f, 1.2f, 1f)
        }
        run {
            val n = mb.node(); n.translation.set(-4f, 1.7f, 0f)
            mb.part("left", 4, posNor().toLong(), concrete).box(1f, 3.4f, 1f)
        }
        run {
            val n = mb.node(); n.translation.set(4f, 1.7f, 0f)
            mb.part("right", 4, posNor().toLong(), concrete).box(1f, 3.4f, 1f)
        }
        return mb.end()
    }

    private fun buildCoin(): Model {
        val mb = ModelBuilder()
        mb.begin()
        mb.node().id = "c"
        mb.part("c", 4, posNor().toLong(),
            Material(ColorAttribute.createDiffuse(Color(0xffc93cff.toInt())), ColorAttribute.createSpecular(1f, 1f, 0.85f, 1f), FloatAttribute.createShininess(0.6f)))
            .cylinder(0.62f, 0.07f, 0.62f, 12)
        return mb.end()
    }
}
