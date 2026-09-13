using UnityEngine;
using DummySurfer.Audio;
using DummySurfer.Core;
using DummySurfer.Multiplayer;
using DummySurfer.Utilities;

using UnityEngine.UI;
namespace DummySurfer.UI
{
    /// <summary>
    /// Results (spec 4.2): WIN/LOSS/DRAW headline, both players' stats, rematch and exit.
    /// Offline variant: run summary + best. Fully event-driven (MatchResultReady).
    /// </summary>
    public sealed class ResultsScreen : UIScreen
    {
        private Text _title, _reason, _you, _rival, _best;
        private Button _rematch;
        private Text _rematchLabel;
        private bool _online;
        private MatchOutcome _outcome;
        private MatchSideResult _host, _client;

        protected override void Build()
        {
            UICanvasBuilder.StretchPanel(Root, "Dim", new Color(0f, 0f, 0f, 0.7f));

            _title = AddText("RUN COMPLETE", 110, VisualStyles.Primary, new Vector2(0f, 620f), new Vector2(900f, 150f), bold: true);
            _reason = AddText("", 34, VisualStyles.TextMuted, new Vector2(0f, 500f), new Vector2(940f, 70f));

            var panel = UICanvasBuilder.Panel(Root, "StatsPanel", new Vector2(0.5f, 0.5f), new Vector2(0f, 180f), new Vector2(880f, 380f), VisualStyles.Surface);
            UICanvasBuilder.Text(panel.transform, "H0", "", 34, VisualStyles.TextMuted, TextAnchor.MiddleRight, new Vector2(0.5f, 1f), new Vector2(-190f, -50f), new Vector2(300f, 50f));
            UICanvasBuilder.Text(panel.transform, "HYou", "YOU", 38, VisualStyles.Accent, TextAnchor.MiddleRight, new Vector2(0.5f, 1f), new Vector2(-190f, -100f), new Vector2(300f, 50f), true);
            UICanvasBuilder.Text(panel.transform, "HRival", "RIVAL", 38, VisualStyles.Primary, TextAnchor.MiddleRight, new Vector2(0.5f, 1f), new Vector2(190f, -100f), new Vector2(300f, 50f), true);
            _you = UICanvasBuilder.Text(panel.transform, "YouStats", "", 44, VisualStyles.TextPrimary, TextAnchor.UpperRight, new Vector2(0.5f, 1f), new Vector2(-190f, -160f), new Vector2(300f, 240f));
            _rival = UICanvasBuilder.Text(panel.transform, "RivalStats", "", 44, VisualStyles.TextPrimary, TextAnchor.UpperRight, new Vector2(0.5f, 1f), new Vector2(190f, -160f), new Vector2(300f, 240f));

            _best = AddText("", 32, VisualStyles.Gold, new Vector2(0f, -80f), new Vector2(900f, 60f));

            _rematch = AddButton("REMATCH", new Vector2(0f, -230f), new Vector2(560f, 120f),
                VisualStyles.Primary, VisualStyles.Background, 50, Rematch);
            _rematchLabel = _rematch.GetComponentInChildren<Text>();
            AddButton("EXIT TO MENU", new Vector2(0f, -380f), new Vector2(560f, 110f),
                VisualStyles.SurfaceHi, VisualStyles.TextPrimary, 44, Exit);
        }

        protected override void OnShown()
        {
            GameEvents.MatchResultReady += OnMatchResult;
            if (_online && _outcome != MatchOutcome.None) Apply();
        }

        public override void HideInstant()
        {
            GameEvents.MatchResultReady -= OnMatchResult;
            base.HideInstant();
        }

        public void ConfigureOffline(RunSummary s)
        {
            _online = false;
            _outcome = MatchOutcome.None;
            _title.text = "RUN COMPLETE";
            _title.color = s.NewBest ? VisualStyles.Gold : VisualStyles.Primary;
            _reason.text = s.NewBest ? "NEW PERSONAL BEST!" : "Nice run — beat your best!";
            _you.text = $"{s.Distance} m\n{s.Coins} coins";
            _rival.text = "—\n—";
            _best.text = $"BEST  {s.BestDistance} m  ·  {s.BestCoins} coins";
            _rematch.gameObject.SetActive(true);
            _rematchLabel.text = "RUN AGAIN";
            _rematch.interactable = true;
        }

        public void ConfigureOnline(MatchOutcome outcome, MatchSideResult host, MatchSideResult client)
        {
            _online = true;
            _outcome = outcome;
            _host = host;
            _client = client;
            Apply();
        }

        private void OnMatchResult(MatchOutcome outcome, MatchSideResult host, MatchSideResult client)
        {
            ConfigureOnline(outcome, host, client);
        }

        private void Apply()
        {
            bool iAmHost = MultiplayerManager.IsHost;
            bool iWon = _outcome == MatchOutcome.HostWins && iAmHost
                     || _outcome == MatchOutcome.ClientWins && !iAmHost;
            bool draw = _outcome == MatchOutcome.Draw;

            _title.text = draw ? "DRAW" : iWon ? "YOU WIN!" : "YOU LOSE";
            _title.color = draw ? VisualStyles.TextMuted : iWon ? VisualStyles.Accent : VisualStyles.Danger;
            _reason.text = _outcome == MatchOutcome.Draw
                ? "Dead even — same distance AND same coins."
                : iWon ? "Outlasted your rival." : "Your rival outlasted you. Rematch?";

            var mine = iAmHost ? _host : _client;
            var theirs = iAmHost ? _client : _host;
            _you.text = $"{mine.Distance} m\n{mine.Coins} coins";
            _rival.text = $"{theirs.Distance} m\n{theirs.Coins} coins";

            var run = RunManager.Instance;
            _best.text = run != null ? $"YOUR BEST  {run.BestDistance} m  ·  {run.BestCoins} coins" : "";

            _rematch.gameObject.SetActive(true);
            if (MultiplayerManager.IsHost)
            {
                _rematchLabel.text = "REMATCH";
                _rematch.interactable = true;
            }
            else
            {
                _rematchLabel.text = "WAITING FOR HOST…";
                _rematch.interactable = false;
            }

            if (iWon) Audio.AudioManager.Instance?.PlaySfx(SfxId.Win);
            else if (!draw) Audio.AudioManager.Instance?.PlaySfx(SfxId.Lose);
        }

        private void Rematch()
        {
            if (_online)
            {
                MultiplayerManager.Instance?.RequestRematch();
                _rematch.interactable = false;
                _rematchLabel.text = MultiplayerManager.IsHost ? "STARTING…" : "WAITING FOR HOST…";
            }
            else if (ServiceRegistry.TryGet<Track.RunSceneController>(out var scene))
            {
                GameStateManager.Instance?.Set(GameState.Countdown);
                scene.RestartOffline();
            }
        }

        private void Exit()
        {
            if (_online) MultiplayerManager.Instance?.LeaveSession();
            else
            {
                GameStateManager.Ensure().ResetToMenu();
                UnityEngine.SceneManagement.SceneManager.LoadScene(Constants.SceneMenu);
            }
        }
    }
}
