package com.dummysurfers.game

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.AudioManager
import android.media.ToneGenerator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Dummy Surfers — a complete 2D endless runner.
 *
 * 3 lanes, swipe controls, jump / slide / dodge, coins, particle FX,
 * parallax sunset city, ramping difficulty, high-score persistence.
 * Every sprite is drawn procedurally with Canvas — no image assets.
 */
class Game(context: Context) {

    enum class State { MENU, RUNNING, PAUSED, GAME_OVER }

    // ------------------------------------------------------------------
    // Entities
    // ------------------------------------------------------------------

    private class Obstacle(val type: Int, cx: Float, bottomY: Float, laneW: Float, h: Float) {
        val rect: RectF = RectF()
        var passed = false

        init {
            when (type) {
                TYPE_BARRIER -> rect.set(cx - laneW * 0.36f, bottomY - h * 0.075f, cx + laneW * 0.36f, bottomY)
                TYPE_BAR -> rect.set(cx - laneW * 0.38f, bottomY - h * 0.30f, cx + laneW * 0.38f, bottomY - h * 0.10f)
                TYPE_BLOCK -> rect.set(cx - laneW * 0.34f, bottomY - h * 0.24f, cx + laneW * 0.34f, bottomY)
                else -> rect.set(cx - laneW * 0.40f, bottomY - h * 0.36f, cx + laneW * 0.40f, bottomY)
            }
        }

        companion object {
            const val TYPE_BARRIER = 0 // jump over (or dodge)
            const val TYPE_BAR = 1     // slide under (or dodge)
            const val TYPE_BLOCK = 2   // must dodge
            const val TYPE_TRAIN = 3   // long, must dodge
        }
    }

    private class Coin(val x: Float, var y: Float, val r: Float) {
        val phase = Random.nextFloat() * 6.283f
    }

    private class Particle(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var life: Float, val maxLife: Float, val size: Float,
        val color: Int, val grav: Float
    )

    private class Popup(val text: String, val x: Float, var y: Float, var life: Float = 0.8f)

    private class Prop(val x: Float, var y: Float, val left: Boolean)

    private class Building(val x: Float, val y: Float, val w: Float, val h: Float)

    // ------------------------------------------------------------------
    // Persistent state
    // ------------------------------------------------------------------

    private val prefs: SharedPreferences = context.getSharedPreferences("dummysurfers", Context.MODE_PRIVATE)
    private var best = 0
    private var muted = false
    private var newBest = false
    private var tone: ToneGenerator? = null

    var state = State.MENU
        private set

    // ------------------------------------------------------------------
    // Geometry (recomputed on every surface size change)
    // ------------------------------------------------------------------

    private var w = 0f
    private var h = 0f
    private var roadLeft = 0f
    private var roadRight = 0f
    private var roadTop = 0f
    private var laneW = 0f
    private val laneX = FloatArray(3)
    private var baseY = 0f
    private var playerW = 0f
    private var playerH = 0f
    private var coinR = 10f
    private var swipeThreshold = 70f
    private var grav = 1f
    private var jumpV = 1f
    private var skyShader: Shader? = null
    private var glowShader: Shader? = null

    private val pauseBtn = RectF()
    private val muteBtn = RectF()

    // ------------------------------------------------------------------
    // World
    // ------------------------------------------------------------------

    private val obstacles = ArrayList<Obstacle>()
    private val coins = ArrayList<Coin>()
    private val particles = ArrayList<Particle>()
    private val popups = ArrayList<Popup>()
    private val props = ArrayList<Prop>()
    private val skylineFar = ArrayList<Building>()
    private val skylineNear = ArrayList<Building>()
    private val clouds = ArrayList<FloatArray>()

    // player
    private var playerX = 0f
    private var laneFrom = 1
    private var laneTo = 1
    private var laneT = 1f
    private var airY = 0f
    private var vy = 0f
    private var jumping = false
    private var sliding = false
    private var slideTimer = 0f
    private var queuedSlide = false
    private var runPhase = 0f
    private var crashed = false

    // difficulty / timing
    private var scrollSpeed = 0f
    private var elapsed = 0f
    private var spawnTimer = 1f
    private var coinTimer = 0.6f
    private var bgOffset = 0f

    // score / fx
    private var score = 0f
    private var coinCount = 0
    private var time = 0f
    private var gameOverTimer = 0f
    private var shakeTime = 0f
    private var shakeMag = 0f

    // touch
    private var tX = 0f
    private var tY = 0f
    private var tActive = false

    // ------------------------------------------------------------------
    // Paints
    // ------------------------------------------------------------------

    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pShadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pTextShadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pTextStroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stripePath = Path()
    private val tempRect = RectF()
    private val playerRect = RectF()
    private val ovalRect = RectF()

