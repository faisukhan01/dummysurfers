using UnityEngine;
using DummySurfer.Utilities;

namespace DummySurfer.Core
{
    /// <summary>
    /// Owns pool parent roots so recycled objects never pollute the scene hierarchy.
    /// Actual pools live in TrackManager / ObstacleManager / CollectibleManager.
    /// </summary>
    public sealed class PoolManager : PersistentManager<PoolManager>
    {
        public Transform Chunks { get; private set; }
        public Transform Obstacles { get; private set; }
        public Transform Coins { get; private set; }
        public Transform Powerups { get; private set; }
        public Transform Fx { get; private set; }

        protected override void OnManagerAwake()
        {
            ServiceRegistry.Register(this);
            Chunks = MakeChild("Chunks");
            Obstacles = MakeChild("Obstacles");
            Coins = MakeChild("Coins");
            Powerups = MakeChild("Powerups");
            Fx = MakeChild("Fx");
        }

        private Transform MakeChild(string name)
        {
            var t = new GameObject(name).transform;
            t.SetParent(transform, false);
            return t;
        }
    }
}
