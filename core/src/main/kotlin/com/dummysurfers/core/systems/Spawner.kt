package com.dummysurfers.core.systems

import com.dummysurfers.core.config.GameConfig
import com.dummysurfers.core.entities.Coin
import com.dummysurfers.core.entities.Obstacle
import com.dummysurfers.core.entities.ObstacleKind
import com.dummysurfers.core.entities.PowerUpPickup
import com.dummysurfers.core.state.PowerUpType
import com.dummysurfers.core.entities.Train
import com.dummysurfers.core.utils.Mathz
import kotlin.random.Random

/**
 * Pattern-based obstacle/coin spawning with IRON RULE enforcement:
 * 1. At least 1 lane always clear (or a full-width jump/slide action exists).
 * 2. Reaction time >= MIN_REACTION_GAP at current speed.
 * 3. No same-action obstacles closer than 0.5s apart.
 * 4. Coin trails guide the player along the safe path.
 */
class Spawner {
    val trains = ArrayList<Train>(12)
    val obstacles = ArrayList<Obstacle>(40)
    val coins = ArrayList<Coin>(140)
    val powerups = ArrayList<PowerUpPickup>(6)

    private val trainPool = ArrayList<Train>(12)
    private val obstaclePool = ArrayList<Obstacle>(40)
    private val coinPool = ArrayList<Coin>(140)
    private val powerupPool = ArrayList<PowerUpPickup>(6)

    private var frontier = 0f
    private var lastActionZ = 0f
    private var lastAction: Char = ' ' // 'j' jump, 's' slide, 'd' dodge
    private var safeLane = 0
    private var rng = Random(1)
    private var powerupTimer = 0f
    private var nextPowerupIn = GameConfig.POWERUP_INTERVAL

    fun reset(seed: Int = (System.nanoTime() and 0xffffff).toInt()) {
        rng = Random(seed)
        drain(trains) { t -> trainPool.add(t) }
        drain(obstacles) { o -> obstaclePool.add(o) }
        drain(coins) { c -> coinPool.add(c) }
        drain(powerups) { p -> powerupPool.add(p) }
        frontier = GameConfig.FIRST_SAFE_METERS
        lastActionZ = 0f
        lastAction = ' '
        safeLane = 0
        powerupTimer = 0f
        nextPowerupIn = GameConfig.POWERUP_INTERVAL * 0.6f
        spawnOpeningRun()
    }

    /**
     * v3.0: a welcoming straight coin run through the safe opening zone.
     * Previously the first 45m were completely EMPTY — the player pressed RUN
     * and stared at bare track for ~5 seconds with nothing to do ("can't
     * collect the coins"). Now: two juicy coin trails + a magnet taste.
     */
    private fun spawnOpeningRun() {
        val lane = 0
        var z = 6f
        while (z < GameConfig.FIRST_SAFE_METERS - 6f) {
            repeat(5) { i -> spawnCoin(lane, z + i * 1.6f, 0.9f) }
            z += 5 * 1.6f + 3.4f
        }
        // a taste of each side lane so swiping feels rewarded immediately
        repeat(4) { i -> spawnCoin(-1, 14f + i * 1.6f, 0.9f) }
        repeat(4) { i -> spawnCoin(1, 26f + i * 1.6f, 0.9f) }
    }

    private inline fun <T> drain(list: ArrayList<T>, recycle: (T) -> Unit) {
        list.forEach(recycle); list.clear()
    }

