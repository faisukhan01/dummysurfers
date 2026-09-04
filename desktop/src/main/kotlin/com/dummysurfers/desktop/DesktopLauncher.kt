package com.dummysurfers.desktop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.utils.ScreenUtils
import com.dummysurfers.core.DummySurfersGame
import java.io.File

/**
 * Desktop test launcher — run with `./gradlew desktop:run`.
 *
 * QA harness (env-driven, no effect in normal play):
 *  - DS_SHOT_DIR=/tmp/shots            → periodic PNG screenshots (DS_SHOT_SEC, default 2s)
 *  - DS_AUTO="1.0:START;3.0:TAP:360,450;5.0:SHOT:menu;7.0:QUIT"
 *      START   → begin run          LEFT/RIGHT/UP/DOWN → swipe
 *      TAP:x,y → real input-path tap at virtual stage coords (menu buttons!)
 *      SHOT:x  → labeled screenshot, QUIT → exit
 *
 * TAP goes through Gdx.input.inputProcessor — the same multiplexer path a
 * finger uses on the phone, so menu-button clickability is genuinely tested.
 */
fun main() {
    // v6.2 QA: execute the SHIPPED BootWatchdog state machine (pure core
    // logic — the exact class the APK's Android overlay calls every 200 ms)
    // and exit before any GL comes up. Exit code 0 = all assertions held.
    if (System.getenv("DS_WATCHDOG_SELFTEST") == "1") {
        runWatchdogSelftest()
        return
    }

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
        // v5.3 QA NOTE: the DS_AUTO script thread here fires taps OUTSIDE the
        // render loop; under Xvfb the LWJGL3 loop may starve between them
        // (taps landed, clicks were never consumed by a frame). Scripted QA
        // therefore moved INTO the game's devHarness (DS_PAUSE_AT, DS_PANEL)
        // which runs inside render() — every beat lands between real frames.
    }

    if (shotDir != null || (script != null && script.isNotBlank() && script != "0")) {
        val dir = shotDir ?: "/tmp/ds-shots"
        File(dir).mkdirs()
        var shotIndex = 0
        var lastShot = 0f

        fun shoot(label: String) {
            val pm = ScreenUtils.getFrameBufferPixmap(0, 0, Gdx.graphics.width, Gdx.graphics.height)
            val flipped = Pixmap(pm.width, pm.height, pm.format)
            for (y in 0 until pm.height) {
                flipped.drawPixmap(pm, 0, y, pm.width, 1, 0, pm.height - 1 - y, pm.width, 1)
            }
            val name = "%04d-%s.png".format(shotIndex++, if (label.isBlank()) "frame" else label)
            PixmapIO.writePNG(com.badlogic.gdx.files.FileHandle(File(dir, name)), flipped)
            flipped.dispose()
            pm.dispose()
            Gdx.app.log("DSHARNESS", "shot $name state=${game.debugState()}")
        }

        /** Virtual-stage coords → window px, then a real touchDown/touchUp pair. */
        fun tapVirtual(vx: Float, vy: Float) {
            val w = Gdx.graphics.width.toFloat()
            val h = Gdx.graphics.height.toFloat()
            val scale = minOf(w / 720f, h / 1280f)
            val vpW = 720f * scale
            val vpH = 1280f * scale
            val vpX = (w - vpW) / 2f
            val vpY = (h - vpH) / 2f
            val sx = (vpX + vx * scale).toInt()
            val sy = (vpY + (1280f - vy) * scale).toInt()
            val proc = Gdx.input.inputProcessor
            Gdx.app.log("DSHARNESS", "tap virtual($vx,$vy) → screen($sx,$sy) proc=${proc?.javaClass?.simpleName}")
            val d = proc?.touchDown(sx, sy, 0, 0)
            val u = proc?.touchUp(sx, sy, 0, 0)
            Gdx.app.log("DSHARNESS", "tap result down=$d up=$u")
        }

        Thread {
            while (Gdx.app == null) Thread.sleep(50)
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

        if (script != null && script.isNotBlank() && script != "0") {
            Thread {
                while (Gdx.app == null) Thread.sleep(50)
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
                            "LEFT" -> game.debugSwipe(com.dummysurfers.core.input.SwipeDetector.Direction.LEFT)
                            "RIGHT" -> game.debugSwipe(com.dummysurfers.core.input.SwipeDetector.Direction.RIGHT)
                            "UP" -> game.debugSwipe(com.dummysurfers.core.input.SwipeDetector.Direction.UP)
                            "DOWN" -> game.debugSwipe(com.dummysurfers.core.input.SwipeDetector.Direction.DOWN)
                            "TAP" -> {
                                val xy = arg.split(",")
                                tapVirtual(xy.getOrNull(0)?.toFloatOrNull() ?: 360f, xy.getOrNull(1)?.toFloatOrNull() ?: 640f)
                            }
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

/** Assertions over com.dummysurfers.core.BootWatchdog — the "30 minutes on
 *  painting textures 2/3" fix. Any failure prints STALLTEST-FAIL and exits 1. */
private fun runWatchdogSelftest() {
    var failed = 0
    fun check(name: String, cond: Boolean) {
        println("STALLTEST ${if (cond) "PASS" else "FAIL"} — $name")
        if (!cond) failed++
    }
    val stall = com.dummysurfers.core.BootWatchdog.STALL_MS
    check("stall window is 20s by default", stall == 20_000L)

    // 1. a HEALTHY boot (status ticks every 500 ms) never stalls
    com.dummysurfers.core.BootWatchdog.reset()
    var t = 0L
    var ever = false
    while (t <= 90_000L) {
        if (com.dummysurfers.core.BootWatchdog.stalled("painting textures 2/3 · ${t / 500} painted", 0.3f, false, null, t)) ever = true
        t += 200
    }
    check("ticking status never triggers the watchdog", !ever)

    // 2. a FROZEN boot (identical status) stalls exactly when the window elapses
    com.dummysurfers.core.BootWatchdog.reset()
    t = 0
    var firstTrue = -1L
    while (t <= stall + 5_000L) {
        if (firstTrue < 0 && com.dummysurfers.core.BootWatchdog.stalled("painting textures 2/3", 0.3f, false, null, t)) firstTrue = t
        t += 200
    }
    check("frozen status stays silent before the window", firstTrue == -1L || firstTrue >= stall)
    check("frozen status triggers at/after ${stall}ms", firstTrue >= stall)

    // 3. progress-only movement still counts as progress (float key)
    com.dummysurfers.core.BootWatchdog.reset()
    t = 0
    ever = false
    while (t <= 90_000L) {
        if (com.dummysurfers.core.BootWatchdog.stalled("fonts", 0.3f + t / 300_000f, false, null, t)) ever = true
        t += 200
    }
    check("moving progress never triggers", !ever)

    // 4. ready disarms and clears state (next boot starts a fresh window)
    com.dummysurfers.core.BootWatchdog.reset()
    com.dummysurfers.core.BootWatchdog.stalled("x", 0.5f, false, null, 0)
    check("ready returns false", !com.dummysurfers.core.BootWatchdog.stalled("x", 0.5f, true, null, 999_999L))
    check("state cleared after ready (fresh window)", !com.dummysurfers.core.BootWatchdog.stalled("y", 0.1f, false, null, 999_999L))

    // 5. an error state disarms (SafeMode owns recovery then)
    check("error state returns false", !com.dummysurfers.core.BootWatchdog.stalled("startup problem", 0.5f, false, "boom", 999_999L))

    // 6. null status is safe
    check("null status safe", !com.dummysurfers.core.BootWatchdog.stalled(null, 0f, false, null, 0L))

    if (failed > 0) {
        println("STALLTEST-FAIL — $failed assertion(s) failed")
        kotlin.system.exitProcess(1)
    }
    println("STALLTEST-OK — all BootWatchdog assertions held")
}
