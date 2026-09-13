using UnityEngine;
using UnityEngine.UI;
using DummySurfer.Data;
using DummySurfer.Player;
using DummySurfer.Utilities;

namespace DummySurfer.UI
{
    /// <summary>Character select: two original runners with stats + colorways (spec 10 CHARACTERS).</summary>
    public sealed class CharacterScreen : UIScreen
    {
        private Text _junoBtn, _kaiBtn;

        protected override void Build()
        {
            UICanvasBuilder.StretchPanel(Root, "Bg", VisualStyles.Background);
            AddText("CHARACTER", 84, VisualStyles.TextPrimary, new Vector2(0f, 760f), new Vector2(900f, 110f), bold: true);
            AddText("Two original runners — pick your style.", 30, VisualStyles.TextMuted,
                new Vector2(0f, 660f), new Vector2(900f, 50f));

            BuildCard(0, new Vector2(-230f, 180f), out _junoBtn);
            BuildCard(1, new Vector2(230f, 180f), out _kaiBtn);

            AddText("KAI changes lanes faster · JUNO is rock solid overall.",
                30, VisualStyles.TextMuted, new Vector2(0f, -320f), new Vector2(940f, 60f));

            AddButton("DONE", new Vector2(0f, -700f), new Vector2(460f, 110f),
                VisualStyles.Primary, VisualStyles.Background, 46, Done);
        }

        private void BuildCard(int index, Vector2 pos, out Text selectLabel)
        {
            var stats = CharacterStats.Load(index);
            var panel = UICanvasBuilder.Panel(Root, "Char_" + stats.displayName, new Vector2(0.5f, 0.5f),
                pos, new Vector2(420f, 600f), VisualStyles.Surface);

            var swatch = UICanvasBuilder.Panel(panel.transform, "Swatch", new Vector2(0.5f, 1f),
                new Vector2(0f, -140f), new Vector2(180f, 180f), stats.primary);
            UICanvasBuilder.Panel(swatch.transform, "Accent", new Vector2(0.5f, 0f),
                new Vector2(0f, 24f), new Vector2(180f, 44f), stats.secondary);

            UICanvasBuilder.Text(panel.transform, "Name", stats.displayName, 64, VisualStyles.TextPrimary,
                TextAnchor.MiddleCenter, new Vector2(0.5f, 1f), new Vector2(0f, -280f), new Vector2(380f, 90f), bold: true);
            UICanvasBuilder.Text(panel.transform, "Blurb", stats.blurb, 28, VisualStyles.TextMuted,
                TextAnchor.UpperCenter, new Vector2(0.5f, 1f), new Vector2(0f, -350f), new Vector2(360f, 140f));

            var btn = UICanvasBuilder.Button(panel.transform, "", new Vector2(0f, -230f), new Vector2(320f, 100f),
                VisualStyles.Primary, VisualStyles.Background, 40, () => Select(index));
            selectLabel = btn.GetComponentInChildren<Text>();
        }

        protected override void OnShown() => Refresh();

        private void Refresh()
        {
            int selected = CharacterSelector.SelectedIndex;
            _junoBtn.text = selected == 0 ? "SELECTED" : "SELECT";
            _kaiBtn.text = selected == 1 ? "SELECTED" : "SELECT";
        }

        private void Select(int index)
        {
            CharacterSelector.Select(index);
            Refresh();
            GameEvents.PublishToast($"{CharacterStats.Load(index).displayName} selected!");
        }

        private void Done()
        {
            UI.CloseOverlayToMenu();
        }
    }
}
