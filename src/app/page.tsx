'use client'

import { useEffect, useState } from 'react'

const NAV = [
  { href: '#ss-redesign', label: 'SS Redesign' },
  { href: '#status', label: 'Status' },
  { href: '#features', label: 'Features' },
  { href: '#spec', label: 'Spec 35/35' },
  { href: '#console', label: 'Build' },
  { href: '#ship', label: 'Ship It' },
  { href: '#changelog', label: 'Changelog' },
]

const features = [
  { icon: '🎥', title: 'Pseudo-3D Engine', desc: 'scale(z) = f/(f+z) perspective projection, z-sorted rendering, atmospheric fog — the real 3D illusion.' },
  { icon: '🎨', title: '7-Layer Parallax', desc: 'Bright noon sky, sun glow, fluffy clouds, two city skylines, converging rails, rushing sleepers, warm fog.' },
  { icon: '🐦', title: 'Living World', desc: 'Bird flocks glide the skyline, commuters wait on platforms, 6 segment biomes with tunnels & bridges.' },
  { icon: '🏃', title: 'Subway-Surfers Feel', desc: '0.15s ease-out lane switches, 0.6s parabolic jumps, 0.5s slides, jump buffering, squash & stretch.' },
  { icon: '🚂', title: 'SS Trains', desc: 'White body band, yellow cab face, graffiti freight: 6 liveries, parked / same-direction / oncoming, horns & headlights.' },
  { icon: '🧠', title: 'Iron-Rule Spawner', desc: 'Always ≥1 safe lane, guaranteed reaction time, coins guide the safe path. Never unfair.' },
  { icon: '⚡', title: '5 Power-Ups', desc: 'Magnet, Score ×2, Shield, Boost, Super Jump — each upgradeable 3 levels, trails hue-cycle rainbow.' },
  { icon: '🔊', title: '100% Synth Audio', desc: 'PCM music sequencer at 132 BPM + 14 procedural SFX that follow game speed. Zero audio files.' },
]

const modules = [
  { name: 'core', desc: 'Pure Kotlin game engine — projection, spawner, synth audio, SS renderers & UI (~5,300 LOC)' },
  { name: 'android', desc: 'Android launcher, portrait fullscreen, API 24+, icon set' },
  { name: 'desktop', desc: 'LWJGL3 launcher for fast desktop testing (`gradlew desktop:run`)' },
  { name: '.github/workflows', desc: 'CI on every push → debug APK + release APK + Play AAB artifacts' },
]

const specSections = [
  'Tech stack', 'SS reference study', 'Project structure', 'Visual quality', 'Core gameplay',
  'World generation', 'Trains', 'Coins', 'Power-ups', 'Chaser',
  'Score system', 'Characters', 'Shop', 'Main menu', 'Game HUD',
  'Pause', 'Game over', 'Tutorial', 'Missions', 'Save system',
  'Settings', 'Audio', 'Particles', 'Haptics', 'Game states',
  'Performance', 'Resolution', 'Code quality', 'Game config', 'No fake features',
  'Dev order', 'Build validation', 'Final quality', 'Original content', 'Android optimization',
]

const paletteChips = [
  { hex: '#3FB8F5', name: 'SKY' },
  { hex: '#8FD8F8', name: 'AZURE' },
  { hex: '#FFE9C2', name: 'CREAM' },
  { hex: '#FFC93C', name: 'GOLD' },
  { hex: '#FF5A3C', name: 'ORANGE' },
  { hex: '#3DBB5A', name: 'GREEN' },
  { hex: '#7B84D6', name: 'PERIWINKLE' },
  { hex: '#2A3057', name: 'NAVY' },
]

const consoleLines = [
  { p: '$', t: 'gradle :android:assembleDebug', c: 'text-[#E4E8FC]' },
  { p: '>', t: 'Task :core:compileKotlin', c: 'text-[#AAB4E8]' },
  { p: '', t: 'BUILD SUCCESSFUL in 7s', c: 'text-[#7FE39A]' },
  { p: '>', t: 'Task :android:assembleDebug', c: 'text-[#AAB4E8]' },
  { p: '', t: '11 actionable tasks: 11 executed', c: 'text-[#AAB4E8]' },
  { p: '', t: 'BUILD SUCCESSFUL in 32s', c: 'text-[#7FE39A]' },
  { p: '', t: 'android/build/outputs/apk/debug/android-debug.apk  (2.0 MB)', c: 'text-[#FFC93C]' },
]

