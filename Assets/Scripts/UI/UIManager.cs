using System;
using System.Collections;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.SceneManagement;
using UnityEngine.UI;
using DummySurfer.Core;
using DummySurfer.Multiplayer;
using DummySurfer.Utilities;

namespace DummySurfer.UI
{
    /// <summary>
    /// Owns the single runtime-built canvas and routes game states to screens
    /// (spec 5.2 UIManager). All screens are constructed from code — no UI prefabs needed.
    /// </summary>
    public sealed class UIManager : PersistentManager<UIManager>
    {
        private Canvas _canvas;
        private Text _toast;
        private readonly Dictionary<Type, UIScreen> _screens = new Dictionary<Type, UIScreen>(12);
        private UIScreen _current;
        private Coroutine _toastRoutine;

        public event Action<GameState> StateScreenShown;

        protected override void OnManagerAwake()
        {
            ServiceRegistry.Register(this);
            BuildCanvas();
            BuildScreens();

            GameEvents.GameStateChanged += OnGameStateChanged;
            GameEvents.ToastRequested += ShowToast;
            MultiplayerManager.Ensure();
            GameEvents.MultiplayerError += OnMpError;
        }

        protected override void OnDestroy()
        {
            GameEvents.GameStateChanged -= OnGameStateChanged;
            GameEvents.ToastRequested -= ShowToast;
            GameEvents.MultiplayerError -= OnMpError;
            base.OnDestroy();
        }

        private void BuildCanvas()
        {
            var go = new GameObject("UIRoot");
            DontDestroyOnLoad(go);

            _canvas = go.AddComponent<Canvas>();
            _canvas.renderMode = RenderMode.ScreenSpaceOverlay;
            var scaler = go.AddComponent<CanvasScaler>();
            scaler.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
            scaler.referenceResolution = new Vector2(1080f, 1920f);
            scaler.matchWidthOrHeight = 0.5f;
            go.AddComponent<GraphicRaycaster>();

            if (EventSystem.current == null)
            {
                var es = new GameObject("EventSystem");
                es.AddComponent<EventSystem>();
                try { es.AddComponent<UnityEngine.InputSystem.UI.InputSystemUIInputModule>(); }
                catch { es.AddComponent<StandaloneInputModule>(); }
                DontDestroyOnLoad(es);
            }

            var safeRoot = UICanvasBuilder.Rect(go.transform, "SafeArea", Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);
            safeRoot.gameObject.AddComponent<SafeAreaFitter>();
            CanvasRoot = safeRoot;
        }

        public RectTransform CanvasRoot { get; private set; }

        private void BuildScreens()
        {
            void Add<T>(GameState bind = GameState.Boot, bool bindState = false) where T : UIScreen
            {
                var rt = UICanvasBuilder.Rect(CanvasRoot, typeof(T).Name, Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);
                var screen = (T)((RectTransform)rt).gameObject.AddComponent<T>();
                screen.Init(this, rt);
                _screens[typeof(T)] = screen;
                if (bindState) _stateMap[bind] = screen;
            }

            Add<BootScreen>(GameState.Boot, true);
            Add<MainMenuScreen>(GameState.Menu, true);
            Add<MultiplayerScreen>();
            Add<LobbyScreen>(GameState.Lobby, true);
            Add<CountdownScreen>(GameState.Countdown, true);
            Add<HudScreen>(GameState.Running, true);
            Add<PauseScreen>(GameState.Paused, true);
            Add<ResultsScreen>(GameState.Results, true);
            Add<DisconnectScreen>(GameState.Disconnected, true);
            Add<SettingsScreen>();
            Add<CharacterScreen>();
        }

        private readonly Dictionary<GameState, UIScreen> _stateMap = new Dictionary<GameState, UIScreen>(8);

        public T Get<T>() where T : UIScreen => _screens.TryGetValue(typeof(T), out var s) ? s as T : null;

        public void Show<T>() where T : UIScreen
        {
            var target = Get<T>();
            if (target == null) return;
            if (_current == target) return;
            _current?.HideInstant();
            _current = target;
            target.Show();
        }

        private void OnGameStateChanged(GameState state)
        {
            if (state == GameState.Menu)
            {
                // Menu is the default; overlays handled by buttons.
            }
            if (_stateMap.TryGetValue(state, out var screen) && screen != null)
            {
                ShowScreenDirect(screen);
            }
        }

        private void ShowScreenDirect(UIScreen screen)
        {
            if (_current == screen) { screen.Show(); return; }
            _current?.HideInstant();
            _current = screen;
            screen.Show();
        }

        /// <summary>Manual overlay navigation (Settings / Character / Multiplayer panels).</summary>
        public void OpenOverlay<T>() where T : UIScreen => Show<T>();

        public void CloseOverlayToMenu()
        {
            if (_stateMap.TryGetValue(GameState.Menu, out var menu) && menu != null)
                ShowScreenDirect(menu);
        }

        // ---------- offline results ----------

        public void ShowOfflineResults(RunSummary summary)
        {
            var results = Get<ResultsScreen>();
            if (results == null) return;
            results.ConfigureOffline(summary);
            ShowScreenDirect(results);
        }

        // ---------- toasts ----------

        public void ShowToast(string message)
        {
            if (string.IsNullOrEmpty(message)) return;
            if (_toast == null)
            {
                _toast = UICanvasBuilder.Text(CanvasRoot, "Toast", message, 36, VisualStyles.TextPrimary,
                    TextAnchor.MiddleCenter, new Vector2(0.5f, 0.08f), Vector2.zero, new Vector2(920f, 120f));
                _toast.gameObject.AddComponent<UnityEngine.UI.Outline>().effectColor = new Color(0f, 0f, 0f, 0.6f);
            }
            _toast.text = message;
            _toast.color = VisualStyles.TextPrimary;
            if (_toastRoutine != null) StopCoroutine(_toastRoutine);
            _toastRoutine = StartCoroutine(FadeToast(2.6f));
        }

        private IEnumerator FadeToast(float hold)
        {
            yield return new WaitForSecondsRealtime(hold);
            float t = 0f;
            while (t < 0.5f)
            {
                t += Time.unscaledDeltaTime;
                _toast.color = Color.Lerp(VisualStyles.TextPrimary, new Color(0f, 0f, 0f, 0f), t / 0.5f);
                yield return null;
            }
        }

        private void OnMpError(string message) => ShowToast(message);
    }
}
