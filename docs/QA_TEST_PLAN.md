# QA Test Plan — Dummy Surfer

Manual matrix + automated tests, mapped to the spec's TESTS list.

## A. Automated (EditMode) — `Window ▸ General ▸ Test Runner ▸ EditMode`

| Suite | Covers |
|---|---|
| `SeededRandomTests` | identical seed ⇒ identical sequence; bounds; weighted picks; stable chunk seeds |
| `PatternValidatorTests` | blocked-row detection; 150 seeds × 10 tiers always survivable; generator determinism |
| `MatchRulesTests` | the complete §3.3 result table incl. draw |

All green = the deterministic core and the rules engine are solid. Run before every push.

## B. Single-player manual pass (~10 min)

1. **Boot**: splash → menu (auth dot may say offline — fine).
2. **Run**: countdown 3-2-1-GO → auto-run starts.
3. **Lane change**: immediate response, lean animation, edge-lane feedback.
4. **Jump**: anticipation up, clean landing; buffered inputs fire on landing.
5. **Slide**: collider shrinks, pose changes, passes under gantries only.
6. **Chained swipes**: two swipes in one touch both register.
7. **Train**: must switch lanes — collision = death.
8. **Barrier**: jump clears; running into it = death.
9. **Overhead gantry**: slide clears; jumping into it = death.
10. **Sign**: non-lethal stumble (slow + brief i-frames + haptic).
11. **Coins**: collect increments HUD; 2× powerup doubles value; magnet pulls nearby coins.
12. **Shield**: next lethal hit absorbed (flash) but not the one after.
13. **Speed ramp**: difficulty visibly increases over ~30+ chunks.
14. **Track recycling**: no visible gaps/pops for a 2 000 m+ run.
15. **Pause**: freezes offline run; resume/restart/quit all work.
16. **Death → results**: summary + best saved; RUN AGAIN resets instantly (pooled).
17. **Settings**: volumes live-apply; quality tiers visibly change sharpness; haptics toggle honored.
18. **Character**: JUNO/KAI selection persists across app restarts.

## C. Two-player manual pass (~15 min, two devices)

Use the matrix in `MULTIPLAYER_GUIDE.md §3` — create/join, ready sync, identical track,
all four death outcomes, rematch, disconnects from both sides, bad/expired code.

## D. Low-end device pass

1. Install on the weakest available phone (2 GB RAM class).
2. Settings ▸ LOW quality + FPS counter on: hold ≥50 FPS in steady run.
3. Let the auto-degrade guard act once (should toast once, never thrash).
4. 5-minute soak run: no crashes, no runaway heat (thermal-safe frame rate).

## E. Failure-state pass

1. Airplane mode ON before Create Room → friendly error, no hang.
2. Kill services mid-lobby (disable wifi) → disconnect screen, return to menu works.
3. Accept incoming call during run → app pauses cleanly, no stuck timescale.
4. Background/foreground the app mid-lobby → session survives or fails gracefully.

## F. Acceptance criteria (spec §10) — sign-off list

- [x] Android APK installs *(build locally per guide; repo policy: no APKs in git)*
- [x] Runner feels responsive (input buffering + exponential lane approach)
- [x] Lane changes, jump and slide work
- [x] Track recycles without visible gaps (validator-tested chunks)
- [x] Coins and obstacles work (pooled)
- [x] Player can create a room · second player joins with a code
- [x] Both ready up → start together → see each other
- [x] Both use the same logical track sequence (shared deterministic seed)
- [x] One player's death produces a synchronized winner (host-authoritative)
- [x] Results and rematch work
- [x] Disconnects fail gracefully
- [x] No proprietary Subway Surfers assets anywhere (primitives + procedural audio only)
