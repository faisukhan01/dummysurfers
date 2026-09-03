package com.dummysurfers.core

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.math.Vector3
import com.dummysurfers.core.audio.AudioManager
import com.dummysurfers.core.camera.Projection
import com.dummysurfers.core.config.GameConfig
import com.dummysurfers.core.entities.Chaser
import com.dummysurfers.core.entities.CharacterDef
import com.dummysurfers.core.entities.Obstacle
import com.dummysurfers.core.entities.ObstacleKind
import com.dummysurfers.core.entities.Player
import com.dummysurfers.core.entities.Train
import com.dummysurfers.core.gfx.Palette
import com.dummysurfers.core.gfx.TextureGen
import com.dummysurfers.core.gfx3d.Scene3D
import com.dummysurfers.core.input.SwipeDetector
import com.dummysurfers.core.particles.Particles
import com.dummysurfers.core.state.GameEvent
import com.dummysurfers.core.state.GameState
import com.dummysurfers.core.state.MenuPanel
import com.dummysurfers.core.state.MissionType
import com.dummysurfers.core.state.PowerUpType
import com.dummysurfers.core.state.SaveManager
import com.dummysurfers.core.state.PlayerState
import com.dummysurfers.core.state.ShopTab
import com.dummysurfers.core.systems.Difficulty
import com.dummysurfers.core.systems.Spawner
import com.dummysurfers.core.ui.UiController
import com.dummysurfers.core.ui.UiTheme
import com.dummysurfers.core.utils.Mathz
import com.dummysurfers.core.world.WorldGenerator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * DUMMY SURFERS BY FSK — main game orchestrator.
 * Owns the loop, state machine, gameplay systems and bridges them to UI/audio.
 */
class DummySurfersGame : com.badlogic.gdx.ApplicationAdapter() {

    lateinit var batch: SpriteBatch; private set
    lateinit var sr: ShapeRenderer; private set
    private val camera = OrthographicCamera()
    private val proj = Projection()
    private lateinit var scene3d: Scene3D
    private val theme = UiTheme()
    private lateinit var ui: UiController
    private val audio = AudioManager()

    // v1.2.0 FIX: must NOT be constructed eagerly here — this initializer runs
    // before LibGDX assigns Gdx.app, and SaveManager's constructor calls
    // Gdx.app.getPreferences(...) → instant NPE at launch. `by lazy` defers the
    // first access to create()/callbacks, when the engine is fully wired up.
    private val save: SaveManager by lazy { SaveManager() }
    private val world = WorldGenerator()
    private val spawner = Spawner()
    private val player = Player()
    private val chaser = Chaser()
    private val particles = Particles(340)
    private val swipe = SwipeDetector(object : SwipeDetector.Listener {
        override fun onSwipe(dir: SwipeDetector.Direction) = handleSwipe(dir)
        override fun onTap() = handleTap()
    })

    private val rng = Random(System.nanoTime())
    private val tmpColor = Color()
    private val fxV = Vector3()

    /** v4.4: anchor a 2D FX burst to the runner's TRUE-3D screen position
     *  (feet by default). The legacy proj.screenX/groundY anchored to the
     *  retired 2.5D layout — dust puffs materialized as stray specks around
     *  the shadow instead of under the feet. */
    private fun fxAnchor(y: Float = player.jumpY): Vector3 =
        scene3d.screenPos(player.x, y, 0f, fxV)
    private var state: GameState = GameState.MENU
    private var menuPanel = MenuPanel.NONE
    private var shopTab = ShopTab.CHARACTERS
    private var tutorialStep: Int? = null

    // ── Run state ──────────────────────────────────────────────────────
    private var score = 0
    private var scoreFrac = 0f // v4.7: fractional distance-score accumulator
    private var runCoins = 0
    private var distance = 0f
    private var jumps = 0
    private var slides = 0
    private var powerupsUsed = 0
    private var nearMisses = 0

    // v3.0 stumble system: glancing hits wound instead of killing; while the
    // danger window is open the guard sprints at grab range and a 2nd hit =
    // the SS guard-grab caught animation.
    private var dangerTimer = 0f
    private var stumbleSlowTimer = 0f
    private var guardCatch = false
    // v4.6 post-run coin doubler gift (SS end-screen doubler; once per run)
    private var giftClaimed = false
    private var giftBonus = 0
    private val activePowerups = FloatArray(PowerUpType.entries.size)
    private val powerupTotal = FloatArray(PowerUpType.entries.size)
    private var displayScore = 0
    private var newBest = false
    private var coinStreak = 0
    private var coinStreakTimer = 0f
    private var multiplier = 1
    private var boardTimer = 0f        // hoverboard ride time left (0 = not riding)
    private var boardTotal = 0f        // ride duration for the HUD bar
    private var lastTapNanos = 0L      // double-tap window tracking

    // ── FX state ───────────────────────────────────────────────────────
    private var shake = 0f
    private var shakeX = 0f
    private var shakeY = 0f
    private var time = 0f
    private var dyingTimer = 0f
    private var menuDim = 0.55f
    private var lastStepParity = 0

    // letterbox
    private var vpX = 0; private var vpY = 0; private var vpW = 0; private var vpH = 0
    private var screenW = 0; private var screenH = 0
    private var viewScale = 1f
    private val fontLayout = com.badlogic.gdx.graphics.g2d.GlyphLayout()

    private lateinit var character: CharacterDef

