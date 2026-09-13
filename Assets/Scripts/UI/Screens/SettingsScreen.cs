using UnityEngine;
using UnityEngine.UI;
using DummySurfer.Audio;
using DummySurfer.Core;
using DummySurfer.Utilities;

namespace DummySurfer.UI
{
    /// <summary>Settings: music/SFX volume, haptics, quality tier, FPS meter (spec 4.2).</summary>
    public sealed class SettingsScreen : UIScreen
    {
        private Button _low, _mid, _high;

        protected override void Build()
        {
            UICanvasBuilder.StretchPanel(Root, "Bg", VisualStyles.Background);
            AddText("SETTINGS", 84, VisualStyles.TextPrimary, new Vector2(0f, 760f), new Vector2(900f, 110f), bold: true);

            AddText("MUSIC", 40, VisualStyles.TextPrimary, new Vector2(-140f, 560f), new Vector2(400f, 60f), TextAnchor.MiddleLeft, bold: true);
            var music = UICanvasBuilder.Slider(Root, new Vector2(120f, 560f), new Vector2(520f, 70f), AudioManager.Instance.MusicVolume);
            music.onValueChanged.AddListener(v => AudioManager.Instance.SetMusicVolume(v));

            AddText("SOUND FX", 40, VisualStyles.TextPrimary, new Vector2(-140f, 430f), new Vector2(400f, 60f), TextAnchor.MiddleLeft, bold: true);
            var sfx = UICanvasBuilder.Slider(Root, new Vector2(120f, 430f), new Vector2(520f, 70f), AudioManager.Instance.SfxVolume);
            sfx.onValueChanged.AddListener(v => AudioManager.Instance.SetSfxVolume(v));

            UICanvasBuilder.Toggle(Root, "HAPTICS", new Vector2(0f, 300f), new Vector2(640f, 96f),
                PlayerPrefs.GetInt(Constants.Prefs.Haptics, 1) == 1,
                on => PlayerPrefs.SetInt(Constants.Prefs.Haptics, on ? 1 : 0));

            AddText("GRAPHICS QUALITY", 40, VisualStyles.TextPrimary, new Vector2(0f, 180f), new Vector2(700f, 60f), bold: true);
            _low = AddButton("LOW", new Vector2(-220f, 70f), new Vector2(200f, 96f), VisualStyles.Surface, VisualStyles.TextPrimary, 38, () => SetQuality(0));
            _mid = AddButton("MID", new Vector2(0f, 70f), new Vector2(200f, 96f), VisualStyles.Surface, VisualStyles.TextPrimary, 38, () => SetQuality(1));
            _high = AddButton("HIGH", new Vector2(220f, 70f), new Vector2(200f, 96f), VisualStyles.Surface, VisualStyles.TextPrimary, 38, () => SetQuality(2));

            UICanvasBuilder.Toggle(Root, "SHOW FPS COUNTER", new Vector2(0f, -70f), new Vector2(640f, 96f),
                PlayerPrefs.GetInt(Constants.Prefs.ShowFps, 0) == 1,
                on => PlayerPrefs.SetInt(Constants.Prefs.ShowFps, on ? 1 : 0));

            AddText("Auto performance guard: if FPS drops below 45 for a while,\nthe game lowers quality automatically once.",
                28, VisualStyles.TextMuted, new Vector2(0f, -220f), new Vector2(900f, 100f));

            AddButton("DONE", new Vector2(0f, -700f), new Vector2(460f, 110f),
                VisualStyles.Primary, VisualStyles.Background, 46, Done);
        }

        protected override void OnShown() => HighlightQuality();

        private void HighlightQuality()
        {
            int tier = Core.PerformanceMonitor.Instance != null ? Core.PerformanceMonitor.Instance.QualityTier : 2;
            _low.image.color = tier == 0 ? VisualStyles.Primary : VisualStyles.Surface;
            _mid.image.color = tier == 1 ? VisualStyles.Primary : VisualStyles.Surface;
            _high.image.color = tier == 2 ? VisualStyles.Primary : VisualStyles.Surface;
        }

        private void SetQuality(int tier)
        {
            Core.PerformanceMonitor.Instance?.SetTier(tier);
            HighlightQuality();
            GameEvents.PublishToast($"Quality: {(tier == 0 ? "LOW" : tier == 1 ? "MID" : "HIGH")}");
        }

        private void Done()
        {
            PlayerPrefs.Save();
            UI.CloseOverlayToMenu();
        }
    }
}
