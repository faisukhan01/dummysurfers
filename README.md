# DUMMY SURFER

**Android-first, two-player online 3D endless runner.** Free-first architecture.
Subway-Surfers-*genre*-inspired gameplay feel — with 100% original code, characters,
visuals (procedural primitives), audio (generated at runtime) and UI.

> Working title per the university brief. Not affiliated with, endorsed by, or derived
> from SYBO Games' Subway Surfers. No proprietary source code, assets, characters,
> audio, branding or typography from any commercial title is included. (See `LICENSE`.)

---

## ✨ Feature Highlights (vertical slice, per the master spec)

| Area | What's implemented |
|---|---|
| **Core loop** | Launch → menu → play → countdown → run → results. Auto-forward runner, 3 lanes, swipe left/right = lane, up = jump, down = slide. |
| **Speed & difficulty** | Distance-driven speed ramp, chunk-content difficulty curve, all data-driven via ScriptableObjects. |
| **Track** | Deterministic chunk system (60 m chunks), pooled & recycled behind both players, procedural rail-yard skyline, lamps, gantries, platforms, decorative moving trains. |
| **Obstacles** | Trains (lane-switch), barriers (jump), overhead gantries (slide), warning signs (stumble). Every pattern is **validated to guarantee a survivable path** before activation. |
| **Collectibles** | Pooled coins (+ arcs over barriers), power-ups: Magnet, Shield (absorbs one lethal hit), 2× coins. |
| **Two-player online** | Create Room → 6-char join code → copy / native Android share sheet → join → lobby with READY cards → host-authoritative synchronized countdown → both run on the **same deterministic track** → survivor-wins / both-dead compare (distance → coins → draw) → results → rematch. |
| **Networking** | Unity **Relay** (no dedicated server, no custom backend) + **Netcode for GameObjects**, Unity **Authentication** (anonymous), timeouts/retries/friendly errors on every network op, graceful disconnect screens. |
| **UI** | Premium flat mobile-first UI built 100% from code: Boot, Main Menu, Multiplayer, Lobby, Countdown, HUD (distance/coins/pause/opponent chip/power-up badges), Pause, Results, Settings, Character select, Disconnect, toasts. Safe-area aware (notches). |
| **Audio** | Fully **procedural** SFX + two chiptune music loops generated at runtime (zero audio assets) — drop your own clips into `AudioBank` to override any sound. |
| **Performance** | Target 60 FPS, object pooling everywhere (no Instantiate/Destroy in gameplay), event-driven UI (no per-frame text rebuilds), low/mid/high quality tiers with resolution scaling + auto-degrade guard, linear color space, URP. |
| **Engineering** | Namespaced assembly, small single-responsibility managers, static event hub (no giant GameManager), ScriptableObject tuning, generic pools, seeded PRNG (SplitMix64), EditMode tests for RNG / pattern validity / result rules. |
| **Tooling** | **One-click editor wizard**: `Tools ▸ Dummy Surfer ▸ 1. Setup Everything` builds folders, URP asset, Android settings (IL2CPP, ARM64, portrait, internet permission), tuning assets, the network player prefab and all 3 scenes. |

---

## 🚀 Quick Start (5 minutes)

**Requirements:** Unity **6000.x LTS** (Unity 6) with Android Build Support modules.

1. **Clone / open** this folder as a Unity project (any Unity 6 LTS version works —
   the package manifest will resolve; accept version changes if prompted).
2. Menu: **`Tools ▸ Dummy Surfer ▸ 1. Setup Everything (One-Click)`**
   — creates render pipeline, Android settings, tuning assets, the `NetworkRunner`
   prefab, and the three scenes (`Boot`, `MainMenu`, `Game`) + build settings.
   - If Unity asks to **restart for the Input System**, let it.
3. Press **Play** — single-player runs instantly. No services, no accounts.
4. **Controls (editor):** `A/←` `D/→` lanes · `W/↑/Space` jump · `S/↓` slide · `Esc/P` pause.
   **On device:** swipe.

