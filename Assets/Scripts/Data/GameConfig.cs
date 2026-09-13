using UnityEngine;

namespace DummySurfer.Data
{
    /// <summary>
    /// Master tuning table for the whole game (spec 5.3: data-driven, no hard-coded tunables
    /// inside MonoBehaviours). A defaults-filled instance is also created at runtime if the
    /// asset is missing, so the game always boots. Edit via Tools > Dummy Surfer wizard assets.
    /// </summary>
    [CreateAssetMenu(menuName = "Dummy Surfer/Game Config", fileName = "GameConfig")]
    public class GameConfig : ScriptableObject
    {
        [Header("Lanes & Feel")]
        public float laneWidth = 2.2f;
        public float laneLerpSpeed = 13f;
        public float jumpVelocity = 9.6f;
        public float gravity = -26f;
        public float fastFallVelocity = -14f;
        public float slideDuration = 0.8f;
        public float slideColliderHeight = 0.85f;
        public float standColliderHeight = 1.7f;

        [Header("Stumble (non-lethal sign hit)")]
        public float stumbleSlowFactor = 0.55f;
        public float stumbleDuration = 1.0f;
        public float stumbleInvulnerability = 0.5f;

        [Header("Speed (m/s)")]
        public float baseSpeed = 9f;
        public float maxSpeed = 22f;

        [Header("Track")]
        public float chunkLength = 60f;
        public int chunksAhead = 4;
        public int chunksBehind = 2;
        public float decoyTrainChance = 0.35f;   // decorative far-rail trains (local only)

        [Header("Match")]
        public float countdownSeconds = 3f;
        public float deathComparisonWindowSeconds = 5f;  // spec 3.2 configurable window

        [Header("Power-ups")]
        public float magnetRadius = 6.5f;
        public float magnetDuration = 8f;
        public float shieldInvulnerability = 1.4f;
        public float multiplierDuration = 8f;
        public int multiplierValue = 2;
        public float powerupChancePerChunk = 0.16f;

        [Header("Input")]
        public float swipeThresholdPx = 42f;
        public float swipeMaxDuration = 0.4f;
        public float inputBufferTime = 0.22f;

        [Header("Pools (prewarm counts)")]
        public int poolChunks = 14;
        public int poolCoins = 260;
        public int poolObstacles = 90;
        public int poolPowerups = 8;

        [Header("Performance")]
        public int targetFrameRate = 60;
        public float fogStart = 55f;
        public float fogEnd = 150f;

        [Header("Camera")]
        public float camHeight = 4.6f;
        public float camBack = 7.6f;
        public float camBaseFov = 60f;
        public float camSpeedFov = 12f;

        [Header("Online")]
        public float netOperationTimeoutSeconds = 15f;
        public int maxNetRetries = 2;

        // ---- Runtime fallback ----
        private static GameConfig _runtime;
        public static GameConfig Runtime
        {
            get
            {
                if (_runtime != null) return _runtime;
                _runtime = Resources.Load<GameConfig>("Config/GameConfig");
                if (_runtime == null)
                {
                    _runtime = CreateInstance<GameConfig>(); // safe defaults
                    _runtime.name = "GameConfig (runtime defaults)";
                }
                return _runtime;
            }
        }
    }
}
