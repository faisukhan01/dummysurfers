using UnityEngine;

namespace DummySurfer.Utilities
{
    /// <summary>Lightweight haptics with a user setting. Kept intentionally tiny.</summary>
    public static class Haptics
    {
        public static bool Enabled => PlayerPrefs.GetInt(Constants.Prefs.Haptics, 1) == 1;

        public static void Impact()
        {
            if (!Enabled) return;
#if UNITY_ANDROID && !UNITY_EDITOR
#pragma warning disable 618
            try { Handheld.Vibrate(); } catch { }
#pragma warning restore 618
#endif
        }
    }
}
