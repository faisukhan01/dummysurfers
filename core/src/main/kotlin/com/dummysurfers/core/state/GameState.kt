package com.dummysurfers.core.state

/** High-level game phases. */
enum class GameState {
    LOADING, MENU, TUTORIAL, PLAYING, DYING, GAME_OVER, PAUSED
}

/** UI overlays stacked on top of MENU. */
enum class MenuPanel { NONE, SHOP, CHARACTERS, MISSIONS, SETTINGS, RESET_CONFIRM }

/** Shop sections. */
enum class ShopTab { CHARACTERS, UPGRADES, TRAILS }

/** Power-up identifiers (indices into GameConfig.POWERUP_DURATIONS). */
enum class PowerUpType(val index: Int) {
    MAGNET(0), X2(1), SHIELD(2), BOOST(3), SUPERJUMP(4), JETPACK(5);
    companion object {
        fun byIndex(i: Int) = entries.first { it.index == i }
    }
}

/** Player action states driving animation + collision box. */
enum class PlayerState { RUNNING, LANE_SWITCH, JUMPING, SLIDING, LANDING, DEAD }

/** Events emitted by gameplay that systems can react to (audio, haptics, particles). */
enum class GameEvent {
    COIN, JUMP, SLIDE, LANE, CRASH, POWERUP, HORN, CLICK, NEAR_MISS, NEW_BEST, GAME_OVER,
    FOOTSTEP, SHIELD_BREAK, LAND, TUTORIAL_STEP, STUMBLE, WHISTLE, CAUGHT
}