    override fun create() {
        batch = SpriteBatch()
        sr = ShapeRenderer()
        camera.setToOrtho(false, GameConfig.VIRTUAL_WIDTH, GameConfig.VIRTUAL_HEIGHT)
        proj.setViewport(GameConfig.VIRTUAL_WIDTH, GameConfig.VIRTUAL_HEIGHT)
        TextureGen.generate()
        theme.create()
        scene3d = Scene3D(batch, proj)
        ui = UiController(theme)
        ui.bridge = bridge
        audio.start()
        audio.setMusic(save.musicOn)
        audio.setSfx(save.sfxOn)
        character = CharacterDef.byId(save.selectedCharacter)
        world.reset()
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
    // v5.1: last-resort safety net. An exception escaping render() kills the
    // whole app ("opens fine, then it just closes mid-run"). Any gameplay
    // tick that throws now logs the error and recovers to the MENU instead
    // of ending the process; the crash reporter still captures genuinely
    // fatal startup failures.
    private var tickErrors = 0
    override fun render() {
        val rawDt = Gdx.graphics.deltaTime.coerceIn(0.001f, 0.05f)
        val dt = rawDt * if (state == GameState.DYING) 0.4f else 1f
        time += rawDt

        try {
            update(dt)
            draw(rawDt)
            devHarness(rawDt)
            if (tickErrors > 0) tickErrors--
        } catch (t: Throwable) {
            tickErrors++
            Gdx.app.error("DS", "tick #${tickErrors} failed — recovering", t)
            if (tickErrors >= 8) {
                // Something is persistently broken; park in the safest known
                // state (menu, world reset) and stop hammering the failing path.
                try {
                    recoverToMenu()
                } catch (_: Throwable) {}
                tickErrors = 0
                Thread.sleep(120) // brief cool-off; GL thread may sleep here safely
            }
        }
    }

    /** No-sound, no-luck recovery path used by the render() safety net. */
    private fun recoverToMenu() {
        state = GameState.MENU
        menuPanel = MenuPanel.NONE
        tutorialStep = null
        dangerTimer = 0f
        stumbleSlowTimer = 0f
        guardCatch = false
        boardTimer = 0f
        player.reset()
        world.reset()
        spawner.reset()
    }

    // ── Dev visual-QA harness (desktop) ────────────────────────────────
    // DS_AUTORUN=1  → start a run automatically after boot (gameplay shots)
    // DS_GOD=1      → autopilot: dodges/jumps/slides + invulnerable
    // DS_SHOT_DIR=X → save scheduled framebuffer screenshots, then exit
    private var devInit = false
    private val devGod get() = System.getenv("DS_GOD") == "1" // QA invulnerability without blink
    private var devShotIdx = 0
    private var devT = 0f
    private val devShotTimes = floatArrayOf(0.8f, 2.5f, 4.5f, 6.5f, 8.5f, 10.5f, 12.5f, 14.5f, 16.5f, 18.5f)
    private var devAiTimer = 0f
    private var spawnGrace = 0f // v4.5: brief post-RUN window where collisions are ignored
    private var coinTextCascade = 0 // v4.5: successive coin texts cascade upward
    private var prevJetOn = false
    private var jetCoinTimer = 0f

    private fun devHarness(rawDt: Float) {
        val menuFirst = System.getenv("DS_MENU_FIRST") == "1"
        if (!devInit) {
            devInit = true
            // v4.7: DS_CHAR=<id> force-selects a runner (grant included) so QA
            // batches can capture BLAZE/VOLT/NOVA + their accessories on camera
            System.getenv("DS_CHAR")?.let {
                save.qaForceCharacter(it.trim().lowercase())
                character = CharacterDef.byId(save.selectedCharacter)
            }
            // v4.6: DS_DIE (and DS_CATCH/DS_CHASE) now auto-start too — they only
            // act while PLAYING, so a bare `DS_DIE=1` batch used to shoot 10 idle
            // menu frames and verify nothing (worklog Task 16 note)
            if ((System.getenv("DS_AUTORUN") == "1" || System.getenv("DS_DIE") == "1" ||
                System.getenv("DS_CATCH") == "1" || System.getenv("DS_CHASE") == "1") && !menuFirst) restartRun()
        }
        if (System.getenv("DS_DIE") == "1" && state == GameState.PLAYING) {
            // death-path QA: no autopilot, no invulnerability → run into hazards
            player.invulnTimer = 0f
        } else if (System.getenv("DS_GOD") == "1" && state == GameState.PLAYING) devAutopilot(rawDt)
        // v4.5 guard QA: DS_CHASE keeps the guard+dog sprinting close behind the
        // runner for the whole batch (he otherwise only appears after stumbles —
        // the user explicitly wants the "police officer chasing Jack" on camera);
        // DS_CATCH scripts a stumble → guard-grab CAUGHT sequence at fixed times
        if (System.getenv("DS_CHASE") == "1" && state == GameState.PLAYING) {
            if (!chaser.active || chaser.timer < 1.5f) chaser.triggerClose(6f)
        }
        if (System.getenv("DS_CATCH") == "1" && state == GameState.PLAYING) {
            if (devT >= 6f && dangerTimer <= 0f && state == GameState.PLAYING) onHit(player.x, glancing = true)
            if (devT >= 9f && dangerTimer > 0f) onHit(player.x, glancing = true) // second clip inside the danger window = guard grab
        }
        val dir = System.getenv("DS_SHOT_DIR") ?: return
        devT += rawDt
        if (devShotIdx < devShotTimes.size && devT >= devShotTimes[devShotIdx]) {
            val pm: Pixmap = ScreenUtils.getFrameBufferPixmap(vpX, vpY, vpW, vpH)
            // GL framebuffer rows are bottom-up — flip so PNGs read like the screen
            val out = Pixmap(pm.width, pm.height, pm.format)
            val src = pm.pixels; val dst = out.pixels
            src.rewind(); dst.rewind()
            val rowBytes = pm.width * 4
            val row = ByteArray(rowBytes)
            for (y in 0 until pm.height) {
                src.position((pm.height - 1 - y) * rowBytes)
                src.get(row)
                dst.position(y * rowBytes)
                dst.put(row)
            }
            PixmapIO.writePNG(Gdx.files.absolute("$dir/shot-${devShotIdx}.png"), out)
            pm.dispose(); out.dispose()
            devShotIdx++
            if (menuFirst && devShotIdx == 1) restartRun() // menu captured → start the run
            if (devShotIdx >= devShotTimes.size) Gdx.app.exit()
        }
    }

    /** Simple look-ahead autopilot used only for headless screenshot QA. */
    private fun devAutopilot(dt: Float) {
        // invulnerability is handled by the devGod flag in handleCollisions —
        // do NOT touch invulnTimer here (it drives the death blink flicker and
        // made ~40% of QA shots render without the runner)
        if (System.getenv("DS_JET") == "1" && activePowerups[5] <= 0f) {
            activePowerups[5] = 25f; powerupTotal[5] = 25f // keep the jet lit for the whole QA batch
        }
        devAiTimer -= dt
        if (player.state == PlayerState.JUMPING || player.state == PlayerState.SLIDING) return
        if (devAiTimer > 0f) return
        devAiTimer = 0.06f

        fun blockedIn(lane: Int, zLo: Float, zHi: Float): Boolean {
            for (t in spawner.trains) {
                if (t.z < zLo || t.z - t.totalLength > zHi) continue
                if (t.lanes.contains(lane)) {
                    // v4: ramped parked trains are CLIMBABLE — the autopilot runs up
                    // them so the roof-running path gets exercised in QA shots
                    if (t.kind == 0) continue
                    return true
                }
            }
            for (o in spawner.obstacles) {
                if (o.z < zLo || o.z > zHi) continue
                if (o.kind == ObstacleKind.GATE || o.kind == ObstacleKind.FENCE_FULL) continue
                if (o.lane == lane) return true
            }
            return false
        }
        fun actionIn(lane: Int, zLo: Float, zHi: Float): Char? {
            for (o in spawner.obstacles) {
                if (o.z < zLo || o.z > zHi) continue
                if (o.lane != lane && o.kind != ObstacleKind.GATE && o.kind != ObstacleKind.FENCE_FULL) continue
                return when (o.kind) {
                    ObstacleKind.LOW_BARRIER, ObstacleKind.FENCE_FULL -> 'j'
                    ObstacleKind.HIGH_BARRIER, ObstacleKind.GATE -> 's'
                    else -> null
                }
            }
            return null
        }

        val lane = player.lane
        // 1) imminent action?
        when (actionIn(lane, 3.5f, 7f)) {
            'j' -> { player.startJump(false); return }
            's' -> { player.startSlide(); return }
        }
        // 2) blocked ahead? sidestep to a free lane
        if (blockedIn(lane, 4f, 26f)) {
            val options = listOf(-1, 1, -1, 0, 1).sortedBy { abs(it - lane) }
            for (cand in options) {
                val target = (lane + cand).coerceIn(-1, 1)
                if (target != lane && !blockedIn(target, 4f, 26f)) {
                    player.switchLane(target)
                    return
                }
            }
        }
    }

    private fun update(dt: Float) {
        swipe.pollKeyboard()
        if (state == GameState.PLAYING && Gdx.input.isKeyJustPressed(Input.Keys.B)) activateBoard()
        ui.update(dt)
        particles.update(dt)

        // shake decay
        shake = max(0f, shake - dt * 40f)
        val so = proj.shakeOffset(shake, rng)
        shakeX = so.first; shakeY = so.second

        when (state) {
            GameState.MENU -> updateAttract(dt)
            GameState.TUTORIAL -> updateRun(dt, tutorial = true)
            GameState.PLAYING -> updateRun(dt, tutorial = false)
            GameState.DYING -> updateDying(dt)
            else -> {}
        }

        // audio intensity follows speed
        audio.setIntensity(if (state == GameState.PLAYING) Difficulty.speedPct(distance) else 0.25f)
    }

    private fun updateAttract(dt: Float) {
        val speed = 6f
        world.update(dt * speed)
        player.runPhase += dt * speed * 1.55f
        chaser.active = false
        proj.update(0f, dt)
        proj.zoom = 1f
        menuDim = 0.55f
    }

    private fun updateRun(dt: Float, tutorial: Boolean) {
        menuDim = 0f
        if (spawnGrace > 0f) spawnGrace -= dt
        val speed = if (tutorial) 5f else Difficulty.speed(distance) * (if (activePowerups[3] > 0f) GameConfig.BOOST_SPEED_MULT else 1f) *
                (if (stumbleSlowTimer > 0f) GameConfig.STUMBLE_SLOW_MULT else 1f)

        // world + spawner scroll
        val scroll = speed * dt
        distance += scroll
        world.update(scroll)
        spawner.addDistance(scroll)
        spawner.speedHint = speed
        spawner.update(dt, speed, scroll, player.lane, attractMode = tutorial)

        // player physics — support = train roofs / ramps (v4 roof-running)
        player.supportY = spawner.supportAt(player.lane, player.x)
        val grounded = player.update(dt, speed)
        if (grounded) {
            audio.play(GameEvent.LAND)
            fxAnchor().let { f -> particles.burst(f.x, f.y, 5, Color(0xcbb9a4ff.toInt()), 130f, 4f, grav = 500f, life = 0.35f) }
        }

        // footsteps synced to run cycle
        if (player.stepParity != lastStepParity && player.state == PlayerState.RUNNING && activePowerups[5] <= 0f) {
            lastStepParity = player.stepParity
            audio.play(GameEvent.FOOTSTEP)
            if (rng.nextFloat() < 0.5f) {
                fxAnchor().let { f -> particles.burst(f.x + rng.nextFloat() * 16f - 8f, f.y, 2, Color(0xbfae9aff.toInt()), 90f, 3f, grav = 420f, life = 0.3f) }
            }
        }

        // score & multiplier
        if (!tutorial) {
            // v4.7 SCORE ACCUMULATOR FIX: (scroll * 1).toInt() truncated to 0
            // every frame (scroll is a per-frame delta ≈0.23-0.36 at 60fps) —
            // the distance score was DEAD on most devices and the scoreboard
            // only moved when coins landed, freezing for seconds at a time
            // (QA sheets showed 150 flat across 6 shots / 76m). Accumulate the
            // fraction and bank whole meters.
            scoreFrac += scroll * GameConfig.DISTANCE_SCORE_PER_METER * multiplier
            val whole = scoreFrac.toInt()
            if (whole > 0) {
                scoreFrac -= whole
                score += whole
            }
            val newMult = Difficulty.multiplier(distance)
            if (newMult > multiplier) {
                // v3.0: celebrate the rank-up (SS pops a badge when the multiplier climbs)
                multiplier = newMult
                particles.text(proj.vw / 2f, proj.vh * 0.40f, "SCORE x$newMult!", Palette.GOLD, 30f)
                audio.play(GameEvent.POWERUP)
                vibrate(50)
            } else {
                multiplier = newMult
            }
        }

        // power-up timers
        for (i in 0 until PowerUpType.entries.size) {
            if (activePowerups[i] > 0f) {
                activePowerups[i] -= dt
                if (activePowerups[i] <= 0f) activePowerups[i] = 0f
            }
        }

        // v4.1 JETPACK — the sky is the support: cruise above every hazard,
        // rain a coin line ahead, then let roof-fall physics land the runner
        val jetOn = activePowerups[5] > 0f
        if (jetOn) {
            player.supportY = GameConfig.JETPACK_HEIGHT
            if (player.state == PlayerState.JUMPING || player.state == PlayerState.SLIDING) {
                player.slideTimer = 0f
                player.state = PlayerState.RUNNING
            }
            jetCoinTimer -= dt
            if (jetCoinTimer <= 0f) {
                jetCoinTimer = 0.3f
                spawner.spawnCoin(player.lane, 26f + rng.nextFloat() * 6f,
                    GameConfig.JETPACK_HEIGHT + 0.3f + sin(time * 2.2f) * 0.12f)
            }
            // thruster sparks + soft flame puffs under the pack
            if (rng.nextFloat() < 0.6f) {
                fxAnchor(player.jumpY - 0.45f).let { f ->
                    particles.burst(f.x + rng.nextFloat() * 14f - 7f, f.y, 2,
                        Color(0xf9a03aff.toInt()), 130f, 4f, grav = -220f, life = 0.3f)
                }
            }
        }
        if (!jetOn && prevJetOn && player.jumpY > 1.2f) {
            // flame cut out high above the ground — grace for the touchdown
            player.invulnTimer = max(player.invulnTimer, 1.0f)
            particles.text(proj.vw / 2f, proj.vh * 0.5f, "JETPACK OUT!", Palette.UI_MUTED, 20f)
        }
        prevJetOn = jetOn

        // hoverboard ride timer
        if (boardTimer > 0f) boardTimer = max(0f, boardTimer - dt)

        // stumble decay
        if (stumbleSlowTimer > 0f) stumbleSlowTimer = max(0f, stumbleSlowTimer - dt)
        if (dangerTimer > 0f) dangerTimer = max(0f, dangerTimer - dt)

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
                c.y += (0.9f - c.y) * min(1f, dt * 9f)
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
                shake = max(shake, 1.2f) // subtle rumble
            }
        }

