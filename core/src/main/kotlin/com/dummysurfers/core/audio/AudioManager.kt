package com.dummysurfers.core.audio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.AudioDevice
import com.dummysurfers.core.state.GameEvent
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 100% procedural audio: an oscillator/noise synth mixing into a raw PCM
 * AudioDevice thread. Music is a scheduled pattern loop whose intensity
 * follows game speed; every SFX is synthesized on demand (no files).
 */
class AudioManager {
    private var device: AudioDevice? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    @Volatile private var musicOn = true
    @Volatile private var sfxOn = true
    @Volatile private var intensity = 0f // 0..1 follows game speed

    private val sfxQueue = ConcurrentLinkedQueue<FloatArray>()
    private val active = ArrayList<Voice>()
    private val rng = Random(7)

    private val sampleRate = 44100

    private fun pw(a: Float, b: Float): Float = Math.pow(a.toDouble(), b.toDouble()).toFloat()
    private val noiseBuf = FloatArray(sampleRate)

    // ── Music state ────────────────────────────────────────────────────
    private var samplePos = 0L
    private var nextNoteSample = 0L
    private var step = 0

    private class Voice(var data: FloatArray, var pos: Int, var gain: Float)

    fun start() {
        if (running) return
        for (i in noiseBuf.indices) noiseBuf[i] = rng.nextFloat() * 2f - 1f
        running = true
        device = Gdx.audio.newAudioDevice(sampleRate, true)
        thread = Thread({ loop() }, "DummyAudio")
        thread?.isDaemon = true
        thread?.start()
    }

    fun dispose() {
        running = false
        try { thread?.join(500) } catch (_: Exception) {}
        device?.dispose()
        device = null
        thread = null
    }

    fun setMusic(on: Boolean) { musicOn = on }
    fun setSfx(on: Boolean) { sfxOn = on }
    fun setIntensity(v: Float) { intensity = v.coerceIn(0f, 1f) }

    // ── Public SFX API ─────────────────────────────────────────────────
    fun play(event: GameEvent, param: Float = 0f) {
        if (!sfxOn) return
        val buf = when (event) {
            GameEvent.COIN -> coin(param)          // param = streak 0..12
            GameEvent.JUMP -> tone(280f, 520f, 0.14f, 0.5f, "square")
            GameEvent.SLIDE -> noiseSweep(0.2f, 900f, 240f, 0.4f)
            GameEvent.LANE -> noiseSweep(0.09f, 1600f, 500f, 0.22f)
            GameEvent.CRASH -> crash()
            GameEvent.POWERUP -> arp(floatArrayOf(523f, 659f, 784f, 1046f), 0.34f, 0.4f, "triangle")
            GameEvent.HORN -> horn()
            GameEvent.CLICK -> tone(1150f, 900f, 0.035f, 0.3f, "square")
            GameEvent.NEAR_MISS -> noiseSweep(0.16f, 2400f, 300f, 0.3f)
            GameEvent.NEW_BEST -> arp(floatArrayOf(523f, 659f, 784f, 1046f, 1318f), 0.5f, 0.4f, "triangle")
            GameEvent.GAME_OVER -> arp(floatArrayOf(392f, 311f, 233f), 0.7f, 0.35f, "saw")
            GameEvent.FOOTSTEP -> footstep()
            GameEvent.SHIELD_BREAK -> glass()
            GameEvent.LAND -> noiseSweep(0.05f, 500f, 180f, 0.16f)
            GameEvent.TUTORIAL_STEP -> arp(floatArrayOf(659f, 880f), 0.2f, 0.3f, "sine")
            GameEvent.STUMBLE -> noiseSweep(0.22f, 700f, 120f, 0.5f)             // heavy scrape-thud
            GameEvent.WHISTLE -> whistle()                                        // guard referee whistle
            GameEvent.CAUGHT -> arp(floatArrayOf(440f, 349f, 293f, 233f), 0.8f, 0.4f, "square") // grab sting
        }
        sfxQueue.offer(buf)
    }

