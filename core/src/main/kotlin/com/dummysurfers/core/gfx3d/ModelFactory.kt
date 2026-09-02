package com.dummysurfers.core.gfx3d

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.dummysurfers.core.gfx.TextureGen

/**
 * v4 true-3D layer — builds and caches every mesh the scene needs.
 * All geometry is generated procedurally (boxes / cylinders) with baked
 * vertex normals + diffuse materials; textures come from TextureGen.
 *
 * GL-space convention (world → GL):  gx = x, gy = y, gz = -z_game
 *   (+z_game is AHEAD of the runner, camera sits at gz > 0 looking down -gz).
 */
class ModelFactory {

    companion object {
        val ATTRS = (VertexAttributes.Usage.Position or
                VertexAttributes.Usage.Normal or
                VertexAttributes.Usage.TextureCoordinates).toLong()
    }

    private val models = HashMap<String, Model>()
    private val materials = HashMap<String, Material>()
    /** Shared builder — exposed so character compositor can build multi-part body models. */
    val mb = ModelBuilder()

    // scratch corners
    private val c000 = Vector3(); private val c010 = Vector3(); private val c100 = Vector3(); private val c110 = Vector3()
    private val c001 = Vector3(); private val c011 = Vector3(); private val c101 = Vector3(); private val c111 = Vector3()

    fun matColor(hex: Int, factor: Float = 1f): Material {
        val key = "c$hex-$factor"
        if (System.getenv("DS_QA") == "1" && !materials.containsKey(key)) {
            val c = mul(Color(hex), factor)
            println("[MAT] $key -> (${(c.r * 255).toInt()},${(c.g * 255).toInt()},${(c.b * 255).toInt()})")
            if (c.r > 0.9f && c.g < 0.4f && c.b > 0.4f) {
                println("[MAT-PINK] key=$key from:")
                Thread.currentThread().stackTrace.take(8).forEach { println("    at $it") }
            }
        }
        return materials.getOrPut(key) { Material(ColorAttribute.createDiffuse(mul(Color(hex), factor))) }
    }

    fun matTex(tex: Texture): Material {
        val key = "t${tex.hashCode()}"
        return materials.getOrPut(key) { Material(TextureAttribute.createDiffuse(tex)) }
    }

