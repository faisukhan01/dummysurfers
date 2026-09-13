using DummySurfer.Data;
using DummySurfer.Utilities;

namespace DummySurfer.Core
{
    /// <summary>
    /// Single read-point for difficulty tuning: speed at distance (per-player),
    /// and deterministic content difficulty by chunk index (shared by both clients).
    /// </summary>
    public sealed class DifficultyManager : PersistentManager<DifficultyManager>
    {
        private GameConfig _cfg;
        private DifficultyCurve _curve;

        public GameConfig Cfg => _cfg ??= GameConfig.Runtime;
        public DifficultyCurve Curve => _curve ??= DifficultyCurve.Runtime;

        protected override void OnManagerAwake()
        {
            ServiceRegistry.Register(this);
        }

        /// <summary>Run speed in m/s for the LOCAL player's traveled distance.</summary>
        public float SpeedAt(float distanceMeters)
        {
            return Curve.SpeedAt(distanceMeters, Cfg);
        }

        /// <summary>
        /// Content difficulty for a chunk index — pure function of the index so both
        /// clients produce identical track content from the shared seed (spec 10).
        /// </summary>
        public float ContentDifficulty01(int chunkIndex)
        {
            return Curve.Difficulty01(chunkIndex);
        }
    }
}
