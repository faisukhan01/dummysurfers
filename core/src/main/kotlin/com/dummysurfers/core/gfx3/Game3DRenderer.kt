package com.dummysurfers.core.gfx3

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.dummysurfers.core.config.GameConfig
import com.dummysurfers.core.entities.Chaser
import com.dummysurfers.core.entities.ObstacleKind
import com.dummysurfers.core.entities.Player
import com.dummysurfers.core.state.PlayerState
import com.dummysurfers.core.systems.Spawner
import kotlin.math.sin

/**
 * Renders gameplay entities in 3D: trains (cab face at the leading min-z end),
 * ramps, obstacles, coins, power-ups, plus the player / guard / dog rigs.
 */
class Game3DRenderer(
    private val scene: Scene3D,
    private val playerRig: HumanoidRig,
    private val guardRig: HumanoidRig,
    private val dogRig: DogRig
) {
    private val batch: ModelBatch get() = scene.batch
    private val env get() = scene.env

    private class Pool(var used: Int = 0) {
        val instances = ArrayList<ModelInstance>(10)
        fun next(): ModelInstance = instances[used++]
        fun reset() { used = 0 }
        fun canTake() = used < instances.size
    }

    private val carPools = Array(6) { Pool() }
    private val cabPool = Pool()
    private val barrierPool = Pool()
    private val gatePool = Pool()
    private val blockadePool = Pool()
    private val rampPool = Pool()
    private val coinPool = Pool()
    private val powerPool = Pool()

    fun create() {
        repeat(5) { l -> repeat(5) { carPools[l].instances.add(ModelInstance(Assets3D.trainCars[l])) } }
        repeat(6) { cabPool.instances.add(ModelInstance(Assets3D.trainFront)) }
        repeat(10) { barrierPool.instances.add(ModelInstance(Assets3D.hazardBarrier)) }
        repeat(10) { gatePool.instances.add(ModelInstance(Assets3D.gate)) }
        repeat(8) { blockadePool.instances.add(ModelInstance(Assets3D.blockade)) }
        repeat(6) { rampPool.instances.add(ModelInstance(Assets3D.ramp)) }
        repeat(90) { coinPool.instances.add(ModelInstance(Assets3D.coin)) }
        repeat(6) { powerPool.instances.add(ModelInstance(Assets3D.powerCube)) }
    }

    private fun placeSimple(pool: Pool, x: Float, y: Float, z: Float, rotY: Float = 0f) {
        if (!pool.canTake()) return
        val inst = pool.next()
        inst.transform.setToTranslation(x, y, z)
        if (rotY != 0f) inst.transform.rotate(0f, 1f, 0f, rotY)
        batch.render(inst, env)
    }

    // ── main render ─────────────────────────────────────────────────────
    fun render(
        spawner: Spawner,
        player: Player,
        chaser: Chaser,
        time: Float,
        supportY: Float,
        invulnBlink: Boolean,
        shieldOn: Boolean
    ) {
        carPools.forEach { it.reset() }; cabPool.reset(); barrierPool.reset()
        gatePool.reset(); blockadePool.reset(); rampPool.reset(); coinPool.reset(); powerPool.reset()

        // ── trains ──
        for (t in spawner.trains) {
            if (t.z < -30f || t.z > 100f) continue
            val laneX = t.lanes[0] * GameConfig.LANE_WIDTH
            val pool = carPools[t.livery % 6]
            for (car in 0 until t.cars) {
                val cz = t.z - (car + 0.5f) * GameConfig.TRAIN_CAR_LENGTH
                if (cz < -12f || cz > 100f) continue
                if (pool.canTake()) {
                    val inst = pool.next()
                    inst.transform.setToTranslation(laneX, 0f, cz)
                    batch.render(inst, env)
                }
            }
            // cab face at the leading (min-z) end — the face the player meets first
            placeSimple(cabPool, laneX, 0f, t.z - t.totalLength + GameConfig.TRAIN_CAR_LENGTH / 2f + 0.05f, 180f)
        }

        // ── ramps (rotated 180° so the LOW edge is at min-z, met first) ──
        for (r in spawner.ramps) {
            if (r.z < -8f || r.z > 100f) continue
            placeSimple(rampPool, r.lane * GameConfig.LANE_WIDTH, 0f, r.z, 180f)
        }

        // ── obstacles ──
        for (o in spawner.obstacles) {
            if (o.z < -6f || o.z > 100f) continue
            val lx = o.lane * GameConfig.LANE_WIDTH
            when (o.kind) {
                ObstacleKind.LOW_BARRIER -> placeSimple(barrierPool, lx, 0.45f, o.z)
                ObstacleKind.HIGH_BARRIER, ObstacleKind.GATE, ObstacleKind.FENCE_FULL -> placeSimple(gatePool, lx, 0f, o.z)
                ObstacleKind.BLOCKADE -> placeSimple(blockadePool, lx, 1.2f, o.z)
            }
        }

        // ── coins ──
        for (c in spawner.coins) {
            if (c.collected || c.z < -4f || c.z > 100f) continue
            if (!coinPool.canTake()) break
            val inst = coinPool.next()
            inst.transform.setToTranslation(c.x, c.y, c.z)
            inst.transform.rotate(0f, 1f, 0f, (time * 260f) % 360f)
            batch.render(inst, env)
        }

        // ── power-ups ──
        for (p in spawner.powerups) {
            if (p.taken || p.z < -4f || p.z > 100f) continue
            if (!powerPool.canTake()) break
            val inst = powerPool.next()
            val bob = sin(time * 3f + p.phase) * 0.12f
            inst.transform.setToTranslation(p.lane * GameConfig.LANE_WIDTH, 1.15f + bob, p.z)
            inst.transform.rotate(0f, 1f, 0f, (time * 120f) % 360f)
            val col = when (p.type) {
                0 -> Color(0xef4444ff.toInt())
                1 -> Color(0xf59e0bff.toInt())
                2 -> Color(0x2dd4bfff.toInt())
                3 -> Color(0xa3e635ff.toInt())
                else -> Color(0xf97316ff.toInt())
            }
            inst.materials.first().set(ColorAttribute.createDiffuse(col))
            batch.render(inst, env)
        }

        // ── player rig ──
        val blink = invulnBlink && (time * 12f).toInt() % 2 == 0
        if (!blink) {
            val pose = when (player.state) {
                PlayerState.JUMPING -> Pose.JUMP
                PlayerState.SLIDING -> Pose.ROLL
                PlayerState.DEAD -> Pose.DEAD
                else -> Pose.RUN
            }
            val phaseArg = when (pose) {
                Pose.JUMP -> ((player.vy + 8f) / 16f).coerceIn(0f, 1f)
                Pose.ROLL -> player.stateTime / GameConfig.SLIDE_DURATION * 6.2832f
                Pose.DEAD -> player.deadTimer
                else -> player.runPhase
            }
            playerRig.apply(pose, player.x, player.jumpY + player.groundY, 0f, phaseArg, player.lean, time)
            for (p in playerRig.parts) batch.render(p.instance, env)
        }

        // ── guard + dog (chase pressure made visible) ──
        if (chaser.active) {
            val gz = GameConfig.CHASER_Z - 0.8f + sin(time * 2f) * 0.2f
            val gx = player.x * 0.9f
            guardRig.apply(Pose.RUN, gx, 0f, gz, chaser.runPhase, 0f, time)
            for (p in guardRig.parts) batch.render(p.instance, env)
            dogRig.apply(gx + 0.95f, 0f, gz - 0.5f, chaser.runPhase * 1.3f, true)
            for (p in dogRig.parts) batch.render(p.instance, env)
        }

        // ── game-over catch sequence: guard grabs the player ──
        if (player.state == PlayerState.DEAD) {
            val gz = -1.4f
            guardRig.apply(Pose.RUN, player.x * 0.6f, 0f, gz, time * 9f, 0f, time)
            for (p in guardRig.parts) batch.render(p.instance, env)
            dogRig.apply(player.x * 0.6f + 0.9f, 0f, gz - 0.4f, time * 11f, true)
            for (p in dogRig.parts) batch.render(p.instance, env)
        }
    }

    /** Menu attract mode: hero waving on the tracks, guard + dog lurking behind. */
    fun renderMenu(time: Float) {
        playerRig.apply(Pose.WAVE, 0f, 0f, 0f, 0f, 0f, time)
        for (p in playerRig.parts) batch.render(p.instance, env)
        val gz = -4.6f + sin(time * 0.7f) * 0.5f
        guardRig.apply(Pose.IDLE, -1.35f, 0f, gz, time, 0f, time)
        for (p in guardRig.parts) batch.render(p.instance, env)
        dogRig.apply(1.25f, 0f, gz - 0.35f, time * 2f, false)
        for (p in dogRig.parts) batch.render(p.instance, env)
    }
}
