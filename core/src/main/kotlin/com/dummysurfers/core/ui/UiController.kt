package com.dummysurfers.core.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.Align
import com.dummysurfers.core.config.GameConfig
import com.dummysurfers.core.entities.CharacterDef
import com.dummysurfers.core.gfx.Palette
import com.dummysurfers.core.gfx.TextureGen
import com.dummysurfers.core.state.GameEvent
import com.dummysurfers.core.state.GameState
import com.dummysurfers.core.state.MenuPanel
import com.dummysurfers.core.state.MissionType
import com.dummysurfers.core.state.ShopTab
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Immediate-mode game UI: menu, HUD, pause, game over, shop, missions,
 * settings, tutorial overlay. Game-style (chunky buttons, arcade font),
 * NOT Material Design. All hit-testing against a virtual 720x1280 stage.
 */
class UiController(val theme: UiTheme) : InputAdapter() {
    private val batch get() = bridge!!.batch
    private val sr get() = bridge!!.sr

    var bridge: Bridge? = null
    private val vw = GameConfig.VIRTUAL_WIDTH
    private val vh = GameConfig.VIRTUAL_HEIGHT

    interface Bridge {
        val batch: SpriteBatch
        val sr: ShapeRenderer
        val state: GameState
        val menuPanel: MenuPanel
        val shopTab: ShopTab
        var shopTabSet: ShopTab
        var tutorialStep: Int?
        val score: Int
        val displayScore: Int
        val runCoins: Int
        val distance: Float
        val multiplier: Int
        val powerupRemaining: FloatArray // 5
        val powerupTotal: FloatArray     // 5
        val boardTimer: Float            // hoverboard ride time left (0 = not riding)
        val boardTotal: Float            // hoverboard ride duration
        val newBest: Boolean
        val guardCatch: Boolean
        val save: com.dummysurfers.core.state.SaveManager
        val toFrame: (FloatArray) -> Unit // converts screen touch to virtual coords

        fun startRun()
        fun pauseGame()
        fun resumeGame()
        fun restartRun()
        fun toMenu()
        fun openPanel(p: MenuPanel)
        fun closePanel()
        fun selectCharacter(id: String)
        fun buyCharacter(id: String)
        fun buyUpgrade(name: String)
        fun buyHoverboard()
        fun activateBoard()
        fun buyTrail(index: Int)
        fun claimMission(index: Int)
        fun setMusic(on: Boolean)
        fun setSfx(on: Boolean)
        fun setVibration(on: Boolean)
        fun resetProgress()
        fun click()
    }

    // ── Input plumbing ─────────────────────────────────────────────────
    private class HitRect(val id: String, val x: Float, val y: Float, val w: Float, val h: Float, val action: () -> Unit)
    private val hits = ArrayList<HitRect>(48)
    private var pressedId: String? = null
    private var clickId: String? = null
    private var touchVirtualX = 0f
    private var touchVirtualY = 0f
    private var scrollDragStart = 0f
    private var scrollDragValue = 0f
    private var dragging = false

    var scrollY = 0f
    private var toastMsg: String? = null
    private var toastTimer = 0f

    fun toast(msg: String) {
        toastMsg = msg
        toastTimer = 1.6f
    }

