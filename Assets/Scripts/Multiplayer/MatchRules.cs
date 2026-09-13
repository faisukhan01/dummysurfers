using UnityEngine;

namespace DummySurfer.Multiplayer
{
    public enum MatchPhase : byte { None = 0, Lobby, Countdown, Running, Results }

    public enum MatchOutcome : byte { None = 0, HostWins, ClientWins, Draw, Aborted }

    [System.Serializable]
    public struct MatchSideResult : Unity.Netcode.INetworkSerializable
    {
        public bool Alive;
        public int Distance;
        public int Coins;

        public void NetworkSerialize<T>(BufferSerializer<T> serializer) where T : IReaderWriter
        {
            serializer.SerializeValue(ref Alive);
            serializer.SerializeValue(ref Distance);
            serializer.SerializeValue(ref Coins);
        }
    }

    public readonly struct OpponentInfo
    {
        public readonly bool Connected;
        public readonly bool Alive;
        public readonly int Distance;
        public readonly int Coins;
        public OpponentInfo(bool connected, bool alive, int distance, int coins)
        { Connected = connected; Alive = alive; Distance = distance; Coins = coins; }
    }

    /// <summary>
    /// Pure, unit-tested result rules — spec 3.3 exactly:
    /// A alive + B dead -> A WINS · both dead -> greater distance, then coins, else DRAW.
    /// </summary>
    public static class MatchRules
    {
        public static MatchOutcome Resolve(bool hostAlive, bool clientAlive,
            int hostDistance, int clientDistance, int hostCoins, int clientCoins)
        {
            if (hostAlive && clientAlive) return MatchOutcome.None;      // still running
            if (hostAlive && !clientAlive) return MatchOutcome.HostWins;
            if (!hostAlive && clientAlive) return MatchOutcome.ClientWins;

            if (hostDistance != clientDistance)
                return hostDistance > clientDistance ? MatchOutcome.HostWins : MatchOutcome.ClientWins;
            if (hostCoins != clientCoins)
                return hostCoins > clientCoins ? MatchOutcome.HostWins : MatchOutcome.ClientWins;
            return MatchOutcome.Draw;
        }
    }
}
