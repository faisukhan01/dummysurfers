using System;
using System.Threading.Tasks;
using Unity.Netcode;
using Unity.Netcode.Transports.UTP;
using UnityEngine;
using UnityEngine.SceneManagement;
using DummySurfer.Core;
using DummySurfer.Data;
using DummySurfer.Utilities;

namespace DummySurfer.Multiplayer
{
    /// <summary>
    /// Room flow orchestration (spec 7 / 10 ROOM FLOW):
    /// Create Room → join code → Player 2 joins → both Ready → shared seed → synchronized
    /// countdown → run → results → rematch/exit. Every network operation has timeout, retry
    /// and friendly error states. Session transport = Unity Relay + Netcode (no custom backend).
    /// </summary>
    public sealed class MultiplayerManager : PersistentManager<MultiplayerManager>
    {
        public enum MpStatus { Idle, Creating, Joining, InLobby, Starting, InMatch, Error }

        public MpStatus Status { get; private set; } = MpStatus.Idle;
        public string JoinCode { get; private set; } = "";
        public bool LocalReady { get; private set; }
        public string LastError { get; private set; } = "";

        public bool InUnexpectedDisconnect { get; private set; }

        public static bool IsOnline => NetworkManager.Singleton != null && NetworkManager.Singleton.IsListening;
        public static bool IsHost => NetworkManager.Singleton != null && NetworkManager.Singleton.IsHost;

        private bool _callbacksHooked;
        private bool _intentionalShutdown;

        protected override void OnManagerAwake()
        {
            ServiceRegistry.Register(this);
        }

        // ---------------- offline ----------------

        public static void LaunchOffline()
        {
            var mp = Ensure();
            mp.ShutdownSession();
            if (GameStateManager.Ensure() is { } gs) gs.IsOnlineRun = false;
            SceneManager.LoadScene(Constants.SceneGame);
        }

        // ---------------- room flow ----------------

        public async Task CreateRoomAsync()
        {
            try
            {
                SetStatus(MpStatus.Creating, "Creating room…");
                EnsureNetworkManager();

                var cfg = GameConfig.Runtime;
                JoinCode = await RelayRoomService.CreateRoomAndStartHostAsync(cfg.netOperationTimeoutSeconds);
                PlayerPrefs.SetString(Constants.Prefs.LastJoinCode, JoinCode);

                LocalReady = false;
                SetStatus(MpStatus.InLobby, "");
                GameEvents.PublishJoinCodeReady(JoinCode);
                PublishLobbySnapshot();
            }
            catch (Exception e)
            {
                Fail(Friendly(e, "create room"));
            }
        }

        public async Task JoinRoomAsync(string code)
        {
            try
            {
                SetStatus(MpStatus.Joining, "Joining room…");
                EnsureNetworkManager();

                var cfg = GameConfig.Runtime;
                await RelayRoomService.JoinRoomAndStartClientAsync(code, cfg.netOperationTimeoutSeconds);

                JoinCode = code.Trim().ToUpperInvariant();
                PlayerPrefs.SetString(Constants.Prefs.LastJoinCode, JoinCode);
                LocalReady = false;
                SetStatus(MpStatus.InLobby, "");
                PublishLobbySnapshot();
            }
            catch (Exception e)
            {
                Fail(Friendly(e, "join room"));
            }
        }

        public void SetReady(bool ready)
        {
            LocalReady = ready;
            LobbyState.Instance?.SetLocalReady(IsHost, ready);
            PublishLobbySnapshot();
            if (IsHost) CheckStartCondition();
        }

        /// <summary>Called by LobbyState when ready flags change over the network.</summary>
        public void NotifyLobbyChanged()
        {
            PublishLobbySnapshot();
            if (IsHost) CheckStartCondition();
        }

        private void CheckStartCondition()
        {
            var lobby = LobbyState.Instance;
            var nm = NetworkManager.Singleton;
            if (lobby == null || nm == null || !nm.IsHost) return;
            if (nm.ConnectedClients.Count < 2) return;
            if (!lobby.HostReady.Value || !lobby.ClientReady.Value) return;

            SetStatus(MpStatus.Starting, "Starting…");
            lobby.ResetFlags();
            LocalReady = false;
            // Authoritative synchronized scene switch into the Game scene (spec 7 flow).
            nm.SceneManager.LoadScene(Constants.SceneGame, LoadSceneMode.Single);
        }

        public void RequestRematch()
        {
            if (IsHost) MatchStateManager.Instance?.ServerRematch();
            else GameEvents.PublishToast("Only the host can start the rematch.");
        }

        public void LeaveSession()
        {
            _intentionalShutdown = true;
            ShutdownSession();
            var gs = GameStateManager.Instance;
            if (gs != null) gs.ResetToMenu();
            if (SceneManager.GetActiveScene().name != Constants.SceneMenu)
                SceneManager.LoadScene(Constants.SceneMenu);
        }

