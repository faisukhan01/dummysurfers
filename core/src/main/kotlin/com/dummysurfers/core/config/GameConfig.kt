package com.dummysurfers.core.config

/**
 * DUMMY SURFERS BY FSK — Central Game Config.
 * Every tunable parameter lives here. No magic numbers in gameplay code.
 */
object GameConfig {
    // ── Player movement ────────────────────────────────────────────────
    const val BASE_SPEED = 8f              // meters/second at run start
    const val MAX_SPEED = 22f
    const val ACCEL_FACTOR = 0.00028f      // speed curve exponent
    const val LANE_WIDTH = 2.5f
    const val LANE_SWITCH_DURATION = 0.15f // seconds, ease-out
    const val JUMP_STRENGTH = 4.0f         // apex height (world units)
    const val JUMP_DURATION = 0.6f
    const val SUPERJUMP_STRENGTH_MULT = 2.1f
    const val SUPERJUMP_DURATION_MULT = 1.3f
    const val SLIDE_DURATION = 0.5f
    const val SLIDE_HEIGHT_RATIO = 0.4f
    const val PLAYER_HEIGHT = 1.75f
    const val PLAYER_HALF_WIDTH = 0.42f
    const val FAST_FALL_GRAVITY = 34f

    // ── Camera / projection ────────────────────────────────────────────
    const val VANISHING_POINT_Y = 0.38f    // fraction of screen height
    const val PLAYER_BASE_Y = 0.78f        // ground line at z=0 (fraction)
    const val FOCAL_LENGTH = 9f            // scale(z) = f / (f + z)
    const val VIEW_DISTANCE = 68f
    const val CAMERA_FOLLOW = 0.42f
    const val CAMERA_LERP = 0.1f
    const val NEAR_MISS_SHAKE = 5f
    const val CRASH_SHAKE = 16f

    // ── Virtual resolution ─────────────────────────────────────────────
    const val VIRTUAL_WIDTH = 720f
    const val VIRTUAL_HEIGHT = 1280f

    // ── Spawning / world ───────────────────────────────────────────────
    const val SEGMENT_LENGTH = 25f
    const val FIRST_SAFE_METERS = 45f
    const val SLEEPER_SPACING = 1.7f
    const val POWERUP_INTERVAL = 26f
    const val MIN_REACTION_GAP = 0.62f
    const val MIN_PATTERN_GAP = 9f

    // ── Difficulty phases ──────────────────────────────────────────────
    // until / speedPct / gapMin / gapMax
    val PHASES = arrayOf(
        floatArrayOf(200f, 0.55f, 34f, 52f),
        floatArrayOf(800f, 0.66f, 24f, 40f),
        floatArrayOf(2000f, 0.78f, 15f, 26f),
        floatArrayOf(4000f, 0.88f, 11f, 19f),
        floatArrayOf(Float.MAX_VALUE, 0.97f, 9f, 15f)
    )

    // ── Scoring ────────────────────────────────────────────────────────
    const val COIN_VALUE = 10
    const val POWERUP_SCORE = 50
    const val NEAR_MISS_DISTANCE = 1.05f
    const val NEAR_MISS_SCORE = 25
    const val DISTANCE_SCORE_PER_METER = 1
    val MULTIPLIER_MILESTONES = arrayOf(floatArrayOf(1000f, 2f), floatArrayOf(2500f, 4f))

    // ── Power-ups ──────────────────────────────────────────────────────
    const val MAGNET_RANGE_Z = 24f
    const val SHIELD_INVULN = 1.5f
    const val BOOST_SPEED_MULT = 1.32f
    val POWERUP_DURATIONS = floatArrayOf(18f, 22f, 12f, 10f, 15f) // magnet,x2,shield,boost,superjump
    val POWERUP_LABELS = arrayOf("MAGNET", "SCORE x2", "SHIELD", "BOOST", "SUPER JUMP")

    // ── Hoverboard (2nd chance) ────────────────────────────────────────
    const val HOVERBOARD_DURATION = 15f     // seconds of crash immunity while riding
    const val HOVERBOARD_SAVE_INVULN = 1.6f // invulnerability after the board shatters
    const val HOVERBOARD_COST = 300         // coins per board in the shop
    const val HOVERBOARD_MAX = 9            // rack capacity

    // ── Trains ─────────────────────────────────────────────────────────
    const val TRAIN_CAR_LENGTH = 6.4f
    const val TRAIN_WIDTH = 2.05f
    const val TRAIN_HEIGHT = 2.35f
    const val MOVING_TRAIN_REL_SPEED = 4.2f
    const val APPROACH_TRAIN_SPEED = 9f
    const val TRAIN_HORN_DISTANCE = 46f

    // ── Chaser ─────────────────────────────────────────────────────────
    const val CHASER_START_TIME = 4.5f
    const val CHASER_NEARMISS_TIME = 2.8f
    const val CHASER_Z = -3.4f

    // ── Economy ────────────────────────────────────────────────────────
    val UPGRADE_COSTS = arrayOf(intArrayOf(300, 700, 1500), intArrayOf(300, 700, 1500), intArrayOf(250, 600, 1300), intArrayOf(250, 600, 1300), intArrayOf(350, 800, 1600))
    val TRAIL_COSTS = intArrayOf(0, 200, 600, 1200) // none,gold,fire,rainbow
    val CHARACTER_COSTS = intArrayOf(0, 500, 1000, 2000)
}
