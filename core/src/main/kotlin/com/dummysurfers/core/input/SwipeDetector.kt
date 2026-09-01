package com.dummysurfers.core.input

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter

/**
 * Subway-Surfers-grade swipe detection: 15px dead zone, 300ms window,
 * dominant-axis decision, single action per gesture. Also exposes keyboard
 * for desktop testing.
 */
class SwipeDetector(private val listener: Listener) : InputAdapter() {
    interface Listener {
        fun onSwipe(dir: Direction)
        fun onTap() {}   // quick touch without movement (double-tap = hoverboard)
    }

    enum class Direction { LEFT, RIGHT, UP, DOWN }

    private val deadZone = 15f
    private val maxDuration = 0.3f
    private var startX = 0f
    private var startY = 0f
    private var startNanos = 0L
    private var tracking = false
    var keyboardEnabled = true

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        startX = screenX.toFloat()
        startY = screenY.toFloat()
        startNanos = System.nanoTime()
        tracking = true
        return true
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (!tracking) return true
        tracking = false
        val dx = screenX - startX
        val dy = screenY - startY
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
        val dtSec = (System.nanoTime() - startNanos) / 1e9f
        if (dist < deadZone) {
            if (dtSec <= 0.28f) listener.onTap()
            return true
        }
        if (dtSec > maxDuration) return true
        if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
            listener.onSwipe(if (dx > 0) Direction.RIGHT else Direction.LEFT)
        } else {
            listener.onSwipe(if (dy > 0) Direction.DOWN else Direction.UP)
        }
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
