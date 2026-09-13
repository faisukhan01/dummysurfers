using UnityEngine;

namespace DummySurfer.Utilities
{
    /// <summary>Shared gameplay constants and PlayerPrefs keys.</summary>
    public static class Constants
    {
        public const int LaneCount = 3;
        public const float GroundY = 0f;

        public const string SceneBoot = "Boot";
        public const string SceneMenu = "MainMenu";
        public const string SceneGame = "Game";

        /// <summary>World X of a lane index (0 = left, 1 = center, 2 = right).</summary>
        public static float LaneX(int lane, float laneWidth) => (lane - 1) * laneWidth;

        /// <summary>Clamp a desired lane delta into valid range.</summary>
        public static int ClampLane(int lane) => Mathf.Clamp(lane, 0, LaneCount - 1);

        public static class Prefs
        {
            public const string MusicVolume = "ds_music_vol";
            public const string SfxVolume = "ds_sfx_vol";
            public const string Haptics = "ds_haptics";
            public const string Quality = "ds_quality";       // 0 low, 1 mid, 2 high
            public const string ShowFps = "ds_show_fps";
            public const string Character = "ds_character";   // 0 Juno, 1 Kai
            public const string BestDistance = "ds_best_dist";
            public const string BestCoins = "ds_best_coins";
            public const string TotalRuns = "ds_total_runs";
            public const string LastJoinCode = "ds_last_code";
            public const string ServiceRegion = "ds_relay_region";
        }
    }
}