const changelog = [
  {
    v: 'v6.3.0', date: 'The fast second boot — first launch paints, every launch after loads', latest: true, items: [
      '⚡ PNG STARTUP CACHE: the first launch paints the world once and saves every texture as a finished PNG — from the SECOND launch the game loads that art directly, skipping ALL per-pixel painting. Boot drops from "up to a minute" to a fraction, and the exact code region the slow device froze inside is barely visited at all',
      '🧠 30 PAINTS ELIMINATED PER BOOT: found the power-up/nav icon builder painting all six icons six times each (36 paints for 10 textures) — now each slot paints exactly one icon. Less work, faster boot, same art',
      '🩹 SELF-HEALING CACHE: a corrupted cache file deletes itself and repaints on the spot — proven on camera (1 corrupted file → 79 loaded from cache + 1 repainted, boot clean). Only successful paints are ever cached, so a placeholder from a deadline breach can never poison tomorrow\'s boot',
      '🔒 PIXEL-IDENTICAL: filters travel with the cached file, so art loaded from cache is visually identical to a fresh paint — verified with gameplay screenshots side by side',
      '✅ RE-PROVEN SAFE: the full v6.2.0 safety net re-tested green — watchdog 9/9 assertions, forced 1ms paint budgets still boot a playable game, cold cache hard-fails its own QA assertion (so the tests cannot lie)',
      '🔎 VERSION ON SCREEN: the boot screen prints the exact build ("v6.3.0 (code 29)") — you can always verify which APK you are running. If it does not say v6.3.0, you are running an old download',
      '🏷️ versionCode 29 — clearly distinct from every older download',
    ]
  },
  {
    v: 'v6.2.0', date: 'The never-stuck boot — a 30-minute freeze is now structurally impossible', items: [
      '⏱️ PAINT DEADLINES: the "stuck on painting textures 2/3 for 30 minutes" report had NO time limit anywhere — now every texture paints under a hard deadline. A slow texture bails out and boots as a tiny placeholder instead of freezing your phone. A texture can cost seconds, never minutes, never infinity',
      '👮 BOOT WATCHDOG: a native watcher now checks every 200 ms whether boot progress is actually MOVING. 20 seconds of true silence = frozen → the app silently restarts itself once for a clean boot — most stalls never reach your eyes',
      '🧊 IF IT EVER FREEZES TWICE: a native "BOOT STILL FROZEN" card appears with RESTART APP / COPY REPORT / CLOSE APP buttons — pure Android widgets that work even when the 3D engine is hard-blocked, which nothing inside the engine could recover from',
      '📶 LIVE PER-TEXTURE PROGRESS: the loading status now ticks ("painting textures 2/3 · 7 painted") so you SEE movement during startup — and slow-but-healthy phones automatically get bigger time budgets, so slow devices never false-trigger the watchdog',
      '📊 EVERY STALL IS RECORDED: stalls land in the boot report with the exact stage, so a device that misbehaves twice in a row is diagnosable from the report alone',
      '🧪 PROVEN ON CAMERA: 9/9 watchdog-logic assertions passed, forced 1ms texture budgets still boot a playable game, and a simulated 8-second engine freeze recovered exactly as designed',
      '🏷️ versionCode 28 — clearly distinct from every older download',
    ]
  },
  {
    v: 'v6.1.0', date: 'The always-visible boot — the black blank page is extinct', items: [
      '🖼️ NATIVE LOADING SCREEN FROM FRAME ONE: launch now shows a real progress bar + live status ("painting textures 2/3…") pinned over the game — the old build painted the whole world BEFORE the first frame, so slow phones stared at a black void for the entire startup. Verified on camera: ~28s of loading now plays as a visible progress screen instead of a frozen black page',
      '📋 NATIVE ERROR CARD INSTEAD OF SILENCE: if any startup stage fails on your device, the overlay flips to a readable card naming the exact stage with a COPY REPORT button — pure Android widgets, works even if the 3D engine is completely dead. No more guessing what went wrong',
      '🎬 GL-LOADING SCREEN TOO: the in-engine boot shows the gold DUMMY SURFERS title, a 9-stage checklist (engine → textures 1/3 → fonts → 3D world → track) and a filling gold bar — frames render from the very first second',
      '🛡️ SAFEMODE CAN NO LONGER GO BLIND: the old SafeMode needed the very component it blamed — if the render batch itself failed it silently swallowed an exception every frame and painted a blank navy screen forever (that was the black page). It now degrades cleanly and the native card carries the message',
      '🎮 GAMEPLAY KEEPS RENDERING THROUGH ERRORS: update and render are guarded independently now — the worst a broken gameplay tick can do is pause the action; the sky, the world and the HUD stay on screen',
      '🎨 EGL HARDENING: the GL surface now requests true 8888 color + 16-bit depth + immersive mode — the old driver-lottery defaults let some OEM devices composite the surface as black. That entire failure class is gone',
      '♻️ THE CRASH DIALOG LOST ITS POWER: the "Oops — hit a snag" report now appears only after the game is visibly up, and every report is read-once (it can never loop over your launch again)',
      '🏷️ versionCode 27 — clearly distinct from every older download',
    ]
  },
  {
    v: 'v6.0.0', date: 'The cant-crash release + the big SS metro world pass', items: [
      '🛡️ FREETYPE IS GONE FROM YOUR PHONE: fonts ship pre-baked as atlas images — the uncatchable native crash class (the "opens and instantly closes" on some devices) is extinct. ~1MB lighter APK too',
      '🧹 FRESH INSTALLS STAY FRESH: Android auto-backup could silently restore a poisoned save onto a brand-new install and re-create the instant-close loop no update could fix. Backup is now off — every install starts clean, always',
      '❤️ EVERY lifecycle callback guarded: the AndroidInput.onResume() crash reported on Android 16 devices can no longer kill the process — worst case it is logged and the game keeps running',
      '💾 Unreadable save = auto-rebuilt: the save system purges corrupt data and rebuilds instead of crashing — never throws, never loops',
      '🚄 SS METRO WORLD PASS: saturated daylight, puffy cartoon clouds, mow-striped grass, gravel-speckled ballast, lane wear streaks, warm-steel rails, chunky red-brown sleepers — and 8 SS-grade train liveries with big rounded windows, passenger doors, bogies, cab windscreen + glowing headlights, walkway-striped roofs',
      '📊 920 documented tuning attempts logged — 1,018+ commits on the graph',
      '🏷️ versionCode 26 — clearly distinct from every older download',
    ]
  },
  {
    v: 'v5.4.0', date: 'Startup immunity — the app can literally never close itself anymore', items: [
      '🛡️ STARTUP IMMUNITY: boot is now staged with a fallback at every layer — one bad texture becomes a white substitute, one bad font becomes the engine font, and even a broken 3D engine still opens a SAFE MODE screen showing exactly what failed with a TAP TO RETRY button',
      '🚀 Start-first launcher: the game boots IMMEDIATELY on every open; the crash-report dialog now appears over the running game instead of gating it behind a black screen (this was the "opens once then never again" loop)',
      '🪂 Native fallback screen: if even the GL view cannot be created, the app shows the full error with COPY REPORT / TRY AGAIN buttons — the process never auto-closes, ever',
      '📋 Self-reporting: any startup failure is written to the crash report AND shown on screen, so a device-specific bug tells us exactly what happened instead of dying silently',
      '🧪 Proven on camera: forced-failure QA runs photographed all recovery paths — fonts-fail boots a playable game, scene-fail shows SafeMode, one tap rebuilds the full engine into the real menu',
      '🏷️ versionCode 25 — clearly distinct from every older download',
    ]
  },
  {
    v: 'v5.3.0', date: 'Jelly-UI fidelity pass — every button feels like the real game', items: [
      '🍬 SS JELLY BUTTONS: every button rebuilt — dark navy outline all around, glossy top band, chunky darker bottom lip that sells the 3D press. Buttons finally feel like candy, not sticky notes',
      '⭕ Round jelly buttons everywhere: pause roundel + settings & missions floaters got the white-ring circle treatment; the menu bottom bar went from heavy navy slabs to four floating round buttons with tiny labels',
      '▶️ PLAY triangles on RUN / RESUME / RUN AGAIN — the iconic SS action-button DNA',
      '⏸️ Pause card re-layout: big green RESUME play-slab + REDO / HOME candy buttons, portrait in a deep slot, gold score under the hero, everything floating on soft drop shadows',
      '💥 BUSTED! — the guard-catch game-over now shows SS\'s iconic giant red BUSTED! headline (display font, navy drop)',
      '🪙 Coin chips fixed: icon + count sit as one centered row on the pause & game-over cards (the icon used to hover over the digits)',
      '👻 HUD stays on screen during the catch slow-mo — score no longer vanishes while the guard grabs you',
      '🏷️ versionCode 24 — clearly distinct from every older download',
    ]
  },
  {
    v: 'v5.2.1', date: 'Cap-dome rebuild + portrait cuteness pass', items: [
      '🧢 THE red brick is dead: the in-game cap was a flat slab floating above the head — now a real rounded dome (squashed low-poly sphere) that hugs the skull like a baseball cap, with back stitch + top button',
      '😍 Menu portrait cuteness pass: symmetric arch brows, bigger centered irises w/ one big sparkle glint (no more googly eyes), narrowed teeth band, weird side "brim-tip" blobs removed, clean white rounded-square cap badge like the app icon',
      '🎒 Backpack focal detail: light front pocket + gold zipper replace the two dark harness straps; goggles & headphones re-fitted around the new spherical dome',
      '🪙 HUD coin counter now sits in a frosted navy pill — matching the pause roundel, straight off the real SS screen',
      '🌞 Menu daylight lift: soft white + sky wash brightens the title screen (no more grey/moody backdrop)',
      '🌥️ Runner shadow softened + tightened — no more dark hole under the character',
      '🏷️ versionCode 23 — clearly distinct from every older download',
    ]
  },
  {
    v: 'v5.2.0', date: 'Real-SS HUD + character rebuild', items: [
      '🎮 HUD mirrored to the REAL Subway Surfers layout: pause roundel top-LEFT (frosted navy, white bars), big outlined score top-RIGHT with gold ×N chip, clean coin icon + count beneath, distance under that — no more gold slab pills',
      '🧑‍🎨 Menu portrait rebuilt from scratch: one big face circle (no more mushroom robot), clipped cap dome w/ strap + seams + backwards brim tips, scalloped hair fringe, oval eyes w/ iris + double glints, open grin w/ teeth + tongue, blush, drawstrings, chunky white-soled sneakers',
      '🎒 Gameplay back-view rebuild: sneaker white mid-sole + heel tabs, sphere hands + shoulder joints, cap seam band + back stitch, nape hair spikes, wider ears, smaller pack with side pockets, vertical straps + gold clips, hood roll behind the neck',
      '🎥 Camera pulled back + up — the runner now fills ~25% of the frame like the real game, so you can actually read the track ahead',
      '🐛 Menu coin icon rendered as a dark blob (stale SpriteBatch tint leaked across frames) — fixed everywhere; settings gear is a real white gear glyph now',
      '🏷️ versionCode 22 — clearly distinct from every older download',
    ]
  },
  {
    v: 'v5.1.0', date: 'Crash-fix + thumbnail hero', items: [
      '🛡️ THE crash-loop fix: the in-app crash reporter could crash the launcher itself (crash file never consumed + unguarded dialog) — after one crash the app died on every launch. Report is now consumed atomically and the dialog path is fully guarded',
      '🎧 Audio-thread immunity: a dying audio device (background/foreground races, audio-focus) used to kill the whole process mid-run — the mixer now degrades to silence instead',
      '🧯 Render safety net: a failed gameplay tick recovers to the menu instead of ending the process; corrupt save files fall back to a fresh start instead of crashing at boot',
      '🧑‍🎨 JACK is now the thumbnail character: blue pack, dark navy jeans, red sneakers, big cartoon eyes + smile, hoodie drawstrings — menu portrait & gameplay match the app icon',
      '🌄 Warmer world: sand-brown ballast replaces the washed-out gray; menu shows BY FAISAL KHAN',
      '🏷️ versionCode 21 — clearly distinct from every older download',
    ]
  },
  {
    v: 'v5.0.0', date: 'Playability overhaul', items: [
      '🖱️ THE menu fix: RUN/CHARS/SHOP/TASKS/SETUP were dead on touch devices — touch events now use real event coordinates (verified end-to-end by scripted taps)',
      '🔊 Audio engine crash fix: sample-index overflow in the mixer thread + realtime pacing for weird audio devices',
      '⚖️ Fair obstacle rules: jump→slide combos can no longer spawn closer than the jump arc allows',
      '🖐️ Swipe quality: mid-gesture firing at ~22dp, no gesture-duration limit, flick fallback',
      '🚫 Non-blocking first run: hint chips float over gameplay — the old lock-step tutorial trap is gone',
      '🏷️ versionCode 20 — clearly distinct from every older download',
    ]
  },
  { v: 'v4.0 – v4.7', date: 'Deep polish era', items: ['🚂 Roof-running: ramps lead up onto train roofs with rooftop coin trails', '🚨 Guard catch sequence + chase pressure tuning', '🧍 3D character rig polish: hanging limb geometry, arm pump, goggle details', '🌆 Urban canyon: walls with crowd art, teal skyline haze, moving-train headlights', '🎁 Gift doubler, mission trophy popups, hoverboard double-tap'] },
  { v: 'v3.0', date: 'SS flow', items: ['▶️ Straight into the run — the lock-step tutorial gate removed for SS-style onboarding'] },
  { v: 'v1.0 – v2.0', date: 'Foundation', items: ['3D engine scaffolding, trains, lanes, coins, shop/missions/settings, CI releases'] },
]

