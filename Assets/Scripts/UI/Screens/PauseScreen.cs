using UnityEngine;
using DummySurfer.Core;
using DummySurfer.Multiplayer;
using DummySurfer.Utilities;

using UnityEngine.UI;
namespace DummySurfer.UI
{
    /// <summary>
    /// Pause overlay. Offline: freezes simulation. Online: overlay-only — the competitive
    /// run keeps going (fairness), clearly communicated in the note (spec 3.1/7).
    /// </summary>
    public sealed class PauseScreen : UIScreen
    {
        private Button _restart;
        private Text _note;

        protected override void Build()
        {
            UICanvasBuilder.StretchPanel(Root, "Dim", new Color(0f, 0f, 0f, 0.62f));
            AddText("PAUSED", 96, VisualStyles.TextPrimary, new Vector2(0f, 480f), new Vector2(900f, 130f), bold: true);

            AddButton("RESUME", new Vector2(0f, 220f), new Vector2(560f, 120f),
                VisualStyles.Primary, VisualStyles.Background, 50, Resume);
            _restart = AddButton("RESTART RUN", new Vector2(0f, 80f), new Vector2(560f, 120f),
                VisualStyles.Surface, VisualStyles.TextPrimary, 46, Restart);
            AddButton("QUIT TO MENU", new Vector2(0f, -60f), new Vector2(560f, 120f),
                VisualStyles.SurfaceHi, VisualStyles.Danger, 46, Quit);

            _note = AddText("", 30, VisualStyles.TextMuted, new Vector2(0f, -240f), new Vector2(900f, 120f));
        }

        protected override void OnShown()
        {
            bool online = GameStateManager.Instance != null && GameStateManager.Instance.IsOnlineRun;
            _restart.gameObject.SetActive(!online);
            _note.text = online
                ? "Online match keeps running while paused —\nsurviving is the only way to win!"
                : "";
        }

        private void Resume()
        {
            GameStateManager.Instance?.Set(GameState.Running);
        }

        private void Restart()
        {
            if (ServiceRegistry.TryGet<Track.RunSceneController>(out var scene))
            {
                GameStateManager.Instance?.Set(GameState.Countdown);
                scene.RestartOffline();
            }
        }

        private void Quit()
        {
            var gs = GameStateManager.Instance;
            if (gs != null && gs.IsOnlineRun) MultiplayerManager.Instance?.LeaveSession();
            else OfflineExit();
        }

        private static void OfflineExit()
        {
            if (Time.timeScale == 0f) Time.timeScale = 1f;
            GameStateManager.Ensure().ResetToMenu();
            UnityEngine.SceneManagement.SceneManager.LoadScene(Constants.SceneMenu);
        }
    }
}
