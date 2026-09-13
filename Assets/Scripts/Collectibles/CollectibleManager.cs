using UnityEngine;
using DummySurfer.Core;
using DummySurfer.Data;
using DummySurfer.Powerups;
using DummySurfer.Utilities;

namespace DummySurfer.Collectibles
{
    /// <summary>Pooled coins & power-ups lifecycle (spec 6.3).</summary>
    public sealed class CollectibleManager : PersistentManager<CollectibleManager>
    {
        private ObjectPool<Coin> _coins;
        private ObjectPool<PowerupPickup> _powerups;

        protected override void OnManagerAwake()
        {
            ServiceRegistry.Register(this);
            var cfg = GameConfig.Runtime;

            _coins = new ObjectPool<Coin>(PoolManager.Ensure().Coins, BuildCoin,
                onGet: c => c.Returned = false,
                prewarm: Mathf.Clamp(cfg.poolCoins / 4, 24, 80));

            _powerups = new ObjectPool<PowerupPickup>(PoolManager.Ensure().Powerups, BuildPowerup,
                onGet: p => p.Returned = false,
                prewarm: 4);
        }

        private Coin BuildCoin()
        {
            var root = new GameObject("Coin");
            var col = root.AddComponent<BoxCollider>();
            col.isTrigger = true;
            col.center = new Vector3(0f, 0f, 0f);
            col.size = new Vector3(0.85f, 1.3f, 0.85f);

            var mesh = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            Object.Destroy(mesh.GetComponent<Collider>());
            mesh.name = "Mesh";
            mesh.transform.SetParent(root.transform, false);
            mesh.transform.localPosition = Vector3.zero;
            mesh.transform.localRotation = Quaternion.Euler(90f, 0f, 0f);
            mesh.transform.localScale = new Vector3(0.7f, 0.07f, 0.7f);
            VisualStyles.AddMesh(mesh, VisualStyles.Gold, unlit: true);

            return root.AddComponent<Coin>();
        }

        private PowerupPickup BuildPowerup()
        {
            var root = new GameObject("Powerup");
            var col = root.AddComponent<BoxCollider>();
            col.isTrigger = true;
            col.size = new Vector3(1.0f, 1.4f, 1.0f);

            var shell = GameObject.CreatePrimitive(PrimitiveType.Cube);
            Object.Destroy(shell.GetComponent<Collider>());
            shell.name = "Shell";
            shell.transform.SetParent(root.transform, false);
            shell.transform.localPosition = Vector3.zero;
            shell.transform.localScale = new Vector3(0.55f, 0.55f, 0.55f);
            VisualStyles.AddMesh(shell, VisualStyles.Accent);

            var core = GameObject.CreatePrimitive(PrimitiveType.Cube);
            Object.Destroy(core.GetComponent<Collider>());
            core.name = "Core";
            core.transform.SetParent(shell.transform, false);
            core.transform.localScale = new Vector3(0.42f, 0.42f, 0.42f);
            VisualStyles.AddMesh(core, Color.white, unlit: true);

            return root.AddComponent<PowerupPickup>();
        }

        public Coin SpawnCoin(Transform chunkParent, Vector3 localPosition)
        {
            var coin = _coins.Get();
            coin.transform.SetParent(chunkParent, false);
            coin.transform.localPosition = localPosition;
            return coin;
        }

        public PowerupPickup SpawnPowerup(PowerupType type, Transform chunkParent, Vector3 localPosition)
        {
            var pu = _powerups.Get();
            pu.transform.SetParent(chunkParent, false);
            pu.transform.localPosition = localPosition;
            pu.SetType(type);
            return pu;
        }

        public void ReturnCoin(Coin coin)
        {
            if (coin == null || coin.Returned) return;
            coin.Returned = true;
            _coins.Release(coin);
        }

        public void ReturnPowerup(PowerupPickup pu)
        {
            if (pu == null || pu.Returned) return;
            pu.Returned = true;
            _powerups.Release(pu);
        }
    }
}
