package com.dummysurfers.core.particles

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.dummysurfers.core.utils.Mathz
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pooled screen-space particle system: coin sparkles, power bursts, dust,
 * shield fragments, confetti, speed streaks and floating score texts.
 */
class Particles(private val max: Int = 320) {
    class P {
        var active = false
        var x = 0f; var y = 0f; var vx = 0f; var vy = 0f
        var life = 0f; var maxLife = 1f; var size = 3f
        var color = Color(Color.WHITE)
        var shape = 0 // 0 circle, 1 spark, 2 confetti, 3 ring, 4 streak, 5 text
        var rot = 0f; var vrot = 0f
        var grav = 0f
        var text: String? = null
    }

    private val pool = Array(max) { P() }
    private var cursor = 0
    private val rng = Random(99)
    private val tmp = Color()

    private fun next(): P {
        val p = pool[cursor]
        cursor = (cursor + 1) % max
        return p
    }

    fun burst(cx: Float, cy: Float, count: Int, color: Color, speed: Float, size: Float, shape: Int = 0, grav: Float = 900f, life: Float = 0.6f) {
        for (i in 0 until count) {
            val p = next()
            val a = rng.nextFloat() * 2f * PI.toFloat()
            val sp = speed * (0.4f + rng.nextFloat() * 0.8f)
            p.active = true
            p.x = cx; p.y = cy
            p.vx = cos(a) * sp; p.vy = sin(a) * sp
            p.maxLife = life * (0.6f + rng.nextFloat() * 0.7f); p.life = p.maxLife
            p.size = size * (0.6f + rng.nextFloat() * 0.8f)
            tmp.set(color); tmp.a = 1f; p.color.set(tmp)
            p.shape = shape
            p.rot = rng.nextFloat() * 360f; p.vrot = (rng.nextFloat() * 2f - 1f) * 300f
            p.grav = grav
            p.text = null
        }
    }

    fun confetti(cx: Float, cy: Float, count: Int) {
        val colors = arrayOf(Color(0xffc93cff.toInt()), Color(0xef4444ff.toInt()), Color(0x2dd4bfff.toInt()), Color(0xf2a75bff.toInt()), Color(0xa3e635ff.toInt()))
        for (i in 0 until count) {
            val p = next()
            val a = rng.nextFloat() * 2f * PI.toFloat()
            val sp = 240f + rng.nextFloat() * 420f
            p.active = true
            p.x = cx; p.y = cy
            p.vx = cos(a) * sp; p.vy = sin(a) * sp + 260f
            p.maxLife = 1.1f + rng.nextFloat() * 0.7f; p.life = p.maxLife
            p.size = 5f + rng.nextFloat() * 6f
            p.color.set(colors[rng.nextInt(colors.size)])
            p.shape = 2
            p.rot = rng.nextFloat() * 360f; p.vrot = (rng.nextFloat() * 2f - 1f) * 420f
            p.grav = 700f
            p.text = null
        }
    }

    fun text(x: Float, y: Float, msg: String, color: Color, size: Float = 18f) {
        val p = next()
        p.active = true
        p.x = x; p.y = y; p.vx = 0f; p.vy = 90f
        p.maxLife = 0.9f; p.life = p.maxLife
        p.size = size
        p.color.set(color)
        p.shape = 5
        p.grav = 0f
        p.text = msg
    }

    /** Boost speed streaks from screen edges toward center. */
    fun streak(sw: Float, sh: Float, count: Int) {
        for (i in 0 until count) {
            val p = next()
            val side = rng.nextInt(2)
            p.active = true
            p.x = if (side == 0) rng.nextFloat() * sw * 0.3f else sw * (0.7f + rng.nextFloat() * 0.3f)
            p.y = sh * (0.35f + rng.nextFloat() * 0.6f)
            p.vx = (if (p.x < sw / 2f) 1f else -1f) * (600f + rng.nextFloat() * 500f)
            p.vy = (rng.nextFloat() * 2f - 1f) * 60f
            p.maxLife = 0.22f + rng.nextFloat() * 0.12f; p.life = p.maxLife
            p.size = 2f + rng.nextFloat() * 3f
            p.color.set(1f, 1f, 0.95f, 0.8f)
            p.shape = 4
            p.grav = 0f
        }
    }

    fun update(dt: Float) {
        for (p in pool) {
            if (!p.active) continue
            p.life -= dt
            if (p.life <= 0f) { p.active = false; continue }
            p.vy -= p.grav * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.rot += p.vrot * dt
        }
    }

    fun render(sr: ShapeRenderer) {
        // NOTE: rendered inside a single ShapeType.Filled pass (see EntityRenderer).
        // We must NOT call sr.set() here — it throws unless autoShapeType was
        // enabled, and this was a LATENT CRASH on every coin-pickup spark burst.
        // Line-style shapes are drawn as thin filled quads (rectLine) instead.
        for (p in pool) {
            if (!p.active) continue
            val t = p.life / p.maxLife
            tmp.set(p.color); tmp.a = Mathz.clamp01(t * 1.4f)
            sr.setColor(tmp)
            when (p.shape) {
                0 -> sr.circle(p.x, p.y, p.size * (0.5f + t * 0.5f))
                1 -> {
                    val w = (p.size * 0.45f).coerceAtLeast(1.2f)
                    sr.rectLine(p.x, p.y, p.x - p.vx * 0.03f, p.y - p.vy * 0.03f, w)
                }
                2 -> sr.rect(p.x - p.size / 2, p.y - p.size / 4, p.size / 2, p.size / 4, p.size, p.size / 2, 1f, 1f, p.rot)
                3 -> {
                    // ring drawn as short filled segments around the circumference
                    val r = p.size * (1.4f - t)
                    val segs = 12
                    val lw = 1.6f
                    var i = 0
                    while (i < segs) {
                        val a0 = 2.0 * PI * i / segs
                        val a1 = 2.0 * PI * (i + 1) / segs
                        sr.rectLine(
                            p.x + cos(a0).toFloat() * r, p.y + sin(a0).toFloat() * r,
                            p.x + cos(a1).toFloat() * r, p.y + sin(a1).toFloat() * r, lw
                        )
                        i++
                    }
                }
                4 -> sr.rectLine(p.x, p.y, p.x - p.vx * 0.05f, p.y - p.vy * 0.05f, (p.size * 0.6f).coerceAtLeast(1.2f))
            }
        }
    }

    fun eachText(cb: (P) -> Unit) {
        for (p in pool) if (p.active && p.shape == 5) cb(p)
    }

    fun clear() {
        for (p in pool) p.active = false
    }
}