export default function Home() {
  const [tick, setTick] = useState(0)
  const [activeSpec, setActiveSpec] = useState<number | null>(null)
  useEffect(() => {
    const t = setInterval(() => setTick(v => v + 1), 80)
    return () => clearInterval(t)
  }, [])

  const phase = Math.sin(tick * 0.35)

  return (
    <main
      className="ss-font min-h-screen flex flex-col text-[#24316B] selection:bg-[#FFC93C]/60 selection:text-[#24316B]"
      style={{ background: 'linear-gradient(180deg,#8FD8F8 0%,#C9ECFB 28%,#FFE9C2 72%,#FFE9C2 100%)' }}
    >
      <style>{`
        .ss-font { font-family: var(--font-fredoka), 'Baloo 2', 'Fredoka', ui-rounded, 'Segoe UI Rounded', 'Comic Sans MS', 'Trebuchet MS', system-ui, sans-serif; }
        .ss-head { text-shadow: 0 3px 0 rgba(255,255,255,.7); }
        .ss-card { border: 3px solid #fff; border-radius: 1.25rem; box-shadow: 0 6px 0 rgba(36,49,107,.22); }
        .ss-slot { background: #4A529E; box-shadow: inset 0 3px 6px rgba(20,24,52,.35); }
        .ss-btn { display:inline-flex; align-items:center; justify-content:center; gap:.55rem; border-radius:1rem; border:3px solid #fff; padding:.8rem 1.5rem; font-weight:700; text-transform:uppercase; letter-spacing:.04em; color:#fff; text-shadow:0 2px 0 rgba(36,49,107,.45); transition: transform .08s ease, box-shadow .08s ease, filter .15s ease; }
        .ss-btn:hover { filter: brightness(1.06); }
        .ss-btn:active { transform: translateY(4px); }
        .ss-btn-gold { background:#FFC93C; box-shadow: 0 5px 0 #E09B12, 0 10px 18px rgba(36,49,107,.25); }
        .ss-btn-gold:active { box-shadow: 0 1px 0 #E09B12; }
        .ss-btn-green { background:#3DBB5A; box-shadow: 0 5px 0 #2E9147, 0 10px 18px rgba(36,49,107,.25); }
        .ss-btn-green:active { box-shadow: 0 1px 0 #2E9147; }
        .ss-btn-navy { background:#2A3057; box-shadow: 0 5px 0 #1B2038, 0 10px 18px rgba(36,49,107,.25); }
        .ss-btn-navy:active { box-shadow: 0 1px 0 #1B2038; }
        @keyframes ss-float { 0%,100% { transform: translateY(0); } 50% { transform: translateY(-8px); } }
        .ss-cloud { animation: ss-float 6s ease-in-out infinite; }
      `}</style>

      {/* ── HERO: bright SS sky ─────────────────────────────────────── */}
      <header className="relative overflow-hidden">
        <div className="absolute inset-0" style={{ background: 'linear-gradient(180deg,#3FB8F5 0%,#8FD8F8 58%,#FFE9C2 100%)' }} />
        {/* sun */}
        <div aria-hidden className="absolute right-[8%] top-[10%] h-24 w-24 sm:h-32 sm:w-32 rounded-full bg-[#FFFDF2] shadow-[0_0_60px_30px_rgba(255,237,184,.9)]" />
        {/* fluffy clouds */}
        <Cloud className="left-[5%] top-[16%] h-10 w-28 sm:h-14 sm:w-40" />
        <Cloud className="left-[38%] top-[7%] h-8 w-20 sm:h-10 sm:w-28 opacity-90" delay={1.2} />
        <Cloud className="right-[28%] top-[26%] h-9 w-24 sm:h-12 sm:w-32 opacity-85" delay={2.4} />

        <div className="relative max-w-5xl mx-auto px-4 sm:px-10 pt-10 sm:pt-16 pb-40 sm:pb-48">
          {/* ── Author byline — label on top, name beneath ─────────── */}
          <div className="inline-block select-none" title="Faisal Khan — Developer">
            <p className="flex items-center gap-2 text-[10px] sm:text-[11px] font-bold uppercase tracking-[0.32em] text-[#24316B] [text-shadow:0_1px_0_rgba(255,255,255,.65)]">
              <span aria-hidden className="h-[2px] w-6 sm:w-8 rounded-full bg-gradient-to-r from-transparent to-[#E0A62B]" />
              Built by
            </p>
            <div className="mt-0.5">
              <GraffitiName
                name="Faisal Khan"
                className="text-[1.4rem] sm:text-[1.75rem]"
                shadowFilter="drop-shadow(0 2px 0 rgba(36,49,107,.9)) drop-shadow(0 5px 8px rgba(36,49,107,.22))"
              />
            </div>
          </div>
          <h1 className="mt-3 font-bold leading-[0.95] select-none">
            <GraffitiWord word="DUMMY" tilt={-2} className="text-[3.6rem] sm:text-8xl" />
            <GraffitiWord word="SURFERS" tilt={1.5} className="text-[3.6rem] sm:text-8xl mt-1 sm:mt-2" />
          </h1>
          <p className="mt-6 max-w-xl text-base sm:text-lg font-semibold text-[#24316B]">
            A premium Android endless runner in Kotlin + LibGDX — now wearing Subway Surfers&apos;
            bright daylight look. Fully playable, original art & synth audio, zero external assets.
          </p>
          <div className="mt-7 flex flex-wrap gap-3">
            <a href="#ship" className="ss-btn ss-btn-gold text-sm sm:text-base">🔨 Build the APK</a>
            <a href="#features" className="ss-btn ss-btn-green text-sm sm:text-base">🎮 What&apos;s inside</a>
          </div>
          <div className="mt-6 flex flex-wrap gap-2.5">
            <Badge>✅ Kotlin 2.0</Badge>
            <Badge>✅ LibGDX 1.12.1</Badge>
            <Badge>✅ APK build verified in sandbox</Badge>
            <Badge>✅ GitHub Actions CI included</Badge>
          </div>
        </div>

        {/* terracotta ground strip with CSS rails + runner parade */}
        <div aria-hidden className="absolute bottom-0 left-0 right-0">
          <div className="h-3 bg-[#5FBF4A] shadow-[inset_0_3px_0_rgba(255,255,255,.35)]" />
          <div className="relative h-16 sm:h-20 bg-[#C97B5E] overflow-hidden shadow-[inset_0_6px_0_rgba(0,0,0,.08)]">
            <div className="absolute inset-0 opacity-50" style={{ backgroundImage: 'repeating-linear-gradient(90deg,#6B4A36 0 14px,transparent 14px 64px)' }} />
            <div className="absolute left-0 right-0 top-2.5 h-[5px] rounded-full bg-[#E8E4DA] shadow-[0_2px_0_#B4553A]" />
            <div className="absolute left-0 right-0 bottom-3 h-[5px] rounded-full bg-[#E8E4DA] shadow-[0_2px_0_#B4553A]" />
            <div className="absolute bottom-1 left-0 flex items-center gap-2 text-2xl sm:text-3xl" style={{ transform: `translateX(${(tick * 6) % 400 - 80}px)` }}>
              <span style={{ display: 'inline-block', transform: `rotate(${phase * 6}deg)` }}>🏃</span>
              <span className="inline-block animate-spin" style={{ animationDuration: '1.6s' }}>🪙</span>
              <span className="ml-16 text-3xl sm:text-4xl">🚂</span>
            </div>
          </div>
        </div>
      </header>

      {/* ── STICKY NAV: sky-cyan glass ──────────────────────────────── */}
      <nav
        className="sticky top-0 z-30 border-b-[3px] border-white/70 backdrop-blur-md"
        style={{ background: 'linear-gradient(90deg, rgba(63,184,245,.92), rgba(143,216,248,.92))', boxShadow: '0 4px 0 rgba(36,49,107,.15)' }}
      >
        <div className="max-w-5xl mx-auto px-4 sm:px-10 py-2.5 flex items-center gap-2 overflow-x-auto">
          {NAV.map(n => (
            <a key={n.href} href={n.href}
              className="shrink-0 rounded-full bg-[#2A3057] px-4 py-1.5 text-sm font-semibold text-white border-2 border-white/60 [text-shadow:0_1px_0_rgba(36,49,107,.5)] transition-colors hover:bg-[#FFC93C] hover:text-[#24316B] hover:[text-shadow:none]">
              {n.label}
            </a>
          ))}
          <span className="ml-auto shrink-0 rounded-full bg-[#FFC93C] px-3 py-1 text-[11px] sm:text-xs font-bold uppercase tracking-wider text-[#24316B] border-2 border-white shadow-[0_2px_0_#E09B12]">
            v6.3.0 — The Fast Second Boot!
          </span>
        </div>
      </nav>

      {/* ── SS REDESIGN SHOWCASE (new in v1.2) ──────────────────────── */}
      <section id="ss-redesign" className="max-w-5xl mx-auto w-full px-4 sm:px-10 pt-10 pb-12 scroll-mt-24">
        <H2>Subway Surfers Redesign</H2>
        <p className="-mt-2 mb-6 max-w-2xl text-sm sm:text-base font-semibold text-[#3A4470]">
          The whole game is painted in Subway Surfers&apos; bright visual language — studied
          from official screenshots, rebuilt 100% procedurally, now true-3D with ramps,
          train roofs, guard chases and hoverboards.
        </p>

        {/* palette swatch strip */}
        <div className="grid grid-cols-4 sm:grid-cols-8 gap-3 mb-8">
          {paletteChips.map(c => (
            <div key={c.hex} className="text-center">
              <div className="h-14 rounded-2xl border-[3px] border-white shadow-[0_4px_0_rgba(36,49,107,.18)]" style={{ background: c.hex }} />
              <p className="mt-1.5 text-[11px] font-bold uppercase tracking-wide text-[#24316B]">{c.name}</p>
              <p className="font-mono text-[10px] text-[#5A659B]">{c.hex}</p>
            </div>
          ))}
        </div>

        <div className="grid lg:grid-cols-2 gap-6 items-start">
          {/* HUD mock — pure CSS */}
          <div className="ss-card relative overflow-hidden h-72 sm:h-80" style={{ background: 'linear-gradient(180deg,#3FB8F5 0%,#8FD8F8 60%,#FFE9C2 100%)' }}>
            <div aria-hidden className="absolute right-6 top-6 h-10 w-10 rounded-full bg-[#FFFDF2] shadow-[0_0_24px_12px_rgba(255,237,184,.9)]" />
            <Cloud className="left-6 top-10 h-7 w-20 opacity-90" />
            <Cloud className="left-[42%] top-20 h-6 w-16 opacity-80" delay={1.6} />
            {/* ground */}
            <div aria-hidden className="absolute bottom-0 left-0 right-0">
              <div className="h-2.5 bg-[#5FBF4A]" />
              <div className="relative h-12 bg-[#C97B5E] overflow-hidden">
                <div className="absolute inset-0 opacity-50" style={{ backgroundImage: 'repeating-linear-gradient(90deg,#6B4A36 0 10px,transparent 10px 46px)' }} />
                <div className="absolute left-0 right-0 top-1.5 h-1 bg-[#E8E4DA]" />
                <div className="absolute left-0 right-0 bottom-2 h-1 bg-[#E8E4DA]" />
              </div>
            </div>
            {/* coin pill */}
            <div className="absolute left-3 top-3 flex items-center gap-1.5 rounded-full bg-[#FFC93C] border-b-4 border-[#E09B12] px-3 py-1.5 shadow-[0_3px_0_rgba(36,49,107,.35)]">
              <span className="grid h-5 w-5 place-items-center rounded-full border-2 border-[#E09B12] bg-[#FFD23E] text-[10px] leading-none text-[#7A4A12]">★</span>
              <span className="text-sm font-bold text-[#7A4A12]">12,480</span>
            </div>
            {/* score + multiplier */}
            <div className="absolute left-1/2 top-3 -translate-x-1/2 flex items-center gap-2">
              <span className="hidden sm:grid place-items-center rounded-full bg-[#FFC93C] border-b-4 border-[#E09B12] px-2 py-0.5 text-xs font-bold text-white [text-shadow:0_2px_0_rgba(36,49,107,.5)]">×2</span>
              <span className="text-xl sm:text-3xl font-bold text-white" style={{ WebkitTextStroke: '2px #24316B', textShadow: '0 4px 0 rgba(36,49,107,.45)' }}>184,320</span>
            </div>
            {/* pause */}
            <div className="absolute right-3 top-3 grid h-11 w-11 place-items-center rounded-xl border-[3px] border-white bg-[#FF5A3C] shadow-[0_4px_0_rgba(36,49,107,.35)]">
              <span className="flex gap-1">
                <span className="h-4 w-1.5 rounded-full bg-white" />
                <span className="h-4 w-1.5 rounded-full bg-white" />
              </span>
            </div>
            {/* power meter */}
            <div className="absolute bottom-3 left-1/2 -translate-x-1/2 w-44 rounded-full border-[3px] border-white bg-[#2A3057]/85 p-1 shadow-[0_3px_0_rgba(36,49,107,.35)]">
              <div className="h-3 rounded-full" style={{ backgroundImage: 'repeating-linear-gradient(90deg,#3DBB5A 0 18px,#2E9147 18px 22px)' }} />
            </div>
            <p className="absolute bottom-1 right-3 font-mono text-[10px] text-white/80 [text-shadow:0_1px_0_rgba(36,49,107,.6)]">HUD mock — pure CSS</p>
          </div>

          {/* before/after + link */}
          <div className="space-y-5">
            <div className="ss-card bg-[#7B84D6] p-5">
              <p className="mb-4 text-xs font-bold uppercase tracking-[0.2em] text-white [text-shadow:0_2px_0_rgba(36,49,107,.5)]">Before / After</p>
              <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-3">
                <div className="rounded-2xl border-[3px] border-white/80 p-3 text-center" style={{ background: 'linear-gradient(180deg,#2E2A4A 0%,#F2A75B 60%,#FFD9A0 100%)' }}>
                  <p className="text-2xl">🌇</p>
                  <p className="mt-1 text-xs font-bold uppercase text-white [text-shadow:0_1px_0_rgba(0,0,0,.4)]">v1.1 — sunset</p>
                </div>
                <span className="text-2xl font-bold text-white [text-shadow:0_2px_0_rgba(36,49,107,.5)]">→</span>
                <div className="rounded-2xl border-[3px] border-white/80 p-3 text-center" style={{ background: 'linear-gradient(180deg,#3FB8F5 0%,#8FD8F8 60%,#FFE9C2 100%)' }}>
                  <p className="text-2xl">☀️</p>
                  <p className="mt-1 text-xs font-bold uppercase text-[#24316B]">v4.x — daylight</p>
                </div>
              </div>
              <p className="mt-4 text-sm font-semibold leading-relaxed text-[#E4E8FC]">
                The moody v1.1 sunset is gone — the run now happens at bright noon: cyan sky,
                warm cream horizon, terracotta ballast and vivid grass, exactly like the reference.
              </p>
            </div>
            <a
              href="https://play.google.com/store/apps/details?id=com.kiloo.subwaysurf"
              target="_blank" rel="noreferrer"
              className="ss-btn ss-btn-navy w-full text-sm"
            >
              🛤️ Subway Surfers on Play Store ↗
            </a>
          </div>
        </div>
      </section>

      {/* ── STATS STRIP ─────────────────────────────────────────────── */}
      <section className="max-w-5xl mx-auto w-full px-4 sm:px-10 pb-12 grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3 sm:gap-4">
        {[
          { k: '35/35', v: 'spec sections done' },
          { k: '~5,300', v: 'lines of Kotlin' },
          { k: '2.0 MB', v: 'debug APK built' },
          { k: '100%', v: 'procedural assets' },
          { k: '6', v: 'train liveries' },
          { k: '18', v: 'deco kinds' },
        ].map(s => (
          <div key={s.v} className="ss-card bg-[#7B84D6] p-4 text-center hover:-translate-y-1 transition-transform">
            <p className="text-2xl sm:text-3xl font-bold text-[#FFD23E] [text-shadow:0_3px_0_rgba(36,49,107,.45)]">{s.k}</p>
            <p className="mt-1 text-[11px] sm:text-xs font-semibold uppercase tracking-wide text-white/90">{s.v}</p>
          </div>
        ))}
      </section>

      {/* ── BUILD MATRIX ────────────────────────────────────────────── */}
      <section id="status" className="max-w-5xl mx-auto w-full px-4 sm:px-10 pb-12 scroll-mt-24">
        <H2>Build Status <span className="text-[#3DBB5A] [text-shadow:0_2px_0_rgba(255,255,255,.7)]">● ALL PASSED</span></H2>
        <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {modules.map(m => (
            <div key={m.name} className="ss-card bg-[#7B84D6] p-5 hover:-translate-y-1 transition-transform">
              <div className="flex items-center justify-between gap-2 mb-3 flex-wrap">
                <span className="ss-slot rounded-lg px-2.5 py-1 font-mono text-sm font-bold text-[#FFD23E]">{m.name}</span>
                <span className="inline-flex items-center rounded-full bg-[#3DBB5A] border-2 border-white px-2.5 py-0.5 text-[10px] font-bold uppercase tracking-wider text-white shadow-[0_2px_0_#2E9147] [text-shadow:0_1px_0_rgba(36,49,107,.4)]">
                  ✓ Successful
                </span>
              </div>
              <p className="text-sm leading-relaxed text-[#E4E8FC]">{m.desc}</p>
            </div>
          ))}
        </div>
      </section>

      {/* ── FEATURES ────────────────────────────────────────────────── */}
      <section id="features" className="max-w-5xl mx-auto w-full px-4 sm:px-10 pb-12 scroll-mt-24">
        <H2>What&apos;s inside the game</H2>
        <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {features.map(f => (
            <div key={f.title} className="ss-card bg-[#9AA3E8] p-5 hover:-translate-y-1 transition-transform">
              <div className="w-11 h-11 grid place-items-center rounded-xl bg-[#FFC93C] border-b-4 border-[#E09B12] text-xl shadow-[0_3px_0_rgba(36,49,107,.25)]">
                {f.icon}
              </div>
              <p className="mt-3 font-bold uppercase tracking-wide text-white [text-shadow:0_2px_0_rgba(36,49,107,.45)]">{f.title}</p>
              <p className="mt-1.5 text-sm leading-relaxed text-[#F0F2FF]">{f.desc}</p>
            </div>
          ))}
        </div>
      </section>

      {/* ── SPEC COVERAGE ───────────────────────────────────────────── */}
      <section id="spec" className="max-w-5xl mx-auto w-full px-4 sm:px-10 pb-12 scroll-mt-24">
        <H2>Spec Coverage <span className="text-[#E09B12]">35 / 35</span></H2>
        <p className="-mt-2 mb-6 text-sm font-semibold text-[#3A4470]">Every section of the original 35-part specification — tap any tile.</p>
        <div className="grid grid-cols-5 sm:grid-cols-7 gap-2.5">
          {specSections.map((name, i) => (
            <button key={name}
              onMouseEnter={() => setActiveSpec(i)}
              onMouseLeave={() => setActiveSpec(null)}
              onClick={() => setActiveSpec(i)}
              aria-label={`Section ${i + 1}: ${name} — done`}
              className={`aspect-square rounded-xl border-b-4 grid place-items-center font-bold transition-all ${
                activeSpec === i
                  ? 'bg-[#FFC93C] border-[#E09B12] text-[#24316B] scale-105 shadow-[0_4px_10px_rgba(36,49,107,.3)]'
                  : 'bg-[#7B84D6] border-[#4A529E] text-white hover:bg-[#8D96E0] [text-shadow:0_2px_0_rgba(36,49,107,.35)]'
              }`}>
              {i + 1}
            </button>
          ))}
        </div>
        <p className="mt-4 h-6 text-sm font-bold text-[#24316B]">
          {activeSpec !== null ? `§${activeSpec + 1} — ${specSections[activeSpec]}  ✓ implemented` : '👆 hover / tap a tile for its name'}
        </p>
      </section>

      {/* ── BUILD CONSOLE ───────────────────────────────────────────── */}
      <section id="console" className="max-w-5xl mx-auto w-full px-4 sm:px-10 pb-12 scroll-mt-24">
        <H2>Build Console</H2>
        <div className="ss-card overflow-hidden">
          <div className="bg-[#2A3057] px-4 py-2.5 flex items-center gap-2 border-b-[3px] border-white/60">
            <span className="w-3 h-3 rounded-full bg-[#FF5A3C] border border-white/50" />
            <span className="w-3 h-3 rounded-full bg-[#FFC93C] border border-white/50" />
            <span className="w-3 h-3 rounded-full bg-[#3DBB5A] border border-white/50" />
            <span className="ml-3 text-xs text-[#C9CFF2] font-mono">gradle — dummy-surfers</span>
          </div>
          <div className="ss-slot p-5 font-mono text-sm space-y-1.5">
            {consoleLines.map((l, i) => (
              <p key={i} className={l.c}>
                {l.p && <span className="text-[#7FE39A] mr-2">{l.p}</span>}{l.t}
              </p>
            ))}
            <p className="text-[#7FE39A]">▌</p>
          </div>
        </div>
      </section>

      {/* ── SHIPPING GUIDE ──────────────────────────────────────────── */}
      <section id="ship" className="max-w-5xl mx-auto w-full px-4 sm:px-10 pb-16 scroll-mt-24">
        <H2>Ship it from your GitHub</H2>
        <ol className="space-y-4">
          {[
            { t: 'Copy the project', d: 'Take the dummy-surfers/ folder (multi-module Gradle + CI workflow + README) into your repo root.' },
            { t: 'Push to GitHub', d: 'The included .github/workflows/android.yml runs on every push: it builds a debug APK, a release APK and a Play-ready AAB — all uploaded as artifacts.' },
            { t: 'Install on your phone', d: 'Download dummy-surfers-debug-apk from the Actions run, enable "install unknown apps", install, and play. Portrait, swipe controls, haptics.' },
            { t: 'Optional — Play Store', d: 'Add your signing config + secrets to android/build.gradle.kts, then upload the AAB artifact to Play Console.' },
          ].map((s, i) => (
            <li key={s.t} className="ss-card bg-[#2A3057] p-5 flex gap-4 items-start">
              <span className="w-10 h-10 shrink-0 rounded-xl bg-[#FFC93C] border-b-4 border-[#E09B12] text-[#24316B] font-bold grid place-items-center">{i + 1}</span>
              <div>
                <p className="font-bold uppercase tracking-wide text-white [text-shadow:0_2px_0_rgba(36,49,107,.6)]">{s.t}</p>
                <p className="text-sm text-[#C9CFF2] mt-1 leading-relaxed">{s.d}</p>
              </div>
            </li>
          ))}
        </ol>

        <div className="ss-card ss-slot mt-6 p-5 font-mono text-sm overflow-x-auto">
          <p className="text-[#AAB4E8]"># local desktop preview (any PC with Java 17+)</p>
          <p className="text-[#FFC93C]">./gradlew desktop:run</p>
          <p className="text-[#AAB4E8] mt-3"># build the APK yourself</p>
          <p className="text-[#FFC93C]">./gradlew :android:assembleDebug</p>
        </div>
      </section>

      {/* ── CHANGELOG ───────────────────────────────────────────────── */}
      <section id="changelog" className="max-w-5xl mx-auto w-full px-4 sm:px-10 pb-16 scroll-mt-24">
        <H2>Changelog</H2>
        <div className="space-y-4">
          {changelog.map(rel => (
            <div key={rel.v} className={`ss-card p-5 ${rel.latest ? 'bg-[#7B84D6]' : 'bg-[#9AA3E8]'}`}>
              <div className="flex items-center gap-3 mb-3 flex-wrap">
                <span className="rounded-lg bg-[#FFC93C] border-b-4 border-[#E09B12] px-3 py-1 font-bold text-[#24316B]">{rel.v}</span>
                {rel.latest && (
                  <span className="rounded-full bg-[#FF5A3C] border-2 border-white px-2.5 py-0.5 text-[10px] font-bold uppercase tracking-wider text-white shadow-[0_2px_0_rgba(36,49,107,.35)] [text-shadow:0_1px_0_rgba(36,49,107,.4)]">
                    Latest
                  </span>
                )}
                <span className={`text-sm font-semibold ${rel.latest ? 'text-[#E4E8FC]' : 'text-[#3A4470]'}`}>{rel.date}</span>
              </div>
              <ul className={`space-y-1.5 text-sm leading-relaxed ${rel.latest ? 'text-white' : 'text-[#2A3057]'}`}>
                {rel.items.map(it => <li key={it}>{it}</li>)}
              </ul>
            </div>
          ))}
        </div>
      </section>

      {/* ── STICKY FOOTER (navy) ────────────────────────────────────── */}
      <footer className="mt-auto bg-[#2A3057] border-t-4 border-[#FFC93C]">
        <div
          className="max-w-5xl mx-auto px-4 sm:px-10 pt-6 flex flex-col sm:flex-row items-center justify-between gap-3 text-sm text-[#C9CFF2]"
          style={{ paddingBottom: 'max(1.5rem, env(safe-area-inset-bottom))' }}
        >
          <p className="font-bold uppercase tracking-wide text-[#FFC93C] [text-shadow:0_2px_0_rgba(36,49,107,.8)]">
            Dummy Surfers <span className="text-white">by</span>
            <GraffitiName name="Faisal Khan" className="ml-2 inline-block align-middle normal-case text-[1.55rem] sm:text-[1.85rem]" stroke="#1B2038" shadowFilter="drop-shadow(0 3px 0 rgba(9,12,30,.55))" />
          </p>
          <p>Kotlin • LibGDX • Procedural everything • <span className="text-[#FFD23E] font-semibold">v6.3.0 — The Fast Second Boot!</span></p>
        </div>
      </footer>
    </main>
  )
}

