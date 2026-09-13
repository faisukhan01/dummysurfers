using System;
using DummySurfer.Core;

namespace DummySurfer.Utilities
{
    /// <summary>
    /// Static event hub used to decouple managers from each other and from UI
    /// (spec 10 "CODE QUALITY": events for decoupling, no giant GameManager).
    /// UI subscribes here; gameplay systems publish here. Keep payloads immutable structs.
    /// </summary>
    public static class GameEvents
    {
        // ---- Global app state ----
        public static event Action<GameState> GameStateChanged;
        public static void PublishGameStateChanged(GameState s) => GameStateChanged?.Invoke(s);

        // ---- Local run ----
        public static event Action RunStarted;
        public static void PublishRunStarted() => RunStarted?.Invoke();

        public static event Action<RunSummary> RunEnded;
        public static void PublishRunEnded(RunSummary r) => RunEnded?.Invoke(r);

        public static event Action<int> DistanceChanged;      // meters, fired only when value changes
        public static void PublishDistanceChanged(int m) => DistanceChanged?.Invoke(m);

        public static event Action<int> CoinsChanged;
        public static void PublishCoinsChanged(int c) => CoinsChanged?.Invoke(c);

        public static event Action LocalPlayerSpawned;
        public static void PublishLocalPlayerSpawned() => LocalPlayerSpawned?.Invoke();

        public static event Action LocalPlayerDied;
        public static void PublishLocalPlayerDied() => LocalPlayerDied?.Invoke();

        public static event Action PlayerStumbled;
        public static void PublishPlayerStumbled() => PlayerStumbled?.Invoke();

        // ---- Powerups ----
        public static event Action<Powerups.PowerupType, float> PowerupStarted;
        public static void PublishPowerupStarted(Powerups.PowerupType t, float dur) => PowerupStarted?.Invoke(t, dur);
        public static event Action<Powerups.PowerupType> PowerupEnded;
        public static void PublishPowerupEnded(Powerups.PowerupType t) => PowerupEnded?.Invoke(t);
        public static event Action ShieldConsumed;
        public static void PublishShieldConsumed() => ShieldConsumed?.Invoke();

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
        public static void PublishLobbyChanged(LobbySnapshot s) => LobbyChanged?.Invoke(s);

        public static event Action<string> JoinCodeReady;
        public static void PublishJoinCodeReady(string code) => JoinCodeReady?.Invoke(code);

        // ---- Match ----
        public static event Action<Multiplayer.MatchPhase> MatchPhaseChanged;
        public static void PublishMatchPhaseChanged(Multiplayer.MatchPhase p) => MatchPhaseChanged?.Invoke(p);

        public static event Action<int> CountdownTick;   // 3,2,1 then 0 = GO
        public static void PublishCountdownTick(int n) => CountdownTick?.Invoke(n);

        public static event Action<Multiplayer.MatchOutcome, Multiplayer.MatchSideResult, Multiplayer.MatchSideResult> MatchResultReady;
        public static void PublishMatchResultReady(Multiplayer.MatchOutcome o, Multiplayer.MatchSideResult host, Multiplayer.MatchSideResult client)
            => MatchResultReady?.Invoke(o, host, client);

        public static event Action<Multiplayer.OpponentInfo> OpponentInfoChanged;
        public static void PublishOpponentInfoChanged(Multiplayer.OpponentInfo info) => OpponentInfoChanged?.Invoke(info);

        // ---- Connectivity / errors ----
        public static event Action<string> MultiplayerError;
        public static void PublishMultiplayerError(string msg) => MultiplayerError?.Invoke(msg);

        public static event Action OpponentDisconnected;
        public static void PublishOpponentDisconnected() => OpponentDisconnected?.Invoke();

        // ---- Misc UI ----
        public static event Action<string> ToastRequested;
        public static void PublishToast(string msg) => ToastRequested?.Invoke(msg);
    }
}
