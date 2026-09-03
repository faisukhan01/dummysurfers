package com.dummysurfers.core.entities

import com.dummysurfers.core.config.GameConfig
import com.dummysurfers.core.state.PlayerState
import com.dummysurfers.core.utils.Mathz

/** Original characters — SS-styled (big head, hoodie, backpack, backward cap), 100% ours. */
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
    val accent: Int,
    val hair: Int = 0x3a2a1eff.toInt(),
    /** v3.0: denim/side vest overlay color (0 = no vest) — Jack's signature layer. */
    val vest: Int = 0,
    /** v3.0: hood-bunch/undershirt color (0 = darker hoodie) — Jack's red tee. */
    val hoodLining: Int = 0,
    /** v3.0: front-panel color on a backwards cap (0 = none) — Jake DNA. */
    val capPanel: Int = 0,
    /** v4.2: signature accessory — 0 none, 1 spray can, 2 cap goggles, 3 headphones. */
    val accessory: Int = 0
) {
    companion object {
        val ALL = arrayOf(
            // JACK — the face of the game (Jake-inspired, 100% original art):
            // red hoodie + white tee underneath, denim vest, dark navy jeans,
            // red sneakers, red backwards cap (clean dome — a FRONT panel only
            // exists on forward caps; the white egg it drew read as a balloon
            // in the menu portrait), brown spiky hair, BLUE pack — v5.1:
            // palette re-matched 1:1 to the launcher-icon character (dark
            // jeans + red shoes + blue pack) so the game hero IS the thumbnail
            CharacterDef("dash", "JACK", 0, 0xf2c49bff.toInt(), 0xd8322aff.toInt(), 0x2c3e63ff.toInt(), 0xd84a30ff.toInt(), 0xd8322aff.toInt(), 0x2f6fd0ff.toInt(), 0xffd24aff.toInt(), 0x5a3a22ff.toInt(), 0x7d97b8ff.toInt(), 0xf2f2f0ff.toInt(), 0),
            // BLAZE — street artist: burnt-orange hoodie, black cap, spray-can energy
            CharacterDef("blaze", "BLAZE", 500, 0xd9975fff.toInt(), 0xf28c1aff.toInt(), 0x2e2320ff.toInt(), 0xf2b03cff.toInt(), 0x1e1e24ff.toInt(), 0xc22f2fff.toInt(), 0xff9c3cff.toInt(), 0x1e1611ff.toInt(), accessory = 1),
            // VOLT — speed demon: electric-yellow hoodie, teal hair streak, cap goggles
            CharacterDef("volt", "VOLT", 1000, 0xc9a07aff.toInt(), 0xf2c53cff.toInt(), 0x2b2b2fff.toInt(), 0x3a3a42ff.toInt(), 0x1e1e24ff.toInt(), 0x454545ff.toInt(), 0xfff060ff.toInt(), 0x2ec4d9ff.toInt(), accessory = 2),
            // NOVA — cool runner: mint hoodie, lavender hair, white cap, headphones
            CharacterDef("nova", "NOVA", 2000, 0x8a6a52ff.toInt(), 0x2dd4bfff.toInt(), 0x1f4d47ff.toInt(), 0xf2ead0ff.toInt(), 0xf2ead0ff.toInt(), 0x25a89aff.toInt(), 0x7df2e2ff.toInt(), 0xb48ce0ff.toInt(), accessory = 3)
        )

        fun byId(id: String): CharacterDef = ALL.firstOrNull { it.id == id } ?: ALL[0]
    }
}

/**
 * The runner. Owns lane physics, jump arc, slide timing, lean, squash-stretch
 * and the run-cycle phase that drives procedural animation (and footsteps).
 */
class Player {
    var lane = 0                        // -1, 0, +1
    var x = 0f                          // continuous world x
    var jumpY = 0f                      // height above ground
    var supportY = 0f                   // v4: ground height under the player (train roofs/ramps)
    var jumpBase = 0f                   // v4: height the current jump started from
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
    var curJumpStrength = GameConfig.JUMP_STRENGTH
    var curJumpDuration = GameConfig.JUMP_DURATION
    var stepParity = 0

    val height: Float
        get() = if (state == PlayerState.SLIDING) GameConfig.PLAYER_HEIGHT * GameConfig.SLIDE_HEIGHT_RATIO else GameConfig.PLAYER_HEIGHT

