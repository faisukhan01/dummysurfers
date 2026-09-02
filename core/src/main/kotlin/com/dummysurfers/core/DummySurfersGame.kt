package com.dummysurfers.core

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.dummysurfers.core.audio.AudioManager
import com.dummysurfers.core.config.GameConfig
import com.dummysurfers.core.entities.Chaser
import com.dummysurfers.core.entities.CharacterDef
import com.dummysurfers.core.entities.ObstacleKind
import com.dummysurfers.core.entities.Player
import com.dummysurfers.core.state.PlayerState
import com.dummysurfers.core.gfx.TextureGen
import com.dummysurfers.core.gfx3.Assets3D
import com.dummysurfers.core.gfx3.DogRig
import com.dummysurfers.core.gfx3.Game3DRenderer
import com.dummysurfers.core.gfx3.HumanoidRig
import com.dummysurfers.core.gfx3.Pose
import com.dummysurfers.core.gfx3.RigPalettes
import com.dummysurfers.core.gfx3.Scene3D
import com.dummysurfers.core.input.SwipeDetector
import com.dummysurfers.core.particles.Particles
import com.dummysurfers.core.state.GameEvent
import com.dummysurfers.core.state.GameState
import com.dummysurfers.core.state.MenuPanel
import com.dummysurfers.core.state.SaveManager
import com.dummysurfers.core.state.ShopTab
import com.dummysurfers.core.systems.Difficulty
import com.dummysurfers.core.systems.Spawner
import com.dummysurfers.core.ui.UiController
import com.dummysurfers.core.ui.UiTheme
import com.dummysurfers.core.utils.Mathz
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * DUMMY SURFERS BY FSK — main game orchestrator.
 * True-3D world (Scene3D) + 2D UI overlay. Owns the loop, state machine,
 * gameplay systems and bridges them to UI/audio.
 */
class DummySurfersGame : com.badlogic.gdx.ApplicationAdapter() {

    lateinit var batch: SpriteBatch; private set
    private lateinit var sr: ShapeRenderer
    private val camera = OrthographicCamera()
    private val theme = UiTheme()
    private lateinit var ui: UiController
    private val audio = AudioManager()

    // 3D world (lazy: constructing a PerspectiveCamera touches natives, which are
    // only loaded once the backend Application exists)
    private val scene: Scene3D by lazy { Scene3D() }
    private lateinit var game3d: Game3DRenderer
    private lateinit var playerRig: HumanoidRig
    private lateinit var guardRig: HumanoidRig
    private lateinit var dogRig: DogRig

    private val save: SaveManager by lazy { SaveManager() }
    private val spawner = Spawner()
    private val player = Player()
    private val chaser = Chaser()
    private val particles = Particles(340)
    private val swipe = SwipeDetector(object : SwipeDetector.Listener {
        override fun onSwipe(dir: SwipeDetector.Direction) = handleSwipe(dir)
    })

    private val rng = Random(System.nanoTime())
    private var state: GameState = GameState.MENU
    private var menuPanel = MenuPanel.NONE
    private var shopTab = ShopTab.CHARACTERS

    /** Non-blocking first-run hints (left,right,jump,slide). true = still pending. */
    private val tutorialHints = BooleanArray(4)

    // ── Run state ──────────────────────────────────────────────────────
    private var score = 0
    private var runCoins = 0
    private var distance = 0f
    private var jumps = 0
    private var slides = 0
    private var powerupsUsed = 0
    private var nearMisses = 0
    private val activePowerups = FloatArray(5)
    private val powerupTotal = FloatArray(5)
    private var displayScore = 0
    private var newBest = false
    private var coinStreak = 0
    private var coinStreakTimer = 0f
    private var multiplier = 1

    // ── FX state ───────────────────────────────────────────────────────
    private var shake = 0f
    private var time = 0f
    private var dyingTimer = 0f
    private var menuDim = 0.55f
    private var lastStepParity = 0

    // letterboxed 2D UI stage over a full-window 3D world
    private var vpX = 0; private var vpY = 0; private var vpW = 0; private var vpH = 0
    private var screenW = 0; private var screenH = 0
    private var viewScale = 1f
    private val projOut = FloatArray(2)
    private val virtOut = FloatArray(2)
    private val layout = com.badlogic.gdx.graphics.g2d.GlyphLayout()

