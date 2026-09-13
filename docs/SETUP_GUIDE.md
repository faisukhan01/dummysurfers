# Setup Guide — Dummy Surfer

From zero to playing. Total time: ~5–10 minutes.

## 1. Requirements

| Tool | Version |
|---|---|
| Unity Hub | latest |
| Unity Editor | **Unity 6 LTS (6000.x)** — any patch |
| Modules | Android Build Support (+ OpenJDK + Android SDK & NDK Tools) |

The repo ships `Packages/manifest.json`, so all packages (Netcode for GameObjects,
Relay, Authentication, Transport, URP, Input System, Test Framework) install
automatically on first open. If Unity offers to update package versions, accept.

## 2. Open & one-click setup

1. Open the project folder with Unity 6.
2. Wait for the compile + package resolve.
3. Menu **`Tools ▸ Dummy Surfer ▸ 1. Setup Everything (One-Click)`**.

The wizard does:
- Creates folders (`Scenes/`, `Prefabs/`, `ScriptableObjects/Resources/…`).
- Creates + assigns a **mobile-tuned URP asset** (MSAA 2×, HDR off, cheap shadows).
  (If URP APIs ever change, it degrades to the Built-in pipeline with a warning — gameplay is unaffected.)
- Configures **Android** player settings: IL2CPP, ARM64-only, min SDK 24, portrait,
  internet permission, package id `com.dummysurfer.university`.
- Sets **Active Input Handling → Input System Package (new)** (restart if prompted).
- Creates tuning **ScriptableObjects** (`GameConfig`, `DifficultyCurve`, `ObstacleCatalog`,
  `AudioBank`, `Juno`, `Kai`) under `Assets/ScriptableObjects/Resources/`.
- Builds the **`NetworkRunner`** prefab (Netcode player prefab) into `Assets/Resources/`.
- Builds the three scenes — `Boot`, `MainMenu`, `Game` — and registers Build Settings.
- Runs the **validation report** (also available: `Tools ▸ Dummy Surfer ▸ 5. Validate Project`).

## 3. Play immediately

Press **Play** (from any scene — the bootstrap self-heals). Single-player works fully
offline: lanes, jump, slide, stumble, trains, coins, power-ups, best scores.

**Editor controls:** `A/←`, `D/→` lane · `W/↑/Space` jump · `S/↓` slide · `Esc/P` pause.
**Device controls:** swipes (chained swipes in one touch are supported).

## 4. Two-device testing (after UGS setup)

Follow `MULTIPLAYER_GUIDE.md` to link Unity Gaming Services once. Then:
1. Build & install the APK on both phones (see `ANDROID_BUILD_GUIDE.md`).
2. Phone A: Multiplayer → Create Room → **Share Invite**.
3. Phone B: Multiplayer → enter code → Join Room.
4. Both: **READY UP** → synchronized countdown → same track, live rival chip.
5. Test disconnects: kill the app on one phone — the other gets a friendly result/screen.

You can also test multiplayer with **two editor instances** (ParrelSync-style duplicates
or a build + editor) once services are linked.

## 5. Manual steps if you skip the wizard

| Wizard step | Manual equivalent |
|---|---|
| URP asset | Create a URP asset via right-click `Create ▸ Rendering ▸ URP Asset`, assign in Project Settings ▸ Graphics + Quality |
| Input handling | Project Settings ▸ Player ▸ Other Settings ▸ Active Input Handling ▸ Input System Package (new) |
| Scenes | Create `Boot` (add `GameBootstrap`), `MainMenu` (add `MenuBackdrop`, `LobbyState` + `NetworkObject`), `Game` (add camera+`RunnerCamera`, `RunSceneController`, `MatchState` + `NetworkObject`), then add all 3 to Build Settings |
| Network prefab | Run `Tools ▸ Dummy Surfer ▸ 3. Rebuild Prefabs + Data Assets` |

## 6. Troubleshooting

| Symptom | Fix |
|---|---|
| `InvalidOperationException: NetworkRunner prefab is missing` | Run wizard step **3** (creates `Assets/Resources/NetworkRunner.prefab`). |
| Online buttons fail with services errors | Project isn't linked to UGS — see `MULTIPLAYER_GUIDE.md`; single-player still works. |
| No input on device | Confirm Active Input Handling = **Input System Package (new)**. |
| UI too small/large | UI scales from 1080×1920 reference; adjust `UIManager` canvas scaler if needed. |
| Join code "doesn't exist" | Codes expire with the Relay allocation; the host must stay in the lobby. |
