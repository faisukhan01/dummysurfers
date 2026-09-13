using Unity.Netcode;
using UnityEngine;
using DummySurfer.Core;
using DummySurfer.Data;
using DummySurfer.Utilities;

namespace DummySurfer.Multiplayer
{
    /// <summary>
    /// Authoritative match state (scene-placed NetworkObject in the Game scene).
    /// Host decides death windows, result transitions and validates scoring-critical data
    /// (spec 7.3 AUTHORITY). Late packets can never resurrect a dead player: Alive flags only
    /// flip true on rematch countdown.
    /// </summary>
    public sealed class MatchStateManager : NetworkBehaviour
    {
        public static MatchStateManager Instance { get; private set; }

        public readonly NetworkVariable<ulong> SharedSeed = new NetworkVariable<ulong>(0ul);
        public readonly NetworkVariable<byte> Phase = new NetworkVariable<byte>((byte)MatchPhase.None);
        public readonly NetworkVariable<byte> Outcome = new NetworkVariable<byte>((byte)MatchOutcome.None);
        public readonly NetworkVariable<int> CountdownTicks = new NetworkVariable<int>(0);
        public readonly NetworkVariable<MatchSideResult> HostScore =
            new NetworkVariable<MatchSideResult>(default, NetworkVariableReadPermission.Everyone, NetworkVariableWritePermission.Server);
        public readonly NetworkVariable<MatchSideResult> ClientScore =
            new NetworkVariable<MatchSideResult>(default, NetworkVariableReadPermission.Everyone, NetworkVariableWritePermission.Server);

        private float _phaseTimer;
        private float _runElapsed;
        private float _firstDeathServerTime = -1f;
        private bool _resolving;
        private GameConfig _cfg;

        public override void OnNetworkSpawn()
        {
            Instance = this;
            _cfg = GameConfig.Runtime;
            Phase.OnValueChanged += (_, next) => GameEvents.PublishMatchPhaseChanged((MatchPhase)next);
            CountdownTicks.OnValueChanged += (_, ticks) => GameEvents.PublishCountdownTick(ticks);
            Outcome.OnValueChanged += (_, __) => PublishResult();
            GameEvents.PublishMatchPhaseChanged((MatchPhase)Phase.Value);
        }

        public override void OnNetworkDespawn()
        {
            if (Instance == this) Instance = null;
        }

        // ---------------- server flow ----------------

        public void ServerStartCountdown(ulong seed)
        {
            if (!IsServer) return;
            SharedSeed.Value = seed;
            Outcome.Value = (byte)MatchOutcome.None;
            HostScore.Value = new MatchSideResult { Alive = true };
            ClientScore.Value = new MatchSideResult { Alive = true };
            _firstDeathServerTime = -1f;
            _resolving = false;
            _runElapsed = 0f;
            _phaseTimer = _cfg.countdownSeconds;
            CountdownTicks.Value = Mathf.CeilToInt(_phaseTimer);
            Phase.Value = (byte)MatchPhase.Countdown;
        }

        public void ServerRematch()
        {
            if (!IsServer) return;
            ServerStartCountdown(SeededRandom.NewSeed());
        }

        private void Update()
        {
            if (!IsServer) return;

            switch ((MatchPhase)Phase.Value)
            {
                case MatchPhase.Countdown:
                    _phaseTimer -= Time.deltaTime;
                    int ticks = Mathf.Max(0, Mathf.CeilToInt(_phaseTimer));
                    if (ticks != CountdownTicks.Value) CountdownTicks.Value = ticks;
                    if (_phaseTimer <= 0f) Phase.Value = (byte)MatchPhase.Running;
                    break;

                case MatchPhase.Running:
                    _runElapsed += Time.deltaTime;

                    // Death comparison window (spec 3.2): if one died and the other survives
                    // the window, the survivor wins immediately.
                    if (_firstDeathServerTime >= 0f && !_resolving &&
                        (Time.realtimeSinceStartup - _firstDeathServerTime) > _cfg.deathComparisonWindowSeconds)
                    {
                        // Exactly one side is dead here; whoever is still alive wins.
                        Resolve(HostScore.Value.Alive ? MatchOutcome.HostWins : MatchOutcome.ClientWins);
                    }
                    break;
            }
        }