    private lateinit var character: CharacterDef

    override fun create() {
        batch = SpriteBatch()
        sr = ShapeRenderer()
        sr.setAutoShapeType(true)
        camera.setToOrtho(false, GameConfig.VIRTUAL_WIDTH, GameConfig.VIRTUAL_HEIGHT)
        Assets3D.create()
        scene.create()
        TextureGen.generate()
        theme.create()
        playerRig = HumanoidRig(RigPalettes.JACK)
        guardRig = HumanoidRig(RigPalettes.GUARD)
        dogRig = DogRig()
        game3d = Game3DRenderer(scene, playerRig, guardRig, dogRig)
        game3d.create()
        ui = UiController(theme)
        ui.bridge = bridge
        audio.start()
        audio.setMusic(save.musicOn)
        audio.setSfx(save.sfxOn)
        character = CharacterDef.byId(save.selectedCharacter)
        spawner.reset()
        Gdx.input.inputProcessor = InputMultiplexer(ui, swipe)
        resize(Gdx.graphics.width, Gdx.graphics.height)
    }

    override fun resize(width: Int, height: Int) {
        screenW = width; screenH = height
        viewScale = min(width / GameConfig.VIRTUAL_WIDTH, height / GameConfig.VIRTUAL_HEIGHT)
        vpW = (GameConfig.VIRTUAL_WIDTH * viewScale).toInt()
        vpH = (GameConfig.VIRTUAL_HEIGHT * viewScale).toInt()
        vpX = (width - vpW) / 2
        vpY = (height - vpH) / 2
        camera.setToOrtho(false, GameConfig.VIRTUAL_WIDTH, GameConfig.VIRTUAL_HEIGHT)
        batch.projectionMatrix = camera.combined
        sr.projectionMatrix = camera.combined
    }

    // ── Main loop ──────────────────────────────────────────────────────
    override fun render() {
        val rawDt = Gdx.graphics.deltaTime.coerceIn(0.001f, 0.05f)
        val dt = rawDt * if (state == GameState.DYING) 0.4f else 1f
        time += rawDt

        update(dt)
        draw(rawDt)
    }

    private fun update(dt: Float) {
        swipe.pollKeyboard()
        ui.update(dt)
        particles.update(dt)

        shake = max(0f, shake - dt * 40f)

        when (state) {
            GameState.MENU -> updateAttract(dt)
            GameState.PLAYING -> updateRun(dt)
            GameState.DYING -> updateDying(dt)
            else -> {}
        }

        audio.setIntensity(if (state == GameState.PLAYING) Difficulty.speedPct(distance) else 0.25f)
    }

    private fun updateAttract(dt: Float) {
        val speed = 6f
        scene.update(dt * speed)
        player.runPhase += dt * speed * 1.55f
        chaser.active = false
        menuDim = 0.55f
    }

