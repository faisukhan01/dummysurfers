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

// v7.0.0 SHIPPED ART — the permanent end of the "painting textures" boot freeze.
// A field device froze 30+ minutes painting procedural textures on its GL
// thread; v6.2 deadlines + watchdog and v6.3 filesDir caching each shrank the
// risk without removing the cause. This task removes the cause: it boots the
// FULL game on the desktop in bake mode — every texture painted from live
// code, each one saved as a PNG — into android/assets/gfx-baked/tv3, which is
// COMMITTED and ships inside the APK. The phone then only LOADS PNGs; the
// paint phase (and the boot-report page that exposed it) is gone from the
// device entirely.
// Re-run ONLY when a painter in TextureGen changes (and then bump
// TextureGen.CACHE_GEN so old baked art can never survive).
// Run:  xvfb-run -a ./gradlew :desktop:bakeTextures     (needs a display)
tasks.register("bakeTextures", JavaExec::class) {
    group = "build"
    description = "Paints every game texture once on the desktop → android/assets/gfx-baked (committed; the device never paints)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.dummysurfers.desktop.DesktopLauncherKt")
    workingDir = projectDir
    dependsOn("processResources")
    // Bake mode: TextureGen exports every successful paint into this dir and
    // refuses cache READS (always paints from live code). DS_QUIT_AFTER_BOOT
    // ends the game cleanly once the staged boot completes.
    environment("DS_TEXCACHE_EXPORT", rootProject.layout.projectDirectory.dir("android/assets/gfx-baked").asFile.absolutePath)
    environment("DS_QUIT_AFTER_BOOT", "1")
}

// Desktop launcher reads assets from ../android/assets
sourceSets.getByName("main") {
    resources.setSrcDirs(listOf("src/main/resources", "$rootDir/android/assets"))
}