        public void ShutdownSession()
        {
            var nm = NetworkManager.Singleton;
            if (nm != null && nm.IsListening) nm.Shutdown();
            JoinCode = "";
            LocalReady = false;
            NetworkPlayerSync.ResetRemoteTracking();
            SetStatus(MpStatus.Idle, "");
        }

        /// <summary>Best-effort session teardown for offline entry points — never throws.</summary>
        public static void ShutdownQuietly()
        {
            try { Instance?.ShutdownSession(); }
            catch (System.Exception e) { Debug.LogWarning($"[Multiplayer] quiet shutdown: {e.Message}"); }
        }

        /// <summary>Called by ConnectionGuard after an unexpected transport drop.</summary>
        public void HandleUnexpectedDisconnect()
        {
            if (InUnexpectedDisconnect) return;
            InUnexpectedDisconnect = true;
            ShutdownSession();
            GameEvents.PublishMultiplayerError("Connection lost.");
            GameStateManager.Ensure().SetDisconnected();
            _ = ResetFlagSoon();
        }

        private async Task ResetFlagSoon()
        {
            await Task.Delay(1500);
            InUnexpectedDisconnect = false;
        }

        // ---------------- NetworkManager bootstrap ----------------

        public void EnsureNetworkManager()
        {
            var nm = NetworkManager.Singleton;
            if (nm == null)
            {
                var go = new GameObject("NetworkManager");
                DontDestroyOnLoad(go);
                nm = go.AddComponent<NetworkManager>();
                go.AddComponent<UnityTransport>();

                var playerPrefab = Resources.Load<GameObject>("NetworkRunner");
                if (playerPrefab == null)
                    throw new InvalidOperationException(
                        "NetworkRunner prefab is missing. Open Unity and run Tools > Dummy Surfer > 1. Setup Everything (see docs/SETUP_GUIDE.md).");
                nm.AddNetworkPrefab(playerPrefab);
                go.AddComponent<ConnectionGuard>();
            }
            HookCallbacksOnce(nm);
        }

        private void HookCallbacksOnce(NetworkManager nm)
        {
            if (_callbacksHooked) return;
            _callbacksHooked = true;
            nm.OnClientConnectedCallback += OnClientConnected;
            nm.OnClientDisconnectCallback += OnClientDisconnected;
        }

        private void OnClientConnected(ulong clientId)
        {
            PublishLobbySnapshot();
            if (IsHost) GameEvents.PublishToast("A player joined the room!");
        }

        private void OnClientDisconnected(ulong clientId)
        {
            bool iAmHost = NetworkManager.Singleton != null && NetworkManager.Singleton.IsHost;

            if (iAmHost)
            {
                MatchStateManager.Instance?.ServerHandleOpponentLeft(clientId);
                LobbyState.Instance?.ResetFlags();
                GameEvents.PublishOpponentDisconnected();
                PublishLobbySnapshot();
            }
            else if (clientId == NetworkManager.ServerClientId)
            {
                // Server left: ConnectionGuard picks up the transport drop; nudge state too.
                HandleUnexpectedDisconnect();
            }
        }

        public void PublishLobbySnapshot()
        {
            var lobby = LobbyState.Instance;
            var nm = NetworkManager.Singleton;
            bool connected = nm != null && nm.IsHost
                ? nm.ConnectedClients.Count >= 2
                : nm != null && nm.IsConnectedClient;
            GameEvents.PublishLobbyChanged(new GameEvents.LobbySnapshot(
                hasSession: IsOnline,
                joinCode: JoinCode,
                hostReady: lobby != null && lobby.HostReady.Value,
                clientReady: lobby != null && lobby.ClientReady.Value,
                clientConnected: connected));
        }

        // ---------------- helpers ----------------

        private void SetStatus(MpStatus s, string info)
        {
            Status = s;
            LastError = info;
        }

        private void Fail(string message)
        {
            SetStatus(MpStatus.Error, message);
            ShutdownSession();
            GameEvents.PublishMultiplayerError(message);
        }

        public static string Friendly(Exception e, string context)
        {
            if (e is TimeoutException)
                return $"{context} timed out. Check your internet connection and try again.";
            if (e is InvalidOperationException ioe && !string.IsNullOrWhiteSpace(ioe.Message))
                return ioe.Message;
            string msg = e.Message ?? e.GetType().Name;
            if (msg.Contains("NotFound") || msg.Contains("404"))
                return "That room code doesn't exist (or expired). Double-check it with your friend.";
            if (msg.Contains("auth") || msg.ToLower().Contains("sign"))
                return GameServicesInitializer.LastError;
            return $"{context} failed: {msg}";
        }
    }
}
