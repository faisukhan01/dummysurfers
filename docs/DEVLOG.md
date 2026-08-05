
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
