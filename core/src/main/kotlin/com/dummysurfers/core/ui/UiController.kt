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
        val newBest: Boolean
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
            val hit = hits.lastOrNull { it.id == "pause" && inside(it, touchVirtualX, touchVirtualY) }
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
        scrollY = max(0f, scrollDragValue - dy)
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

    private fun scrollable() = bridge!!.menuPanel == MenuPanel.SHOP || bridge!!.menuPanel == MenuPanel.MISSIONS

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
        // coins chip
        chipCoins(vw - 250f, vh - 96f)
        // best score chip
        chip("BEST ${b.save.best}", 24f, vh - 96f, Palette.UI_PANEL_LIGHT)

        // title with subtle bounce
        val bounce = sin(time * 2.2f) * 6f
        theme.text(batch, theme.fontHuge, "DUMMY", 0f, 1075f + bounce, Palette.GOLD, Align.center, vw)
        theme.text(batch, theme.fontHuge, "SURFERS", 0f, 1010f + bounce, Palette.GOLD, Align.center, vw)
        theme.text(batch, theme.fontTiny, "BY FSK", 0f, 965f + bounce, Palette.UI_ACCENT2, Align.center, vw)

        // PLAY
        if (btn("play", vw / 2 - 190f, 700f, 380f, 116f, Palette.UI_ACCENT, "PLAY", theme.fontLarge)) {
            b.startRun()
        }
        // secondary row
        val bw = 160f
        val gap = 16f
        val total = bw * 4 + gap * 3
        val x0 = (vw - total) / 2
        if (btn("chars", x0, 540f, bw, 150f, Palette.UI_PANEL_LIGHT, "CHARS", theme.fontSmall)) b.openPanel(MenuPanel.CHARACTERS)
        if (btn("shop", x0 + (bw + gap), 540f, bw, 150f, Palette.UI_PANEL_LIGHT, "SHOP", theme.fontSmall)) b.openPanel(MenuPanel.SHOP)
        if (btn("missions", x0 + (bw + gap) * 2, 540f, bw, 150f, Palette.UI_PANEL_LIGHT, "TASKS", theme.fontSmall)) b.openPanel(MenuPanel.MISSIONS)
        if (btn("settings", x0 + (bw + gap) * 3, 540f, bw, 150f, Palette.UI_PANEL_LIGHT, "SETUP", theme.fontSmall)) b.openPanel(MenuPanel.SETTINGS)

        // missions ready hint
        val ready = b.save.missions.count { !it.claimed && it.progress >= it.goal }
        if (ready > 0) {
            theme.text(batch, theme.fontSmall, "$ready", x0 + (bw + gap) * 2 + bw - 34f, 540f + 150f - 6f, Palette.DANGER)
        }

        theme.text(batch, theme.fontTiny, "V1.0  DUMMY SURFERS BY FSK", 24f, 40f, Palette.UI_MUTED)
        theme.text(batch, theme.fontTiny, "SWIPE TO MOVE", 0f, 40f, Palette.UI_MUTED, Align.center, vw)
    }

    private fun chipCoins(x: Float, y: Float) {
        val b = bridge!!
        val label = "${b.save.totalCoins}"
        val w = max(150f, theme.textWidth(theme.fontMed, label) + 80f)
        theme.panel(batch, x, y, w, 64f, Palette.UI_PANEL_LIGHT)
        theme.coinIcon(batch, x + 14f, y + 10f, 44f)
        theme.text(batch, theme.fontMed, label, x + 64f, y + 40f, Palette.GOLD)
    }

    private fun chip(label: String, x: Float, y: Float, color: Color) {
        val w = theme.textWidth(theme.fontSmall, label) + 44f
        theme.panel(batch, x, y, w, 56f, color)
        theme.text(batch, theme.fontSmall, label, x + 22f, y + 36f, Palette.UI_MUTED)
    }

    // ════════════════════════════════════════════════════════════════════
    //  HUD
    // ════════════════════════════════════════════════════════════════════
    fun drawHud(time: Float) {
        val b = bridge!!
        // score
        theme.text(batch, theme.fontLarge, "${b.score}", 0f, vh - 40f, Color.WHITE, Align.center, vw)
        // multiplier
        val multColor = when (b.multiplier) {
            4 -> Palette.GOLD
            2 -> Palette.UI_ACCENT2
            else -> Palette.UI_MUTED
        }
        theme.text(batch, theme.fontSmall, "x${b.multiplier}", 0f, vh - 84f, multColor, Align.center, vw)
        // run coins
        theme.panel(batch, 20f, vh - 108f, 150f, 56f, Palette.UI_PANEL)
        theme.coinIcon(batch, 28f, vh - 101f, 38f)
        theme.text(batch, theme.fontSmall, "${b.runCoins}", 74f, vh - 70f, Palette.GOLD)
        // distance
        theme.text(batch, theme.fontSmall, "${b.distance.toInt()}m", 0f, vh - 130f, Palette.UI_MUTED, Align.center, vw)
        // pause button
        hits.add(HitRect("pause", vw - 92f, vh - 96f, 68f, 68f) {})
        val pressed = pressedId == "pause"
        theme.button(batch, vw - 92f, vh - 96f, 68f, 68f, Palette.UI_PANEL, pressed)
        theme.text(batch, theme.fontSmall, "| |", vw - 92f, vh - 49f, Palette.UI_TEXT, Align.center, 68f)

        // power-up bars
        var py = vh - 190f
        for (i in 0 until 5) {
            val rem = b.powerupRemaining[i]
            if (rem > 0f) {
                val total = b.powerupTotal[i]
                val t = rem / total
                val flashing = rem < 3f && (time * 4f).toInt() % 2 == 0
                val color = powerColor(i)
                theme.panel(batch, 20f, py, 250f, 52f, Palette.UI_PANEL)
                batch.setColor(1f, 1f, 1f, if (flashing) 0.5f else 1f)
                batch.draw(TextureGen.powerIcons[i], 26f, py + 6f, 40f, 40f)
                batch.setColor(1f, 1f, 1f, 1f)
                theme.progressBar(sr, 76f, py + 16f, 180f, 14f, t, color)
                theme.text(batch, theme.fontTiny, GameConfig.POWERUP_LABELS[i], 76f, py + 46f, Palette.UI_TEXT)
                py -= 62f
            }
        }

        // shield ready indicator
        if (b.powerupRemaining[2] > 0f) {
            theme.text(batch, theme.fontTiny, "SHIELD ACTIVE", 0f, vh - 84f, Palette.UI_ACCENT2, Align.center, vw)
        }
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
        theme.panel(batch, vw / 2 - 270f, vh / 2 - 320f, 540f, 640f, Palette.UI_PANEL)
        theme.text(batch, theme.fontLarge, "PAUSED", 0f, vh / 2 + 230f, Palette.UI_TEXT, Align.center, vw)
        theme.text(batch, theme.fontSmall, "SCORE ${b.score}", 0f, vh / 2 + 170f, Palette.UI_MUTED, Align.center, vw)
        if (btn("resume", vw / 2 - 190f, vh / 2 + 30f, 380f, 100f, Palette.UI_ACCENT2, "RESUME")) b.resumeGame()
        if (btn("restart", vw / 2 - 190f, vh / 2 - 100f, 380f, 100f, Palette.UI_ACCENT, "RESTART")) b.restartRun()
        if (btn("home", vw / 2 - 190f, vh / 2 - 230f, 380f, 100f, Palette.UI_PANEL_LIGHT, "HOME")) b.toMenu()
    }

    // ════════════════════════════════════════════════════════════════════
    //  GAME OVER
    // ════════════════════════════════════════════════════════════════════
    fun drawGameOver() {
        val b = bridge!!
        theme.panel(batch, vw / 2 - 320f, 220f, 640f, 820f, Palette.UI_PANEL)
        theme.text(batch, theme.fontLarge, "RUN OVER", 0f, 970f, Palette.DANGER, Align.center, vw)

        if (b.newBest) {
            val pulse = 1f + sin(System.nanoTime() / 1.2e8f) * 0.06f
            theme.text(batch, theme.fontSmall, "NEW BEST!", 0f, 880f, Palette.GOLD, Align.center, vw)
        }

        theme.text(batch, theme.fontHuge, "${b.displayScore}", 0f, 790f, Color.WHITE, Align.center, vw)
        theme.text(batch, theme.fontTiny, "SCORE", 0f, 745f, Palette.UI_MUTED, Align.center, vw)

        // chips: distance & coins
        theme.panel(batch, vw / 2 - 280f, 610f, 265f, 110f, Palette.UI_PANEL_LIGHT)
        theme.text(batch, theme.fontMed, "${b.distance.toInt()}m", vw / 2 - 280f, 665f, Palette.UI_ACCENT2, Align.center, 265f)
        theme.text(batch, theme.fontTiny, "DISTANCE", vw / 2 - 280f, 630f, Palette.UI_MUTED, Align.center, 265f)
        theme.panel(batch, vw / 2 + 15f, 610f, 265f, 110f, Palette.UI_PANEL_LIGHT)
        theme.coinIcon(batch, vw / 2 + 15f + 105f, 680f, 38f)
        theme.text(batch, theme.fontMed, "${b.runCoins}", vw / 2 + 15f, 665f, Palette.GOLD, Align.center, 265f)
        theme.text(batch, theme.fontTiny, "COINS", vw / 2 + 15f, 630f, Palette.UI_MUTED, Align.center, 265f)

        // best
        theme.text(batch, theme.fontSmall, "BEST ${b.save.best}", 0f, 555f, Palette.UI_MUTED, Align.center, vw)

        if (btn("retry", vw / 2 - 190f, 360f, 380f, 110f, Palette.UI_ACCENT, "RETRY", theme.fontLarge)) {
            b.restartRun()
        }
        if (btn("gohome", vw / 2 - 190f, 250f, 380f, 90f, Palette.UI_PANEL_LIGHT, "HOME")) {
            b.toMenu()
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  SHOP / CHARACTERS / MISSIONS / SETTINGS panels
    // ════════════════════════════════════════════════════════════════════
    fun drawPanel(time: Float) {
        val b = bridge!!
        // backdrop
        theme.panel(batch, 0f, 0f, vw, vh, Palette.UI_PANEL, 0.94f)
        // header
        if (btn("back", 24f, vh - 104f, 96f, 72f, Palette.UI_PANEL_LIGHT, "<", theme.fontMed)) b.closePanel()
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
            theme.panel(batch, x, vh - 210f, tw, 64f, if (active) Palette.UI_ACCENT else Palette.UI_PANEL_LIGHT)
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
        var y = vh - 300f
        for ((ci, ch) in CharacterDef.ALL.withIndex()) {
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
                selected -> theme.panel(batch, vw - 260f, y + 45f, 210f, 70f, Palette.UI_PANEL)
                owned -> {
                    if (btn(id, vw - 260f, y + 45f, 210f, 70f, Palette.UI_ACCENT2, "SELECT", theme.fontSmall)) b.selectCharacter(ch.id)
                }
                else -> {
                    if (btn(id, vw - 260f, y + 45f, 210f, 70f, Palette.GOLD, "BUY", theme.fontSmall, Color(0x241d1aff.toInt()))) b.buyCharacter(ch.id)
                }
            }
            y -= 200f
        }
    }

    private fun shopUpgrades() {
        val b = bridge!!
        var y = vh - 300f
        for ((i, name) in com.dummysurfers.core.state.SaveManager.Companion.POWERUP_NAMES.withIndex()) {
            val lvl = b.save.upgradeLevel(name)
            theme.panel(batch, 24f, y, vw - 48f, 170f, Palette.UI_PANEL_LIGHT)
            batch.setColor(1f, 1f, 1f, 1f)
            batch.draw(TextureGen.powerIcons[i], 44f, y + 45f, 80f, 80f)
            theme.text(batch, theme.fontSmall, GameConfig.POWERUP_LABELS[i], 150f, y + 120f, Palette.UI_TEXT)
            // level pips
            for (p in 0 until 3) {
                sr.begin(ShapeRenderer.ShapeType.Filled)
                sr.setColor(if (p < lvl) Palette.UI_ACCENT2 else Color(0x4a4238ff.toInt()))
                sr.circle(160f + p * 34f, y + 85f, 12f)
                sr.end()
            }
            val costText = if (lvl >= 3) "MAXED" else "+3s  ${GameConfig.UPGRADE_COSTS[i][lvl]} C"
            theme.text(batch, theme.fontTiny, costText, 150f, y + 50f, Palette.UI_MUTED)
            if (lvl < 3) {
                val id = "upg_$name"
                if (btn(id, vw - 260f, y + 50f, 210f, 70f, Palette.GOLD, "UPGRADE", theme.fontSmall, Color(0x241d1aff.toInt()))) b.buyUpgrade(name)
            } else {
                theme.panel(batch, vw - 260f, y + 50f, 210f, 70f, Palette.UI_PANEL)
                theme.text(batch, theme.fontTiny, "MAX", vw - 260f, y + 80f, Palette.UI_MUTED, Align.center, 210f)
            }
            y -= 190f
        }
    }

    private fun shopTrails() {
        val b = bridge!!
        val names = arrayOf("NONE", "GOLD", "FIRE", "RAINBOW")
        val colors = arrayOf(Color(0x4a4238ff.toInt()), Palette.GOLD, Color(0xf97316ff.toInt()), Color(0x2dd4bfff.toInt()))
        var y = vh - 300f
        for (i in names.indices) {
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
                    if (btn(id, vw - 260f, y + 40f, 210f, 70f, Palette.UI_ACCENT2, "EQUIP", theme.fontSmall)) b.buyTrail(i)
                }
                else -> {
                    if (btn(id, vw - 260f, y + 40f, 210f, 70f, Palette.GOLD, "BUY", theme.fontSmall, Color(0x241d1aff.toInt()))) b.buyTrail(i)
                }
            }
            y -= 170f
        }
    }

    private fun drawCharacters() {
        val b = bridge!!
        var y = vh - 300f
        for ((ci, ch) in CharacterDef.ALL.withIndex()) {
            val owned = b.save.ownedCharacters.contains(ch.id)
            val selected = b.save.selectedCharacter == ch.id
            theme.panel(batch, 24f, y, vw - 48f, 200f, Palette.UI_PANEL_LIGHT)
            batch.setColor(1f, 1f, 1f, 1f)
            batch.draw(TextureGen.previews[ci], 50f, y + 20f, 160f, 160f)
            theme.text(batch, theme.fontMed, ch.name, 240f, y + 150f, Palette.UI_TEXT)
            theme.text(batch, theme.fontTiny, if (owned) if (selected) "SELECTED" else "OWNED" else "${ch.cost} COINS", 240f, y + 108f, if (selected) Palette.UI_ACCENT2 else Palette.UI_MUTED)
            val id = "selchar_${ch.id}"
            when {
                selected -> theme.panel(batch, vw - 280f, y + 65f, 230f, 74f, Palette.UI_PANEL)
                owned -> {
                    if (btn(id, vw - 280f, y + 65f, 230f, 74f, Palette.UI_ACCENT2, "SELECT", theme.fontSmall)) b.selectCharacter(ch.id)
                }
                else -> {
                    if (btn(id, vw - 280f, y + 65f, 230f, 74f, Palette.GOLD, "BUY", theme.fontSmall, Color(0x241d1aff.toInt()))) b.buyCharacter(ch.id)
                }
            }
            y -= 220f
        }
    }

    private fun drawMissions() {
        val b = bridge!!
        var y = vh - 300f
        b.save.missions.forEachIndexed { idx, m ->
            val done = m.progress >= m.goal
            theme.panel(batch, 24f, y, vw - 48f, 200f, Palette.UI_PANEL_LIGHT)
            theme.text(batch, theme.fontSmall, missionLabel(m.type), 44f, y + 150f, Palette.UI_TEXT)
            theme.text(batch, theme.fontTiny, "REWARD ${m.reward} COINS", 44f, y + 112f, Palette.GOLD)
            theme.progressBar(sr, 44f, y + 55f, vw - 400f, 20f, m.progress.toFloat() / m.goal, if (done) Palette.UI_ACCENT2 else Palette.UI_ACCENT)
            theme.text(batch, theme.fontTiny, "${min(m.progress, m.goal)}/${m.goal}", 44f, y + 40f, Palette.UI_MUTED)
            if (m.claimed) {
                theme.panel(batch, vw - 280f, y + 60f, 230f, 80f, Palette.UI_PANEL)
                theme.text(batch, theme.fontTiny, "DONE", vw - 280f, y + 95f, Palette.UI_MUTED, Align.center, 230f)
            } else if (done) {
                if (btn("claim$idx", vw - 280f, y + 60f, 230f, 80f, Palette.GOLD, "CLAIM", theme.fontSmall, Color(0x241d1aff.toInt()))) b.claimMission(idx)
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
            sr.setColor(if (on) Palette.UI_ACCENT2 else Color(0x4a4238ff.toInt()))
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
        theme.panel(batch, vw / 2 - 300f, 140f, 600f, 90f, Palette.UI_PANEL_LIGHT, alpha)
        theme.text(batch, theme.fontSmall, toastMsg!!, 0f, 195f, Palette.GOLD, Align.center, vw)
    }

    fun update(dt: Float) {
        // nothing time-based yet; kept for parity
    }
}
