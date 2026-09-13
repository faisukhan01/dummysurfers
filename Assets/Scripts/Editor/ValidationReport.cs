using System.IO;
using System.Text;
using UnityEditor;
using UnityEngine;
using UnityEngine.Rendering;
using DummySurfer.Data;

namespace DummySurfer.EditorTools
{
    /// <summary>Fast sanity checks after setup; surfaces problems before the first Play.</summary>
    public static class ValidationReport
    {
        public static string RunAndCollect()
        {
            var sb = new StringBuilder();
            int problems = 0;

            void Check(bool ok, string label, string fix)
            {
                if (ok)
                {
                    sb.AppendLine("  ✓ " + label);
                }
                else
                {
                    problems++;
                    sb.AppendLine("  ✗ " + label + "\n      → " + fix);
                }
            }

            sb.AppendLine("VALIDATION REPORT");

            Check(File.Exists("Packages/manifest.json"), "Package manifest present", "Re-clone the repo.");
            Check(File.Exists("Assets/Resources/NetworkRunner.prefab"),
                "NetworkRunner prefab exists",
                "Run Tools > Dummy Surfer > 3. Rebuild Prefabs + Data Assets.");
            Check(AssetDatabase.LoadAssetAtPath<GameConfig>("Assets/ScriptableObjects/Resources/Config/GameConfig.asset") != null,
                "GameConfig asset exists",
                "Run Tools > Dummy Surfer > 3. Rebuild Prefabs + Data Assets.");
            Check(File.Exists("Assets/Scenes/Boot.unity") && File.Exists("Assets/Scenes/Game.unity"),
                "Scenes built",
                "Run Tools > Dummy Surfer > 2. Rebuild Scenes + Build Settings.");
            Check(EditorBuildSettings.scenes.Length >= 3, "Build settings contain 3 scenes",
                "Run Tools > Dummy Surfer > 2. Rebuild Scenes + Build Settings.");

            var inputHandler = 0;
            try
            {
                var prop = typeof(PlayerSettings).GetProperty("activeInputHandler",
                    System.Reflection.BindingFlags.Public | System.Reflection.BindingFlags.NonPublic | System.Reflection.BindingFlags.Static);
                if (prop != null) inputHandler = (int)prop.GetValue(null);
            }
            catch { }
            Check(inputHandler is 1 or 2,
                "Active Input Handling = Input System",
                "Player Settings > Other Settings > Active Input Handling → 'Input System Package (new)'.");

            Check(GraphicsSettings.defaultRenderPipeline != null,
                "Render pipeline assigned (URP preferred)",
                "Run Tools > Dummy Surfer > 1. Setup Everything.");

            sb.AppendLine();
            sb.AppendLine(problems == 0
                ? "All checks passed. Press Play and go!"
                : $"{problems} item(s) need attention — fixes listed above.");

            Debug.Log("[DummySurfer]\n" + sb);
            return sb.ToString();
        }
    }
}