        [ServerRpc(RequireOwnership = false)]
        public void SubmitStatsServerRpc(ulong reporterClientId, int distance, int coins, bool alive)
        {
            if (!IsServer) return;
            distance = ClampPlausible(distance);
            coins = Mathf.Clamp(coins, 0, 100000);
            bool isHost = reporterClientId == NetworkManager.ServerClientId;

            if (isHost)
            {
                if (HostScore.Value.Alive)  // never resurrect (spec 10 NETWORK STATE)
                    HostScore.Value = new MatchSideResult { Alive = alive, Distance = distance, Coins = coins };
            }
            else
            {
                if (ClientScore.Value.Alive)
                    ClientScore.Value = new MatchSideResult { Alive = alive, Distance = distance, Coins = coins };
            }
        }

        [ServerRpc(RequireOwnership = false)]
        public void SubmitDeathServerRpc(ulong reporterClientId, int distance, int coins)
        {
            if (!IsServer || _resolving) return;
            distance = ClampPlausible(distance);
            coins = Mathf.Clamp(coins, 0, 100000);
            bool isHost = reporterClientId == NetworkManager.ServerClientId;

            var dead = new MatchSideResult { Alive = false, Distance = distance, Coins = coins };
            if (isHost) HostScore.Value = dead; else ClientScore.Value = dead;

            if (_firstDeathServerTime < 0f)
            {
                _firstDeathServerTime = Time.realtimeSinceStartup;
                return;
            }

            // Both dead → compare distance, then coins (spec 3.3)
            var outcome = MatchRules.Resolve(
                HostScore.Value.Alive, ClientScore.Value.Alive,
                HostScore.Value.Distance, ClientScore.Value.Distance,
                HostScore.Value.Coins, ClientScore.Value.Coins);
            Resolve(outcome);
        }

        /// <summary>Host short-circuit when ITS OWN runner dies (no RPC hop needed).</summary>
        public void ServerRecordDeath(ulong clientId, int distance, int coins)
        {
            if (!IsServer) return;
            SubmitDeathServerRpc(clientId, distance, coins);
        }

        /// <summary>Opponent left mid-run → survivor wins by abort (graceful disconnect, spec 10).</summary>
        public void ServerHandleOpponentLeft(ulong leftClientId)
        {
            if (!IsServer || _resolving) return;
            bool hostLeft = leftClientId == NetworkManager.ServerClientId;
            if ((MatchPhase)Phase.Value == MatchPhase.Running || (MatchPhase)Phase.Value == MatchPhase.Countdown)
            {
                var aborted = new MatchSideResult { Alive = false, Distance = 0, Coins = 0 };
                if (hostLeft) HostScore.Value = aborted; else ClientScore.Value = aborted;
                Resolve(hostLeft ? MatchOutcome.ClientWins : MatchOutcome.HostWins);
            }
        }

        private void Resolve(MatchOutcome outcome)
        {
            if (_resolving || outcome == MatchOutcome.None) return;
            _resolving = true;
            Outcome.Value = (byte)outcome;
            Phase.Value = (byte)MatchPhase.Results;
        }

        private int ClampPlausible(int distance)
        {
            float maxPossible = (_runElapsed + _cfg.countdownSeconds + 10f) * _cfg.maxSpeed * 1.25f
                                + _cfg.chunkLength * 2f;
            return Mathf.Clamp(distance, 0, Mathf.CeilToInt(maxPossible));
        }

        // ---------------- client helpers ----------------

        public MatchSideResult ScoreFor(bool isHost) => isHost ? HostScore.Value : ClientScore.Value;
        public MatchSideResult ScoreForOpponent(bool isHost) => isHost ? ClientScore.Value : HostScore.Value;

        private void PublishResult()
        {
            var outcome = (MatchOutcome)Outcome.Value;
            if (outcome == MatchOutcome.None) return;
            GameEvents.PublishMatchResultReady(outcome, HostScore.Value, ClientScore.Value);
        }
    }
}