function Cloud({ className = '', delay = 0 }: { className?: string; delay?: number }) {
  return (
    <div aria-hidden className={`ss-cloud pointer-events-none absolute ${className}`} style={{ animationDelay: `${delay}s` }}>
      <div className="relative h-full w-full">
        <span className="absolute bottom-0 left-0 h-1/2 w-full rounded-full bg-white/95" />
        <span className="absolute bottom-[22%] left-[16%] h-[78%] w-[40%] rounded-full bg-white" />
        <span className="absolute bottom-[22%] right-[15%] h-[60%] w-[36%] rounded-full bg-white/90" />
      </div>
    </div>
  )
}

function GraffitiWord({ word, tilt, className = '' }: { word: string; tilt: number; className?: string }) {
  return (
    <span className={`block ${className}`} style={{ transform: `rotate(${tilt}deg)` }}>
      {word.split('').map((ch, i) => (
        <span
          key={`${ch}-${i}`}
          className="inline-block text-[#FFC93C]"
          style={{
            transform: `rotate(${(i % 2 === 0 ? -1 : 1) * (2 + (i % 3))}deg) translateY(${i % 2 === 0 ? 0 : 3}px)`,
            WebkitTextStroke: '2px #24316B',
            textShadow: '0 5px 0 rgba(36,49,107,.9), 0 10px 18px rgba(36,49,107,.35)',
          }}
        >
          {ch}
        </span>
      ))}
    </span>
  )
}

