using UnityEngine;
using DummySurfer.Data;
using DummySurfer.Player;
using DummySurfer.Powerups;
using DummySurfer.Utilities;

namespace DummySurfer.Collectibles
{
    /// <summary>Pooled power-up pickup box (Magnet / Shield / 2x).</summary>
    public sealed class PowerupPickup : MonoBehaviour
    {
        public PowerupType Type { get; private set; } = PowerupType.Magnet;
        public bool Returned { get; set; }

        private static readonly Color MagnetColor = VisualStyles.Accent;
        private static readonly Color ShieldColor = VisualStyles.Gold;
        private static readonly Color MultiplierColor = VisualStyles.Primary;

        public void SetType(PowerupType type)
        {
            Type = type;
            Color c = type switch
            {
                PowerupType.Magnet => MagnetColor,
                PowerupType.Shield => ShieldColor,
                PowerupType.Multiplier => MultiplierColor,
                _ => Color.white
            };
            var shell = transform.Find("Shell");
            if (shell != null)
            {
                var r = shell.GetComponent<MeshRenderer>();
                if (r != null) r.sharedMaterial = VisualStyles.Lit(c);
            }
        }

        private void Update()
        {
            transform.Rotate(0f, 120f * Time.deltaTime, 0f, Space.Self);
            var lp = transform.localPosition;
            lp.y += Mathf.Sin(Time.time * 2.4f) * 0.2f * Time.deltaTime;
            transform.localPosition = lp;
        }

        private void OnTriggerEnter(Collider other)
        {
            var player = other.GetComponentInParent<PlayerController>();
            if (player == null || !player.IsAlive) return;
            player.CollectPowerup(Type);
            CollectibleManager.Ensure().ReturnPowerup(this);
        }
    }
}
