using System;
using DummySurfer.Core;

namespace DummySurfer.Utilities
{
    /// <summary>
    /// Static event hub used to decouple managers from each other and from UI
    /// (spec 10 "CODE QUALITY": events for decoupling, no giant GameManager).
    /// UI subscribes here; gameplay systems publish here. Keep payloads immutable structs.
    /// </summary>
    /// <remarks>
    /// SAFE PUBLISH: every publish iterates subscribers individually and isolates exceptions.
    /// On device, one throwing subscriber (audio, UI, anything) used to abort the whole
    /// invocation chain AND the publisher — which froze runs before they started and left
    /// players staring at an idle character. That failure mode is now impossible: a broken
    /// subscriber logs a warning and the rest of the game keeps running.
    /// </remarks>
    public static class GameEvents
    {
        private static void SafeInvoke(Delegate handlers, params object[] args)
        {
            if (handlers == null) return;
            foreach (var d in handlers.GetInvocationList())
            {
                try { d.DynamicInvoke(args); }
                catch (Exception e)
                {
                    var inner = e.InnerException ?? e;
                    UnityEngine.Debug.LogWarning($"[GameEvents] Subscriber '{d.Method.DeclaringType?.Name}.{d.Method.Name}' threw: {inner.Message}\n{inner.StackTrace}");
                }
            }
        }

        // ---- Global app state ----
        public static event Action<GameState> GameStateChanged;
        public static void PublishGameStateChanged(GameState s) => SafeInvoke(GameStateChanged, s);

        // ---- Local run ----
        public static event Action RunStarted;
        public static void PublishRunStarted() => SafeInvoke(RunStarted);

        public static event Action<RunSummary> RunEnded;
        public static void PublishRunEnded(RunSummary r) => SafeInvoke(RunEnded, r);

        public static event Action<int> DistanceChanged;      // meters, fired only when value changes
        public static void PublishDistanceChanged(int m) => SafeInvoke(DistanceChanged, m);

        public static event Action<int> CoinsChanged;
        public static void PublishCoinsChanged(int c) => SafeInvoke(CoinsChanged, c);

        public static event Action LocalPlayerSpawned;
        public static void PublishLocalPlayerSpawned() => SafeInvoke(LocalPlayerSpawned);

        public static event Action LocalPlayerDied;
        public static void PublishLocalPlayerDied() => SafeInvoke(LocalPlayerDied);

        public static event Action PlayerStumbled;
        public static void PublishPlayerStumbled() => SafeInvoke(PlayerStumbled);

        // ---- Powerups ----
        public static event Action<Powerups.PowerupType, float> PowerupStarted;
        public static void PublishPowerupStarted(Powerups.PowerupType t, float dur) => SafeInvoke(PowerupStarted, t, dur);
        public static event Action<Powerups.PowerupType> PowerupEnded;
        public static void PublishPowerupEnded(Powerups.PowerupType t) => SafeInvoke(PowerupEnded, t);
        public static event Action ShieldConsumed;
        public static void PublishShieldConsumed() => SafeInvoke(ShieldConsumed);

        // ---- Multiplayer lobby ----
        public readonly struct LobbySnapshot
        {
            public readonly bool HasSession;
            public readonly string JoinCode;
            public readonly bool HostReady;
            public readonly bool ClientReady;
            public readonly bool ClientConnected;
            public LobbySnapshot(bool hasSession, string joinCode, bool hostReady, bool clientReady, bool clientConnected)
            { HasSession = hasSession; JoinCode = joinCode; HostReady = hostReady; ClientReady = clientReady; ClientConnected = clientConnected; }
        }
        public static event Action<LobbySnapshot> LobbyChanged;
        public static void PublishLobbyChanged(LobbySnapshot s) => SafeInvoke(LobbyChanged, s);

        public static event Action<string> JoinCodeReady;
        public static void PublishJoinCodeReady(string code) => SafeInvoke(JoinCodeReady, code);

        // ---- Match ----
        public static event Action<Multiplayer.MatchPhase> MatchPhaseChanged;
        public static void PublishMatchPhaseChanged(Multiplayer.MatchPhase p) => SafeInvoke(MatchPhaseChanged, p);

        public static event Action<int> CountdownTick;   // 3,2,1 then 0 = GO
        public static void PublishCountdownTick(int n) => SafeInvoke(CountdownTick, n);

        public static event Action<Multiplayer.MatchOutcome, Multiplayer.MatchSideResult, Multiplayer.MatchSideResult> MatchResultReady;
        public static void PublishMatchResultReady(Multiplayer.MatchOutcome o, Multiplayer.MatchSideResult host, Multiplayer.MatchSideResult client)
            => SafeInvoke(MatchResultReady, o, host, client);

        public static event Action<Multiplayer.OpponentInfo> OpponentInfoChanged;
        public static void PublishOpponentInfoChanged(Multiplayer.OpponentInfo info) => SafeInvoke(OpponentInfoChanged, info);

        // ---- Connectivity / errors ----
        public static event Action<string> MultiplayerError;
        public static void PublishMultiplayerError(string msg) => SafeInvoke(MultiplayerError, msg);

        public static event Action OpponentDisconnected;
        public static void PublishOpponentDisconnected() => SafeInvoke(OpponentDisconnected);

        // ---- Misc UI ----
        public static event Action<string> ToastRequested;
        public static void PublishToast(string msg) => SafeInvoke(ToastRequested, msg);
    }
}
