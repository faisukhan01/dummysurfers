# DUMMY SURFERS by FSK 🛹🚂

A premium, production-quality **Android endless runner** in the spirit of Subway Surfers —
built with **Kotlin + LibGDX** per the full 35-section specification. 100% original code,
procedurally generated art & audio (zero external assets).

![Stack](https://img.shields.io/badge/Kotlin-2.0-purple) ![Engine](https://img.shields.io/badge/LibGDX-1.12.1-orange) ![API](https://img.shields.io/badge/Android-7.0%2B%20(API%2024%2B)-green)

---

## 📲 Download & play

Every push to `main` triggers GitHub Actions, which builds a **signed release APK** and
publishes it to the [Releases page](../../releases).

**Direct download (always newest):**

```
https://github.com/faisukhan01/dummysurfers/releases/latest/download/DummySurfers.apk
```

1. Open the link on your phone (or scan the QR code on the project landing page).
2. Allow "install unknown apps" for your browser.
3. Install **DummySurfers.apk** → play.

The APK is signed with the committed project keystore, so every new release installs
straight over the previous one (no uninstall needed).

---

## ✅ Feature checklist (spec sections 1–35)

| System | Status |
|---|---|
| Pseudo-3D perspective projection (`scale(z) = f/(f+z)`), z-sorted rendering | ✅ |
| 7-layer parallax (sky, sun, clouds, 2 skylines, ground, track, fog) | ✅ |
| 3-lane movement, 0.15s ease-out lane switching with lean | ✅ |
| Parabolic jump (0.6s) + landing squash + jump buffering | ✅ |
| Slide (0.5s, 40% hitbox) + swipe-down air slam | ✅ |
| Swipe detector: 15px dead zone, 300ms window, dominant axis | ✅ |
| Procedural endless world (open/urban/station/bridge/tunnel/industrial segments) | ✅ |
| Pattern spawner with IRON RULES (always ≥1 safe lane, reaction gaps, no same-action spam) | ✅ |
| Trains: parked / same-direction / approaching (horn + headlights), multi-car, multi-lane | ✅ |
| Obstacles: low barriers (jump), high barriers + gates (slide), blockades, full fences | ✅ |
| Coins: 10-frame spin, glow, bob, arcs/lines/zigzags guiding the safe path, rising-pitch ding | ✅ |
| Power-ups: Magnet / Score ×2 / Shield / Boost / Super Jump (+3s per upgrade level) | ✅ |
| Chaser (security guard) pressure system | ✅ |
| Near-miss scoring (+25, shake, floating text) | ✅ |
| Difficulty curve `base + (max−base)(1−e^(−d·k))` with 5 phases | ✅ |
| Score, multiplier milestones (×2 @1000m, ×4 @2500m), coin streaks | ✅ |
| Particles: coin sparkles, bursts, confetti, dust, boost streaks, shield break | ✅ |
| Procedural PCM audio engine: music sequencer (kick/hat/bass/lead, 132 BPM) + all SFX | ✅ |
| Haptics via Android Vibrator API | ✅ |
| Menu / HUD / Pause / Game Over / Shop / Characters / Missions / Settings / Tutorial | ✅ |
| Shop: 4 characters, 5 power-up upgrades (3 levels), 4 trail cosmetics — all functional | ✅ |
| Missions: 3 active, auto-generated, claimable rewards | ✅ |
| Save system: Preferences + JSON (offline, versioned, deep-merged) | ✅ |
| Interactive first-run tutorial (persisted) | ✅ |
| Virtual 720×1280 letterboxed stage — fits every aspect ratio | ✅ |

---

## Project structure

```
dummy-surfers/
├── core/      # ALL game code (pure Kotlin + LibGDX, platform-independent)
│   └── src/main/kotlin/com/dummysurfers/core/
│       ├── config/GameConfig.kt        # every tunable in one place
│       ├── state/                      # GameState, SaveManager (JSON), missions
│       ├── camera/Projection.kt        # pseudo-3D perspective math
│       ├── input/SwipeDetector.kt      # subway-surfers-grade swipes
│       ├── entities/Entities.kt        # Player, Train, Obstacle, Coin, PowerUp, Chaser
│       ├── systems/                    # Spawner (iron rules), Difficulty
│       ├── world/WorldGenerator.kt     # endless segments + decorations
│       ├── gfx/TextureGen.kt           # ALL textures generated via Pixmap
│       ├── audio/AudioManager.kt       # PCM synth + music sequencer (no files)
│       ├── rendering/                  # World / Deco / Entity renderers
│       ├── particles/Particles.kt
│       ├── ui/                         # UiTheme (freetype fonts) + all screens
│       └── DummySurfersGame.kt         # orchestrator / loop
├── android/   # Android launcher + manifest + icons + assets (fonts)
├── desktop/   # LWJGL3 desktop launcher for fast iteration
├── keystore/  # release signing keystore (stable key → updates install over old builds)
└── .github/workflows/android.yml  # CI: debug APK + signed release APK + AAB + GitHub Release
```

## Run / build

```bash
# Desktop (fastest way to try it)
./gradlew desktop:run

# Android debug APK  → android/build/outputs/apk/debug/android-debug.apk
./gradlew :android:assembleDebug

# Play Store bundle  → android/build/outputs/bundle/release/
./gradlew :android:bundleRelease
```

### GitHub Actions
Every push builds and publishes:
- **Release** `v*` → `DummySurfers.apk` (signed release) + `DummySurfers-debug.apk` + `DummySurfers-release.aab`
- Workflow artifacts → `dummy-surfers-build` (same three files)
- Signing uses the committed `keystore/dummysurfers-release.keystore` (hobby project; swap in
  your own keystore + secrets for a Play release if you prefer).

## Credits
- Fonts: [Luckiest Guy](https://fonts.google.com/specimen/Luckiest+Guy) + [Fugaz One](https://fonts.google.com/specimen/Fugaz+One) + [Fredoka](https://fonts.google.com/specimen/Fredoka) + [Baloo 2](https://fonts.google.com/specimen/Baloo+2) (OFL/Apache)
- Everything else: 100% original procedural code — **Dummy Surfers by FSK**
