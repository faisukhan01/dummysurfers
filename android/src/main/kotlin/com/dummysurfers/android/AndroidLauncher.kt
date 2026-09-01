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
 *    storage and offered as a copy/share dialog on the next launch, so
 *    bug reports work without adb.
 */
class AndroidLauncher : AndroidApplication() {

    private lateinit var crashFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashGuard()
        crashFile = File(filesDir, "crash-last.txt")

        val last = crashFile.takeIf { it.isFile }?.readText()?.take(6000)
        if (last != null) askToReport(last) else startGame()
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

    private fun buildReport(t: Thread, e: Throwable): String {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        val at = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        return buildString {
            appendLine("Dummy Surfers crash report — $at")
            appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
            appendLine("version: 1.2.0 (code 4)")
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
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Dummy Surfers crash report")
                    putExtra(Intent.EXTRA_TEXT, report)
                }
                startActivity(Intent.createChooser(i, "Share crash report"))
            }
            .setNeutralButton("Start game", null)
            .setOnDismissListener { startGame() }
            .show()
    }

    private fun copy(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Dummy Surfers crash report", text))
    }
}
