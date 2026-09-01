plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// LibGDX natives jars carry their .so files at the JAR ROOT (libgdx.so,
// libgdx-freetype.so) instead of lib/<abi>/, so AGP cannot package them as
// plain dependencies. We resolve them via this custom configuration and
// extract them into per-ABI folders ourselves (see extractGdxNatives below).
val gdxNatives by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

android {
    namespace = "com.dummysurfers.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fsk.dummysurfers"
        minSdk = 24
        targetSdk = 34
        versionCode = 15
        versionName = "4.3.0"
    }

    signingConfigs {
        create("release") {
            // Keystore is committed on purpose (hobby project): keeps every CI build
            // signed with the SAME key, so new releases install over old ones.
            storeFile = rootProject.file("keystore/dummysurfers-release.keystore")
            storePassword = "dummysurfers"
            keyAlias = "dummysurfers"
            keyPassword = "dummysurfers"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += listOf("META-INF/AL2.0", "META-INF/LGPL2.1")
        }
    }

    sourceSets {
        getByName("main") {
            // LibGDX template layout: game assets live in android/assets
            // (the desktop launcher also reads them from there).
            assets.srcDirs("assets")
            // Extracted LibGDX natives land here at build time.
            jniLibs.srcDir(layout.buildDirectory.dir("gdx-jniLibs"))
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-android:1.12.1")

    gdxNatives("com.badlogicgames.gdx:gdx-platform:1.12.1:natives-armeabi-v7a")
    gdxNatives("com.badlogicgames.gdx:gdx-platform:1.12.1:natives-arm64-v8a")
    gdxNatives("com.badlogicgames.gdx:gdx-platform:1.12.1:natives-x86_64") // emulators
    gdxNatives("com.badlogicgames.gdx:gdx-freetype-platform:1.12.1:natives-armeabi-v7a")
    gdxNatives("com.badlogicgames.gdx:gdx-freetype-platform:1.12.1:natives-arm64-v8a")
    gdxNatives("com.badlogicgames.gdx:gdx-freetype-platform:1.12.1:natives-x86_64") // emulators
}

// Extract every natives jar into build/gdx-jniLibs/<abi>/*.so so AGP packages
// them as real JNI libraries. Mirrors the classic LibGDX copyAndroidNatives
// task, adapted to Kotlin DSL.
val extractGdxNatives = tasks.register("extractGdxNatives") {
    val outDir = layout.buildDirectory.dir("gdx-jniLibs")
    inputs.files(gdxNatives)
    outputs.dir(outDir)
    doLast {
        val out = outDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        gdxNatives.files.forEach { jar ->
            val abi = Regex("""natives-(.+?)\.jar$""").find(jar.name)?.groupValues?.get(1)
                ?: return@forEach
            copy {
                from(zipTree(jar))
                include("*.so")
                into(File(out, abi))
            }
        }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(extractGdxNatives)
}