    fun reset() {
        lane = 0; x = 0f; jumpY = 0f; supportY = 0f; jumpBase = 0f
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

    /** Jump arc: y(t) = base + strength * sin(PI * t / duration). Params captured at launch.
     * @return true when a fresh jump actually started (not buffered). */
    fun startJump(superJump: Boolean): Boolean {
        if (state == PlayerState.JUMPING) { jumpBuffered = true; bufferTimer = 0.1f; return false }
        if (state == PlayerState.SLIDING) state = PlayerState.RUNNING
        state = PlayerState.JUMPING
        stateTime = 0f
        fastFalling = false
        jumpBase = jumpY
        curJumpStrength = GameConfig.JUMP_STRENGTH * if (superJump) GameConfig.SUPERJUMP_STRENGTH_MULT else 1f
        curJumpDuration = GameConfig.JUMP_DURATION * if (superJump) GameConfig.SUPERJUMP_DURATION_MULT else 1f
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
            jumpY = Mathz.approach(jumpY, supportY, dt * 14f)
            return false
        }

        // v4 roof-running: if the support under us dropped (ran off a train end
        // or swiped into an empty lane) the runner FALLS back down; if support
        // rose (ran up a ramp) the runner is carried up.
        if (state == PlayerState.RUNNING || state == PlayerState.LANE_SWITCH) {
            if (jumpY > supportY + 0.02f) {
                // airborne fall
                jumpY -= GameConfig.ROOF_FALL_GRAVITY * dt
                if (jumpY <= supportY) {
                    jumpY = supportY
                    land()
                    landed = true
                }
            } else if (jumpY < supportY - 0.02f) {
                // carried up by a ramp
                jumpY = Mathz.approach(jumpY, supportY, dt * 12f)
            } else {
                jumpY = supportY
            }
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
                val t = stateTime / curJumpDuration
                if (fastFalling) {
                    jumpY -= GameConfig.FAST_FALL_GRAVITY * dt
                    if (jumpY <= supportY) { jumpY = supportY; land(); landed = true }
                } else if (t >= 1f) {
                    jumpY = supportY; land(); landed = true
                } else {
                    jumpY = jumpBase + curJumpStrength * Mathz.sinPi(t)
                    // landed early on a rising support (ramp top / train roof)
                    if (jumpY < supportY) { jumpY = supportY; land(); landed = true }
                }
            }
            PlayerState.SLIDING -> {
                slideTimer -= dt
                if (slideTimer <= 0f) { state = PlayerState.RUNNING; stateTime = 0f }
            }
            PlayerState.LANDING -> {
                squash = Mathz.clamp01(1f - stateTime / 0.12f)
                if (stateTime >= 0.12f) { state = PlayerState.RUNNING; stateTime = 0f; squash = 0f }
            }
            else -> {}
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

    val totalLength: Float get() = cars * GameConfig.TRAIN_CAR_LENGTH

    fun reset(lanes: IntArray, z: Float, cars: Int, kind: Int, speed: Float, livery: Int) {
        this.lanes = lanes; this.z = z; this.cars = cars; this.kind = kind
        this.speed = speed; this.livery = livery; this.hornDone = kind != 2
        this.passedNear = false
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

    /** v3.0: true while the guard has sprinted into grab range (after a stumble). */
    var close = false

    /** v3.0: 0..1 rush-in animation for the guard-grab death sequence. */
    var catchT = 0f

    /** v3.0: grab pose blend 0..1 once the guard reaches the player. */
    var grabbed = false

    /** v3.0: the guard's dog — sprinting beside him, own run cycle. */
    var dogPhase = 0f

    fun trigger(duration: Float) {
        active = true
        timer = duration
    }

    /** Stumble pressure: guard sprints up close for [duration] seconds. */
    fun triggerClose(duration: Float) {
        trigger(duration)
        close = true
    }

    fun beginCatch() {
        active = true
        close = true
        grabbed = false
        catchT = 0f
    }

    fun reset() {
        active = false; timer = 0f; close = false; grabbed = false; catchT = 0f
    }

    fun update(dt: Float, speed: Float) {
        if (!active) return
        if (catchT > 0f) {
            // death sequence: rush toward the player then hold the grab
            if (catchT < 1f) catchT = (catchT + dt * 1.6f).coerceAtMost(1f)
            else grabbed = true
            runPhase += dt * 16f
            dogPhase += dt * 18f
            return
        }
        timer -= dt
        if (timer <= 0f) { active = false; close = false }
        runPhase += dt * speed * 1.4f
        dogPhase += dt * speed * 1.7f
        lean = kotlin.math.sin(runPhase * 0.5f) * 0.08f
    }
}
