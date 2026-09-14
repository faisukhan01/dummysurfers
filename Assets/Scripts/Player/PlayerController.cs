using System;
using UnityEngine;
using DummySurfer.Audio;
using DummySurfer.Core;
using DummySurfer.Data;
using DummySurfer.Obstacles;
using DummySurfer.Powerups;
using DummySurfer.Utilities;

namespace DummySurfer.Player
{
    /// <summary>
    /// Local movement core (spec 3.1): automatic forward running, 3 lanes,
    /// swipe-driven lane change / jump / slide, stumble on non-lethal hits,
    /// lethal hits end the run. Input can arrive slightly early and is buffered
    /// so controls feel immediate even mid-animation (spec 4.3).
    /// Pure simulation — no networking concerns (NetworkPlayerSync mirrors state online).
    /// </summary>
    [RequireComponent(typeof(Rigidbody))]
    [RequireComponent(typeof(CapsuleCollider))]
    public sealed class PlayerController : MonoBehaviour
    {
        public bool IsLocal { get; private set; } = true;
        public bool IsAlive { get; private set; } = true;
        public bool SimEnabled { get; private set; }   // true during Running state
        public int CurrentLane { get; private set; } = 1;
        public float Speed { get; private set; }
        public bool IsSliding { get; private set; }
        public bool IsAirborne => transform.position.y > 0.02f;
        public float Distance => transform.position.z;
        public int CoinsRun => RunManager.Instance?.Coins ?? 0;

        public PowerupController Powerups { get; private set; }

        public event Action Died;                 // lethal hit (local player only)
        public event Action Stumbled;

        private GameConfig _cfg;
        private CharacterStats _stats;
        private DifficultyManager _diff;
        private RunnerVisualRig _rig;

        private Rigidbody _rb;
        private CapsuleCollider _col;
        private float _vy;
        private float _slideTimer;
        private float _stumbleTimer;
        private float _invulnTimer;

        // buffered input (spec 4.3 responsiveness)
        private int _bufferedLane;
        private int _bufferedJumpSlide;   // 0 none, 1 jump, 2 slide
        private float _bufferTimer;
        private float _visualLaneFrom, _visualLaneTo, _laneBlendT;

        public void Initialize(CharacterStats stats, bool isLocal)
        {
            _stats = stats;
            IsLocal = isLocal;
            _cfg = GameConfig.Runtime;
            _diff = DifficultyManager.Ensure();
            Powerups = GetComponent<PowerupController>();
            _rb = GetComponent<Rigidbody>();
            _rb.isKinematic = true;
            _rb.useGravity = false;
            _col = GetComponent<CapsuleCollider>();
            _rig = GetComponent<RunnerVisualRig>();
            _rig.ApplyColors(stats);
        }

        public void ResetForRun()
        {
            transform.position = new Vector3(Constants.LaneX(1, _cfg.laneWidth), 0f, 0f);
            CurrentLane = 1;
            IsAlive = true;
            SimEnabled = false;
            _vy = 0f;
            _slideTimer = 0f;
            _stumbleTimer = 0f;
            _invulnTimer = 0f;
            _bufferedLane = 0;
            _bufferedJumpSlide = 0;
            _bufferTimer = 0f;
            SetSlidePose(false);
            _rig?.Play(RunnerAnimState.Idle);
        }

        /// <summary>Gate set by RunSceneController when the countdown finishes.</summary>
        public void SetSimEnabled(bool on)
        {
            SimEnabled = on;
            if (on && IsAlive) _rig?.Play(RunnerAnimState.Run);
        }

        public void PlayVictory() => _rig?.Play(RunnerAnimState.Victory);

        private bool CanSim => IsLocal && IsAlive && SimEnabled && Time.timeScale > 0f;

        private void Update()
        {
            float dt = Time.deltaTime;
            if (dt <= 0f) return;

            _invulnTimer = Mathf.Max(0f, _invulnTimer - dt);
            if (_stumbleTimer > 0f) _stumbleTimer = Mathf.Max(0f, _stumbleTimer - dt);

            if (!CanSim)
            {
                _rig?.Tick(0f, dt);
                return;
            }

            // --- forward speed ---
            float stumbleFactor = _stumbleTimer > 0f ? _cfg.stumbleSlowFactor : 1f;
            Speed = _diff.SpeedAt(Distance) * _stats.speedMult * stumbleFactor;
            var p = transform.position;
            p.z += Speed * dt;

            // --- lanes (immediate target, exponential approach) ---
            float targetX = Constants.LaneX(CurrentLane, _cfg.laneWidth);
            float before = p.x;
            p.x = p.x.Approach(targetX, _cfg.laneLerpSpeed * _stats.laneMult, dt);
            float lateral = p.x - before;
            _rig?.SetLean(Mathf.Clamp(lateral * 6f, -1f, 1f));

            // --- vertical (jump physics) ---
            if (IsAirborne || _vy != 0f)
            {
                _vy += _cfg.gravity * dt;
                p.y += _vy * dt;
                if (p.y <= 0f)
                {
                    p.y = 0f;
                    _vy = 0f;
                    OnLanded();
                }
            }

            transform.position = p;

            // --- slide timer ---
            if (IsSliding)
            {
                _slideTimer -= dt;
                if (_slideTimer <= 0f) SetSlidePose(false);
            }

            // --- buffered actions ---
            if (_bufferTimer > 0f)
            {
                _bufferTimer -= dt;
                if (_bufferTimer <= 0f) { _bufferedJumpSlide = 0; _bufferedLane = 0; }
            }
            ExecuteBuffered();

            // --- run bookkeeping + anim ---
            RunManager.Instance?.ReportDistance(Distance);
            _rig?.Tick(Mathf.InverseLerp(_cfg.baseSpeed, _cfg.maxSpeed, Speed), dt);
        }

