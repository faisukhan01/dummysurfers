package com.dummysurfers.core.gfx3d

import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.dummysurfers.core.entities.Chaser
import com.dummysurfers.core.entities.Player
import com.dummysurfers.core.state.PlayerState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * v4 articulated character rigs — a tiny parent/child transform hierarchy over
 * ModelInstances. Each [Part] hangs its mesh from a JOINT (mesh built so the
 * joint sits at the part origin); per-frame pose angles are written into
 * [Part.local] = T(baseOffset)·R(pitch,yaw,roll), then world matrices compose.
 */
class Part(val instance: ModelInstance, val parent: Part?, val bx: Float, val by: Float, val bz: Float) {
    val local = Matrix4()
    val world = Matrix4()

    init { local.setToTranslation(bx, by, bz) }

    fun update(parentWorld: Matrix4) {
        world.set(parentWorld).mul(local)
        instance.transform.set(world)
    }
}

class Rig {
    val parts = ArrayList<Part>(12)

    fun add(instance: ModelInstance, parent: Part?, bx: Float, by: Float, bz: Float): Part {
        val p = Part(instance, parent, bx, by, bz)
        parts.add(p)
        return p
    }

    fun renderables(): List<ModelInstance> = parts.map { it.instance }

    /** Compose world transforms down the tree. NOTE: locals are owned by pose() calls. */
    fun update(root: Matrix4) {
        for (p in parts) {
            val pw = p.parent?.world ?: root
            p.update(pw)
        }
    }
}

/** Pose angles for one part (radians). */
class Ang(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f)

private val q = Quaternion()
private val tmpM = Matrix4()
private val tmpV = Vector3()

/** Apply euler angles to a part's local matrix after translation. */
fun Part.pose(a: Ang) {
    tmpM.setToTranslation(bx, by, bz)
    q.setEulerAnglesRad(a.y, a.x, a.z)
    tmpM.rotate(q)
    local.set(tmpM)
}

/**
 * A chibi human — SS proportions (big head ≈ 40%, chunky sneakers, expressive
 * poses). One rig; palette-driven so JACK, BLAZE, VOLT, NOVA and the GUARD all
 * come from the same builder. Head/torso/pack/etc. are baked multi-part models
 * from [ModelFactory], limbs are animated individually.
 */
