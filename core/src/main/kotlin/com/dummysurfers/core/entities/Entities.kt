package com.dummysurfers.core.entities

import com.dummysurfers.core.config.GameConfig
import com.dummysurfers.core.state.PlayerState
import com.dummysurfers.core.utils.Mathz

/** Original character definitions — procedurally drawn, unique palettes. */
class CharacterDef(
    val id: String,
    val name: String,
    val cost: Int,
    val skin: Int,
    val hoodie: Int,
    val pants: Int,
    val shoes: Int,
    val cap: Int,
    val backpack: Int,
    val accent: Int
) {
    companion object {
        val ALL = arrayOf(
            CharacterDef("dash", "DASH", 0, 0xf2c49bff.toInt(), 0x3f8ce0ff.toInt(), 0x35455aff.toInt(), 0xe23c3cff.toInt(), 0xe23c3cff.toInt(), 0xf2a03cff.toInt(), 0xffd24aff.toInt()),
            CharacterDef("blaze", "BLAZE", 500, 0xd9975fff.toInt(), 0xc22f2fff.toInt(), 0x2e2320ff.toInt(), 0xf2b03cff.toInt(), 0xf28c3cff.toInt(), 0x8a2f2fff.toInt(), 0xff9c3cff.toInt()),
            CharacterDef("volt", "VOLT", 1000, 0xc9a07aff.toInt(), 0xf2c53cff.toInt(), 0x2b2b2fff.toInt(), 0x3a3a42ff.toInt(), 0x1e1e24ff.toInt(), 0x454545ff.toInt(), 0xfff060ff.toInt()),
            CharacterDef("nova", "NOVA", 2000, 0x8a6a52ff.toInt(), 0x2dd4bfff.toInt(), 0x1f4d47ff.toInt(), 0xf2ead0ff.toInt(), 0xf2ead0ff.toInt(), 0x25a89aff.toInt(), 0x7df2e2ff.toInt())
        )

        fun byId(id: String): CharacterDef = ALL.firstOrNull { it.id == id } ?: ALL[0]
    }
}

/** Jump arc via velocity physics — supports platforms (train roofs). */
class Player {
    var lane = 0                        // -1, 0, +1
    var x = 0f                          // continuous world x
    var jumpY = 0f                      // height ABOVE groundY
    var groundY = 0f                    // platform height under the player (roof = TRAIN_HEIGHT)
    var vy = 0f                         // vertical velocity
    var airborne = false
    var state = PlayerState.RUNNING
    var stateTime = 0f
    var runPhase = 0f                   // drives leg/arm animation
    var lean = 0f                       // -1..1 visual tilt
    var squash = 0f                     // landing squash amount 0..1
    var slideTimer = 0f
    var jumpBuffered = false
    var bufferTimer = 0f
    var invulnTimer = 0f
    var deadTimer = 0f
    var deathSpin = 0f
    var fromLane = 0
    var toLane = 0
    var switchT = 0f
    var fastFalling = false
    var curJumpStrength = GameConfig.JUMP_VELOCITY
    var curJumpDuration = GameConfig.JUMP_DURATION
    var stepParity = 0

    val height: Float
        get() = if (state == PlayerState.SLIDING) GameConfig.PLAYER_HEIGHT * GameConfig.SLIDE_HEIGHT_RATIO else GameConfig.PLAYER_HEIGHT

    fun reset() {
        lane = 0; x = 0f; jumpY = 0f; groundY = 0f; vy = 0f; airborne = false
        state = PlayerState.RUNNING; stateTime = 0f
        runPhase = 0f; lean = 0f; squash = 0f; slideTimer = 0f; jumpBuffered = false
        bufferTimer = 0f; invulnTimer = 0f; deadTimer = 0f; deathSpin = 0f
        fastFalling = false
    }

    fun switchLane(dir: Int) {
        val target = (lane + dir).coerceIn(-1, 1)
        if (target == lane) return
        fromLane = lane
        toLane = target
        lane = target
        switchT = 0f
        state = if (state != PlayerState.JUMPING && state != PlayerState.SLIDING) PlayerState.LANE_SWITCH else state
        stateTime = 0f
    }

