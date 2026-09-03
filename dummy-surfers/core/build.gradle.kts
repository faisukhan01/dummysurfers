plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api("com.badlogicgames.gdx:gdx:1.12.1")
    // v6.0.0: gdx-freetype REMOVED from the game — fonts ship pre-baked
    // (android/assets/fonts-baked). freetype now only runs on the desktop
    // inside the build-time FontBaker (declared in :desktop).
}
