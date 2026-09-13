# 🎮 DUMMY SURFER — Extreme Visual Fidelity 3D Endless Runner

**Android-first · Unity 6 · C# · URP · Two-player online endless runner**

Built to the *Extreme Visual Fidelity & Premium 3D Master Prompt*:
a polished, original 3D mobile endless runner — real-time 3D only, no 2D shortcuts,
no placeholder geometry. Original characters, world, trains and UI. Nothing ripped.

---

## 📲 Getting the APK

### Option 1 — GitHub Actions CI (fully automatic)
The repo ships with a complete CI pipeline (`.github/workflows/unity-android.yml`) that
prepares all scenes/assets headlessly, builds the **Unity 6 URP** Android APK, signs it and
publishes it to **Releases** on every push to `main`.

Unity requires a **one-time license secret** that only you (the account owner) can add —
the exact 5-minute steps are in **[docs/CI_UNITY_LICENSE_SETUP.md](docs/CI_UNITY_LICENSE_SETUP.md)**.
After that, your download link is always:

```
https://github.com/faisukhan01/dummysurfers/releases/latest/download/DummySurfers.apk
```

### Option 2 — Build locally (5 minutes, no CI)
1. Install **Unity 6000.0.32f1** via Unity Hub → open this project.
2. Menu **Tools → Dummy Surfer → 1. Setup Everything (One-Click)** — generates URP settings,
   quality tiers, tuning assets, network prefab, scenes and build settings.
3. *File → Build Settings → Android → Build and Run.* Done — real 3D on your phone.

> 🕹️ A tiny **2D arcade prototype** (Kotlin Canvas — explicitly *not* allowed by the master
> prompt as the game itself) is preserved for reference on the
> [`arcade-2d-backup`](https://github.com/faisukhan01/dummysurfers/tree/arcade-2d-backup) branch.

---

## ✅ Spec coverage (Master Prompt → implementation)

| # | Master Prompt section | Where it lives |
|---|---|---|
| 1 | Technology — Unity 6 / C# / URP / Input System / Netcode / Relay / Auth / pooling | `Packages/manifest.json`, `Core/PoolManager.cs`, `Core/GameServicesInitializer.cs` |
| 2 | Visual north star — authored, intentional look | `Editor/VisualStyles.cs` + `Editor/AssetFactory.cs` (procedural authored materials, never raw primitives) |
| 3 | Original character art (2 heroes, distinct silhouettes) | `Player/RunnerFactory.cs`, `Player/RunnerVisualRig.cs`, `Player/CharacterSelector.cs` |
| 4 | Full animation set (Idle/Run/Jump/Slide/Hit/…) with blending | `Player/PlayerController.cs` + `RunnerVisualRig` procedural squash-and-stretch rig |
| 5 | Camera & game feel — speed FOV, landing punch, restrained shake | `Cam/RunnerCamera.cs` |
| 6 | Three-lane railway world — rails, sleepers, ballast, platforms, stations | `Track/*` (authored chunks, `ChunkPattern`, `PatternGenerator`) |
| 7 | Trains as complete stylized assets (panels, windows, bogies, lights) | `Obstacles/TrainObstacle.cs`, `Obstacles/DecorTrain.cs`, `ObstacleFactory.cs` |
| 8 | Materials & lighting — separated material families, atmospheric depth | `Editor/ProjectConfigurator.ConfigureUrp()`, `VisualStyles` |
| 9 | Coins & power-ups — Magnet, Jetpack, Super Sneakers, Score Multiplier | `Collectibles/*`, `Powerups/PowerupController.cs`, `Powerups/PowerupType.cs` |
| 10 | VFX for pickup / jump / slide / collision / victory / defeat | `Utilities/GameEvents.cs` + pooled particle hooks |
| 11 | Original commercial UI — menu, lobby, HUD, results, settings | `UI/*` (procedural `UICanvasBuilder`, all 11 screens) |
| 12 | Two-player online — room code, Relay, ready-up, synced run, winner rules | `Multiplayer/*` (Netcode + Relay + Auth), `MatchRules.cs` |
| 13 | Deterministic endless generation — shared seed, validated routes, pooling | `Utilities/SeededRandom.cs`, `Track/PatternValidator.cs`, `Core/PoolManager.cs` |
| 14 | Original audio — procedural, zero ripped assets | `Audio/ProceduralAudio.cs`, `AudioManager.cs` |
| 15 | Performance — 60 FPS target, quality tiers, profiling discipline | `Core/PerformanceMonitor.cs`, URP quality tiers, `docs/QA_TEST_PLAN.md` |
| 16 | Milestone-based verified builds | `docs/SPEC_MAPPING.md`, `Editor/ValidationReport.cs` |
| 17 | Hard visual failure guards | `Editor/ValidationReport.cs` (automated pre-flight) |
| 18 | Final acceptance test | `docs/QA_TEST_PLAN.md` + validation report |

## 🗂 Project layout

```
Assets/Scripts/
├── Core/          # bootstrap, state machine, difficulty, pooling, services
├── Data/          # ScriptableObject tuning (GameConfig, ObstacleCatalog, DifficultyCurve…)
├── Player/        # controller, swipe input, runner rig & factory, character select
├── Obstacles/     # barriers, overhead hazards, stumble signs, trains
├── Track/         # chunked railway world, validated pattern generation
├── Collectibles/  # coins, power-up pickups
├── Powerups/      # magnet / jetpack / super sneakers / score multiplier
├── Multiplayer/   # Netcode + Relay + Auth, lobby, match rules, remote sync
├── UI/            # 11 procedural screens (Boot→Results), safe-area, share
├── Audio/         # procedural music & SFX engine
├── Cam/           # dynamic runner camera
├── Utilities/     # seeded random, haptics, events, persistence, service registry
├── Editor/        # one-click setup wizard, scene builder, asset factory, validation, CI entry
└── Tests/         # EditMode tests (match rules, patterns, seeded random)
docs/              # setup, architecture, multiplayer, QA plan, spec mapping, CI license guide
```

## 🚀 Quick start (dev)

1. Unity Hub → open project with **Unity 6000.0.32f1**.
2. **Tools → Dummy Surfer → 1. Setup Everything (One-Click)**.
3. Press **Play** — single-player runs instantly.
4. For online 1v1: follow `docs/MULTIPLAYER_GUIDE.md` (Unity Gaming Services link + Relay).

---

*Original work. No SYBO/Unity proprietary characters, models, textures, audio or branding —
the reference library was used for visual principles only, exactly as the master prompt requires.*
