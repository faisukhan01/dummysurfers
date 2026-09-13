using DummySurfer.Data;

namespace DummySurfer.Player
{
    /// <summary>Character selection persisted in PlayerPrefs (JUNO = 0, KAI = 1).</summary>
    public static class CharacterSelector
    {
        public static int SelectedIndex
        {
            get => UnityEngine.PlayerPrefs.GetInt(Constants.Prefs.Character, 0);
            set => UnityEngine.PlayerPrefs.SetInt(Constants.Prefs.Character, UnityEngine.Mathf.Clamp(value, 0, 1));
        }

        public static CharacterStats GetSelectedStats() => CharacterStats.Load(SelectedIndex);

        public static CharacterStats GetStatsFor(int index) => CharacterStats.Load(index);
    }
}
