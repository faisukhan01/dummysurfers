using UnityEngine;
using DummySurfer.Core;
using DummySurfer.Multiplayer;
using DummySurfer.Utilities;

namespace DummySurfer.UI
{
    /// <summary>Graceful disconnect screen (spec 10: disconnects fail gracefully).</summary>
    public sealed class DisconnectScreen : UIScreen
    {
        private Text _reason;

        protected override void Build()
        {
            UICanvasBuilder.StretchPanel(Root, "Bg", new Color(0.05f, 0.06f, 0.09f, 0.96f));
            AddText("DISCONNECTED", 92, VisualStyles.Danger, new Vector2(0f, 420f), new Vector2(940f, 130f), bold: true);
            _reason = AddText("The connection to the match was lost.", 36, VisualStyles.TextPrimary,
                new Vector2(0f, 280f), new Vector2(920f, 120f));
            AddText("Your distance and coins for that run are not saved as a best.\n" +
                    "This keeps the two-player mode fair for both sides.",
                30, VisualStyles.TextMuted, new Vector2(0f, 100f), new Vector2(920f, 140f));
            AddButton("BACK TO MENU", new Vector2(0f, -160f), new Vector2(560f, 120f),
                VisualStyles.Primary, VisualStyles.Background, 48, Back);
        }

        protected override void OnShown()
        {
            _reason.text = !string.IsNullOrEmpty(MultiplayerManager.Instance?.LastError)
                ? MultiplayerManager.Instance.LastError
                : "The connection to the match was lost.";
        }

        private void Back()
        {
            MultiplayerManager.Instance?.LeaveSession();
        }
    }
}
