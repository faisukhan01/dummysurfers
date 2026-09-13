using System;
using Unity.Netcode;
using UnityEngine;
using DummySurfer.Core;
using DummySurfer.Data;
using DummySurfer.Player;
using DummySurfer.Powerups;
using DummySurfer.Utilities;

namespace DummySurfer.Multiplayer
{
    [Flags]
    public enum RunnerFlags : byte
    {
        None = 0,
        Airborne = 1,
        Sliding = 2,
        Dead = 4,
        Victory = 8,
        Stumble = 16
    }

    [Serializable]
    public struct RunnerNetState : INetworkSerializable
    {
        public sbyte Lane;
        public float Z;
        public float Y;
        public byte Flags;
        public float Speed;

        public void NetworkSerialize<T>(BufferSerializer<T> serializer) where T : IReaderWriter
        {
            serializer.SerializeValue(ref Lane);
            serializer.SerializeValue(ref Z);
            serializer.SerializeValue(ref Y);
            serializer.SerializeValue(ref Flags);
            serializer.SerializeValue(ref Speed);
        }
    }

    /// <summary>
    /// Network-facing player state (spec 5.2/7.1): owner broadcasts lane/position/flags at
    /// Netcode tick rate; remote side interpolates. Decorative particles, camera shake and
    /// audio stay local (spec 7.2). Death is reported to the authoritative MatchStateManager.
    /// </summary>
    public sealed class NetworkPlayerSync : NetworkBehaviour
    {
        public static NetworkPlayerSync Local { get; private set; }
        public static float RemoteZ { get; private set; } = float.NegativeInfinity;
        public static bool HasRemote { get; private set; }

        public static event Action<PlayerController> LocalPlayerReady;

        /// <summary>Clears remote opponent tracking (used on session shutdown).</summary>
        public static void ResetRemoteTracking()
        {
            HasRemote = false;
            RemoteZ = float.NegativeInfinity;
        }

        public readonly NetworkVariable<RunnerNetState> State =
            new NetworkVariable<RunnerNetState>(default, NetworkVariableReadPermission.Everyone, NetworkVariableWritePermission.Owner);

        private PlayerController _pc;
        private RunnerVisualRig _rig;
        private GameConfig _cfg;
        private bool _owner;
        private RunnerNetState _lastSent;
        private float _statsTimer;
        private RunnerAnimState _remoteAnim = RunnerAnimState.Idle;

        public override void OnNetworkSpawn()
        {
            _cfg = GameConfig.Runtime;
            _pc = GetComponent<PlayerController>();
            _rig = GetComponent<RunnerVisualRig>();
            _owner = IsOwner;

            if (_owner)
            {
                Local = this;
                _pc.Initialize(CharacterSelector.GetSelectedStats(), isLocal: true);
                _pc.ResetForRun();
                _pc.Died += OnLocalDeath;
                RemoteZ = float.NegativeInfinity;
                HasRemote = false;
                LocalPlayerReady?.Invoke(_pc);
            }
            else
            {
                // Remote opponent: visuals only, no local sim (spec 7.1 interpolate remote movement).
                _pc.enabled = false;
                var pu = GetComponent<PowerupController>();
                if (pu != null) pu.enabled = false;
                _rig.ApplyColors(CharacterSelector.GetStatsFor(1 - CharacterSelector.SelectedIndex));
                _rig.Play(RunnerAnimState.Idle);
                HasRemote = true;
            }
        }

        public override void OnNetworkDespawn()
        {
            if (_owner && Local == this)
            {
                Local = null;
                HasRemote = false;
                RemoteZ = float.NegativeInfinity;
            }
        }

        private void Update()
        {
            float dt = Time.deltaTime;

            if (_owner)
            {
                PublishLocalState();
                _statsTimer -= dt;
                if (_statsTimer <= 0f && MatchStateManager.Instance != null &&
                    (MatchPhase)MatchStateManager.Instance.Phase.Value == MatchPhase.Running && _pc.IsAlive)
                {
                    _statsTimer = 0.5f;
                    SendStats();
                }
            }
            else
            {
                InterpolateRemote(dt);
            }
        }

