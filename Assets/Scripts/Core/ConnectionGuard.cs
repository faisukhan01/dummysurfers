using Unity.Netcode;
using UnityEngine;
using DummySurfer.Core;
using DummySurfer.Multiplayer;

namespace DummySurfer.Core
{
    /// <summary>
    /// Watchdog attached next to the NetworkManager: detects silent transport drops
    /// (the opponent's app was killed, signal lost) and routes the game into the
    /// Disconnected state with a friendly screen (spec 10: disconnects fail gracefully).
    /// </summary>
    public sealed class ConnectionGuard : MonoBehaviour
    {
        private bool _wasListening;
        private float _graceUntil;

        private void Update()
        {
            var nm = NetworkManager.Singleton;
            if (nm == null)
            {
                _wasListening = false;
                return;
            }

            if (nm.IsListening)
            {
                _wasListening = true;
                _graceUntil = Time.unscaledTime + 1f;   // ignore the first frames of startup
                return;
            }

            if (_wasListening && Time.unscaledTime > _graceUntil)
            {
                _wasListening = false;
                var mp = MultiplayerManager.Instance;
                if (mp != null && mp.Status == MultiplayerManager.MpStatus.Error) return; // already handled
                mp?.HandleUnexpectedDisconnect();
            }
        }
    }
}
