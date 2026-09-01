package com.dummysurfers.core.systems

import com.dummysurfers.core.config.GameConfig
import com.dummysurfers.core.utils.Mathz

/**
 * Progressive difficulty manager: speed curve + phase lookup used by the
 * spawner. currentSpeed = base + (max-base) * (1 - e^(-distance * accel)).
 */
object Difficulty {
    fun phase(distance: Float): FloatArray {
        for (p in GameConfig.PHASES) if (distance <= p[0]) return p
        return GameConfig.PHASES[GameConfig.PHASES.size - 1]
    }

    fun speedPct(distance: Float): Float = phase(distance)[1]

    fun speed(distance: Float): Float = Mathz.speedAt(distance)

    /** Score multiplier from distance milestones. */
    fun multiplier(distance: Float): Int {
        var m = 1
        for (ms in GameConfig.MULTIPLIER_MILESTONES) if (distance >= ms[0]) m = ms[1].toInt()
        return m
    }
}