    private fun updateRun(dt: Float) {
        menuDim = 0f
        val speed = Difficulty.speed(distance) * (if (activePowerups[3] > 0f) GameConfig.BOOST_SPEED_MULT else 1f)

        // world + spawner scroll
        val scroll = speed * dt
        distance += scroll
        scene.update(scroll)
        spawner.addDistance(scroll)
        spawner.speedHint = speed
        spawner.update(dt, speed, scroll, player.lane, attractMode = false)

        // support: ramps / train roofs lift the ground under the player
        player.groundY = computeSupport()

        // player physics
        val grounded = player.update(dt, speed)
        if (grounded) {
            audio.play(GameEvent.LAND)
            emit3D(player.x, player.groundY + player.jumpY + 0.05f, 0f, { px, py ->
                particles.burst(px, py, 5, Color(0xcbb9a4ff.toInt()), 130f, 4f, grav = 500f, life = 0.35f)
            })
        }

        // footsteps synced to run cycle
        if (player.stepParity != lastStepParity && player.state == PlayerState.RUNNING) {
            lastStepParity = player.stepParity
            audio.play(GameEvent.FOOTSTEP)
            if (rng.nextFloat() < 0.5f) {
                emit3D(player.x, player.groundY + 0.05f, 0f, { px, py ->
                    particles.burst(px, py, 2, Color(0xbfae9aff.toInt()), 90f, 3f, grav = 420f, life = 0.3f)
                })
            }
        }

        // score & multiplier
        score += (scroll * GameConfig.DISTANCE_SCORE_PER_METER).toInt() * multiplier
        multiplier = Difficulty.multiplier(distance)

        // power-up timers
        for (i in 0 until 5) {
            if (activePowerups[i] > 0f) {
                activePowerups[i] -= dt
                if (activePowerups[i] <= 0f) activePowerups[i] = 0f
            }
        }

        // chaser behavior
        chaser.update(dt, speed)

        // magnet flight
        if (activePowerups[0] > 0f) {
            for (c in spawner.coins) {
                if (!c.collected && c.z < GameConfig.MAGNET_RANGE_Z && c.z > -1f) c.magnet = true
            }
        }
        for (c in spawner.coins) {
            if (c.magnet && !c.collected) {
                c.x += (player.x - c.x) * min(1f, dt * 9f)
                c.y += (player.groundY + 0.9f - c.y) * min(1f, dt * 9f)
                c.z -= 14f * dt
            }
        }

        // trains: horn + approach rumble
        for (t in spawner.trains) {
            if (t.kind == 2 && !t.hornDone && t.z < GameConfig.TRAIN_HORN_DISTANCE) {
                t.hornDone = true
                audio.play(GameEvent.HORN)
            }
            if (t.kind == 1 && t.z < 30f && t.z > 2f && rng.nextFloat() < 0.06f) {
                shake = max(shake, 1.2f)
            }
        }

        // collisions & pickups
        handleCollisions()
        handleCoins(dt)
        handlePowerups()

        // top-speed juice
        val topSpeed = Difficulty.speed(distance) > GameConfig.MAX_SPEED * 0.88f
        if (activePowerups[3] > 0f || topSpeed) {
            particles.streak(GameConfig.VIRTUAL_WIDTH, GameConfig.VIRTUAL_HEIGHT, if (activePowerups[3] > 0f) 3 else 2)
        }

        // trail cosmetics
        if (save.trail > 0 && player.state != PlayerState.SLIDING) {
            val col = when (save.trail) {
                1 -> Color(0xffc93cff.toInt())
                2 -> Color(0xf97316ff.toInt())
                else -> Mathz.hsv(time * 140f, 0.85f, 1f, 1f, Color())
            }
            emit3D(player.x, player.groundY + player.jumpY + 0.1f, 0f, { px, py ->
                particles.burst(px, py, 1, col, 40f, 5f, grav = -60f, life = 0.4f)
            })
        }

        coinStreakTimer -= dt
        if (coinStreakTimer <= 0f) coinStreak = 0
    }

    private fun updateDying(dt: Float) {
        scene.update(dt * 4f)
        player.update(dt, 0f)
        dyingTimer += dt
        if (dyingTimer >= 1.4f) {
            finalizeRun()
        }
    }

    /** Project a 3D world point to 2D virtual stage coords, then run [emit]. */
    private fun emit3D(x: Float, y: Float, z: Float, emit: (Float, Float) -> Unit) {
        scene.projectTo(x, y, z, projOut)
        virtOut[0] = (projOut[0] - vpX) / viewScale
        virtOut[1] = GameConfig.VIRTUAL_HEIGHT - (projOut[1] - vpY) / viewScale
        emit(virtOut[0], virtOut[1])
    }

