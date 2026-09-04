package com.dummysurfers.android

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.dummysurfers.core.DummySurfersGame
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Android entry point — portrait, GL ES 2.0, immersive.
 *
 * v7.0.0 "SHIPPED ART" — THE PAGE IS GONE.
 *
 * Field history: v6.1.0 introduced a native boot-report overlay so startup
 * was never a black screen; a device then sat on it ("painting textures 2/3")
 * for 30+ minutes. v6.2 added per-texture paint deadlines + a stall watchdog
 * + a restart ladder; v6.3 added a filesDir PNG cache — and the device STILL
 * wedged inside the paint phase. Every patch protected the same wrong
 * constant: the phone painting ~80 procedural textures on its GL thread.
 * v7.0.0 deletes that constant: all art is painted ONCE on the desktop
 * (`:desktop:bakeTextures`), committed under android/assets/gfx-baked, and
 * ships inside the APK. The phone LOADS PNGs — the phase the device froze in
 * no longer exists on the device, and with it the reason for the diagnostic
 * page is gone:
 *
 *  1. NO NATIVE BOOT OVERLAY ANYMORE — the user-visible boot report page is
 *     removed. Boot is a ~1–2 s in-game (GL) loading frame, then the menu.
 *  2. SILENT WATCHDOG stays (no UI): if boot shows zero progress for 20 s it
 *     self-restarts once; a second freeze is recorded but never loops and
 *     never nags. With no painting on the device this is expected to stay
 *     at zero forever.
 *  3. Crash reports from OTHER app versions are dropped on read — upgrading
 *     can never resurrect an old version's "Something went wrong last time"
 *     dialog. (Reports from the current version are still offered once,
 *     after the game is visibly up.)
 *  4. initialize()-level failure (no GL view at all) still shows the full
 *     native fallback screen with Copy/Retry/Close.
 *
 * Still true from v5.4/v6.0: any Java-side crash lands in crash-last.txt,
 * every Activity lifecycle callback is guarded, and the game boots in
 * per-frame stages with its own fallbacks — see DummySurfersGame.
 */
class AndroidLauncher : AndroidApplication() {

    private lateinit var game: DummySurfersGame
    private val uiThread = Handler(Looper.getMainLooper())

    // ── v7.0 silent boot watchdog (NO UI — the page is gone) ─────────────
    // stall #1 (this install) → one silent process restart. stall #2+ →
    // recorded only (crash-last.txt + boot-stalls.txt). Never loops, never
    // nags — and with zero painting on the device it should never fire.
    private val stallFile: File get() = File(filesDir, "boot-stalls.txt")
    // v7.1 FIX: history is loaded in onCreate — filesDir is NOT attached
    // during property initialization, so the old initializer always read 0
    // and the "one restart, then stop" ladder could have LOOPED forever on a
    // device that kept stalling. Now: history is version-tagged, upgrades
    // start clean, and a persistent wedge restarts at most ONCE, ever.
    private var stallCount = 0
    private var stallHandled = false
    private var lastStatusSeen: String? = null
    private var watcherDone = false
    private var pendingReport: String? = null

    private fun loadStallHistory() {
        try {
            val lines = stallFile.readText().trim().lines()
            val count = lines.firstOrNull()?.trim()?.toIntOrNull() ?: 0
            val ver = lines.getOrNull(1)?.trim()?.removePrefix("v=") ?: ""
            if (ver != versionLabel()) {
                // history from another version (or untagged pre-7.1 format) —
                // the new build always starts with its own free restart
                try { stallFile.delete() } catch (_: Throwable) {}
                stallCount = 0
            } else {
                stallCount = count.coerceIn(0, 99)
            }
        } catch (_: Throwable) { stallCount = 0 }
    }

    private fun saveStallHistory() {
        try { stallFile.writeText("$stallCount\nv=${versionLabel()}") } catch (_: Throwable) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashGuard()
        loadStallHistory() // v7.1: context is attached here — safe to read filesDir

        // Consume the last crash report (read + delete atomically) BEFORE the
        // game boots so nothing below can resurrect it into a launch loop.
        // v7.0: reports written by OTHER versions are dropped — an upgrade
        // must never pop a stale "Something went wrong last time" dialog.
        var report: String? = try {
            val f = File(filesDir, "crash-last.txt")
            if (f.isFile) {
                val text = f.readText().take(6000)
                f.delete()
                text
            } else null
        } catch (_: Throwable) {
            try { File(filesDir, "crash-last.txt").delete() } catch (_: Throwable) {}
            null
        }
        if (report != null && !report.contains("version: v${versionLabel()}")) {
            report = null // old version's stall/crash — meaningless now, silently dropped
        }
        pendingReport = report

        // The game's boot screen prints the real version — inject before boot.
        try { DummySurfersGame.bootVersion = versionLabel() } catch (_: Throwable) {}

        try {
            startGame()
        } catch (t: Throwable) {
            // initialize() itself died (GL view / EGL level). Show a real
            // Android screen with the reason + retry — NEVER auto-close.
            writeCrashFile("Java-side launch failure", t)
            try { DummySurfersGame.bootError = "engine start failure: ${t.javaClass.simpleName}: ${t.message?.take(120)}" } catch (_: Throwable) {}
            showFallbackScreen(t)
            return
        }

        installSilentWatchdog()
    }

