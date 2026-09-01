package com.dummysurfers.core.gfx3d

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.dummysurfers.core.camera.Projection
import com.dummysurfers.core.config.GameConfig
import com.dummysurfers.core.entities.*
import com.dummysurfers.core.gfx.Palette
import com.dummysurfers.core.gfx.TextureGen
import com.dummysurfers.core.world.Deco
import com.dummysurfers.core.world.DecoKind
import com.dummysurfers.core.world.WorldGenerator
import com.dummysurfers.core.state.PlayerState
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/**
 * v4 TRUE-3D scene renderer — perspective camera behind the runner, lit
 * meshes, scrolling textured track, trains with real depth, articulated
 * chibi humans. Replaces the pseudo-3D World/Entity/Deco renderers.
 *
 * World→GL: gz = -z_game (ahead of the runner = deeper into the screen).
 */
class Scene3D(private val batch: SpriteBatch, private val proj: Projection) {

    val factory = ModelFactory()
    private val modelBatch = ModelBatch()
    val cam = PerspectiveCamera(60f, GameConfig.VIRTUAL_WIDTH, GameConfig.VIRTUAL_HEIGHT).apply {
        near = 0.3f; far = 130f
        position.set(0f, 2.6f, 4.9f)
        lookAt(0f, 1.15f, -6f)
    }

    private val env = Environment()
    private val ambientDay = ColorAttribute(ColorAttribute.AmbientLight, 0.74f, 0.74f, 0.8f, 1f)
    private val ambientTunnel = ColorAttribute(ColorAttribute.AmbientLight, 0.30f, 0.28f, 0.34f, 1f)
    private val sun = DirectionalLight().set(0.62f, 0.58f, 0.5f, -0.32f, -1f, -0.22f)

    // ── instance pools ─────────────────────────────────────────────────
    private val frame = ArrayList<ModelInstance>(220)
    private val coinPool = ArrayList<ModelInstance>(96)
    private val carPool = ArrayList<ModelInstance>(56)
    private val rampPool = ArrayList<ModelInstance>(12)
    private val obstaclePool = ArrayList<ModelInstance>(28)
    private val powerPool = ArrayList<ModelInstance>(8)
    private val shadowPool = ArrayList<ModelInstance>(16)
    private val lampPool = ArrayList<ModelInstance>(8)
    private val lampPoolR = ArrayList<ModelInstance>(8)
    private val lampGlowPool = ArrayList<ModelInstance>(12)
    private val decoMap = HashMap<Deco, DecoInst>(160)

    /** Cached instance + the deco shape it was built for (Deco objects are pooled & repurposed). */
    private class DecoInst(val inst: ModelInstance, var kind: DecoKind, var variant: Int)

    // track/dirt/wall recycled strips
    private val trackSegs = ArrayList<ModelInstance>(14)
    private val dirtL = ArrayList<ModelInstance>(14)
    private val dirtR = ArrayList<ModelInstance>(14)
    private val wallL = ArrayList<ModelInstance>(14)
    private val wallR = ArrayList<ModelInstance>(14)
    private val SEG = 7f
    private val NSEG = 12

    // characters
    private var human: Human3D? = null
    private var humanCharId = ""
    private var guard: Human3D? = null
    private val dog = Dog3D(factory)

    // v4.2: proper hoverboard model (stripe + fins) replaces the plain slab
    private val boardModel by lazy { factory.hoverboard() }
    private val boardGlow by lazy { factory.glowBillboard(1.2f, TextureGen.glow) }
    private val jetFlameInstance by lazy { ModelInstance(factory.glowBillboard(1.1f, TextureGen.jetFlame)) }
    private val jetCoreInstance by lazy { ModelInstance(factory.glowBillboard(0.9f, TextureGen.glow)) }

    // scratch
    private val m = Matrix4()
    private val m2 = Matrix4()
    private val v = Vector3()

    /** World (game z) → screen in virtual units — anchors 2D FX (dust/sparks)
     *  to the TRUE-3D camera. The legacy Projection.screenX/groundY anchored
     *  them to the retired 2.5D layout, scattering specks off the runner's
     *  feet (QA 2026-09-02). Valid after this frame's cam.update(). */
    fun screenPos(x: Float, y: Float, zGame: Float, out: Vector3): Vector3 {
        out.set(x, y, -zGame)
        return cam.project(out)
    }