    /** Support height (ground/roof) under the player right now. */
    private fun computeSupport(): Float {
        val absBottom = player.groundY + player.jumpY
        var support = 0f
        val px = player.x
        // ramps: span [r.z, r.z + RAMP_LENGTH] rising away; player on it when r.z ∈ [-len, 0]
        for (r in spawner.ramps) {
            val rx = r.lane * GameConfig.LANE_WIDTH
            if (abs(px - rx) > GameConfig.PLAYER_HALF_WIDTH + 0.7f) continue
            if (r.z <= 0f && r.z >= -GameConfig.RAMP_LENGTH) {
                val f = (-r.z / GameConfig.RAMP_LENGTH).coerceIn(0f, 1f)
                val h = GameConfig.TRAIN_HEIGHT * f
                if (h <= absBottom + 0.5f && h > support) support = h
            }
        }
        // train roofs (only ramped parked trains are climbable): span [t.z, t.z + len]
        for (t in spawner.trains) {
            if (!t.hasRoof) continue
            if (!t.lanes.contains(player.lane)) continue
            val zNear = t.z
            val zFar = t.z + t.totalLength
            if (0f >= zNear && 0f <= zFar) {
                if (GameConfig.TRAIN_HEIGHT <= absBottom + 0.5f && GameConfig.TRAIN_HEIGHT > support) support = GameConfig.TRAIN_HEIGHT
            }
        }
        return support
    }

    // ── Gameplay interactions ──────────────────────────────────────────
    private fun handleSwipe(dir: SwipeDetector.Direction) {
        when (state) {
            GameState.PLAYING -> {
                performSwipe(dir)
                val idx = when (dir) {
                    SwipeDetector.Direction.LEFT -> 0
                    SwipeDetector.Direction.RIGHT -> 1
                    SwipeDetector.Direction.UP -> 2
                    else -> 3
                }
                if (tutorialHints[idx]) {
                    tutorialHints[idx] = false
                    if (tutorialHints.all { !it }) save.markTutorialDone()
                }
            }
            else -> {}
        }
    }

    private fun performSwipe(dir: SwipeDetector.Direction) {
        when (dir) {
            SwipeDetector.Direction.LEFT -> { player.switchLane(-1); audio.play(GameEvent.LANE) }
            SwipeDetector.Direction.RIGHT -> { player.switchLane(1); audio.play(GameEvent.LANE) }
            SwipeDetector.Direction.UP -> {
                if (player.startJump(activePowerups[4] > 0f)) {
                    jumps++
                    audio.play(GameEvent.JUMP)
                }
            }
            SwipeDetector.Direction.DOWN -> {
                player.startSlide()
                slides++
                audio.play(GameEvent.SLIDE)
            }
        }
    }

    private fun playerOverlaps(boxX: Float, boxHalfW: Float, zNear: Float, zFar: Float, yLo: Float, yHi: Float): Boolean {
        val px = player.x
        if (abs(px - boxX) > GameConfig.PLAYER_HALF_WIDTH + boxHalfW) return false
        val absBottom = player.groundY + player.jumpY
        if (absBottom > yHi || absBottom + player.height < yLo) return false
        return 0.5f >= zNear && -0.5f <= zFar
    }

