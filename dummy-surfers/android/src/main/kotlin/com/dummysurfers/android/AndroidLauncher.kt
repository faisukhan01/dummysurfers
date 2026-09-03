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
 * v5.4.0 "the app can never close itself" contract:
 *  1. The game starts IMMEDIATELY on every launch. The crash-report dialog,
 *     when a report exists, is posted 600ms later OVER the running game — it
 *     can no longer gate the game behind a black screen.
 *  2. If even initialize() throws (EGL/GLSurfaceView-level failure), a native
 *     Android fallback screen shows the full error with COPY / TRY AGAIN —
 *     the process stays alive and the player sees exactly why.
 *  3. Inside the game, boot is staged with per-stage fallbacks (fonts →
 *     engine font, textures → white substitutes, core → SafeMode screen with
 *     tap-to-retry). See DummySurfersGame.
 *  4. Any crash is still written to filesDir/crash-last.txt and offered as
 *     Copy/Share on the next launch.
 */
class AndroidLauncher : AndroidApplication() {

    private lateinit var game: DummySurfersGame

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

        try {
            startGame()
        } catch (t: Throwable) {
            // initialize() itself died (GL view / EGL level). Show a real
            // Android screen with the reason + retry — NEVER auto-close.
            writeCrashFile("Java-side launch failure", t)
            showFallbackScreen(t)
            return
        }

        if (report != null) {
            // Game is already booting behind the GL surface — offer the last
            // report non-blockingly. If anything here fails, the game goes on.
            window.decorView.postDelayed({
                try { askToReport(report) } catch (_: Throwable) {}
            }, 600)
        }
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
            // DO NOT set resolutionStrategy = null: GLSurfaceView20.onMeasure()
            // dereferences it → instant NPE on first frame. Default (Fill) is right.
        }
        // Keep the screen on while the game is visible (same effect the old
        // useWakelock=true chased, with zero native/lifecycle involvement).
        try { window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } catch (_: Throwable) {}
        initialize(game, config)
    }

    // ── v6.0.0 lifecycle immunity ──────────────────────────────────────────
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
    }

    /** v5.4: full-native last resort when the GL view cannot even be created. */
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