    private fun panelOpen() = bridge!!.menuPanel != MenuPanel.NONE || bridge!!.state == GameState.GAME_OVER || bridge!!.state == GameState.PAUSED

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        val b = bridge ?: return false
        if (!panelOpen() && b.state != GameState.PLAYING && b.state != GameState.TUTORIAL) return false
        if (!panelOpen() && (b.state == GameState.PLAYING || b.state == GameState.TUTORIAL)) {
            // only the pause button steals touches during play
            val v = FloatArray(2)
            b.toFrame(v)
            touchVirtualX = v[0]; touchVirtualY = v[1]
            val hit = hits.lastOrNull { (it.id == "pause" || it.id == "board") && inside(it, touchVirtualX, touchVirtualY) }
            if (hit != null) { pressedId = hit.id; return true }
            return false
        }
        val v = FloatArray(2)
        b.toFrame(v)
        touchVirtualX = v[0]; touchVirtualY = v[1]
        val hit = hits.lastOrNull { inside(it, touchVirtualX, touchVirtualY) }
        if (hit != null) {
            pressedId = hit.id
            dragging = false
            return true
        }
        // start scroll drag inside scrollable content
        if (scrollable()) {
            dragging = true
            scrollDragStart = touchVirtualY
            scrollDragValue = scrollY
        }
        return true
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        if (!dragging) return panelOpen()
        val v = FloatArray(2)
        bridge!!.toFrame(v)
        val dy = v[1] - scrollDragStart
        scrollY = (scrollDragValue - dy).coerceIn(0f, maxScroll())
        return true
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        val wasPressed = pressedId
        pressedId = null
        dragging = false
        if (wasPressed == null) return panelOpen()
        val v = FloatArray(2)
        bridge!!.toFrame(v)
        val hit = hits.lastOrNull { it.id == wasPressed && inside(it, v[0], v[1]) }
        if (hit != null) {
            clickId = hit.id
        }
        return true
    }

    private fun inside(h: HitRect, x: Float, y: Float) = x >= h.x - 8 && x <= h.x + h.w + 8 && y >= h.y - 8 && y <= h.y + h.h + 8

    private fun scrollable() = bridge!!.menuPanel == MenuPanel.SHOP || bridge!!.menuPanel == MenuPanel.MISSIONS || bridge!!.menuPanel == MenuPanel.CHARACTERS

    private fun maxScroll(): Float = when (bridge!!.menuPanel) {
        MenuPanel.SHOP -> when (bridge!!.shopTab) {
            ShopTab.CHARACTERS -> 60f
            ShopTab.UPGRADES -> 260f
            ShopTab.TRAILS -> 40f
        }
        MenuPanel.CHARACTERS -> 120f
        MenuPanel.MISSIONS -> 60f
        else -> 0f
    }

    /** Registers + draws a button. Fires [action] on release-inside. */
    private fun btn(id: String, x: Float, y: Float, w: Float, h: Float, color: Color, label: String, font: BitmapFont? = null, labelColor: Color = Palette.UI_TEXT, enabled: Boolean = true): Boolean {
        hits.add(HitRect(id, x, y, w, h) {})
        val pressed = pressedId == id && enabled
        theme.button(batch, x, y, w, h, if (enabled) color else Color(0x5a5148ff.toInt()), pressed)
        val f = font ?: theme.fontMed
        theme.text(batch, f, label, x, y + h / 2f + f.capHeight / 2f, labelColor, Align.center, w)
        val clicked = clickId == id && enabled
        if (clicked) bridge!!.click()
        return clicked
    }

    fun flushFrame() {
        hits.clear()
    }

    /** Clears one-shot click flags after the frame consumed them. */
    fun endFrame() {
        clickId = null
    }

    // ════════════════════════════════════════════════════════════════════
    //  MENU
    // ════════════════════════════════════════════════════════════════════
    fun drawMenu(time: Float) {
        val b = bridge!!
        // top currency pills (navy/gold, SS style)
        chipCoins(vw - 250f, vh - 96f)
        // settings gear shortcut top-left
        if (btn("gear", 24f, vh - 100f, 76f, 76f, Palette.UI_NAVY, "O", theme.fontMed)) b.openPanel(MenuPanel.SETTINGS)

        // v3.0: warm sun glow behind the logo block (SS title-screen warmth)
        val glowPulse = 0.30f + sin(time * 1.4f) * 0.05f
        batch.setColor(1f, 0.92f, 0.55f, glowPulse)
        batch.draw(TextureGen.glow, vw / 2f - 330f, 940f, 660f, 660f)
        batch.setColor(1f, 1f, 1f, 1f)

        // graffiti-style logo with bounce (double-draw: navy drop + main)
        val bounce = sin(time * 2.2f) * 8f
        theme.text(batch, theme.fontHuge, "DUMMY", 4f, 1147f + bounce, Palette.UI_OUTLINE, Align.center, vw)
        theme.text(batch, theme.fontHuge, "DUMMY", 0f, 1150f + bounce, Palette.GOLD, Align.center, vw)
        theme.text(batch, theme.fontHuge, "SURFERS", 4f, 1079f + bounce, Palette.UI_OUTLINE, Align.center, vw)
        theme.text(batch, theme.fontHuge, "SURFERS", 0f, 1082f + bounce, Color.WHITE, Align.center, vw)
        // orange "BY FSK" tag chip
        val tagW = 150f
        theme.button(batch, vw / 2f - tagW / 2f, 1002f + bounce, tagW, 44f, Palette.UI_ORANGE, false)
        theme.text(batch, theme.fontSmall, "BY FSK", 0f, 1031f + bounce, Color.WHITE, Align.center, vw)

        // selected character preview front & center on the tracks (big SS-style hero)
        val selIdx = CharacterDef.ALL.indexOfFirst { it.id == b.save.selectedCharacter }.coerceAtLeast(0)
        drawMiniCharacter(selIdx, vw / 2f - 130f, 704f + sin(time * 1.7f) * 7f, 260f)

        // HIGH SCORE card (periwinkle + deep slot + gold star)
        val hcW = 460f
        val hcX = vw / 2f - hcW / 2f
        theme.panel(batch, hcX, 560f, hcW, 108f, Palette.UI_PANEL)
        theme.panel(batch, hcX + 18f, 576f, hcW - 36f, 56f, Palette.UI_PANEL_DEEP)
        batch.setColor(1f, 1f, 1f, 1f)
        batch.draw(TextureGen.powerIcons[1], hcX + 22f, 580f, 48f, 48f)
        theme.text(batch, theme.fontTiny, "HIGH SCORE", hcX + 80f, 638f, Palette.UI_MUTED)
        theme.text(batch, theme.fontMed, "${b.save.best}", hcX + 80f, 616f, Palette.GOLD)
        // hoverboard rack count
        theme.text(batch, theme.fontTiny, "HOVERBOARDS x${b.save.hoverboards}", 0f, 532f, Palette.UI_MUTED, Align.center, vw)

        // giant gold RUN button
        if (btn("play", vw / 2 - 210f, 384f, 420f, 136f, Palette.UI_GOLD_BTN, "RUN", theme.fontLarge)) {
            b.startRun()
        }

        // bottom tab bar (navy tabs, SS layout)
        val tabW = (vw - 24f * 2 - 12f * 3) / 4f
        val tabY = 96f
        val tabs = arrayOf("chars" to "CHARS", "shop" to "SHOP", "tasks" to "TASKS", "setup" to "SETUP")
        tabs.forEachIndexed { i, (id, label) ->
            val x = 24f + i * (tabW + 12f)
            hits.add(HitRect(id, x, tabY, tabW, 128f) {})
            theme.button(batch, x, tabY, tabW, 128f, Palette.UI_NAVY, pressedId == id)
            theme.text(batch, theme.fontSmall, label, x, tabY + 38f, Color.WHITE, Align.center, tabW)
            when (id) {
                "chars" -> if (clickId == id) b.openPanel(MenuPanel.CHARACTERS)
                "shop" -> if (clickId == id) b.openPanel(MenuPanel.SHOP)
                "tasks" -> if (clickId == id) b.openPanel(MenuPanel.MISSIONS)
                "setup" -> if (clickId == id) b.openPanel(MenuPanel.SETTINGS)
            }
        }
        // red "!" badge when missions are claimable
        val ready = b.save.missions.count { !it.claimed && it.progress >= it.goal }
        if (ready > 0) {
            val bx = 24f + 2 * (tabW + 12f) + tabW - 26f
            theme.button(batch, bx, tabY + 96f, 44f, 44f, Palette.DANGER, false)
            theme.text(batch, theme.fontSmall, "!", bx, tabY + 128f, Color.WHITE, Align.center, 44f)
        }

        theme.text(batch, theme.fontTiny, "SWIPE TO MOVE — UP JUMP — DOWN SLIDE", 0f, 52f, Color.WHITE, Align.center, vw)
    }

    private fun chipCoins(x: Float, y: Float) {
        val b = bridge!!
        val label = "${b.save.totalCoins}"
        val w = max(170f, theme.textWidth(theme.fontMed, label) + 92f)
        theme.button(batch, x, y, w, 64f, Palette.UI_GOLD_BTN, false)
        theme.coinIcon(batch, x + 14f, y + 10f, 44f)
        theme.text(batch, theme.fontMed, label, x + 64f, y + 40f, Color.WHITE)
    }

    private fun chip(label: String, x: Float, y: Float, color: Color) {
        val w = theme.textWidth(theme.fontSmall, label) + 44f
        theme.panel(batch, x, y, w, 56f, color)
        theme.text(batch, theme.fontSmall, label, x + 22f, y + 36f, Color.WHITE)
    }

    // ════════════════════════════════════════════════════════════════════
    //  HUD
    // ════════════════════════════════════════════════════════════════════
    fun drawHud(time: Float) {
        val b = bridge!!
        // gold coin pill — top-left (SS style)
        val pillH = 60f
        val pillW = max(170f, theme.textWidth(theme.fontMed, "${b.runCoins}") + 100f)
        theme.button(batch, 20f, vh - 96f, pillW, pillH, Palette.UI_GOLD_BTN, false)
        theme.coinIcon(batch, 32f, vh - 87f, 42f)
        theme.text(batch, theme.fontMed, "${b.runCoins}", 84f, vh - 54f, Color.WHITE)

        // big score — top-center, white w/ navy outline (font baked)
        theme.text(batch, theme.fontHuge, "${b.score}", 0f, vh - 34f, Color.WHITE, Align.center, vw)
        // x2 star chip beside score when multiplier is up
        if (b.multiplier > 1) {
            val chipW = 74f
            val scoreW = theme.textWidth(theme.fontHuge, "${b.score}")
            val chipX = vw / 2f - scoreW / 2f - chipW - 16f
            theme.button(batch, chipX, vh - 96f, chipW, 58f, Palette.UI_GOLD_BTN, false)
            theme.text(batch, theme.fontSmall, "x${b.multiplier}", chipX, vh - 55f, Color.WHITE, Align.center, chipW)
        }
        // distance under score
        theme.text(batch, theme.fontSmall, "${b.distance.toInt()}m", 0f, vh - 118f, Color.WHITE, Align.center, vw)

        // v3.0: live "BEST!" flag the moment the current run passes the record
        if (!b.newBest && b.save.best > 0 && b.score > b.save.best) {
            val bob = sin(time * 6f) * 4f
            val bw = 150f
            val bx = vw / 2f - bw / 2f
            theme.button(batch, bx, vh - 200f + bob, bw, 54f, Palette.UI_GREEN, false)
            theme.text(batch, theme.fontSmall, "★ BEST!", bx, vh - 163f + bob, Color.WHITE, Align.center, bw)
        }

        // pause button — orange rounded square, white bars (top-right)
        val pauseX = vw - 92f
        val pauseY = vh - 96f
        hits.add(HitRect("pause", pauseX, pauseY, 68f, 68f) {})
        theme.button(batch, pauseX, pauseY, 68f, 68f, Palette.UI_ORANGE, pressedId == "pause")
        sr.begin(ShapeRenderer.ShapeType.Filled)
        sr.setColor(1f, 1f, 1f, 1f)
        sr.rect(pauseX + 20f, pauseY + 17f, 8f, 34f)
        sr.rect(pauseX + 40f, pauseY + 17f, 8f, 34f)
        sr.end()

        // hoverboard chip — bottom-right: tap it (or double-tap anywhere) to ride
        val boards = b.save.hoverboards
        val bt = b.boardTimer
        if (boards > 0 || bt > 0f) {
            val chipW = 110f
            val chipH = 92f
            val chipX = vw - chipW - 24f
            val chipY = 30f
            hits.add(HitRect("board", chipX, chipY, chipW, chipH) {})
            val active = bt > 0f
            theme.button(batch, chipX, chipY, chipW, chipH, if (active) Palette.UI_ACCENT2 else Palette.UI_NAVY, pressedId == "board")
            val ix = chipX + chipW / 2f
            val iy = chipY + chipH / 2f + 8f
            sr.begin(ShapeRenderer.ShapeType.Filled)
            sr.setColor(if (active) Color.WHITE else Palette.UI_ACCENT2)
            sr.rect(ix - 32f, iy - 7f, 64f, 14f)
            sr.circle(ix - 32f, iy, 7f)
            sr.circle(ix + 32f, iy, 7f)
            sr.setColor(Palette.GOLD)
            sr.rect(ix - 32f, iy - 2f, 64f, 4f)
            sr.end()
            if (active) {
                val t = if (b.boardTotal > 0f) (bt / b.boardTotal).coerceIn(0f, 1f) else 0f
                sr.begin(ShapeRenderer.ShapeType.Filled)
                sr.setColor(Palette.UI_PANEL_DEEP)
                sr.rect(chipX + 12f, chipY + 14f, chipW - 24f, 10f)
                sr.setColor(Color.WHITE)
                sr.rect(chipX + 12f, chipY + 14f, (chipW - 24f) * t, 10f)
                sr.end()
            } else {
                theme.text(batch, theme.fontTiny, "x$boards", chipX, chipY + 12f, Color.WHITE, Align.center, chipW)
            }
            if (clickId == "board" && !active) b.activateBoard()
            // first-runs hint
            if (!active && boards > 0 && b.save.stats.runs < 3 && b.distance < 80f) {
                theme.text(batch, theme.fontTiny, "TAP BOARD = 2ND CHANCE (OR DOUBLE-TAP)", 0f, 14f, Color.WHITE, Align.center, vw)
            }
        }

        // active power-ups — SS hoverboard-style segmented meter, bottom-center
        var activeCount = 0
        for (i in 0 until 5) {
            val rem = b.powerupRemaining[i]
            if (rem > 0f) {
                activeCount++
                val total = b.powerupTotal[i]
                val t = rem / total
                val flashing = rem < 3f && (time * 4f).toInt() % 2 == 0
                val color = powerColor(i)
                val mW = 320f
                val mX = vw / 2f - mW / 2f
                val mY = 120f - (activeCount - 1) * 74f
                theme.panel(batch, mX, mY, mW, 56f, Palette.UI_PANEL)
                theme.panel(batch, mX + 8f, mY + 8f, mW - 16f, 40f, Palette.UI_PANEL_DEEP)
                batch.setColor(1f, 1f, 1f, if (flashing) 0.55f else 1f)
                batch.draw(TextureGen.powerIcons[i], mX + 14f, mY + 8f, 40f, 40f)
                batch.setColor(1f, 1f, 1f, 1f)
                // segmented fill (5 segments like the SS board meter)
                sr.begin(ShapeRenderer.ShapeType.Filled)
                val segs = 5
                val segW = (mW - 76f) / segs
                val filled = ceil(t * segs).toInt().coerceIn(0, segs)
                for (sg in 0 until filled) {
                    sr.setColor(color)
                    sr.rect(mX + 62f + sg * segW + 2f, mY + 12f, segW - 4f, 32f)
                }
                sr.end()
            }
        }

        drawFirstRunHints(time)
    }

    /**
     * v3.0: non-blocking SS-style guidance for brand-new players. A bobbing
     * gold chip cycles the three moves over the first meters of the first two
     * runs — it never interrupts play (the old forced tutorial did).
     */
    private fun drawFirstRunHints(time: Float) {
        val b = bridge!!
        if (b.save.stats.runs >= 2 || b.distance > 90f) return
        val d = b.distance
        val msg = when {
            d < 14f -> "SWIPE LEFT / RIGHT TO CHANGE LANES"
            d < 26f -> "SWIPE UP TO JUMP"
            d < 38f -> "SWIPE DOWN TO ROLL"
            d < 58f -> "GRAB THE COINS!"
            else -> "DODGE THE TRAINS — THE COP IS CHASING!"
        }
        val bob = sin(time * 3.1f) * 5f
        val w = theme.textWidth(theme.fontSmall, msg) + 64f
        val x = vw / 2f - w / 2f
        val y = 210f + bob
        theme.button(batch, x, y, w, 58f, Palette.UI_GOLD_BTN, false)
        theme.text(batch, theme.fontSmall, msg, x, y + 37f, Color.WHITE, Align.center, w)
    }

    private fun powerColor(i: Int): Color = when (i) {
        0 -> Color(0xef4444ff.toInt())
        1 -> Color(0xf59e0bff.toInt())
        2 -> Color(0x2dd4bfff.toInt())
        3 -> Color(0xa3e635ff.toInt())
        else -> Color(0xf97316ff.toInt())
    }

    // ════════════════════════════════════════════════════════════════════
    //  TUTORIAL
    // ════════════════════════════════════════════════════════════════════
    fun drawTutorial(time: Float) {
        val b = bridge!!
        drawHud(time)
        val step = b.tutorialStep ?: return
        val msgs = arrayOf(
            "SWIPE LEFT\nTO MOVE LEFT",
            "SWIPE RIGHT\nTO MOVE RIGHT",
            "SWIPE UP\nTO JUMP",
            "SWIPE DOWN\nTO SLIDE"
        )
        val msg = msgs[step.coerceIn(0, 3)]
        // pulsing arrow
        val pulse = sin(time * 5f) * 14f
        sr.begin(ShapeRenderer.ShapeType.Filled)
        sr.setColor(Palette.GOLD)
        val cx = vw / 2f
        val cy = vh * 0.42f
        when (step) {
            0 -> { // left chevron
                sr.triangle(cx - 40f - pulse, cy, cx + 10f - pulse, cy + 50f, cx + 10f - pulse, cy - 50f)
            }
            1 -> { // right chevron
                sr.triangle(cx + 40f + pulse, cy, cx - 10f + pulse, cy + 50f, cx - 10f + pulse, cy - 50f)
            }
            2 -> { // up chevron
                sr.triangle(cx, cy + 44f + pulse, cx - 48f, cy - 6f + pulse, cx + 48f, cy - 6f + pulse)
            }
            3 -> { // down chevron
                sr.triangle(cx, cy - 44f - pulse, cx - 48f, cy + 6f - pulse, cx + 48f, cy + 6f - pulse)
            }
        }
        sr.end()
        theme.panel(batch, 60f, vh * 0.16f, vw - 120f, 150f, Palette.UI_PANEL)
        theme.text(batch, theme.fontMed, msg, 0f, vh * 0.16f + 105f, Palette.UI_TEXT, Align.center, vw)
    }

    // ════════════════════════════════════════════════════════════════════
    //  PAUSE
    // ════════════════════════════════════════════════════════════════════
    fun drawPause() {
        val b = bridge!!
        theme.panel(batch, vw / 2 - 270f, vh / 2 - 400f, 540f, 800f, Palette.UI_PANEL)
        theme.panel(batch, vw / 2 - 240f, vh / 2 - 40f, 480f, 300f, Palette.UI_PANEL_DEEP)
        theme.text(batch, theme.fontLarge, "PAUSED", 0f, vh / 2 + 230f, Color.WHITE, Align.center, vw)
        theme.text(batch, theme.fontMed, "SCORE ${b.score}", 0f, vh / 2 + 130f, Palette.GOLD, Align.center, vw)
        theme.text(batch, theme.fontSmall, "${b.distance.toInt()}m  •  ${b.runCoins} COINS", 0f, vh / 2 + 70f, Palette.UI_MUTED, Align.center, vw)
        if (btn("resume", vw / 2 - 190f, vh / 2 - 120f, 380f, 100f, Palette.UI_GREEN, "RESUME")) b.resumeGame()
        if (btn("restart", vw / 2 - 190f, vh / 2 - 240f, 380f, 100f, Palette.UI_GOLD_BTN, "RESTART")) b.restartRun()
        if (btn("home", vw / 2 - 190f, vh / 2 - 360f, 380f, 100f, Palette.UI_NAVY, "HOME")) b.toMenu()
    }

    // ════════════════════════════════════════════════════════════════════
    //  GAME OVER
    // ════════════════════════════════════════════════════════════════════
    fun drawGameOver() {
        val b = bridge!!
        // SS celebration: warm glow pulse behind the panel on NEW BEST (no fullscreen rainbow)
        if (b.newBest) {
            val pulse = 0.30f + sin(System.nanoTime() / 2.4e8f) * 0.10f
            batch.setColor(1f, 0.9f, 0.55f, pulse)
            batch.draw(TextureGen.glow, vw / 2f - 330f, 470f, 660f, 660f)
            batch.setColor(1f, 1f, 1f, 1f)
        }
        theme.panel(batch, vw / 2 - 320f, 220f, 640f, 820f, Palette.UI_PANEL)
        if (b.newBest) {
            theme.text(batch, theme.fontLarge, "NEW HIGH SCORE!", 0f, 962f, Palette.GOLD, Align.center, vw)
        } else if (b.guardCatch) {
            theme.text(batch, theme.fontLarge, "CAUGHT BY THE GUARD!", 0f, 966f, Palette.DANGER, Align.center, vw)
        } else {
            theme.text(batch, theme.fontLarge, "RUN OVER", 0f, 970f, Color.WHITE, Align.center, vw)
        }

        // score on deep slot (SS profile card style)
        theme.panel(batch, vw / 2 - 280f, 660f, 560f, 200f, Palette.UI_PANEL_DEEP)
        theme.text(batch, theme.fontHuge, "${b.displayScore}", 0f, 790f, Color.WHITE, Align.center, vw)
        theme.text(batch, theme.fontTiny, "SCORE", 0f, 745f, Palette.UI_MUTED, Align.center, vw)

        // chips: distance & coins
        theme.panel(batch, vw / 2 - 280f, 560f, 265f, 110f, Palette.UI_PANEL_LIGHT)
        theme.text(batch, theme.fontMed, "${b.distance.toInt()}m", vw / 2 - 280f, 622f, Color.WHITE, Align.center, 265f)
        theme.text(batch, theme.fontTiny, "DISTANCE", vw / 2 - 280f, 588f, Palette.UI_MUTED, Align.center, 265f)
        theme.panel(batch, vw / 2 + 15f, 560f, 265f, 110f, Palette.UI_PANEL_LIGHT)
        theme.coinIcon(batch, vw / 2 + 15f + 105f, 630f, 38f)
        theme.text(batch, theme.fontMed, "${b.runCoins}", vw / 2 + 15f, 622f, Palette.GOLD, Align.center, 265f)
        theme.text(batch, theme.fontTiny, "COINS", vw / 2 + 15f, 588f, Palette.UI_MUTED, Align.center, 265f)

        // best
        theme.text(batch, theme.fontSmall, "BEST ${b.save.best}", 0f, 528f, if (b.newBest) Palette.GOLD else Palette.UI_MUTED, Align.center, vw)

        if (btn("retry", vw / 2 - 190f, 366f, 380f, 112f, Palette.UI_GOLD_BTN, "RUN AGAIN", theme.fontLarge)) {
            b.restartRun()
        }
        if (btn("gohome", vw / 2 - 190f, 250f, 380f, 90f, Palette.UI_NAVY, "HOME")) {
            b.toMenu()
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  SHOP / CHARACTERS / MISSIONS / SETTINGS panels
    // ════════════════════════════════════════════════════════════════════
    fun drawPanel(time: Float) {
        val b = bridge!!
        // backdrop — periwinkle wash, world peeks through
        theme.panel(batch, 0f, 0f, vw, vh, Palette.UI_PANEL, 0.94f)
        // header
        if (btn("back", 24f, vh - 104f, 96f, 72f, Palette.UI_ORANGE, "<", theme.fontMed)) b.closePanel()
        val title = when (b.menuPanel) {
            MenuPanel.SHOP -> "SHOP"
            MenuPanel.CHARACTERS -> "CHARACTERS"
            MenuPanel.MISSIONS -> "MISSIONS"
            MenuPanel.SETTINGS -> "SETTINGS"
            else -> ""
        }
        theme.text(batch, theme.fontMed, title, 140f, vh - 52f, Palette.UI_TEXT)
        chipCoins(vw - 250f, vh - 104f)

        when (b.menuPanel) {
            MenuPanel.SHOP -> drawShop(time)
            MenuPanel.CHARACTERS -> drawCharacters()
            MenuPanel.MISSIONS -> drawMissions()
            MenuPanel.SETTINGS -> drawSettings()
            MenuPanel.RESET_CONFIRM -> drawResetConfirm()
            else -> {}
        }
    }

    private fun drawShop(time: Float) {
        val b = bridge!!
        // tabs
        val tabs = ShopTab.entries
        val tw = (vw - 48f - 24f) / 3f
        tabs.forEachIndexed { i, tab ->
            val x = 24f + i * (tw + 12f)
            val active = b.shopTab == tab
            hits.add(HitRect("tab$i", x, vh - 210f, tw, 64f) {})
            theme.button(batch, x, vh - 210f, tw, 64f, if (active) Palette.UI_GOLD_BTN else Palette.UI_NAVY, false)
            theme.text(batch, theme.fontTiny, tab.name, x, vh - 165f, if (active) Color.WHITE else Palette.UI_MUTED, Align.center, tw)
            if (clickId == "tab$i") {
                b.shopTabSet = tab
                scrollY = 0f
            }
        }

        sr.begin(ShapeRenderer.ShapeType.Filled)
        sr.setColor(1f, 1f, 1f, 1f)
        sr.end()
        when (b.shopTab) {
            ShopTab.CHARACTERS -> shopCharacters()
            ShopTab.UPGRADES -> shopUpgrades()
            ShopTab.TRAILS -> shopTrails()
        }
    }

    private fun clipContent(top: Float, bottom: Float, contentHeight: Float, drawContent: () -> Unit) {
        // simple scroll clamp
        scrollY = min(scrollY, max(0f, contentHeight - (top - bottom)))
        val yShift = -scrollY
        drawContent()
    }

    private fun shopCharacters() {
        val b = bridge!!
        var y = vh - 300f - scrollY
        for ((ci, ch) in CharacterDef.ALL.withIndex()) {
            if (y < 150f) break
            if (y > vh) { y -= 200f; continue }
            val owned = b.save.ownedCharacters.contains(ch.id)
            val selected = b.save.selectedCharacter == ch.id
            theme.panel(batch, 24f, y, vw - 48f, 180f, Palette.UI_PANEL_LIGHT)
            batch.setColor(1f, 1f, 1f, 1f)
            batch.draw(TextureGen.previews[ci], 40f, y + 15f, 150f, 150f)
            theme.text(batch, theme.fontMed, ch.name, 210f, y + 135f, Palette.UI_TEXT)
            val statLine = when {
                selected -> "SELECTED"
                owned -> "OWNED"
                else -> "${ch.cost} COINS"
            }
            theme.text(batch, theme.fontTiny, statLine, 210f, y + 95f, if (selected) Palette.UI_ACCENT2 else Palette.UI_MUTED)
            val id = "char_${ch.id}"
            when {
                selected -> theme.panel(batch, vw - 260f, y + 45f, 210f, 70f, Palette.UI_PANEL_DEEP)
                owned -> {
                    if (btn(id, vw - 260f, y + 45f, 210f, 70f, Palette.UI_GREEN, "SELECT", theme.fontSmall)) b.selectCharacter(ch.id)
                }
                else -> {
                    if (btn(id, vw - 260f, y + 45f, 210f, 70f, Palette.UI_GOLD_BTN, "BUY", theme.fontSmall)) b.buyCharacter(ch.id)
                }
            }
            y -= 200f
        }
    }

    private fun shopUpgrades() {
        val b = bridge!!
        var y = vh - 300f - scrollY
        for ((i, name) in com.dummysurfers.core.state.SaveManager.Companion.POWERUP_NAMES.withIndex()) {
            if (y < 150f) break
            if (y > vh) { y -= 165f; continue }
            val lvl = b.save.upgradeLevel(name)
            theme.panel(batch, 24f, y, vw - 48f, 148f, Palette.UI_PANEL_LIGHT)
            batch.setColor(1f, 1f, 1f, 1f)
            batch.draw(TextureGen.powerIcons[i], 44f, y + 38f, 72f, 72f)
            theme.text(batch, theme.fontSmall, GameConfig.POWERUP_LABELS[i], 142f, y + 106f, Palette.UI_TEXT)
            // level pips
            for (p in 0 until 3) {
                sr.begin(ShapeRenderer.ShapeType.Filled)
                sr.setColor(if (p < lvl) Palette.GOLD else Palette.UI_PANEL_DEEP)
                sr.circle(152f + p * 32f, y + 76f, 11f)
                sr.end()
            }
            val costText = if (lvl >= 3) "MAXED" else "+3s  ${GameConfig.UPGRADE_COSTS[i][lvl]} C"
            theme.text(batch, theme.fontTiny, costText, 142f, y + 42f, Palette.UI_MUTED)
            if (lvl < 3) {
                val id = "upg_$name"
                if (btn(id, vw - 260f, y + 40f, 210f, 68f, Palette.UI_GOLD_BTN, "UPGRADE", theme.fontSmall)) b.buyUpgrade(name)
            } else {
                theme.panel(batch, vw - 260f, y + 40f, 210f, 68f, Palette.UI_PANEL_DEEP)
                theme.text(batch, theme.fontTiny, "MAX", vw - 260f, y + 62f, Palette.UI_MUTED, Align.center, 210f)
            }
            y -= 165f
        }
        // hoverboard consumable — the SS 2nd-chance machine
        if (y >= 150f) {
            theme.panel(batch, 24f, y, vw - 48f, 148f, Palette.UI_PANEL_LIGHT)
            val ix = 80f
            val iy = y + 74f
            sr.begin(ShapeRenderer.ShapeType.Filled)
            sr.setColor(Palette.UI_ACCENT2)
            sr.rect(ix - 34f, iy - 8f, 68f, 16f)
            sr.circle(ix - 34f, iy, 8f)
            sr.circle(ix + 34f, iy, 8f)
            sr.setColor(Palette.GOLD)
            sr.rect(ix - 34f, iy - 2f, 68f, 4f)
            sr.end()
            theme.text(batch, theme.fontSmall, "HOVERBOARD", 142f, y + 106f, Palette.UI_TEXT)
            val full = b.save.hoverboards >= GameConfig.HOVERBOARD_MAX
            theme.text(batch, theme.fontTiny, "x${b.save.hoverboards} · SAVES FROM ONE CRASH · DOUBLE-TAP TO RIDE", 142f, y + 74f, Palette.UI_MUTED)
            if (full) {
                theme.panel(batch, vw - 260f, y + 40f, 210f, 68f, Palette.UI_PANEL_DEEP)
                theme.text(batch, theme.fontTiny, "RACK FULL", vw - 260f, y + 62f, Palette.UI_MUTED, Align.center, 210f)
            } else {
                if (btn("buy_board", vw - 260f, y + 40f, 210f, 68f, Palette.UI_GOLD_BTN, "${GameConfig.HOVERBOARD_COST} C", theme.fontSmall)) b.buyHoverboard()
            }
        }
    }

    private fun shopTrails() {
        val b = bridge!!
        val names = arrayOf("NONE", "GOLD", "FIRE", "RAINBOW")
        val colors = arrayOf(Color(0x4a4238ff.toInt()), Palette.GOLD, Color(0xf97316ff.toInt()), Color(0x2dd4bfff.toInt()))
        var y = vh - 300f - scrollY
        for (i in names.indices) {
            if (y < 150f) break
            if (y > vh) { y -= 170f; continue }
            val owned = i == 0 || b.save.trail >= i
            val active = b.save.trail == i
            theme.panel(batch, 24f, y, vw - 48f, 150f, Palette.UI_PANEL_LIGHT)
            // trail preview: three blobs
            sr.begin(ShapeRenderer.ShapeType.Filled)
            for (k in 0 until 3) {
                sr.setColor(colors[i])
                sr.circle(90f + k * 34f, y + 75f - k * 4f, 14f - k * 3f)
            }
            sr.end()
            theme.text(batch, theme.fontMed, names[i], 200f, y + 100f, Palette.UI_TEXT)
            val statusText = if (i == 0) "DEFAULT" else if (active) "EQUIPPED" else if (owned) "OWNED" else "${GameConfig.TRAIL_COSTS[i]} COINS"
            theme.text(batch, theme.fontTiny, statusText, 200f, y + 60f, if (active) Palette.UI_ACCENT2 else Palette.UI_MUTED)
            val id = "trail_$i"
            when {
                active -> {}
                owned -> {
                    if (btn(id, vw - 260f, y + 40f, 210f, 70f, Palette.UI_GREEN, "EQUIP", theme.fontSmall)) b.buyTrail(i)
                }
                else -> {
                    if (btn(id, vw - 260f, y + 40f, 210f, 70f, Palette.UI_GOLD_BTN, "BUY", theme.fontSmall)) b.buyTrail(i)
                }
            }
            y -= 170f
        }
    }

    private fun drawCharacters() {
        val b = bridge!!
        var y = vh - 300f - scrollY
        for ((ci, ch) in CharacterDef.ALL.withIndex()) {
            if (y < 150f) break
            if (y > vh) { y -= 220f; continue }
            val owned = b.save.ownedCharacters.contains(ch.id)
            val selected = b.save.selectedCharacter == ch.id
            theme.panel(batch, 24f, y, vw - 48f, 200f, Palette.UI_PANEL_LIGHT)
            batch.setColor(1f, 1f, 1f, 1f)
            batch.draw(TextureGen.previews[ci], 50f, y + 20f, 160f, 160f)
            theme.text(batch, theme.fontMed, ch.name, 240f, y + 150f, Palette.UI_TEXT)
            theme.text(batch, theme.fontTiny, if (owned) if (selected) "SELECTED" else "OWNED" else "${ch.cost} COINS", 240f, y + 108f, if (selected) Palette.UI_ACCENT2 else Palette.UI_MUTED)
            val id = "selchar_${ch.id}"
            when {
                selected -> theme.panel(batch, vw - 280f, y + 65f, 230f, 74f, Palette.UI_PANEL_DEEP)
                owned -> {
                    if (btn(id, vw - 280f, y + 65f, 230f, 74f, Palette.UI_GREEN, "SELECT", theme.fontSmall)) b.selectCharacter(ch.id)
                }
                else -> {
                    if (btn(id, vw - 280f, y + 65f, 230f, 74f, Palette.UI_GOLD_BTN, "BUY", theme.fontSmall)) b.buyCharacter(ch.id)
                }
            }
            y -= 220f
        }
    }

    private fun drawMissions() {
        val b = bridge!!
        var y = vh - 300f - scrollY
        b.save.missions.forEachIndexed { idx, m ->
            if (y < 150f) return
            if (y > vh) { y -= 220f; return@forEachIndexed }
            val done = m.progress >= m.goal
            theme.panel(batch, 24f, y, vw - 48f, 200f, Palette.UI_PANEL_LIGHT)
            theme.text(batch, theme.fontSmall, missionLabel(m.type), 44f, y + 150f, Palette.UI_TEXT)
            theme.text(batch, theme.fontTiny, "REWARD ${m.reward} COINS", 44f, y + 112f, Palette.GOLD)
            theme.progressBar(sr, 44f, y + 55f, vw - 400f, 20f, m.progress.toFloat() / m.goal, if (done) Palette.UI_GREEN else Palette.GOLD)
            theme.text(batch, theme.fontTiny, "${min(m.progress, m.goal)}/${m.goal}", 44f, y + 40f, Palette.UI_MUTED)
            if (m.claimed) {
                theme.panel(batch, vw - 280f, y + 60f, 230f, 80f, Palette.UI_PANEL_DEEP)
                theme.text(batch, theme.fontTiny, "DONE", vw - 280f, y + 95f, Palette.UI_MUTED, Align.center, 230f)
            } else if (done) {
                if (btn("claim$idx", vw - 280f, y + 60f, 230f, 80f, Palette.UI_GOLD_BTN, "CLAIM", theme.fontSmall)) b.claimMission(idx)
            } else {
                theme.panel(batch, vw - 280f, y + 60f, 230f, 80f, Palette.UI_PANEL)
            }
            y -= 220f
        }
    }

    private fun missionLabel(t: MissionType): String = when (t) {
        MissionType.DISTANCE -> "RUN FAR"
        MissionType.COINS -> "GRAB COINS"
        MissionType.JUMPS -> "JUMP PRO"
        MissionType.SLIDES -> "SLIDE KING"
        MissionType.POWERUPS -> "POWER UP"
        MissionType.SCORE -> "HIGH SCORER"
        MissionType.NEAR_MISS -> "DAREDEVIL"
    }

    private fun drawSettings() {
        val b = bridge!!
        var y = vh - 320f
        fun toggle(id: String, label: String, on: Boolean, set: (Boolean) -> Unit) {
            theme.panel(batch, 24f, y, vw - 48f, 110f, Palette.UI_PANEL_LIGHT)
            theme.text(batch, theme.fontMed, label, 48f, y + 65f, Palette.UI_TEXT)
            // switch
            val sx = vw - 220f
            sr.begin(ShapeRenderer.ShapeType.Filled)
            sr.setColor(if (on) Palette.UI_GREEN else Palette.UI_PANEL_DEEP)
            sr.rect(sx, y + 30f, 150f, 50f)
            sr.circle(sx + 25f, y + 55f, 25f)
            sr.circle(sx + 125f, y + 55f, 25f)
            sr.setColor(Color.WHITE)
            sr.circle(if (on) sx + 118f else sx + 32f, y + 55f, 21f)
            sr.end()
            hits.add(HitRect(id, sx - 20f, y + 20f, 190f, 70f) {})
            if (clickId == id) set(!on)
        }
        toggle("music", "MUSIC", b.save.musicOn) { b.setMusic(it) }
        y -= 130f
        toggle("sfx", "SOUND FX", b.save.sfxOn) { b.setSfx(it) }
        y -= 130f
        toggle("vib", "VIBRATION", b.save.vibrationOn) { b.setVibration(it) }
        y -= 170f
        if (btn("reset", vw / 2 - 190f, y, 380f, 100f, Palette.DANGER, "RESET DATA", theme.fontSmall)) b.openPanel(MenuPanel.RESET_CONFIRM)
        theme.text(batch, theme.fontTiny, "DUMMY SURFERS BY FSK — V1.0", 0f, 80f, Palette.UI_MUTED, Align.center, vw)
        theme.text(batch, theme.fontTiny, "RUNS ${b.save.stats.runs}   BEST DIST ${b.save.stats.bestDistance}m", 0f, 110f, Palette.UI_MUTED, Align.center, vw)
    }

    private fun drawResetConfirm() {
        val b = bridge!!
        theme.panel(batch, vw / 2 - 300f, vh / 2 - 200f, 600f, 400f, Palette.UI_PANEL_LIGHT)
        theme.text(batch, theme.fontMed, "RESET ALL", 0f, vh / 2 + 120f, Palette.DANGER, Align.center, vw)
        theme.text(batch, theme.fontSmall, "THIS WIPES ALL\nPROGRESS AND COINS", 0f, vh / 2 + 30f, Palette.UI_MUTED, Align.center, vw)
        if (btn("confirm", vw / 2 - 240f, vh / 2 - 160f, 220f, 90f, Palette.DANGER, "YES")) b.resetProgress()
        if (btn("cancel", vw / 2 + 20f, vh / 2 - 160f, 220f, 90f, Palette.UI_PANEL_LIGHT, "NO")) b.closePanel()
    }

    // ── Mini character preview (pre-rendered portrait) ──────────────
    private fun drawMiniCharacter(characterIndex: Int, x: Float, y: Float, size: Float) {
        batch.setColor(1f, 1f, 1f, 1f)
        batch.draw(TextureGen.previews[characterIndex.coerceIn(0, TextureGen.previews.size - 1)], x, y, size, size)
    }

    // ── Toast ──────────────────────────────────────────────────────────
    fun drawToast(dt: Float) {
        if (toastMsg == null) return
        toastTimer -= dt
        if (toastTimer <= 0f) { toastMsg = null; return }
        val alpha = min(1f, toastTimer * 2f)
        theme.button(batch, vw / 2 - 300f, 140f, 600f, 90f, Palette.UI_NAVY, false)
        theme.text(batch, theme.fontSmall, toastMsg!!, 0f, 195f, Palette.GOLD, Align.center, vw)
    }

    fun update(dt: Float) {
        // nothing time-based yet; kept for parity
    }
}