    private fun handleCollisions() {
        if (player.state == PlayerState.DEAD || player.invulnTimer > 0f) return
        val onRoof = player.groundY >= GameConfig.TRAIN_HEIGHT - 0.05f

        // trains — skip the roofed train we're standing on
        for (t in spawner.trains) {
            val zNear = t.z                 // leading face (meets the player first)
            val zFar = t.z + t.totalLength  // tail, extends away
            if (zNear > 0.5f || zFar < -0.5f) continue
            if (t.hasRoof && onRoof && t.lanes.contains(player.lane)) continue
            for (lane in t.lanes) {
                val laneX = lane * GameConfig.LANE_WIDTH
                if (playerOverlaps(laneX, GameConfig.TRAIN_WIDTH / 2f, zNear, zFar, 0f, GameConfig.TRAIN_HEIGHT)) {
                    onHit(laneX)
                    return
                }
            }
            if (!t.passedNear && zNear < 0.6f && zNear > 0.0f) {
                t.passedNear = true
                val minDist = t.lanes.minOf { abs(it * GameConfig.LANE_WIDTH - player.x) }
                if (minDist < GameConfig.NEAR_MISS_DISTANCE + GameConfig.TRAIN_WIDTH / 2f && minDist > GameConfig.PLAYER_HALF_WIDTH + GameConfig.TRAIN_WIDTH / 2f - 0.12f) {
                    registerNearMiss()
                }
            }
        }

        // obstacles
        for (o in spawner.obstacles) {
            if (o.z > 0.85f || o.z < -0.35f) continue
            if (o.passed && o.z < -0.35f) continue
            val affects = o.kind == ObstacleKind.GATE || o.kind == ObstacleKind.FENCE_FULL || o.lane == player.lane ||
                    abs(o.lane * GameConfig.LANE_WIDTH - player.x) < 1.0f
            if (!affects) {
                if (!o.passed && o.z < 0.6f) { o.passed = true }
                continue
            }
            if (onRoof) continue // obstacles live on the ground plane
            val boxX = o.lane * GameConfig.LANE_WIDTH
            val hit = when (o.kind) {
                ObstacleKind.LOW_BARRIER -> playerOverlaps(boxX, 1.0f, o.z - 0.35f, o.z + 0.35f, 0f, 0.95f)
                ObstacleKind.FENCE_FULL -> playerOverlaps(player.x, 1.0f, o.z - 0.35f, o.z + 0.35f, 0f, 0.95f)
                ObstacleKind.HIGH_BARRIER -> playerOverlaps(boxX, 1.1f, o.z - 0.35f, o.z + 0.35f, 1.2f, 2.3f)
                ObstacleKind.GATE -> playerOverlaps(player.x, 1.0f, o.z - 0.4f, o.z + 0.4f, 1.2f, 2.3f)
                ObstacleKind.BLOCKADE -> playerOverlaps(boxX, 1.0f, o.z - 0.4f, o.z + 0.4f, 0f, 2.4f)
            }
            if (hit) { onHit(boxX); return }
            if (!o.passed && o.z < 0.6f) {
                o.passed = true
                val lateral = abs(boxX - player.x)
                if (lateral < GameConfig.NEAR_MISS_DISTANCE + 1.0f && lateral > 0.9f) registerNearMiss()
            }
        }
    }

    private fun registerNearMiss() {
        nearMisses++
        val pts = GameConfig.NEAR_MISS_SCORE * multiplier
        score += pts
        shake = max(shake, GameConfig.NEAR_MISS_SHAKE * 0.6f)
        audio.play(GameEvent.NEAR_MISS)
        particles.text(GameConfig.VIRTUAL_WIDTH / 2f, GameConfig.VIRTUAL_HEIGHT * 0.6f, "+$pts", Color(0x8ff2e2ff.toInt()), 22f)
        if (rng.nextFloat() < 0.65f) chaser.trigger(GameConfig.CHASER_NEARMISS_TIME)
    }

    private fun onHit(hitX: Float) {
        if (activePowerups[2] > 0f) {
            // shield absorbs one hit
            activePowerups[2] = 0f
            player.invulnTimer = GameConfig.SHIELD_INVULN
            audio.play(GameEvent.SHIELD_BREAK)
            vibrate(60)
            emit3D(player.x, player.groundY + player.jumpY + 1.1f, 0f, { px, py ->
                particles.burst(px, py, 16, Color(0x2dd4bfff.toInt()), 320f, 6f, shape = 1)
            })
            return
        }
        player.state = PlayerState.DEAD
        player.deadTimer = 0f
        state = GameState.DYING
        dyingTimer = 0f
        shake = GameConfig.CRASH_SHAKE
        audio.play(GameEvent.CRASH)
        vibrate(120)
        emit3D(player.x, player.groundY + player.jumpY + 1.1f, 0f, { px, py ->
            particles.burst(px, py, 26, Color(0xef4444ff.toInt()), 420f, 7f, shape = 1)
            particles.burst(px, py, 14, Color(0xf2a75bff.toInt()), 300f, 5f)
        })
    }

