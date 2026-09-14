using System;
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.UI;
using DummySurfer.Core;
using DummySurfer.Utilities;

namespace DummySurfer.UI
{
    /// <summary>
    /// Subway-mode HUD: fully self-contained canvas built defensively at runtime.
    /// Deliberately NOT part of the legacy UIManager screen stack — every element is
    /// wrapped so that even if fonts/shaders/USS warmup misbehave on a device, the
    /// game still shows *something* and remains fully playable (score → results → restart).
    /// </summary>
    public sealed class SubwayHud : MonoBehaviour
    {
        public Func<bool> StartTapped;        // full-screen tap catcher (start state)
        public Action RestartRequested;
        public Action ResumeRequested;
        public Action MenuRequested;

        private Canvas _canvas;
        private Text _score, _coins, _best, _big, _sub;
        private GameObject _startRoot, _hudRoot, _pauseRoot, _resultsRoot;
        private Text _rScore, _rCoins, _rBest, _rNewBest;
        private RectTransform _root;
        private float _pulse;

        public static SubwayHud Create()
        {
            var go = new GameObject("SubwayHud");
            var hud = go.AddComponent<SubwayHud>();
            hud.Build();
            return hud;
        }

        // ---------------- construction ----------------

        private void Build()
        {
            Try(() =>
            {
                var go = new GameObject("SubwayHudCanvas");
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
                    var es = new GameObject("SubwayEventSystem");
                    es.AddComponent<EventSystem>();
                    try { es.AddComponent<UnityEngine.InputSystem.UI.InputSystemUIInputModule>(); }
                    catch { es.AddComponent<StandaloneInputModule>(); }
                    DontDestroyOnLoad(es);
                }

                var safe = UICanvasBuilder.Rect(go.transform, "SafeArea", Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);
                safe.gameObject.AddComponent<SafeAreaFitter>();
                _root = safe;
            }, "canvas");
        }

        private static void Try(Action a, string what)
        {
            try { a(); }
            catch (Exception e) { Debug.LogWarning($"[SubwayHud] build '{what}' failed (game continues): {e.Message}"); }
        }

        private Text MakeText(Transform parent, string name, string content, int size, Color color,
            Vector2 anchor, Vector2 pos, Vector2 dims, bool bold = true, TextAnchor align = TextAnchor.MiddleCenter)
        {
            Text t = null;
            Try(() => { t = UICanvasBuilder.Text(parent, name, content, size, color, align, anchor, pos, dims, bold); }, "text:" + name);
            return t;
        }

        private Button MakeButton(Transform parent, string label, Vector2 pos, Vector2 size,
            Color bg, Color fg, int fontSize, Action onClick)
        {
            Button b = null;
            Try(() => { b = UICanvasBuilder.Button(parent, label, pos, size, bg, fg, fontSize, () => onClick?.Invoke()); }, "btn:" + label);
            return b;
        }

        // ---------------- screens ----------------

        public void ShowStart()
        {
            HideAll();
            Try(() =>
            {
                if (_startRoot == null)
                {
                    _startRoot = UICanvasBuilder.Rect(_root, "StartRoot", Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero).gameObject;

                    // full-screen invisible tap catcher — starts the game even if every text failed to build
                    var catcher = _startRoot.AddComponent<Image>();
                    catcher.color = new Color(0f, 0f, 0f, 0.001f);
                    var btn = _startRoot.AddComponent<Button>();
                    btn.transition = Selectable.Transition.None;
                    btn.onClick.AddListener(() => StartTapped?.Invoke());

                    var dim = UICanvasBuilder.StretchPanel(_startRoot.transform, "Dim", new Color(0f, 0.05f, 0.12f, 0.25f));
                    dim.raycastTarget = false;

                    MakeText(_startRoot.transform, "Title1", "DUMMY", 130, VisualStyles.TextPrimary,
                        new Vector2(0.5f, 0.5f), new Vector2(0f, 420f), new Vector2(980f, 170f));
                    MakeText(_startRoot.transform, "Title2", "SURFERS", 130, VisualStyles.Primary,
                        new Vector2(0.5f, 0.5f), new Vector2(0f, 280f), new Vector2(980f, 170f));
                    MakeText(_startRoot.transform, "Tagline", "dodge the trains · grab the coins · outrun the inspector",
                        34, VisualStyles.TextMuted, new Vector2(0.5f, 0.5f), new Vector2(0f, 190f), new Vector2(1000f, 60f), bold: false);

                    _big = MakeText(_startRoot.transform, "TapToPlay", "TAP TO PLAY", 72, VisualStyles.Gold,
                        new Vector2(0.5f, 0.5f), new Vector2(0f, -60f), new Vector2(900f, 120f));

                    var run = RunManager.Instance;
                    MakeText(_startRoot.transform, "BestHint",
                        run != null && run.BestDistance > 0 ? $"BEST  {run.BestDistance} m" : "swipe to move · jump · roll",
                        38, VisualStyles.TextPrimary, new Vector2(0.5f, 0.5f), new Vector2(0f, -190f), new Vector2(900f, 70f));

                    MakeText(_startRoot.transform, "Controls",
                        "swipe  =  lanes   |   swipe up  =  jump   |   swipe down  =  roll",
                        26, VisualStyles.TextMuted, new Vector2(0.5f, 0f), new Vector2(0f, -640f), new Vector2(1040f, 50f), bold: false);
                }
                _startRoot.SetActive(true);
                RefreshBestHint();
            }, "ShowStart");
        }

