using UnityEngine;
using DummySurfer.Utilities;

namespace DummySurfer.Data
{
    /// <summary>
    /// Per-difficulty weights for obstacle archetypes (spec 6.2 SPAWN RULES).
    /// The deterministic generator reads weights from here so designers can retune
    /// patterns without touching code.
    /// </summary>
    [CreateAssetMenu(menuName = "Dummy Surfer/Obstacle Catalog", fileName = "ObstacleCatalog")]
    public class ObstacleCatalog : ScriptableObject
    {
        [Header("Relative weights at difficulty 0 -> 1 (linear lerp)")]
        [Tooltip("Full-height lethal train occupying one lane for several rows.")]
        public Vector2 trainWeight = new Vector2(0.10f, 0.42f);
        [Tooltip("Low lethal barrier - must jump.")]
        public Vector2 barrierWeight = new Vector2(0.40f, 0.30f);
        [Tooltip("Overhead gantry - must slide.")]
        public Vector2 overheadWeight = new Vector2(0.34f, 0.20f);
        [Tooltip("Non-lethal warning sign - stumble (slowdown) on hit.")]
        public Vector2 signWeight = new Vector2(0.16f, 0.08f);

        [Header("Train shape")]
        public int trainRowsMin = 3;
        public int trainRowsMax = 7;

        [Header("Coins")]
        public int coinRunMin = 4;
        public int coinRunMax = 9;
        [Range(0f, 1f)] public float coinArcChance = 0.25f;   // arc over a barrier

        private static ObstacleCatalog _runtime;
        public static ObstacleCatalog Runtime
        {
            get
            {
                if (_runtime != null) return _runtime;
                _runtime = Resources.Load<ObstacleCatalog>("Config/ObstacleCatalog");
                if (_runtime == null)
                {
                    _runtime = CreateInstance<ObstacleCatalog>();
                    _runtime.name = "ObstacleCatalog (runtime defaults)";
                }
                return _runtime;
            }
        }

        public float Lerp(Vector2 w, float d01) => Mathf.Lerp(w.x, w.y, d01);
    }
}
