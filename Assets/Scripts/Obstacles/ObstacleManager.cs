using System.Collections.Generic;
using UnityEngine;
using DummySurfer.Core;
using DummySurfer.Data;
using DummySurfer.Track;
using DummySurfer.Utilities;

namespace DummySurfer.Obstacles
{
    /// <summary>Pooled obstacle lifecycle (spec 5.2/6.3). No Instantiate/Destroy during runs.</summary>
    public sealed class ObstacleManager : PersistentManager<ObstacleManager>
    {
        private readonly Dictionary<ObstacleKind, ObjectPool<ObstacleBase>> _pools =
            new Dictionary<ObstacleKind, ObjectPool<ObstacleBase>>(4);

        protected override void OnManagerAwake()
        {
            ServiceRegistry.Register(this);
            var cfg = GameConfig.Runtime;
            int prewarm = Mathf.Max(2, cfg.poolObstacles / 8);
            foreach (ObstacleKind kind in new[] { ObstacleKind.Train, ObstacleKind.Barrier, ObstacleKind.Overhead, ObstacleKind.Sign })
            {
                var k = kind;
                _pools[kind] = new ObjectPool<ObstacleBase>(
                    PoolManager.Ensure().Obstacles,
                    () => ObstacleFactory.Build(k),
                    prewarm: prewarm);
            }
        }

        public ObstacleBase Spawn(ObstacleKind kind, Transform chunkParent, Vector3 localPosition, float length)
        {
            if (!_pools.TryGetValue(kind, out var pool)) return null;
            var ob = pool.Get();
            ob.transform.SetParent(chunkParent, false);
            ob.OnSpawned(localPosition, length);
            return ob;
        }

        public void Return(ObstacleBase obstacle)
        {
            if (obstacle == null) return;
            if (_pools.TryGetValue(obstacle.Kind, out var pool))
            {
                obstacle.OnReturned();
                pool.Release(obstacle);
            }
        }
    }
}
