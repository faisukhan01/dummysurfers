package com.dummysurfers.core.state

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences

/**
 * Mission definition + progress for one active mission slot.
 * Serialized as CSV fields inside the save JSON.
 */
class MissionSave(
    var type: MissionType,
    var goal: Int,
    var progress: Int,
    var reward: Int,
    var claimed: Boolean
) {
    fun toCsv(): String = "${type.name},$goal,$progress,$reward,${if (claimed) 1 else 0}"

    companion object {
        fun fromCsv(csv: String): MissionSave? {
            val p = csv.split(",")
            if (p.size < 5) return null
            val type = try { MissionType.valueOf(p[0]) } catch (_: Exception) { return null }
            return MissionSave(type, p[1].toIntOrNull() ?: 0, p[2].toIntOrNull() ?: 0, p[3].toIntOrNull() ?: 0, p[4] == "1")
        }
    }
}

enum class MissionType { DISTANCE, COINS, JUMPS, SLIDES, POWERUPS, SCORE, NEAR_MISS }

/** Player statistics accumulated across all runs. */
class Stats(
    var runs: Int = 0,
    var bestDistance: Int = 0,
    var totalDistance: Int = 0,
    var totalCoins: Int = 0,
    var totalJumps: Int = 0,
    var totalSlides: Int = 0,
    var totalPowerups: Int = 0,
    var totalNearMisses: Int = 0,
    var playSeconds: Int = 0
)

/**
 * Offline persistence: LibGDX Preferences (SharedPreferences on Android)
 * holding a JSON document. 100% offline, no login required.
 * Swap the storage calls for a Firebase adapter later if cloud sync is wanted.
 */
class SaveManager {
    private val prefs: Preferences = Gdx.app.getPreferences("dummysurfers")
    val stats = Stats()

    var best: Int = 0; private set
    var totalCoins: Int = 0; private set
    var selectedCharacter: String = "dash"; private set
    val ownedCharacters = LinkedHashSet(listOf("dash"))
    val upgrades = HashMap<String, Int>()          // powerup name -> level 0..3
    var trail: Int = 0; private set                 // 0 none,1 gold,2 fire,3 rainbow
    var musicOn = true; private set
    var sfxOn = true; private set
    var vibrationOn = true; private set
    var tutorialDone = false; private set
    var hoverboards: Int = 1; private set      // consumable 2nd-chance boards
    val missions = ArrayList<MissionSave>(3)

    private val listeners = ArrayList<(SaveManager) -> Unit>()

    init {
        load()
    }

    fun onChange(cb: (SaveManager) -> Unit) { listeners.add(cb) }
    private fun notifyChanged() { listeners.forEach { it(this) } }

    private fun load() {
        val json = prefs.getString("save", "")
        if (json.isBlank()) { resetMissions(); return }
        val root = MiniJson.parse(json) as? HashMap<*, *> ?: return
        fun n(key: String, def: Int) = (root[key] as? Float)?.toInt() ?: def
        fun b(key: String, def: Boolean) = root[key] as? Boolean ?: def
        fun s(key: String, def: String) = root[key] as? String ?: def

        best = n("best", 0)
        totalCoins = n("coins", 0)
        selectedCharacter = s("character", "dash")
        trail = n("trail", 0)
        musicOn = b("music", true)
        sfxOn = b("sfx", true)
        vibrationOn = b("vib", true)
        tutorialDone = b("tut", false)
        hoverboards = n("boards", 1)

        ownedCharacters.clear(); ownedCharacters.add("dash")
        s("owned", "dash").split("|").filter { it.isNotBlank() }.forEach { ownedCharacters.add(it) }

        upgrades.clear()
        s("upg", "").split("|").filter { it.isNotBlank() }.forEach {
            val kv = it.split(":")
            if (kv.size == 2) upgrades[kv[0]] = kv[1].toIntOrNull() ?: 0
        }
        POWERUP_NAMES.forEach { upgrades.putIfAbsent(it, 0) }

        val st = root["stats"] as? HashMap<*, *>
        if (st != null) {
            fun sn(key: String, def: Int) = (st[key] as? Float)?.toInt() ?: def
            stats.runs = sn("runs", 0); stats.bestDistance = sn("bestD", 0)
            stats.totalDistance = sn("totD", 0); stats.totalCoins = sn("totC", 0)
            stats.totalJumps = sn("jumps", 0); stats.totalSlides = sn("slides", 0)
            stats.totalPowerups = sn("pows", 0); stats.totalNearMisses = sn("nm", 0)
            stats.playSeconds = sn("play", 0)
        }

        missions.clear()
        (root["missions"] as? ArrayList<*>)?.forEach { m ->
            (m as? String)?.let { csv -> MissionSave.fromCsv(csv)?.let { missions.add(it) } }
        }
        resetMissions()
    }

