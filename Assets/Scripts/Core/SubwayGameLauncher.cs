using System.Collections;
using UnityEngine;
using UnityEngine.InputSystem;
using UnityEngine.SceneManagement;
using DummySurfer.Audio;
using DummySurfer.Cam;
using DummySurfer.Core;
using DummySurfer.Data;
using DummySurfer.Player;
using DummySurfer.Track;
using DummySurfer.UI;
using DummySurfer.Utilities;

namespace DummySurfer.Core
{
    /// <summary>
    /// Subway-mode orchestrator: the game IS the app. Launches straight into a bright,
    /// alive subway world — "TAP TO PLAY" → instant GO → run → busted → instant retry.
    /// ZERO dependency on the legacy menu/lobby/network stack: every manager is ensured
    /// defensively, the legacy canvas is retired, and the run AUTO-STARTS after a few
    /// seconds even if input or UI totally fail on a device. Frozen screens are impossible.
    /// </summary>
    public sealed class SubwayGameLauncher : MonoBehaviour
    {
        private enum Mode { Ready, Starting, Running, Paused, Dead }

        private Mode _mode = Mode.Ready;

        private PlayerController _player;
        private GuardChaser _guard;
        private SubwayHud _hud;
        private SwipeInputReader _reader;
        private GameConfig _cfg;
        private GameStateManager _gs;
        private float _readyDeadline;
        private float _deathTime;

        private void OnDestroy()
        {
            // HUD canvas is DontDestroyOnLoad — it must not outlive its launcher,
            // otherwise stale screens stack up when the Game scene reloads.
            if (_hud != null) Destroy(_hud.gameObject);
        }

        private static void Try(System.Action a, string what)
        {
            try { a(); }
            catch (System.Exception e) { Debug.LogWarning($"[Subway] init '{what}' failed (game continues): {e.Message}"); }
        }

        private void Start()
        {
            Time.timeScale = 1f;
            _cfg = GameConfig.Runtime;

            // Defensive manager stack — each piece independent; one failure can't freeze the game.
            Try(() => GameBootstrap.EnsureAll(), "bootstrap");
            Try(RetireLegacyUi, "retire legacy UI");
            Try(() => _gs = GameStateManager.Ensure(), "gameState");
            Try(() => RunManager.Ensure(), "runManager");
            Try(() => DifficultyManager.Ensure(), "difficulty");
            Try(() => PoolManager.Ensure(), "pools");
            Try(() => AudioManager.Ensure(), "audio");
            Try(() => TrackManager.Ensure(), "track");

            ConfigureAtmosphere();
            EnsureCamera();
            BuildInput();

            // World immediately visible behind the start overlay
            Try(() => TrackManager.Ensure().BeginRun(SeededRandom.NewSeed()), "world");

            BuildPlayer();

            Try(() =>
            {
                _hud = SubwayHud.Create();
                _hud.StartTapped = () => { if (_mode == Mode.Ready) BeginStartSequence(); };
                _hud.PauseRequested = TogglePause;
                _hud.ResumeRequested = Resume;
                _hud.RestartRequested = Restart;
                _hud.MenuRequested = GoToMenu;
            }, "hud");

            var am = AudioManager.Instance;
            if (am != null) Try(() => am.PlayMusic(MusicId.Menu), "menuMusic");

            EnterReady();
        }

        // ---------------- setup ----------------

        /// <summary>The legacy lobby/menu canvas fights the subway HUD — retire it in this mode.</summary>
        private static void RetireLegacyUi()
        {
            var legacy = FindFirstObjectByType<UI.UIManager>();
            if (legacy != null) Destroy(legacy.gameObject);
        }