    init {
        env.set(ambientDay)
        env.add(sun)
        // build recycled strips once
        // v4.4: texGround quads — box() transposed the top-face UV (ties ran
        // ALONG the track, rails ACROSS as zebra stripes; QA 2026-09-02)
        val trackM = factory.texGround("trackSeg", 10.6f, SEG, TextureGen.trackTex, 1f, 2f)
        val dirtM = factory.texGround("dirtSeg", 7.5f, SEG, TextureGen.dirtTex, 3f, 2f)
        val wallM = factory.texBox("wallSeg", 0.35f, 3.0f, SEG, TextureGen.wallTex, 2f, 1f)
        repeat(NSEG) {
            trackSegs.add(ModelInstance(trackM))
            dirtL.add(ModelInstance(dirtM)); dirtR.add(ModelInstance(dirtM))
            wallL.add(ModelInstance(wallM)); wallR.add(ModelInstance(wallM))
        }
    }

    // ── Public render entry ────────────────────────────────────────────
    fun render(
        distance: Float, speed: Float, time: Float,
        world: WorldGenerator, spawnerSystems: com.dummysurfers.core.systems.Spawner,
        player: Player, chaser: Chaser, character: CharacterDef,
        shakeX: Float, shakeY: Float,
        blinkHide: Boolean, boardOn: Boolean, stumbleOn: Boolean, shieldOn: Boolean,
        jetOn: Boolean = false,
        tunnelDark: Float, menuDim: Float
    ) {
        frame.clear()

        drawSky(distance, shakeX, shakeY)

        val gl = Gdx.gl
        gl.glClear(GL20.GL_DEPTH_BUFFER_BIT)

        // tunnel darkness dims ambient
        if (tunnelDark > 0.02f) {
            val t = tunnelDark.coerceIn(0f, 1f)
            env.set(ColorAttribute(ColorAttribute.AmbientLight,
                MathUtils.lerp(ambientDay.color.r, ambientTunnel.color.r, t),
                MathUtils.lerp(ambientDay.color.g, ambientTunnel.color.g, t),
                MathUtils.lerp(ambientDay.color.b, ambientTunnel.color.b, t), 1f))
        } else env.set(ambientDay)

        // ── camera rig ─────────────────────────────────────────────────
        // v4.2: follow 0.85 / look 0.95 — the old 0.58 follow let lane changes
        // throw the runner to the screen edge (QA shots 6-9)
        val followX = player.x * 0.85f
        val bob = if (player.state == PlayerState.RUNNING || player.state == PlayerState.LANE_SWITCH)
            sin(player.runPhase * 2f) * 0.035f else 0f
        // v4.1: camera rides up with jetpack/high flight so the runner stays
        // framed with the track visible far below (SS jetpack framing)
        val airLift = max(0f, player.jumpY - 1.6f)
        cam.position.set(followX + shakeX * 0.012f, 2.62f + bob + airLift * 0.62f + shakeY * 0.01f, 4.9f)
        cam.lookAt(player.x * 0.95f, 1.12f + player.jumpY * 0.34f + airLift * 0.42f, -7f)
        cam.update()

        // ── scrolling ground strips ────────────────────────────────────
        // Strides slide TOWARD the camera as distance grows; strip 0 sits just
        // behind the runner, the last one far ahead (gz negative = ahead).
        val slide = distance % SEG
        for (i in 0 until NSEG) {
            val gz = slide + SEG * (1 - i)
            trackSegs[i].transform.setToTranslation(0f, -0.05f, gz)
            dirtL[i].transform.setToTranslation(-8.8f, -0.07f, gz)
            dirtR[i].transform.setToTranslation(8.8f, -0.07f, gz)
            wallL[i].transform.setToTranslation(-5.95f, 1.5f, gz)
            wallR[i].transform.setToTranslation(5.95f, 1.5f, gz)
            frame.add(trackSegs[i]); frame.add(dirtL[i]); frame.add(dirtR[i])
            frame.add(wallL[i]); frame.add(wallR[i])
        }

        // ── decorations ────────────────────────────────────────────────
        for (d in world.decos) {
            // v4.2: cull decos once they pass the runner — benches/signs at
            // z≈0 projected into giant dark slabs at the frame edges
            if (d.z < 0.5f || d.z > 92f) continue
            var di = decoMap.getOrPut(d) { DecoInst(ModelInstance(decoModel(d)), d.kind, d.variant) }
            // pooled Deco objects get repurposed — rebuild the instance when the shape changed
            if (di.kind != d.kind || di.variant != d.variant) {
                di = DecoInst(ModelInstance(decoModel(d)), d.kind, d.variant)
                decoMap[d] = di
            }
            placeDeco(di.inst, d, time)
            frame.add(di.inst)
        }
        // occasional cleanup of stale entries
        if ((time * 60f).toInt() % 300 == 0 && world.decos.isNotEmpty()) {
            decoMap.keys.removeAll { k -> world.decos.none { it === k } }
        }

        // ── tunnel interior lighting pass (v4.2) ─────────────────────
        // Warm tube lamps every 8u along each tunnel + soft halos — tunnels
        // used to be a flat dark box with nothing to look at.
        // (per-side pools: a pooled instance is welded to its model/side)
        var liL = 0; var liR = 0
        val lampModelL by lazy { factory.tunnelLamp(-1) }
        val lampModelR by lazy { factory.tunnelLamp(1) }
        for (r in world.tunnelRanges) {
            if (r[1] < 0f || r[0] > 80f) continue
            var z = (r[0] + 3f)
            while (z < r[1] - 1f) {
                if (z > -1f) {
                    val gz = -z
                    val side = if (((z / 8f).toInt() % 2 == 0)) -1 else 1
                    if (side < 0) {
                        val inst = poolGet(lampPool, liL++, "tlampL") { ModelInstance(lampModelL) }
                        inst.transform.setToTranslation(0f, 0f, gz)
                        frame.add(inst)
                    } else {
                        val inst = poolGet(lampPoolR, liR++, "tlampR") { ModelInstance(lampModelR) }
                        inst.transform.setToTranslation(0f, 0f, gz)
                        frame.add(inst)
                    }
                    val glow = poolGet(lampGlowPool, liL + liR - 1, "tglow") { ModelInstance(factory.glowBillboard(2.6f, TextureGen.glow)) }
                    billboard(glow, side * 4.35f, 5.2f, gz, 0.5f)
                    frame.add(glow)
                }
                z += 8f
            }
        }

        // ── trains (+ramps) ────────────────────────────────────────────
        var ci = 0; var ri = 0
        for (t in spawnerSystems.trains) {
            val zNear = t.z - t.totalLength
            val zFar = t.z
            if (zFar < -6f) continue
            for (lane in t.lanes) {
                val lx = lane * GameConfig.LANE_WIDTH
                var cz = zNear + GameConfig.TRAIN_CAR_LENGTH * 0.5f
                for (car in 0 until t.cars) {
                    if (cz > zFar + 1f) break
                    // v4.1: never render a car the camera is inside — close
                    // dodges put the cam within the car's x/z footprint and
                    // its interior filled the whole screen
                    val camInFootprint =
                        abs(cz + 4.9f) < GameConfig.TRAIN_CAR_LENGTH * 0.5f + 0.5f &&
                        abs(lx - cam.position.x) < 1.25f
                    if (cz > -2.6f && !camInFootprint) {
                        val inst = poolGet(carPool, ci++, "car") { ModelInstance(factory.trainCar(t.livery)) }
                        inst.transform.setToTranslation(lx, 0f, -cz)
                        frame.add(inst)
                    }
                    cz += GameConfig.TRAIN_CAR_LENGTH
                }
                // ramp leaning onto parked trains
                if (t.kind == 0) {
                    val rampGz = 3.15f - zNear
                    // v4.3: the wedge's bottom end swept INTO the lens during the
                    // climb (rampGz up to 3.15 = 1.75u from the camera → the
                    // giant yellow slab in QA shots 3/5). Cull once the nose is
                    // this close — the train takes over visually for the last
                    // stretch of the climb.
                    if (zNear > 1.4f) {
                        val r = poolGet(rampPool, ri++, "ramp") { ModelInstance(factory.ramp()) }
                        r.transform.setToTranslation(lx, 0f, rampGz)
                        frame.add(r)
                    }
                }
            }
        }

        // ── obstacles ──────────────────────────────────────────────────
        var oi = 0
        for (o in spawnerSystems.obstacles) {
            if (o.z < 0.6f || o.z > 80f) continue
            val inst = poolGet(obstaclePool, oi++, "obs") { ModelInstance(obstacleModel(o.kind)) }
            val lx = o.lane * GameConfig.LANE_WIDTH
            inst.transform.setToTranslation(lx, 0f, -o.z)
            frame.add(inst)
        }

        // ── coins ──────────────────────────────────────────────────────
        var kn = 0
        val spin = time * 3.2f
        for (c in spawnerSystems.coins) {
            // v4.1: cull coins BEFORE they reach the camera — near-passing
            // coins projected into giant sky blobs (shot-verified bug)
            if (c.collected || c.z < 2.2f || c.z > 85f) continue
            val inst = poolGet(coinPool, kn++, "coin") { ModelInstance(factory.coin()) }
            val wob = sin(time * 2.4f + c.phase) * 0.08f
            m.setToTranslation(c.x, c.y + wob, -c.z)
            m.rotate(Vector3(0f, 1f, 0f), (spin + c.phase) * 57.2958f)
            m.rotate(Vector3(1f, 0f, 0f), 90f) // face the camera like SS coins
            inst.transform.set(m)
            frame.add(inst)
        }

        // ── power-up pickups ───────────────────────────────────────────
        var pi = 0
        for (p in spawnerSystems.powerups) {
            if (p.taken || p.z < 2.2f || p.z > 85f) continue
            val lx = p.lane * GameConfig.LANE_WIDTH
            val y = 1.25f + sin(time * 2.2f + p.phase) * 0.12f
            val glowInst = poolGet(powerPool, pi++, "pow") { ModelInstance(factory.glowBillboard(1.5f, TextureGen.glow)) }
            billboard(glowInst, lx, y, -p.z, 0.8f)
            val iconInst = poolGet(powerPool, pi++, "powIcon") {
                ModelInstance(factory.texPlane("picon" + p.type, 0.85f, 0.85f, TextureGen.powerIcons[p.type]))
            }
            billboard(iconInst, lx, y, -p.z, 1f)
            frame.add(glowInst); frame.add(iconInst)
        }

        // ── blob shadows ───────────────────────────────────────────────
        var si = 0
        fun shadow(x: Float, z: Float, r: Float, shrink: Float) {
            val s = poolGet(shadowPool, si++, "sh") { ModelInstance(factory.shadowBlob(0.55f)) }
            val sc = (1.15f - shrink * 0.55f).coerceIn(0.35f, 1.15f) * r
            s.transform.setToTranslation(x, 0.02f, -z)
            s.transform.scale(sc, 1f, sc)
            frame.add(s)
        }
        shadow(player.x, 0f, 0.85f, player.jumpY)
        if (chaser.active) {
            // v4.3: shadows track the offset intro-chase positions
            val converge = if (chaser.catchT > 0f) (chaser.catchT * chaser.catchT).coerceIn(0f, 1f) else 0f
            val gx = guardX(player) + (1f - converge) * 0.95f
            val gz = chaserZ(chaser) - (1f - converge) * 0.55f
            shadow(gx, gz, 1.0f, 0f)
            shadow(gx + GameConfig.CHASER_DOG_OFFSET_X * 0.55f, gz + 0.1f, 0.7f, 0f)
        }

        // ── characters ─────────────────────────────────────────────────
        val h = getHuman(character)
        if (!blinkHide) {
            // v4.1 FIX: jumpY is the ABSOLUTE altitude (mirrors supportY on
            // roofs, arcs during jumps) — mesh y is jumpY alone. The old
            // supportY-only transform ground-locked jumps; adding supportY
            // double-lifts roof/jet flight out of frame.
            // v4.4: crash tumble drifts AWAY from the lens and settles to the
            // ground (the in-place spin swung limbs right into the camera)
            if (player.state == PlayerState.DEAD && !chaser.grabbed) {
                val t = (player.stateTime / 0.9f).coerceIn(0f, 1f)
                m.setToTranslation(player.x, player.jumpY * (1f - t) - 0.15f * t, -t * 0.85f)
            } else {
                m.setToTranslation(player.x, player.jumpY, 0f)
            }
            h.animate(m, player.state, player.runPhase, player.stateTime, player.lean,
                player.stateTime / player.curJumpDuration, stumbleOn, time, time, guardCatchFlag(chaser))
            for (p in h.rig.parts) frame.add(p.instance)
            // v4.1 jetpack thruster glow under the flyer
            if (jetOn) {
                val flicker = 0.75f + sin(time * 31f) * 0.2f
                val fy = player.jumpY - 0.32f
                val f = jetFlameInstance
                billboard(f, player.x, fy, 0.12f, 0.85f * flicker)
                frame.add(f)
                val fc = jetCoreInstance
                billboard(fc, player.x, fy - 0.08f, 0.12f, 0.38f * flicker)
                frame.add(fc)
            }
            // hoverboard under the feet
            if (boardOn) {
                val b = boardInstance
                b.transform.setToTranslation(player.x, player.jumpY + 0.12f, 0.1f)
                b.transform.rotate(Vector3(0f, 0f, 1f), -player.lean * 14f)
                frame.add(b)
                val g = boardGlowInstance
                billboard(g, player.x, player.jumpY + 0.05f, 0.15f, 0.7f)
                frame.add(g)
            }
        }

        // ── the chase: guard + dog ─────────────────────────────────────
        // v4.2 SIGN FIX: chaserZ() returns WORLD z (negative = behind the
        // runner); meshes need gz = -worldZ like every other entity.
        // v4.3 FRAMING FIX: during the intro the guard chased in the SAME lane
        // 2.2u behind — at 0.84 scale his cap/shoulders filled the bottom half
        // of the screen (QA shots 0-1). Now he chases one lane OVER and a touch
        // farther back at SS scale, converging into the runner's lane only for
        // the grab lunge.
        if (chaser.active) {
            val g = getGuard()
            val converge = if (chaser.catchT > 0f) (chaser.catchT * chaser.catchT).coerceIn(0f, 1f) else 0f
            val gx = guardX(player) + (1f - converge) * 0.95f
            val guardWorldZ = chaserZ(chaser) - (1f - converge) * 0.55f
            val gz = -guardWorldZ
            m.setToTranslation(gx, 0f, gz)
            m.scale(0.68f, 0.68f, 0.68f)
            if (chaser.grabbed) {
                // grab pose: guard lunges onto the runner
                g.animate(m, PlayerState.DEAD, chaser.runPhase, 0f, 0f, 0f, false, time, time, true)
            } else {
                g.animate(m, PlayerState.RUNNING, chaser.runPhase, 0f, chaser.lean, 0f, false, time, time, false)
            }
            for (p in g.rig.parts) frame.add(p.instance)
            // dog gallops beside the guard (a touch further back)
            m2.setToTranslation(gx + GameConfig.CHASER_DOG_OFFSET_X * 0.55f, 0f, gz - 0.3f)
            m2.scale(0.56f, 0.56f, 0.56f)
            dog.animate(m2, chaser.dogPhase, time)
            for (p in dog.rig.parts) frame.add(p.instance)
        }

        modelBatch.begin(cam)
        modelBatch.render(frame, env)
        modelBatch.end()

        // horizon haze drawn over the far 3D world
        drawHaze(menuDim)
    }

