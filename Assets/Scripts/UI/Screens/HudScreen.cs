using UnityEngine;
using DummySurfer.Core;
using DummySurfer.Multiplayer;
using DummySurfer.Powerups;
using DummySurfer.Utilities;

using UnityEngine.UI;
namespace DummySurfer.UI
{
    /// <summary>
    /// In-run HUD (spec 4.2): distance, coins, pause, compact opponent status, power-up badges.
    /// Text updates are event-driven only (spec 10 PERFORMANCE: minimized UI redraws).
    /// </summary>
    public sealed class HudScreen : UIScreen
    {
        private Text _distance, _coins, _opponent, _fps, _badges;
        private int _lastDist = -1, _lastCoins = -1;
        private OpponentInfo _lastOpponent;
        private float _fpsTimer;

        protected override void Build()
        {
            UICanvasBuilder.StretchPanel(Root, "Bg", new Color(0f, 0f, 0f, 0f));

            _distance = AddText("0 m", 76, VisualStyles.TextPrimary, new Vector2(-260f, -120f), new Vector2(480f, 110f), TextAnchor.MiddleLeft, bold: true);
            _coins = AddText("0", 76, VisualStyles.Gold, new Vector2(260f, -120f), new Vector2(480f, 110f), TextAnchor.MiddleRight, bold: true);
            AddText("DISTANCE", 26, VisualStyles.TextMuted, new Vector2(-260f, -195f), new Vector2(480f, 40f), TextAnchor.MiddleLeft);
            AddText("COINS", 26, VisualStyles.TextMuted, new Vector2(260f, -195f), new Vector2(480f, 40f), TextAnchor.MiddleRight);

            var chip = UICanvasBuilder.Panel(Root, "OpponentChip", new Vector2(0.5f, 1f), new Vector2(0f, -70f), new Vector2(430f, 84f), new Color(0f, 0f, 0f, 0.45f));
            _opponent = UICanvasBuilder.Text(chip.transform, "OpponentText", "", 32, VisualStyles.TextPrimary,
                TextAnchor.MiddleCenter, new Vector2(0.5f, 0.5f), Vector2.zero, new Vector2(-20f, -10f), true);

            _badges = AddText("", 34, VisualStyles.Accent, new Vector2(-320f, -330f), new Vector2(460f, 160f), TextAnchor.UpperLeft, bold: true);

            UICanvasBuilder.Button(Root, "II", new Vector2(430f, -260f), new Vector2(96f, 96f),
                new Color(0f, 0f, 0f, 0.45f), VisualStyles.TextPrimary, 40, Pause);

            _fps = AddText("", 28, VisualStyles.TextMuted, new Vector2(440f, 120f), new Vector2(200f, 50f), TextAnchor.MiddleRight);
        }

        protected override void OnShown()
        {
            GameEvents.DistanceChanged += OnDistance;
            GameEvents.CoinsChanged += OnCoins;
            GameEvents.OpponentInfoChanged += OnOpponent;
            GameEvents.PowerupStarted += OnPowerupStarted;
            GameEvents.PowerupEnded += OnPowerupEnded;
            GameEvents.ShieldConsumed += OnShieldConsumed;
            _lastDist = -1;
            _lastCoins = -1;
            _badges.text = "";
            _opponent.text = "";
        }

        public override void HideInstant()
        {
            GameEvents.DistanceChanged -= OnDistance;
            GameEvents.CoinsChanged -= OnCoins;
            GameEvents.OpponentInfoChanged -= OnOpponent;
            GameEvents.PowerupStarted -= OnPowerupStarted;
            GameEvents.PowerupEnded -= OnPowerupEnded;
            GameEvents.ShieldConsumed -= OnShieldConsumed;
            base.HideInstant();
        }

        private void OnDistance(int meters)
        {
            if (meters == _lastDist) return;
            _lastDist = meters;
            _distance.text = $"{meters} m";
        }

        private void OnCoins(int coins)
        {
            if (coins == _lastCoins) return;
            _lastCoins = coins;
            _coins.text = coins.ToString();
        }

        private void OnOpponent(OpponentInfo info)
        {
            _lastOpponent = info;
            if (!info.Connected) { _opponent.text = ""; return; }
            _opponent.text = info.Alive
                ? $"RIVAL  {info.Distance} m"
                : "RIVAL  DOWN — SURVIVE!";
            _opponent.color = info.Alive ? VisualStyles.TextPrimary : VisualStyles.Danger;
        }

        private void OnPowerupStarted(PowerupType type, float duration)
        {
            if (type == PowerupType.Magnet) _badges.text = AppendBadge(_badges.text, "MAGNET");
            if (type == PowerupType.Multiplier) _badges.text = AppendBadge(_badges.text, "2x COINS");
            if (type == PowerupType.Shield) _badges.text = AppendBadge(_badges.text, "SHIELD");
        }

        private void OnPowerupEnded(PowerupType type) => RefreshBadges();

        private void OnShieldConsumed() => RefreshBadges();

        private static string AppendBadge(string current, string badge) =>
            string.IsNullOrEmpty(current) ? badge : current + "\n" + badge;

        private void RefreshBadges()
        {
            string s = "";
            var pu = FindLocalPowerups();
            if (pu != null)
            {
                if (pu.ShieldHeld) s = AppendBadge(s, "SHIELD");
                if (pu.CurrentMultiplier > 1) s = AppendBadge(s, "2x COINS");
            }
            _badges.text = s;
        }

        private static Powerups.PowerupController FindLocalPowerups()
        {
            var sync = NetworkPlayerSync.Local;
            return sync != null ? sync.GetComponent<Powerups.PowerupController>() : null;
        }

        private void Pause()
        {
            var gs = GameStateManager.Instance;
            if (gs == null) return;
            if (gs.Current == GameState.Running) gs.Set(GameState.Paused);
        }

        private void Update()
        {
            if (PlayerPrefs.GetInt(Constants.Prefs.ShowFps, 0) == 0)
            {
                if (_fps.text.Length > 0) _fps.text = "";
                return;
            }
            _fpsTimer += Time.unscaledDeltaTime;
            if (_fpsTimer >= 0.5f)
            {
                _fpsTimer = 0f;
                var perf = Core.PerformanceMonitor.Instance;
                _fps.text = perf != null ? $"{perf.AverageFps:0} FPS" : "";
            }
        }
    }
}