    init {
        pStroke.style = Paint.Style.STROKE
        pShadow.style = Paint.Style.FILL
        pShadow.color = Color.BLACK
        pText.typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        pTextShadow.set(pText)
        pTextShadow.color = Color.argb(150, 0, 0, 0)
        pTextStroke.set(pText)
        pTextStroke.style = Paint.Style.STROKE
        pText.textAlign = Paint.Align.CENTER
        muted = prefs.getBoolean("muted", false)
        best = prefs.getInt("best", 0)
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    fun onSurfaceChanged(width: Int, height: Int) {
        w = width.toFloat()
        h = height.toFloat()
        roadLeft = w * 0.12f
        roadRight = w * 0.88f
        roadTop = h * 0.56f
        laneW = (roadRight - roadLeft) / 3f
        for (i in 0..2) laneX[i] = roadLeft + laneW * (i + 0.5f)
        baseY = h * 0.80f
        playerW = laneW * 0.46f
        playerH = h * 0.125f
        coinR = h * 0.024f
        swipeThreshold = max(w * 0.07f, 70f)
        grav = h * 4.6f
        jumpV = h * 1.32f
        if (state == State.MENU) playerX = laneX[1]

        pauseBtn.set(w - w * 0.13f, h * 0.03f, w - w * 0.04f, h * 0.03f + w * 0.09f)
        muteBtn.set(w - w * 0.25f, h * 0.03f, w - w * 0.16f, h * 0.03f + w * 0.09f)

        skyShader = LinearGradient(
            0f, 0f, 0f, roadTop,
            intArrayOf(
                0xFF141230.toInt(),
                0xFF4B2460.toInt(),
                0xFF9A3E63.toInt(),
                0xFFFF8E5E.toInt()
            ),
            floatArrayOf(0f, 0.45f, 0.78f, 1f),
            Shader.TileMode.CLAMP
        )
        glowShader = RadialGradient(
            w * 0.76f, roadTop - h * 0.14f, w * 0.26f,
            intArrayOf(Color.argb(200, 255, 214, 140), Color.argb(0, 255, 214, 140)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )

        buildSkyline()
        buildClouds()
        buildProps()
    }

    fun pauseGame() {
        if (state == State.RUNNING) state = State.PAUSED
    }

    fun handleBack(): Boolean = when (state) {
        State.RUNNING -> {
            state = State.PAUSED
            true
        }
        State.PAUSED -> {
            state = State.RUNNING
            true
        }
        State.GAME_OVER -> {
            state = State.MENU
            true
        }
        else -> false
    }

    fun release() {
        try {
            tone?.release()
        } catch (_: Exception) {
        }
        tone = null
    }

    private fun savePrefs() {
        prefs.edit().putInt("best", best).putBoolean("muted", muted).apply()
    }

    private fun playTone(event: Int, durationMs: Int) {
        if (muted) return
        try {
            if (tone == null) tone = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            tone?.startTone(event, durationMs)
        } catch (_: Exception) {
            tone = null
        }
    }

    // ------------------------------------------------------------------
    // Background builders
    // ------------------------------------------------------------------

    private fun buildSkyline() {
        skylineFar.clear()
        skylineNear.clear()
        var x = 0f
        while (x < w * 2f) {
            val bw = w * (0.05f + Random.nextFloat() * 0.08f)
            val bh = h * (0.06f + Random.nextFloat() * 0.10f)
            skylineFar.add(Building(x, roadTop - bh, bw, bh))
            x += bw + w * Random.nextFloat() * 0.02f
        }
        x = 0f
        while (x < w * 2f) {
            val bw = w * (0.06f + Random.nextFloat() * 0.09f)
            val bh = h * (0.03f + Random.nextFloat() * 0.06f)
            skylineNear.add(Building(x, roadTop - bh, bw, bh))
            x += bw + w * 0.008f
        }
    }

    private fun buildClouds() {
        clouds.clear()
        for (i in 0 until 6) {
            clouds.add(
                floatArrayOf(
                    Random.nextFloat() * w,
                    roadTop * (0.12f + Random.nextFloat() * 0.55f),
                    0.6f + Random.nextFloat() * 0.9f
                )
            )
        }
    }

    private fun buildProps() {
        props.clear()
        val span = h - roadTop
        for (i in 0..3) {
            val yl = roadTop - h * 0.10f + i * (span / 4f) + span * 0.06f
            props.add(Prop(roadLeft * 0.42f, yl, true))
            props.add(Prop(w - roadLeft * 0.42f, yl + span * 0.125f, false))
        }
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    fun onTouchDown(x: Float, y: Float) {
        tX = x
        tY = y
        tActive = true
    }

    fun onTouchUp(x: Float, y: Float) {
        if (!tActive) return
        tActive = false
        val dx = x - tX
        val dy = y - tY
        val tap = abs(dx) < swipeThreshold && abs(dy) < swipeThreshold

        when (state) {
            State.MENU -> {
                if (tap && muteBtn.contains(x, y)) {
                    muted = !muted
                    savePrefs()
                } else {
                    startGame()
                }
            }
            State.RUNNING -> {
                if (tap && pauseBtn.contains(x, y)) {
                    state = State.PAUSED
                    return
                }
                if (tap && muteBtn.contains(x, y)) {
                    muted = !muted
                    savePrefs()
                    return
                }
                if (abs(dx) > abs(dy)) {
                    if (dx > 0f) moveLane(1) else moveLane(-1)
                } else {
                    if (dy < 0f) doJump() else doSlide()
                }
            }
            State.PAUSED -> state = State.RUNNING
            State.GAME_OVER -> if (gameOverTimer > 0.7f) startGame()
        }
    }

    private fun moveLane(dir: Int) {
        val target = (laneTo + dir).coerceIn(0, 2)
        if (target != laneTo) {
            laneFrom = laneTo
            laneTo = target
            laneT = 0f
        }
    }

    private fun doJump() {
        if (sliding) sliding = false
        if (!jumping) {
            jumping = true
            vy = jumpV
            queuedSlide = false
        }
    }

    private fun doSlide() {
        if (jumping) {
            vy = min(vy, -h * 2.2f) // fast fall, slide on landing
            queuedSlide = true
        } else {
            startSlide()
        }
    }

    private fun startSlide() {
        sliding = true
        slideTimer = 0.55f
    }

    private fun startGame() {
        obstacles.clear()
        coins.clear()
        particles.clear()
        popups.clear()
        score = 0f
        coinCount = 0
        elapsed = 0f
        spawnTimer = 1.0f
        coinTimer = 0.4f
        laneFrom = 1
        laneTo = 1
        laneT = 1f
        playerX = laneX[1]
        airY = 0f
        vy = 0f
        jumping = false
        sliding = false
        slideTimer = 0f
        queuedSlide = false
        crashed = false
        newBest = false
        gameOverTimer = 0f
        shakeTime = 0f
        state = State.RUNNING
        playTone(ToneGenerator.TONE_PROP_BEEP, 120)
    }

    // ------------------------------------------------------------------
    // Spawning
    // ------------------------------------------------------------------

    private fun pickType(): Int {
        val r = Random.nextFloat()
        return when {
            r < 0.34f -> Obstacle.TYPE_BARRIER
            r < 0.58f -> Obstacle.TYPE_BAR
            r < 0.82f -> Obstacle.TYPE_BLOCK
            else -> Obstacle.TYPE_TRAIN
        }
    }

    private fun spawnObstacles(diff: Float) {
        val lanes = mutableListOf(0, 1, 2)
        lanes.shuffle(Random)
        val count = if (Random.nextFloat() < 0.15f + 0.30f * diff) 2 else 1
        val bottomY = -h * 0.03f
        val created = ArrayList<Obstacle>(count)
        for (i in 0 until count) {
            created.add(Obstacle(pickType(), laneX[lanes[i]], bottomY, laneW, h))
        }
        // keep a generous vertical clearance so nothing unfair ever spawns
        var left = w
        var right = 0f
        for (o in created) {
            left = min(left, o.rect.left)
            right = max(right, o.rect.right)
        }
        tempRect.set(left, -h * 1.15f, right, h * 0.10f)
        for (o in obstacles) {
            if (RectF.intersects(tempRect, o.rect)) return
        }
        obstacles.addAll(created)
    }

    private fun spawnCoins() {
        val lane = Random.nextInt(3)
        val x = laneX[lane]
        val n = 5 + Random.nextInt(4)
        var y = -h * 0.12f
        for (i in 0 until n) {
            if (!coinBlocked(x, y)) coins.add(Coin(x, y, coinR))
            y -= h * 0.13f
        }
    }

    private fun coinBlocked(x: Float, y: Float): Boolean {
        val m = h * 0.06f
        tempRect.set(x - coinR - m, y - coinR - m, x + coinR + m, y + coinR + m)
        for (o in obstacles) {
            if (RectF.intersects(tempRect, o.rect)) return true
        }
        return false
    }

    // ------------------------------------------------------------------
    // Update
    // ------------------------------------------------------------------

    private fun playerHitbox(): RectF {
        val curH = if (sliding) playerH * 0.55f else playerH
        val bottom = baseY - airY
        playerRect.set(
            playerX - playerW * 0.38f,
            bottom - curH * 0.96f,
            playerX + playerW * 0.38f,
            bottom - h * 0.004f
        )
        return playerRect
    }

    private fun crash() {
        crashed = true
        state = State.GAME_OVER
        gameOverTimer = 0f
        shakeTime = 0.45f
        shakeMag = w * 0.022f
        val total = floor(score).toInt()
        if (total > best) {
            best = total
            newBest = true
            savePrefs()
        }
        for (i in 0 until 26) {
            val a = Random.nextFloat() * 6.283f
            val sp = h * (0.15f + Random.nextFloat() * 0.45f)
            particles.add(
                Particle(
                    playerX, baseY - playerH * 0.5f,
                    cos(a) * sp, sin(a) * sp - h * 0.2f,
                    0.5f + Random.nextFloat() * 0.5f, 1f,
                    w * (0.008f + Random.nextFloat() * 0.014f),
                    if (Random.nextBoolean()) Color.argb(255, 242, 227, 198)
                    else Color.argb(255, 255, 107, 74),
                    h * 2.2f
                )
            )
        }
        playTone(ToneGenerator.TONE_SUP_ERROR, 300)
    }

    private fun sparkle(x: Float, y: Float) {
        for (i in 0 until 8) {
            val a = Random.nextFloat() * 6.283f
            val sp = h * (0.08f + Random.nextFloat() * 0.22f)
            particles.add(
                Particle(
                    x, y, cos(a) * sp, sin(a) * sp,
                    0.3f + Random.nextFloat() * 0.3f, 0.6f,
                    w * 0.006f + Random.nextFloat() * w * 0.006f,
                    if (Random.nextBoolean()) Color.argb(255, 255, 201, 60)
                    else Color.argb(255, 255, 240, 180),
                    0f
                )
            )
        }
    }

    fun update(dt: Float) {
        if (w <= 0f || h <= 0f) return
        time += dt

        val worldMove: Float = when (state) {
            State.RUNNING -> scrollSpeed * dt
            State.MENU -> h * 0.12f * dt
            else -> 0f
        }
        bgOffset += worldMove

        if (state == State.RUNNING) {
            elapsed += dt
            val diff = min(elapsed / 70f, 1f)
            scrollSpeed = lerp(h * 0.52f, h * 1.02f, diff)

            // lane transition (smoothstep)
            laneT = min(laneT + dt / 0.13f, 1f)
            val s = laneT * laneT * (3f - 2f * laneT)
            playerX = lerp(laneX[laneFrom], laneX[laneTo], s)

            // jump physics
            if (jumping) {
                vy -= grav * dt
                airY += vy * dt
                if (airY <= 0f) {
                    airY = 0f
                    vy = 0f
                    jumping = false
                    if (queuedSlide) {
                        queuedSlide = false
                        startSlide()
                    }
                }
            }
            if (sliding) {
                slideTimer -= dt
                if (slideTimer <= 0f) sliding = false
            }
            runPhase += dt * (7f + 9f * diff)

            // world scroll
            val move = scrollSpeed * dt
            for (o in obstacles) o.rect.offset(0f, move)
            for (cn in coins) cn.y += move

            // spawns
            spawnTimer -= dt
            if (spawnTimer <= 0f) {
                val base = lerp(1.55f, 0.85f, diff)
                spawnTimer = base * (0.85f + Random.nextFloat() * 0.35f)
                spawnObstacles(diff)
            }
            coinTimer -= dt
            if (coinTimer <= 0f) {
                coinTimer = 1.1f + Random.nextFloat() * 0.6f
                spawnCoins()
            }

            // distance score
            score += move * 0.02f

            // obstacle cleanup / pass bonus / collision
            val box = playerHitbox()
            val oi = obstacles.iterator()
            while (oi.hasNext()) {
                val o = oi.next()
                if (o.rect.top > h + h * 0.12f) {
                    oi.remove()
                    continue
                }
                if (!o.passed && o.rect.top > baseY) {
                    o.passed = true
                    score += 5f
                }
                if (RectF.intersects(box, o.rect)) {
                    crash()
                    break
                }
            }

            // coin collection
            if (state == State.RUNNING) {
                val ci = coins.iterator()
                while (ci.hasNext()) {
                    val cn = ci.next()
                    if (cn.y - cn.r > h + h * 0.05f) {
                        ci.remove()
                        continue
                    }
                    tempRect.set(cn.x - cn.r, cn.y - cn.r, cn.x + cn.r, cn.y + cn.r)
                    if (RectF.intersects(box, tempRect)) {
                        ci.remove()
                        coinCount++
                        score += 10f
                        popups.add(Popup("+10", cn.x, cn.y))
                        sparkle(cn.x, cn.y)
                        playTone(ToneGenerator.TONE_CDMA_PIP, 90)
                    }
                }
            }
        } else if (state == State.MENU) {
            runPhase += dt * 7f
        } else if (state == State.GAME_OVER) {
            gameOverTimer += dt
        }

        // street props wrap
        for (p in props) {
            p.y += worldMove
            if (p.y > h + h * 0.22f) p.y -= (h - roadTop) + h * 0.22f
        }

        // particles & popups keep animating unless fully paused
        if (state != State.PAUSED) {
            val pi = particles.iterator()
            while (pi.hasNext()) {
                val p = pi.next()
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.vy += p.grav * dt
                p.life -= dt
                if (p.life <= 0f) pi.remove()
            }
            val qi = popups.iterator()
            while (qi.hasNext()) {
                val q = qi.next()
                q.y -= h * 0.09f * dt
                q.life -= dt
                if (q.life <= 0f) qi.remove()
            }
            if (shakeTime > 0f) shakeTime = (shakeTime - dt).coerceAtLeast(0f)
        }
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    // ------------------------------------------------------------------
    // Render
    // ------------------------------------------------------------------

    fun render(canvas: Canvas) {
        if (w <= 0f || h <= 0f) return

        canvas.save()
        if (shakeTime > 0f) {
            val m = shakeMag * (shakeTime / 0.45f)
            canvas.translate((Random.nextFloat() - 0.5f) * 2f * m, (Random.nextFloat() - 0.5f) * 2f * m)
        }

        drawSky(canvas)
        drawRoad(canvas)
        for (p in props) drawProp(canvas, p)
        for (o in obstacles) drawObstacle(canvas, o)
        for (cn in coins) drawCoin(canvas, cn)

        if (state == State.MENU) {
            val hop = abs(sin(time * 2.2f)) * h * 0.012f
            drawDummy(canvas, w * 0.5f, h * 0.47f - hop, h * 0.49f, 1.35f, runPhase, false, 0f, false)
        } else {
            drawDummy(canvas, playerX, baseY - airY, baseY, 1f, runPhase, sliding, airY, crashed)
        }

        drawParticles(canvas)
        canvas.restore()
        drawPopups(canvas)

        when (state) {
            State.MENU -> drawMenu(canvas)
            State.RUNNING -> drawHud(canvas)
            State.PAUSED -> {
                drawHud(canvas)
                drawCenterOverlay(canvas, "PAUSED", "TAP TO RESUME")
            }
            State.GAME_OVER -> {
                drawHud(canvas)
                drawGameOver(canvas)
            }
        }
    }

    // ----- background -----

    private fun drawSky(canvas: Canvas) {
        pFill.shader = skyShader
        canvas.drawRect(0f, 0f, w, roadTop + 2f, pFill)
        pFill.shader = glowShader
        val sx = w * 0.76f
        val sy = roadTop - h * 0.14f
        canvas.drawRect(sx - w * 0.3f, sy - w * 0.3f, sx + w * 0.3f, sy + w * 0.3f, pFill)
        pFill.shader = null
        pFill.color = Color.argb(255, 255, 216, 155)
        canvas.drawCircle(sx, sy, w * 0.075f, pFill)

        // clouds
        pFill.color = Color.argb(120, 255, 170, 160)
        val off = (bgOffset * 0.05f) % (w * 2f)
        for (cl in clouds) {
            val cw = w * 0.10f * cl[2]
            var cx = cl[0] - off
            if (cx < -cw * 4f) cx += w * 2f
            val cy = cl[1]
            canvas.drawOval(cx - cw, cy, cx + cw, cy + cw * 0.55f, pFill)
            canvas.drawOval(cx - cw * 0.4f, cy - cw * 0.45f, cx + cw * 0.5f, cy + cw * 0.25f, pFill)
            canvas.drawOval(cx + cw * 0.1f, cy - cw * 0.3f, cx + cw * 1.1f, cy + cw * 0.35f, pFill)
        }

        drawSkyline(canvas, skylineFar, 0.045f, Color.argb(160, 58, 40, 78))
        drawSkyline(canvas, skylineNear, 0.10f, Color.argb(220, 40, 30, 56))
    }

    private fun drawSkyline(canvas: Canvas, list: List<Building>, par: Float, color: Int) {
        pFill.color = color
        val off = (bgOffset * par) % (w * 2f)
        for (b in list) {
            var bx = b.x - off
            if (bx < -b.w) bx += w * 2f
            canvas.drawRect(bx, b.y, bx + b.w, roadTop, pFill)
        }
    }

    private fun drawRoad(canvas: Canvas) {
        // side ground
        pFill.color = Color.argb(255, 52, 42, 66)
        canvas.drawRect(0f, roadTop, w, h, pFill)
        // asphalt
        pFill.color = Color.argb(255, 58, 53, 71)
        canvas.drawRect(roadLeft, roadTop, roadRight, h, pFill)
        // glowing edges
        pFill.color = Color.argb(255, 255, 201, 107)
        canvas.drawRect(roadLeft - w * 0.006f, roadTop, roadLeft, h, pFill)
        canvas.drawRect(roadRight, roadTop, roadRight + w * 0.006f, h, pFill)
        // scrolling lane dashes
        pFill.color = Color.argb(150, 235, 230, 245)
        val period = h * 0.085f
        val dashH = h * 0.042f
        var y0 = roadTop + (bgOffset % period) - period
        for (lane in 1..2) {
            val x = roadLeft + laneW * lane
            var y = y0
            while (y < h) {
                canvas.drawRect(x - w * 0.004f, y, x + w * 0.004f, y + dashH, pFill)
                y += period
            }
        }
        // horizon edge
        pFill.color = Color.argb(255, 30, 24, 40)
        canvas.drawRect(0f, roadTop - h * 0.006f, w, roadTop, pFill)
    }

    private fun drawProp(canvas: Canvas, p: Prop) {
        val poleH = h * 0.15f
        val dir = if (p.left) 1f else -1f
        pStroke.color = Color.argb(220, 24, 20, 34)
        pStroke.strokeWidth = w * 0.008f
        canvas.drawLine(p.x, p.y, p.x, p.y - poleH, pStroke)
        canvas.drawLine(p.x, p.y - poleH, p.x + dir * w * 0.045f, p.y - poleH, pStroke)
        pFill.color = if ((time * 0.7f + p.x) % 7f < 6.5f) Color.argb(255, 255, 214, 130)
        else Color.argb(255, 120, 110, 100)
        canvas.drawCircle(p.x + dir * w * 0.045f, p.y - poleH + w * 0.006f, w * 0.012f, pFill)
        pFill.color = Color.argb(26, 255, 214, 130)
        stripePath.reset()
        stripePath.moveTo(p.x + dir * w * 0.045f, p.y - poleH + w * 0.014f)
        stripePath.lineTo(p.x + dir * w * 0.005f, p.y)
        stripePath.lineTo(p.x + dir * w * 0.085f, p.y)
        stripePath.close()
        canvas.drawPath(stripePath, pFill)
    }

    // ----- obstacles -----

    private fun drawObstacle(canvas: Canvas, o: Obstacle) {
        val r = o.rect
        when (o.type) {
            Obstacle.TYPE_BARRIER -> {
                pFill.color = Color.argb(255, 245, 242, 234)
                canvas.drawRoundRect(r, w * 0.012f, w * 0.012f, pFill)
                canvas.save()
                canvas.clipRect(r)
                pFill.color = Color.argb(255, 224, 69, 58)
                val sw = r.width() / 5f
                val skew = r.height() * 0.8f
                var sx = r.left - skew
                while (sx < r.right) {
                    stripePath.reset()
                    stripePath.moveTo(sx, r.bottom)
                    stripePath.lineTo(sx + sw, r.bottom)
                    stripePath.lineTo(sx + sw + skew, r.top)
                    stripePath.lineTo(sx + skew, r.top)
                    stripePath.close()
                    canvas.drawPath(stripePath, pFill)
                    sx += sw * 2f
                }
                canvas.restore()
                pStroke.color = Color.argb(255, 40, 32, 48)
                pStroke.strokeWidth = w * 0.005f
                canvas.drawRoundRect(r, w * 0.012f, w * 0.012f, pStroke)
                pFill.color = if ((time * 2.5f) % 1f < 0.5f) Color.argb(255, 255, 180, 60)
                else Color.argb(255, 110, 90, 60)
                canvas.drawCircle(r.centerX(), r.top - w * 0.014f, w * 0.011f, pFill)
            }
            Obstacle.TYPE_BAR -> {
                pStroke.color = Color.argb(200, 200, 195, 210)
                pStroke.strokeWidth = w * 0.004f
                canvas.drawLine(r.centerX() - r.width() * 0.3f, r.top, r.centerX() - r.width() * 0.3f, r.top - h * 0.05f, pStroke)
                canvas.drawLine(r.centerX() + r.width() * 0.3f, r.top, r.centerX() + r.width() * 0.3f, r.top - h * 0.05f, pStroke)
                pFill.color = Color.argb(255, 60, 50, 76)
                canvas.drawRoundRect(r, w * 0.01f, w * 0.01f, pFill)
                pFill.color = Color.argb(255, 255, 201, 60)
                val n = 3
                for (i in 0 until n) {
                    val cx = r.left + r.width() * ((i + 0.5f) / n)
                    val cy = r.centerY()
                    stripePath.reset()
                    stripePath.moveTo(cx - w * 0.018f, cy - h * 0.012f)
                    stripePath.lineTo(cx, cy + h * 0.008f)
                    stripePath.lineTo(cx + w * 0.018f, cy - h * 0.012f)
                    stripePath.lineTo(cx + w * 0.018f, cy - h * 0.026f)
                    stripePath.lineTo(cx, cy - h * 0.006f)
                    stripePath.lineTo(cx - w * 0.018f, cy - h * 0.026f)
                    stripePath.close()
                    canvas.drawPath(stripePath, pFill)
                }
                pStroke.color = Color.argb(255, 24, 20, 34)
                pStroke.strokeWidth = w * 0.005f
                canvas.drawRoundRect(r, w * 0.01f, w * 0.01f, pStroke)
            }
            Obstacle.TYPE_BLOCK -> {
                pFill.color = Color.argb(255, 94, 107, 124)
                canvas.drawRoundRect(r, w * 0.012f, w * 0.012f, pFill)
                pFill.color = Color.argb(255, 74, 84, 99)
                canvas.drawRect(r.left, r.top, r.right, r.top + r.height() * 0.18f, pFill)
                pFill.color = Color.argb(255, 70, 80, 96)
                for (i in 1..3) {
                    val x = r.left + r.width() * i / 4f
                    canvas.drawRect(x - w * 0.006f, r.top + r.height() * 0.22f, x + w * 0.006f, r.bottom - r.height() * 0.05f, pFill)
                }
                pFill.color = Color.argb(255, 30, 26, 38)
                canvas.drawCircle(r.left + r.width() * 0.18f, r.bottom, w * 0.013f, pFill)
                canvas.drawCircle(r.right - r.width() * 0.18f, r.bottom, w * 0.013f, pFill)
                pStroke.color = Color.argb(255, 24, 20, 34)
                pStroke.strokeWidth = w * 0.005f
                canvas.drawRoundRect(r, w * 0.012f, w * 0.012f, pStroke)
            }
            else -> { // TYPE_TRAIN
                pFill.color = Color.argb(255, 226, 87, 76)
                canvas.drawRoundRect(r, w * 0.03f, w * 0.03f, pFill)
                pFill.color = Color.argb(255, 242, 144, 127)
                canvas.drawRoundRect(
                    r.left + w * 0.01f, r.top + w * 0.012f,
                    r.right - w * 0.01f, r.top + r.height() * 0.16f,
                    w * 0.02f, w * 0.02f, pFill
                )
                pFill.color = Color.argb(255, 255, 239, 217)
                val cw = r.width() / 4f
                for (i in 0 until 3) {
                    val wx = r.left + cw * (i + 0.6f)
                    canvas.drawRoundRect(
                        wx, r.top + r.height() * 0.24f,
                        wx + cw * 0.55f, r.top + r.height() * 0.46f,
                        w * 0.008f, w * 0.008f, pFill
                    )
                }
                pFill.color = Color.argb(255, 255, 201, 60)
                canvas.drawRect(
                    r.left + w * 0.012f, r.top + r.height() * 0.55f,
                    r.right - w * 0.012f, r.top + r.height() * 0.63f, pFill
                )
                pFill.color = Color.argb(255, 255, 244, 200)
                canvas.drawCircle(r.left + r.width() * 0.18f, r.bottom - r.height() * 0.10f, w * 0.014f, pFill)
                canvas.drawCircle(r.right - r.width() * 0.18f, r.bottom - r.height() * 0.10f, w * 0.014f, pFill)
                pFill.color = Color.argb(255, 40, 32, 48)
                canvas.drawRoundRect(
                    r.left + w * 0.01f, r.bottom - r.height() * 0.045f,
                    r.right - w * 0.01f, r.bottom, w * 0.008f, w * 0.008f, pFill
                )
                pStroke.color = Color.argb(255, 40, 32, 48)
                pStroke.strokeWidth = w * 0.005f
                canvas.drawRoundRect(r, w * 0.03f, w * 0.03f, pStroke)
            }
        }
    }

    // ----- coins / fx -----

    private fun drawCoin(canvas: Canvas, cn: Coin) {
        val bob = sin(time * 5f + cn.phase) * h * 0.004f
        val y = cn.y + bob
        val spin = abs(cos(time * 3.5f + cn.phase))
        val rx = cn.r * (0.25f + 0.75f * spin)
        ovalRect.set(cn.x - rx, y - cn.r, cn.x + rx, y + cn.r)
        pFill.color = Color.argb(255, 255, 201, 60)
        canvas.drawOval(ovalRect, pFill)
        ovalRect.set(cn.x - rx * 0.55f, y - cn.r * 0.55f, cn.x + rx * 0.55f, y + cn.r * 0.55f)
        pFill.color = Color.argb(255, 255, 232, 140)
        canvas.drawOval(ovalRect, pFill)
        pStroke.color = Color.argb(255, 176, 126, 22)
        pStroke.strokeWidth = w * 0.003f
        ovalRect.set(cn.x - rx, y - cn.r, cn.x + rx, y + cn.r)
        canvas.drawOval(ovalRect, pStroke)
    }

    private fun drawParticles(canvas: Canvas) {
        for (p in particles) {
            val a = (p.life / p.maxLife).coerceIn(0f, 1f)
            pFill.color = p.color
            pFill.alpha = (a * 255f).toInt()
            canvas.drawCircle(p.x, p.y, p.size * (0.5f + 0.5f * a), pFill)
        }
        pFill.alpha = 255
    }

    private fun drawPopups(canvas: Canvas) {
        for (p in popups) {
            val a = (p.life / 0.8f).coerceIn(0f, 1f)
            setTextSize(w * 0.035f)
            pText.color = Color.argb((a * 255f).toInt(), 255, 214, 90)
            canvas.drawText(p.text, p.x, p.y, pText)
        }
    }

    // ----- the dummy -----

    private fun drawDummy(
        canvas: Canvas, x: Float, footY: Float, groundY: Float,
        scale: Float, phase: Float, slide: Boolean, air: Float, crashed: Boolean
    ) {
        val H = playerH * scale
        val W = playerW * scale
        val bodySkin = Color.argb(255, 242, 227, 198)
        val outline = Color.argb(255, 40, 32, 48)

        // shadow
        val sh = 1f / (1f + air / (h * 0.14f))
        pShadow.alpha = (130 * sh).toInt().coerceIn(0, 160)
        canvas.drawOval(x - W * 0.6f * sh, groundY - H * 0.03f, x + W * 0.6f * sh, groundY + H * 0.05f, pShadow)

        if (slide) {
            pFill.color = bodySkin
            canvas.drawRoundRect(x - W * 0.48f, footY - H * 0.34f, x + W * 0.48f, footY, W * 0.14f, W * 0.14f, pFill)
            pStroke.color = outline
            pStroke.strokeWidth = w * 0.004f
            canvas.drawRoundRect(x - W * 0.48f, footY - H * 0.34f, x + W * 0.48f, footY, W * 0.14f, W * 0.14f, pStroke)
            drawDisc(canvas, x, footY - H * 0.17f, W * 0.15f)
            drawHead(canvas, x + W * 0.10f, footY - H * 0.36f, W * 0.24f, false)
            pStroke.color = bodySkin
            pStroke.strokeWidth = W * 0.11f
            pStroke.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(x - W * 0.3f, footY - H * 0.12f, x - W * 0.5f, footY - H * 0.02f, pStroke)
            canvas.drawLine(x + W * 0.3f, footY - H * 0.12f, x + W * 0.55f, footY - H * 0.02f, pStroke)
            pStroke.color = Color.argb(110, 255, 255, 255)
            pStroke.strokeWidth = w * 0.004f
            canvas.drawLine(x - W * 0.9f, footY - H * 0.30f, x - W * 0.5f, footY - H * 0.30f, pStroke)
            canvas.drawLine(x - W * 1.0f, footY - H * 0.18f, x - W * 0.55f, footY - H * 0.18f, pStroke)
            return
        }

        canvas.save()
        if (crashed) canvas.rotate(26f, x, footY - H * 0.5f)

        val swing = sin(phase)
        // legs
        pStroke.color = bodySkin
        pStroke.strokeWidth = W * 0.11f
        pStroke.strokeCap = Paint.Cap.ROUND
        val legSwing = if (air > 0f) 0f else swing
        val liftA = if (air > 0f) H * 0.14f else max(0f, sin(phase)) * H * 0.05f
        val liftB = if (air > 0f) H * 0.10f else max(0f, -sin(phase)) * H * 0.05f
        canvas.drawLine(x - W * 0.10f, footY - H * 0.36f, x - W * 0.10f - legSwing * W * 0.22f, footY - liftA, pStroke)
        canvas.drawLine(x + W * 0.10f, footY - H * 0.36f, x + W * 0.10f + legSwing * W * 0.22f, footY - liftB, pStroke)
        // torso
        pFill.color = bodySkin
        canvas.drawRoundRect(x - W * 0.28f, footY - H * 0.80f, x + W * 0.28f, footY - H * 0.32f, W * 0.12f, W * 0.12f, pFill)
        pStroke.color = outline
        pStroke.strokeWidth = w * 0.004f
        canvas.drawRoundRect(x - W * 0.28f, footY - H * 0.80f, x + W * 0.28f, footY - H * 0.32f, W * 0.12f, W * 0.12f, pStroke)
        // crash-test disc on the back
        drawDisc(canvas, x, footY - H * 0.56f, W * 0.14f)
        // arms
        pStroke.color = bodySkin
        pStroke.strokeWidth = W * 0.09f
        if (air > 0f) {
            canvas.drawLine(x - W * 0.26f, footY - H * 0.74f, x - W * 0.40f, footY - H * 0.92f, pStroke)
            canvas.drawLine(x + W * 0.26f, footY - H * 0.74f, x + W * 0.40f, footY - H * 0.92f, pStroke)
        } else {
            val armSwing = -swing
            canvas.drawLine(x - W * 0.26f, footY - H * 0.74f, x - W * 0.26f - armSwing * W * 0.20f, footY - H * 0.48f, pStroke)
            canvas.drawLine(x + W * 0.26f, footY - H * 0.74f, x + W * 0.26f + armSwing * W * 0.20f, footY - H * 0.48f, pStroke)
        }
        // head
        drawHead(canvas, x, footY - H * 0.94f, W * 0.24f, crashed)
        canvas.restore()
    }

    private fun drawHead(canvas: Canvas, cx: Float, cy: Float, r: Float, crashed: Boolean) {
        val outline = Color.argb(255, 40, 32, 48)
        pFill.color = Color.argb(255, 242, 227, 198)
        canvas.drawCircle(cx, cy, r, pFill)
        pStroke.color = outline
        pStroke.strokeWidth = w * 0.004f
        canvas.drawCircle(cx, cy, r, pStroke)
        // helmet dome
        ovalRect.set(cx - r * 1.12f, cy - r * 1.12f, cx + r * 1.12f, cy + r * 1.12f)
        pFill.color = Color.argb(255, 255, 107, 74)
        canvas.drawArc(ovalRect, 180f, 180f, true, pFill)
        // helmet stripe
        pFill.color = Color.argb(255, 255, 235, 220)
        canvas.drawRect(cx - r * 0.28f, cy - r * 1.10f, cx + r * 0.28f, cy - r * 0.72f, pFill)
        // face
        pFill.color = outline
        if (crashed) {
            pStroke.color = outline
            pStroke.strokeWidth = w * 0.004f
            canvas.drawLine(cx - r * 0.5f, cy - r * 0.1f, cx - r * 0.2f, cy + r * 0.2f, pStroke)
            canvas.drawLine(cx - r * 0.2f, cy - r * 0.1f, cx - r * 0.5f, cy + r * 0.2f, pStroke)
            canvas.drawLine(cx + r * 0.2f, cy - r * 0.1f, cx + r * 0.5f, cy + r * 0.2f, pStroke)
            canvas.drawLine(cx + r * 0.5f, cy - r * 0.1f, cx + r * 0.2f, cy + r * 0.2f, pStroke)
            pFill.color = Color.argb(255, 255, 201, 60)
            for (i in 0 until 3) {
                val a = time * 4f + i * 2.094f
                canvas.drawCircle(cx + cos(a) * r * 1.7f, cy - r * 0.4f + sin(a) * r * 0.7f, r * 0.16f, pFill)
            }
        } else {
            canvas.drawCircle(cx - r * 0.38f, cy + r * 0.05f, r * 0.10f, pFill)
            canvas.drawCircle(cx + r * 0.38f, cy + r * 0.05f, r * 0.10f, pFill)
            pStroke.color = outline
            pStroke.strokeWidth = w * 0.003f
            canvas.drawLine(cx - r * 0.25f, cy + r * 0.45f, cx + r * 0.25f, cy + r * 0.45f, pStroke)
        }
    }

    private fun drawDisc(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        ovalRect.set(cx - r, cy - r, cx + r, cy + r)
        pFill.color = Color.argb(255, 255, 201, 60)
        canvas.drawArc(ovalRect, 45f, 90f, true, pFill)
        canvas.drawArc(ovalRect, 225f, 90f, true, pFill)
        pFill.color = Color.argb(255, 30, 26, 38)
        canvas.drawArc(ovalRect, 135f, 90f, true, pFill)
        canvas.drawArc(ovalRect, 315f, 90f, true, pFill)
        pStroke.color = Color.argb(255, 30, 26, 38)
        pStroke.strokeWidth = w * 0.003f
        canvas.drawCircle(cx, cy, r, pStroke)
    }

    // ----- HUD & overlays -----

    private fun setTextSize(px: Float) {
        pText.textSize = px
        pTextShadow.textSize = px
        pTextStroke.textSize = px
        pTextStroke.strokeWidth = px * 0.12f
    }

    private fun drawTextC(
        canvas: Canvas, text: String, cx: Float, cy: Float,
        size: Float, color: Int, outlined: Boolean = false
    ) {
        setTextSize(size)
        if (outlined) {
            pTextStroke.color = Color.argb(230, 26, 20, 34)
            canvas.drawText(text, cx, cy, pTextStroke)
        }
        pText.color = color
        canvas.drawText(text, cx, cy, pText)
    }

    private fun drawIconButton(canvas: Canvas, r: RectF, isPause: Boolean) {
        pFill.color = Color.argb(90, 20, 16, 30)
        canvas.drawRoundRect(r, r.width() * 0.25f, r.width() * 0.25f, pFill)
        pStroke.color = Color.argb(160, 255, 255, 255)
        pStroke.strokeWidth = w * 0.003f
        canvas.drawRoundRect(r, r.width() * 0.25f, r.width() * 0.25f, pStroke)
        pFill.color = Color.WHITE
        val cx = r.centerX()
        val cy = r.centerY()
        val s = r.width() * 0.16f
        if (isPause) {
            canvas.drawRoundRect(cx - s * 1.2f, cy - s, cx - s * 0.4f, cy + s, s * 0.2f, s * 0.2f, pFill)
            canvas.drawRoundRect(cx + s * 0.4f, cy - s, cx + s * 1.2f, cy + s, s * 0.2f, s * 0.2f, pFill)
        } else {
            stripePath.reset()
            stripePath.moveTo(cx - s * 1.4f, cy - s * 0.5f)
            stripePath.lineTo(cx - s * 0.5f, cy - s * 0.5f)
            stripePath.lineTo(cx + s * 0.4f, cy - s * 1.3f)
            stripePath.lineTo(cx + s * 0.4f, cy + s * 1.3f)
            stripePath.lineTo(cx - s * 0.5f, cy + s * 0.5f)
            stripePath.lineTo(cx - s * 1.4f, cy + s * 0.5f)
            stripePath.close()
            canvas.drawPath(stripePath, pFill)
            if (muted) {
                pStroke.color = Color.argb(255, 255, 107, 74)
                pStroke.strokeWidth = w * 0.008f
                canvas.drawLine(cx + s * 0.7f, cy - s * 0.9f, cx + s * 1.6f, cy + s * 0.9f, pStroke)
            } else {
                pStroke.color = Color.WHITE
                pStroke.strokeWidth = w * 0.005f
                canvas.drawLine(cx + s * 0.7f, cy - s * 0.5f, cx + s * 0.7f, cy + s * 0.5f, pStroke)
                canvas.drawLine(cx + s * 1.1f, cy - s * 0.9f, cx + s * 1.1f, cy + s * 0.9f, pStroke)
            }
        }
    }

    private fun drawHud(canvas: Canvas) {
        pText.textAlign = Paint.Align.LEFT
        setTextSize(w * 0.085f)
        val sc = floor(score).toInt().toString()
        canvas.drawText(sc, w * 0.049f, h * 0.089f, pTextShadow)
        pText.color = Color.WHITE
        canvas.drawText(sc, w * 0.045f, h * 0.085f, pText)
        // coin counter
        val cy = h * 0.085f + w * 0.055f
        pFill.color = Color.argb(255, 255, 201, 60)
        canvas.drawCircle(w * 0.061f, cy - w * 0.012f, w * 0.016f, pFill)
        pFill.color = Color.argb(255, 255, 232, 140)
        canvas.drawCircle(w * 0.061f, cy - w * 0.012f, w * 0.008f, pFill)
        setTextSize(w * 0.045f)
        pText.color = Color.argb(255, 255, 214, 90)
        canvas.drawText("x $coinCount", w * 0.085f, cy, pText)
        pText.textAlign = Paint.Align.CENTER
        drawIconButton(canvas, muteBtn, false)
        drawIconButton(canvas, pauseBtn, true)
    }

    private fun drawMenu(canvas: Canvas) {
        drawTextC(canvas, "DUMMY", w * 0.5f, h * 0.20f, w * 0.155f, Color.argb(255, 255, 201, 60), true)
        drawTextC(canvas, "SURFERS", w * 0.5f, h * 0.30f, w * 0.115f, Color.WHITE, true)
        if (best > 0) {
            drawTextC(canvas, "BEST  $best", w * 0.5f, h * 0.385f, w * 0.045f, Color.argb(255, 255, 214, 90))
        }
        val pulse = 0.55f + 0.45f * sin(time * 3.2f)
        setTextSize(w * 0.062f)
        pText.color = Color.argb((pulse * 255f).toInt(), 255, 255, 255)
        canvas.drawText("TAP TO START", w * 0.5f, h * 0.585f, pText)

        pFill.color = Color.argb(120, 20, 16, 30)
        val panelTop = h * 0.68f
        canvas.drawRoundRect(w * 0.08f, panelTop, w * 0.92f, h * 0.90f, w * 0.04f, w * 0.04f, pFill)
        setTextSize(w * 0.038f)
        pText.color = Color.argb(235, 245, 240, 255)
        canvas.drawText("SWIPE LEFT / RIGHT - CHANGE LANE", w * 0.5f, panelTop + h * 0.05f, pText)
        canvas.drawText("SWIPE UP - JUMP THE BARRIERS", w * 0.5f, panelTop + h * 0.095f, pText)
        canvas.drawText("SWIPE DOWN - SLIDE UNDER BARS", w * 0.5f, panelTop + h * 0.14f, pText)
        setTextSize(w * 0.032f)
        pText.color = Color.argb(200, 255, 214, 90)
        canvas.drawText("COLLECT COINS - BEAT YOUR BEST", w * 0.5f, panelTop + h * 0.19f, pText)
        drawIconButton(canvas, muteBtn, false)
    }

    private fun drawCenterOverlay(canvas: Canvas, title: String, subtitle: String) {
        pFill.color = Color.argb(140, 15, 10, 22)
        canvas.drawRect(0f, 0f, w, h, pFill)
        drawTextC(canvas, title, w * 0.5f, h * 0.42f, w * 0.12f, Color.WHITE, true)
        val pulse = 0.55f + 0.45f * sin(time * 3.2f)
        setTextSize(w * 0.05f)
        pText.color = Color.argb((pulse * 255f).toInt(), 255, 255, 255)
        canvas.drawText(subtitle, w * 0.5f, h * 0.52f, pText)
    }

    private fun drawGameOver(canvas: Canvas) {
        if (gameOverTimer < 0.35f) return
        pFill.color = Color.argb(170, 15, 10, 22)
        canvas.drawRect(0f, 0f, w, h, pFill)
        drawTextC(canvas, "CRASHED!", w * 0.5f, h * 0.30f, w * 0.14f, Color.argb(255, 255, 107, 74), true)
        drawTextC(canvas, "SCORE  ${floor(score).toInt()}", w * 0.5f, h * 0.42f, w * 0.075f, Color.WHITE)
        drawTextC(canvas, "COINS  $coinCount", w * 0.5f, h * 0.50f, w * 0.06f, Color.argb(255, 255, 214, 90))
        if (newBest) {
            val pulse = 0.6f + 0.4f * sin(time * 5f)
            setTextSize(w * 0.055f)
            pText.color = Color.argb((pulse * 255f).toInt(), 255, 201, 60)
            canvas.drawText("NEW BEST!", w * 0.5f, h * 0.58f, pText)
        } else {
            drawTextC(canvas, "BEST  $best", w * 0.5f, h * 0.58f, w * 0.05f, Color.argb(200, 235, 230, 245))
        }
        if (gameOverTimer > 0.7f) {
            val pulse = 0.55f + 0.45f * sin(time * 3.2f)
            setTextSize(w * 0.055f)
            pText.color = Color.argb((pulse * 255f).toInt(), 255, 255, 255)
            canvas.drawText("TAP TO RETRY", w * 0.5f, h * 0.70f, pText)
        }
    }
}