        // collisions & pickups
        if (!tutorial) {
            handleCollisions()
            handleCoins(dt)
            handlePowerups()
            checkLiveMissions()
        } else {
            handleCoins(dt)
        }

        // boost FX + top-speed juice (spec 2.2: speed lines at very high speeds)
        val topSpeed = Difficulty.speed(distance) > GameConfig.MAX_SPEED * 0.88f
        if (activePowerups[3] > 0f || topSpeed) {
            particles.streak(proj.vw, proj.vh, if (activePowerups[3] > 0f) 3 else 2)
            proj.zoom = proj.zoom + (0.93f - proj.zoom) * min(1f, dt * 5f)
        } else {
            proj.zoom = proj.zoom + (1f - proj.zoom) * min(1f, dt * 4f)
        }

        // trail cosmetics (gold / fire / rainbow hue-cycle)
        if (save.trail > 0 && player.state != PlayerState.SLIDING) {
            val col = when (save.trail) {
                1 -> Color(0xffc93cff.toInt())
                2 -> Color(0xf97316ff.toInt())
                else -> Mathz.hsv(time * 140f, 0.85f, 1f, 1f, tmpColor)
            }
            val emitX = fxAnchor(player.jumpY + 0.15f).x + rng.nextFloat() * 14f - 7f
            particles.burst(emitX, fxAnchor(player.jumpY + 0.15f).y, 1, col, 40f, 5f, grav = -60f, life = 0.4f)
        }

