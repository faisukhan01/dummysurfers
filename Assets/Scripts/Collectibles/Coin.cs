using UnityEngine;
using DummySurfer.Player;
using DummySurfer.Powerups;

namespace DummySurfer.Collectibles
{
    /// <summary>Pooled coin with spin, bob and magnet attraction (local effect, spec 7.2).</summary>
    public sealed class Coin : MonoBehaviour
    {
        public static bool MagnetActive;
        public static Transform MagnetTarget;
        public static float MagnetRadius = 6.5f;

        public bool Returned { get; set; }

        private float _bobPhase;

        private void OnEnable()
        {
            Returned = false;
            _bobPhase = Random.value * Mathf.PI * 2f;
        }

        private void Update()
        {
            float dt = Time.deltaTime;
            transform.Rotate(0f, 240f * dt, 0f, Space.Self);

            if (MagnetActive && MagnetTarget != null)
            {
                Vector3 toTarget = (MagnetTarget.position + Vector3.up * 0.8f) - transform.position;
                if (toTarget.sqrMagnitude < MagnetRadius * MagnetRadius)
                    transform.position += toTarget.normalized * (16f * dt);
            }
            else
            {
                var lp = transform.localPosition;
                lp.y += Mathf.Sin(Time.time * 3f + _bobPhase) * 0.15f * dt;
                transform.localPosition = lp;
            }
        }

        private void OnTriggerEnter(Collider other)
        {
            var player = other.GetComponentInParent<PlayerController>();
            if (player == null || !player.IsAlive) return;
            player.CollectCoin(1);
            CollectibleManager.Ensure().ReturnCoin(this);
        }
    }
}