        private static void ConfigureAtmosphere()
        {
            RenderSettings.fog = true;
            RenderSettings.fogMode = FogMode.Linear;
            RenderSettings.fogColor = VisualStyles.FogColor;
            RenderSettings.fogStartDistance = 60f;
            RenderSettings.fogEndDistance = 170f;
            RenderSettings.ambientMode = UnityEngine.Rendering.AmbientMode.Flat;
            RenderSettings.ambientLight = new Color(0.62f, 0.72f, 0.82f);

            bool hasLight = false;
            foreach (var l in FindObjectsByType<Light>(FindObjectsSortMode.None))
            {
                if (l.type == LightType.Directional)
                {
                    hasLight = true;
                    l.color = new Color(1f, 0.96f, 0.88f);
                    l.intensity = Mathf.Max(l.intensity, 1.25f);
                }
            }
            if (!hasLight)
            {
                var sunGo = new GameObject("Sun");
                var sun = sunGo.AddComponent<Light>();
                sun.type = LightType.Directional;
                sun.color = new Color(1f, 0.96f, 0.88f);
                sun.intensity = 1.3f;
                sun.shadows = LightShadows.Soft;
                sunGo.transform.rotation = Quaternion.Euler(48f, -32f, 0f);
            }
        }

        private static void EnsureCamera()
        {
            if (RunnerCamera.Main != null)
            {
                var cam0 = RunnerCamera.Main.GetComponent<Camera>();
                if (cam0 != null) cam0.backgroundColor = VisualStyles.SkyTop;
                return;
            }
            var go = new GameObject("RunnerCamera");
            go.tag = "MainCamera";
            var cam = go.AddComponent<Camera>();
            cam.clearFlags = CameraClearFlags.SolidColor;
            cam.backgroundColor = VisualStyles.SkyTop;
            cam.nearClipPlane = 0.1f;
            cam.farClipPlane = 300f;
            go.AddComponent<AudioListener>();
            go.AddComponent<RunnerCamera>();
        }

        private void BuildInput()
        {
            var inputGo = new GameObject("SubwayInput");
            _reader = inputGo.AddComponent<SwipeInputReader>();
            _reader.SwipedHorizontal += d => { if (_mode == Mode.Ready) BeginStartSequence(); else _player?.RequestLane(d); };
            _reader.SwipedUp += () => { if (_mode == Mode.Ready) BeginStartSequence(); else _player?.RequestJump(); };
            _reader.SwipedDown += () => { if (_mode == Mode.Ready) BeginStartSequence(); else _player?.RequestSlide(); };
            _reader.PausePressed += () =>
            {
                if (_mode == Mode.Running) Pause();
                else if (_mode == Mode.Paused) Resume();
            };
        }

        private void BuildPlayer()
        {
            var stats = CharacterSelector.GetSelectedStats();
            var go = RunnerFactory.BuildOfflineRunner(stats);
            _player = go.GetComponent<PlayerController>();
            _player.Initialize(stats, isLocal: true);
            _player.ResetForRun();
            _player.Died += OnPlayerDied;
            _player.Stumbled += OnPlayerStumbled;

            _guard = GuardChaser.Create(_player.transform);
            _guard.EnterReady();

            var cam = RunnerCamera.Main;
            if (cam != null) { cam.Target = _player.transform; cam.SnapToTarget(); }
        }

        // ---------------- states ----------------

        private void EnterReady()
        {
            _mode = Mode.Ready;
            _readyDeadline = Time.unscaledTime + 3.5f;   // motion is guaranteed even with zero input
            if (_player != null) { _player.ResetForRun(); _player.SetSimEnabled(false); }
            if (_guard != null) _guard.EnterReady();
            if (_hud != null) _hud.ShowStart();
            Try(() => _gs?.Set(GameState.Menu), "gsMenu");
        }

        private void BeginStartSequence()
        {
            if (_mode != Mode.Ready) return;
            _mode = Mode.Starting;
            _hud?.ShowGo("GET READY…");
            var am = AudioManager.Instance;
            if (am != null) Try(() => am.PlaySfx(SfxId.UiClick), "startSfx");
            StartCoroutine(GoSequence());
        }

        private IEnumerator GoSequence()
        {
            yield return new WaitForSecondsRealtime(0.85f);
            StartRun();
        }