        private void PublishLocalState()
        {
            var s = new RunnerNetState
            {
                Lane = (sbyte)_pc.CurrentLane,
                Z = transform.position.z,
                Y = transform.position.y,
                Speed = _pc.Speed,
                Flags = (byte)(
                    (_pc.IsAirborne ? RunnerFlags.Airborne : 0) |
                    (_pc.IsSliding ? RunnerFlags.Sliding : 0) |
                    (!_pc.IsAlive ? RunnerFlags.Dead : 0))
            };

            bool changed = Mathf.Abs(s.Z - _lastSent.Z) > 0.03f
                        || Mathf.Abs(s.Y - _lastSent.Y) > 0.02f
                        || s.Lane != _lastSent.Lane
                        || s.Flags != _lastSent.Flags;
            if (changed)
            {
                State.Value = s;
                _lastSent = s;
            }
        }

        private void InterpolateRemote(float dt)
        {
            var s = State.Value;
            var cfg = _cfg;
            float targetX = Constants.LaneX(s.Lane, cfg.laneWidth);
            var p = transform.position;
            p.x = p.x.Approach(targetX, 14f, dt);
            p.z = Mathf.Lerp(p.z, s.Z, 1f - Mathf.Exp(-16f * dt));
            p.y = Mathf.Lerp(p.y, s.Y, 1f - Mathf.Exp(-16f * dt));
            transform.position = p;
            RemoteZ = p.z;

            var flags = (RunnerFlags)s.Flags;
            RunnerAnimState target =
                (flags & RunnerFlags.Dead) != 0 ? RunnerAnimState.Dead :
                (flags & RunnerFlags.Sliding) != 0 ? RunnerAnimState.Slide :
                (flags & RunnerFlags.Airborne) != 0 ? RunnerAnimState.Jump :
                RunnerAnimState.Run;
            if (target != _remoteAnim)
            {
                _remoteAnim = target;
                _rig.Play(target);
            }
            _rig.SetLean(Mathf.Clamp((targetX - p.x) * 4f, -1f, 1f));
            _rig.Tick(Mathf.InverseLerp(cfg.baseSpeed, cfg.maxSpeed, s.Speed), dt);

            if ((flags & RunnerFlags.Victory) != 0 && _remoteAnim != RunnerAnimState.Victory)
            {
                _remoteAnim = RunnerAnimState.Victory;
                _rig.Play(RunnerAnimState.Victory);
            }
        }

        /// <summary>Remote-side victory pose, triggered by RunSceneController on results.</summary>
        public void PlayVictoryRemote()
        {
            var s = State.Value;
            s.Flags |= (byte)RunnerFlags.Victory;
            State.Value = s;
        }

        private void SendStats()
        {
            var m = MatchStateManager.Instance;
            if (m == null) return;
            int dist = Mathf.FloorToInt(_pc.Distance);
            int coins = RunManager.Instance != null ? RunManager.Instance.Coins : 0;
            m.SubmitStatsServerRpc(NetworkManager.LocalClientId, dist, coins, _pc.IsAlive);
        }

        private void OnLocalDeath()
        {
            int dist = Mathf.FloorToInt(_pc.Distance);
            int coins = RunManager.Instance != null ? RunManager.Instance.Coins : 0;
            if (IsServer)
            {
                var m = MatchStateManager.Instance;
                if (m != null) m.SubmitDeathServerRpc(NetworkManager.LocalClientId, dist, coins);
            }
            else
            {
                SubmitDeathServerRpc(NetworkManager.LocalClientId, dist, coins);
            }
        }

        [ServerRpc(RequireOwnership = false)]
        private void SubmitDeathServerRpc(ulong reporterClientId, int distance, int coins)
        {
            MatchStateManager.Instance?.SubmitDeathServerRpc(reporterClientId, distance, coins);
        }

        [ServerRpc(RequireOwnership = false)]
        private void SubmitStatsServerRpc(ulong reporterClientId, int distance, int coins, bool alive)
        {
            MatchStateManager.Instance?.SubmitStatsServerRpc(reporterClientId, distance, coins, alive);
        }
    }
}
