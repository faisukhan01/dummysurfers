using System.Collections;
using UnityEngine;
using DummySurfer.Audio;
using DummySurfer.Cam;
using DummySurfer.Core;
using DummySurfer.Data;
using DummySurfer.Multiplayer;
using DummySurfer.Player;
using DummySurfer.Utilities;

namespace DummySurfer.Track
{
    /// <summary>
    /// Game-scene orchestrator (single, small responsibility per spec 10 CODE QUALITY):
    /// sets up atmosphere/camera/input, spawns or binds the local runner, drives the
    /// synchronized countdown, starts the run on the shared deterministic seed, tracks
    /// both runners for chunk recycling, and routes death → results → rematch/exit.
    /// </summary>
    public sealed class RunSceneController : MonoBehaviour
    {
        private PlayerController _localPlayer;
        private GameConfig _cfg;
        private GameStateManager _gs;
        private bool _online;
        private bool _runStarted;
        private bool _resultsShown;
        private bool _subscribedMatch;

        private void Start()
        {
            GameBootstrap.EnsureAll();
            _cfg = GameConfig.Runtime;
            _gs = GameStateManager.Ensure();
            ServiceRegistry.Register(this);

            ConfigureAtmosphere();
            CreateCamera();

            _online = MultiplayerManager.IsOnline;
            _gs.IsOnlineRun = _online;

            var inputGo = new GameObject("InputReader");
            var reader = inputGo.AddComponent<SwipeInputReader>();
            reader.SwipedHorizontal += d => _localPlayer?.RequestLane(d);
            reader.SwipedUp += () => _localPlayer?.RequestJump();
            reader.SwipedDown += () => _localPlayer?.RequestSlide();
            reader.PausePressed += TogglePause;

            StartCoroutine(_online ? OnlineFlow() : OfflineFlow());
        }

        private void OnDestroy()
        {
            if (_subscribedMatch)
                GameEvents.MatchPhaseChanged -= OnMatchPhaseChanged;
        }

        // ---------------- flows ----------------

        private IEnumerator OfflineFlow()
        {
            _gs.Set(GameState.Countdown);

            var stats = CharacterSelector.GetSelectedStats();
            var go = Instantiate(RunnerFactory.BuildOfflineRunner(stats));
            _localPlayer = go.GetComponent<PlayerController>();
            _localPlayer.Initialize(stats, isLocal: true);
            _localPlayer.ResetForRun();
            _localPlayer.Died += OnLocalDied;
            BindCamera();

            GameEvents.PublishLocalPlayerSpawned();
            yield return RunLocalCountdown();

            StartRun(SeededRandom.NewSeed());
        }

        private IEnumerator OnlineFlow()
        {
            // Wait for the synchronized scene state (spec 7): match object + local runner.
            float deadline = Time.unscaledTime + 30f;
            while ((MatchStateManager.Instance == null || NetworkPlayerSync.Local == null) &&
                   Time.unscaledTime < deadline)
                yield return null;

            if (MatchStateManager.Instance == null || NetworkPlayerSync.Local == null)
            {
                _gs.SetDisconnected();
                yield break;
            }

            _localPlayer = NetworkPlayerSync.Local.GetComponent<PlayerController>();
            _localPlayer.Died += OnLocalDied;
            BindCamera();
            GameEvents.PublishLocalPlayerSpawned();

            var m = MatchStateManager.Instance;
            if (m.IsServer && (MatchPhase)m.Phase.Value == MatchPhase.None)
                m.ServerStartCountdown(SeededRandom.NewSeed());

            _subscribedMatch = true;
            GameEvents.MatchPhaseChanged += OnMatchPhaseChanged;

            _gs.Set(GameState.Countdown);
            yield return new WaitUntil(() => (MatchPhase)m.Phase.Value == MatchPhase.Running);

            StartRun(m.SharedSeed.Value);
        }

        // ---------------- run control ----------------

        private void StartRun(ulong seed)
        {
            TrackManager.Ensure().BeginRun(seed);
            RunManager.Ensure().BeginRun();
            _localPlayer.SetSimEnabled(true);
            _runStarted = true;
            _resultsShown = false;
            _gs.Set(GameState.Running);
        }

        private IEnumerator RunLocalCountdown()
        {
            int ticks = Mathf.Max(1, Mathf.CeilToInt(_cfg.countdownSeconds));
            for (int i = ticks; i >= 1; i--)
            {
                GameEvents.PublishCountdownTick(i);
                AudioManager.Instance.PlaySfx(SfxId.CountdownTick);
                yield return new WaitForSecondsRealtime(1f);
            }
            GameEvents.PublishCountdownTick(0);
            AudioManager.Instance.PlaySfx(SfxId.Go);
        }

        private void OnLocalDied()
        {
            if (!_online)
            {
                var summary = RunManager.Ensure().EndRun();
                _gs.Set(GameState.Results);
                ServiceRegistry.TryGet<UI.UIManager>(out var ui);
                ui?.ShowOfflineResults(summary);
            }
            // Online: MatchStateManager drives results (window + compare rules).
        }

