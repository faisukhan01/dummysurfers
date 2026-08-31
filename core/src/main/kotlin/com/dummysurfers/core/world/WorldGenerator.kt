package com.dummysurfers.core.world

import com.dummysurfers.core.config.GameConfig
import com.dummysurfers.core.utils.Mathz
import kotlin.random.Random

/** Environment decoration kinds. */
enum class DecoKind {
    BUILDING, SKYSCRAPER, POLE, BILLBOARD, LAMP, TREE, PLATFORM, SHELTER,
    STATION_SIGN, BENCH, SIGNAL, GRAFFITI_WALL, BRIDGE_GIRDER, TUNNEL_ARCH,
    TUNNEL_WALL, FENCE, BUSH
}

/** One decoration instance (pooled via ArrayList swap-remove). */
class Deco {
    var kind = DecoKind.BUILDING
    var side = 1
    var x = 0f
    var z = 0f
    var h = 5f
    var w = 4f
    var variant = 0
    var lit = false
}

enum class SegmentKind { OPEN, URBAN, STATION, BRIDGE, TUNNEL, INDUSTRIAL }

/**
 * Procedural endless world: segments ahead are decorated, segments behind
 * are recycled. Segment types vary the environment density/identity.
 */
class WorldGenerator {
    val decos = ArrayList<Deco>(220)
    private val pool = ArrayList<Deco>(220)
    private var nextZ = 0f
    private var rng = Random(1)

    val tunnelRanges = ArrayList<FloatArray>() // [zStart, zEnd]

    fun reset(seed: Int = (System.nanoTime() and 0xffffff).toInt()) {
        rng = Random(seed)
        decos.forEach { pool.add(it) }
        decos.clear()
        nextZ = -14f
        tunnelRanges.clear()
        // prime the world ahead
        while (nextZ < GameConfig.VIEW_DISTANCE * 1.6f) spawnSegment()
    }

    /** Scroll world; spawn new segments as the horizon expands. */
    fun update(scrollDelta: Float) {
        nextZ -= scrollDelta
        for (r in tunnelRanges) { r[0] -= scrollDelta; r[1] -= scrollDelta }
        while (nextZ < GameConfig.VIEW_DISTANCE * 1.6f) spawnSegment()

        var i = 0
        while (i < decos.size) {
            val d = decos[i]
            d.z -= scrollDelta
            if (d.z < -20f) {
                pool.add(d)
                decos[i] = decos[decos.size - 1]
                decos.removeAt(decos.size - 1)
            } else i++
        }
        var t = 0
        while (t < tunnelRanges.size) {
            if (tunnelRanges[t][1] < -10f) tunnelRanges.removeAt(t) else t++
        }
    }

    private fun obtain(): Deco = pool.removeLastOrNull() ?: Deco()

    private fun add(kind: DecoKind, side: Int, x: Float, z: Float, h: Float, w: Float, variant: Int = 0, lit: Boolean = false) {
        val d = obtain()
        d.kind = kind; d.side = side; d.x = x; d.z = z; d.h = h; d.w = w
        d.variant = variant; d.lit = lit
        decos.add(d)
    }