    // ── Synth primitives (render full PCM buffers) ─────────────────────
    private fun tone(f0: Float, f1: Float, dur: Float, gain: Float, wave: String): FloatArray {
        val n = (dur * sampleRate).toInt()
        val out = FloatArray(n)
        var phase = 0f
        for (i in 0 until n) {
            val t = i / n.toFloat()
            val f = f0 * pw(f1 / f0, t)
            phase += 2f * PI.toFloat() * f / sampleRate
            val s = when (wave) {
                "square" -> if (sin(phase) > 0) 1f else -1f
                "saw" -> 2f * ((phase / (2f * PI.toFloat())) % 1f) - 1f
                "triangle" -> 2f * kotlin.math.abs(2f * ((phase / (2f * PI.toFloat())) % 1f) - 1f) - 1f
                else -> sin(phase)
            }
            val env = attackRelease(i, n, 0.008f, 0.25f)
            out[i] = s * env * gain
        }
        return out
    }

    private fun attackRelease(i: Int, n: Int, attack: Float, relFrac: Float): Float {
        val a = (attack * sampleRate).toInt().coerceAtLeast(1)
        val r = (n * relFrac).toInt().coerceAtLeast(1)
        val e = when {
            i < a -> i / a.toFloat()
            i > n - r -> (n - i) / r.toFloat()
            else -> 1f
        }
        return e.coerceIn(0f, 1f)
    }

    private fun noiseSweep(dur: Float, f0: Float, f1: Float, gain: Float): FloatArray {
        val n = (dur * sampleRate).toInt()
        val out = FloatArray(n)
        var lp = 0f
        for (i in 0 until n) {
            val t = i / n.toFloat()
            val cutoff = f0 * pw(f1 / f0, t)
            val alpha = (cutoff / sampleRate).coerceIn(0.01f, 0.95f)
            lp += alpha * (noiseBuf[(((samplePos + i) % sampleRate).toInt())] - lp)
            out[i] = lp * attackRelease(i, n, 0.01f, 0.5f) * gain
        }
        return out
    }

    private fun coin(streak: Float): FloatArray {
        val semis = streak.coerceIn(0f, 12f)
        val f = 1046f * pw(2f, semis / 12f)
        val n = (0.09f * sampleRate).toInt()
        val out = FloatArray(n)
        var phase = 0f
        var phase2 = 0f
        for (i in 0 until n) {
            phase += 2f * PI.toFloat() * f / sampleRate
            phase2 += 2f * PI.toFloat() * f * 2.01f / sampleRate
            val env = attackRelease(i, n, 0.002f, 0.85f)
            out[i] = (sin(phase) * 0.7f + sin(phase2) * 0.3f) * env * 0.42f
        }
        return out
    }

    private fun crash(): FloatArray {
        val n = (0.45f * sampleRate).toInt()
        val out = FloatArray(n)
        var lp = 0f
        for (i in 0 until n) {
            val t = i / n.toFloat()
            lp += 0.35f * (noiseBuf[(((samplePos + i) % sampleRate).toInt())] - lp)
            val thump = sin(2f * PI.toFloat() * (110f * (1f - t * 0.7f)) * i / sampleRate) * (1f - t) * 0.9f
            out[i] = (thump + lp * 0.8f * (1f - t * t)) * attackRelease(i, n, 0.001f, 0.7f) * 0.6f
        }
        return out
    }

    private fun glass(): FloatArray {
        val n = (0.3f * sampleRate).toInt()
        val out = FloatArray(n)
        val freqs = floatArrayOf(1900f, 2533f, 3170f)
        for (i in 0 until n) {
            var s = 0f
            for (k in freqs.indices) {
                s += sin(2f * PI.toFloat() * freqs[k] * (1f + 0.02f * sin(2f * PI.toFloat() * 13f * i / sampleRate)) * i / sampleRate) * 0.3f
            }
            out[i] = s * attackRelease(i, n, 0.001f, 0.9f) * 0.3f
        }
        return out
    }

