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
    implementation("com.badlogicgames.gdx:gdx-freetype-platform:1.12.1:natives-desktop")
}

application {
    mainClass.set("com.dummysurfers.desktop.DesktopLauncherKt")
}

// Desktop launcher reads assets from ../android/assets
sourceSets.getByName("main") {
    resources.setSrcDirs(listOf("src/main/resources", "$rootDir/android/assets"))
}
