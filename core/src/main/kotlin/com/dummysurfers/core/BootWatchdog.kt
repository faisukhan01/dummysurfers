package com.dummysurfers.core

/**
 * v6.2.0 BOOT WATCHDOG — the "30 minutes on painting textures 2/3" killer.
 *
 * FIELD REPORT (v6.1.0): a device sat on the boot screen at "painting
 * textures 2/3" for 30+ minutes. The native overlay's UI thread was alive
 * (it kept rendering the same status text every 200 ms) — but NOTHING
 * watched whether that status was actually CHANGING, and nothing had the
 * authority to recover. The GL thread was stuck, the player had a frozen
 * app, and the only way out was killing it by hand.
 *
 * This object is the missing judge. It is PURE logic (no Android imports —
 * the same class ships in the desktop QA harness, where a selftest can
 * execute it), driven by the Android overlay's existing 200 ms poll:
 *
 *     every tick → BootWatchdog.stalled(status, progress, ready, error, now)
 *     returns true EXACTLY when the boot has stopped making progress for
 *     [STALL_MS] while the game is neither ready nor in an error state.
 *
 * Progress = the boot status string, which since v6.2 includes a per-texture
 * counter ("painting textures 2/3 · 7 painted") — a chunk painting at any
 * speed changes the string every few hundred ms, so a 20 s silence genuinely
 * means FROZEN (native GL call blocked, GC death-spiral, driver stall…),
 * not merely slow. The launcher reacts to `true` with its recovery ladder:
 * first stall → silent process restart; stall again → native card with
 * RESTART APP / COPY REPORT / CLOSE APP. A 30-minute hang is structurally
 * impossible from here on.
 */
object BootWatchdog {

    /** No progress for this long while booting = frozen. 20 s: a boot that
     *  changes nothing for 20 s on ANY device is not loading, it is stuck
     *  (normal full boots finish in well under a minute WITH visible ticks
     *  every few hundred ms). Env override exists for the desktop selftest
     *  only — Android has no env to set. */
    val STALL_MS: Long =
        System.getenv("DS_WATCHDOG_STALL_MS")?.toLongOrNull()?.coerceAtLeast(1_000L) ?: 20_000L

    @Volatile private var lastKey: String? = null
    @Volatile private var lastChangeMs: Long = 0L

    /** Feed once per overlay tick. Returns true exactly once per stall episode
     *  — i.e. when [status]+[progress] stayed identical for ≥ [STALL_MS] while
     *  [ready] is false and [error] is null. Any change resets the window;
     *  reaching ready or an error disarms and clears state. */
    fun stalled(
        status: String?,
        progress: Float,
        ready: Boolean,
        error: String?,
        nowMs: Long,
    ): Boolean {
        if (ready || !error.isNullOrEmpty()) { reset(); return false }
        val key = "${status ?: "?"}|${(progress * 1000f).toInt()}"
        if (key != lastKey) {
            lastKey = key
            lastChangeMs = nowMs
            return false
        }
        return nowMs - lastChangeMs >= STALL_MS
    }

    /** Clear all state (new boot attempt / after recovery). */
    fun reset() {
        lastKey = null
        lastChangeMs = 0L
    }
}