    // ── helpers ────────────────────────────────────────────────────────

    private val boardInstance by lazy { ModelInstance(boardModel) }
    private val boardGlowInstance by lazy { ModelInstance(boardGlow) }

    private fun guardCatchFlag(c: Chaser) = c.grabbed

    private fun guardX(player: Player): Float =
        player.x * 0.85f  // guard tracks the runner's lane with lag

    private fun chaserZ(c: Chaser): Float = when {
        // v4.2: rush lands just BEHIND the runner (world z -0.45) — the old
        // +0.1 put the grab inside the player mesh
        c.catchT > 0f -> -2.2f + (c.catchT * c.catchT) * 1.75f
        c.close -> GameConfig.CHASER_Z_CLOSE
        else -> GameConfig.CHASER_Z
    }

    private inline fun poolGet(pool: ArrayList<ModelInstance>, idx: Int, tag: String, create: () -> ModelInstance): ModelInstance {
        while (pool.size <= idx) pool.add(create())
        return pool[idx]
    }

    private fun getHuman(ch: CharacterDef): Human3D {
        if (human == null || humanCharId != ch.id) {
            human = Human3D(factory, ch.skin, ch.hoodie, ch.pants, ch.shoes, ch.cap, ch.backpack,
                ch.accent, ch.hair, ch.vest, ch.hoodLining, ch.capPanel, ch.accessory, isGuard = false)
            humanCharId = ch.id
        }
        return human!!
    }

