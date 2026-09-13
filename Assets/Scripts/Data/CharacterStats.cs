using UnityEngine;

namespace DummySurfer.Data
{
    /// <summary>
    /// Stats + colorway for one original runner character (spec 10 CHARACTERS:
    /// two original runners, distinct silhouettes, no reproduction of any named
    /// Subway Surfers character).
    /// </summary>
    [CreateAssetMenu(menuName = "Dummy Surfer/Character Stats", fileName = "CharacterStats")]
    public class CharacterStats : ScriptableObject
    {
        [Header("Identity")]
        public string displayName = "JUNO";
        [TextArea] public string blurb = "Balanced street sprinter. Teal jacket, orange cap.";

        [Header("Tuning (multipliers)")]
        public float speedMult = 1.0f;
        public float jumpMult = 1.0f;
        public float laneMult = 1.0f;
        public float slideMult = 1.0f;

        [Header("Colorway")]
        public Color primary = new Color(0.184f, 0.835f, 0.784f);   // teal
        public Color secondary = new Color(1f, 0.48f, 0.18f);       // orange
        public Color skin = new Color(0.94f, 0.76f, 0.62f);
        public Color pants = new Color(0.16f, 0.18f, 0.22f);
        public Color shoes = new Color(0.9f, 0.9f, 0.93f);

        public static CharacterStats Default(int index)
        {
            var s = CreateInstance<CharacterStats>();
            if (index == 0)
            {
                s.displayName = "JUNO";
                s.blurb = "Balanced sprinter. Reliable in every lane.";
                s.primary = new Color(0.184f, 0.835f, 0.784f);  // teal accent
                s.secondary = new Color(1f, 0.48f, 0.18f);      // orange cap
            }
            else
            {
                s.displayName = "KAI";
                s.blurb = "Quick lane changes, slightly floatier jumps.";
                s.primary = new Color(0.90f, 0.28f, 0.61f);
                s.secondary = new Color(1f, 0.79f, 0.24f);
                s.laneMult = 1.12f;
                s.jumpMult = 1.05f;
                s.speedMult = 0.99f;
            }
            s.name = s.displayName;
            return s;
        }

        public static CharacterStats Load(int index)
        {
            var loaded = Resources.Load<CharacterStats>($"Characters/{(index == 0 ? "Juno" : "Kai")}");
            return loaded != null ? loaded : Default(index);
        }
    }
}