    /** Velocity-based jump. @return true when a fresh jump started. */
    fun startJump(superJump: Boolean): Boolean {
        if (state == PlayerState.JUMPING) { jumpBuffered = true; bufferTimer = 0.1f; return false }
        if (state == PlayerState.SLIDING) state = PlayerState.RUNNING
        state = PlayerState.JUMPING
        stateTime = 0f
        fastFalling = false
        vy = GameConfig.JUMP_VELOCITY * if (superJump) 1.45f else 1f
        airborne = true
        return true
    }

    fun startSlide() {
        if (state == PlayerState.JUMPING) { fastFalling = true; return }
        if (state == PlayerState.SLIDING) { slideTimer = GameConfig.SLIDE_DURATION; stateTime = 0f; return }
        state = PlayerState.SLIDING
        slideTimer = GameConfig.SLIDE_DURATION
        stateTime = 0f
    }

    fun update(dt: Float, speed: Float): Boolean {
        var landed = false
        stateTime += dt

        if (state == PlayerState.DEAD) {
            deadTimer += dt
            deathSpin += dt * 9f
            jumpY = Mathz.approach(jumpY, 0f, dt * 14f)
            return false
        }

        // lane interpolation with ease-out (0.15s)
        if (switchT < GameConfig.LANE_SWITCH_DURATION) {
            switchT += dt
            val t = Mathz.easeOut(Mathz.clamp01(switchT / GameConfig.LANE_SWITCH_DURATION))
            x = Mathz.lerp(fromLane.toFloat(), toLane.toFloat(), t) * GameConfig.LANE_WIDTH
            lean = Mathz.sign((toLane - fromLane).toFloat()) * (1f - t) * 0.6f
            if (switchT >= GameConfig.LANE_SWITCH_DURATION) {
                x = toLane * GameConfig.LANE_WIDTH
                lean = 0f
                if (state == PlayerState.LANE_SWITCH) state = PlayerState.RUNNING
            }
        }

        when (state) {
            PlayerState.JUMPING -> {
                // velocity + gravity
                vy -= GameConfig.GRAVITY * dt * (if (fastFalling) 2.4f else 1f)
                jumpY += vy * dt
                if (jumpY <= groundY && vy < 0f) {
                    jumpY = groundY; vy = 0f; airborne = false
                    land(); landed = true
                }
            }
            PlayerState.SLIDING -> {
                slideTimer -= dt
                if (slideTimer <= 0f) { state = PlayerState.RUNNING; stateTime = 0f }
                // sliding off a roof edge still falls
                if (jumpY > groundY + 0.01f) {
                    vy -= GameConfig.GRAVITY * dt
                    jumpY += vy * dt
                    if (jumpY <= groundY) { jumpY = groundY; vy = 0f }
                }
            }
            PlayerState.LANDING -> {
                squash = Mathz.clamp01(1f - stateTime / 0.12f)
                if (stateTime >= 0.12f) { state = PlayerState.RUNNING; stateTime = 0f; squash = 0f }
                // ground may have dropped (roof edge) while landing
                if (jumpY > groundY + 0.01f) state = PlayerState.JUMPING
            }
            else -> {}
        }

        // support dropped away (ran off a roof): fall with gravity in any state
        if (state != PlayerState.JUMPING && state != PlayerState.DEAD && jumpY > groundY + 0.01f) {
            vy -= GameConfig.GRAVITY * dt
            jumpY += vy * dt
            if (jumpY <= groundY) { jumpY = groundY; vy = 0f; land(); landed = true }
        }

        if (jumpBuffered) {
            bufferTimer -= dt
            if (bufferTimer <= 0f) jumpBuffered = false
            else if (landed) { jumpBuffered = false; startJump(false) }
        }

        if (invulnTimer > 0f) invulnTimer -= dt

        // run cycle syncs with ground speed
        if (state == PlayerState.RUNNING || state == PlayerState.LANE_SWITCH) {
            val prev = runPhase % (2f * Math.PI.toFloat())
            runPhase += dt * speed * 1.55f
            val now = runPhase % (2f * Math.PI.toFloat())
            if (prev > now) stepParity = 1 - stepParity
        }
        return landed
    }

