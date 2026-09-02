package com.dummysurfers.core.gfx3

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.sin

/**
 * Manual humanoid rig: every body part is a scaled unit cube positioned via
 * T(pivot) * R * T(offset) * S(size) each frame. Big-head Subway-Surfers
 * proportions, original palette-driven design.
 */
class BodyPart(
    val instance: ModelInstance,
    val pivot: Vector3,     // rotation pivot relative to rig root
    val offset: Vector3,    // box center offset from pivot (in pivot space)
    val size: Vector3
) {
    val tmpM = Matrix4()
    val tmpQ = Quaternion()

    fun set(rx: Float, ry: Float, rz: Float, worldX: Float, worldY: Float, worldZ: Float) {
        tmpM.setToTranslation(worldX + pivot.x, worldY + pivot.y, worldZ + pivot.z)
        tmpQ.setEulerAnglesRad(ry, rx, rz)
        tmpM.rotate(tmpQ)
        tmpM.translate(offset.x, offset.y, offset.z)
        tmpM.scale(size.x, size.y, size.z)
        instance.transform.set(tmpM)
    }
}

/** Palette for a humanoid — everything is derived from 8 colors. */
class RigPalette(
    val skin: Int, val hair: Int, val torso: Int, val sleeves: Int,
    val hands: Int, val legs: Int, val shoes: Int, val backpack: Int
)

/** Jack-inspired default runner palette (white hoodie, blue sleeves, denim, red kicks). */
object RigPalettes {
    val JACK = RigPalette(0xe8b08a, 0x5a3a24, 0xf2f2f0, 0x3f8ce0, 0xe8b08a, 0x35455a, 0xe23c3c, 0xf2a03c)
    val GUARD = RigPalette(0xd9a276, 0x3a3a3a, 0x2e3f66, 0x2e3f66, 0xd9a276, 0x232c44, 0x1d1d24, 0x2e3f66)
    val DASH = RigPalette(0xf2c49b, 0x5a3a24, 0x3f8ce0, 0xf2f2f0, 0xf2c49b, 0x35455a, 0xe23c3c, 0xf2a03c)
    val BLAZE = RigPalette(0xd9975f, 0x241a12, 0xc22f2f, 0xc22f2f, 0xd9975f, 0x2e2320, 0xf2b03c, 0x8a2f2f)
    val VOLT = RigPalette(0xc9a07a, 0x1e1e24, 0xf2c53c, 0x2b2b2f, 0xc9a07a, 0x2b2b2f, 0x3a3a42, 0x454545)
    val NOVA = RigPalette(0x8a6a52, 0x1a2420, 0x2dd4bf, 0x1f4d47, 0x8a6a52, 0xf2ead0, 0xf2ead0, 0x25a89a)

    fun byId(id: String): RigPalette = when (id) {
        "blaze" -> BLAZE; "volt" -> VOLT; "nova" -> NOVA; "guard" -> GUARD; else -> JACK
    }
}

/** Poses understood by the animator. */
object Pose {
    const val IDLE = 0; const val RUN = 1; const val JUMP = 2; const val ROLL = 3
    const val DEAD = 4; const val WAVE = 5; const val STUMBLE = 6; const val HANG = 7
}

class HumanoidRig(palette: RigPalette) {
    val parts = ArrayList<BodyPart>(16)

    // sizes (big-head SS proportions; total height ~1.75)
    private val c = { hex: Int -> Color(hex.shl(8) or 0xff) }