    private fun getGuard(): Human3D {
        if (guard == null) {
            guard = Human3D(factory, 0xe8a97eff.toInt(), 0x2c3a58ff.toInt(), 0x25314aff.toInt(),
                0x1c2028ff.toInt(), 0x25314aff.toInt(), 0x25314aff.toInt(),
                0xffd23eff.toInt(), 0x4a3a2aff.toInt(), 0, 0, 0, 0, isGuard = true)
        }
        return guard!!
    }

    private fun billboard(inst: ModelInstance, x: Float, y: Float, gz: Float, scale: Float) {
        inst.transform.setToTranslation(x, y, gz)
        val yaw = MathUtils.atan2(cam.direction.x, cam.direction.z) * MathUtils.radiansToDegrees
        inst.transform.rotate(Vector3(0f, 1f, 0f), yaw)
        inst.transform.scale(scale, scale, scale)
    }

    // ── deco → model/transform ─────────────────────────────────────────
    private fun decoModel(d: Deco) = when (d.kind) {
        DecoKind.BUILDING, DecoKind.SKYSCRAPER -> {
            val tex = if (d.kind == DecoKind.SKYSCRAPER) TextureGen.glassTex
            else TextureGen.facades[d.variant % TextureGen.facades.size]
            factory.building(1f, 1f, 1f, tex)
        }
        DecoKind.POLE -> factory.pole()
        DecoKind.BILLBOARD -> factory.billboard(d.variant)
        DecoKind.LAMP -> factory.lamp()
        DecoKind.TREE -> factory.tree()
        DecoKind.BUSH -> factory.bush()
        DecoKind.PLATFORM -> factory.platform()
        DecoKind.SHELTER -> factory.shelter()
        DecoKind.STATION_SIGN -> factory.stationSign()
        DecoKind.BENCH -> factory.bench()
        DecoKind.SIGNAL -> factory.signal(d.variant == 0)
        DecoKind.GRAFFITI_WALL -> factory.graffitiWall()
        DecoKind.BRIDGE_GIRDER -> factory.girder()
        DecoKind.TUNNEL_ARCH -> factory.tunnelArch()
        DecoKind.TUNNEL_WALL -> factory.tunnelWall()
        DecoKind.PASSENGER -> factory.passenger(d.variant)
        else -> factory.bush()
    }