    private fun arp(freqs: FloatArray, dur: Float, gain: Float, wave: String): FloatArray {
        val noteN = (dur / freqs.size * sampleRate).toInt()
        val out = FloatArray((dur * sampleRate).toInt())
        for ((idx, f) in freqs.withIndex()) {
            val seg = tone(f, f, dur / freqs.size, gain, wave)
            val off = idx * noteN
            for (i in seg.indices) if (off + i < out.size) out[off + i] += seg[i]
        }
        return out
    }

    private fun horn(): FloatArray {
        val n = (0.55f * sampleRate).toInt()
        val out = FloatArray(n)
        for (i in 0 until n) {
            val vib = 1f + 0.01f * sin(2f * PI.toFloat() * 6f * i / sampleRate)
            val s = sawAt(233f * vib, i) * 0.5f + sawAt(311f * vib, i) * 0.5f
            out[i] = s * attackRelease(i, n, 0.03f, 0.35f) * 0.3f
        }
        return out
    }

    /** v3.0: guard's referee whistle — two shrill trills with a warble. */
    private fun whistle(): FloatArray {
        val dur = 0.5f
        val n = (dur * sampleRate).toInt()
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i / sampleRate
            // warble around 2100Hz, gap between two trills
            val trill = if (t < 0.21f || t > 0.27f) 1f else 0f
            val warble = 1f + 0.045f * sin(2f * PI.toFloat() * 38f * t)
            val s = sin(2f * PI.toFloat() * 2100f * warble * t) + 0.35f * sin(2f * PI.toFloat() * 2800f * warble * t)
            out[i] = s * trill * attackRelease(i, n, 0.01f, 0.12f) * 0.24f
        }
        return out
    }

    private fun sawAt(f: Float, i: Int): Float {
        val p = (f * i / sampleRate) % 1f
        return 2f * p - 1f
    }

    private fun footstep(): FloatArray {
        val n = (0.03f * sampleRate).toInt()
        val out = FloatArray(n)
        var lp = 0f
        for (i in 0 until n) {
            lp += 0.4f * (noiseBuf[(((samplePos + i) % sampleRate).toInt())] - lp)
            out[i] = lp * (1f - i / n.toFloat()) * 0.16f
        }
        return out
    }

    // ── Music synthesis ────────────────────────────────────────────────
    private fun kick(): FloatArray {
        val n = (0.16f * sampleRate).toInt()
        val out = FloatArray(n)
        for (i in 0 until n) {
            val t = i / n.toFloat()
            val f = 130f * pw(0.45f, t * 8f)
            out[i] = sin(2f * PI.toFloat() * f * i / sampleRate) * (1f - t) * 0.85f
        }
        return out
    }

    private fun hat(open: Boolean): FloatArray {
        val n = ((if (open) 0.09f else 0.03f) * sampleRate).toInt()
        val out = FloatArray(n)
        var hp = 0f
        var prev = 0f
        for (i in 0 until n) {
            val x = noiseBuf[(((samplePos + i) % sampleRate).toInt())]
            hp = 0.7f * (hp + x - prev)
            prev = x
            out[i] = hp * (1f - i / n.toFloat()) * (0.16f + 0.12f * intensity)
        }
        return out
    }

    private fun bass(f: Float, dur: Float): FloatArray {
        val n = (dur * sampleRate).toInt()
        val out = FloatArray(n)
        for (i in 0 until n) {
            val p = f * i / sampleRate
            val sq = if (p % 1f < 0.5f) 1f else -1f
            val env = min(1f, i / (0.004f * sampleRate)) * exp(-3.2f * i / n)
            out[i] = sq * env * 0.26f
        }
        return out
    }

    private fun lead(f: Float, dur: Float): FloatArray {
        val n = (dur * sampleRate).toInt()
        val out = FloatArray(n)
        for (i in 0 until n) {
            val p = f * i / sampleRate
            val tri = 2f * kotlin.math.abs(2f * (p % 1f) - 1f) - 1f
            val env = min(1f, i / (0.006f * sampleRate)) * exp(-4.5f * i / n)
            out[i] = tri * env * (0.1f + 0.14f * intensity)
        }
        return out
    }

    // ── Mixer thread ───────────────────────────────────────────────────
    private fun loop() {
        val chunk = 1024
        val mixBuf = FloatArray(chunk)
        val outBuf = ShortArray(chunk)
        val clockNanos = System.nanoTime()
        // per-step synth caches
        val kickBuf = kick(); val hatC = hat(false); val hatO = hat(true)
        val bassCache = HashMap<Float, FloatArray>()
        val leadCache = HashMap<Float, FloatArray>()
        val bassLine = intArrayOf(0, 0, 7, 0, 5, 0, 3, 5)   // semitone offsets from A1
        val leadPattern = intArrayOf(12, 15, 19, 15, 17, 12, 10, 12, 15, 19, 22, 19, 17, 15, 12, 10)
        val stepDur = 60f / 132f / 4f // 16th notes @132bpm
        val stepSamples = (stepDur * sampleRate).toLong()

        while (running) {
            val dev = device ?: break
            // realtime pacing: if a dummy/non-blocking audio device lets us run
            // ahead of the wall clock, throttle so the sequencer stays at 1x and
            // samplePos can't sprint into Int overflow
            val expectedSamples = ((System.nanoTime() - clockNanos) / 1e9f * sampleRate).toLong()
            if (samplePos > expectedSamples + sampleRate / 2) {
                try { Thread.sleep(4) } catch (_: InterruptedException) {}
            }
            // drain SFX queue
            var q = sfxQueue.poll()
            while (q != null) {
                if (active.size < 24) active.add(Voice(q, 0, 1f))
                q = sfxQueue.poll()
            }

            for (i in 0 until chunk) {
                var s = 0f
                val globalSample = samplePos + i

                // music sequencer
                if (musicOn && globalSample >= nextNoteSample) {
                    val st = step % 16
                    val bar = (step / 16) % 4
                    // kick on quarters
                    if (st % 4 == 0) active.add(Voice(kickBuf, 0, 0.9f))
                    // hats on 8ths, open on offbeat of bar 2/4 (intensity scales at schedule time)
                    if (st % 2 == 0) active.add(Voice(if (st == 6 && bar % 2 == 1) hatO else hatC, 0, 0.55f + 0.55f * intensity))
                    // bass 8ths
                    if (st % 2 == 0) {
                        val semi = bassLine[(st / 2 + bar * 2) % bassLine.size]
                        val f = 55f * pw(2f, semi / 12f)
                        val b = bassCache.getOrPut(f) { bass(f, stepDur * 1.8f) }
                        active.add(Voice(b, 0, 1f))
                    }
                    // lead melody 16ths in later phase
                    if (intensity > 0.12f) {
                        val semi = leadPattern[st]
                        if (semi != -99) {
                            val f = 220f * pw(2f, semi / 12f)
                            val l = leadCache.getOrPut(f) { lead(f, stepDur * 1.6f) }
                            active.add(Voice(l, 0, 0.35f + 0.75f * intensity))
                        }
                    }
                    if (active.size > 40) active.removeAt(0)
                    step++
                    nextNoteSample = globalSample + stepSamples
                }

                // mix active voices
                var vi = 0
                while (vi < active.size) {
                    val v = active[vi]
                    if (v.pos < v.data.size) {
                        s += v.data[v.pos] * v.gain
                        v.pos++
                        vi++
                    } else {
                        active[vi] = active[active.size - 1]
                        active.removeAt(active.size - 1)
                    }
                }
                mixBuf[i] = s.coerceIn(-1f, 1f)
            }

            for (i in 0 until chunk) outBuf[i] = (mixBuf[i] * 32700f).toInt().toShort()
            dev.writeSamples(outBuf, 0, chunk)
            samplePos += chunk
        }
    }
}