    private fun resetMissions() {
        while (missions.size < 3) missions.add(MissionGenerator.random(missions))
    }

    fun persist() {
        val owned = ownedCharacters.joinToString("|")
        val upg = upgrades.entries.joinToString("|") { "${it.key}:${it.value}" }
        val statsJson = buildString {
            append("{")
            append("\"runs\":${stats.runs},\"bestD\":${stats.bestDistance},\"totD\":${stats.totalDistance},")
            append("\"totC\":${stats.totalCoins},\"jumps\":${stats.totalJumps},\"slides\":${stats.totalSlides},")
            append("\"pows\":${stats.totalPowerups},\"nm\":${stats.totalNearMisses},\"play\":${stats.playSeconds}")
            append("}")
        }
        val missionsJson = missions.joinToString(",") { MiniJson.str(it.toCsv()) }
        val json = buildString {
            append("{")
            append("\"best\":$best,")
            append("\"coins\":$totalCoins,")
            append("\"character\":${MiniJson.str(selectedCharacter)},")
            append("\"owned\":${MiniJson.str(owned)},")
            append("\"upg\":${MiniJson.str(upg)},")
            append("\"trail\":$trail,")
            append("\"music\":$musicOn,")
            append("\"sfx\":$sfxOn,")
            append("\"vib\":$vibrationOn,")
            append("\"tut\":$tutorialDone,")
            append("\"boards\":$hoverboards,")
            append("\"stats\":$statsJson,")
            append("\"missions\":[$missionsJson]")
            append("}")
        }
        prefs.putString("save", json)
        prefs.flush()
        notifyChanged()
    }

    // ── Mutations ──────────────────────────────────────────────────────
    fun addCoins(n: Int) { totalCoins += n; stats.totalCoins += n }
    fun spendCoins(n: Int): Boolean {
        if (totalCoins < n) return false
        totalCoins -= n
        return true
    }

    /** Take one hoverboard out of the rack (activation). */
    fun consumeHoverboard(): Boolean {
        if (hoverboards <= 0) return false
        hoverboards--
        persist()
        return true
    }

    /** Buy one hoverboard for the rack. */
    fun buyHoverboard(cost: Int, max: Int): Boolean {
        if (hoverboards >= max) return false
        if (!spendCoins(cost)) return false
        hoverboards++
        persist()
        return true
    }

    fun submitRun(score: Int, coins: Int, distance: Int, jumps: Int, slides: Int, powerups: Int, nearMisses: Int): Boolean {
        stats.runs++
        stats.totalDistance += distance
        stats.totalJumps += jumps
        stats.totalSlides += slides
        stats.totalPowerups += powerups
        stats.totalNearMisses += nearMisses
        if (distance > stats.bestDistance) stats.bestDistance = distance
        addCoins(coins)
        val newBest = score > best
        if (newBest) best = score
        for (m in missions) if (!m.claimed) {
            val add = when (m.type) {
                MissionType.DISTANCE -> distance
                MissionType.COINS -> coins
                MissionType.JUMPS -> jumps
                MissionType.SLIDES -> slides
                MissionType.POWERUPS -> powerups
                MissionType.SCORE -> score
                MissionType.NEAR_MISS -> nearMisses
            }
            m.progress = (m.progress + add).coerceAtMost(m.goal)
        }
        persist()
        return newBest
    }

