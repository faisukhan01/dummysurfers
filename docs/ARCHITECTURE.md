# Architecture — Dummy Surfer

## 1. Big picture

```
┌────────────────────────────── Boot / Menu scenes ─────────────────────────────┐
│ GameBootstrap (composition root, DontDestroyOnLoad)                           │
│   ├─ GameStateManager   Boot→Menu→Lobby→Countdown→Running→Paused→Results→Disc │
│   ├─ RunManager         local distance/coins/bests (offline + local half)     │
│   ├─ DifficultyManager  speed(dist) + contentDifficulty(chunkIndex)           │
│   ├─ PoolManager        pooled-object parents                                 │
│   ├─ AudioManager       music/SFX buses + procedural clips                    │
│   ├─ UIManager          one canvas, 11 code-built screens, state routing      │
│   ├─ PerformanceMonitor 60 FPS target, tiers, auto-degrade                    │
│   └─ MultiplayerManager room flow, timeouts, friendly errors                 │
└───────────────────────────────────────────────────────────────────────────────┘
                 │ MENU: LobbyState (scene NetworkObject, ready flags)
                 ▼ GAME scene (loaded synchronized by NGO scene manager)
┌───────────────────────────────────────────────────────────────────────────────┐
│ RunSceneController  atmosphere · camera · input wiring · countdown · results  │
│ TrackManager        chunk window [ahead/behind], recycle behind BOTH runners  │
│   └─ TrackChunk     static geometry + seeded skyline + PatternGenerator output│
│        ├─ ObstacleManager  pooled Train/Barrier/Overhead/Sign                 │
│        └─ CollectibleManager pooled Coins / Powerups                          │
│ PlayerController    pure local sim: lanes/jump/slide/stumble/death            │
│ NetworkPlayerSync   owner → state NV (~16 B) ; remote → interpolation         │
│ MatchStateManager   host-authoritative seed/phase/scores/death-window/outcome │
└───────────────────────────────────────────────────────────────────────────────┘
```

No giant GameManager: systems are small, talk through the static **GameEvents** hub
and a tiny **ServiceRegistry**, and every singleton self-heals via
`PersistentManager<T>.Ensure()` so any scene can be entered directly.

## 2. Deterministic track pipeline (the multiplayer trick)

```
host: seed = SplitMix64(System.Random)          ── NetworkVariable<ulong> ──►  client
chunk i content = PatternGenerator.Generate(seed, i)
   rng      = SeededRandom(ChunkSeed(seed, i))        // pure, per-index
   params   = DifficultyCurve.Difficulty01(i)         // pure, per-index  (NOT player distance!)
   grid     = weighted archetypes (train/barrier/overhead/sign)
   validate = PatternValidator.HasSurvivablePath  → deterministic repair loop
   coins/powerups = from same rng stream
```

Both clients then run **identical worlds** while only movement is networked.
`PatternValidator` is BFS over rows×lanes: a lane is enterable if it isn't Train-blocked,
moves are ±1 lane/row. Impossible grids are deterministically repaired (clear one train
column) — provable, unit-tested, and never dependent on `UnityEngine.Random`.

## 3. State machines

**GameState** (app): `Boot → Menu → (Lobby) → Countdown → Running ⇄ Paused → Results`
plus `Disconnected`. Offline pause sets `Time.timeScale = 0`; online pause is
overlay-only (competitive fairness, communicated in-UI).

**MatchPhase** (networked): `None → Lobby → Countdown → Running → Results`.
Server ticks the countdown and flips phases authoritatively; clients render from
NetworkVariables (no clock-skew issues).

**Player sim**: `Idle → Running → (Jump|Slide|Stumble)* → Dead | Victory`, with a
0.22 s input buffer so swipes during animation feel immediate (spec §4.3).

## 4. Death & result resolution (host authority)

```
death report (owner) ──► host: mark side dead w/ death-time stats
  ├─ other already dead  → Resolve(both-dead): distance → coins → DRAW   (unit-tested)
  ├─ first death         → start 5 s window (GameConfig.deathComparisonWindowSeconds)
  │     └─ window expires with survivor → survivor wins
  └─ disconnect mid-run  → aborted side marked dead → survivor wins
plausibility clamp: distance ≤ (elapsed+countdown+10) × maxSpeed × 1.25 + slack
```

`MatchRules.Resolve` is a pure static — locked by `MatchRulesTests` against the §3.3 table.

## 5. Performance engineering

| Technique | Where |
|---|---|
| Object pooling (chunks, obstacles, coins, powerups, decor trains) | `ObjectPool<T>` + managers |
| Full pooled reset before reuse | `TrackChunk.Clear()`, `OnSpawned` overrides |
| Event-driven UI (no per-frame text writes) | `GameEvents` + HUD delta checks |
| Single shared canvas + code-built screens | `UIManager` |
| Quality tiers = renderScale + shadow tier | `PerformanceMonitor` |
| Speed-based FOV, minimal shake | `RunnerCamera` |
| Unlit/simple-lit primitives + fog for depth (no textures) | `VisualStyles` |
| Procedural audio, generated once, cached | `ProceduralAudio` |
| Kinematic player + trigger colliders only | `PlayerController` / `ObstacleBase` |

## 6. Content = code

Characters, track dressing (skyline, lamps, gantries, platforms), obstacles, pickups,
UI and audio are **all built procedurally** at runtime (or wizard time for the prefab).
Benefits: text-only repo (clean diffs, no LFS), zero missing-asset failures, instantly
auditable originality (IP boundary). `RunnerVisualRig` does the animation
(run-cycle sine swinging, jump tuck, slide pitch, stumble flail, death fall, victory bounce).

## 7. Extension points

- **New chunk archetypes** → add kind to `ObstacleKind`, weights in `ObstacleCatalog`,
  visuals in `ObstacleFactory`, reaction in `ChunkPattern.ReactionFor`.
- **New power-ups** → `PowerupType` + `PowerupController.Activate` + HUD badge.
- **Sessions SDK** → swap `RelayRoomService` internals (see MULTIPLAYER_GUIDE §6).
- **Deep-link invites** → extend `ShareService` behind the same call sites.
- **More characters** → another `CharacterStats` asset (rig + colors are data).