        private void RefreshBestHint()
        {
            Try(() =>
            {
                var hint = _startRoot != null && _startRoot.transform.Find("BestHint") != null
                    ? _startRoot.transform.Find("BestHint").GetComponent<Text>() : null;
                var run = RunManager.Instance;
                if (hint != null && run != null)
                    hint.text = run.BestDistance > 0 ? $"BEST  {run.BestDistance} m" : "swipe to move · jump · roll";
            }, "bestHint");
        }

        public void ShowGo(string msg)
        {
            HideAll();
            Try(() =>
            {
                if (_hudRoot == null) BuildHudRoot();
                _hudRoot.SetActive(true);
                _sub.gameObject.SetActive(true);
                _sub.text = msg;
                _sub.fontSize = 110;
                _sub.color = VisualStyles.Gold;
            }, "ShowGo");
        }

        public void ShowHud()
        {
            HideAll();
            Try(() =>
            {
                if (_hudRoot == null) BuildHudRoot();
                _hudRoot.SetActive(true);
                if (_sub != null) _sub.gameObject.SetActive(false);
            }, "ShowHud");
        }

        private void BuildHudRoot()
        {
            _hudRoot = UICanvasBuilder.Rect(_root, "HudRoot", Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero).gameObject;

            _score = MakeText(_hudRoot.transform, "Score", "0", 78, VisualStyles.TextPrimary,
                new Vector2(0.5f, 1f), new Vector2(0f, -110f), new Vector2(600f, 110f));
            Try(() =>
            {
                var o = _score.gameObject.AddComponent<Outline>();
                o.effectColor = new Color(0f, 0f, 0f, 0.55f);
                o.effectDistance = new Vector2(3f, -3f);
            }, "scoreOutline");

            _coins = MakeText(_hudRoot.transform, "Coins", "0", 52, VisualStyles.Gold,
                new Vector2(0.5f, 1f), new Vector2(0f, -195f), new Vector2(600f, 70f));
            Try(() =>
            {
                var o = _coins.gameObject.AddComponent<Outline>();
                o.effectColor = new Color(0f, 0f, 0f, 0.55f);
                o.effectDistance = new Vector2(2f, -2f);
            }, "coinsOutline");

            _best = MakeText(_hudRoot.transform, "Best", "", 34, new Color(1f, 1f, 1f, 0.85f),
                new Vector2(0.5f, 1f), new Vector2(0f, -250f), new Vector2(600f, 50f), bold: false);

            _sub = MakeText(_hudRoot.transform, "Sub", "", 110, VisualStyles.Gold,
                new Vector2(0.5f, 0.5f), new Vector2(0f, 60f), new Vector2(900f, 200f));

            MakeButton(_hudRoot.transform, "II", new Vector2(470f, -110f), new Vector2(110f, 110f),
                new Color(0f, 0f, 0f, 0.35f), VisualStyles.TextPrimary, 48, () => PauseRequested?.Invoke());
        }

        public Action PauseRequested { get; set; }

        public void ShowPause()
        {
            HideAll();
            Try(() =>
            {
                if (_pauseRoot == null)
                {
                    _pauseRoot = UICanvasBuilder.Rect(_root, "PauseRoot", Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero).gameObject;
                    var dim = UICanvasBuilder.StretchPanel(_pauseRoot.transform, "Dim", new Color(0.01f, 0.05f, 0.1f, 0.72f));
                    dim.raycastTarget = true;

                    MakeText(_pauseRoot.transform, "Title", "PAUSED", 96, VisualStyles.TextPrimary,
                        new Vector2(0.5f, 0.5f), new Vector2(0f, 300f), new Vector2(900f, 140f));

                    MakeButton(_pauseRoot.transform, "RESUME", new Vector2(0f, 60f), new Vector2(560f, 120f),
                        VisualStyles.Primary, VisualStyles.Background, 52, () => ResumeRequested?.Invoke());
                    MakeButton(_pauseRoot.transform, "RESTART", new Vector2(0f, -80f), new Vector2(560f, 110f),
                        VisualStyles.Surface, VisualStyles.TextPrimary, 44, () => RestartRequested?.Invoke());
                    MakeButton(_pauseRoot.transform, "MAIN MENU", new Vector2(0f, -210f), new Vector2(560f, 110f),
                        VisualStyles.Surface, VisualStyles.TextPrimary, 44, () => MenuRequested?.Invoke());
                }
                _pauseRoot.SetActive(true);
            }, "ShowPause");
        }

