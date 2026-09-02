package com.dummysurfers.desktop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.utils.ScreenUtils
import com.dummysurfers.core.DummySurfersGame
import com.dummysurfers.core.input.SwipeDetector
import java.io.File

/**
 * Desktop test launcher — run with `./gradlew desktop:run`.
 *
 * QA harness (used with xvfb-run in CI-like sandboxes):
 *  - DS_SHOT_DIR=/tmp/shots  → saves a PNG screenshot every DS_SHOT_SEC (default 2s)
 *  - DS_AUTO="1.0:START;3.0:RIGHT;5.0:UP;7.0:SHOT:jump;9.0:DOWN" → scripted play
 *    Actions: START, LEFT, RIGHT, UP, DOWN, PAUSE, RESUME, HOME, RETRY,
 *             SHOT[:label], QUIT
 */
fun main() {
    val shotDir = System.getenv("DS_SHOT_DIR")
    val shotSec = System.getenv("DS_SHOT_SEC")?.toFloatOrNull() ?: 2f
    val script = System.getenv("DS_AUTO")

    val game = DummySurfersGame()
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Dummy Surfers by FSK")
        setWindowedMode(540, 960)
        setResizable(false)
        useVsync(false)
        setForegroundFPS(60)
    }

    // ── Autopilot + screenshot engine ──────────────────────────────────
    if (script != null || shotDir != null) {
        val dir = shotDir ?: "/tmp/ds-shots"
        File(dir).mkdirs()
        var shotIndex = 0
        var lastShot = 0f

        fun shoot(label: String) {
            if (!Gdx.graphics.isGL30Available && false) return
            val pm = ScreenUtils.getFrameBufferPixmap(0, 0, Gdx.graphics.width, Gdx.graphics.height)
            val flipped = Pixmap(pm.width, pm.height, pm.format)
            for (y in 0 until pm.height) {
                flipped.drawPixmap(pm, 0, y, pm.width, 1, 0, pm.height - 1 - y, pm.width, 1)
            }
            val name = "%04d-%s.png".format(shotIndex++, if (label.isBlank()) "frame" else label)
            PixmapIO.writePNG(File(dir, name), flipped)
            flipped.dispose()
            pm.dispose()
            Gdx.app.log("DSHARNESS", "shot $name state=${game.debugState()}")
        }

        // periodic screenshots
        Thread {
            while (true) {
                Thread.sleep(120)
                if (shotSec > 0f) {
                    val now = System.nanoTime() / 1e9f
                    if (now - lastShot >= shotSec) {
                        lastShot = now
                        Gdx.app.postRunnable { shoot("t${(now * 10).toInt()}") }
                    }
                }
            }
        }.apply { isDaemon = true }.start()

        // scripted timeline
        if (script != null && script.isNotBlank() && script != "0") {
            Thread {
                val t0 = System.nanoTime()
                fun elapsed(): Float = (System.nanoTime() - t0) / 1e9f
                for (raw in script.split(";")) {
                    val parts = raw.trim().split(":")
                    val at = parts.getOrNull(0)?.toFloatOrNull() ?: continue
                    val action = parts.getOrNull(1) ?: continue
                    val arg = parts.getOrNull(2) ?: ""
                    while (elapsed() < at) Thread.sleep(40)
                    Gdx.app.postRunnable {
                        Gdx.app.log("DSHARNESS", "action=$action arg=$arg state=${game.debugState()}")
                        when (action) {
                            "START" -> game.debugStartRun()
                            "LEFT" -> game.debugSwipe(SwipeDetector.Direction.LEFT)
                            "RIGHT" -> game.debugSwipe(SwipeDetector.Direction.RIGHT)
                            "UP" -> game.debugSwipe(SwipeDetector.Direction.UP)
                            "DOWN" -> game.debugSwipe(SwipeDetector.Direction.DOWN)
                            "SHOT" -> shoot(arg)
                            "QUIT" -> Gdx.app.exit()
                        }
                    }
                }
            }.apply { isDaemon = true }.start()
        }
    }

    Lwjgl3Application(game, config)
}