    fun matBlend(hex: Int, alpha: Float): Material {
        val key = "b$hex-$alpha"
        return materials.getOrPut(key) {
            Material(ColorAttribute.createDiffuse(Color(hex)),
                BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, alpha))
        }
    }

    private fun mul(c: Color, f: Float): Color = Color(c.r * f, c.g * f, c.b * f, c.a)

    /** Build a one-part model. */
    private fun build(mat: Material, block: MeshPartBuilder.() -> Unit): Model {
        mb.begin()
        val mpb = mb.part("p", GL20.GL_TRIANGLES, ATTRS, mat)
        mpb.block()
        return mb.end()
    }

    // ── Primitive builders ─────────────────────────────────────────────

    fun colorBox(key: String, w: Float, h: Float, d: Float, hex: Int, factor: Float = 1f): Model =
        models.getOrPut(key) {
            build(matColor(hex, factor)) {
                setUVRange(0f, 0f, 1f, 1f)
                cbox(0f, 0f, 0f, w, h, d)
            }
        }

    /**
     * v4.7: limb geometry that HANGS BELOW its pivot. colorBox centers the box
     * on the joint, so half the upper-arm poked ABOVE the shoulder — whenever
     * the torso rolled (every lane change) that top half jutted out past the
     * silhouette as a diagonal shoulder "wing" (the slab arms in every mid-lean
     * QA shot). Hanging boxes rotate around the joint like real limbs.
     */
    fun colorBoxHang(key: String, w: Float, h: Float, d: Float, hex: Int, factor: Float = 1f): Model =
        models.getOrPut(key) {
            build(matColor(hex, factor)) {
                setUVRange(0f, 0f, 1f, 1f)
                cbox(0f, -h / 2f, 0f, w, h, d)
            }
        }

    /** Box with a texture on every face (UV repeats across each face). */
    fun texBox(key: String, w: Float, h: Float, d: Float, tex: Texture, uvU: Float = 1f, uvV: Float = 1f): Model =
        models.getOrPut(key) {
            build(matTex(tex)) {
                setUVRange(0f, 0f, uvU, uvV)
                cbox(0f, 0f, 0f, w, h, d)
            }
        }

    /**
     * Flat ground strip with CORRECT texture orientation: texture-u across x
     * (width), texture-v along z (depth). LibGDX's box() maps the top face
     * TRANSPOSED — ties rendered ALONG the track and rails ACROSS it (QA
     * 2026-09-02). Top face only: the camera never dips below the ground.
     */
    fun texGround(key: String, w: Float, d: Float, tex: Texture, uvU: Float = 1f, uvV: Float = 1f): Model =
        models.getOrPut(key) {
            mb.begin()
            val mpb = mb.part("p", GL20.GL_TRIANGLES, ATTRS, matTex(tex))
            val hw = w / 2f; val hd = d / 2f
            mpb.setUVRange(0f, 0f, uvU, uvV)
            // CCW seen from +y (right-hand rule normal = +y, else back-face culled)
            mpb.rect(Vector3(-hw, 0f, hd), Vector3(hw, 0f, hd), Vector3(hw, 0f, -hd), Vector3(-hw, 0f, -hd),
                Vector3(0f, 1f, 0f))
            mb.end()
        }

    /** Thin textured slab standing up (double-sided by default). */
    fun texPlane(key: String, w: Float, h: Float, tex: Texture, doubleSided: Boolean = true): Model =
        models.getOrPut(key) {
            mb.begin()
            val mpb = mb.part("p", GL20.GL_TRIANGLES, ATTRS, matTex(tex))
            val hw = w / 2f; val hh = h / 2f
            mpb.setUVRange(0f, 0f, 1f, 1f)
            mpb.rect(Vector3(-hw, -hh, 0f), Vector3(hw, -hh, 0f), Vector3(hw, hh, 0f), Vector3(-hw, hh, 0f),
                Vector3(0f, 0f, 1f))
            if (doubleSided) {
                mpb.setUVRange(0f, 0f, 1f, 1f)
                mpb.rect(Vector3(hw, -hh, 0f), Vector3(-hw, -hh, 0f), Vector3(-hw, hh, 0f), Vector3(hw, hh, 0f),
                    Vector3(0f, 0f, -1f))
            }
            mb.end()
        }

    /** Cylinder standing on the XZ plane (axis = Y), centered at origin. */
    fun cyl(key: String, diameter: Float, h: Float, hex: Int, div: Int = 10, factor: Float = 1f): Model =
        models.getOrPut(key) {
            build(matColor(hex, factor)) {
                setUVRange(0f, 0f, 1f, 1f)
                cylinder(diameter, h, diameter, div)
            }
        }

    /** Spinning coin — gold disc pair (outer + inner ridge).
     *  v4.3: shrunk to SS scale — at 0.68u diameter close coins projected as
     *  dinner plates (QA shot-2/8). */
    fun coin(): Model = models.getOrPut("coin") {
        build(matColor(0xffd23eff.toInt())) {
            setUVRange(0f, 0f, 1f, 1f)
            cylinder(0.46f, 0.07f, 0.46f, 14)
            setUVRange(0f, 0f, 1f, 1f)
            cylinder(0.34f, 0.09f, 0.34f, 14)
        }
    }

    /** Soft dark ellipse under characters/props (v4.4: lighter + tighter — the
     *  0.22 blob still read as a tar hole under the runner in QA; SS shadows
     *  are faint, tight halos). */
    fun shadowBlob(r: Float): Model = models.getOrPut("shadow$r") {
        build(matBlend(0x39424fff.toInt(), 0.15f)) {
            setUVRange(0f, 0f, 1f, 1f)
            cylinder(r * 2f, 0.02f, r * 2f, 14)
        }
    }

    /** Glowing billboard quad (alpha-blended, double-sided). */
    fun glowBillboard(size: Float, tex: Texture): Model = models.getOrPut("glow${size}${tex.hashCode()}") {
        mb.begin()
        val m = Material(TextureAttribute.createDiffuse(tex),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.9f))
        val mpb = mb.part("p", GL20.GL_TRIANGLES, ATTRS, m)
        val h = size / 2f
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.rect(Vector3(-h, -h, 0f), Vector3(h, -h, 0f), Vector3(h, h, 0f), Vector3(-h, h, 0f), Vector3(0f, 0f, 1f))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.rect(Vector3(h, -h, 0f), Vector3(-h, -h, 0f), Vector3(-h, h, 0f), Vector3(h, h, 0f), Vector3(0f, 0f, -1f))
        mb.end()
    }

    /**
     * v4.6 TUNNEL LIGHT POOLS — ADDITIVE warm quad lying on the ballast under
     * each ceiling lamp (GL_SRC_ALPHA, GL_ONE: brightens whatever is under it,
     * so the grey ballast reads sunlit-patch warm inside the dark tunnel —
     * the lamp tubes existed since v4.2 but threw NO light on the ground).
     * Single +y face (never seen from below).
     */
    fun lightPool(w: Float, d: Float): Model = models.getOrPut("lpool${w}x$d") {
        mb.begin()
        // v4.7: 0.55 read "subtle" in every QA batch since v4.6 (Task 17 note) —
        // 0.75 makes the lamp pools read as actual light on the ballast while
        // the v4.7 near-camera cull keeps the pass-under from flooding the lens
        val m = Material(TextureAttribute.createDiffuse(TextureGen.warmGlow),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE, 0.75f))
        val mpb = mb.part("p", GL20.GL_TRIANGLES, ATTRS, m)
        val hw = w / 2f; val hd = d / 2f
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.rect(Vector3(-hw, 0f, hd), Vector3(hw, 0f, hd), Vector3(hw, 0f, -hd), Vector3(-hw, 0f, -hd),
            Vector3(0f, 1f, 0f))
        mb.end()
    }

    // ── Train car (multi-part, per-livery, cached) ─────────────────────

    /**
     * One subway car: textured sides/roof/front/rear + skirt + wheels + roof vents.
     * FRONT face (the one the runner sees) points toward -z_game... i.e., toward +gz.
     * Origin: floor center; car length along gz.
     */
    fun trainCar(livery: Int): Model = models.getOrPut("car$livery") {
        val side = TextureGen.trainSides[livery]
        val front = TextureGen.trainFronts[livery]
        val rear = TextureGen.trainRears[livery]
        val roof = TextureGen.trainRoofTex
        val W = 2.05f; val H = 2.35f; val L = 6.4f
        val hw = W / 2f; val hl = L / 2f
        val y0 = 0.34f; val y1 = H
        mb.begin()
        // left (-x) / right (+x) sides — full side texture
        var mpb = mb.part("sideL", GL20.GL_TRIANGLES, ATTRS, matTex(side))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.rect(Vector3(-hw, y0, hl), Vector3(-hw, y0, -hl), Vector3(-hw, y1, -hl), Vector3(-hw, y1, hl),
            Vector3(-1f, 0f, 0f))
        mpb = mb.part("sideR", GL20.GL_TRIANGLES, ATTRS, matTex(side))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.rect(Vector3(hw, y0, -hl), Vector3(hw, y0, hl), Vector3(hw, y1, hl), Vector3(hw, y1, -hl),
            Vector3(1f, 0f, 0f))
        // roof
        mpb = mb.part("roof", GL20.GL_TRIANGLES, ATTRS, matTex(roof))
        mpb.setUVRange(0f, 0f, 1f, 2f)
        mpb.rect(Vector3(-hw, y1, -hl), Vector3(hw, y1, -hl), Vector3(hw, y1, hl), Vector3(-hw, y1, hl),
            Vector3(0f, 1f, 0f))
        // front (toward camera, +gz) & rear
        mpb = mb.part("front", GL20.GL_TRIANGLES, ATTRS, matTex(front))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.rect(Vector3(-hw, y0, hl), Vector3(hw, y0, hl), Vector3(hw, y1, hl), Vector3(-hw, y1, hl),
            Vector3(0f, 0f, 1f))
        mpb = mb.part("rear", GL20.GL_TRIANGLES, ATTRS, matTex(rear))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.rect(Vector3(hw, y0, -hl), Vector3(-hw, y0, -hl), Vector3(-hw, y1, -hl), Vector3(hw, y1, -hl),
            Vector3(0f, 0f, -1f))
        // skirt + bogies + wheels + roof pods.
        // v4.4 REWORK: the old skirt was a 1.95×5.9 near-black slab under the
        // whole car — from the chase cam its top face read as a giant charcoal
        // plain eating the adjacent lane (QA shot-7/8). Now: slim inset skirt
        // + two bogies with visible wheels (SS trains read as trains from the
        // side). The v4.2 "roof vents" were also spawned at y=0.17 — UNDER the
        // car, poking out as silver tabs at the ends; deleted (the roof
        // texture already bakes vents + AC pod).
        mpb = mb.part("dark", GL20.GL_TRIANGLES, ATTRS, matColor(0x363b44ff.toInt()))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.cbox(0f, y0 / 2f, 0f, W - 0.55f, y0, L - 1.3f)
        // bogies (heavier iron) at the car ends
        mpb = mb.part("bogie", GL20.GL_TRIANGLES, ATTRS, matColor(0x22262cff.toInt()))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        for (zz in floatArrayOf(-hl + 1.15f, hl - 1.15f)) {
            mpb.cbox(0f, 0.17f, zz, W - 0.75f, 0.3f, 1.5f)
        }
        // wheels: 8 steel discs (2 axles per bogie × 2 sides), upright —
        // cylinder axis is Y, so rotate the vertex transform 90° around Z
        mpb = mb.part("wheels", GL20.GL_TRIANGLES, ATTRS, matColor(0x8d949eff.toInt()))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        val wheelXf = Matrix4()
        for (zz in floatArrayOf(-hl + 0.7f, -hl + 1.6f, hl - 1.6f, hl - 0.7f)) {
            for (xx in floatArrayOf(-(W / 2f - 0.24f), W / 2f - 0.24f)) {
                wheelXf.setToTranslation(xx, 0.3f, zz).rotate(Vector3(0f, 0f, 1f), 90f)
                mpb.setVertexTransform(wheelXf)
                mpb.cylinder(0.3f, 0.1f, 0.3f, 10)
            }
        }
        mpb.setVertexTransform(null)
        // roof pods (on the roof, these are fine — AC units along the spine)
        mpb = mb.part("vents", GL20.GL_TRIANGLES, ATTRS, matColor(0xaeb6c0ff.toInt()))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.cbox(0f, y1 + 0.07f, -1.4f, 1.0f, 0.15f, 1.5f)
        mpb.cbox(0f, y1 + 0.07f, 1.4f, 1.0f, 0.15f, 1.5f)
        mb.end()
    }

    /**
     * Yellow/black ramp slab leaning onto a train roof.
     * LOCAL space: bottom end at gz=0 on the ground, top end at gz=-3.15 / y=2.38
     * (the roof edge is FARTHER ahead = smaller gz). Position via instance transform.
     */
    fun ramp(): Model = models.getOrPut("ramp") {
        val hw = 0.88f
        build(matTex(TextureGen.hazardTex)) {
            setUVRange(0f, 0f, 1f, 6f)
            c000.set(-hw, 0f, 0f); c010.set(-hw, 0.18f, 0f); c100.set(hw, 0f, 0f); c110.set(hw, 0.18f, 0f)
            c001.set(-hw, 2.2f, -3.15f); c011.set(-hw, 2.38f, -3.15f)
            c101.set(hw, 2.2f, -3.15f); c111.set(hw, 2.38f, -3.15f)
            box(c000, c010, c100, c110, c001, c011, c101, c111)
        }
    }

    // ── Track-side hazards ─────────────────────────────────────────────

    /** Low jump barrier: gray posts + red/white striped bar. */
    fun barrierLow(): Model = models.getOrPut("barrierLow") {
        mb.begin()
        var mpb = mb.part("posts", GL20.GL_TRIANGLES, ATTRS, matColor(0x5b6470ff.toInt()))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.cbox(-1.0f, 0.475f, 0f, 0.12f, 0.95f, 0.12f)
        mpb.cbox(1.0f, 0.475f, 0f, 0.12f, 0.95f, 0.12f)
        mpb = mb.part("bar", GL20.GL_TRIANGLES, ATTRS, matTex(TextureGen.barrierRedTex))
        mpb.setUVRange(0f, 0f, 4f, 1f)
        mpb.cbox(0f, 0.72f, 0f, 2.2f, 0.42f, 0.14f)
        mb.end()
    }

    /** High roadwork barrier — must slide under. */
    fun barrierHigh(): Model = models.getOrPut("barrierHigh") {
        mb.begin()
        var mpb = mb.part("posts", GL20.GL_TRIANGLES, ATTRS, matColor(0x5b6470ff.toInt()))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.cbox(-1.05f, 1.15f, 0f, 0.14f, 2.3f, 0.14f)
        mpb.cbox(1.05f, 1.15f, 0f, 0.14f, 2.3f, 0.14f)
        mpb = mb.part("panel", GL20.GL_TRIANGLES, ATTRS, matTex(TextureGen.hazardTex))
        mpb.setUVRange(0f, 0f, 4f, 1f)
        mpb.cbox(0f, 1.72f, 0f, 2.24f, 1.15f, 0.16f)
        mb.end()
    }

    /** Full blockade — solid, ends the run. */
    fun blockade(): Model = models.getOrPut("blockade") {
        mb.begin()
        var mpb = mb.part("body", GL20.GL_TRIANGLES, ATTRS, matColor(0x8d939cff.toInt()))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.cbox(0f, 1.0f, 0f, 2.2f, 2.0f, 0.9f)
        mpb = mb.part("stripe", GL20.GL_TRIANGLES, ATTRS, matTex(TextureGen.hazardTex))
        mpb.setUVRange(0f, 0f, 3f, 1f)
        mpb.cbox(0f, 1.7f, 0f, 2.24f, 0.5f, 0.94f)
        mb.end()
    }

    /** Overhead gate/sign across the whole track — slide under. */
    fun gate(): Model = models.getOrPut("gate") {
        mb.begin()
        var mpb = mb.part("posts", GL20.GL_TRIANGLES, ATTRS, matColor(0x4c5560ff.toInt()))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.cbox(-3.4f, 1.25f, 0f, 0.16f, 2.5f, 0.16f)
        mpb.cbox(3.4f, 1.25f, 0f, 0.16f, 2.5f, 0.16f)
        mpb.cbox(0f, 2.55f, 0f, 7.0f, 0.14f, 0.16f)
        mpb = mb.part("sign", GL20.GL_TRIANGLES, ATTRS, matTex(TextureGen.signTealTex))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.cbox(0f, 1.85f, 0f, 4.6f, 1.1f, 0.12f)
        mb.end()
    }

    /** Full-width fence — must be jumped. */
    fun fenceFull(): Model = models.getOrPut("fenceFull") {
        mb.begin()
        var mpb = mb.part("posts", GL20.GL_TRIANGLES, ATTRS, matColor(0x5b6470ff.toInt()))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        for (x in floatArrayOf(-3.4f, -1.15f, 1.15f, 3.4f)) mpb.cbox(x, 0.475f, 0f, 0.12f, 0.95f, 0.12f)
        mpb = mb.part("bar", GL20.GL_TRIANGLES, ATTRS, matTex(TextureGen.barrierRedTex))
        mpb.setUVRange(0f, 0f, 10f, 1f)
        mpb.cbox(0f, 0.72f, 0f, 7.0f, 0.44f, 0.12f)
        mb.end()
    }

    // ── Street furniture / deco ────────────────────────────────────────

    /** Catenary pole + crossarm. */
    fun pole(): Model = models.getOrPut("pole") {
        build(matColor(0x3d444cff.toInt())) {
            setUVRange(0f, 0f, 1f, 1f)
            cbox(0f, 2.7f, 0f, 0.18f, 5.4f, 0.18f)
            cbox(0f, 5.15f, 0f, 2.6f, 0.12f, 0.12f)
        }
    }

    /** Street lamp: pole + arm + warm head. */
    fun lamp(): Model = models.getOrPut("lamp") {
        mb.begin()
        var mpb = mb.part("metal", GL20.GL_TRIANGLES, ATTRS, matColor(0x39414aff.toInt()))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.cbox(0f, 1.8f, 0f, 0.14f, 3.6f, 0.14f)
        mpb.cbox(0.4f, 3.55f, 0f, 0.9f, 0.1f, 0.1f)
        mpb = mb.part("head", GL20.GL_TRIANGLES, ATTRS, matColor(0xffe9a8ff.toInt()))
        mpb.cbox(0.75f, 3.48f, 0f, 0.42f, 0.14f, 0.26f)
        mb.end()
    }

    /** Railway signal with red/green head. */
    fun signal(green: Boolean): Model = models.getOrPut("signal$green") {
        mb.begin()
        var mpb = mb.part("post", GL20.GL_TRIANGLES, ATTRS, matColor(0x333a42ff.toInt()))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.cbox(0f, 1.3f, 0f, 0.12f, 2.6f, 0.12f)
        mpb.cbox(0f, 2.0f, 0f, 0.34f, 1.0f, 0.22f)
        mpb = mb.part("lamp", GL20.GL_TRIANGLES, ATTRS, matColor(if (green) 0x54e06aff.toInt() else 0xff4b3eff.toInt()))
        mpb.cbox(0f, 2.32f, 0.13f, 0.18f, 0.18f, 0.06f)
        mb.end()
    }

    /** Street tree: trunk + two stacked canopies. */
    fun tree(): Model = models.getOrPut("tree") {
        mb.begin()
        var mpb = mb.part("trunk", GL20.GL_TRIANGLES, ATTRS, matColor(0x6b4a2eff.toInt()))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.cbox(0f, 0.65f, 0f, 0.26f, 1.3f, 0.26f)
        mpb = mb.part("leaf", GL20.GL_TRIANGLES, ATTRS, matColor(0x4fae4fff.toInt()))
        mpb.cbox(0f, 1.7f, 0f, 1.5f, 0.9f, 1.5f)
        mpb.cbox(0f, 2.4f, 0f, 1.0f, 0.7f, 1.0f)
        mb.end()
    }

    fun bush(): Model = models.getOrPut("bush") {
        build(matColor(0x59a648ff.toInt())) {
            setUVRange(0f, 0f, 1f, 1f)
            cbox(0f, 0.35f, 0f, 1.1f, 0.7f, 1.1f)
        }
    }

    /** Platform slab with yellow safety edge (origin center, length along z). */
    fun platform(): Model = models.getOrPut("platform") {
        mb.begin()
        var mpb = mb.part("slab", GL20.GL_TRIANGLES, ATTRS, matColor(0xa8a29aff.toInt()))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.cbox(0f, 0.275f, 0f, 3.4f, 0.55f, 24f)
        mpb = mb.part("edge", GL20.GL_TRIANGLES, ATTRS, matColor(0xf2c53cff.toInt()))
        mpb.cbox(-1.55f, 0.58f, 0f, 0.4f, 0.06f, 24f)
        mb.end()
    }

    /** Station shelter: posts + roof + glass back wall. */
    fun shelter(): Model = models.getOrPut("shelter") {
        mb.begin()
        var mpb = mb.part("frame", GL20.GL_TRIANGLES, ATTRS, matColor(0x46505aff.toInt()))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.cbox(-2.6f, 1.25f, -2.6f, 0.14f, 2.5f, 0.14f)
        mpb.cbox(-2.6f, 1.25f, 2.6f, 0.14f, 2.5f, 0.14f)
        mpb.cbox(2.6f, 1.25f, -2.6f, 0.14f, 2.5f, 0.14f)
        mpb.cbox(2.6f, 1.25f, 2.6f, 0.14f, 2.5f, 0.14f)
        mpb.cbox(0f, 2.55f, 0f, 5.6f, 0.18f, 5.6f)
        mpb = mb.part("glass", GL20.GL_TRIANGLES, ATTRS, matColor(0x9fd8e8ff.toInt(), 0.9f))
        mpb.cbox(0f, 1.3f, -2.65f, 5.5f, 1.9f, 0.1f)
        mb.end()
    }

    fun bench(): Model = models.getOrPut("bench") {
        mb.begin()
        var mpb = mb.part("seat", GL20.GL_TRIANGLES, ATTRS, matColor(0x7a5c3cff.toInt()))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.cbox(0f, 0.5f, 0f, 1.7f, 0.1f, 0.5f)
        mpb.cbox(0f, 0.75f, -0.22f, 1.7f, 0.5f, 0.1f)
        mpb = mb.part("legs", GL20.GL_TRIANGLES, ATTRS, matColor(0x3c434bff.toInt()))
        mpb.cbox(-0.7f, 0.25f, 0f, 0.1f, 0.5f, 0.4f)
        mpb.cbox(0.7f, 0.25f, 0f, 0.1f, 0.5f, 0.4f)
        mb.end()
    }

    fun stationSign(): Model = models.getOrPut("stSign") {
        mb.begin()
        var mpb = mb.part("post", GL20.GL_TRIANGLES, ATTRS, matColor(0x3c434bff.toInt()))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.cbox(0f, 1.1f, 0f, 0.1f, 2.2f, 0.1f)
        mpb = mb.part("board", GL20.GL_TRIANGLES, ATTRS, matColor(0x2a3057ff.toInt()))
        mpb.cbox(0f, 1.9f, 0f, 1.4f, 0.7f, 0.08f)
        mb.end()
    }

    fun billboard(variant: Int): Model = models.getOrPut("bill$variant") {
        val colors = intArrayOf(0xd94a38ff.toInt(), 0x3e7bc0ff.toInt(), 0xf2a63bff.toInt(), 0x8a55c9ff.toInt())
        mb.begin()
        var mpb = mb.part("posts", GL20.GL_TRIANGLES, ATTRS, matColor(0x3c434bff.toInt()))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.cbox(-1.5f, 1.3f, 0f, 0.14f, 2.6f, 0.14f)
        mpb.cbox(1.5f, 1.3f, 0f, 0.14f, 2.6f, 0.14f)
        mpb = mb.part("panel", GL20.GL_TRIANGLES, ATTRS, matColor(colors[variant % colors.size]))
        mpb.cbox(0f, 3.1f, 0f, 3.4f, 1.9f, 0.16f)
        mpb = mb.part("frame", GL20.GL_TRIANGLES, ATTRS, matColor(0xf2ead0ff.toInt()))
        mpb.cbox(0f, 4.1f, 0f, 3.6f, 0.16f, 0.2f)
        mpb.cbox(0f, 2.1f, 0f, 3.6f, 0.16f, 0.2f)
        mb.end()
    }

    /** Graffiti wall segment (sits at track edge, length along z). */
    fun graffitiWall(): Model = models.getOrPut("gwall") {
        build(matTex(TextureGen.wallTex)) {
            setUVRange(0f, 0f, 1.6f, 1f)
            cbox(0f, 1.4f, 0f, 0.22f, 2.8f, 5.5f)
        }
    }

    /** Tall tunnel side wall (length along z). */
    fun tunnelWall(): Model = models.getOrPut("twall") {
        build(matTex(TextureGen.tunnelTex)) {
            setUVRange(0f, 0f, 8f, 1.6f)
            cbox(0f, 3.25f, 0f, 0.4f, 6.5f, 25f)
        }
    }

    /** Tunnel rib arch (boxy). */
    fun tunnelArch(): Model = models.getOrPut("tarch") {
        build(matColor(0x585653ff.toInt())) {
            setUVRange(0f, 0f, 1f, 1f)
            cbox(-5.1f, 3.0f, 0f, 0.5f, 6.0f, 0.7f)
            cbox(5.1f, 3.0f, 0f, 0.5f, 6.0f, 0.7f)
            cbox(0f, 6.2f, 0f, 10.7f, 0.8f, 0.7f)
        }
    }

    /**
     * v4.2 HOVERBOARD v2 — proper SS-style board: chamfered teal deck,
     * yellow racing stripe, griptape nose/tail, angled fins at both ends,
     * dark undercarriage. Replaces the plain teal slab.
     */
    fun hoverboard(): Model = models.getOrPut("hoverboard2") {
        mb.begin()
        var mpb = mb.part("deck", GL20.GL_TRIANGLES, ATTRS, matColor(0x2fd0bfff.toInt()))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.cbox(0f, 0.045f, 0f, 0.6f, 0.05f, 1.44f)
        // top deck slightly narrower (chamfer illusion)
        mpb = mb.part("deckTop", GL20.GL_TRIANGLES, ATTRS, matColor(0x3ee2cfff.toInt()))
        mpb.cbox(0f, 0.08f, 0f, 0.52f, 0.04f, 1.3f)
        // yellow racing stripe down the middle
        mpb = mb.part("stripe", GL20.GL_TRIANGLES, ATTRS, matColor(0xffd23eff.toInt()))
        mpb.cbox(0f, 0.105f, 0f, 0.13f, 0.015f, 1.26f)
        // griptape nose + tail pads
        mpb = mb.part("grip", GL20.GL_TRIANGLES, ATTRS, matColor(0x223038ff.toInt()))
        mpb.cbox(0f, 0.105f, 0.56f, 0.4f, 0.015f, 0.22f)
        mpb.cbox(0f, 0.105f, -0.56f, 0.4f, 0.015f, 0.22f)
        // angled fins (nose up-kick, tail up-kick)
        mpb = mb.part("fins", GL20.GL_TRIANGLES, ATTRS, matColor(0x1fb89fff.toInt()))
        mpb.cbox(0f, 0.12f, 0.72f, 0.5f, 0.05f, 0.14f)
        mpb.cbox(0f, 0.12f, -0.72f, 0.5f, 0.05f, 0.14f)
        // dark undercarriage + skid rails
        mpb = mb.part("under", GL20.GL_TRIANGLES, ATTRS, matColor(0x243038ff.toInt()))
        mpb.cbox(0f, 0.015f, 0f, 0.42f, 0.03f, 1.2f)
        mb.end()
    }

    /**
     * v4.2 TUNNEL LIGHTING PASS — ceiling tube lamp: dark bracket + bright
     * warm tube + a second inner "burning filament" box so the tube reads lit
     * even with no point lights in the default shader.
     */
    fun tunnelLamp(side: Int): Model = models.getOrPut("tlamp$side") {
        mb.begin()
        var mpb = mb.part("bracket", GL20.GL_TRIANGLES, ATTRS, matColor(0x33312eff.toInt()))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.cbox(side * 4.55f, 5.55f, 0f, 0.9f, 0.12f, 0.3f)
        mpb = mb.part("tube", GL20.GL_TRIANGLES, ATTRS, matColor(0xfff3c4ff.toInt()))
        mpb.cbox(side * 4.35f, 5.45f, 0f, 1.7f, 0.16f, 0.22f)
        mpb = mb.part("filament", GL20.GL_TRIANGLES, ATTRS, matColor(0xffffffbf.toInt()))
        mpb.cbox(side * 4.35f, 5.36f, 0f, 1.5f, 0.05f, 0.14f)
        mb.end()
    }

    /**
     * v4.3 Bridge truss REBUILD — the old model was one solid 24u red slab +
     * teeth and projected as giant floating red walls in every bridge segment.
     * Now an OPEN lattice: slim vertical posts + horizontal rails + a top beam,
     * so the world reads through it like real bridge steel.
     */
    fun girder(): Model = models.getOrPut("girder") {
        mb.begin()
        var mpb = mb.part("posts", GL20.GL_TRIANGLES, ATTRS, matColor(0x7a4a38ff.toInt()))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        var z = -11f
        while (z <= 11f) {
            mpb.cbox(0f, 2.9f, z, 0.2f, 5.8f, 0.2f)
            z += 2.75f
        }
        mpb = mb.part("rails", GL20.GL_TRIANGLES, ATTRS, matColor(0x8a563eff.toInt()))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.cbox(0f, 1.8f, 0f, 0.15f, 0.2f, 22f)
        mpb.cbox(0f, 3.6f, 0f, 0.15f, 0.2f, 22f)
        mpb.cbox(0f, 4.9f, 0f, 0.15f, 0.2f, 22f)
        mpb = mb.part("topBeam", GL20.GL_TRIANGLES, ATTRS, matColor(0x66412fff.toInt()))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.cbox(0f, 6.0f, 0f, 0.34f, 0.44f, 24f)
        mb.end()
    }

    /** Static waiting passenger (variant colors). */
    fun passenger(variant: Int): Model = models.getOrPut("pax$variant") {
        val bodies = intArrayOf(0x37b8a8ff.toInt(), 0xd94a38ff.toInt(), 0x8a55c9ff.toInt(), 0xf2a63bff.toInt())
        val pants = intArrayOf(0x3f5a83ff.toInt(), 0x2e2320ff.toInt(), 0x4a4a52ff.toInt(), 0x2b4a3aff.toInt())
        mb.begin()
        var mpb = mb.part("legs", GL20.GL_TRIANGLES, ATTRS, matColor(pants[variant % 4]))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.cbox(0f, 0.275f, 0f, 0.34f, 0.55f, 0.26f)
        mpb = mb.part("body", GL20.GL_TRIANGLES, ATTRS, matColor(bodies[variant % 4]))
        mpb.cbox(0f, 0.82f, 0f, 0.5f, 0.55f, 0.3f)
        mpb = mb.part("head", GL20.GL_TRIANGLES, ATTRS, matColor(0xf2c49bff.toInt()))
        mpb.cbox(0f, 1.3f, 0f, 0.36f, 0.36f, 0.32f)
        mb.end()
    }

    /** Building block with facade texture (origin at floor center). */
    fun building(w: Float, h: Float, d: Float, tex: Texture): Model {
        val key = "bld${tex.hashCode()}-${"%.1f".format(w)}-${"%.1f".format(h)}"
        return models.getOrPut(key) {
            build(matTex(tex)) {
                setUVRange(0f, 0f, (w / 3f).coerceAtLeast(1f), (h / 3f).coerceAtLeast(1f))
                cbox(0f, h / 2f, 0f, w, h, d)
            }
        }
    }

    fun dispose() {
        for (m in models.values) m.dispose()
        models.clear()
        materials.clear()
    }
}

// scratch corners for the top-level cbox extension
private val _c000 = Vector3(); private val _c010 = Vector3(); private val _c100 = Vector3(); private val _c110 = Vector3()
private val _c001 = Vector3(); private val _c011 = Vector3(); private val _c101 = Vector3(); private val _c111 = Vector3()

/** Axis-aligned box centered at (cx,cy,cz) — safe 8-corner path (normals auto-computed). */
fun MeshPartBuilder.cbox(cx: Float, cy: Float, cz: Float, w: Float, h: Float, d: Float) {
    val x0 = cx - w / 2f; val x1 = cx + w / 2f
    val y0 = cy - h / 2f; val y1 = cy + h / 2f
    val z0 = cz - d / 2f; val z1 = cz + d / 2f
    _c000.set(x0, y0, z0); _c010.set(x0, y1, z0); _c100.set(x1, y0, z0); _c110.set(x1, y1, z0)
    _c001.set(x0, y0, z1); _c011.set(x0, y1, z1); _c101.set(x1, y0, z1); _c111.set(x1, y1, z1)
    box(_c000, _c010, _c100, _c110, _c001, _c011, _c101, _c111)
}