    private fun handleCoins(dt: Float) {
        for (c in spawner.coins) {
            if (c.collected) continue
            if (c.z < -0.8f || c.z > 1.0f) continue
            val dx = abs(c.x - player.x)
            val playerMid = player.groundY + player.jumpY + player.height * 0.5f
            if (dx < 0.85f && c.y <= playerMid + 0.9f && c.y >= playerMid - 1.3f) {
                c.collected = true
                runCoins++
                val pts = GameConfig.COIN_VALUE * multiplier * (if (activePowerups[1] > 0f) 2 else 1)
                score += pts
                coinStreak++
                coinStreakTimer = 0.8f
                audio.play(GameEvent.COIN, coinStreak.toFloat())
                emit3D(c.x, c.y, c.z, { px, py ->
                    particles.burst(px, py, 7, Color(0xffc93cff.toInt()), 240f, 5f, shape = 1, life = 0.45f)
                    particles.text(px, py + 20f, "+${GameConfig.COIN_VALUE * multiplier}", Color(0xffc93cff.toInt()), 16f)
                })
            }
        }
    }

    private fun handlePowerups() {
        for (p in spawner.powerups) {
            if (p.taken) continue
            if (p.z < -0.8f || p.z > 1.2f) continue
            val px = p.lane * GameConfig.LANE_WIDTH
            if (abs(px - player.x) < 0.9f) {
                p.taken = true
                val idx = p.type
                val dur = GameConfig.POWERUP_DURATIONS[idx] + save.upgradeLevel(SaveManager.POWERUP_NAMES[idx]) * 3f
                activePowerups[idx] = dur
                powerupTotal[idx] = dur
                powerupsUsed++
                score += GameConfig.POWERUP_SCORE * multiplier
                audio.play(GameEvent.POWERUP)
                vibrate(40)
                emit3D(px, 1.2f, p.z, { sx, sy ->
                    particles.burst(sx, sy, 20, powerColor(idx), 340f, 6f, life = 0.7f)
                    particles.text(sx, sy + 30f, GameConfig.POWERUP_LABELS[idx], powerColor(idx), 22f)
                })
            }
        }
    }

    private fun powerColor(i: Int): Color = when (i) {
        0 -> Color(0xef4444ff.toInt())
        1 -> Color(0xf59e0bff.toInt())
        2 -> Color(0x2dd4bfff.toInt())
        3 -> Color(0xa3e635ff.toInt())
        else -> Color(0xf97316ff.toInt())
    }

    // ── Run lifecycle ──────────────────────────────────────────────────
    private fun startRunInternal() {
        score = 0; displayScore = 0; runCoins = 0; distance = 0f
        jumps = 0; slides = 0; powerupsUsed = 0; nearMisses = 0
        newBest = false; coinStreak = 0; multiplier = 1
        for (i in 0 until 5) { activePowerups[i] = 0f; powerupTotal[i] = 0f }
        player.reset()
        chaser.active = false
        particles.clear()
        spawner.reset()
        shake = 0f
        chaser.trigger(GameConfig.CHASER_START_TIME)
    }

    fun startRun() {
        startRunInternal()
        state = GameState.PLAYING
        val firstRun = !save.tutorialDone
        for (i in 0 until 4) tutorialHints[i] = firstRun
        audio.play(GameEvent.CLICK)
    }

    fun restartRun() {
        startRunInternal()
        state = GameState.PLAYING
        audio.play(GameEvent.CLICK)
    }

    fun pauseGame() { if (state == GameState.PLAYING) state = GameState.PAUSED }
    fun resumeGame() { if (state == GameState.PAUSED) state = GameState.PLAYING }

    fun toMenu() {
        state = GameState.MENU
        menuPanel = MenuPanel.NONE
        player.reset()
        spawner.reset()
        audio.play(GameEvent.CLICK)
    }

    private fun finalizeRun() {
        save.markTutorialDone()
        newBest = save.submitRun(score, runCoins, distance.toInt(), jumps, slides, powerupsUsed, nearMisses)
        displayScore = 0
        state = GameState.GAME_OVER
        if (newBest) {
            audio.play(GameEvent.NEW_BEST)
            particles.confetti(GameConfig.VIRTUAL_WIDTH / 2f, GameConfig.VIRTUAL_HEIGHT * 0.75f, 60)
            vibrate(160)
        } else {
            audio.play(GameEvent.GAME_OVER)
        }
    }

    private fun vibrate(ms: Int) {
        if (save.vibrationOn) Gdx.input.vibrate(ms)
    }

    override fun pause() {
        if (state == GameState.PLAYING) state = GameState.PAUSED
    }