### Build the APK
`File ▸ Build Settings ▸ Android ▸ Switch Platform ▸ Build` — full walkthrough with
IL2CPP/ARM64/keystore notes: [`docs/ANDROID_BUILD_GUIDE.md`](docs/ANDROID_BUILD_GUIDE.md).

### Enable the two-player online mode
One-time Unity Gaming Services setup (free tier): link the project, enable
**Authentication** + **Relay**. Step-by-step: [`docs/MULTIPLAYER_GUIDE.md`](docs/MULTIPLAYER_GUIDE.md).

> No APKs are committed to this repo (policy: `.gitignore` blocks `*.apk`/`*.aab`).
> Build locally and share the APK directly with testers.

---

## 📁 Repository Layout

```
Assets/
  Scripts/
    Core/         GameBootstrap, GameState, Run, Difficulty, Pools, Perf, Services, ConnectionGuard
    Player/       PlayerController, SwipeInputReader, procedural rig, runner factory, characters
    Cam/          RunnerCamera (follow + speed FOV + shake)
    Track/        TrackManager, TrackChunk, PatternGenerator + Validator, RunSceneController
    Obstacles/    Base, Factory, Manager, Train/Barrier/Overhead/Sign, DecorTrain
    Collectibles/ Coin, PowerupPickup, CollectibleManager
    Powerups/     Types + PowerupController (Magnet / Shield / 2x)
    UI/           UIManager, canvas builder, 11 screens, share service, menu backdrop
    Audio/        AudioManager + procedural SFX/music generator
    Multiplayer/  MultiplayerManager, RelayRoomService, LobbyState, MatchStateManager, NetworkPlayerSync
    Services/     (Unity Services init lives in Core/GameServicesInitializer)
    Data/         GameConfig, DifficultyCurve, CharacterStats, ObstacleCatalog, AudioBank (SOs)
    Utilities/    SeededRandom, GameEvents, pools, registry, styles, haptics, safe area
    Editor/       One-click setup wizard, scene/prefab/asset builders, validation report
  Tests/EditMode/ SeededRandom, PatternGenerator/Validator, MatchRules tests
Packages/manifest.json   Netcode, Relay, Authentication, Transport, URP, Input System
ProjectSettings/         Editor version
docs/                    Setup, multiplayer, Android build, QA plan, architecture, spec mapping
```

## 🧠 Design Decisions (why it's built this way)

- **Deterministic generation is pure.** Chunk content = `f(sharedSeed, chunkIndex)` only —
  never player distance, never `UnityEngine.Random`. Both clients simulate identical worlds;
  only movement state is networked (~16 B/tick). The validator BFS proves every pattern keeps
  a survivable path — impossible sequences are repaired deterministically.
- **Host authority for results.** Deaths are reported per-owner, the host validates plausible
  stats, enforces the 5 s death-comparison window and decides WIN/LOSS/DRAW (spec §3.3 rules
  are unit-tested). Late packets can never resurrect a dead player.
- **Pool-first.** Chunks, obstacles, coins, power-ups and decor trains are all pooled and fully
  reset on reuse — restart is near-instant, zero GC churn mid-run.
- **Code-built content.** Scenes are thin; UI, characters, track dressing and audio are all
  built procedurally — the repo stays text-only, diff-able, and needs zero binary assets.
- **Free-first.** Unity Personal + UGS free quotas + Relay + no backend. The sessions SDK
  migration path and quota notes are documented.

Deep dive: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) ·
spec-section→file map: [`docs/SPEC_MAPPING.md`](docs/SPEC_MAPPING.md).

## 🧪 Tests

`Window ▸ General ▸ Test Runner ▸ EditMode` — covers seeded RNG determinism,
pattern survivability (150 seeds × 10 chunk tiers), generator determinism, and the full
result-rules table from the spec.

## 🗺 Roadmap beyond the slice

Deep links for invites (interface already isolated in `ShareService`), cosmetic trails,
weekly leaderboards (UGS), sessions-SDK migration, more chunk archetypes.

## 📄 License

MIT — see [`LICENSE`](LICENSE). Original university project; genre-inspired only.
