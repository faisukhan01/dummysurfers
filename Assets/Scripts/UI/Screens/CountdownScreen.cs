using UnityEngine;
using DummySurfer.Core;
using DummySurfer.Multiplayer;
using DummySurfer.Utilities;

using UnityEngine.UI;
namespace DummySurfer.UI
{
    /// <summary>Synchronized countdown overlay (online ticks arrive from MatchStateManager).</summary>
    public sealed class CountdownScreen : UIScreen
    {
        private Text _number;
        private Text _mode;

        protected override void Build()
        {
            UICanvasBuilder.StretchPanel(Root, "Bg", new Color(0f, 0f, 0f, 0.55f));
            AddText("GET READY", 64, VisualStyles.TextPrimary, new Vector2(0f, 300f), new Vector2(900f, 100f), bold: true);
            _number = AddText("3", 300, VisualStyles.Primary, Vector2.zero, new Vector2(500f, 420f), bold: true);
            _mode = AddText("", 36, VisualStyles.TextMuted, new Vector2(0f, -300f), new Vector2(900f, 60f));
        }

        protected override void OnShown()
        {
            GameEvents.CountdownTick += OnTick;
            bool online = GameStateManager.Instance != null && GameStateManager.Instance.IsOnlineRun;
            _mode.text = online ? "ONLINE MATCH — SAME TRACK, SAME START" : "SINGLE RUN — SWIPE TO SURVIVE";
            _number.text = "";
        }

        public override void HideInstant()
        {
            GameEvents.CountdownTick -= OnTick;
            base.HideInstant();
        }

        private void OnTick(int tick)
        {
            if (_number == null) return;
            _number.text = tick <= 0 ? "GO!" : tick.ToString();
            _number.color = tick <= 0 ? VisualStyles.Accent : VisualStyles.Primary;
        }
    }
}