        // ---------------- input API ----------------

        public void RequestLane(int dir)
        {
            if (!IsAlive || !SimEnabled) return;
            int target = Constants.ClampLane(CurrentLane + dir);
            if (target == CurrentLane)
            {
                // bump feedback on edge lanes
                _rig?.SetLean(dir * 0.4f);
                return;
            }
            CurrentLane = target;
            var amLane = AudioManager.Instance; if (amLane != null) amLane.PlaySfx(SfxId.SlideWhoosh, 0.5f);
        }

        public void RequestJump()
        {
            if (!IsAlive || !SimEnabled) return;
            if (IsSliding) SetSlidePose(false);
            if (!IsAirborne)
            {
                _vy = _cfg.jumpVelocity * _stats.jumpMult;
                _rig?.Play(RunnerAnimState.Jump);
                var amJump = AudioManager.Instance; if (amJump != null) amJump.PlaySfx(SfxId.Jump);
            }
            else Buffer(1);
        }

        public void RequestSlide()
        {
            if (!IsAlive || !SimEnabled) return;
            if (IsAirborne)
            {
                _vy = Mathf.Min(_vy, _cfg.fastFallVelocity);   // fast-fall anticipation
                Buffer(2);
                return;
            }
            StartSlide();
        }

        private void Buffer(int action)
        {
            _bufferedJumpSlide = action;
            _bufferTimer = _cfg.inputBufferTime;
        }

        private void ExecuteBuffered()
        {
            if (_bufferedJumpSlide == 0 || IsAirborne || IsSliding) return;
            if (_bufferedJumpSlide == 1) RequestJump();
            else if (_bufferedJumpSlide == 2) StartSlide();
            _bufferedJumpSlide = 0;
        }

        private void OnLanded()
        {
            if (_bufferedJumpSlide == 2) { _bufferedJumpSlide = 0; StartSlide(); return; }
            _rig?.Play(RunnerAnimState.Run);
        }

        private void StartSlide()
        {
            IsSliding = true;
            _slideTimer = _cfg.slideDuration * _stats.slideMult;
            SetSlidePose(true);
            _rig?.Play(RunnerAnimState.Slide);
            var amSlide = AudioManager.Instance; if (amSlide != null) amSlide.PlaySfx(SfxId.SlideWhoosh);
        }

        private void SetSlidePose(bool on)
        {
            IsSliding = on;
            if (_col == null) return;
            float h = on ? _cfg.slideColliderHeight : _cfg.standColliderHeight;
            _col.height = h;
            _col.center = new Vector3(0f, h * 0.5f, 0f);
        }

        // ---------------- collisions ----------------

        /// <summary>Called by ObstacleBase triggers. Spec 3.1: lethal ends run; signs stumble;
        /// a held shield absorbs exactly one lethal hit (then brief i-frames).</summary>
        public void OnHitBy(ObstacleBase obstacle)
        {
            if (!IsLocal || !IsAlive || !SimEnabled) return;
            if (_invulnTimer > 0f) return;

            if (!obstacle.Lethal)
            {
                StumbleNow();
                return;
            }

            if (Powerups != null && Powerups.TryConsumeShield())
            {
                _invulnTimer = _cfg.shieldInvulnerability;
                _rig?.Flash(Color.white);
                Haptics.Impact();
                return;
            }

            Die();
        }

        private void StumbleNow()
        {
            _stumbleTimer = _cfg.stumbleDuration;
            _invulnTimer = Mathf.Max(_invulnTimer, _cfg.stumbleInvulnerability);
            _rig?.Play(RunnerAnimState.Stumble);
            var amStumble = AudioManager.Instance; if (amStumble != null) amStumble.PlaySfx(SfxId.Stumble);
            Haptics.Impact();
            Stumbled?.Invoke();
            GameEvents.PublishPlayerStumbled();
        }

        private void Die()
        {
            IsAlive = false;
            SimEnabled = false;
            Speed = 0f;
            _rig?.Play(RunnerAnimState.Dead);
            var amCrash = AudioManager.Instance; if (amCrash != null) amCrash.PlaySfx(SfxId.Crash);
            Haptics.Impact();
            var cam = Cam.RunnerCamera.Main;
            if (cam != null) cam.Shake(0.35f, 0.4f);
            GameEvents.PublishLocalPlayerDied();
            Died?.Invoke();
        }

        public void CollectCoin(int value)
        {
            if (!IsAlive) return;
            int multiplier = Powerups != null ? Powerups.CurrentMultiplier : 1;
            RunManager.Instance?.AddCoins(value * multiplier);
            var amCoin = AudioManager.Instance; if (amCoin != null) amCoin.PlaySfx(SfxId.Coin);
        }

        public void CollectPowerup(PowerupType type)
        {
            if (!IsAlive) return;
            Powerups?.Activate(type);
        }
    }
}
