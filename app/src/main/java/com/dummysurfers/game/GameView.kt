package com.dummysurfers.game

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * SurfaceView hosting the fixed-timestep-ish game loop.
 * All heavy lifting lives in [Game]; this class only wires the
 * render thread, surface lifecycle and touch input.
 */
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private val game = Game(context)
    private var thread: Thread? = null

    @Volatile private var running = false
    @Volatile private var surfaceReady = false
    @Volatile private var lastTime = 0L

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
        keepScreenOn = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        startThread()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        game.onSurfaceChanged(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        stopThread()
    }

    private fun startThread() {
        if (running) return
        running = true
        lastTime = System.nanoTime()
        thread = Thread(this, "DummySurfersLoop")
        thread?.start()
    }

    private fun stopThread() {
        running = false
        var retry = 0
        while (thread?.isAlive == true && retry < 60) {
            try {
                thread?.join(50)
            } catch (_: InterruptedException) {
            }
            retry++
        }
        thread = null
        game.release()
    }

    fun pauseGame() {
        game.pauseGame()
    }

    fun handleBack(): Boolean = game.handleBack()

    override fun run() {
        while (running) {
            val now = System.nanoTime()
            var dt = (now - lastTime) / 1_000_000_000f
            lastTime = now
            if (dt > 0.05f) dt = 0.05f
            if (dt < 0f) dt = 0f

            game.update(dt)

            if (surfaceReady) {
                val canvas: Canvas? = try {
                    holder.lockCanvas()
                } catch (_: Exception) {
                    null
                }
                if (canvas != null) {
                    try {
                        synchronized(holder) { game.render(canvas) }
                    } finally {
                        try {
                            holder.unlockCanvasAndPost(canvas)
                        } catch (_: Exception) {
                        }
                    }
                }
            } else {
                try {
                    Thread.sleep(16)
                } catch (_: InterruptedException) {
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> game.onTouchDown(event.x, event.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> game.onTouchUp(event.x, event.y)
        }
        return true
    }
}