    // root space: y=0 at feet. Each part gets its OWN model (own material) —
    // per-part tinting is then guaranteed to render correctly.
    private fun add(hex: Int, px: Float, py: Float, pz: Float, ox: Float, oy: Float, oz: Float, sx: Float, sy: Float, sz: Float): BodyPart {
        val p = BodyPart(ModelInstance(Assets3D.boxModel(c(hex)))), Vector3(px, py, pz), Vector3(ox, oy, oz), Vector3(sx, sy, sz))
        parts.add(p)
        return p
    }

    // pivots
    private val hipY = 0.92f
    val torso: BodyPart = add(palette.torso, 0f, hipY, 0f, 0f, 0.28f, 0f, 0.5f, 0.62f, 0.3f)
    val hood: BodyPart = add(palette.sleeves, 0f, hipY + 0.5f, 0f, 0f, 0.08f, -0.14f, 0.42f, 0.22f, 0.2f)
    val head: BodyPart = add(palette.skin, 0f, hipY + 0.62f, 0f, 0f, 0.3f, 0f, 0.46f, 0.42f, 0.44f)
    val hair: BodyPart = add(palette.hair, 0f, hipY + 0.62f, 0f, 0f, 0.42f, -0.03f, 0.48f, 0.2f, 0.46f)
    val capBrim: BodyPart = add(palette.sleeves, 0f, hipY + 0.62f, 0f, 0f, 0.34f, 0.22f, 0.44f, 0.07f, 0.2f)
    val eyeL: BodyPart = add(0x22262e, 0f, hipY + 0.62f, 0f, -0.1f, 0.33f, 0.23f, 0.07f, 0.09f, 0.02f)
    val eyeR: BodyPart = add(0x22262e, 0f, hipY + 0.62f, 0f, 0.1f, 0.33f, 0.23f, 0.07f, 0.09f, 0.02f)
    val armL: BodyPart = add(palette.sleeves, -0.32f, hipY + 0.5f, 0f, 0f, -0.24f, 0f, 0.14f, 0.5f, 0.16f)
    val handL: BodyPart = add(palette.hands, -0.32f, hipY + 0.5f, 0f, 0f, -0.52f, 0f, 0.13f, 0.13f, 0.15f)
    val armR: BodyPart = add(palette.sleeves, 0.32f, hipY + 0.5f, 0f, 0f, -0.24f, 0f, 0.14f, 0.5f, 0.16f)
    val handR: BodyPart = add(palette.hands, 0.32f, hipY + 0.5f, 0f, 0f, -0.52f, 0f, 0.13f, 0.13f, 0.15f)
    val legL: BodyPart = add(palette.legs, -0.14f, hipY, 0f, 0f, -0.26f, 0f, 0.17f, 0.56f, 0.2f)
    val shoeL: BodyPart = add(palette.shoes, -0.14f, hipY, 0f, 0f, -0.56f, 0.05f, 0.18f, 0.14f, 0.32f)
    val legR: BodyPart = add(palette.legs, 0.14f, hipY, 0f, 0f, -0.26f, 0f, 0.17f, 0.56f, 0.2f)
    val shoeR: BodyPart = add(palette.shoes, 0.14f, hipY, 0f, 0f, -0.56f, 0.05f, 0.18f, 0.14f, 0.32f)
    val pack: BodyPart = add(palette.backpack, 0f, hipY + 0.18f, 0f, 0f, 0.02f, -0.22f, 0.36f, 0.44f, 0.16f)
    val packStrapL: BodyPart = add(0x35455a, 0f, hipY + 0.18f, 0f, -0.15f, 0.02f, -0.15f, 0.07f, 0.44f, 0.1f)
    val packStrapR: BodyPart = add(0x35455a, 0f, hipY + 0.18f, 0f, 0.15f, 0.02f, -0.15f, 0.07f, 0.44f, 0.1f)

    /** Animate the whole rig. (x,y,z) = feet world position. */
    fun apply(pose: Int, x: Float, y: Float, z: Float, phase: Float, lean: Float, time: Float) {
        when (pose) {
            Pose.RUN, Pose.IDLE -> runPose(x, y, z, if (pose == Pose.RUN) phase else time * 1.6f, lean, pose == Pose.IDLE)
            Pose.JUMP -> jumpPose(x, y, z, phase, lean)
            Pose.ROLL -> rollPose(x, y, z, phase)
            Pose.DEAD -> deadPose(x, y, z, phase)
            Pose.WAVE -> wavePose(x, y, z, time)
            Pose.STUMBLE -> stumblePose(x, y, z, phase, lean)
        }
    }

    private fun runPose(x: Float, y: Float, z: Float, phase: Float, lean: Float, idle: Boolean) {
        val s = if (idle) sin(phase) * 0.12f else sin(phase)
        val s2 = sin(phase + MathUtils.PI)
        val bounce = if (idle) 0f else abs(sin(phase)) * 0.06f
        val bob = y + bounce

        // torso slight forward pitch + lane lean roll
        torso.set(-0.12f - (if (idle) 0f else 0.1f), lean * 0.3f, lean * 0.35f, x, bob, z)
        head.set(0.08f, lean * 0.2f, -lean * 0.2f, x, bob, z)
        hair.set(0f, lean * 0.2f, -lean * 0.2f, x, bob, z)
        capBrim.set(0f, lean * 0.2f, -lean * 0.2f, x, bob, z)
        eyeL.set(0f, 0f, 0f, x, bob, z)
        eyeR.set(0f, 0f, 0f, x, bob, z)
        hood.set(0.1f, 0f, 0f, x, bob, z)
        pack.set(0f, 0f, 0f, x, bob, z)
        packStrapL.set(0f, 0f, 0f, x, bob, z)
        packStrapR.set(0f, 0f, 0f, x, bob, z)

        // arms opposite to legs, elbows always a bit bent
        armL.set(s2 * (if (idle) 0.5f else 1.1f), 0f, 0.12f, x, bob, z)
        handL.set(0f, 0f, 0f, x, bob, z)
        armR.set(s * (if (idle) 0.5f else 1.1f), 0f, -0.12f, x, bob, z)
        handR.set(0f, 0f, 0f, x, bob, z)

        // legs scissor, shoes follow
        legL.set(s * (if (idle) 0.25f else 0.95f), 0f, 0f, x, bob, z)
        legR.set(s2 * (if (idle) 0.25f else 0.95f), 0f, 0f, x, bob, z)
        val kneeL = MathUtils.clamp(-s, 0f, 1f) * (if (idle) 0f else 1.1f)
        val kneeR = MathUtils.clamp(-s2, 0f, 1f) * (if (idle) 0f else 1.1f)
        shoeL.set(kneeL, 0f, 0f, x, bob, z)
        shoeR.set(kneeR, 0f, 0f, x, bob, z)
    }

    private fun jumpPose(x: Float, y: Float, z: Float, phase: Float, lean: Float) {
        val tuck = MathUtils.clamp(phase, 0f, 1f)
        val armUp = -2.4f * tuck
        torso.set(0.05f, lean * 0.3f, lean * 0.3f, x, y, z)
        head.set(-0.1f, lean * 0.2f, -lean * 0.15f, x, y, z)
        hair.set(0f, 0f, 0f, x, y, z); capBrim.set(0f, 0f, 0f, x, y, z)
        eyeL.set(0f, 0f, 0f, x, y, z); eyeR.set(0f, 0f, 0f, x, y, z)
        hood.set(0f, 0f, 0f, x, y, z); pack.set(0f, 0f, 0f, x, y, z)
        packStrapL.set(0f, 0f, 0f, x, y, z); packStrapR.set(0f, 0f, 0f, x, y, z)
        armL.set(armUp, 0f, 0.35f, x, y, z); handL.set(0f, 0f, 0f, x, y, z)
        armR.set(armUp, 0f, -0.35f, x, y, z); handR.set(0f, 0f, 0f, x, y, z)
        legL.set(-1.5f * tuck, 0f, 0f, x, y, z); legR.set(-1.2f * tuck, 0f, 0f, x, y, z)
        shoeL.set(1.3f * tuck, 0f, 0f, x, y, z); shoeR.set(1.1f * tuck, 0f, 0f, x, y, z)
    }

    /** Somersault: whole rig spins around X at hip height. */
    private fun rollPose(x: Float, y: Float, z: Float, spin: Float) {
        val roll = spin
        val compressed = y * 0.45f
        torso.set(roll, 0f, 0f, x, y + 0.25f + compressed, z)
        head.set(roll, 0f, 0f, x, y + 0.25f + compressed, z)
        hair.set(roll, 0f, 0f, x, y + 0.25f + compressed, z)
        capBrim.set(roll, 0f, 0f, x, y + 0.25f + compressed, z)
        eyeL.set(roll, 0f, 0f, x, y + 0.25f + compressed, z)
        eyeR.set(roll, 0f, 0f, x, y + 0.25f + compressed, z)
        hood.set(roll, 0f, 0f, x, y + 0.25f + compressed, z)
        pack.set(roll, 0f, 0f, x, y + 0.25f + compressed, z)
        packStrapL.set(roll, 0f, 0f, x, y + 0.25f + compressed, z)
        packStrapR.set(roll, 0f, 0f, x, y + 0.25f + compressed, z)
        armL.set(roll - 2.2f, 0f, 0.4f, x, y + 0.25f + compressed, z); handL.set(0f, 0f, 0f, x, 0f, z)
        armR.set(roll - 2.2f, 0f, -0.4f, x, y + 0.25f + compressed, z); handR.set(0f, 0f, 0f, x, 0f, z)
        legL.set(roll + 1.6f, 0f, 0f, x, y + 0.25f + compressed, z)
        legR.set(roll + 1.4f, 0f, 0f, x, y + 0.25f + compressed, z)
        shoeL.set(0f, 0f, 0f, x, 0f, z); shoeR.set(0f, 0f, 0f, x, 0f, z)
    }

    /** Knocked flat: tumble backward. */
    private fun deadPose(x: Float, y: Float, z: Float, t: Float) {
        val fall = MathUtils.clamp(t * 2.2f, 0f, 1f)
        val ang = fall * 1.5f
        val drop = fall * (y - 0.1f)
        val yy = y - drop
        torso.set(-ang, 0f, 0f, x, yy, z)
        head.set(-ang * 1.1f, 0f, 0.2f * sin(t * 6f), x, yy, z)
        hair.set(0f, 0f, 0f, x, yy, z); capBrim.set(0f, 0f, 0f, x, yy, z)
        eyeL.set(0f, 0f, 0f, x, yy, z); eyeR.set(0f, 0f, 0f, x, yy, z)
        hood.set(0f, 0f, 0f, x, yy, z); pack.set(0f, 0f, 0f, x, yy, z)
        packStrapL.set(0f, 0f, 0f, x, yy, z); packStrapR.set(0f, 0f, 0f, x, yy, z)
        armL.set(-ang + 1.8f * fall, 0f, 1f * fall, x, yy, z); handL.set(0f, 0f, 0f, x, yy, z)
        armR.set(-ang + 1.6f * fall, 0f, -1f * fall, x, yy, z); handR.set(0f, 0f, 0f, x, yy, z)
        legL.set(ang * 0.7f, 0f, 0.3f, x, yy, z); legR.set(ang * 0.5f, 0f, -0.3f, x, yy, z)
        shoeL.set(0f, 0f, 0f, x, yy, z); shoeR.set(0f, 0f, 0f, x, yy, z)
    }

    /** Menu idle wave. */
    private fun wavePose(x: Float, y: Float, z: Float, time: Float) {
        val breathe = sin(time * 2f) * 0.02f
        val wave = sin(time * 7f) * 0.5f
        runPose(x, y, z, time * 1.2f, 0f, true)
        armR.set(-2.6f + wave, 0f, -0.5f, x, y + breathe, z)
        handR.set(wave * 0.8f, 0f, 0f, x, y + breathe, z)
    }

    private fun stumblePose(x: Float, y: Float, z: Float, phase: Float, lean: Float) {
        runPose(x, y, z, phase, lean, false)
        torso.set(0.45f, 0f, lean, x, y, z) // pitching forward hard
        armL.set(-2.8f, 0f, 0.6f, x, y, z)
        armR.set(-2.8f, 0f, -0.6f, x, y, z)
    }
}

