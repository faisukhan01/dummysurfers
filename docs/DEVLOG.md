
- Locked the stack: pure Kotlin + LibGDX, zero external assets, everything procedural.

- Mapped every spec section to a module so nothing ships as a stub.

- All tunables in one file — speeds, lanes, jump/slide timing, phases, economy.

- scale(z) = f/(f+z), fog blending, camera follow + shake. The 3D illusion works!

- 15px dead zone, 300ms window, dominant-axis lock. Feels exactly right on touch.

- Lane lerp with ease-out, parabolic jump + buffering, slide, squash & stretch.

- Endless segments: open / urban / station / bridge / tunnel / industrial, 16 deco kinds.

- Always >=1 safe lane, guaranteed reaction gaps, coins trace the safe path.

- 3 kinds — parked, same-direction, approaching with horn + headlights. Multi-car consists.

- White band + yellow cab look, graffiti freight cars. Trains finally look alive.

- 10-frame spin, glow, bob; magnet flight arcs to the player. Rising-pitch ding!

- Magnet / x2 / shield / boost / super-jump, 3 upgrade levels each, +3s per level.

- base + (max-base)(1-e^(-dk)) with 5 phases. Smooth wall, no cheap spikes.

- ALL art via Pixmap: skies, skylines, coin frames, 9-patch UI, launcher icons.

- PCM sequencer at 132BPM (kick/hat/bass/lead) + 14 SFX. Zero audio files!

- Intensity scales live with the run — the mix gets hotter as you fly.

- The guard + dog hunt you after stumbles. Spacing tuned so he taunts, never cheats.

- Preferences + JSON, versioned deep-merge, migrations safe for older builds.

- 3 active auto-generated missions with claimable rewards.

- 4 characters, 5 upgrades, 4 trails — all wired to real persistence.

- 4-step first-run tutorial, swipe-gated, persisted completion.

- +25 bonus, camera shake, floating text. Skimming trains feels amazing.

- Sparkles, bursts, confetti, dust, boost streaks, shield break — all pooled.

- Sky, sun glow, clouds, 2 skylines, ground, track bed, fog. Depth at last.

- Segment-specific ambience: tunnel darkness, bridge sky gaps.

- Vibrator API wired to lane switches, landings, coins and crashes.

- Letterboxed virtual stage — every aspect ratio gets the same fair view.

- LWJGL3 module for fast iteration; same core, instant reloads.

- GitHub Actions: debug APK + release APK + AAB on every push.

- :core, :android, :desktop all compile. The game is REAL.

- Hue-cycling fixed (was stuck teal); emit point tightened to the feet.

- Panels now offset by scrollY with clamped drag; CHARACTERS scrolls.

- Bird flocks in the sky, commuters waiting on platforms.

- Speed lines past 88% + warm vignette during boost.

- Hat/lead scaling moved to schedule time so it follows speed LIVE.

- Palette, liveries, UI DNA locked. Prepping the full Subway-Surfers-style repaint.

- Cyan sky, cream horizon, terracotta ballast, vivid grass. Goodbye sunset.

- Gold coin pill, white outlined score, orange pause square, segmented power meter.

- Luckiest Guy display + Fugaz One body, navy outlines everywhere.

- Radial rainbow burst + confetti + streaks on a fresh high score.

- Panels with deep navy slots, gold values, tabbed menu with missions badge.

- Committed keystore so every CI build keeps the same stable signature.

- Stable /releases/latest/download asset name for scan-to-install.

- Sketching bigger heads, layered clothes, knee-bend run cycle from behind.

- x86_64 natives + crash guard for devices that refuse to start.

## v6.0.0 — versionCode 26 · "can't-crash cant-stop"

- FREETYPE IS GONE FROM THE DEVICE PATH: fonts ship as build-time baked BMFont
  atlases (FontBaker at desktop build); gdx-freetype natives + TTF assets
  dropped (~1MB lighter). The uncatchable native-crash class is extinct.

- allowBackup=false: Android auto-backup could silently restore poisoned
  prefs/files onto FRESH installs, resurrecting "downloaded the app, opens and
  instantly closes" loops no APK fix could break. Every install now starts
  clean, always.

- Every Activity lifecycle callback (onResume/onPause/onStop/onDestroy) is
  guarded — the AndroidInput.onResume() NPE class reported on realme RMX5555
  (Android 16) can no longer end the process; keep-screen-on set via window
  flag (zero PowerManager involvement).

- SaveManager nuke-from-orbit: an unreadable save purges prefs, rebuilds fresh,
  persists — never throws, never loops.

- SS metro world pass 20-c/d: saturated daylight palette, puffy multi-puff
  clouds, mow-stripe grass, gravel speckle field, lane wear streaks, warm-steel
  slim rails, redder chunky sleepers w/ lit edges, high-contrast walkway-
  striped train roofs (de-clipped top light), 8 SS-grade train liveries w/
  big rounded windows, doors, bogies, cab windscreen + glow headlights.

- 920 documented tuning/fidelity attempts logged in docs/attempts/ (total
  main-branch commits: 1017+).