    private fun placeDeco(inst: ModelInstance, d: Deco, time: Float) {
        val gz = -d.z
        when (d.kind) {
            DecoKind.BUILDING, DecoKind.SKYSCRAPER -> {
                inst.transform.setToTranslation(d.x, 0f, gz)
                inst.transform.scale(d.w, d.h, d.w * 0.85f)
            }
            DecoKind.PLATFORM -> {
                inst.transform.setToTranslation(d.x, 0f, gz)
                inst.transform.scale(1f, 1f, d.w / 24f)
            }
            DecoKind.LAMP, DecoKind.BENCH, DecoKind.STATION_SIGN, DecoKind.TREE, DecoKind.POLE ->
                if (d.side < 0) {
                    inst.transform.setToRotation(Vector3(0f, 1f, 0f), 180f)
                    inst.transform.trn(d.x, 0f, gz)
                } else inst.transform.setToTranslation(d.x, 0f, gz)
            DecoKind.PASSENGER -> {
                inst.transform.setToTranslation(d.x, 0f, gz)
                inst.transform.rotate(Vector3(0f, 1f, 0f), (d.variant * 67) % 360f)
            }
            else -> inst.transform.setToTranslation(d.x, 0f, gz)
        }
    }

    private fun obstacleModel(kind: ObstacleKind) = when (kind) {
        ObstacleKind.LOW_BARRIER -> factory.barrierLow()
        ObstacleKind.HIGH_BARRIER -> factory.barrierHigh()
        ObstacleKind.BLOCKADE -> factory.blockade()
        ObstacleKind.GATE -> factory.gate()
        ObstacleKind.FENCE_FULL -> factory.fenceFull()
    }