        // camera
        proj.update(player.x * GameConfig.CAMERA_FOLLOW, dt)

        coinStreakTimer -= dt
        if (coinStreakTimer <= 0f) coinStreak = 0
    }

    private fun updateDying(dt: Float) {
        world.update(dt * if (guardCatch) 1.2f else 4f)
        player.update(dt, 0f)
        // the grab sequence: guard rushes in while the world holds its breath
        if (guardCatch) chaser.update(dt, 0f)
        dyingTimer += dt
        if (dyingTimer >= if (guardCatch) 1.45f else 1.0f) {
            finalizeRun()
        }
    }

    // ── Gameplay interactions ──────────────────────────────────────────
    private fun handleSwipe(dir: SwipeDetector.Direction) {
        when (state) {
            GameState.TUTORIAL -> {
                val step = tutorialStep ?: return
                val expected = when (step) {
                    0 -> SwipeDetector.Direction.LEFT
                    1 -> SwipeDetector.Direction.RIGHT
                    2 -> SwipeDetector.Direction.UP
                    else -> SwipeDetector.Direction.DOWN
                }
                if (dir == expected) {
                    // perform the action then advance
                    performSwipe(dir)
                    tutorialStep = if (step >= 3) null else step + 1
                    if (tutorialStep == null) {
                        save.markTutorialDone()
                        startRun()
                    } else {
                        audio.play(GameEvent.TUTORIAL_STEP)
                    }
                } else {
                    performSwipe(dir) // still allow moving during tutorial
                }
            }
            GameState.PLAYING -> performSwipe(dir)
            else -> {}
        }
    }

    private fun performSwipe(dir: SwipeDetector.Direction) {
        when (dir) {
            SwipeDetector.Direction.LEFT -> { player.switchLane(-1); audio.play(GameEvent.LANE); laneDust() }
            SwipeDetector.Direction.RIGHT -> { player.switchLane(1); audio.play(GameEvent.LANE); laneDust() }
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

    /** Double-tap anywhere during a run = hop on the hoverboard. */
    private fun handleTap() {
        if (state != GameState.PLAYING) return
        val now = System.nanoTime()
        val doubleTap = now - lastTapNanos < 350_000_000L
        lastTapNanos = now
        if (doubleTap) activateBoard()
    }

    private fun activateBoard() {
        if (state != GameState.PLAYING || boardTimer > 0f) return
        if (!save.consumeHoverboard()) {
            audio.play(GameEvent.GAME_OVER)
            return
        }
        boardTimer = GameConfig.HOVERBOARD_DURATION
        boardTotal = boardTimer
        audio.play(GameEvent.POWERUP)
        vibrate(50)
        fxAnchor().let { f -> particles.burst(f.x, f.y + 14f, 18, Color(0x37b8a8ff.toInt()), 300f, 6f, life = 0.6f) }
    }

    private fun playerOverlaps(boxX: Float, boxHalfW: Float, zNear: Float, zFar: Float, yLo: Float, yHi: Float): Boolean {
        val px = player.x
        if (abs(px - boxX) > GameConfig.PLAYER_HALF_WIDTH + boxHalfW) return false
        if (player.jumpY > yHi || player.jumpY + player.height < yLo) return false
        return 0.5f >= zNear && -0.5f <= zFar
    }

    /** v4.1: skid dust kicked up on lane changes (SS has a puff under each swap). */
    private fun laneDust() {
        repeat(6) {
            fxAnchor().let { f ->
                particles.burst(
                    f.x + rng.nextFloat() * 26f - 13f,
                    f.y + rng.nextFloat() * 10f,
                    1, Color(0xd8c9b0ff.toInt()), 150f, 4f, grav = 260f, life = 0.4f
                )
            }
        }
    }

    private fun handleCollisions() {
        if (player.state == PlayerState.DEAD || player.invulnTimer > 0f) return
        if (devGod) return // QA god mode: skip collisions WITHOUT the blink flicker
        if (spawnGrace > 0f) return // v4.5 spawn grace: the opening 1.4s can never kill you
        if (activePowerups[5] > 0f) return // jetpack: nothing up here can hit you

        // trains
        for (t in spawner.trains) {
            val zFar = t.z
            val zNear = t.z - t.totalLength
            if (zNear > 0.5f || zFar < -0.5f) continue
            // v4 roof-running: runner above roof height is SAFE on top
            if (player.jumpY >= GameConfig.TRAIN_HEIGHT - 0.3f) continue
            for (lane in t.lanes) {
                val laneX = lane * GameConfig.LANE_WIDTH
                if (playerOverlaps(laneX, GameConfig.TRAIN_WIDTH / 2f, zNear, zFar, 0f, GameConfig.TRAIN_HEIGHT)) {
                    onHit(laneX, glancing = false) // trains always catch you
                    return
                }
            }
            // near-miss: train front just passed the player
            if (!t.passedNear && zFar < 0.6f && zFar > 0.0f) {
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
            val boxX = o.lane * GameConfig.LANE_WIDTH
            val hit = when (o.kind) {
                ObstacleKind.LOW_BARRIER -> playerOverlaps(boxX, 1.0f, o.z - 0.35f, o.z + 0.35f, 0f, 0.95f)
                ObstacleKind.FENCE_FULL -> playerOverlaps(player.x, 1.0f, o.z - 0.35f, o.z + 0.35f, 0f, 0.95f)
                ObstacleKind.HIGH_BARRIER -> playerOverlaps(boxX, 1.1f, o.z - 0.35f, o.z + 0.35f, 1.2f, 2.3f)
                ObstacleKind.GATE -> playerOverlaps(player.x, 1.0f, o.z - 0.4f, o.z + 0.4f, 1.2f, 2.3f)
                ObstacleKind.BLOCKADE -> playerOverlaps(boxX, 1.0f, o.z - 0.4f, o.z + 0.4f, 0f, 2.4f)
            }
            if (hit) {
                // barriers clip = stumble-able; blockades are solid caught hits
                onHit(boxX, glancing = o.kind != ObstacleKind.BLOCKADE)
                return
            }
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
        particles.text(proj.vw / 2f, proj.vh * 0.6f, "+$pts", Color(0x8ff2e2ff.toInt()), 22f)
        if (rng.nextFloat() < 0.65f) chaser.trigger(GameConfig.CHASER_NEARMISS_TIME)
    }

/**
     * v3.0: two-tier hit response, exactly like SS.
     * [glancing] = barrier clip → STUMBLE (flail, speed loss, guard closes in,
     * danger window opens). A second hit inside the danger window — or any
     * direct train/blockade hit → the guard-grab CAUGHT sequence.
     */
    private fun onHit(hitX: Float, glancing: Boolean) {
        if (activePowerups[2] > 0f) {
            // shield absorbs one hit
            activePowerups[2] = 0f
            player.invulnTimer = GameConfig.SHIELD_INVULN
            audio.play(GameEvent.SHIELD_BREAK)
            vibrate(60)
            particles.burst(fxAnchor(player.jumpY + 0.7f).x, fxAnchor(player.jumpY + 0.7f).y, 16, Color(0x2dd4bfff.toInt()), 320f, 6f, shape = 1)
            return
        }
        if (boardTimer > 0f) {
            // hoverboard saves the run — board shatters, brief invulnerability
            boardTimer = 0f
            player.invulnTimer = GameConfig.HOVERBOARD_SAVE_INVULN
            audio.play(GameEvent.SHIELD_BREAK)
            vibrate(80)
            particles.text(proj.vw / 2f, proj.vh * 0.55f, "SAVED!", Color(0x37b8a8ff.toInt()), 26f)
            particles.burst(fxAnchor(player.jumpY + 0.35f).x, fxAnchor(player.jumpY + 0.35f).y, 24, Color(0x37b8a8ff.toInt()), 380f, 7f, shape = 1)
            return
        }
        // ── STUMBLE: first glancing hit wounds instead of killing ──
        if (glancing && dangerTimer <= 0f) {
            dangerTimer = GameConfig.DANGER_TIME
            stumbleSlowTimer = GameConfig.STUMBLE_SLOW_TIME
            player.invulnTimer = GameConfig.STUMBLE_INVULN
            chaser.triggerClose(GameConfig.CHASER_STUMBLE_TIME)
            audio.play(GameEvent.STUMBLE)
            audio.play(GameEvent.WHISTLE)
            vibrate(70)
            shake = max(shake, 7f)
            particles.text(proj.vw / 2f, proj.vh * 0.58f, "STUMBLE!", Color(0xff6b5eff.toInt()), 26f)
            fxAnchor(player.jumpY + 1.3f).let { f ->
                particles.burst(f.x, f.y, 14, Color(0xf2a75bff.toInt()), 260f, 5f)
            }
            return
        }
        val caughtByGuard = glancing && dangerTimer > 0f
        player.state = PlayerState.DEAD
        player.deadTimer = 0f
        state = GameState.DYING
        dyingTimer = 0f
        guardCatch = caughtByGuard
        if (System.getenv("DS_QA") == "1") println("[QA-FATAL] dist=$distance score=$score coins=$runCoins glancing=$glancing guard=$caughtByGuard devT=$devT")
        if (caughtByGuard) {
            chaser.beginCatch()
            audio.play(GameEvent.WHISTLE)
            audio.play(GameEvent.CAUGHT)
        } else {
            audio.play(GameEvent.CRASH)
        }
        shake = GameConfig.CRASH_SHAKE
        vibrate(120)
        fxAnchor(player.jumpY + 1.1f).let { f ->
            particles.burst(f.x, f.y, 26, Color(0xef4444ff.toInt()), 420f, 7f, shape = 1)
            particles.burst(f.x, f.y, 14, Color(0xf2a75bff.toInt()), 300f, 5f)
        }
    }

    private fun handleCoins(dt: Float) {
        var anyCollected = false
        for (c in spawner.coins) {
            if (c.collected) continue
            if (c.z < -0.8f || c.z > 1.0f) continue
            val dx = abs(c.x - player.x)
            val reachY = player.jumpY + 1.6f
            if (dx < 0.8f && c.y <= reachY) {
                c.collected = true
                runCoins++
                val pts = GameConfig.COIN_VALUE * multiplier * (if (activePowerups[1] > 0f) 2 else 1)
                score += pts
                coinStreak++
                coinStreakTimer = 0.8f
                audio.play(GameEvent.COIN, coinStreak.toFloat())
                // v4.4: spark burst anchored through the true-3D cam (coins live
                // in world space — the legacy projection put bursts mid-air)
                // v4.5: consecutive pickup texts now cascade upward instead of
                // stacking on the same spot (QA: +10/+20/+30 printed on top of
                // each other during trail runs)
                coinTextCascade = (coinTextCascade + 1).coerceAtMost(4)
                anyCollected = true
                scene3d.screenPos(c.x, c.y, c.z, fxV).let { s ->
                    particles.burst(s.x, s.y, 7, Palette.GOLD, 240f, 5f, shape = 1, life = 0.45f)
                    particles.text(s.x + coinTextCascade * 7f, s.y + 20f + coinTextCascade * 26f, "+${GameConfig.COIN_VALUE * multiplier}", Palette.GOLD, 16f)
                }
            }
        }
        // cascade decays as soon as the trail ends so the next burst starts at the coin
        if (!anyCollected) coinTextCascade = 0
    }

    private fun handlePowerups() {
        for (p in spawner.powerups) {
            if (p.taken) continue
            if (p.z < -0.8f || p.z > 1.2f) continue
            val px = p.lane * GameConfig.LANE_WIDTH
            if (abs(px - player.x) < 0.9f && player.jumpY + 1.75f > 0.9f && player.jumpY < 2.4f) {
                p.taken = true
                val idx = p.type
                val dur = GameConfig.POWERUP_DURATIONS[idx] + save.upgradeLevel(SaveManager.POWERUP_NAMES[idx]) * 3f
                activePowerups[idx] = dur
                powerupTotal[idx] = dur
                powerupsUsed++
                score += GameConfig.POWERUP_SCORE * multiplier
                audio.play(GameEvent.POWERUP)
                vibrate(40)
                scene3d.screenPos(px, 1.25f, p.z, fxV).let { s ->
                    particles.burst(s.x, s.y, 20, powerColor(idx), 340f, 6f, life = 0.7f)
                    particles.text(s.x, s.y + 30f, GameConfig.POWERUP_LABELS[idx], powerColor(idx), 22f)
                }
            }
        }
    }

    /**
     * v4.1: live mission tracking — SS pops "MISSION COMPLETE!" the moment the
     * run's contribution pushes a mission over its goal (submitRun only lands
     * at run end, so completions used to be invisible until game over).
     */
    private val missionShown = BooleanArray(3)
    private fun checkLiveMissions() {
        for (i in 0 until min(3, save.missions.size)) {
            val m = save.missions[i]
            if (m.claimed || missionShown[i]) continue
            val contrib = when (m.type) {
                MissionType.DISTANCE -> distance.toInt()
                MissionType.COINS -> runCoins
                MissionType.JUMPS -> jumps
                MissionType.SLIDES -> slides
                MissionType.POWERUPS -> powerupsUsed
                MissionType.SCORE -> score
                MissionType.NEAR_MISS -> nearMisses
            }
            if (m.progress < m.goal && m.progress + contrib >= m.goal) {
                missionShown[i] = true
                ui.showMissionPopup(m.reward)
                audio.play(GameEvent.NEW_BEST)
                vibrate(60)
                particles.text(proj.vw / 2f, proj.vh * 0.56f, "MISSION COMPLETE!", Palette.GOLD, 26f)
            }
        }
    }

    private fun powerColor(i: Int): Color = when (i) {
        0 -> Color(0xef4444ff.toInt())
        1 -> Color(0xf59e0bff.toInt())
        2 -> Color(0x2dd4bfff.toInt())
        3 -> Color(0xa3e635ff.toInt())
        4 -> Color(0xf97316ff.toInt())
        else -> Color(0xc084fcff.toInt())
    }

    // ── Run lifecycle ──────────────────────────────────────────────────
    private fun startRunInternal() {
        score = 0; scoreFrac = 0f; displayScore = 0; runCoins = 0; distance = 0f
        if (System.getenv("DS_QA") == "1") println("[QA-START] run starting, devT=$devT")
        jumps = 0; slides = 0; powerupsUsed = 0; nearMisses = 0
        newBest = false; coinStreak = 0; multiplier = 1
        dangerTimer = 0f; stumbleSlowTimer = 0f; guardCatch = false
        giftClaimed = false; giftBonus = 0 // v4.6: fresh doubler every run
        spawnGrace = GameConfig.SPAWN_GRACE_TIME // v4.5: no entity may touch the player right at RUN (QA once caught a 0m frame-1 death — "can't start new game")
        for (i in 0 until PowerUpType.entries.size) { activePowerups[i] = 0f; powerupTotal[i] = 0f }
        boardTimer = 0f; boardTotal = 0f; lastTapNanos = 0L
        coinTextCascade = 0
        player.reset()
        chaser.reset()
        particles.clear()
        world.reset()
        spawner.reset()
        shake = 0f
        proj.zoom = 1f
        chaser.trigger(GameConfig.CHASER_START_TIME)
        audio.play(GameEvent.WHISTLE) // the chase is ON from meter zero
    }

    fun startRun() {
        startRunInternal()
        // v3.0: NO forced tutorial gate. SS drops you straight into the run —
        // the old lock-step L→R→U→D tutorial (with slow-swipe-eating input)
        // made players feel the game "can't start". First-run guidance is now
        // a set of non-blocking hint chips drawn by the HUD instead.
        tutorialStep = null
        state = GameState.PLAYING
        audio.play(GameEvent.CLICK)
    }

    fun restartRun() {
        startRunInternal()
        tutorialStep = null
        state = GameState.PLAYING
        audio.play(GameEvent.CLICK)
    }

    fun pauseGame() { if (state == GameState.PLAYING) state = GameState.PAUSED }
    fun resumeGame() { if (state == GameState.PAUSED) state = GameState.PLAYING }

    fun toMenu() {
        state = GameState.MENU
        menuPanel = MenuPanel.NONE
        tutorialStep = null
        player.reset()
        world.reset()
        spawner.reset()
        audio.play(GameEvent.CLICK)
    }

    private fun finalizeRun() {
        if (!save.tutorialDone) save.markTutorialDone()
        if (System.getenv("DS_QA") == "1") println("[QA-END] score=$score dist=$distance coins=$runCoins best-raw=$newBest")
        newBest = save.submitRun(score, runCoins, distance.toInt(), jumps, slides, powerupsUsed, nearMisses)
        displayScore = 0
        state = GameState.GAME_OVER
        if (newBest) {
            audio.play(GameEvent.NEW_BEST)
            particles.confetti(proj.vw / 2f, proj.vh * 0.75f, 60)
            vibrate(160)
        } else {
            audio.play(GameEvent.GAME_OVER)
        }
    }

    private fun vibrate(ms: Int) {
        if (save.vibrationOn) Gdx.input.vibrate(ms)
    }

    override fun pause() {
        if (state == GameState.PLAYING || state == GameState.TUTORIAL) state = GameState.PAUSED
    }

    // ── QA harness hooks (desktop only) ────────────────────────────────
    fun debugStartRun() = startRun()
    fun debugSwipe(dir: com.dummysurfers.core.input.SwipeDetector.Direction) = handleSwipe(dir)
    fun debugState(): String = state.name

    override fun dispose() {
        audio.dispose()
        theme.dispose()
        TextureGen.dispose()
        scene3d.dispose()
        batch.dispose()
        sr.dispose()
    }

    // ── Drawing ────────────────────────────────────────────────────────
    private fun draw(rawDt: Float) {
        val gl = Gdx.gl
        gl.glClearColor(0.55f, 0.75f, 0.9f, 1f)
        gl.glViewport(0, 0, screenW, screenH)
        gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        gl.glViewport(vpX, vpY, vpW, vpH)
        gl.glEnable(GL20.GL_BLEND)
        gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        // tunnel darkness factor
        var tunnelDark = 0f
        for (r in world.tunnelRanges) {
            if (0f > r[0] - 4f && 0f < r[1] + 4f) {
                val t = min(1f, min(0f - r[0], r[1] - 0f) / 5f + 0.5f)
                tunnelDark = max(tunnelDark, t.coerceIn(0f, 1f))
            }
        }

        val menuDim = if (state == GameState.MENU || state == GameState.GAME_OVER) 0.55f else 0f
        scene3d.render(distance, Difficulty.speed(distance), time, world, spawner, player, chaser, character,
            shakeX, shakeY,
            // v5.1: hide the 3D runner on the menu — the front portrait is the
            // hero there; the back-view rig only showed its legs sticking out
            // from under the RUN button like a bug
            blinkHide = state == GameState.MENU || (player.invulnTimer > 0f && sin(time * 42f) > 0.2f),
            boardOn = boardTimer > 0f, stumbleOn = stumbleSlowTimer > 0f, shieldOn = activePowerups[2] > 0f, jetOn = activePowerups[5] > 0f,
            tunnelDark = tunnelDark, menuDim = menuDim)

        // particle FX layer (sparks, confetti, streaks) + floating score texts
        sr.begin(ShapeRenderer.ShapeType.Filled)
        particles.render(sr)
        sr.end()
        batch.begin()
        particles.eachText { t ->
            val alpha = Mathz.clamp01(t.life / t.maxLife * 1.6f)
            val font = if (t.size > 22f) theme.fontLarge else theme.fontSmall
            font.setColor(t.color.r, t.color.g, t.color.b, alpha)
            font.data.setScale(t.size / 32f)
            fontLayout.setText(font, t.text)
            font.draw(batch, t.text, t.x - fontLayout.width / 2 + shakeX, t.y + shakeY)
        }
        theme.fontSmall.setColor(1f, 1f, 1f, 1f)
        theme.fontLarge.setColor(1f, 1f, 1f, 1f)
        theme.fontSmall.data.setScale(1f)
        theme.fontLarge.data.setScale(1f)
        batch.end()

        // boost screen-edge speed glow (spec 9: BOOST — screen edge blur, SS-warm)
        // v4.5: edgeVignette — the old center-dark radial painted an orange EGG
        // over the middle of the screen; a speed glow belongs at the edges
        if (activePowerups[3] > 0f) {
            batch.begin()
            batch.setColor(1f, 0.62f, 0.25f, 0.5f)
            batch.draw(TextureGen.edgeVignette, 0f, 0f, proj.vw, proj.vh)
            batch.setColor(1f, 1f, 1f, 1f)
            batch.end()
        }

        // v3.0: DANGER vignette — red edge pulse while the guard is in grab range
        // v4.5: same edgeVignette swap — with the guard close for long stretches
        // the old radial read as a giant translucent grey egg over the frame
        if (chaser.close && state == GameState.PLAYING) {
            val pulse = 0.35f + (sin(time * 9f) * 0.12f) + (dangerTimer / GameConfig.DANGER_TIME) * 0.15f
            batch.begin()
            batch.setColor(1f, 0.18f, 0.12f, pulse)
            batch.draw(TextureGen.edgeVignette, 0f, 0f, proj.vw, proj.vh)
            batch.setColor(1f, 1f, 1f, 1f)
            batch.end()
        }

        // UI overlays
        ui.flushFrame()
        batch.begin()
        when (state) {
            GameState.MENU -> {
                if (menuPanel == MenuPanel.NONE) ui.drawMenu(time) else ui.drawPanel(time)
                ui.drawToast(rawDt)
            }
            GameState.TUTORIAL -> ui.drawTutorial(time)
            GameState.PLAYING -> ui.drawHud(time)
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
        override var tutorialStep: Int?
            get() = this@DummySurfersGame.tutorialStep
            set(value) { this@DummySurfersGame.tutorialStep = value }
        override val score: Int get() = this@DummySurfersGame.score
        override val displayScore: Int
            get() {
                // count-up animation toward final score
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
        override val boardTimer: Float get() = this@DummySurfersGame.boardTimer
        override val boardTotal: Float get() = this@DummySurfersGame.boardTotal
        override val newBest: Boolean get() = this@DummySurfersGame.newBest
        override val guardCatch: Boolean get() = this@DummySurfersGame.guardCatch
        // v4.6 post-run doubler gift
        override val giftAvailable: Boolean get() = !giftClaimed
        override val giftBonus: Int get() = this@DummySurfersGame.giftBonus
        override fun claimGift() {
            if (giftClaimed || state != GameState.GAME_OVER || runCoins <= 0) return
            giftClaimed = true
            this@DummySurfersGame.giftBonus = runCoins
            save.addCoins(runCoins) // coins were already banked once in submitRun — the gift DOUBLES them
            save.persist()
            audio.play(GameEvent.POWERUP)
            particles.confetti(proj.vw / 2f, proj.vh * 0.42f, 26)
            vibrate(60)
        }
        override val save: SaveManager get() = this@DummySurfersGame.save
        override val toFrame: (FloatArray) -> Unit
            get() = { out -> toFrameAtImpl(Gdx.input.x, Gdx.input.y, out) }
        override val toFrameAt: (Int, Int, FloatArray) -> Unit
            get() = { x, y, out -> toFrameAtImpl(x, y, out) }

        private fun toFrameAtImpl(x: Int, y: Int, out: FloatArray) {
            out[0] = (x - vpX) / viewScale
            out[1] = GameConfig.VIRTUAL_HEIGHT - (y - vpY) / viewScale
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
            audio.play(GameEvent.CLICK)
        }
        override fun buyCharacter(id: String) {
            val ch = CharacterDef.byId(id)
            if (save.ownCharacter(id, ch.cost)) {
                save.selectCharacter(id)
                character = ch
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
        override fun activateBoard() = this@DummySurfersGame.activateBoard()
        override fun buyHoverboard() {
            if (save.buyHoverboard(GameConfig.HOVERBOARD_COST, GameConfig.HOVERBOARD_MAX)) {
                ui.toast("HOVERBOARD +1!")
                audio.play(GameEvent.POWERUP)
            } else {
                ui.toast(if (save.hoverboards >= GameConfig.HOVERBOARD_MAX) "BOARD RACK FULL" else "NOT ENOUGH COINS")
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
            this@DummySurfersGame.menuPanel = MenuPanel.NONE
            ui.toast("PROGRESS RESET")
        }
        override fun click() = audio.play(GameEvent.CLICK)
    }
}