    private fun startGame() {
        game = DummySurfersGame()
        val config = AndroidApplicationConfiguration().apply {
            useAccelerometer = false
            useCompass = false
            useGyroscope = false
            // v6.0.0: do NOT ask libgdx to manage a wake lock — the keep-screen-on
            // behavior is set directly below with a window flag (needs no
            // permission, touches no PowerManager, cannot throw at onResume time).
            useWakelock = false
            maxSimultaneousSounds = 8
            // v6.1.0: stop leaving the EGL visual to the driver's lottery. The
            // old defaults (RGB565, alpha 0, depth 0) let odd OEM devices pick
            // configs where the GL surface composites BLACK or z-fights badly.
            // 8888 + depth 16 is universally supported on GLES2 hardware.
            r = 8; g = 8; b = 8; a = 8
            depth = 16
            stencil = 0
            numSamples = 0
            useImmersiveMode = true
            // DO NOT set resolutionStrategy = null: GLSurfaceView20.onMeasure()
            // dereferences it → instant NPE on first frame. Default (Fill) is right.
        }
        // Keep the screen on while the game is visible (same effect the old
        // useWakelock=true chased, with zero native/lifecycle involvement).
        try { window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } catch (_: Throwable) {}
        initialize(game, config)
    }

    // ── v7.0 silent watchdog poll — pure logic, zero UI ────────────────────
    private fun installSilentWatchdog() {
        uiThread.postDelayed(silentWatch, 250)
    }

    private val silentWatch = object : Runnable {
        override fun run() {
            var keepGoing = true
            try {
                val ready = DummySurfersGame.bootReady
                val err = DummySurfersGame.bootError
                if (ready) {
                    // a successful boot clears the stall history — a future
                    // stall (if one ever happens) gets the free silent restart
                    try { stallFile.delete() } catch (_: Throwable) {}
                    // offer a CURRENT-VERSION crash report once, after the game
                    // is visibly up; then the watcher retires
                    if (pendingReport != null) {
                        val rep = pendingReport
                        pendingReport = null
                        try { askToReport(rep!!) } catch (_: Throwable) {}
                    }
                    watcherDone = true
                    keepGoing = false
                } else if (err == null) {
                    checkStall(false, null)
                }
                // bootError != null → the GL SafeMode frame owns the recovery
                // UX (tap anywhere to retry). Nothing native to do or show.
            } catch (_: Throwable) {}
            if (keepGoing && !watcherDone) uiThread.postDelayed(this, 250)
        }
    }

    // ── stall recovery (silent) ─────────────────────────────────────────────
    private fun checkStall(ready: Boolean, err: String?) {
        // a status change re-arms the watchdog (BootWatchdog resets internally;
        // the latch here stops repeated handling of ONE frozen episode)
        val status = DummySurfersGame.bootStatus
        if (status != lastStatusSeen) {
            lastStatusSeen = status
            stallHandled = false
        }
        if (stallHandled) return
        val stalled = try {
            com.dummysurfers.core.BootWatchdog.stalled(
                status, DummySurfersGame.bootProgress, ready, err,
                android.os.SystemClock.elapsedRealtime())
        } catch (_: Throwable) { false }
        if (stalled) {
            stallHandled = true
            handleStall()
        }
    }

    private fun handleStall() {
        val where = DummySurfersGame.bootStatus ?: "?"
        stallCount++
        saveStallHistory()
        // persist for diagnosis (also picked up by the crash-report path —
        // offered only if THIS app version ends up showing it)
        try {
            File(filesDir, "crash-last.txt").writeText(reportTextRaw(
                "boot stall #$stallCount",
                "The boot froze at '$where' with no progress for ${com.dummysurfers.core.BootWatchdog.STALL_MS / 1000}s.\n\n" +
                "boot log:\n${DummySurfersGame.bootLogText}"))
        } catch (_: Throwable) {}
        if (stallCount <= 1) {
            // first stall this install: one silent self-heal — restart the
            // process. If the boot freezes again the watcher records it and
            // STOPS: no loops, no dialogs, no page — by design.
            uiThread.postDelayed({ restartApp() }, 500)
        }
    }

    /** Process-level restart — the ONLY recovery that works when the GL
     *  thread is blocked inside a native call. Launches a fresh intent, then
     *  ends this process; Android brings the new task up immediately. */
    private fun restartApp() {
        try {
            val i = packageManager.getLaunchIntentForPackage(packageName)
            i?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            if (i != null) startActivity(i)
        } catch (_: Throwable) {}
        try { finish() } catch (_: Throwable) {}
        uiThread.postDelayed({ Runtime.getRuntime().exit(0) }, 300)
    }

