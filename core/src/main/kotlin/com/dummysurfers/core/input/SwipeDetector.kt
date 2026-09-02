package com.dummysurfers.core.input

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter

/**
 * Subway-Surfers-grade swipe detection:
 *  - fires the MOMENT the drag crosses the trigger distance (mid-gesture, like the
 *    real game) instead of waiting for finger release;
 *  - density-aware trigger (~22dp) so it feels identical on any screen;
 *  - no gesture-duration limit (slow deliberate swipes count, too);
 *  - release-flick fallback for very quick flicks that lift before the threshold;
 *  - one action per gesture, then locked until the finger lifts;
 *  - keyboard support for desktop testing.
 */
class SwipeDetector(private val listener: Listener) : InputAdapter() {
    interface Listener {
        fun onSwipe(dir: Direction)
    }

    enum class Direction { LEFT, RIGHT, UP, DOWN }

    private var startX = 0f
    private var startY = 0f
    private var tracking = false
    private var fired = false

    /** ~22dp in physical pixels, clamped so desktop (density 1) stays snappy. */
    private val triggerPx: Float
        get() = (22f * Gdx.graphics.density).coerceIn(24f, 110f)

    private val flickPx: Float
        get() = triggerPx * 0.55f

    var keyboardEnabled = true

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        startX = screenX.toFloat()
        startY = screenY.toFloat()
        tracking = true
        fired = false
        return false
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        if (!tracking || fired) return false
        val dx = screenX - startX
        val dy = screenY - startY
        val adx = kotlin.math.abs(dx)
        val ady = kotlin.math.abs(dy)
        val trigger = triggerPx
        if (adx >= trigger && adx > ady) {
            fire(if (dx > 0) Direction.RIGHT else Direction.LEFT)
        } else if (ady >= trigger) {
            // screen y grows downward; finger moving down = swipe DOWN
            fire(if (dy > 0) Direction.DOWN else Direction.UP)
        }
        return false
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (!tracking) return false
        tracking = false
        if (fired) return false
        // flick fallback: short but very fast gesture counts
        val dx = screenX - startX
        val dy = screenY - startY
        val adx = kotlin.math.abs(dx)
        val ady = kotlin.math.abs(dy)
        val flick = flickPx
        if (adx >= flick && adx > ady) {
            fire(if (dx > 0) Direction.RIGHT else Direction.LEFT)
        } else if (ady >= flick) {
            fire(if (dy > 0) Direction.DOWN else Direction.UP)
        }
        return false
    }

    private fun fire(dir: Direction) {
        if (fired) return
        fired = true
        listener.onSwipe(dir)
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
