# 🔑 CI Unity License Setup — enable APK builds on GitHub Actions

GitHub Actions **can** compile Dummy Surfer into an APK automatically, but Unity requires a
valid license **before** the editor will run in batch mode. Unity licenses can only be
activated by the account owner — this is a one-time, ~5 minute manual step.

> Workflow: [`.github/workflows/unity-android.yml`](../.github/workflows/unity-android.yml)
> As long as no license secret exists, the workflow stays **green** and only prints guidance.
> Once you add the secret, every push to `main` builds → signs → **publishes a Release with the APK**.

---

## Option A — Unity Personal (free) ✅ recommended

1. **Install Unity on your own PC**
   - Unity Hub → Installs → Install **6000.0.32f1** (the exact version in
     `ProjectSettings/ProjectVersion.txt`).
   - Open the Hub's **Preferences → Licenses → Add → Get a free personal license**
     (or inside the editor: *Help → Licensing Management*).
2. **Locate your license file** (created right after activation):

   | OS | Path |
   |---|---|
   | Windows | `C:\ProgramData\Unity\Unity_lic.ulf` |
   | macOS | `/Library/Application Support/Unity/Unity_lic.ulf` |
   | Linux | `~/.local/share/unity3d/Unity/Unity_lic.ulf` |

   (`ProgramData` is hidden — paste the path into Explorer.)
3. **Open the `.ulf` file with a text editor** and copy **everything** (it's a small XML file).
4. **Add it to GitHub**
   - Repo → *Settings → Secrets and variables → Actions*
   - **New repository secret**
   - Name: `UNITY_LICENSE`
   - Secret: paste the full XML contents → *Add secret*
5. **Re-run the workflow**
   - Repo → *Actions → Build Unity Android APK (Dummy Surfer 3D)* → **Run workflow** → `main`.
   - ≈ 20–40 min later: a new **Release** appears with `DummySurfers.apk` attached. 🎉

## Option B — Unity Pro / Plus / Industry

If you have a paid seat, skip the `.ulf` and add these three secrets instead:

| Secret | Value |
|---|---|
| `UNITY_EMAIL` | your Unity account e-mail |
| `UNITY_PASSWORD` | your Unity account password |
| `UNITY_SERIAL` | your license serial (Unity → Settings → Licenses) |

The workflow detects the serial automatically and activates the Pro license in CI.

---

## What the CI pipeline does

1. **License gate** — checks for `UNITY_LICENSE` / `UNITY_SERIAL` secrets.
2. **Debug keystore** — generates a throw-away signing keystore so the APK installs out of the box.
3. **Headless project preparation** — runs
   `DummySurfer.EditorTools.CiEntryPoint.PrepareCiBuild` inside the official
   `unityci/editor:ubuntu-6000.0.32f1-android-3` image:
   folders → URP + quality tiers → Android player settings → data ScriptableObjects →
   network runner prefab → scenes (`Boot`, `MainMenu`, `Game`) + build settings → validation report.
4. **Android build** — `game-ci/unity-builder@v4`, `targetPlatform: Android`, APK signed
   with the CI debug keystore.
5. **Release** — the APK is uploaded as a build artifact **and** attached to a GitHub Release
   (`unity-v1.0.<run_number>`), so `…/releases/latest/download/DummySurfers.apk` always
   points at the newest 3D build.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `No valid Unity license` in the build log | Re-copy the `.ulf` **after** activating exactly version `6000.0.32f1`; old-version ulf files are sometimes rejected. Re-activate, re-copy, re-run. |
| Docker image pull fails for `6000.0.32f1` | Wait a bit (images are built per Unity release) or temporarily pin `unityVersion:` in the workflow to another 6000.0.x patch you have a license file for. |
| `FATAL — no scenes in EditorBuildSettings` | The headless preparation step failed earlier — check its log output for the first red error. |
| Build succeeds but APK is unsigned | Ensure the keystore step ran (it always does in this workflow); check `androidKeystore*` inputs were not modified. |
| Need a production Play-Store signature | Create your own keystore, add `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASS`, `ANDROID_KEYALIAS_NAME`, `ANDROID_KEYALIAS_PASS` as secrets and replace the hardcoded CI keystore values in the workflow. |

## Local build (no CI)

1. Open the project in Unity 6000.0.32f1.
2. Menu **Tools → Dummy Surfer → 1. Setup Everything (One-Click)**.
3. *File → Build Settings → Android → Build* — done, real 3D APK on your device.
