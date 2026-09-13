# 🛹 Dummy Surfers

A fast & fun **endless runner for Android** — 100% Kotlin, built automatically by GitHub Actions.

Crash-test dummy + endless road + sunset city = chaos. Dodge barriers, slide under bars, dodge oncoming trains and collect as many coins as you can!

---

## 📲 Download the APK (latest build)

**Direct download link:**

> ### https://github.com/faisukhan01/dummysurfers/releases/latest/download/DummySurfers-debug.apk

**Releases page:** https://github.com/faisukhan01/dummysurfers/releases

Every push to `main` is compiled automatically by **GitHub Actions** — a fresh installable APK is attached to a new release each time. This link always points at the newest build.

> ℹ️ The APK is debug-signed (standard for CI builds). When installing, Android may ask you to allow *"Install unknown apps"* — that's normal for apps outside the Play Store.

## 🎮 How to play

| Gesture | Action |
| --- | --- |
| Swipe ⬅️ / ➡️ | Change lane |
| Swipe ⬆️ | Jump over barriers |
| Swipe ⬇️ | Slide under bars (or fast-fall while jumping) |
| Tap ⏸ / 🔊 | Pause / mute |

- 3 lanes, endless road, speed keeps ramping up
- Red/white **barriers** → jump them
- Overhead **bars** → slide under them
- **Dumpsters & trains** → switch lanes!
- Coins = +10 pts, passing obstacles = +5 pts
- High score is saved on your device

## 🛠 Technology

**Native Android version (`/app`)**

- Kotlin 1.9 + Android SDK 34 (minSdk 24 → Android 7.0+)
- Custom 2D engine: `SurfaceView` + dedicated game-loop thread
- All graphics drawn procedurally with `Canvas` — **zero image assets**, tiny APK
- Swipe gesture controls, particle FX, parallax sunset city, screen shake
- `ToneGenerator` retro SFX (mutable), high-score persistence via `SharedPreferences`
- Gradle 8.7 + AGP 8.4.1, CI: `android.yml` workflow → Release APK on every push

**Unity 6 multiplayer edition (`/Assets`, `/Packages`, `/ProjectSettings`)**

The repository also keeps the original **Unity 6 (6000.0.32f1)** two-player online endless-runner source (lobby, relay multiplayer, procedural audio, editor scene-builder). Unity builds require the Unity Editor + a Unity license and therefore can't be compiled by GitHub Actions — open the project in Unity 6 and use `Window → Dummy Surfer Setup Wizard` to generate scenes, then build normally. Docs: [`docs/ANDROID_BUILD_GUIDE.md`](docs/ANDROID_BUILD_GUIDE.md).

## 🏗 Build the Android version yourself

```bash
git clone https://github.com/faisukhan01/dummysurfers.git
cd dummysurfers
gradle assembleDebug          # Gradle 8.7+ (or ./gradlew assembleDebug)
# APK → app/build/outputs/apk/debug/app-debug.apk
```

Or in **Android Studio**: *File → Open* → select the repo folder → *Run*.

---

Made with 💛 by dummy dummies who surf.