    // ── 2D sky / haze (drawn around the 3D pass) ───────────────────────
    private fun drawSky(distance: Float, sx: Float, sy: Float) {
        val vw = proj.vw; val vh = proj.vh
        batch.begin()
        batch.setColor(1f, 1f, 1f, 1f)
        batch.draw(TextureGen.sky, sx, proj.horizonY + sy, vw, vh - proj.horizonY)
        val sunX = vw * 0.68f - proj.camX * 6f
        batch.setColor(1f, 0.92f, 0.7f, 0.5f)
        batch.draw(TextureGen.glow, sunX - 130f, proj.horizonY + 60f - 130f, 260f, 260f)
        batch.setColor(1f, 0.98f, 0.88f, 0.95f)
        batch.draw(TextureGen.glow, sunX - 42f, proj.horizonY + 60f - 42f, 84f, 84f)
        val t = distance * 0.02f
        drawCloud(TextureGen.cloudA, t * 8f + 40f, vh * 0.86f, 1f, 0.85f, sx)
        drawCloud(TextureGen.cloudA, t * 5f + 520f, vh * 0.92f, 0.8f, 0.7f, sx)
        drawCloud(TextureGen.cloudB, t * 12f + 240f, vh * 0.78f, 0.9f, 0.55f, sx)
        drawCloud(TextureGen.cloudB, t * 9f + 640f, vh * 0.83f, 0.7f, 0.5f, sx)
        drawTiled(TextureGen.skylineFar, distance * 1.2f, proj.horizonY - 6f, 0.9f, sx, sy)
        drawTiled(TextureGen.skylineNear, distance * 3.4f, proj.horizonY - 14f, 1f, sx, sy)
        batch.end()
    }