    fun update(dt: Float, speed: Float, scrollDelta: Float, playerLane: Int, attractMode: Boolean) {
        if (attractMode) return
        // scroll entities toward the player (z = distance ahead decreases)
        scroll(trains, scrollDelta) { it.z -= scrollDelta; it.z += it.speed * dt } // moving trains adjust
        scroll(obstacles, scrollDelta) { it.z -= scrollDelta }
        scroll(coins, scrollDelta) { it.z -= scrollDelta }
        scroll(powerups, scrollDelta) { it.z -= scrollDelta }

        // recycle behind the camera
        var i = 0
        while (i < trains.size) {
            val t = trains[i]
            if (t.z - t.totalLength < -24f) { trainPool.add(t); trains[i] = trains[trains.size - 1]; trains.removeAt(trains.size - 1) } else i++
        }
        i = 0
        while (i < obstacles.size) {
            val o = obstacles[i]
            if (o.z < -12f) { obstaclePool.add(o); obstacles[i] = obstacles[obstacles.size - 1]; obstacles.removeAt(obstacles.size - 1) } else i++
        }
        i = 0
        while (i < coins.size) {
            val c = coins[i]
            if (c.z < -6f || c.collected) { coinPool.add(c); coins[i] = coins[coins.size - 1]; coins.removeAt(coins.size - 1) } else i++
        }
        i = 0
        while (i < powerups.size) {
            val p = powerups[i]
            if (p.z < -8f || p.taken) { powerupPool.add(p); powerups[i] = powerups[powerups.size - 1]; powerups.removeAt(powerups.size - 1) } else i++
        }

        // power-up cadence
        powerupTimer += dt
        if (powerupTimer >= nextPowerupIn) {
            powerupTimer = 0f
            nextPowerupIn = GameConfig.POWERUP_INTERVAL * Mathz.rnd(rng, 0.8f, 1.25f)
            spawnPowerup()
        }

        // fill the frontier with patterns
        val phase = Difficulty.phase(distanceProxy())
        val gapUnits = (GameConfig.MIN_REACTION_GAP * speed).coerceAtLeast(GameConfig.MIN_PATTERN_GAP)
        val gap = Mathz.lerp(phase[2], phase[3], rng.nextFloat()) * (gapUnits / 22f).coerceIn(0.75f, 1.6f)
        while (frontier < GameConfig.VIEW_DISTANCE) {
            spawnPattern(phase)
            frontier += Mathz.rnd(rng, gap * 0.85f, gap * 1.25f)
        }
        frontier -= scrollDelta
        if (frontier < 10f) frontier = 10f
    }

    private var distance = 0f
    fun addDistance(d: Float) { distance += d }
    private fun distanceProxy() = distance

    private inline fun <T> scroll(list: ArrayList<T>, delta: Float, apply: (T) -> Unit) {
        for (e in list) apply(e)
    }

    // ── Pattern selection ──────────────────────────────────────────────
    private fun spawnPattern(phase: FloatArray) {
        val hard = phase[1] > 0.75f
        val mid = phase[1] > 0.62f
        val weights = if (hard) {
            floatArrayOf(14f, 12f, 16f, 14f, 8f, 10f, 14f, 6f, 8f)
        } else if (mid) {
            floatArrayOf(16f, 12f, 16f, 14f, 5f, 5f, 12f, 6f, 10f)
        } else {
            floatArrayOf(20f, 8f, 18f, 14f, 0f, 0f, 10f, 4f, 16f)
        }
        val pick = weighted(weights)
        when (pick) {
            0 -> singleTrain()
            1 -> doubleTrain()
            2 -> lowBarriers()
            3 -> highBarriers()
            4 -> gate()
            5 -> fenceFull()
            6 -> movingTrain()
            7 -> mixedCombo()
            else -> coinBonus()
        }
    }

    private fun weighted(w: FloatArray): Int {
        var sum = 0f
        for (v in w) sum += v
        var r = rng.nextFloat() * sum
        for (i in w.indices) { r -= w[i]; if (r <= 0f) return i }
        return w.size - 1
    }

    private fun laneArrayOf(): IntArray = when (rng.nextInt(3)) { 0 -> intArrayOf(-1); 1 -> intArrayOf(0); else -> intArrayOf(1) }

    // ── Patterns ───────────────────────────────────────────────────────
    private fun singleTrain() {
        val lanes = laneArrayOf()
        safeLane = pickSafe(lanes)
        val t = spawnTrain(lanes, cars = 3 + rng.nextInt(6), kind = 0, speed = 0f)
        coinTrailTo(safeLane, frontier + 2f, 10f)
        maybeRoofCoins(t)
    }

    private fun doubleTrain() {
        val clear = rng.nextInt(3) - 1
        val lanes = (-1..1).filter { it != clear }.toIntArray()
        safeLane = clear
        val t = spawnTrain(lanes, cars = 3 + rng.nextInt(4), kind = 0, speed = 0f)
        coinTrailTo(clear, frontier + 2f, 12f)
        maybeRoofCoins(t)
    }

    /** v4 roof-running: parked trains get a ramp + a jackpot coin line on top. */
    private fun maybeRoofCoins(t: Train) {
        if (t.cars < 3) return
        val zNear = t.z - t.totalLength
        var z = zNear + 1.4f
        while (z < t.z - 1.2f) {
            spawnCoin(t.lanes[0], z, GameConfig.TRAIN_HEIGHT + 0.7f)
            z += 1.7f
        }
    }

