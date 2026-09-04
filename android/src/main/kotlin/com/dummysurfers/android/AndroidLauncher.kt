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
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
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
 * v6.1.0 "always visible" contract — the field reports ("error dialog then a
 * black blank page", "takes too time to open") came from three stacked gaps:
 *
 *  1. NATIVE LOADING OVERLAY (new): the moment the GL view exists we pin a
 *     real Android view on top of it — title, progress bar, live boot status
 *     (fed by the game's static BootBridge across the GL/UIThread wall).
 *     The player sees progress from the first second; the app can NEVER
 *     present a black window again, even if GL renders nothing at all.
 *  2. NATIVE ERROR CARD (new): if the boot bridge reports a failure, the
 *     overlay swaps to an error card with the exact stage + COPY REPORT —
 *     pure Android widgets, works even when the GL surface is completely
 *     dead. Taps pass through to the game so SafeMode's tap-to-retry works.
 *  3. The old report dialog now appears only AFTER the game is visibly up.
 *
 *  Still true from v5.4/v6.0: any Java-side crash lands in crash-last.txt
 *  (read + deleted on next launch, offered as Copy/Share), every Activity
 *  lifecycle callback is guarded, and an initialize()-level failure shows
 *  the native fallback screen. The game itself boots in per-frame stages
 *  with its own fallbacks — see DummySurfersGame.
 */
class AndroidLauncher : AndroidApplication() {

    private lateinit var game: DummySurfersGame
    private val uiThread = Handler(Looper.getMainLooper())

    // ── boot overlay state ──────────────────────────────────────────────
    private var overlayRoot: FrameLayout? = null
    private var loadingBox: LinearLayout? = null
    private var errorBox: LinearLayout? = null
    private var statusTv: TextView? = null
    private var tipTv: TextView? = null
    private var versionTv: TextView? = null
    private var progressBar: ProgressBar? = null
    private var copyChip: Button? = null
    private var errorBodyTv: TextView? = null
    private var errorShown = false
    private var overlayGone = false
    private var waitedMs = 0L
    private var pendingReport: String? = null
    private var reportShown = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashGuard()

        // Consume the last crash report (read + delete atomically) BEFORE the
        // game boots so nothing below can resurrect it into a launch loop.
        val report = try {
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
        pendingReport = report
        reportShown = report == null

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

        installBootOverlay()
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

    // ── v6.1 native boot overlay ────────────────────────────────────────────
    // Plain Android widgets pinned OVER the GL surface. Driven by the game's
    // static BootBridge fields at 200ms ticks. This is the layer that makes a
    // black/blank startup structurally impossible: it is visible from the
    // first frames, shows live progress, and if boot fails it becomes the
    // readable error card (no GL required).

    private fun dp(v: Int): Int = (resources.displayMetrics.density * v).toInt()

    private fun installBootOverlay() {
        try {
            val content = findViewById<ViewGroup>(android.R.id.content)
            val root = FrameLayout(this).apply {
                setBackgroundColor(Color.parseColor("#141830"))
                isClickable = true
                isFocusable = true
            }

            // ── loading box ──
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            fun label(text: String, size: Float, color: String, bold: Boolean = false): TextView =
                TextView(this@AndroidLauncher).apply {
                    this.text = text
                    textSize = size
                    setTextColor(Color.parseColor(color))
                    setTypeface(typeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(0, dp(6), 0, dp(6))
                }
            box.addView(label("DUMMY SURFERS", 30f, "#FFC93C", bold = true))
            val ver = label("v${DummySurfersGame.bootVersion}", 13f, "#8A90B8")
            versionTv = ver
            box.addView(ver)
            box.addView(label("Getting the run ready…", 16f, "#FFFFFF"))

            val pb = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = 2
                layoutParams = LinearLayout.LayoutParams(dp(230), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, dp(18), 0, dp(10))
                }
            }
            progressBar = pb
            box.addView(pb)

            val status = label(DummySurfersGame.bootStatus, 14f, "#C9CFF2")
            statusTv = status
            box.addView(status)

            val tip = label("Painting the world for the first time — up to a minute on slower phones", 12f, "#8A90B8")
            tipTv = tip
            box.addView(tip)

            val chip = chipButton("COPY REPORT", "#4A529E") {
                copy(buildString {
                    appendLine("Dummy Surfers boot report — v${DummySurfersGame.bootVersion}")
                    appendLine("status: ${DummySurfersGame.bootStatus}")
                    DummySurfersGame.bootError?.let { appendLine("error: $it") }
                    appendLine()
                    append(DummySurfersGame.bootLogText)
                })
                Toast.makeText(this@AndroidLauncher, "Report copied", Toast.LENGTH_SHORT).show()
            }
            copyChip = chip
            chip.visibility = View.GONE
            box.addView(chip)

            // ── error box (hidden until a boot stage fails) ──
            val ebox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                visibility = View.GONE
                setPadding(dp(20), dp(30), dp(20), dp(20))
            }
            ebox.addView(label("STARTUP PROBLEM", 24f, "#FF5A3C", bold = true))
            ebox.addView(label("The app stays open. Copy this report to get it fixed:", 13f, "#FFFFFF"))
            val body = TextView(this).apply {
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.parseColor("#C9CFF2"))
                setPadding(dp(16), dp(12), dp(16), dp(12))
                setTextIsSelectable(true)
            }
            errorBodyTv = body
            val scroll = ScrollView(this).apply {
                addView(body)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                    setMargins(0, dp(10), 0, dp(10))
                }
            }
            ebox.addView(scroll)
            ebox.addView(chipButton("COPY REPORT", "#4A529E") {
                copy(buildString {
                    appendLine("Dummy Surfers boot report — v${DummySurfersGame.bootVersion}")
                    appendLine(DummySurfersGame.bootLogText)
                })
                Toast.makeText(this@AndroidLauncher, "Report copied", Toast.LENGTH_SHORT).show()
            })
            ebox.addView(chipButton("RETRY BOOT", "#3DBB5A") {
                try { DummySurfersGame.bootError = null; DummySurfersGame.bootReady = false } catch (_: Throwable) {}
            })
            ebox.addView(label("…or tap the screen — the game retries by itself", 12f, "#8A90B8"))

            root.addView(ebox, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                setMargins(dp(16), dp(24), dp(16), dp(24))
            })
            root.addView(box, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
                setMargins(dp(28), 0, dp(28), 0)
            })

            content.addView(root, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            overlayRoot = root
            loadingBox = box
            errorBox = ebox
            uiThread.postDelayed(pollBoot, 200)
        } catch (_: Throwable) {
            // Overlay is a safety layer — it must never be the thing that
            // breaks the app. GL boot screen + SafeMode still work without it.
        }
    }

    private fun chipButton(label: String, bg: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 14f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor(bg))
                cornerRadius = dp(26).toFloat()
            }
            setOnClickListener { try { action() } catch (_: Throwable) {} }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, dp(10), 0, 0)
            }
        }

    private val pollBoot = object : Runnable {
        override fun run() {
            try {
                val ready = DummySurfersGame.bootReady
                val err = DummySurfersGame.bootError
                if (!overlayGone) {
                    if (!errorShown) {
                        progressBar?.progress = (DummySurfersGame.bootProgress * 100).toInt().coerceIn(0, 100)
                        statusTv?.text = DummySurfersGame.bootStatus
                        waitedMs += 200
                        if (err != null) {
                            showError(err)
                        } else if (ready) {
                            dismissOverlay()
                        } else if (waitedMs > 30_000) {
                            tipTv?.text = "Still loading — this device is very slow. If it never finishes, copy the report:"
                            copyChip?.visibility = View.VISIBLE
                        }
                    } else {
                        // error card visible — watch for a successful tap-retry
                        if (err == null && !ready) showLoadingAgain()
                        else if (ready) dismissOverlay()
                        else errorBodyTv?.text = buildString {
                            appendLine(DummySurfersGame.bootLogText)
                            appendLine()
                            appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})")
                            append("version: v${DummySurfersGame.bootVersion}")
                        }
                    }
                }
                // offer the consumed crash report only once the game is visibly up
                if (ready && !reportShown && overlayGone) {
                    pendingReport?.let { try { askToReport(it) } catch (_: Throwable) {} }
                    reportShown = true
                }
            } catch (_: Throwable) {}
            if (!overlayGone || !reportShown) uiThread.postDelayed(this, 200)
        }
    }

    private fun showError(err: String) {
        errorShown = true
        loadingBox?.visibility = View.GONE
        errorBox?.visibility = View.VISIBLE
        errorBodyTv?.text = buildString {
            appendLine(err)
            appendLine()
            append(DummySurfersGame.bootLogText)
        }
        // let taps fall through to the GL SafeMode (tap anywhere to retry)
        overlayRoot?.isClickable = false
        overlayRoot?.isFocusable = false
    }

    private fun showLoadingAgain() {
        errorShown = false
        errorBox?.visibility = View.GONE
        loadingBox?.visibility = View.VISIBLE
        statusTv?.text = DummySurfersGame.bootStatus
        tipTv?.text = "Retrying…"
        copyChip?.visibility = View.GONE
        overlayRoot?.isClickable = true
        overlayRoot?.isFocusable = true
    }

    private fun dismissOverlay() {
        val root = overlayRoot ?: return
        overlayGone = true
        try {
            root.animate().alpha(0f).setDuration(260f.toLong()).withEndAction {
                try { (root.parent as? ViewGroup)?.removeView(root) } catch (_: Throwable) {}
            }.start()
        } catch (_: Throwable) {
            try { (root.parent as? ViewGroup)?.removeView(root) } catch (_: Throwable) {}
        }
        overlayRoot = null
        uiThread.postDelayed({
            if (!reportShown) uiThread.postDelayed(pollBoot, 200)
        }, 300)
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
        try { uiThread.removeCallbacks(pollBoot) } catch (_: Throwable) {}
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