    // ── QA autopilot hooks (used by the desktop test harness only) ─────
    fun debugStartRun() = startRun()
    fun debugSwipe(dir: SwipeDetector.Direction) = handleSwipe(dir)
    fun debugState(): String = state.name
    fun debugMenuPanel(): String = menuPanel.name

    override fun dispose() {
        audio.dispose()
        theme.dispose()
        TextureGen.dispose()
        batch.dispose()
        sr.dispose()
        scene.dispose()
    }

    // ── Drawing ────────────────────────────────────────────────────────
    private fun draw(rawDt: Float) {
        val gl = Gdx.gl
        gl.glClearColor(0.22f, 0.72f, 0.96f, 1f)
        gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        // 3D world fills the whole window
        val camLift = player.groundY * 0.75f
        scene.render(batch, time, if (state == GameState.MENU) 0f else player.x, camLift)

        // entities + rigs (ModelBatch)
        if (state == GameState.PLAYING || state == GameState.DYING) {
            game3d.render(spawner, player, chaser, time, player.groundY,
                invulnBlink = player.invulnTimer > 0f, shieldOn = activePowerups[2] > 0f)
        } else {
            game3d.renderMenu(time)
        }

        // letterboxed 2D overlay (particles + UI)
        gl.glViewport(vpX, vpY, vpW, vpH)
        gl.glEnable(GL20.GL_BLEND)
        gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        sr.begin(ShapeRenderer.ShapeType.Filled)
        particles.render(sr)
        sr.end()

        batch.begin()
        batch.projectionMatrix = camera.combined
        // floating score texts ride the 2D overlay
        particles.eachText { t ->
            val alpha = Mathz.clamp01(t.life / t.maxLife * 1.6f)
            val font = if (t.size > 22f) theme.fontLarge else theme.fontSmall
            font.setColor(t.color.r, t.color.g, t.color.b, alpha)
            font.data.setScale(t.size / 32f)
            layout.setText(font, t.text)
            font.draw(batch, t.text, t.x - layout.width / 2f, t.y)
        }
        theme.fontSmall.setColor(1f, 1f, 1f, 1f)
        theme.fontLarge.setColor(1f, 1f, 1f, 1f)
        theme.fontSmall.data.setScale(1f)
        theme.fontLarge.data.setScale(1f)
        when (state) {
            GameState.MENU -> {
                if (menuPanel == MenuPanel.NONE) ui.drawMenu(time) else ui.drawPanel(time)
                ui.drawToast(rawDt)
            }
            GameState.PLAYING -> {
                ui.drawHud(time)
                if (!save.tutorialDone) ui.drawHintChips(time, tutorialHints)
            }
            GameState.PAUSED -> { ui.drawHud(time); ui.drawPause() }
            GameState.GAME_OVER -> { ui.drawGameOver(); ui.drawToast(rawDt) }
            else -> {}
        }
        batch.end()
        gl.glDisable(GL20.GL_BLEND)
    }