    /**
     * v4: ground/support height under the player — 0 on the ballast,
     * TRAIN_HEIGHT on a train roof, interpolating up a ramp.
     */
    fun supportAt(lane: Int, x: Float): Float {
        var s = 0f
        for (t in trains) {
            if (!t.lanes.contains(lane)) continue
            if (t.kind == 2) continue // oncoming trains never carry you
            val zNear = t.z - t.totalLength
            val zFar = t.z
            if (zNear <= 0f && zFar >= 0f) {
                s = maxOf(s, GameConfig.TRAIN_HEIGHT)
            } else if (t.kind == 0 && zNear > 0f && zNear < GameConfig.RAMP_LENGTH) {
                val h = GameConfig.TRAIN_HEIGHT * (1f - zNear / GameConfig.RAMP_LENGTH)
                s = maxOf(s, h)
            }
        }
        return s
    }

    private fun lowBarriers() {
        val two = rng.nextFloat() < 0.45f
        val lane = rng.nextInt(3) - 1
        if (guard('j')) return
        if (two) {
            val clear = rng.nextInt(3) - 1
            (-1..1).filter { it != clear }.forEach { spawnObstacle(ObstacleKind.LOW_BARRIER, it) }
            safeLane = clear
        } else {
            spawnObstacle(ObstacleKind.LOW_BARRIER, lane)
            safeLane = lane // jump over — coins arc
        }
        coinArcOver(frontier, if (two) safeLane else lane)
        lastAction = 'j'; lastActionZ = frontier
    }

    private fun highBarriers() {
        if (guard('s')) return
        val two = rng.nextFloat() < 0.45f
        val lane = rng.nextInt(3) - 1
        if (two) {
            val clear = rng.nextInt(3) - 1
            (-1..1).filter { it != clear }.forEach { spawnObstacle(ObstacleKind.HIGH_BARRIER, it) }
            safeLane = clear
            coinTrailTo(clear, frontier + 1f, 9f)
        } else {
            spawnObstacle(ObstacleKind.HIGH_BARRIER, lane)
            safeLane = lane
            coinLineLow(frontier, lane)
        }
        lastAction = 's'; lastActionZ = frontier
    }

    private fun gate() {
        if (guard('s')) return
        spawnObstacle(ObstacleKind.GATE, 0)
        safeLane = rng.nextInt(3) - 1
        coinLineLow(frontier, safeLane)
        lastAction = 's'; lastActionZ = frontier
    }

    private fun fenceFull() {
        if (guard('j')) return
        spawnObstacle(ObstacleKind.FENCE_FULL, 0)
        coinArcOver(frontier, rng.nextInt(3) - 1)
        lastAction = 'j'; lastActionZ = frontier
    }

    private fun movingTrain() {
        val lane = rng.nextInt(3) - 1
        safeLane = pickSafe(intArrayOf(lane))
        val approach = rng.nextFloat() < 0.3f
        spawnTrain(
            intArrayOf(lane), cars = 2 + rng.nextInt(3),
            kind = if (approach) 2 else 1,
            speed = if (approach) -GameConfig.APPROACH_TRAIN_SPEED else GameConfig.MOVING_TRAIN_REL_SPEED
        )
        coinTrailTo(safeLane, frontier + 4f, 10f)
    }

    private fun mixedCombo() {
        val lowLane = rng.nextInt(3) - 1
        val highLanes = (-1..1).filter { it != lowLane }
        spawnObstacle(ObstacleKind.LOW_BARRIER, lowLane)
        highLanes.forEach { spawnObstacle(ObstacleKind.HIGH_BARRIER, it) }
        safeLane = lowLane
        coinArcOver(frontier, lowLane)
        lastAction = 'j'; lastActionZ = frontier
    }

    private fun coinBonus() {
        val zigzag = rng.nextBoolean()
        var lane = rng.nextInt(3) - 1
        var z = frontier
        repeat(if (zigzag) 3 else 1) {
            if (zigzag) lane = ((lane + rng.nextInt(3) - 1) % 3).let { if (it > 1) it - 3 else if (it < -1) it + 3 else it }
            repeat(5) { spawnCoin(lane, z, 0.9f); z += 1.6f }
            if (zigzag) z += 2.5f
        }
        safeLane = lane
    }