class Human3D(
    private val f: ModelFactory,
    skin: Int, hoodie: Int, pants: Int, shoes: Int, cap: Int, backpack: Int,
    accent: Int, hair: Int, vest: Int, hoodLining: Int, capPanel: Int,
    accessory: Int = 0,
    private val isGuard: Boolean
) {
    val rig = Rig()
    val thighL: Part; val thighR: Part; val shinL: Part; val shinR: Part
    val armL: Part; val armR: Part; val foreL: Part; val foreR: Part
    val torso: Part; val head: Part

    val aThighL = Ang(); val aThighR = Ang(); val aShinL = Ang(); val aShinR = Ang()
    val aArmL = Ang(); val aArmR = Ang(); val aForeL = Ang(); val aForeR = Ang()
    val aTorso = Ang(); val aHead = Ang()

    init {
        // limb meshes pivot at their TOP (joint) — geometry hangs downward
        val thighM = f.colorBox("thigh$pants", 0.21f, 0.32f, 0.24f, pants)
        val shinM = f.colorBox("shin$pants", 0.18f, 0.26f, 0.2f, pants)
        // v5.2: SNEAKER REBUILD — the flat red box read as a sock from the
        // chase cam. Now: white mid-sole slab + upper + white toe cap + heel
        // tab (heel faces the camera — the detail you actually see running).
        val shoeM = buildShoe(shoes)
        // v4.7: slimmed hanging upper-arm — centered boxes jutted above the
        // shoulder and read as diagonal wings whenever the torso rolled
        val upperM = f.colorBoxHang("upper$hoodie", 0.15f, 0.24f, 0.17f, hoodie)
        // v4.2: guard gets uniform-covered forearms (bare skin read as T-shirt
        // arms on a duty officer)
        val foreM = f.colorBoxHang("fore$skin$isGuard", 0.13f, 0.2f, 0.15f, if (isGuard) hoodie else skin)
        // v5.2: sphere hands (box hands read as mittens)
        val handM = f.colorBall("handBall$skin", 0.15f, skin, 7)
        // v5.2: shoulder joint balls — smooth the arm/torso seam in silhouette
        val shoulderM = f.colorBall("shoulderBall$hoodie", 0.17f, hoodie, 7)

        thighL = rig.add(ModelInstance(thighM), null, -0.13f, 0.72f, 0f)
        thighR = rig.add(ModelInstance(thighM), null, 0.13f, 0.72f, 0f)
        shinL = rig.add(ModelInstance(shinM), thighL, 0f, -0.32f, 0f)
        shinR = rig.add(ModelInstance(shinM), thighR, 0f, -0.32f, 0f)
        rig.add(ModelInstance(shoeM), shinL, 0f, -0.26f, 0.07f)
        rig.add(ModelInstance(shoeM), shinR, 0f, -0.26f, 0.07f)

        val torsoM = buildTorso(hoodie, vest, hoodLining, accent, backpack)
        torso = rig.add(ModelInstance(torsoM), null, 0f, 0.72f, 0f)
        armL = rig.add(ModelInstance(upperM), torso, -0.38f, 0.5f, 0f)
        armR = rig.add(ModelInstance(upperM), torso, 0.38f, 0.5f, 0f)
        rig.add(ModelInstance(shoulderM), torso, -0.38f, 0.5f, 0f)
        rig.add(ModelInstance(shoulderM), torso, 0.38f, 0.5f, 0f)
        foreL = rig.add(ModelInstance(foreM), armL, 0f, -0.27f, 0f)
        foreR = rig.add(ModelInstance(foreM), armR, 0f, -0.27f, 0f)
        rig.add(ModelInstance(handM), foreL, 0f, -0.2f, 0.02f)
        rig.add(ModelInstance(handM), foreR, 0f, -0.2f, 0.02f)

        val headM = buildHead(skin, hair, cap, capPanel, accent)
        head = rig.add(ModelInstance(headM), torso, 0f, 0.58f, 0f)

        // v4.2 signature accessories (child parts of head/torso)
        when (accessory) {
            1 -> {
                // street-artist spray can strapped to the pack's side
                val canM = buildSprayCan(accent)
                rig.add(ModelInstance(canM), torso, 0.27f, 0.3f, -0.33f)
            }
            2 -> {
                // cap goggles: strap around the dome + teal lens on the true front
                val strapM = f.colorBox("gogStrap$accent", 0.05f, 0.055f, 0.5f, 0x22262cff.toInt())
                rig.add(ModelInstance(strapM), head, -0.345f, 0.6f, 0f)
                rig.add(ModelInstance(strapM), head, 0.345f, 0.6f, 0f)
                // v4.7: BACK segment across the dome — the side straps were
                // edge-on from the chase cam, so VOLT's goggles were invisible
                // in every in-game shot (only the CHARS portrait showed them)
                val strapBackM = f.colorBox("gogStrapBack$accent", 0.62f, 0.055f, 0.05f, 0x22262cff.toInt())
                rig.add(ModelInstance(strapBackM), head, 0f, 0.6f, 0.32f) // v5.2.1: outside the dome
                val lensM = f.colorBox("gogLens$accent", 0.4f, 0.13f, 0.05f, accent)
                rig.add(ModelInstance(lensM), head, 0f, 0.61f, -0.325f)
                val rimM = f.colorBox("gogRim$accent", 0.44f, 0.17f, 0.03f, 0x22262cff.toInt())
                rig.add(ModelInstance(rimM), head, 0f, 0.61f, -0.355f)
            }
            3 -> {
                // headphones: over-cap band + cups that show from behind
                val bandM = f.colorBox("hpBand$accent", 0.68f, 0.06f, 0.09f, 0x22262cff.toInt())
                rig.add(ModelInstance(bandM), head, 0f, 0.82f, 0f) // v5.2.1: rides the dome crown
                val cupM = f.colorBox("hpCup$accent", 0.09f, 0.2f, 0.17f, 0x22262cff.toInt())
                rig.add(ModelInstance(cupM), head, -0.35f, 0.56f, 0f)
                rig.add(ModelInstance(cupM), head, 0.35f, 0.56f, 0f)
                val ringM = f.colorBox("hpRing$accent", 0.1f, 0.12f, 0.18f, accent)
                rig.add(ModelInstance(ringM), head, -0.36f, 0.56f, 0f)
                rig.add(ModelInstance(ringM), head, 0.36f, 0.56f, 0f)
            }
        }
    }

    /** Spray can: colored body + silver shoulder + nozzle. */
    private fun buildSprayCan(accent: Int): com.badlogic.gdx.graphics.g3d.Model {
        val f = this.f
        val mb = f.mb
        mb.begin()
        var mpb = mb.part("canBody", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(accent))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.cylinder(0.055f, 0.2f, 0.055f, 10)
        mpb = mb.part("canTop", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(0xc9ced6ff.toInt()))
        mpb.cylinder(0.045f, 0.04f, 0.045f, 10)
        mpb = mb.part("canNozzle", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(0x22262cff.toInt()))
        mpb.cbox(0f, 0.13f, 0.02f, 0.03f, 0.04f, 0.05f)
        return mb.end()
    }

    /**
     * v5.2 sneaker: white mid-sole + colored upper + white toe cap + heel
     * tab. The heel tab deliberately faces +z (the chase camera) — it's the
     * only shoe detail visible mid-run, and SS sneakers always flash white.
     */
    private fun buildShoe(shoes: Int): com.badlogic.gdx.graphics.g3d.Model {
        val f = this.f
        val mb = f.mb
        mb.begin()
        var mpb = mb.part("sole", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(0xeef0f2ff.toInt()))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.cbox(0f, -0.045f, 0.01f, 0.21f, 0.07f, 0.38f)
        mpb = mb.part("upper", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(shoes))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.cbox(0f, 0.03f, 0f, 0.19f, 0.09f, 0.34f)
        mpb = mb.part("toe", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(0xeef0f2ff.toInt()))
        mpb.cbox(0f, 0.005f, -0.145f, 0.17f, 0.055f, 0.07f)
        mpb = mb.part("heel", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(0xeef0f2ff.toInt()))
        mpb.cbox(0f, 0.05f, 0.175f, 0.1f, 0.08f, 0.03f)
        return mb.end()
    }

    private fun buildTorso(hoodie: Int, vest: Int, hoodLining: Int, accent: Int, backpack: Int): com.badlogic.gdx.graphics.g3d.Model {
        val f = this.f
        val mb = f.mb
        mb.begin()
        // main hoodie shell
        var mpb = mb.part("shell", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(hoodie))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.cbox(0f, 0.29f, 0f, 0.6f, 0.58f, 0.34f)
        // denim vest panels hugging the sides (Jack's signature layer)
        if (vest != 0) {
            mpb = mb.part("vest", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(vest))
            mpb.setUVRange(0f, 0f, 1f, 1f)
            mpb.cbox(-0.315f, 0.34f, 0f, 0.07f, 0.42f, 0.36f)
            mpb.cbox(0.315f, 0.34f, 0f, 0.07f, 0.42f, 0.36f)
        }
        // chest tee stripe (red under-layer peeking out) — TRUE front (-z;
        // v4.2: it used to render on the BACK while the pack sat on the chest)
        val lining = if (hoodLining != 0) hoodLining else mul(hoodie, 0.8f)
        mpb = mb.part("tee", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(lining))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.cbox(0f, 0.4f, -0.18f, 0.3f, 0.3f, 0.02f)
        // v5.1: white hoodie drawstrings hanging over the tee — the thumbnail
        // character's signature chest detail (TRUE front = -z)
        if (!isGuard) {
            mpb = mb.part("strings", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(0xf5f5f0ff.toInt()))
            mpb.cbox(-0.07f, 0.36f, -0.2f, 0.035f, 0.16f, 0.03f)
            mpb.cbox(0.07f, 0.36f, -0.2f, 0.035f, 0.16f, 0.03f)
        }
        // v5.2 BACKPACK REBUILD — the old 0.44×0.50 slab covered the whole
        // torso back with flat color ("blue box body" in QA). Smaller pack +
        // side pockets + vertical straps with gold clips + hood roll behind
        // the neck = real gear design the chase cam can actually read.
        if (!isGuard) {
            mpb = mb.part("pack", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(backpack))
            mpb.setUVRange(0f, 0f, 1f, 1f)
            mpb.cbox(0f, 0.31f, 0.26f, 0.4f, 0.44f, 0.2f)
            mpb = mb.part("packFlap", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(mul(backpack, 0.8f)))
            mpb.setUVRange(0f, 0f, 1f, 1f)
            mpb.cbox(0f, 0.47f, 0.345f, 0.4f, 0.12f, 0.05f)
            mpb = mb.part("packBand", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(mul(backpack, 0.5f)))
            mpb.cbox(0f, 0.33f, 0.355f, 0.42f, 0.09f, 0.04f)
            mpb = mb.part("packHandle", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(mul(backpack, 0.62f)))
            mpb.cbox(0f, 0.545f, 0.3f, 0.14f, 0.05f, 0.07f)
            // side pockets (darker) — break up the slab silhouette
            mpb = mb.part("packPocket", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(mul(backpack, 0.72f)))
            mpb.cbox(-0.235f, 0.27f, 0.27f, 0.09f, 0.2f, 0.16f)
            mpb.cbox(0.235f, 0.27f, 0.27f, 0.09f, 0.2f, 0.16f)
            // v5.2.1: light front pocket + gold zip — the pack face finally has
            // a focal detail (the old two dark straps read as a chest harness)
            mpb = mb.part("packPocketFront", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(mul(backpack, 1.3f)))
            mpb.cbox(0f, 0.24f, 0.37f, 0.24f, 0.12f, 0.02f)
            mpb = mb.part("packZip", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(accent))
            mpb.cbox(0f, 0.298f, 0.382f, 0.24f, 0.022f, 0.012f)
            // hood roll behind the neck (white lining) — Jake DNA from behind
            mpb = mb.part("hoodRoll", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(if (hoodLining != 0) hoodLining else mul(hoodie, 1.3f)))
            mpb.cbox(0f, 0.565f, 0.19f, 0.34f, 0.09f, 0.1f)
            mpb = mb.part("straps", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(mul(backpack, 0.7f)))
            mpb.cbox(-0.16f, 0.42f, 0.19f, 0.07f, 0.34f, 0.02f)
            mpb.cbox(0.16f, 0.42f, 0.19f, 0.07f, 0.34f, 0.02f)
        } else {
            // guard: gold badge + belt + shoulder epaulettes
            mpb = mb.part("badge", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(0xffd23eff.toInt()))
            mpb.cbox(0.14f, 0.42f, -0.18f, 0.09f, 0.09f, 0.02f)
            mpb = mb.part("belt", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(0x1c2028ff.toInt()))
            mpb.cbox(0f, 0.04f, 0f, 0.62f, 0.1f, 0.36f)
            mpb = mb.part("epau", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(0xffd23eff.toInt(), 0.85f))
            mpb.cbox(-0.3f, 0.57f, 0f, 0.14f, 0.05f, 0.2f)
            mpb.cbox(0.3f, 0.57f, 0f, 0.14f, 0.05f, 0.2f)
        }
        return mb.end()
    }

    private fun buildHead(skin: Int, hair: Int, cap: Int, capPanel: Int, accent: Int): com.badlogic.gdx.graphics.g3d.Model {
        val f = this.f
        val mb = f.mb
        mb.begin()
        var mpb = mb.part("face", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(skin))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.cbox(0f, 0.26f, 0f, 0.58f, 0.52f, 0.5f)
        // spiky fringe under the cap edge (TRUE front = -z, the run direction;
        // v4.2 fix: face features were mirrored — they stared at the chase cam)
        mpb = mb.part("hair", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(hair))
        mpb.cbox(0f, 0.50f, -0.04f, 0.6f, 0.09f, 0.54f)
        mpb.cbox(0f, 0.44f, -0.27f, 0.44f, 0.1f, 0.04f)
        // BACK of the head — hair panel under the cap (v4.2: the uniform-skin
        // head box read bald from the chase cam; SS heads show hair at the back;
        // v5.1: slimmed + lowered; v5.2: widened 0.46→0.52 and dropped a hair
        // spike row at the nape so the back of the head finally reads as HAIR)
        mpb.cbox(0f, 0.29f, 0.25f, 0.52f, 0.34f, 0.03f)
        mpb.cbox(-0.12f, 0.14f, 0.25f, 0.1f, 0.09f, 0.03f)
        mpb.cbox(0.12f, 0.14f, 0.25f, 0.1f, 0.09f, 0.03f)
        // v5.2.1 CAP REBUILD — the old 0.16-thick box slab floated above the
        // head and read as a red BRICK from the chase cam (QA hud.png). A
        // squashed low-poly sphere hugs the skull like a real baseball-cap
        // dome; baked at the head origin via setVertexTransform (the same
        // trick the train wheels already use).
        mpb = mb.part("cap", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(cap))
        mpb.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 0.57f, -0.005f))
        mpb.sphere(0.72f, 0.37f, 0.63f, 14, 9)
        mpb.setVertexTransform(null)
        mpb.cbox(0f, 0.53f, 0.345f, 0.54f, 0.07f, 0.16f)   // backwards brim — proud to the chase cam
        mpb.cbox(0f, 0.755f, -0.005f, 0.13f, 0.05f, 0.11f) // top button stud
        // back stitch line under the brim — reads as the cap's rear seam
        mpb = mb.part("capStitch", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(cap, 0.78f))
        mpb.cbox(0f, 0.62f, 0.315f, 0.05f, 0.13f, 0.02f)
        if (capPanel != 0) {
            mpb = mb.part("panel", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(capPanel))
            mpb.cbox(0f, 0.62f, -0.315f, 0.30f, 0.14f, 0.03f) // true front badge — menu/face-off view
        }
        if (isGuard) {
            // gold badge on the cap + mustache — true front (-z)
            mpb = mb.part("gbadge", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(0xffd23eff.toInt()))
            mpb.cbox(0f, 0.64f, -0.28f, 0.13f, 0.11f, 0.03f)
            mpb = mb.part("stache", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(0x4a3524ff.toInt()))
            mpb.cbox(0f, 0.17f, -0.265f, 0.3f, 0.07f, 0.03f)
        }
        // ears — v5.2: pushed further out so they read from the chase cam
        mpb = mb.part("ears", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(mul(skin, 0.96f)))
        mpb.cbox(-0.295f, 0.26f, -0.02f, 0.05f, 0.11f, 0.08f)
        mpb.cbox(0.295f, 0.26f, -0.02f, 0.05f, 0.11f, 0.08f)
        // v5.1: big cartoon eyes — the launcher-thumbnail character's whole
        // personality. TRUE front = -z (menu/CHARS portrait + guard face-off);
        // hidden from the chase cam in-game, exactly like the cap panel.
        mpb = mb.part("eyes", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(0xffffffff.toInt()))
        mpb.cbox(-0.13f, 0.33f, -0.245f, 0.11f, 0.14f, 0.03f)
        mpb.cbox(0.13f, 0.33f, -0.245f, 0.11f, 0.14f, 0.03f)
        mpb = mb.part("pupils", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(0x27303fff.toInt()))
        mpb.cbox(-0.115f, 0.305f, -0.262f, 0.055f, 0.075f, 0.02f)
        mpb.cbox(0.115f, 0.305f, -0.262f, 0.055f, 0.075f, 0.02f)
        // v5.1: warm little open-mouth smile — without it the low-set eyes read
        // as a frown in the menu portrait (the whole "grumpy thumbnail" effect)
        mpb = mb.part("mouth", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(0x9c4f3fff.toInt()))
        mpb.cbox(0f, 0.15f, -0.262f, 0.15f, 0.055f, 0.02f)
        return mb.end()
    }

    private fun mul(hex: Int, fac: Float): Int {
        val c = com.badlogic.gdx.graphics.Color(hex)
        // v4.5 FIX: Color.toIntBits() packs ALPHA into the high byte (GL/ABGR
        // order) while Color(int)/matColor read RED from the high byte — the old
        // round-trip scrambled the channels (amber pack × 0.8 rendered MAGENTA;
        // the old white pack's flap was pinkish-white for the same reason).
        // Pack explicitly in RGBA8888 (red = high byte) like every hex literal.
        fun ch(v: Float) = ((v * fac).coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
        return (ch(c.r) shl 24) or (ch(c.g) shl 16) or (ch(c.b) shl 8) or (hex and 0xFF)
    }

    /**
     * Drive the rig. [root] should be positioned at the character's ground
     * point (feet); running-cycle and state poses are computed here.
     */
    fun animate(
        root: Matrix4,
        state: PlayerState, phase: Float, stateTime: Float,
        lean: Float, jumpT: Float, stumbleOn: Boolean,
        flail: Float, time: Float, caughtGrab: Boolean
    ) {
        // defaults
        var torsoPitch = 0.10f
        var headPitch = -0.04f
        var rootRoll = lean * 0.5f
        var rootYaw = -lean * 0.55f
        var rootPitch = 0f
        var bodyLift = 0f

        when (state) {
            PlayerState.RUNNING, PlayerState.LANE_SWITCH -> {
                val s = sin(phase); val c = cos(phase)
                aThighL.x = s * 0.95f; aThighR.x = -s * 0.95f
                aShinL.x = -(0.25f + kotlin.math.max(0f, -s) * 1.25f)
                aShinR.x = -(0.25f + kotlin.math.max(0f, s) * 1.25f)
                // v4.7 ARM PUMP RETUNE: ±0.85 rad swung the chunky upper-arm
                // boxes up to shoulder height, where the low chase cam projected
                // them as horizontal "T-pose" slabs (BLAZE/VOLT QA shots); real
                // runners pump elbows, not windmill. Swing 0.85→0.5, abduction
                // 0.12→0.2 (arms clear the torso), elbow bend -0.7→-1.0 so the
                // pump reads at the FOREARM like SS Jack.
                aArmL.x = -s * 0.5f; aArmR.x = s * 0.5f
                aArmL.z = 0.2f; aArmR.z = -0.2f
                aForeL.x = -1.0f; aForeR.x = -1.0f
                bodyLift = abs(s) * 0.05f
            }
            PlayerState.JUMPING -> {
                val t = jumpT.coerceIn(0f, 1f)
                val tuck = sin(t * Math.PI.toFloat())
                aThighL.x = -1.15f * tuck; aThighR.x = -0.75f * tuck
                aShinL.x = -1.5f * tuck; aShinR.x = -1.1f * tuck
                aArmL.x = -2.3f * tuck; aArmR.x = -2.3f * tuck
                aArmL.z = 0.35f; aArmR.z = -0.35f
                aForeL.x = -0.4f; aForeR.x = -0.4f
                torsoPitch = 0.16f + t * 0.1f
            }
            PlayerState.SLIDING -> {
                // SS roll: whole body somersaults around its center
                rootPitch = -(stateTime / 0.5f) * (Math.PI * 2f).toFloat()
                bodyLift = 0.42f
                aThighL.x = -1.7f; aThighR.x = -1.7f
                aShinL.x = -2.0f; aShinR.x = -2.0f
                aArmL.x = -1.4f; aArmR.x = -1.4f
                aArmL.z = 0.5f; aArmR.z = -0.5f
                aForeL.x = -1.6f; aForeR.x = -1.6f
                torsoPitch = 0.7f
                rootRoll = 0f; rootYaw = 0f
            }
            PlayerState.LANDING -> {
                aThighL.x = -0.5f; aThighR.x = -0.5f
                aShinL.x = -0.7f; aShinR.x = -0.7f
                aArmL.x = -1.1f; aArmR.x = -1.1f
                torsoPitch = 0.3f
                bodyLift = -0.09f
            }
            PlayerState.DEAD -> {
                if (caughtGrab) {
                    // grabbed by the guard: arms pinned up, body yanked back
                    aArmL.x = -2.6f; aArmR.x = -2.6f
                    aForeL.x = -0.3f; aForeR.x = -0.3f
                    aThighL.x = 0.3f; aThighR.x = -0.4f
                    aShinL.x = -0.5f; aShinR.x = -0.7f
                    torsoPitch = -0.25f
                    rootYaw = sin(time * 30f) * 0.06f
                } else {
                    // crash tumble — v4.4: capped at ~half a flip and stops
                    // (the uncapped spin threw limbs INTO the lens: giant shoe/
                    // arm filled the frame at death, QA menu shot-1); the
                    // settle reads as a body-slam, not a windmill
                    rootPitch = -(stateTime * 7f).coerceAtMost(3.4f)
                    rootRoll = 0.5f
                    aArmL.x = -2.8f; aArmR.x = -2.8f
                    aArmL.z = 0.8f; aArmR.z = -0.8f
                    aThighL.x = -1.2f; aThighR.x = 0.4f
                    aShinL.x = -0.8f; aShinR.x = -1.4f
                }
            }
            else -> {}
        }

        if (stumbleOn && state == PlayerState.RUNNING || stumbleOn && state == PlayerState.LANE_SWITCH) {
            // panic flail — arms windmill overhead
            aArmL.x = -2.4f + sin(flail * 26f) * 0.5f
            aArmR.x = -2.4f - sin(flail * 26f) * 0.5f
            aArmL.z = 0.5f; aArmR.z = -0.5f
            torsoPitch = 0.32f + sin(flail * 26f) * 0.08f
            headPitch = sin(flail * 31f) * 0.15f
        }

        aTorso.x = torsoPitch; aTorso.y = 0f; aTorso.z = lean * 0.1f
        aHead.x = headPitch

        // root: lift + tumble rotations
        root.trn(0f, bodyLift, 0f)
        if (rootPitch != 0f) root.rotate(Vector3(1f, 0f, 0f), rootPitch * 57.2958f)
        if (rootRoll != 0f) root.rotate(Vector3(0f, 0f, 1f), rootRoll * 57.2958f)
        if (rootYaw != 0f) root.rotate(Vector3(0f, 1f, 0f), rootYaw * 57.2958f)

        // write pose angles into part locals
        thighL.pose(aThighL); thighR.pose(aThighR)
        shinL.pose(aShinL); shinR.pose(aShinR)
        torso.pose(aTorso)
        armL.pose(aArmL); armR.pose(aArmR)
        foreL.pose(aForeL); foreR.pose(aForeR)
        head.pose(aHead)
        rig.update(root)
    }
}

