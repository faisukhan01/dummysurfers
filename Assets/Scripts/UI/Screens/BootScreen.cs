using System.Collections;
using UnityEngine;
using DummySurfer.Core;
using DummySurfer.Utilities;

using UnityEngine.UI;
namespace DummySurfer.UI
{
    /// <summary>Splash/boot screen with service warm-up status.</summary>
    public sealed class BootScreen : UIScreen
    {
        private Text _status;
        private Text _dots;
        private Coroutine _anim;

        protected override void Build()
        {
            UICanvasBuilder.StretchPanel(Root, "Bg", VisualStyles.Background);
            AddText("DUMMY", 140, VisualStyles.TextPrimary, new Vector2(0f, 220f), new Vector2(900f, 180f), bold: true);
            AddText("SURFER", 140, VisualStyles.Primary, new Vector2(0f, 80f), new Vector2(900f, 180f), bold: true);
            _status = AddText("Starting up…", 38, VisualStyles.TextMuted, new Vector2(0f, -160f), new Vector2(900f, 80f));
            _dots = AddText("", 60, VisualStyles.Primary, new Vector2(0f, -240f), new Vector2(400f, 80f), bold: true);
            AddText("original university runner · not affiliated with any commercial title",
                24, VisualStyles.TextMuted, new Vector2(0f, -780f), new Vector2(1000f, 60f));
        }

        protected override void OnShown()
        {
            if (_anim == null) _anim = StartCoroutine(Animate());
        }

        public override void HideInstant()
        {
            if (_anim != null) { StopCoroutine(_anim); _anim = null; }
            base.HideInstant();
        }

        private IEnumerator Animate()
        {
            int dots = 0;
            while (true)
            {
                dots = (dots + 1) % 4;
                if (_dots != null) _dots.text = new string('.', dots);
                if (_status != null)
                    _status.text = GameServicesInitializer.SignedIn
                        ? "Services ready — sign in OK"
                        : string.IsNullOrEmpty(GameServicesInitializer.LastError)
                            ? "Connecting services…"
                            : "Online services unavailable — offline play works fine";
                yield return new WaitForSecondsRealtime(0.35f);
            }
        }
    }
}
