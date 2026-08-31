package com.dummysurfers.core.camera

import com.dummysurfers.core.config.GameConfig
import com.dummysurfers.core.utils.Mathz

/**
 * Pseudo-3D perspective projection — the heart of the 3D illusion.
 * scale(z) = focal / (focal + z); everything on screen derives from it.
 */
class Projection {
    var vw = GameConfig.VIRTUAL_WIDTH
    var vh = GameConfig.VIRTUAL_HEIGHT
    var horizonY = vh * GameConfig.VANISHING_POINT_Y; private set
    var baseY = vh * GameConfig.PLAYER_BASE_Y; private set
    var ppu = 88f; private set // pixels per world unit at z=0
    var camX = 0f              // world-space camera x (follows player partially)
    var zoom = 1f              // boost zoom-out effect

    fun setViewport(w: Float, h: Float) {
        vw = w; vh = h
        horizonY = vh * GameConfig.VANISHING_POINT_Y
        baseY = vh * GameConfig.PLAYER_BASE_Y
        // 3 lanes (7.5 units) occupy ~92% of virtual width at z=0
        ppu = vw * 0.92f / (GameConfig.LANE_WIDTH * 3f)
    }

    fun scale(z: Float): Float {
        val s = GameConfig.FOCAL_LENGTH / (GameConfig.FOCAL_LENGTH + z)
        return s * zoom
    }

    /** Screen y of a ground-plane point at distance z. */
    fun groundY(z: Float): Float = horizonY + (baseY - horizonY) * scale(z)

    /** Screen x of world-x at distance z. */
    fun screenX(wx: Float, z: Float): Float = vw / 2f + (wx - camX) * ppu * scale(z)

    /** Screen y of a point [height] above the ground at distance z. */
    fun elevatedY(z: Float, height: Float): Float = groundY(z) - height * ppu * scale(z)

    /** Distance fog factor 0 (near) → 1 (at view distance). */
    fun fog(z: Float): Float = Mathz.clamp01((z - 16f) / (GameConfig.VIEW_DISTANCE - 16f) * 1.15f)

    fun update(camTargetX: Float, dt: Float) {
        camX = Mathz.lerp(camX, camTargetX, GameConfig.CAMERA_LERP * dt * 60f)
    }

    fun shakeOffset(shake: Float, rng: kotlin.random.Random): Pair<Float, Float> {
        if (shake <= 0.01f) return 0f to 0f
        return (rng.nextFloat() * 2f - 1f) * shake to (rng.nextFloat() * 2f - 1f) * shake
    }
}