        private void OnMatchPhaseChanged(MatchPhase phase)
        {
            if (phase == MatchPhase.Results && !_resultsShown)
            {
                _resultsShown = true;
                _localPlayer?.SetSimEnabled(false);
            }
            else if (phase == MatchPhase.Countdown)
            {
                // Rematch: full pooled reset, new seed arrives on countdown completion (spec 3.1 restart).
                StartCoroutine(RematchReset());
            }
        }

        private IEnumerator RematchReset()
        {
            _runStarted = false;
            RunManager.Ensure().ResetForRematch();
            TrackManager.Ensure().ResetAll();
            _localPlayer?.ResetForRun();
            BindCamera();
            _gs.Set(GameState.Countdown);

            yield return new WaitUntil(() =>
                MatchStateManager.Instance != null &&
                (MatchPhase)MatchStateManager.Instance.Phase.Value == MatchPhase.Running);

            StartRun(MatchStateManager.Instance.SharedSeed.Value);
        }

        /// <summary>Offline restart from the results screen — pooled objects are reused (near-instant reset).</summary>
        public void RestartOffline()
        {
            if (_online || _runStarted) return;
            StopAllCoroutines();
            RunManager.Ensure().ResetForRematch();
            StartCoroutine(OfflineRestart());
        }

        private IEnumerator OfflineRestart()
        {
            _gs.Set(GameState.Countdown);
            _localPlayer.ResetForRun();
            BindCamera();
            yield return RunLocalCountdown();
            StartRun(SeededRandom.NewSeed());
        }

        // ---------------- per-frame ----------------

        private float _oppTimer;
        private OpponentInfo _lastOpp;

        private void Update()
        {
            if (_localPlayer == null) return;

            var cam = RunnerCamera.Main;
            if (cam != null)
                cam.SpeedNorm = Mathf.InverseLerp(_cfg.baseSpeed, _cfg.maxSpeed, _localPlayer.Speed);

            var track = TrackManager.Instance;
            if (track != null)
            {
                track.CurrentSpeed = _localPlayer.Speed;
                float remoteZ = _online && NetworkPlayerSync.HasRemote ? NetworkPlayerSync.RemoteZ : float.NegativeInfinity;
                track.SetTargets(_localPlayer.Distance, remoteZ);
            }

            if (_online) PublishOpponentInfo();
        }

        /// <summary>Compact opponent status for the HUD chip (spec 4.2), throttled to ~3 Hz.</summary>
        private void PublishOpponentInfo()
        {
            _oppTimer -= Time.unscaledDeltaTime;
            if (_oppTimer > 0f) return;
            _oppTimer = 0.3f;

            var m = MatchStateManager.Instance;
            if (m == null) return;

            bool iAmHost = MultiplayerManager.IsHost;
            var opp = iAmHost ? m.ClientScore.Value : m.HostScore.Value;
            var info = new OpponentInfo(NetworkPlayerSync.HasRemote, opp.Alive, opp.Distance, opp.Coins);
            if (info.Equals(_lastOpp)) return;
            _lastOpp = info;
            GameEvents.PublishOpponentInfoChanged(info);
        }

        // ---------------- helpers ----------------

        private void BindCamera()
        {
            var cam = RunnerCamera.Main;
            if (cam != null)
            {
                cam.Target = _localPlayer.transform;
                cam.SnapToTarget();
            }
        }

        private void TogglePause()
        {
            if (_gs.Current == GameState.Running)
            {
                _gs.Set(GameState.Paused);
                AudioManager.Instance.PlaySfx(SfxId.UiClick);
            }
            else if (_gs.Current == GameState.Paused)
            {
                _gs.Set(GameState.Running);
                AudioManager.Instance.PlaySfx(SfxId.UiClick);
            }
        }

        private void ConfigureAtmosphere()
        {
            RenderSettings.fog = true;
            RenderSettings.fogMode = FogMode.Linear;
            RenderSettings.fogColor = VisualStyles.FogColor;
            RenderSettings.fogStartDistance = _cfg.fogStart;
            RenderSettings.fogEndDistance = _cfg.fogEnd;
            RenderSettings.ambientLight = new Color(0.45f, 0.5f, 0.6f);
            RenderSettings.ambientMode = UnityEngine.Rendering.AmbientMode.Flat;

            bool hasLight = false;
            foreach (var l in FindObjectsByType<Light>(FindObjectsSortMode.None))
                if (l.type == LightType.Directional) { hasLight = true; break; }
            if (!hasLight)
            {
                var lightGo = new GameObject("Sun");
                var light = lightGo.AddComponent<Light>();
                light.type = LightType.Directional;
                light.color = new Color(1f, 0.93f, 0.82f);
                light.intensity = 1.15f;
                light.shadows = LightShadows.Soft;
                lightGo.transform.rotation = Quaternion.Euler(52f, -30f, 0f);
            }
        }

        private void CreateCamera()
        {
            if (RunnerCamera.Main != null) return;
            var go = new GameObject("RunnerCamera");
            go.tag = "MainCamera";
            var cam = go.AddComponent<Camera>();
            cam.clearFlags = CameraClearFlags.SolidColor;
            cam.backgroundColor = VisualStyles.SkyTop;
            cam.nearClipPlane = 0.1f;
            cam.farClipPlane = 260f;
            go.AddComponent<AudioListener>();
            go.AddComponent<RunnerCamera>();
        }
    }
}