        private void StartRun()
        {
            _mode = Mode.Running;
            Try(() => RunManager.Ensure().BeginRun(), "beginRun");
            Try(() => TrackManager.Ensure().BeginRun(SeededRandom.NewSeed()), "worldRun");
            _player?.SetSimEnabled(true);
            _guard?.RunStart();
            _hud?.ShowHud();
            var run = RunManager.Instance;
            if (run != null) _hud?.SetBest(run.BestDistance);
            Try(() => _gs?.Set(GameState.Running), "gsRunning");
        }

        private void Pause()
        {
            if (_mode != Mode.Running) return;
            _mode = Mode.Paused;
            Time.timeScale = 0f;
            _hud?.ShowPause();
            Try(() => _gs?.Set(GameState.Paused), "gsPaused");
        }

        private void Resume()
        {
            if (_mode != Mode.Paused) return;
            _mode = Mode.Running;
            Time.timeScale = 1f;
            _hud?.ShowHud();
            Try(() => _gs?.Set(GameState.Running), "gsResume");
        }

        public void Restart()
        {
            Time.timeScale = 1f;
            Try(() => RunManager.Ensure().ResetForRematch(), "resetRun");
            Try(() => TrackManager.Ensure().ResetAll(), "resetWorld");
            _player?.ResetForRun();
            var cam = RunnerCamera.Main;
            if (cam != null && _player != null) { cam.Target = _player.transform; cam.SnapToTarget(); }
            _mode = Mode.Ready;
            BeginStartSequence();
        }

        private void GoToMenu()
        {
            Time.timeScale = 1f;
            SceneManager.LoadScene(Constants.SceneMenu);
        }

        private void OnPlayerStumbled()
        {
            _guard?.OnPlayerStumbled();
        }

        private void OnPlayerDied()
        {
            if (_mode == Mode.Dead) return;
            _mode = Mode.Dead;
            _deathTime = Time.unscaledTime;
            _guard?.Grab();

            var summary = new RunSummary();
            Try(() => summary = RunManager.Ensure().EndRun(), "endRun");
            _hud?.ShowResults(summary);
            Try(() => _gs?.Set(GameState.Results), "gsResults");

            var am = AudioManager.Instance;
            if (am != null) Try(() => am.PlaySfx(SfxId.Lose), "loseSfx");
        }

        // ---------------- per-frame ----------------

        private void Update()
        {
            // Guaranteed motion: never let the game sit still at start.
            if (_mode == Mode.Ready && Time.unscaledTime >= _readyDeadline)
                BeginStartSequence();

            // Tap anywhere: start (Ready) / quick retry (Dead after a beat)
            if (_mode == Mode.Ready && AnyPress()) { BeginStartSequence(); }
            else if (_mode == Mode.Dead && Time.unscaledTime > _deathTime + 1.1f && AnyPress())
            { Restart(); return; }

            // Feed the world/camera/HUD
            if (_player != null)
            {
                var cam = RunnerCamera.Main;
                if (cam != null)
                    cam.SpeedNorm = Mathf.InverseLerp(_cfg.baseSpeed, _cfg.maxSpeed, _player.Speed);

                Try(() =>
                {
                    var track = TrackManager.Instance;
                    if (track != null)
                    {
                        track.CurrentSpeed = _player.Speed;
                        track.SetTargets(_player.Distance, float.NegativeInfinity);
                    }
                }, "trackFeed");
            }

            var run = RunManager.Instance;
            if (run != null && _mode == Mode.Running)
            {
                _hud?.SetScore(Mathf.FloorToInt(run.Distance));
                _hud?.SetCoins(run.Coins);
            }
        }

        private static bool AnyPress()
        {
            try
            {
                var touch = Touchscreen.current?.primaryTouch;
                if (touch != null && touch.press.wasPressedThisFrame) return true;
                var mouse = Mouse.current;
                if (mouse != null && mouse.leftButton.wasPressedThisFrame) return true;
                var kb = Keyboard.current;
                if (kb != null && (kb.spaceKey.wasPressedThisFrame || kb.enterKey.wasPressedThisFrame)) return true;
            }
            catch { /* input system not ready — ignore */ }
            return false;
        }
    }
}
