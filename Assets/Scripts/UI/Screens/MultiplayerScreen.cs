using System.Threading.Tasks;
using UnityEngine;
using DummySurfer.Core;
using DummySurfer.Multiplayer;
using DummySurfer.Utilities;

using UnityEngine.UI;
namespace DummySurfer.UI
{
    /// <summary>
    /// Multiplayer hub: Create Room / Join Room with code input, busy states, friendly errors
    /// (spec 4.2 + 10 ROOM FLOW: timeout, retry, friendly errors).
    /// </summary>
    public sealed class MultiplayerScreen : UIScreen
    {
        private InputField _code;
        private Text _status;
        private Button _create, _join;
        private bool _busy;

        protected override void Build()
        {
            UICanvasBuilder.StretchPanel(Root, "Bg", VisualStyles.Background);

            AddText("MULTIPLAYER", 84, VisualStyles.TextPrimary, new Vector2(0f, 740f), new Vector2(900f, 110f), bold: true);
            AddText("Race a friend in real time — one creates, one joins.", 30, VisualStyles.TextMuted,
                new Vector2(0f, 640f), new Vector2(950f, 60f));

            _status = AddText("", 34, VisualStyles.Accent, new Vector2(0f, 500f), new Vector2(950f, 70f));

            _create = AddButton("CREATE ROOM", new Vector2(0f, 330f), new Vector2(620f, 120f),
                VisualStyles.Primary, VisualStyles.Background, 50, OnCreate);
            AddText("— or join with a code —", 30, VisualStyles.TextMuted, new Vector2(0f, 220f), new Vector2(900f, 50f));

            _code = UICanvasBuilder.InputField(Root, "ROOM CODE", new Vector2(0f, 90f), new Vector2(620f, 120f), 6, null);
            _join = AddButton("JOIN ROOM", new Vector2(0f, -60f), new Vector2(620f, 120f),
                VisualStyles.Surface, VisualStyles.TextPrimary, 50, OnJoin);

            AddButton("BACK", new Vector2(0f, -740f), new Vector2(420f, 104f),
                VisualStyles.SurfaceHi, VisualStyles.TextPrimary, 42, Back);
        }

        protected override void OnShown()
        {
            _busy = false;
            SetInteractable(true);
            _status.text = GameServicesInitializer.SignedIn ? "" : "Signing in to online services…";
            var last = PlayerPrefs.GetString(Constants.Prefs.LastJoinCode, "");
            if (!string.IsNullOrEmpty(last) && string.IsNullOrEmpty(_code.text)) _code.text = last;
        }

        private void SetInteractable(bool on)
        {
            _create.interactable = on;
            _join.interactable = on;
        }

        private async void OnCreate()
        {
            if (_busy) return;
            _busy = true;
            SetInteractable(false);
            _status.text = "Creating room…";
            await MultiplayerManager.Instance.CreateRoomAsync();
            // Lobby opens via GameState → LobbyScreen on success.
            if (MultiplayerManager.Instance.Status == MultiplayerManager.MpStatus.Error)
                _status.text = MultiplayerManager.Instance.LastError;
            _busy = false;
            SetInteractable(true);
        }

        private async void OnJoin()
        {
            if (_busy) return;
            string code = _code.text.Trim();
            if (code.Length < 4)
            {
                _status.text = "Enter the 6-letter room code first.";
                return;
            }
            _busy = true;
            SetInteractable(false);
            _status.text = "Joining…";
            await MultiplayerManager.Instance.JoinRoomAsync(code);
            if (MultiplayerManager.Instance.Status == MultiplayerManager.MpStatus.Error)
                _status.text = MultiplayerManager.Instance.LastError;
            _busy = false;
            SetInteractable(true);
        }

        private void Back()
        {
            UI.CloseOverlayToMenu();
        }
    }
}