        public void ShowResults(RunSummary s)
        {
            HideAll();
            Try(() =>
            {
                if (_resultsRoot == null)
                {
                    _resultsRoot = UICanvasBuilder.Rect(_root, "ResultsRoot", Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero).gameObject;
                    var dim = UICanvasBuilder.StretchPanel(_resultsRoot.transform, "Dim", new Color(0.01f, 0.05f, 0.1f, 0.6f));
                    dim.raycastTarget = true;

                    var card = UICanvasBuilder.Panel(_resultsRoot.transform, "Card",
                        new Vector2(0.5f, 0.5f), new Vector2(0f, 40f), new Vector2(880f, 980f), VisualStyles.Background);
                    card.raycastTarget = true;

                    var cardT = card.transform;
                    MakeText(cardT, "Busted", "BUSTED!", 104, VisualStyles.Danger,
                        new Vector2(0.5f, 1f), new Vector2(0f, -140f), new Vector2(800f, 140f));
                    _rNewBest = MakeText(cardT, "NewBest", "NEW BEST!", 48, VisualStyles.Gold,
                        new Vector2(0.5f, 1f), new Vector2(0f, -240f), new Vector2(800f, 70f));
                    _rScore = MakeText(cardT, "ScoreVal", "0 m", 92, VisualStyles.TextPrimary,
                        new Vector2(0.5f, 1f), new Vector2(0f, -390f), new Vector2(800f, 130f));
                    _rCoins = MakeText(cardT, "CoinsVal", "0", 56, VisualStyles.Gold,
                        new Vector2(0.5f, 1f), new Vector2(0f, -490f), new Vector2(800f, 80f));
                    _rBest = MakeText(cardT, "BestVal", "", 38, VisualStyles.TextMuted,
                        new Vector2(0.5f, 1f), new Vector2(0f, -570f), new Vector2(800f, 60f), bold: false);

                    MakeButton(cardT, "RUN AGAIN", new Vector2(0f, -700f), new Vector2(640f, 130f),
                        VisualStyles.Primary, VisualStyles.Background, 54, () => RestartRequested?.Invoke());
                    MakeButton(cardT, "MAIN MENU", new Vector2(0f, -850f), new Vector2(640f, 104f),
                        VisualStyles.Surface, VisualStyles.TextPrimary, 40, () => MenuRequested?.Invoke());

                    // decorative only — tap-to-retry is polled directly by SubwayGameLauncher
                    // (a raycast catcher here would swallow the buttons below it)
                    var catcher = _resultsRoot.AddComponent<Image>();
                    catcher.color = new Color(0f, 0f, 0f, 0f);
                    catcher.raycastTarget = false;
                }
                _resultsRoot.SetActive(true);

                if (_rScore != null) _rScore.text = $"{s.Distance} m";
                if (_rCoins != null) _rCoins.text = $"{s.Coins}  coins";
                if (_rBest != null) _rBest.text = $"best  {s.BestDistance} m  ·  {s.BestCoins} coins";
                if (_rNewBest != null) _rNewBest.gameObject.SetActive(s.NewBest);
            }, "ShowResults");
        }

        public GameObject ResultsRoot => _resultsRoot;

        public void HideAll()
        {
            if (_startRoot != null) _startRoot.SetActive(false);
            if (_hudRoot != null) _hudRoot.SetActive(false);
            if (_pauseRoot != null) _pauseRoot.SetActive(false);
            if (_resultsRoot != null) _resultsRoot.SetActive(false);
        }

        // ---------------- live values ----------------

        public void SetScore(int meters)
        {
            if (_score != null && _hudRoot != null && _hudRoot.activeSelf) _score.text = meters.ToString();
        }

        public void SetCoins(int coins)
        {
            if (_coins != null && _hudRoot != null && _hudRoot.activeSelf) _coins.text = coins.ToString();
        }

        public void SetBest(int bestMeters)
        {
            if (_best != null) _best.text = bestMeters > 0 ? $"BEST {bestMeters} m" : "";
        }

        private void Update()
        {
            _pulse += Time.unscaledDeltaTime;
            if (_big != null && _startRoot != null && _startRoot.activeSelf)
            {
                float a = 0.62f + 0.38f * Mathf.Sin(_pulse * 4.2f);
                _big.color = new Color(VisualStyles.Gold.r, VisualStyles.Gold.g, VisualStyles.Gold.b, a);
            }
        }
    }
}
