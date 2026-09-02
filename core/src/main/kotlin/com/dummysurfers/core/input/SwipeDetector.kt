package com.dummysurfers.core.input

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter

/**
 * Subway-Surfers-grade swipe detection.
 *
 * v3.0 FIX — the two bugs that made the game feel "dead" on phones:
 *  1. The old 300ms duration cap DISCARDED every deliberate/slow swipe (very
 *     common on real devices → "game doesn't respond"). Swipes now fire at ANY
 *     duration.
 *  2. Actions only fired on finger LIFT. SS fires the action the moment the
 *     finger crosses the threshold mid-gesture — far snappier. We now fire on
 *     drag as soon as [fireThreshold] px are travelled, one action per gesture.
 */
class SwipeDetector(private val listener: Listener) : InputAdapter() {
    interface Listener {
        fun onSwipe(dir: Direction)
        fun onTap() {}   // quick touch without movement (double-tap = hoverboard)
    }

    enum class Direction { LEFT, RIGHT, UP, DOWN }

    /** px travelled (in either axis) before the swipe fires mid-gesture. */
    private val fireThreshold = 42f
    /** below this on touchUp it's a tap, not a swipe. */
    private val tapZone = 18f
    /** once fired, the finger must return near the start before another fire. */
    private val rearmRation = 2.2f

    private var startX = 0f
    private var startY = 0f
    private var startNanos = 0L
    private var tracking = false
    private var fired = false
    var keyboardEnabled = true

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        startX = screenX.toFloat()
        startY = screenY.toFloat()
        startNanos = System.nanoTime()
        tracking = true
        fired = false
        return true
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        if (!tracking || fired) return false
        tryFire(screenX.toFloat(), screenY.toFloat())
        return false
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (!tracking) return true
        tracking = false
        if (!fired) {
            val handled = tryFire(screenX.toFloat(), screenY.toFloat())
            if (!handled) {
                val dx = screenX - startX
                val dy = screenY - startY
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                val dtSec = (System.nanoTime() - startNanos) / 1e9f
                // quick, still touch = tap (double-tap = hoverboard)
                if (dist < tapZone && dtSec <= 0.30f) listener.onTap()
            }
        }
        return true
    }

    /** Fires the dominant-axis swipe once [fireThreshold] is crossed. Returns true if fired. */
    private fun tryFire(x: Float, y: Float): Boolean {
        val dx = x - startX
        val dy = y - startY
        val adx = kotlin.math.abs(dx)
        val ady = kotlin.math.abs(dy)
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
        if (dist < fireThreshold) return false
        fired = true
        if (adx > ady) listener.onSwipe(if (dx > 0) Direction.RIGHT else Direction.LEFT)
        else listener.onSwipe(if (dy > 0) Direction.DOWN else Direction.UP)
        return true
    }

    /** Call from the game loop to translate desktop key input. */
    fun pollKeyboard() {
        if (!keyboardEnabled) return
        fun tapped(key: Int): Boolean = Gdx.input.isKeyJustPressed(key)
        when {
            tapped(com.badlogic.gdx.Input.Keys.LEFT) || tapped(com.badlogic.gdx.Input.Keys.A) -> listener.onSwipe(Direction.LEFT)
            tapped(com.badlogic.gdx.Input.Keys.RIGHT) || tapped(com.badlogic.gdx.Input.Keys.D) -> listener.onSwipe(Direction.RIGHT)
            tapped(com.badlogic.gdx.Input.Keys.UP) || tapped(com.badlogic.gdx.Input.Keys.W) || tapped(com.badlogic.gdx.Input.Keys.SPACE) -> listener.onSwipe(Direction.UP)
            tapped(com.badlogic.gdx.Input.Keys.DOWN) || tapped(com.badlogic.gdx.Input.Keys.S) -> listener.onSwipe(Direction.DOWN)
        }
    }
}