    private fun reportTextRaw(what: String, body: String): String = buildString {
        appendLine("Dummy Surfers report — ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        appendLine("what: $what")
        appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
        appendLine("version: v${versionLabel()}")
        appendLine()
        append(body)
    }

    // ── v6.0 lifecycle immunity ──────────────────────────────────────────
    // onCreate is guarded, but onResume/onPause/onStop run OUTSIDE it — any
    // throw there (device-specific window/audio/input quirks) used to end the
    // process instantly with no fallback. Every callback now fails soft.
    override fun onResume() {
        try { super.onResume() } catch (t: Throwable) {
            writeCrashFile("lifecycle onResume", t)
        }
    }

    override fun onPause() {
        try { super.onPause() } catch (t: Throwable) {
            writeCrashFile("lifecycle onPause", t)
        }
    }

    override fun onStop() {
        try { super.onStop() } catch (t: Throwable) {
            writeCrashFile("lifecycle onStop", t)
        }
    }

    override fun onDestroy() {
        try { super.onDestroy() } catch (t: Throwable) {
            writeCrashFile("lifecycle onDestroy", t)
        }
        watcherDone = true
    }

    /** Full-native last resort when the GL view cannot even be created. */
    private fun showFallbackScreen(t: Throwable) {
        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 64, 48, 48)
            setBackgroundColor(Color.parseColor("#141830"))
        }
        fun title(text: String, size: Float, color: String, bold: Boolean = true): TextView =
            TextView(ctx).apply {
                this.text = text
                textSize = size
                setTextColor(Color.parseColor(color))
                setTypeface(typeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, 16, 0, 16)
            }
        root.addView(title("DUMMY SURFERS", 30f, "#FFC93C"))
        root.addView(title("v${versionLabel()} — couldn't start the 3D engine", 15f, "#C9CFF2", bold = false))
        root.addView(title("The app stays open. Share this error to get it fixed:", 14f, "#FFFFFF", bold = false))

        val body = TextView(ctx).apply {
            setText(reportText("Engine start failure", t))
            setTextIsSelectable(true)
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setTextColor(Color.parseColor("#FFFFFF"))
            setPadding(32, 24, 32, 24)
        }
        val scroll = ScrollView(ctx).apply {
            addView(body)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { setMargins(0, 8, 0, 8) }
        }
        root.addView(scroll)

        fun chip(label: String, bg: String, action: () -> Unit): Button =
            Button(ctx).apply {
                this.text = label
                textSize = 15f
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(bg))
                    cornerRadius = 28f
                }
                setOnClickListener { try { action() } catch (_: Throwable) {} }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 12, 0, 0)
                }
            }
        root.addView(chip("COPY REPORT", "#4A529E") {
            copy(reportText("Engine start failure", t))
            Toast.makeText(ctx, "Report copied", Toast.LENGTH_SHORT).show()
        })
        root.addView(chip("TRY AGAIN", "#3DBB5A") {
            try {
                startGame()
            } catch (again: Throwable) {
                writeCrashFile("Java-side launch failure (retry)", again)
                Toast.makeText(ctx, "Still failing — report copied", Toast.LENGTH_SHORT).show()
                copy(reportText("Engine start failure (retry)", again))
            }
        })
        root.addView(chip("CLOSE APP", "#3A4060") { finish() })

        setContentView(root)
    }

    // ── Crash reporter ──────────────────────────────────────────────────

    private fun installCrashGuard() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            writeCrashFile("uncaught exception on ${t.name}", e)
            previous?.uncaughtException(t, e) ?: Runtime.getRuntime().exit(10)
        }
    }

    private fun writeCrashFile(what: String, e: Throwable) {
        try {
            File(filesDir, "crash-last.txt").writeText(reportText(what, e))
        } catch (_: Throwable) {
        }
    }

    private fun versionLabel(): String = try {
        val pi = packageManager.getPackageInfo(packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode
        "${pi.versionName} (code $code)"
    } catch (_: Throwable) {
        "unknown"
    }

    private fun reportText(what: String, e: Throwable): String {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        val at = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        return buildString {
            appendLine("Dummy Surfers crash report — $at")
            appendLine("what: $what")
            appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
            appendLine("version: v${versionLabel()}")
            appendLine()
            append(sw.toString())
        }
    }

    private fun askToReport(report: String) {
        val body = TextView(this).apply {
            setText("Something went wrong last time.\nSend this report to the developer (Copy or Share) — it names the exact failure.\n\n$report")
            setTextIsSelectable(true)
            setPadding(48, 24, 48, 24)
        }
        val scroll = ScrollView(this).apply { addView(body) }
        AlertDialog.Builder(this)
            .setTitle("Oops — Dummy Surfers hit a snag")
            .setView(scroll)
            .setPositiveButton("Copy report") { _, _ -> copy(report) }
            .setNegativeButton("Share…") { _, _ ->
                try {
                    val i = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Dummy Surfers crash report")
                        putExtra(Intent.EXTRA_TEXT, report)
                    }
                    startActivity(Intent.createChooser(i, "Share crash report"))
                } catch (_: Throwable) {
                }
            }
            .setNeutralButton("Keep playing", null)
            .show()
    }

    private fun copy(text: String) {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Dummy Surfers crash report", text))
        } catch (_: Throwable) {
        }
    }
}
