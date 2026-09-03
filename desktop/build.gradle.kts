plugins {
    application
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
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:1.12.1")
    implementation("com.badlogicgames.gdx:gdx-platform:1.12.1:natives-desktop")
    // freetype lives ONLY here now: build-time font baking (FontBaker task)
    implementation("com.badlogicgames.gdx:gdx-freetype:1.12.1")
    implementation("com.badlogicgames.gdx:gdx-freetype-platform:1.12.1:natives-desktop")
    // BUILD-TIME ONLY: bakes the BMFont atlases (FontBaker). The baked fonts are
    // committed to android/assets/fonts-baked; the device NEVER needs this.
    implementation("com.badlogicgames.gdx:gdx-tools:1.12.1") {
        // gdx-tools drags in every backend; we only need the bmfont writer
        isTransitive = false
    }
}

application {
    mainClass.set("com.dummysurfers.desktop.DesktopLauncherKt")
}

// v6.0.0 crash fix: bake the game fonts to BMFont (.fnt + .png) ON THE DESKTOP
// so the Android app never runs the freetype native at all. Output is
// committed under android/assets/fonts-baked — re-run only when fonts change.
tasks.register("bakeFonts", JavaExec::class) {
    group = "build"
    description = "Bakes UiTheme fonts to BMFont atlases in android/assets/fonts-baked"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.dummysurfers.desktop.FontBakerKt")
    workingDir = projectDir
}

// Desktop launcher reads assets from ../android/assets
sourceSets.getByName("main") {
    resources.setSrcDirs(listOf("src/main/resources", "$rootDir/android/assets"))
}
