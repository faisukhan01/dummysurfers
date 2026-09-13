using UnityEngine;
using DummySurfer.Core;
using DummySurfer.Multiplayer;
using DummySurfer.Utilities;

namespace DummySurfer.UI
{
    /// <summary>Main menu: logo, Play, Multiplayer, Character, Settings (spec 4.2).</summary>
    public sealed class MainMenuScreen : UIScreen
    {
        private Text _best;
        private Text _authDot;

        protected override void Build()
        {
            UICanvasBuilder.StretchPanel(Root, "Bg", VisualStyles.Background);

            AddText("DUMMY", 150, VisualStyles.TextPrimary, new Vector2(0f, 560f), new Vector2(900f, 190f), bold: true);
            AddText("SURFER", 150, VisualStyles.Primary, new Vector2(0f, 410f), new Vector2(900f, 190f), bold: true);
            AddText("run · dodge · outlast your rival", 34, VisualStyles.TextMuted, new Vector2(0f, 300f), new Vector2(900f, 60f));

            AddButton("PLAY", new Vector2(0f, 40f), new Vector2(560f, 120f),
                VisualStyles.Primary, VisualStyles.Background, 56, OnPlay);
            AddButton("MULTIPLAYER", new Vector2(0f, -100f), new Vector2(560f, 104f),
                VisualStyles.Surface, VisualStyles.TextPrimary, 44, () => UI.OpenOverlay<MultiplayerScreen>());
            AddButton("CHARACTER", new Vector2(0f, -230f), new Vector2(560f, 104f),
                VisualStyles.Surface, VisualStyles.TextPrimary, 44, () => UI.OpenOverlay<CharacterScreen>());
            AddButton("SETTINGS", new Vector2(0f, -360f), new Vector2(560f, 104f),
                VisualStyles.Surface, VisualStyles.TextPrimary, 44, () => UI.OpenOverlay<SettingsScreen>());

            _best = AddText("", 38, VisualStyles.Gold, new Vector2(0f, -620f), new Vector2(900f, 70f), bold: true);
            _authDot = AddText("", 26, VisualStyles.TextMuted, new Vector2(0f, -700f), new Vector2(900f, 50f));
            AddText("v1.0 · Dummy Surfer (working title)", 24, VisualStyles.TextMuted,
                new Vector2(0f, -860f), new Vector2(900f, 50f));
        }

        protected override void OnShown()
        {
            Refresh();
        }

        private void Refresh()
        {
            var run = RunManager.Instance;
            if (run != null)
                _best.text = run.BestDistance > 0
                    ? $"BEST  {run.BestDistance} m   ·   {run.BestCoins} coins"
                    : "No runs yet — go set a record!";
            _authDot.text = GameServicesInitializer.SignedIn
                ? "● online services ready"
                : "○ offline mode (online play unavailable)";
        }

        private void OnPlay()
        {
            MultiplayerManager.LaunchOffline();
        }
    }
}
