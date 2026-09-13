using System.Reflection;
using UnityEngine;
using DummySurfer.Utilities;

namespace DummySurfer.Core
{
    /// <summary>
    /// Target 60 FPS + low/mid/high quality tiers + optional auto-degrade
    /// (spec 10 PERFORMANCE: quality tiers, resolution scaling, profile-driven tuning).
    /// Pipeline asset properties are touched via reflection so the game also runs on
    /// templates without URP (degrades gracefully).
    /// </summary>
    public sealed class PerformanceMonitor : PersistentManager<PerformanceMonitor>
    {
        public const float DegradeFps = 45f;
        public const float DegradeSeconds = 4f;

        public int QualityTier { get; private set; } = 2;
        public float AverageFps { get; private set; } = 60f;
        public bool AutoDegrade { get; set; } = true;

        private float _accumTime;
        private int _accumFrames;
        private float _lowStreak;
        private bool _degradedOnce;

        private static readonly float[] RenderScales = { 0.66f, 0.85f, 1.0f };

        protected override void OnManagerAwake()
        {
            ServiceRegistry.Register(this);
            Application.targetFrameRate = GameConfig.Runtime.targetFrameRate;
            QualitySettings.vSyncCount = 0;
            SetTier(PlayerPrefs.GetInt(Constants.Prefs.Quality, 2), save: false);
        }

        private void Update()
        {
            _accumTime += Time.unscaledDeltaTime;
            _accumFrames++;
            if (_accumTime >= 0.5f && _accumFrames > 0)
            {
                AverageFps = _accumFrames / _accumTime;
                _accumTime = 0f;
                _accumFrames = 0;

                if (AutoDegrade && !_degradedOnce && QualityTier > 0 && Time.unscaledTime > 15f)
                {
                    if (AverageFps < DegradeFps) _lowStreak += 0.5f; else _lowStreak = 0f;
                    if (_lowStreak >= DegradeSeconds)
                    {
                        _degradedOnce = true;
                        SetTier(QualityTier - 1, save: true);
                        GameEvents.PublishToast($"Performance: switched quality to {(QualityTier == 0 ? "LOW" : QualityTier == 1 ? "MID" : "HIGH")}");
                    }
                }
            }
        }

        public void SetTier(int tier, bool save = true)
        {
            QualityTier = Mathf.Clamp(tier, 0, 2);
            if (save) PlayerPrefs.SetInt(Constants.Prefs.Quality, QualityTier);

            float scale = RenderScales[QualityTier];
            TrySetRenderScale(scale);

            QualitySettings.shadowDistance = QualityTier == 0 ? 0f : 45f;
            QualitySettings.shadows = QualityTier == 0 ? ShadowQuality.Disable : ShadowQuality.HardOnly;
        }

        private void TrySetRenderScale(float scale)
        {
            try
            {
                var pipeline = GraphicsSettings.currentRenderPipeline;
                if (pipeline == null) return;
                var prop = pipeline.GetType().GetProperty("renderScale",
                    BindingFlags.Public | BindingFlags.Instance);
                if (prop != null && prop.CanWrite)
                    prop.SetValue(pipeline, scale);
            }
            catch { /* non-URP pipeline or property moved — degrade silently */ }
        }
    }
}