    fun ownCharacter(id: String, cost: Int): Boolean {
        if (ownedCharacters.contains(id)) return false
        if (!spendCoins(cost)) return false
        ownedCharacters.add(id)
        persist()
        return true
    }

    fun selectCharacter(id: String) {
        if (ownedCharacters.contains(id)) { selectedCharacter = id; persist() }
    }

    fun upgradeLevel(name: String) = upgrades[name] ?: 0

    fun buyUpgrade(name: String, cost: Int): Boolean {
        val lvl = upgradeLevel(name)
        if (lvl >= 3) return false
        if (!spendCoins(cost)) return false
        upgrades[name] = lvl + 1
        persist()
        return true
    }

    fun buyTrail(index: Int, cost: Int): Boolean {
        if (!spendCoins(cost)) return false
        trail = index
        persist()
        return true
    }

    fun setMusic(on: Boolean) { musicOn = on; persist() }
    fun setSfx(on: Boolean) { sfxOn = on; persist() }
    fun setVibration(on: Boolean) { vibrationOn = on; persist() }
    fun markTutorialDone() { tutorialDone = true; persist() }

    fun claimMission(index: Int): Boolean {
        val m = missions.getOrNull(index) ?: return false
        if (m.claimed || m.progress < m.goal) return false
        m.claimed = true
        addCoins(m.reward)
        missions[index] = MissionGenerator.random(missions)
        persist()
        return true
    }

    fun reset() {
        best = 0; totalCoins = 0; selectedCharacter = "dash"
        ownedCharacters.clear(); ownedCharacters.add("dash")
        upgrades.clear(); POWERUP_NAMES.forEach { upgrades[it] = 0 }
        trail = 0; musicOn = true; sfxOn = true; vibrationOn = true
        tutorialDone = false
        hoverboards = 1
        stats.runs = 0; stats.bestDistance = 0; stats.totalDistance = 0; stats.totalCoins = 0
        stats.totalJumps = 0; stats.totalSlides = 0; stats.totalPowerups = 0
        stats.totalNearMisses = 0; stats.playSeconds = 0
        missions.clear(); resetMissions()
        persist()
    }

    companion object {
        val POWERUP_NAMES = listOf("magnet", "x2", "shield", "boost", "superjump", "jetpack")
    }
}

/** Generates new missions so exactly 3 active ones always exist. */
object MissionGenerator {
    private val rng = kotlin.random.Random(System.nanoTime())

    fun random(existing: List<MissionSave>): MissionSave {
        val types = MissionType.entries.filter { t ->
            existing.none { it.type == t && !it.claimed }
        }.ifEmpty { MissionType.entries.toList() }
        val type = types[rng.nextInt(types.size)]
        val (goal, reward) = when (type) {
            MissionType.DISTANCE -> pick(intArrayOf(500, 1000, 2000), intArrayOf(50, 100, 200))
            MissionType.COINS -> pick(intArrayOf(50, 150, 300), intArrayOf(50, 100, 200))
            MissionType.JUMPS -> pick(intArrayOf(20, 50, 100), intArrayOf(50, 100, 150))
            MissionType.SLIDES -> pick(intArrayOf(15, 40, 80), intArrayOf(50, 100, 150))
            MissionType.POWERUPS -> pick(intArrayOf(5, 12, 25), intArrayOf(100, 150, 250))
            MissionType.SCORE -> pick(intArrayOf(5000, 15000, 30000), intArrayOf(100, 300, 500))
            MissionType.NEAR_MISS -> pick(intArrayOf(10, 25, 50), intArrayOf(100, 150, 250))
        }
        return MissionSave(type, goal, 0, reward, false)
    }

    private fun pick(goals: IntArray, rewards: IntArray): Pair<Int, Int> {
        val i = rng.nextInt(goals.size)
        return goals[i] to rewards[i]
    }
}