    private fun spawnPowerup() {
        val p = powerupPool.removeLastOrNull() ?: PowerUpPickup()
        p.reset(rng.nextInt(PowerUpType.entries.size), rng.nextInt(3) - 1, frontier + 6f)
        powerups.add(p)
        repeat(4) { i -> spawnCoin(p.lane, p.z - 2f - i * 1.6f, 1f) }
    }

    // ── Helpers ────────────────────────────────────────────────────────
    /** Reject pattern if it repeats the last action too soon (<0.5s of travel). */
    private fun guard(action: Char): Boolean {
        // jump/slide arcs take ~0.7s of travel — ANY jump->slide (or reverse)
        // combo closer than that is physically impossible to clear
        val arcZ = (GameConfig.JUMP_DURATION + 0.35f) * speedHint
        if ((action == 'j' || action == 's') && (lastAction == 'j' || lastAction == 's') &&
            frontier - lastActionZ < arcZ) return true
        if (action == lastAction && frontier - lastActionZ < GameConfig.MIN_REACTION_GAP * speedHint) return true
        return false
    }

    var speedHint: Float = GameConfig.BASE_SPEED

    private fun pickSafe(blocked: IntArray): Int {
        var target = rng.nextInt(3) - 1
        var tries = 0
        while (blocked.contains(target) && tries < 8) { target = rng.nextInt(3) - 1; tries++ }
        if (blocked.contains(target)) {
            for (l in -1..1) if (!blocked.contains(l)) { target = l; break }
        }
        return target
    }

    // v4.6 LIVERY VARIETY: the spawner rolled rng.nextInt(6) while the palette
    // grew to 8 liveries in v4.2 — the teal harbor line and the graphite night
    // express were coded, textured, and NEVER seen in a run. Also anti-repeat:
    // two consists back-to-back in the same paint read as one endless train.
    private var lastLivery = -1
    private fun nextLivery(): Int {
        val n = com.dummysurfers.core.gfx.Palette.TRAIN_LIVERIES.size
        var l = rng.nextInt(n)
        if (l == lastLivery) l = (l + 1 + rng.nextInt(n - 1)) % n
        lastLivery = l
        return l
    }

    private fun spawnTrain(lanes: IntArray, cars: Int, kind: Int, speed: Float): Train {
        val t = trainPool.removeLastOrNull() ?: Train()
        // v4.5 hard clearance: a train's BODY extends BACKWARD from its front (z),
        // so a long train spawned near the frontier could historically stretch its
        // tail over the spawn point — the QA loop once caught that as a 0m frame-1
        // death ("can't start new game"). Never again: push the front out so the
        // tail stays TRAIN_SPAWN_CLEARANCE ahead of the runner.
        var z = frontier
        val tail = z - cars * GameConfig.TRAIN_CAR_LENGTH
        if (tail < GameConfig.TRAIN_SPAWN_CLEARANCE) z += GameConfig.TRAIN_SPAWN_CLEARANCE - tail
        t.reset(lanes, z, cars, kind, speed, nextLivery())
        trains.add(t)
        lastAction = 'd'; lastActionZ = frontier
        return t
    }

    private fun spawnObstacle(kind: ObstacleKind, lane: Int) {
        val o = obstaclePool.removeLastOrNull() ?: Obstacle()
        o.reset(kind, lane, frontier, rng.nextInt(3))
        obstacles.add(o)
    }

    fun spawnCoin(lane: Int, z: Float, y: Float) {
        if (coins.size >= 140) return
        val c = coinPool.removeLastOrNull() ?: Coin()
        c.reset(lane, z, y)
        coins.add(c)
    }

    private fun coinTrailTo(lane: Int, fromZ: Float, length: Float) {
        var z = fromZ
        while (z < fromZ + length) { spawnCoin(lane, z, 0.9f); z += 1.6f }
    }

    private fun coinArcOver(z: Float, lane: Int) {
        // 7-coin jump arc peaking at 2.4 units
        var i = 0
        while (i < 7) {
            val t = i / 6f
            val y = 0.9f + 1.5f * Mathz.sinPi(t)
            spawnCoin(lane, z - 3f + t * 7f, y)
            i++
        }
    }

    private fun coinLineLow(z: Float, lane: Int) {
        repeat(5) { i -> spawnCoin(lane, z + 1f + i * 1.4f, 0.55f) }
    }
}