    // ── UI bridge ──────────────────────────────────────────────────────
    private val bridge = object : UiController.Bridge {
        override val batch: SpriteBatch get() = this@DummySurfersGame.batch
        override val sr: ShapeRenderer get() = this@DummySurfersGame.sr
        override val state: GameState get() = this@DummySurfersGame.state
        override val menuPanel: MenuPanel get() = this@DummySurfersGame.menuPanel
        override val shopTab: ShopTab get() = this@DummySurfersGame.shopTab
        override var shopTabSet: ShopTab
            get() = this@DummySurfersGame.shopTab
            set(value) { this@DummySurfersGame.shopTab = value }
        override val tutorialHints: BooleanArray get() = this@DummySurfersGame.tutorialHints
        override val score: Int get() = this@DummySurfersGame.score
        override val displayScore: Int
            get() {
                val g = this@DummySurfersGame
                if (g.displayScore < g.score) {
                    val step = max(1, ((g.score - g.displayScore) * 0.14f).toInt())
                    g.displayScore = min(g.score, g.displayScore + step)
                }
                return g.displayScore
            }
        override val runCoins: Int get() = this@DummySurfersGame.runCoins
        override val distance: Float get() = this@DummySurfersGame.distance
        override val multiplier: Int get() = this@DummySurfersGame.multiplier
        override val powerupRemaining: FloatArray get() = activePowerups
        override val powerupTotal: FloatArray get() = this@DummySurfersGame.powerupTotal
        override val newBest: Boolean get() = this@DummySurfersGame.newBest
        override val save: SaveManager get() = this@DummySurfersGame.save
        override val toFrame: (FloatArray) -> Unit
            get() = { out ->
                out[0] = (Gdx.input.x - vpX) / viewScale
                out[1] = GameConfig.VIRTUAL_HEIGHT - (Gdx.input.y - vpY) / viewScale
            }

        override fun startRun() = this@DummySurfersGame.startRun()
        override fun pauseGame() = this@DummySurfersGame.pauseGame()
        override fun resumeGame() = this@DummySurfersGame.resumeGame()
        override fun restartRun() = this@DummySurfersGame.restartRun()
        override fun toMenu() = this@DummySurfersGame.toMenu()
        override fun openPanel(p: MenuPanel) { this@DummySurfersGame.menuPanel = p; audio.play(GameEvent.CLICK) }
        override fun closePanel() { this@DummySurfersGame.menuPanel = MenuPanel.NONE; audio.play(GameEvent.CLICK) }
        override fun selectCharacter(id: String) {
            save.selectCharacter(id)
            character = CharacterDef.byId(id)
            swapRig(id)
            audio.play(GameEvent.CLICK)
        }
        override fun buyCharacter(id: String) {
            val ch = CharacterDef.byId(id)
            if (save.ownCharacter(id, ch.cost)) {
                save.selectCharacter(id)
                character = ch
                swapRig(id)
                ui.toast("UNLOCKED ${ch.name}!")
                audio.play(GameEvent.POWERUP)
            } else {
                ui.toast("NOT ENOUGH COINS")
                audio.play(GameEvent.GAME_OVER)
            }
        }
        override fun buyUpgrade(name: String) {
            val idx = SaveManager.POWERUP_NAMES.indexOf(name)
            val lvl = save.upgradeLevel(name)
            if (lvl >= 3) return
            if (save.buyUpgrade(name, GameConfig.UPGRADE_COSTS[idx][lvl])) {
                ui.toast("UPGRADED!")
                audio.play(GameEvent.POWERUP)
            } else {
                ui.toast("NOT ENOUGH COINS")
                audio.play(GameEvent.GAME_OVER)
            }
        }
        override fun buyTrail(index: Int) {
            val owned = index == 0 || save.trail >= index
            if (owned) {
                save.buyTrail(index, 0)
                audio.play(GameEvent.CLICK)
            } else if (save.buyTrail(index, GameConfig.TRAIL_COSTS[index])) {
                ui.toast("TRAIL EQUIPPED!")
                audio.play(GameEvent.POWERUP)
            } else {
                ui.toast("NOT ENOUGH COINS")
                audio.play(GameEvent.GAME_OVER)
            }
        }
        override fun claimMission(index: Int) {
            if (save.claimMission(index)) {
                ui.toast("+${save.missions.getOrNull(index)?.reward ?: 0} COINS")
                audio.play(GameEvent.NEW_BEST)
            }
        }
        override fun setMusic(on: Boolean) { save.setMusic(on); audio.setMusic(on) }
        override fun setSfx(on: Boolean) { save.setSfx(on); audio.setSfx(on) }
        override fun setVibration(on: Boolean) { save.setVibration(on) }
        override fun resetProgress() {
            save.reset()
            character = CharacterDef.byId(save.selectedCharacter)
            swapRig(save.selectedCharacter)
            this@DummySurfersGame.menuPanel = MenuPanel.NONE
            ui.toast("PROGRESS RESET")
        }
        override fun click() = audio.play(GameEvent.CLICK)
    }

    private fun swapRig(id: String) {
        val newRig = HumanoidRig(RigPalettes.byId(id))
        playerRig = newRig
        game3d = Game3DRenderer(scene, playerRig, guardRig, dogRig)
        game3d.create()
    }
}
