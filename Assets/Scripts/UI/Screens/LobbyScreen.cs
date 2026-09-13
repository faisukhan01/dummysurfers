using UnityEngine;
using DummySurfer.Core;
using DummySurfer.Multiplayer;
using DummySurfer.Utilities;

using UnityEngine.UI;
namespace DummySurfer.UI
{
    /// <summary>
    /// Lobby: room code, copy/share, player cards with READY badges, ready toggle, leave
    /// (spec 4.2 Multiplayer lobby + 3.2 room flow).
    /// </summary>
    public sealed class LobbyScreen : UIScreen
    {
        private Text _codeText;
        private Text _cardHost, _cardClient, _hint;
        private Button _readyBtn;
        private Text _readyLabel;
        private bool _localReady;

        protected override void Build()
        {
            UICanvasBuilder.StretchPanel(Root, "Bg", VisualStyles.Background);

            AddText("ROOM LOBBY", 76, VisualStyles.TextPrimary, new Vector2(0f, 760f), new Vector2(900f, 100f), bold: true);
            _codeText = AddText("——––––", 96, VisualStyles.Primary, new Vector2(0f, 620f), new Vector2(900f, 140f), bold: true);

            UICanvasBuilder.Button(Root, "COPY CODE", new Vector2(-170f, 480f), new Vector2(300f, 96f),
                VisualStyles.Surface, VisualStyles.TextPrimary, 36, CopyCode);
            UICanvasBuilder.Button(Root, "SHARE INVITE", new Vector2(170f, 480f), new Vector2(300f, 96f),
                VisualStyles.Surface, VisualStyles.TextPrimary, 36, ShareCode);

            _cardHost = Card("HostCard", 300f);
            _cardClient = Card("ClientCard", 150f);

            _readyBtn = AddButton("READY UP", new Vector2(0f, -110f), new Vector2(640f, 130f),
                VisualStyles.Primary, VisualStyles.Background, 52, ToggleReady);
            _readyLabel = _readyBtn.GetComponentInChildren<Text>();

            _hint = AddText("Both players ready → countdown starts automatically.", 30, VisualStyles.TextMuted,
                new Vector2(0f, -260f), new Vector2(940f, 60f));

            AddButton("LEAVE ROOM", new Vector2(0f, -740f), new Vector2(420f, 104f),
                VisualStyles.SurfaceHi, VisualStyles.Danger, 42, Leave);
        }

        private Text Card(string name, float y)
        {
            var panel = UICanvasBuilder.Panel(Root, name, new Vector2(0.5f, 0.5f), new Vector2(0f, y), new Vector2(860f, 120f), VisualStyles.Surface);
            var t = UICanvasBuilder.Text(panel.transform, "CardText", "", 40, VisualStyles.TextPrimary,
                TextAnchor.MiddleLeft, new Vector2(0f, 0.5f), new Vector2(30f, 0f), new Vector2(640f, 80f), true);
            return t;
        }

        protected override void OnShown()
        {
            _localReady = MultiplayerManager.Instance != null && MultiplayerManager.Instance.LocalReady;
            RefreshReadyButton();
            GameEvents.LobbyChanged += OnLobbyChanged;
            MultiplayerManager.Instance?.PublishLobbySnapshot();
        }

        public override void HideInstant()
        {
            GameEvents.LobbyChanged -= OnLobbyChanged;
            base.HideInstant();
        }

        private void OnLobbyChanged(GameEvents.LobbySnapshot s)
        {
            _codeText.text = string.IsNullOrEmpty(s.JoinCode) ? "——––––" : Spaced(s.JoinCode);
            _cardHost.text = "HOST (you)  ·  " + (s.HostReady ? "<color=#2FD5C8>READY</color>" : "waiting…");
            _cardClient.text = s.ClientConnected
                ? "RIVAL  ·  " + (s.ClientReady ? "<color=#2FD5C8>READY</color>" : "waiting…")
                : "Waiting for your rival to join…";
        }

        private static string Spaced(string code)
        {
            var sb = new System.Text.StringBuilder(code.Length * 2);
            foreach (char c in code) { sb.Append(c); sb.Append(' '); }
            return sb.ToString().TrimEnd();
        }

        private void RefreshReadyButton()
        {
            _readyLabel.text = _localReady ? "CANCEL READY" : "READY UP";
            _readyBtn.image.color = _localReady ? VisualStyles.Surface : VisualStyles.Primary;
        }

        private void ToggleReady()
        {
            _localReady = !_localReady;
            MultiplayerManager.Instance?.SetReady(_localReady);
            RefreshReadyButton();
        }

        private void CopyCode()
        {
            var code = MultiplayerManager.Instance != null ? MultiplayerManager.Instance.JoinCode : "";
            if (string.IsNullOrEmpty(code)) return;
            GUIUtility.systemCopyBuffer = code;
            GameEvents.PublishToast("Room code copied!");
        }

        private void ShareCode()
        {
            var code = MultiplayerManager.Instance != null ? MultiplayerManager.Instance.JoinCode : "";
            if (string.IsNullOrEmpty(code)) return;
            ShareService.ShareRoomCode(code);
        }

        private void Leave()
        {
            MultiplayerManager.Instance?.LeaveSession();
        }
    }
}
