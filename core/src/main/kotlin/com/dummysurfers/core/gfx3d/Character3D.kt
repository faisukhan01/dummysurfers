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

    /** Compose all local transforms down the tree starting from [root]. */
    fun update(root: Matrix4) {
        for (p in parts) {
            val pw = p.parent?.world ?: root
            p.local.setToTranslation(p.bx, p.by, p.bz)
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
        val shoeM = f.colorBox("shoe$shoes$accent", 0.19f, 0.13f, 0.36f, shoes)
        val upperM = f.colorBox("upper$hoodie", 0.17f, 0.27f, 0.18f, hoodie)
        val foreM = f.colorBox("fore$skin", 0.15f, 0.2f, 0.16f, skin)
        val handM = f.colorBox("hand$skin", 0.14f, 0.13f, 0.15f, skin)

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
        foreL = rig.add(ModelInstance(foreM), armL, 0f, -0.27f, 0f)
        foreR = rig.add(ModelInstance(foreM), armR, 0f, -0.27f, 0f)
        rig.add(ModelInstance(handM), foreL, 0f, -0.2f, 0.02f)
        rig.add(ModelInstance(handM), foreR, 0f, -0.2f, 0.02f)

        val headM = buildHead(skin, hair, cap, capPanel, accent)
        head = rig.add(ModelInstance(headM), torso, 0f, 0.6f, 0f)
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
        // chest tee stripe (red under-layer peeking out)
        val lining = if (hoodLining != 0) hoodLining else mul(hoodie, 0.8f)
        mpb = mb.part("tee", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(lining))
        mpb.setUVRange(0f, 0f, 1f, 1f)
        mpb.cbox(0f, 0.4f, 0.18f, 0.3f, 0.3f, 0.02f)
        // backpack + flap + straps
        if (!isGuard) {
            mpb = mb.part("pack", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(backpack))
            mpb.setUVRange(0f, 0f, 1f, 1f)
            mpb.cbox(0f, 0.3f, -0.24f, 0.42f, 0.46f, 0.16f)
            mpb = mb.part("packFlap", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(mul(backpack, 0.8f)))
            mpb.setUVRange(0f, 0f, 1f, 1f)
            mpb.cbox(0f, 0.44f, -0.33f, 0.42f, 0.14f, 0.05f)
            mpb = mb.part("straps", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(mul(backpack, 0.7f)))
            mpb.cbox(-0.16f, 0.42f, 0.18f, 0.07f, 0.34f, 0.02f)
            mpb.cbox(0.16f, 0.42f, 0.18f, 0.07f, 0.34f, 0.02f)
        } else {
            // guard: gold badge + belt + shoulder epaulettes
            mpb = mb.part("badge", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(0xffd23eff.toInt()))
            mpb.cbox(0.14f, 0.42f, 0.18f, 0.09f, 0.09f, 0.02f)
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
        mpb.cbox(0f, 0.24f, 0f, 0.5f, 0.46f, 0.44f)
        // spiky fringe under the cap edge
        mpb = mb.part("hair", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(hair))
        mpb.cbox(0f, 0.47f, 0.03f, 0.52f, 0.1f, 0.46f)
        mpb.cbox(0f, 0.4f, 0.23f, 0.4f, 0.1f, 0.04f)
        // cap dome + brim (backwards = brim behind) + front panel
        mpb = mb.part("cap", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(cap))
        mpb.cbox(0f, 0.56f, -0.01f, 0.54f, 0.14f, 0.48f)
        mpb.cbox(0f, 0.5f, -0.27f, 0.5f, 0.06f, 0.12f) // rear brim
        mpb.cbox(0f, 0.63f, -0.01f, 0.2f, 0.05f, 0.16f) // top button-ish ridge
        if (capPanel != 0) {
            mpb = mb.part("panel", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(capPanel))
            mpb.cbox(0f, 0.56f, 0.24f, 0.34f, 0.12f, 0.03f)
        }
        if (isGuard) {
            // gold badge on the cap + mustache
            mpb = mb.part("gbadge", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(0xffd23eff.toInt()))
            mpb.cbox(0f, 0.57f, 0.24f, 0.12f, 0.1f, 0.03f)
            mpb = mb.part("stache", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(0x4a3524ff.toInt()))
            mpb.cbox(0f, 0.16f, 0.235f, 0.26f, 0.06f, 0.03f)
        }
        // ears
        mpb = mb.part("ears", com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, ModelFactory.ATTRS, f.matColor(mul(skin, 0.92f)))
        mpb.cbox(-0.27f, 0.24f, 0f, 0.07f, 0.12f, 0.1f)
        mpb.cbox(0.27f, 0.24f, 0f, 0.07f, 0.12f, 0.1f)
        return mb.end()
    }

    private fun mul(hex: Int, fac: Float): Int {
        val c = com.badlogic.gdx.graphics.Color(hex)
        return com.badlogic.gdx.graphics.Color(c.r * fac, c.g * fac, c.b * fac, 1f).toIntBits()
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
                aArmL.x = -s * 0.85f; aArmR.x = s * 0.85f
                aArmL.z = 0.12f; aArmR.z = -0.12f
                aForeL.x = -0.7f; aForeR.x = -0.7f
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
                    // crash tumble
                    rootPitch = -stateTime * 7f
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
        rig.update(IDENTITY)
    }

    companion object {
        private val IDENTITY = Matrix4()
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
