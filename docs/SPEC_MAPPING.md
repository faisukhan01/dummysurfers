# Spec → Implementation Map

Every numbered requirement from the master brief ("Prompt.pdf") mapped to code.

| Spec section | Requirement | Implementation |
|---|---|---|
| §1 Research / architecture | Unity + UGS, no custom backend v1 | Relay + NGO + Auth only; `docs/MULTIPLAYER_GUIDE.md` records the decisions + case-study lessons applied (chunked procedural levels, batching, minimal UI redraws) |
| §2 Stack | Unity 6 LTS, C#, URP, Input System, NGO, Multiplayer Services, Relay, Auth, SOs, pooling, Android, no backend | `Packages/manifest.json`, asmdefs, `GameServicesInitializer`, `RelayRoomService`, all Data/SOs |
| §3.1 Single loop | menu→countdown→run, lanes/jump/slide, coins/powerups, speed ramp, lethal death, results, pooled instant restart | `MainMenuScreen`, `RunSceneController`, `PlayerController`, `DifficultyManager`, `ResultsScreen`, `ObjectPool` |
| §3.2 Two-player loop | room code, share sheet, join, lobby, ready, shared seed, simultaneous start, see rival, independent runs, survivor wins, window compare | `MultiplayerManager`, `ShareService`, `LobbyState`, `MatchStateManager`, `NetworkPlayerSync` |
| §3.3 Result rules | A/B/both-dead/compare/draw table | `MatchRules.Resolve` (+ `MatchRulesTests`) |
| §4.1 Visual identity | original rail-city world, cohesive stylized direction, 2 original runners, controlled fog, restrained particles | `VisualStyles` palette, `TrackChunk` dressing, `CityDecorator`-style seeded skyline inside `TrackChunk.RandomizeSkyline`, `CharacterStats`, fog config in `GameConfig` |
| §4.2 UI | premium mobile UI; menu/multiplayer/HUD/results inventories; no SS imitation | `UICanvasBuilder` + 11 screens; original flat palette & typography |
| §4.3 Feel | immediate lanes, responsive swipes, clean jump/slide anticipation, energetic controlled camera, satisfying coins | input buffering + exponential lane approach, `RunnerVisualRig` poses, `RunnerCamera` speed FOV + micro-shake, coin pitch variation in `AudioManager` |
| §5.1 Folders | specified tree | identical tree under `Assets/Scripts/...` |
| §5.2 Managers | full manager list | one file per manager, same responsibilities (see ARCHITECTURE §1); NetworkPlayerController == `NetworkPlayerSync` |
| §5.3 Data-driven | SOs for stats/obstacles/powerups/chunks/curves/cosmetics | `GameConfig`, `DifficultyCurve`, `CharacterStats`, `ObstacleCatalog`, `AudioBank` |
| §6.1 Chunks | 20–40 reusable chunks, connectors, ahead/behind windows, shared seed | `TrackManager`, `TrackChunk` (entry safe rows + exit margin = connectors) |
| §6.2 Spawn rules | never impossible, always a valid path, validate before activation, gradual difficulty, no per-frame RNG | `PatternGenerator` + `PatternValidator` (+ tests), `DifficultyCurve` |
| §6.3 Pooling | pool everything, no Instantiate/Destroy in play, full reset | `ObjectPool<T>`, `PoolManager`, `Clear()`/`OnSpawned()` |
| §7 Multiplayer | host+client via Relay+NGO; sync vs keep-local lists; authority | `NetworkPlayerSync` (state), `MatchStateManager` (authority), decor/particles/shake/audio local |
| §8 Zero budget | free tiers, 2 phones, GitHub, local APK | this repo policy (no APKs committed), UGS free-tier notes |
| §9 24–48h plan | phased build, honest scope | docs mirror phases; this is the verified vertical slice |
| §10 Master prompt | everything above | plus: timeouts/retries (`Extensions.WithTimeout`, `MultiplayerManager.Friendly`), no giant GameManager, namespaces, tests, acceptance list in `QA_TEST_PLAN.md §F` |
| §11 References | Unity case studies & docs | applied as technical lessons only; cited in README; no SYBO material used |
| §12 Recommendation | movement → track → room/join → death logic → UI → performance → content | implementation order followed; priority list satisfied |

## Deliberate, documented deviations

1. **Sessions SDK** → Relay join-codes used directly for v1 (fewer package-API risks);
   migration sketch documented (MULTIPLAYER_GUIDE §6). The brief allows interface-level
   isolation of room entry points.
2. **Movement/death authority** → owner-reported deaths with host-side result authority +
   plausibility clamps (standard for a 2-player friends slice; anti-cheat noted as roadmap).
3. **Legacy uGUI Text** → chosen over TMP to guarantee compile/run on a fresh clone
   without importing TMP Essentials; upgrade is a mechanical swap.
4. **Binary assets** → none by design (procedural primitives + generated audio) to honor
   the IP boundary in a text-only repo.