/** The guard's K9 partner — 4 legs, wagging tail, pure attitude. */
class DogRig {
    val parts = ArrayList<BodyPart>(10)
    private val c = { hex: Int -> Color(hex.shl(8) or 0xff) }
    private fun add(hex: Int, px: Float, py: Float, pz: Float, ox: Float, oy: Float, oz: Float, sx: Float, sy: Float, sz: Float): BodyPart {
        val p = BodyPart(ModelInstance(Assets3D.boxModel(c(hex)))), Vector3(px, py, pz), Vector3(ox, oy, oz), Vector3(sx, sy, sz))
        parts.add(p)
        return p
    }

    private val hipY = 0.52f
    val body = add(0x8a5a34, 0f, hipY, 0f, 0f, 0.05f, 0f, 0.3f, 0.3f, 0.62f)
    val head = add(0x8a5a34, 0f, hipY, 0f, 0f, 0.3f, 0.32f, 0.26f, 0.24f, 0.26f)
    val earL = add(0x6a4426, 0f, hipY, 0f, -0.09f, 0.45f, 0.3f, 0.06f, 0.14f, 0.08f)
    val earR = add(0x6a4426, 0f, hipY, 0f, 0.09f, 0.45f, 0.3f, 0.06f, 0.14f, 0.08f)
    val snout = add(0x6a4426, 0f, hipY, 0f, 0f, 0.24f, 0.48f, 0.14f, 0.12f, 0.12f)
    val legFL = add(0x6a4426, -0.1f, hipY, 0.22f, 0f, -0.24f, 0f, 0.08f, 0.5f, 0.08f)
    val legFR = add(0x6a4426, 0.1f, hipY, 0.22f, 0f, -0.24f, 0f, 0.08f, 0.5f, 0.08f)
    val legBL = add(0x6a4426, -0.1f, hipY, -0.22f, 0f, -0.24f, 0f, 0.08f, 0.5f, 0.08f)
    val legBR = add(0x6a4426, 0.1f, hipY, -0.22f, 0f, -0.24f, 0f, 0.08f, 0.5f, 0.08f)
    val tail = add(0x6a4426, 0f, hipY, 0f, 0f, 0.18f, -0.38f, 0.06f, 0.06f, 0.3f)

    fun apply(x: Float, y: Float, z: Float, phase: Float, running: Boolean) {
        val bob = if (running) abs(sin(phase)) * 0.07f else 0f
        val yy = y + bob
        val s = if (running) sin(phase) else 0f
        val s2 = sin(phase + MathUtils.PI)
        body.set(0.1f, 0f, 0f, x, yy, z)
        head.set(-0.15f, 0f, 0f, x, yy, z)
        earL.set(0f, 0f, 0f, x, yy, z); earR.set(0f, 0f, 0f, x, yy, z)
        snout.set(0f, 0f, 0f, x, yy, z)
        legFL.set(s2 * 1.1f, 0f, 0f, x, yy, z); legFR.set(s * 1.1f, 0f, 0f, x, yy, z)
        legBL.set(s * 1.0f, 0f, 0f, x, yy, z); legBR.set(s2 * 1.0f, 0f, 0f, x, yy, z)
        tail.set(sin(phase * 1.7f) * 0.6f, 0f, 0f, x, yy, z)
    }
}