    private fun drawCloud(tex: Texture, scroll: Float, y: Float, scale: Float, alpha: Float, sx: Float) {
        val vw = proj.vw
        val w = tex.width * scale
        var x = ((scroll % (vw + w)) - w)
        batch.setColor(1f, 1f, 1f, alpha)
        batch.draw(tex, x + sx, y, w, tex.height * scale)
        batch.draw(tex, x + vw + w + sx, y, w, tex.height * scale)
        batch.setColor(1f, 1f, 1f, 1f)
    }

    private fun drawTiled(tex: Texture, scroll: Float, bottomY: Float, alpha: Float, sx: Float, sy: Float) {
        val vw = proj.vw
        val scale = vw / tex.width * 1.6f
        val w = tex.width * scale
        val h = tex.height * scale
        var off = scroll % w
        if (off < 0) off += w
        batch.setColor(1f, 1f, 1f, alpha)
        var x = -off
        while (x < vw) {
            batch.draw(tex, x + sx, bottomY + sy, w, h)
            x += w
        }
        batch.setColor(1f, 1f, 1f, 1f)
    }

    private val hazeC = Color()
    /** Soft symmetric haze hugging the horizon line (v4.1 — the old one-side
     *  fade put a hard cream line across mid-screen). */
    private fun drawHaze(menuDim: Float) {
        val vw = proj.vw; val vh = proj.vh
        val fogH = vh * 0.13f
        batch.begin()
        batch.setColor(1f, 1f, 1f, 0.5f)
        batch.draw(TextureGen.hazeBand, 0f, proj.horizonY - fogH * 0.5f, vw, fogH)
        batch.setColor(1f, 1f, 1f, 1f)
        batch.end()
        if (menuDim > 0.01f) {
            hazeC.set(0.12f, 0.14f, 0.34f, menuDim * 0.42f)
            Gdx.gl.glEnable(GL20.GL_BLEND)
            com.badlogic.gdx.graphics.glutils.ShapeRenderer().let { }
            batch.begin()
            batch.setColor(hazeC)
            batch.draw(TextureGen.white, 0f, 0f, vw, vh)
            batch.end()
            Gdx.gl.glDisable(GL20.GL_BLEND)
        }
    }

    fun dispose() {
        modelBatch.dispose()
        factory.dispose()
    }
}
