package com.dummysurfers.desktop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.g2d.PixmapPacker
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.tools.bmfont.BitmapFontWriter
import com.badlogic.gdx.utils.GdxNativesLoader

/**
 * v6.0.0 THE PERMANENT CRASH FIX — bake every game font into BMFont atlases
 * at BUILD time, on the desktop, where freetype is stable.
 *
 * Why: the shipped APK loaded .ttf fonts through gdx-freetype (a NATIVE lib)
 * on the device at every boot. A native SIGSEGV inside freetype glyph
 * rasterization (rare but real on some ARM chips/drivers) is UNCAUGHTABLE by
 * any Java try/catch — it killed the process instantly, on every launch,
 * with no dialog and no log the player could reach. Baking removes freetype
 * from the device entirely: the game now loads plain PNG+TXT bitmap fonts,
 * which cannot crash natively.
 *
 * Run:  ./gradlew :desktop:bakeFonts
 * Output: android/assets/fonts-baked/<name>.fnt + .png (committed to git)
 *
 * The runtime only needs: BitmapFont(Gdx.files.internal("fonts-baked/<name>.fnt")).
 */
object FontBaker {

    /** 2x bake resolution — runtime loads and halves the scale for crisper glyphs. */
    private const val SS = 2

    private const val EXTRAS = "←→↑↓×★•|/+-–—…!?,.%&()'\"«»"
    private val CHARS = FreeTypeFontGenerator.DEFAULT_CHARS + EXTRAS

    private val NAVY_OUTLINE = Color(0x24316bff.toInt())
    private val SHADOW = Color(0x24316b88.toInt())

    private class Spec(
        val name: String,
        val ttf: String,
        val size: Int,
        val border: Int = 0,
        val shadow: Int = 3
    )

    /** Mirrors UiTheme v5.x exactly (px doubled by SS). */
    private val SPECS = listOf(
        Spec("huge52", "fonts/LuckiestGuy-Regular.ttf", 52, border = 5, shadow = 4),
        Spec("large34", "fonts/LuckiestGuy-Regular.ttf", 34, border = 3, shadow = 3),
        Spec("med24", "fonts/LuckiestGuy-Regular.ttf", 24, border = 3, shadow = 3),
        Spec("small18", "fonts/FugazOne-Regular.ttf", 18, border = 2, shadow = 2),
        Spec("tiny14", "fonts/FugazOne-Regular.ttf", 14, border = 0, shadow = 2)
    )

    fun main(args: Array<String>) {
        GdxNativesLoader.load()
        if (Gdx.files == null) {
            Gdx.files = com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files()
        }
        val outDir = Gdx.files.local("../android/assets/fonts-baked")
        outDir.mkdirs()

        for (s in SPECS) {
            bake(s, outDir)
        }
        println("FONTBAKE OK — ${SPECS.size} fonts baked to ${outDir.path()}")
    }

    private fun bake(s: Spec, outDir: com.badlogic.gdx.files.FileHandle) {
        val gen = FreeTypeFontGenerator(Gdx.files.internal(s.ttf))
        val packer = PixmapPacker(1024, 1024, Pixmap.Format.RGBA8888, 2, false,
            PixmapPacker.SkylineStrategy())
        val param = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
            size = s.size * SS
            color = Color.WHITE
            borderWidth = s.border * SS.toFloat()
            borderColor = NAVY_OUTLINE.cpy()
            shadowOffsetX = s.shadow * SS
            shadowOffsetY = s.shadow * SS
            shadowColor = SHADOW.cpy()
            spaceX = 0 // match v5.x freetype params exactly (default tracking)
            characters = CHARS
            minFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear
            magFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear
            this.packer = packer
            incremental = false
        }
        val data = gen.generateData(param)

        // pull the packed pages out of the packer (pure CPU pixmaps)
        val pixmaps = ArrayList<Pixmap>()
        for (page in packer.pages) {
            pixmaps.add(page.getPixmap())
        }

        val info = BitmapFontWriter.FontInfo("", s.size)
        info.unicode = true
        info.smooth = true
        info.aa = 4
        info.padding = BitmapFontWriter.Padding(2, 2, 2, 2)
        val spacing = BitmapFontWriter.Spacing()
        spacing.horizontal = 1; spacing.vertical = 1
        info.spacing = spacing
        val fnt = outDir.child("${s.name}.fnt")
        // writeFont(data, pixmaps[], fnt, info) also writes the PNG page(s) next
        // to the .fnt automatically
        BitmapFontWriter.writeFont(data, pixmaps.toTypedArray(), fnt, info)

        // textual validation (BitmapFont load needs GL — visual check happens in
        // the Xvfb QA harness): PNG page(s) present + chars counted. Glyphs at
        // 2x may span multiple 1024² pages (huge52_0.png, huge52_1.png, …).
        val pages = outDir.list { f -> f.name.startsWith("${s.name}") && f.name.endsWith(".png") }
        val totalBytes = pages.sumOf { it.length() }
        val fntText = fnt.readString()
        val chars = fntText.lines().count { it.trimStart().startsWith("char ") }
        val lineHeight = fntText.lines().firstOrNull { it.startsWith("common ") }?.trim()
        require(pages.isNotEmpty() && totalBytes > 1000L) { "PNG page(s) missing/too small for ${s.name}" }
        require(chars > 90) { "too few glyphs baked ($chars)" }
        println("  ${s.name}: ${pages.size} page(s), ${totalBytes / 1024f}KB, chars=$chars")
        println("    $lineHeight")
        for (pm in pixmaps) pm.dispose()
        gen.dispose()
    }
}

fun main(args: Array<String>) = FontBaker.main(args)
