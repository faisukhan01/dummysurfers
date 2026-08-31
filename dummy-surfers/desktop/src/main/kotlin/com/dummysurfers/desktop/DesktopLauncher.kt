package com.dummysurfers.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.dummysurfers.core.DummySurfersGame

/** Desktop test launcher — run with `./gradlew desktop:run`. */
fun main() {
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Dummy Surfers by FSK")
        setWindowedMode(540, 960)
        setResizable(true)
        useVsync(true)
    }
    Lwjgl3Application(DummySurfersGame(), config)
}