/** Chunky graffiti-styled name — same DNA as the DUMMY SURFERS title:
 *  Fredoka bold, gold gradient fill, navy outline, hard cartoon shadow.
 *  NOTE: shadows use filter:drop-shadow (NOT text-shadow) because with
 *  background-clip:text the fill paints as background, and text-shadow
 *  would paint on top of it and hide the gradient. */
function GraffitiName({
  name,
  className = '',
  stroke = '#24316B',
  shadowFilter = 'drop-shadow(0 4px 0 rgba(36,49,107,.9)) drop-shadow(0 9px 14px rgba(36,49,107,.28))',
}: { name: string; className?: string; stroke?: string; shadowFilter?: string }) {
  return (
    <span
      className={`ss-font inline-block font-bold leading-[1.15] ${className}`}
      aria-label={name}
      role="text"
      style={{ textShadow: 'none' }}
    >
      {name.split('').map((ch, i) =>
        ch === ' ' ? (
          <span key={`sp-${i}`} aria-hidden className="inline-block w-[0.42em]" />
        ) : (
          <span
            key={`${ch}-${i}`}
            aria-hidden
            className="inline-block cursor-default transition-transform duration-150 hover:-translate-y-1"
            style={{
              transform: `rotate(${(i % 2 === 0 ? -1 : 1) * (1.4 + (i % 3) * 0.7)}deg) translateY(${i % 2 === 0 ? 0 : 2}px)`,
              background: 'linear-gradient(180deg,#FFEDAD 0%,#FFC93C 52%,#F5A623 100%)',
              WebkitBackgroundClip: 'text',
              backgroundClip: 'text',
              color: 'transparent',
              WebkitTextStroke: `2px ${stroke}`,
              filter: shadowFilter,
            }}
          >
            {ch}
          </span>
        )
      )}
    </span>
  )
}

function H2({ children }: { children: React.ReactNode }) {
  return (
    <h2 className="mb-6 text-2xl sm:text-4xl font-bold uppercase tracking-wide text-[#24316B] ss-head">
      {children}
    </h2>
  )
}

function Badge({ children }: { children: React.ReactNode }) {
  return (
    <span className="rounded-full bg-[#2A3057]/90 border-2 border-white/70 px-4 py-1.5 text-xs sm:text-sm font-semibold text-white [text-shadow:0_1px_0_rgba(36,49,107,.5)]">
      {children}
    </span>
  )
}
