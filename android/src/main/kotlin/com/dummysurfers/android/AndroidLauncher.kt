package com.dummysurfers.android

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
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
 * v1.2.0 hardening:
 *  - FIXED launch crash: v1.1.1 set `resolutionStrategy = null`, which
 *    NPEs inside GLSurfaceView20.onMeasure() on the very first frame —
 *    the app closed the instant it opened. Never touch that field.
 *  - NEW in-app crash reporter: any future crash is written to internal
 *    storage and offered as a copy/share dialog on the next launch.
 *
 * v5.1.0 hardening (the "opens once, then never again" crash loop):
 *  - The crash file is DELETED the moment it is read. Showing the dialog
 *    used to keep the file around forever, and worse — if anything in the
 *    dialog path threw, onCreate died BEFORE the game started, so every
 *    subsequent launch crashed instantly too. Now:
 *      1. crash file is consumed atomically (read → delete → then show),
 *      2. the whole dialog flow is wrapped — ANY failure falls back to
 *         starting the game normally,
 *      3. the game can therefore never be held hostage by its own reporter.
 *  - Report header reads the real versionName/versionCode from the
 *    PackageManager (the old hardcoded "1.2.0 (code 4)" lied).
 */
class AndroidLauncher : AndroidApplication() {

    private lateinit var crashFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashGuard()
        crashFile = File(filesDir, "crash-last.txt")

        // Consume the crash report FIRST (read + delete in one place) so no
        // failure below can ever resurrect it into a launch loop.
        val last = try {
            crashFile.takeIf { it.isFile }?.readText()?.take(6000).also {
                if (it != null) crashFile.delete()
            }
        } catch (_: Throwable) {
            try { crashFile.delete() } catch (_: Throwable) {}
            null
        }

        if (last != null) {
            try {
                askToReport(last)
            } catch (_: Throwable) {
                // The reporter must never become the crash. Straight into the game.
                startGame()
            }
        } else {
            startGame()
        }
    }

    private fun startGame() {
        val config = AndroidApplicationConfiguration().apply {
            useAccelerometer = false
            useCompass = false
            useGyroscope = false
            useWakelock = true          // → FLAG_KEEP_SCREEN_ON, needs no permission
            maxSimultaneousSounds = 8
            // DO NOT set resolutionStrategy = null: GLSurfaceView20.onMeasure()
            // dereferences it → instant NPE on first frame. Default (Fill) is right.
        }
        initialize(DummySurfersGame(), config)
    }

    // ── Crash reporter ──────────────────────────────────────────────────

    private fun installCrashGuard() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                File(filesDir, "crash-last.txt").writeText(buildReport(t, e))
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(t, e) ?: Runtime.getRuntime().exit(10)
        }
    }

    private fun versionLabel(): String = try {
        val pi = packageManager.getPackageInfo(packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode
        "v${pi.versionName} (code $code)"
    } catch (_: Throwable) {
        "unknown"
    }

    private fun buildReport(t: Thread, e: Throwable): String {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        val at = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        return buildString {
            appendLine("Dummy Surfers crash report — $at")
            appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
            appendLine("version: ${versionLabel()}")
            appendLine("thread: ${t.name}")
            appendLine()
            append(sw.toString())
        }
    }

    private fun askToReport(report: String) {
        val body = TextView(this).apply {
            setText("Something went wrong last time.\nSend this report to the developer (Copy or Share), then tap Start game.\n\n$report")
            setTextIsSelectable(true)
            setPadding(48, 24, 48, 24)
        }
        val scroll = ScrollView(this).apply { addView(body) }
        AlertDialog.Builder(this)
            .setTitle("Oops — Dummy Surfers crashed")
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
            .setNeutralButton("Start game", null)
            .setOnDismissListener { startGame() }
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
