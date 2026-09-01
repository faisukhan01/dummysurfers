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
    private val decoMap = HashMap<Deco, ModelInstance>(160)

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

    private val boardModel by lazy { factory.colorBox("hoverboard", 0.62f, 0.09f, 1.5f, 0x2fd0bfff.toInt()) }
    private val boardGlow by lazy { factory.glowBillboard(1.2f, TextureGen.glow) }

    // scratch
    private val m = Matrix4()
    private val m2 = Matrix4()
    private val v = Vector3()

    init {
        env.set(ambientDay)
        env.add(sun)
        // build recycled strips once
        val trackM = factory.texBox("trackSeg", 10.6f, 0.1f, SEG, TextureGen.trackTex, 1.42f, 2f)
        val dirtM = factory.texBox("dirtSeg", 7.5f, 0.08f, SEG, TextureGen.dirtTex, 1f, 2f)
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
        val followX = player.x * 0.45f
        val bob = if (player.state == PlayerState.RUNNING || player.state == PlayerState.LANE_SWITCH)
            sin(player.runPhase * 2f) * 0.035f else 0f
        cam.position.set(followX + shakeX * 0.012f, 2.62f + bob + shakeY * 0.01f, 4.9f)
        cam.lookAt(player.x * 0.62f, 1.12f + player.jumpY * 0.32f, -7f)
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
            if (d.z < -14f || d.z > 92f) continue
            val inst = decoMap.getOrPut(d) { ModelInstance(decoModel(d)) }
            placeDeco(inst, d, time)
            frame.add(inst)
        }
        // occasional cleanup of stale entries
        if ((time * 60f).toInt() % 300 == 0 && world.decos.isNotEmpty()) {
            decoMap.keys.removeAll { k -> world.decos.none { it === k } }
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
                    if (cz > -5.5f) {
                        val inst = poolGet(carPool, ci++, "car") { ModelInstance(factory.trainCar(t.livery)) }
                        inst.transform.setToTranslation(lx, 0f, -cz)
                        frame.add(inst)
                    }
                    cz += GameConfig.TRAIN_CAR_LENGTH
                }
                // ramp leaning onto parked trains
                if (t.kind == 0) {
                    val rampGz = 3.15f - zNear
                    if (rampGz > -5f && zNear > 0f) {
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
            if (o.z < -1.2f || o.z > 80f) continue
            val inst = poolGet(obstaclePool, oi++, "obs") { ModelInstance(obstacleModel(o.kind)) }
            val lx = o.lane * GameConfig.LANE_WIDTH
            inst.transform.setToTranslation(lx, 0f, -o.z)
            frame.add(inst)
        }

        // ── coins ──────────────────────────────────────────────────────
        var kn = 0
        val spin = time * 3.2f
        for (c in spawnerSystems.coins) {
            if (c.collected || c.z < -3f || c.z > 85f) continue
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
            if (p.taken || p.z < -3f || p.z > 85f) continue
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
        if (chaser.active) shadow(guardX(player), chaserZ(chaser), 1.0f, 0f)
        if (chaser.active) shadow(guardX(player) + 0.62f, chaserZ(chaser) + 0.1f, 0.7f, 0f)

        // ── characters ─────────────────────────────────────────────────
        val h = getHuman(character)
        if (!blinkHide) {
            m.setToTranslation(player.x, player.supportY, 0f)
            h.animate(m, player.state, player.runPhase, player.stateTime, player.lean,
                player.stateTime / player.curJumpDuration, stumbleOn, time, time, guardCatchFlag(chaser))
            for (p in h.rig.parts) frame.add(p.instance)
            // hoverboard under the feet
            if (boardOn) {
                val b = boardInstance
                b.transform.setToTranslation(player.x, player.supportY + 0.12f + player.jumpY, 0.1f)
                b.transform.rotate(Vector3(0f, 0f, 1f), -player.lean * 14f)
                frame.add(b)
                val g = boardGlowInstance
                billboard(g, player.x, player.supportY + player.jumpY + 0.05f, 0.15f, 0.7f)
                frame.add(g)
            }
        }

        // ── the chase: guard + dog ─────────────────────────────────────
        if (chaser.active) {
            val g = getGuard()
            val gx = guardX(player)
            val gz = chaserZ(chaser)
            m.setToTranslation(gx, 0f, gz)
            m.scale(0.92f, 0.92f, 0.92f)
            if (chaser.grabbed) {
                // grab pose: guard lunges onto the runner
                g.animate(m, PlayerState.DEAD, chaser.runPhase, 0f, 0f, 0f, false, time, time, true)
            } else {
                g.animate(m, PlayerState.RUNNING, chaser.runPhase, 0f, chaser.lean, 0f, false, time, time, false)
            }
            for (p in g.rig.parts) frame.add(p.instance)
            // dog gallops beside the guard
            m2.setToTranslation(gx + GameConfig.CHASER_DOG_OFFSET_X, 0f, gz + 0.12f)
            m2.scale(0.8f, 0.8f, 0.8f)
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
        c.catchT > 0f -> -2.2f + (c.catchT * c.catchT) * 2.3f
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
                ch.accent, ch.hair, ch.vest, ch.hoodLining, ch.capPanel, isGuard = false)
            humanCharId = ch.id
        }
        return human!!
    }

    private fun getGuard(): Human3D {
        if (guard == null) {
            guard = Human3D(factory, 0xe8a97eff.toInt(), 0x2c3a58ff.toInt(), 0x25314aff.toInt(),
                0x1c2028ff.toInt(), 0x25314aff.toInt(), 0x25314aff.toInt(),
                0xffd23eff.toInt(), 0x4a3a2aff.toInt(), 0, 0, 0, isGuard = true)
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
    private fun drawHaze(menuDim: Float) {
        val vw = proj.vw; val vh = proj.vh
        val fogH = vh * 0.14f
        batch.begin()
        batch.setColor(1f, 1f, 1f, 1f)
        batch.draw(TextureGen.fog, 0f, proj.horizonY - fogH * 0.45f, vw, fogH)
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
