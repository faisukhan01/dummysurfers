using UnityEngine;
using DummySurfer.Collectibles;
using DummySurfer.Core;
using DummySurfer.Data;

namespace DummySurfer.Powerups
{
    /// <summary>
    /// Local power-up state machine: Magnet (coin attraction), Shield (survive one lethal hit),
    /// 2x Multiplier. Effects are local-only (spec 7.2); shield matters for death reporting,
    /// which each owning client reports authoritatively for its own runner (documented trade-off).
    /// </summary>
    public sealed class PowerupController : MonoBehaviour
    {
        public bool ShieldHeld { get; private set; }
        public int CurrentMultiplier { get; private set; } = 1;

        private GameConfig _cfg;
        private float _magnetTimer;
        private float _multTimer;
        private PlayerController _player;

        private void Awake()
        {
            _cfg = GameConfig.Runtime;
            _player = GetComponent<PlayerController>();
        }

        public void Activate(PowerupType type)
        {
            switch (type)
            {
                case PowerupType.Magnet:
                    _magnetTimer = _cfg.magnetDuration;
                    if (_player != null && _player.IsLocal)
                    {
                        Coin.MagnetActive = true;
                        Coin.MagnetTarget = transform;
                        Coin.MagnetRadius = _cfg.magnetRadius;
                    }
                    break;

                case PowerupType.Shield:
                    ShieldHeld = true;
                    break;

                case PowerupType.Multiplier:
                    _multTimer = _cfg.multiplierDuration;
                    CurrentMultiplier = _cfg.multiplierValue;
                    break;
            }
            GameEvents.PublishPowerupStarted(type, DurationOf(type));
        }

        private float DurationOf(PowerupType t) => t switch
        {
            PowerupType.Magnet => _cfg.magnetDuration,
            PowerupType.Multiplier => _cfg.multiplierDuration,
            _ => 0f
        };

        public bool TryConsumeShield()
        {
            if (!ShieldHeld) return false;
            ShieldHeld = false;
            GameEvents.PublishShieldConsumed();
            return true;
        }

        private void Update()
        {
            if (_magnetTimer > 0f)
            {
                _magnetTimer -= Time.deltaTime;
                if (_magnetTimer <= 0f)
                {
                    if (_player != null && _player.IsLocal) Coin.MagnetActive = false;
                    GameEvents.PublishPowerupEnded(PowerupType.Magnet);
                }
            }
            if (_multTimer > 0f)
            {
                _multTimer -= Time.deltaTime;
                if (_multTimer <= 0f)
                {
                    CurrentMultiplier = 1;
                    GameEvents.PublishPowerupEnded(PowerupType.Multiplier);
                }
            }
        }

        public void ResetForRun()
        {
            _magnetTimer = 0f;
            _multTimer = 0f;
            ShieldHeld = false;
            CurrentMultiplier = 1;
            if (_player != null && _player.IsLocal) Coin.MagnetActive = false;
        }
    }
}
