using System.Collections.Generic;
using UnityEngine;
using DummySurfer.Collectibles;
using DummySurfer.Core;
using DummySurfer.Data;
using DummySurfer.Obstacles;
using DummySurfer.Utilities;

namespace DummySurfer.Track
{
    /// <summary>
    /// Chunk spawning/recycling (spec 5.2/6.1): keeps only a small window of chunks ahead of the
    /// furthest player, recycles behind the SLOWEST target (both players online), and manages
    /// purely-decorative far-rail trains (kept local, never gameplay-critical — spec 7.2).
    /// </summary>
    public sealed class TrackManager : PersistentManager<TrackManager>
    {
        private readonly Dictionary<int, TrackChunk> _active = new Dictionary<int, TrackChunk>(16);
        private ObjectPool<TrackChunk> _pool;
        private ObjectPool<DecorTrain> _decorPool;
        private readonly List<DecorTrain> _liveDecor = new List<DecorTrain>(4);

        private ulong _seed;
        private GameConfig _cfg;
        private ObstacleManager _om;
        private CollectibleManager _cm;

        private float _minTargetZ, _maxTargetZ;
        private float _decorCooldown;

        /// <summary>World speed used by decorative elements (set from the local player).</summary>
        public float CurrentSpeed { get; set; }

        protected override void OnManagerAwake()
        {
            ServiceRegistry.Register(this);
            _cfg = GameConfig.Runtime;
            _om = ObstacleManager.Ensure();
            _cm = CollectibleManager.Ensure();
            _pool = new ObjectPool<TrackChunk>(PoolManager.Ensure().Chunks,
                () => TrackChunk.Build(PoolManager.Ensure().Chunks, _cfg), prewarm: 0);
            _decorPool = new ObjectPool<DecorTrain>(PoolManager.Ensure().Obstacles, BuildDecorTrain, prewarm: 0);
        }

        private DecorTrain BuildDecorTrain()
        {
            var t = ObstacleFactory.BuildDecorTrain(_cfg);
            return t;
        }

        public void BeginRun(ulong seed)
        {
            ResetAll();
            _seed = seed;
            _minTargetZ = 0f;
            _maxTargetZ = 0f;
            _decorCooldown = 3f;
            EnsureChunksUpTo(_cfg.chunksAhead);
        }

        /// <summary>RunSceneController feeds local + remote Z each frame.</summary>
        public void SetTargets(float localZ, float remoteZ = float.NegativeInfinity)
        {
            _minTargetZ = localZ;
            _maxTargetZ = localZ;
            if (!float.IsNegativeInfinity(remoteZ))
            {
                _minTargetZ = Mathf.Min(_minTargetZ, remoteZ);
                _maxTargetZ = Mathf.Max(_maxTargetZ, remoteZ);
            }
        }

        private void Update()
        {
            if (_active.Count == 0) return;
            MaintainChunks();
            MaintainDecor();
        }

        private void MaintainChunks()
        {
            int head = Mathf.FloorToInt((_maxTargetZ + _cfg.chunksAhead * _cfg.chunkLength) / _cfg.chunkLength);
            EnsureChunksUpTo(head);

            int tail = Mathf.FloorToInt((_minTargetZ - _cfg.chunksBehind * _cfg.chunkLength) / _cfg.chunkLength) - 1;
            List<int> dead = null;
            foreach (var kv in _active)
            {
                if (kv.Key < tail)
                {
                    dead ??= new List<int>(4);
                    dead.Add(kv.Key);
                }
            }
            if (dead != null)
            {
                foreach (int idx in dead) DespawnChunk(idx);
            }
        }

        private void EnsureChunksUpTo(int headIndex)
        {
            for (int i = 0; i <= headIndex; i++)
            {
                if (_active.ContainsKey(i)) continue;
                var chunk = _pool.Get();
                chunk.Populate(i, _seed, _om, _cm);
                _active[i] = chunk;
            }
        }

        private void DespawnChunk(int index)
        {
            if (!_active.TryGetValue(index, out var chunk)) return;
            chunk.Clear();
            _pool.Release(chunk);
            _active.Remove(index);
        }

        private void MaintainDecor()
        {
            // Spawn
            _decorCooldown -= Time.deltaTime;
            if (_decorCooldown <= 0f && _liveDecor.Count < 2 && GameConfig.Runtime.decoyTrainChance > 0f)
            {
                _decorCooldown = Random.Range(4f, 9f);
                if (Random.value < GameConfig.Runtime.decoyTrainChance)
                {
                    var train = _decorPool.Get();
                    train.transform.position = new Vector3(
                        Random.value < 0.5f ? -6.6f : 6.6f,
                        1.35f,
                        _maxTargetZ + 130f);
                    train.Speed = CurrentSpeed * 0.55f;
                    _liveDecor.Add(train);
                }
            }

            // Despawn behind
            for (int i = _liveDecor.Count - 1; i >= 0; i--)
            {
                if (_liveDecor[i].transform.position.z < _minTargetZ - 60f)
                {
                    _decorPool.Release(_liveDecor[i]);
                    _liveDecor.RemoveAt(i);
                }
            }
        }

        public void ResetAll()
        {
            var keys = new List<int>(_active.Keys);
            foreach (int k in keys) DespawnChunk(k);
            for (int i = 0; i < _liveDecor.Count; i++) _decorPool.Release(_liveDecor[i]);
            _liveDecor.Clear();
        }
    }
}