    private fun land() {
        state = PlayerState.LANDING
        stateTime = 0f
        squash = 1f
        fastFalling = false
    }
}

/** Wedge ramp that lifts the runner onto a train roof. */
class Ramp {
    var lane = 0
    var z = 0f                // front (low) edge

    fun reset(lane: Int, z: Float) {
        this.lane = lane
        this.z = z
    }
}

/** A train consist: N carriages spanning [z, z - cars*carLen]. */
class Train {
    var lanes = intArrayOf(0)
    var z = 0f                 // front (closest) end
    var cars = 4
    var kind = 0               // 0 parked, 1 sameDir, 2 approach
    var speed = 0f
    var livery = 0
    var hornDone = false
    var passedNear = false
    var seed = 0
    var hasRoof = false        // true when a ramp leads onto this train's roof

    val totalLength: Float get() = cars * GameConfig.TRAIN_CAR_LENGTH

    fun reset(lanes: IntArray, z: Float, cars: Int, kind: Int, speed: Float, livery: Int) {
        this.lanes = lanes; this.z = z; this.cars = cars; this.kind = kind
        this.speed = speed; this.livery = livery; this.hornDone = kind != 2
        this.passedNear = false
        this.hasRoof = false
        this.seed = (Math.random() * 10000).toInt()
    }

    fun occupies(lane: Int): Boolean = lanes.contains(lane)
}

enum class ObstacleKind { LOW_BARRIER, HIGH_BARRIER, BLOCKADE, GATE, FENCE_FULL }

/** Jumpable (low), slideable (high/gate), or blocking (blockade) hazards. */
class Obstacle {
    var kind = ObstacleKind.LOW_BARRIER
    var lane = 0               // for lane-specific kinds
    var z = 0f
    var passed = false         // near-miss bookkeeping
    var variant = 0

    fun reset(kind: ObstacleKind, lane: Int, z: Float, variant: Int) {
        this.kind = kind; this.lane = lane; this.z = z; this.passed = false; this.variant = variant
    }

    fun affects(lane: Int): Boolean = when (kind) {
        ObstacleKind.GATE, ObstacleKind.FENCE_FULL -> true
        else -> this.lane == lane
    }
}

/** Collectible coin with magnet-flight support. */
class Coin {
    var lane = 0
    var x = 0f
    var y = 0f
    var z = 0f
    var baseY = 0f
    var phase = 0f
    var collected = false
    var magnet = false

    fun reset(lane: Int, z: Float, y: Float) {
        this.lane = lane
        this.x = lane * GameConfig.LANE_WIDTH
        this.z = z
        this.baseY = y
        this.y = y
        this.phase = (Math.random() * 6.28).toFloat()
        this.collected = false
        this.magnet = false
    }
}

/** Floating power-up pickup. */
class PowerUpPickup {
    var type = 0
    var lane = 0
    var z = 0f
    var phase = 0f
    var taken = false

    fun reset(type: Int, lane: Int, z: Float) {
        this.type = type; this.lane = lane; this.z = z
        this.phase = (Math.random() * 6.28).toFloat(); this.taken = false
    }
}

/** The security guard chasing the player — pressure made visible. */
class Chaser {
    var active = false
    var timer = 0f
    var runPhase = 0f
    var lean = 0f

    fun trigger(duration: Float) {
        active = true
        timer = duration
    }

    fun update(dt: Float, speed: Float) {
        if (!active) return
        timer -= dt
        if (timer <= 0f) active = false
        runPhase += dt * speed * 1.4f
        lean = kotlin.math.sin(runPhase * 0.5f) * 0.08f
    }
}