    private fun spawnSegment() {
        val z0 = nextZ
        val kind = when {
            z0 < 30f -> SegmentKind.OPEN
            else -> {
                val roll = rng.nextFloat()
                when {
                    roll < 0.30f -> SegmentKind.URBAN
                    roll < 0.48f -> SegmentKind.OPEN
                    roll < 0.62f -> SegmentKind.STATION
                    roll < 0.74f -> SegmentKind.INDUSTRIAL
                    roll < 0.86f -> SegmentKind.BRIDGE
                    else -> SegmentKind.TUNNEL
                }
            }
        }
        val segLen = GameConfig.SEGMENT_LENGTH
        val edgeX = GameConfig.LANE_WIDTH * 1.5f

        when (kind) {
            SegmentKind.OPEN -> {
                decorateOpen(z0, segLen, edgeX, 0.55f)
            }
            SegmentKind.URBAN -> {
                decorateOpen(z0, segLen, edgeX, 1f)
            }
            SegmentKind.STATION -> {
                decorateOpen(z0, segLen, edgeX, 0.5f)
                val side = if (rng.nextBoolean()) -1 else 1
                add(DecoKind.PLATFORM, side, side * (edgeX + 2.2f), z0 + segLen / 2, 0.55f, segLen)
                add(DecoKind.SHELTER, side, side * (edgeX + 3.4f), z0 + segLen * 0.4f, 2.6f, 7f)
                add(DecoKind.SHELTER, side, side * (edgeX + 3.4f), z0 + segLen * 0.7f, 2.6f, 7f)
                add(DecoKind.STATION_SIGN, side, side * (edgeX + 1.6f), z0 + segLen * 0.55f, 2.2f, 1.6f)
                add(DecoKind.BENCH, side, side * (edgeX + 2.4f), z0 + segLen * 0.3f, 0.7f, 1.8f)
                add(DecoKind.BENCH, side, side * (edgeX + 2.4f), z0 + segLen * 0.62f, 0.7f, 1.8f)
                if (rng.nextFloat() < 0.7f) add(DecoKind.TREE, side, side * (edgeX + 5.5f), z0 + segLen * 0.85f, 3f, 1.8f)
            }
            SegmentKind.INDUSTRIAL -> {
                for (i in 0 until 4) {
                    val side = if (rng.nextBoolean()) -1 else 1
                    add(DecoKind.GRAFFITI_WALL, side, side * (edgeX + 1.8f), z0 + i * segLen / 4f + 3f, 2.8f, 9f, rng.nextInt(4))
                }
                add(DecoKind.BUILDING, 1, edgeX + 6.5f, z0 + 8f, Mathz.rnd(rng, 5f, 9f), 6f, rng.nextInt(8), true)
                add(DecoKind.BUILDING, -1, -(edgeX + 6.8f), z0 + 16f, Mathz.rnd(rng, 4f, 7f), 5f, rng.nextInt(8), false)
                add(DecoKind.SIGNAL, -1, -(edgeX + 0.8f), z0 + 12f, 2.6f, 0.5f)
            }
            SegmentKind.BRIDGE -> {
                add(DecoKind.BRIDGE_GIRDER, -1, -(edgeX + 0.9f), z0 + segLen / 2, 1.4f, segLen)
                add(DecoKind.BRIDGE_GIRDER, 1, edgeX + 0.9f, z0 + segLen / 2, 1.4f, segLen)
                add(DecoKind.LAMP, -1, -(edgeX + 1.6f), z0 + 6f, 3.4f, 0.4f)
                add(DecoKind.LAMP, 1, edgeX + 1.6f, z0 + 19f, 3.4f, 0.4f)
            }
            SegmentKind.TUNNEL -> {
                tunnelRanges.add(floatArrayOf(z0, z0 + segLen))
                add(DecoKind.TUNNEL_WALL, -1, -(edgeX + 1.6f), z0 + segLen / 2, 6.5f, segLen)
                add(DecoKind.TUNNEL_WALL, 1, edgeX + 1.6f, z0 + segLen / 2, 6.5f, segLen)
                var az = z0 + 3f
                while (az < z0 + segLen) {
                    add(DecoKind.TUNNEL_ARCH, 0, 0f, az, 7f, 1.4f, rng.nextInt(3))
                    az += 5.5f
                }
                add(DecoKind.LAMP, -1, -(edgeX + 1.2f), z0 + 8f, 4.2f, 0.4f)
                add(DecoKind.LAMP, 1, edgeX + 1.2f, z0 + 18f, 4.2f, 0.4f)
            }
        }
        nextZ += segLen
    }

    private fun decorateOpen(z0: Float, segLen: Float, edgeX: Float, density: Float) {
        var z = z0 + 2f
        while (z < z0 + segLen) {
            // buildings both sides
            for (side in intArrayOf(-1, 1)) {
                if (rng.nextFloat() < 0.82f * density) {
                    val tall = rng.nextFloat() < 0.25f
                    val h = if (tall) Mathz.rnd(rng, 9f, 15f) else Mathz.rnd(rng, 3.5f, 8f)
                    val w = Mathz.rnd(rng, 4f, 7.5f)
                    val x = side * (edgeX + 3.2f + rng.nextFloat() * 5.5f)
                    add(if (tall) DecoKind.SKYSCRAPER else DecoKind.BUILDING, side, x, z, h, w, rng.nextInt(8), rng.nextFloat() < 0.55f)
                    if (rng.nextFloat() < 0.16f) add(DecoKind.BILLBOARD, side, side * (edgeX + 1.4f), z + 1.5f, 3.2f, 3.6f, rng.nextInt(4))
                } else if (rng.nextFloat() < 0.5f * density) {
                    add(DecoKind.TREE, side, side * (edgeX + 2.6f + rng.nextFloat() * 3f), z, Mathz.rnd(rng, 2.4f, 4f), 1.8f)
                } else if (rng.nextFloat() < 0.35f) {
                    add(DecoKind.BUSH, side, side * (edgeX + 2.2f + rng.nextFloat() * 2f), z, 0.8f, 1.2f)
                }
            }
            // street furniture
            if (rng.nextFloat() < 0.5f) add(DecoKind.LAMP, if (rng.nextBoolean()) -1 else 1, (if (rng.nextBoolean()) -1 else 1) * (edgeX + 1.5f), z + 1f, 3.6f, 0.4f)
            if (rng.nextFloat() < 0.3f) add(DecoKind.SIGNAL, if (rng.nextBoolean()) -1 else 1, 0f, z, 0f, 0f, rng.nextInt(2))
            z += Mathz.rnd(rng, 7f, 13f)
        }
        // catenary poles on a regular rhythm with wires
        var pz = Mathz.hash01((z0 * 13).toInt()) * 14f + z0
        while (pz < z0 + segLen) {
            val side = if (((pz / 14f).toInt() % 2) == 0) -1 else 1
            add(DecoKind.POLE, side, side * (edgeX + 0.7f), pz, 5.4f, 0.45f)
            pz += 14f
        }
    }

    companion object {
        /** Catenary wire y-sag between poles (used by renderer). */
        fun wireSag(t: Float): Float = Mathz.sinPi(t) * 0.35f
    }
}