/** The guard's loyal dog — quadruped gallop rig. */
class Dog3D(private val f: ModelFactory) {
    val rig = Rig()
    private val legFL: Part; val legFR: Part; val legBL: Part; val legBR: Part
    private val tail: Part
    private val body: Part; private val head: Part
    private val a = Ang()

    init {
        val bodyM = f.colorBox("dogBody", 0.28f, 0.3f, 0.62f, 0x8a5a34ff.toInt())
        val headM = f.colorBox("dogHead", 0.26f, 0.24f, 0.26f, 0x8a5a34ff.toInt())
        val snoutM = f.colorBox("dogSnout", 0.14f, 0.12f, 0.16f, 0x6b4526ff.toInt())
        val earM = f.colorBox("dogEar", 0.07f, 0.14f, 0.05f, 0x5c3a1eff.toInt())
        val legM = f.colorBox("dogLeg", 0.09f, 0.3f, 0.09f, 0x7a4e2cff.toInt())
        val tailM = f.colorBox("dogTail", 0.06f, 0.06f, 0.26f, 0x7a4e2cff.toInt())

        body = rig.add(ModelInstance(bodyM), null, 0f, 0.44f, 0f)
        head = rig.add(ModelInstance(headM), body, 0f, 0.18f, 0.36f)
        rig.add(ModelInstance(snoutM), head, 0f, -0.03f, 0.19f)
        rig.add(ModelInstance(earM), head, -0.09f, 0.17f, 0f)
        rig.add(ModelInstance(earM), head, 0.09f, 0.17f, 0f)
        legFL = rig.add(ModelInstance(legM), body, -0.1f, -0.15f, 0.22f)
        legFR = rig.add(ModelInstance(legM), body, 0.1f, -0.15f, 0.22f)
        legBL = rig.add(ModelInstance(legM), body, -0.1f, -0.15f, -0.22f)
        legBR = rig.add(ModelInstance(legM), body, 0.1f, -0.15f, -0.22f)
        tail = rig.add(ModelInstance(tailM), body, 0f, 0.12f, -0.32f)
    }

    fun animate(root: Matrix4, phase: Float, time: Float) {
        val s = sin(phase); val c = sin(phase + 1.9f)
        a.x = s * 0.9f; legFL.pose(a)
        a.x = -s * 0.9f; legFR.pose(a)
        a.x = c * 0.95f; legBL.pose(a)
        a.x = -c * 0.95f; legBR.pose(a)
        a.x = 0.7f; a.y = sin(time * 11f) * 0.5f; tail.pose(a)
        a.y = 0f; a.x = sin(phase) * 0.08f
        // body bob + slight pitch
        tmpM.setToTranslation(0f, abs(sin(phase)) * 0.05f, 0f)
        q.setEulerAnglesRad(0f, sin(phase) * 0.07f, 0f)
        tmpM.rotate(q)
        body.local.set(tmpM)
        head.local.setToTranslation(0f, 0.18f, 0.36f)
        rig.update(root)
    }
}
