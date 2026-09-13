using Unity.Netcode;
using UnityEngine;
using DummySurfer.Utilities;

namespace DummySurfer.Multiplayer
{
    /// <summary>
    /// Lobby readiness for the two-player session (scene-placed NetworkObject in the Menu scene).
    /// Host-authoritative flags; either side can flag itself ready via ServerRpc.
    /// </summary>
    public sealed class LobbyState : NetworkBehaviour
    {
        public static LobbyState Instance { get; private set; }

        public readonly NetworkVariable<bool> HostReady = new NetworkVariable<bool>(false);
        public readonly NetworkVariable<bool> ClientReady = new NetworkVariable<bool>(false);

        public override void OnNetworkSpawn()
        {
            Instance = this;
            HostReady.OnValueChanged += (_, __) => Publish();
            ClientReady.OnValueChanged += (_, __) => Publish();
            Publish();
        }

        public override void OnNetworkDespawn()
        {
            if (Instance == this) Instance = null;
        }

        public void SetLocalReady(bool isHost, bool ready)
        {
            if (isHost) SetHostReady(ready);
            else SetClientReadyServerRpc(ready);
        }

        private void SetHostReady(bool ready)
        {
            if (IsServer) HostReady.Value = ready;
        }

        [ServerRpc(RequireOwnership = false)]
        private void SetClientReadyServerRpc(bool ready) => ClientReady.Value = ready;

        public void ResetFlags()
        {
            if (!IsServer) return;
            HostReady.Value = false;
            ClientReady.Value = false;
        }

        private void Publish()
        {
            bool connected = NetworkManager.Singleton != null &&
                             (NetworkManager.Singleton.IsHost
                                 ? NetworkManager.Singleton.ConnectedClients.Count >= 2
                                 : NetworkManager.Singleton.IsConnectedClient);
            GameEvents.PublishLobbyChanged(new GameEvents.LobbySnapshot(
                hasSession: true,
                joinCode: MultiplayerManager.Instance != null ? MultiplayerManager.Instance.JoinCode : "",
                hostReady: HostReady.Value,
                clientReady: ClientReady.Value,
                clientConnected: connected));
        }
    }
}
