# Android Build Guide — Dummy Surfer

## 0. Prerequisites

- Unity 6 LTS with **Android Build Support**, **OpenJDK**, **Android SDK & NDK Tools**
  (Unity Hub ▸ Installs ▸ Add modules).
- The one-click wizard has been run (`Tools ▸ Dummy Surfer ▸ 1. Setup Everything`) —
  it already sets IL2CPP, ARM64, portrait orientation, min SDK 24 and internet permission.

## 1. Switch platform

`File ▸ Build Settings` → select **Android** → **Switch Platform** (first time only).

## 2. Verify the wizard's settings (optional sanity pass)

`Edit ▸ Project Settings`:

| Section | Setting | Value |
|---|---|---|
| Player ▸ Other | Scripting Backend | **IL2CPP** |
| Player ▸ Other | Target Architectures | **ARM64** only |
| Player ▸ Other | Graphics APIs | OpenGLES3 / Vulkan (default) |
| Player ▸ Other | Color Space | Linear |
| Player ▸ Other | Active Input Handling | **Input System Package (new)** |
| Player ▸ Other | Min API Level | 24 (Android 7.0) |
| Player ▸ Other | Internet Access | Require |
| Player ▸ Resolution | Default Orientation | **Portrait** |
| Identity | Package Name | `com.dummysurfer.university` |

## 3. Signing

**Local testing:** leave *Signing Debug* as-is (debug keystore auto-generates).

**Sharing beyond your own devices / later store upload:** create a keystore —
`Player ▸ Publishing Settings ▸ Keystore Manager ▸ Create New`. Keep the `.keystore`
file + passwords **out of git** (they are already ignored by `.gitignore` patterns for
build artifacts — store the keystore somewhere safe and private).

## 4. Build the APK

1. Build Settings → scenes list should show **Boot / MainMenu / Game** (wizard adds them).
2. **Build** → choose e.g. `Builds/DummySurfer-1.0.apk` (folder is git-ignored).
3. First IL2CPP build takes a few minutes; subsequent builds are much faster.

Optional: **Build And Run** with a phone connected (enable Developer Options + USB
Debugging on the phone) for the fastest iterate loop. Android Logcat package
(`com.unity.mobile.android-logcat`, already in the manifest) adds a device console
window: `Window ▸ Analysis ▸ Android Logcat`.

## 5. Install on a friend's phone

Copy the APK over (Drive/WhatsApp/USB) → open it → allow "install unknown apps"
when prompted. Both players must be on the **same app version** (Netcode protocol).

## 6. Performance checklist (spec §10 PERFORMANCE)

- Target: stable 60 FPS (`Application.targetFrameRate = 60`, set at boot).
- Quality tiers: Settings ▸ LOW/MID/HIGH = render scale 0.66 / 0.85 / 1.0 + shadow tier.
- Auto-guard: <45 FPS for 4 s drops one tier automatically (once per session).
- Profile with `Window ▸ Analysis ▸ Profiler` on-device before calling it optimized;
  check the screen FPS counter (Settings ▸ Show FPS Counter) during runs.

## 7. Troubleshooting builds

| Problem | Fix |
|---|---|
| `Gradle build failed` | Ensure JDK/SDK/NDK come from the Unity Hub modules (Preferences ▸ External Tools ▸ all "Installed with Unity"). |
| Black screen on launch | Confirm Boot scene is first in Build Settings. |
| Input dead on device | Active Input Handling must be **Input System Package (new)**; reinstall after changing it. |
| Relay errors only on device | Internet permission missing (wizard sets it) or the UGS project isn't linked on this machine. |
| APK too large | ARM64-only is already set; next lever is texture compression (ASTC) in Build Settings. |
