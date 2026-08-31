package com.dummysurfers.core.utils

import com.dummysurfers.core.config.GameConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/** Math helpers: easing, interpolation, seeded noise. */
object Mathz {
    fun clamp(v: Float, lo: Float, hi: Float) = max(lo, min(hi, v))
    fun clamp01(v: Float) = clamp(v, 0f, 1f)
    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    fun invLerp(a: Float, b: Float, v: Float) = if (b - a == 0f) 0f else clamp01((v - a) / (b - a))

    /** Ease-out cubic. */
    fun easeOut(t: Float): Float {
        val x = clamp01(t)
        return 1f - (1f - x) * (1f - x) * (1f - x)
    }

    /** Ease-in-out smoothstep. */
    fun smooth(t: Float): Float {
        val x = clamp01(t)
        return x * x * (3f - 2f * x)
    }

    fun approach(cur: Float, target: Float, delta: Float): Float {
        val d = target - cur
        return when {
            abs(d) <= delta -> target
            else -> cur + d.sign * delta
        }
    }

    /** Deterministic hash-based pseudo random in [0,1). */
    fun hash01(seed: Int): Float {
        var h = seed * 374761393 + 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)).toLong() and 0x7fffffffL) / 2147483648f
    }

    /** Smooth 1D value noise. */
    fun noise1(x: Float, seed: Int = 0): Float {
        val i = floor(x).toInt()
        val f = x - i
        val a = hash01(i + seed * 7919)
        val b = hash01(i + 1 + seed * 7919)
        return lerp(a, b, smooth(f))
    }

    fun speedAt(distance: Float): Float {
        val base = GameConfig.BASE_SPEED
        val range = GameConfig.MAX_SPEED - base
        return base + range * (1f - exp(-distance * GameConfig.ACCEL_FACTOR))
    }

    fun sign(v: Float): Float = if (v >= 0f) 1f else -1f

    fun sinPi(t: Float) = sin(PI.toFloat() * clamp01(t))

    fun cos01(t: Float) = cos(t * PI.toFloat())

    fun rnd(rng: Random, lo: Float, hi: Float) = lo + rng.nextFloat() * (hi - lo)

    /** HSV → RGB helper for hue-cycling effects (rainbow trail). */
    fun hsv(h: Float, s: Float, v: Float, a: Float, out: com.badlogic.gdx.graphics.Color): com.badlogic.gdx.graphics.Color {
        val hh = ((h % 360f) + 360f) % 360f / 60f
        val i = hh.toInt()
        val f = hh - i
        val p = v * (1f - s)
        val q = v * (1f - s * f)
        val t = v * (1f - s * (1f - f))
        return when (i % 6) {
            0 -> out.set(v, t, p, a)
            1 -> out.set(q, v, p, a)
            2 -> out.set(p, v, t, a)
            3 -> out.set(p, q, v, a)
            4 -> out.set(t, p, v, a)
            else -> out.set(v, p, q, a)
        }
    }
}
