using UnityEngine;

namespace DummySurfer.Data
{
    /// <summary>
    /// Difficulty curves (spec 5.3, 6.2): speed, density and pattern complexity all ramp
    /// gradually and are driven by data, not code constants.
    /// NOTE: chunk CONTENT difficulty is a pure function of (seed, chunkIndex) so both
    /// clients generate identical worlds (spec 10 DETERMINISTIC TRACK).
    /// </summary>
    [CreateAssetMenu(menuName = "Dummy Surfer/Difficulty Curve", fileName = "DifficultyCurve")]
    public class DifficultyCurve : ScriptableObject
    {
        [Header("Speed over run distance (meters)")]
        public float rampDistance = 3500f;        // distance to reach max speed
        public AnimationCurve speedOverDistance = new AnimationCurve(
            new Keyframe(0f, 0f),       // 0 => base speed
            new Keyframe(1f, 1f));      // 1 => max speed (linear by default)

        [Header("Chunk content difficulty by chunk index")]
        public float difficultyRampChunks = 25f;  // chunks until hardest content
        [Range(0f, 1f)] public float obstacleChanceStart = 0.34f;
        [Range(0f, 1f)] public float obstacleChanceEnd = 0.82f;
        [Range(0f, 1f)] public float trainChanceStart = 0.06f;
        [Range(0f, 1f)] public float trainChanceEnd = 0.34f;
        [Range(0f, 1f)] public float doubleBarrierChanceStart = 0.02f;
        [Range(0f, 1f)] public float doubleBarrierChanceEnd = 0.30f;
        [Range(0f, 1f)] public float signChance = 0.10f;
        public float rowSpacingStart = 7.0f;
        public float rowSpacingEnd = 5.2f;

        private static DifficultyCurve _runtime;
        public static DifficultyCurve Runtime
        {
            get
            {
                if (_runtime != null) return _runtime;
                _runtime = Resources.Load<DifficultyCurve>("Config/DifficultyCurve");
                if (_runtime == null)
                {
                    _runtime = CreateInstance<DifficultyCurve>();
                    _runtime.name = "DifficultyCurve (runtime defaults)";
                }
                return _runtime;
            }
        }

        /// <summary>Normalized content difficulty for a chunk index (0..1).</summary>
        public float Difficulty01(int chunkIndex)
            => Mathf.Clamp01(chunkIndex * 1f / Mathf.Max(1f, difficultyRampChunks));

        /// <summary>Actual run speed in m/s for a traveled distance.</summary>
        public float SpeedAt(float distance, GameConfig cfg)
        {
            float t = speedOverDistance.Evaluate(Mathf.Clamp01(distance / Mathf.Max(1f, rampDistance)));
            return Mathf.Lerp(cfg.baseSpeed, cfg.maxSpeed, t);
        }

        public float Lerp(float a, float b, float d01) => Mathf.Lerp(a, b, d01);
    }
}
