package com.dummysurfers.android

import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.dummysurfers.core.DummySurfersGame

/** Android entry point — portrait, GL ES 2.0, immersive. */
class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = AndroidApplicationConfiguration().apply {
            useAccelerometer = false
            useCompass = false
            useGyroscope = false
            useWakelock = true
            maxSimultaneousSounds = 8
            resolutionStrategy = null
        }
        initialize(DummySurfersGame(), config)
    }
}
