using System;
using System.Threading.Tasks;
using Unity.Netcode;
using Unity.Services.Relay;
using Unity.Services.Relay.Models;
using Unity.Networking.Transport.Relay;
using Unity.Transport;
using UnityEngine;
using DummySurfer.Core;
using DummySurfer.Utilities;

namespace DummySurfer.Multiplayer
{
    /// <summary>
    /// Relay connectivity (spec 2/7): Relay is the network intermediary, Netcode the gameplay
    /// transport. No dedicated server, no custom backend (spec FREE-FIRST).
    /// </summary>
    public static class RelayRoomService
    {
        public static async Task<string> CreateRoomAndStartHostAsync(float timeoutSeconds)
        {
            var nm = GetNetworkManager();
            var transport = GetTransport(nm);

            bool ready = await GameServicesInitializer.EnsureReadyAsync();
            if (!ready)
                throw new InvalidOperationException(GameServicesInitializer.LastError);

            var allocation = await RelayService.Instance.CreateAllocationAsync(maxConnections: 1)
                .WithTimeout(timeoutSeconds, "create room");

            string joinCode = await RelayService.Instance.GetJoinCodeAsync(allocation.AllocationId)
                .WithTimeout(timeoutSeconds, "create join code");

            transport.SetRelayServerData(new RelayServerData(allocation, "dtls"));

            if (!nm.StartHost())
                throw new InvalidOperationException("Could not start the host session.");
            return joinCode;
        }

        public static async Task JoinRoomAndStartClientAsync(string joinCode, float timeoutSeconds)
        {
            if (string.IsNullOrWhiteSpace(joinCode) || joinCode.Trim().Length < 4)
                throw new InvalidOperationException("That room code doesn't look right. Ask your friend for the 6-letter code.");

            var nm = GetNetworkManager();
            var transport = GetTransport(nm);

            bool ready = await GameServicesInitializer.EnsureReadyAsync();
            if (!ready)
                throw new InvalidOperationException(GameServicesInitializer.LastError);

            var allocation = await RelayService.Instance.JoinAllocationAsync(joinCode.Trim().ToUpperInvariant())
                .WithTimeout(timeoutSeconds, "join room");

            transport.SetRelayServerData(new RelayServerData(allocation, "dtls"));

            if (!nm.StartClient())
                throw new InvalidOperationException("Could not connect to the room.");
        }

        private static NetworkManager GetNetworkManager()
        {
            var nm = NetworkManager.Singleton;
            if (nm == null) throw new InvalidOperationException("Network stack is not ready. Try again.");
            if (nm.IsListening) nm.Shutdown();
            return nm;
        }

        private static UnityTransport GetTransport(NetworkManager nm)
        {
            var transport = nm.GetComponent<UnityTransport>();
            if (transport == null) transport = nm.gameObject.AddComponent<UnityTransport>();
            return transport;
        }
    }
}
