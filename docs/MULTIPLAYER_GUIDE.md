# Multiplayer Guide — Dummy Surfer

Architecture: **Unity Relay** (transport intermediary) + **Netcode for GameObjects**
(gameplay sync) + **Unity Authentication** (anonymous identity) + host-authoritative
match state. No dedicated server, no Python/Node backend — exactly the free-first
stack from the brief.

## 1. One-time Unity Gaming Services setup (free tier)

1. Open the project in Unity 6 (setup wizard done).
2. **Edit ▸ Project Settings ▸ Services** → sign in with your Unity ID.
3. Create/select a project ID and **Link** it to this Unity project.
4. In the Unity Dashboard (cloud.unity.com) open the linked project →
   enable **Authentication** (Anonymous sign-in is on by default) and
   **Relay**.
5. Done. The game initializes services at boot and signs in anonymously; Relay
   allocations are created only when someone taps **Create Room**.

> **Free-first notes:** Unity's pricing page publishes free monthly allowances for
> Relay bandwidth and Authentication MAU. A 2-player room with a friend stays far
> inside free quotas; usage is visible in the dashboard. Quotas are limits, not fees —
> the game refuses gracefully with a friendly message if a quota is hit.

## 2. Room flow (implemented)

```
Main Menu → Multiplayer
  CREATE ROOM  → Auth check → Relay allocation → join code (6 chars)
               → lobby (share/copy code, player cards, READY)
  JOIN ROOM    → Auth check → Relay join by code → lobby
  Both READY   → host resets flags, loads "Game" via NGO scene manager
               → host generates shared seed → synchronized 3-2-1 countdown
               → RUNNING (identical deterministic track on both clients)
  One dies     → reported to host → 5 s comparison window
  Both dead    → greater distance → more coins → DRAW   (spec §3.3)
  One survives → survivor wins
  RESULTS      → REMATCH (host) / EXIT
```

**Synchronized vs local:** lane/position/flags (~16 B state at tick rate) + seed +
phase + scores + outcome are networked. Particles, camera shake, decorative trains,
audio and UI animation stay local (spec §7.1/7.2).

## 3. Testing matrix

| Test | How | Expected |
|---|---|---|
| Create/join | Two devices (or editor + device) | Lobby shows both cards |
| Ready sync | Toggle READY on both | Both badges flip; countdown auto-starts |
| Same track | Compare obstacle sequences both screens | Identical patterns & coins |
| Winner | Kill one player, other survives 5 s | Survivor wins immediately |
| Compare | Both die within 5 s | Distance decides, then coins, then DRAW |
| Rematch | Host taps REMATCH | New seed, pooled reset, countdown again |
| Host leaves | Kill host app mid-run | Client → DISCONNECTED screen → menu |
| Client leaves | Kill client app mid-run | Host gets win + "opponent left" toast |
| Bad code | Join with wrong code | Friendly error, stays on screen |

## 4. Technical map

| Concern | File |
|---|---|
| Room orchestration, timeouts, friendly errors | `Scripts/Multiplayer/MultiplayerManager.cs` |
| Relay allocation + transport binding | `Scripts/Multiplayer/RelayRoomService.cs` |
| Lobby readiness (scene NetworkObject in Menu) | `Scripts/Multiplayer/LobbyState.cs` |
| Seed, phases, scores, deaths, outcome (host authority, plausibility clamp) | `Scripts/Multiplayer/MatchStateManager.cs` |
| Per-runner movement sync + interpolation + death reporting | `Scripts/Multiplayer/NetworkPlayerSync.cs` |
| Silent transport-drop watchdog | `Scripts/Core/ConnectionGuard.cs` |
| Pure result rules (unit-tested) | `Scripts/Multiplayer/MatchRules.cs` |
| Deterministic track from shared seed | `Scripts/Utilities/SeededRandom.cs`, `Scripts/Track/PatternGenerator.cs` |

## 5. Authority model & honest limits

- **Host authority for:** match phases, countdown, death windows, result decisions,
  stats plausibility clamping (`distance ≤ elapsed × maxSpeed × margin`).
- **Owner authority for:** that runner's own movement and its own death report — the
  standard lightweight model for a 2-player friends-and-family prototype. This is
  documented deliberately: full server-side movement validation (anti-cheat) is out of
  scope for the university slice and listed in the roadmap.
- **Late packets** can't resurrect anyone: `Alive` only flips back to true when the host
  starts a new countdown.

## 6. Migrating to the Multiplayer Services Sessions SDK (optional)

The brief lists the unified Sessions SDK as the long-term entry point. This slice uses
Relay directly for zero-dependency robustness. Migration sketch: replace
`MultiplayerManager.CreateRoomAsync/JoinRoomAsync` internals with

```csharp
var options = new SessionOptions { Name = roomName, MaxPlayers = 2 }
              .WithRelayNetworkTransport();
session = await MultiplayerSessionService.Instance.CreateOrJoinSessionAsync(id, options);
```

`LobbyState` ready-flags can then be backed by session properties. Everything else
(match logic, seed, sync) is unchanged.
