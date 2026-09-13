using UnityEditor;
using UnityEngine;

namespace DummySurfer.EditorTools
{
    /// <summary>
    /// ONE-CLICK project setup (open Unity, run this once, press Play).
    /// Mirrors the spec's phased build order: project setup → folders → URP → Android →
    /// data assets → network prefab → scenes → validation.
    /// </summary>
    public static class DummySurferSetupWizard
    {
        private const string Menu = "Tools/Dummy Surfer/";

        [MenuItem(Menu + "1. Setup Everything (One-Click)", priority = 0)]
        public static void SetupEverything()
        {
            try
            {
                EditorUtility.DisplayProgressBar("Dummy Surfer Setup", "Creating folders…", 0.1f);
                ProjectConfigurator.EnsureFolders();

                EditorUtility.DisplayProgressBar("Dummy Surfer Setup", "Configuring render pipeline…", 0.25f);
                ProjectConfigurator.ConfigureUrp();

                EditorUtility.DisplayProgressBar("Dummy Surfer Setup", "Configuring Android player settings…", 0.4f);
                ProjectConfigurator.ConfigureAndroid();

                EditorUtility.DisplayProgressBar("Dummy Surfer Setup", "Creating tuning assets…", 0.55f);
                AssetFactory.CreateAll();

                EditorUtility.DisplayProgressBar("Dummy Surfer Setup", "Building NetworkRunner prefab…", 0.7f);
                AssetFactory.BuildRunnerPrefab();

                EditorUtility.DisplayProgressBar("Dummy Surfer Setup", "Building scenes + build settings…", 0.85f);
                SceneBuilder.BuildAll();

                AssetDatabase.Refresh();
            }
            finally
            {
                EditorUtility.ClearProgressBar();
            }

            var report = ValidationReport.RunAndCollect();
            EditorUtility.DisplayDialog("Dummy Surfer Setup",
                "Project setup complete!\n\n" + report +
                "\n\nNext steps:\n" +
                "1. If Unity asks to restart (Input System), do it.\n" +
                "2. For online play, link the project to Unity Gaming Services " +
                "(docs/MULTIPLAYER_GUIDE.md).\n" +
                "3. Press Play — single-player works instantly.\n\n" +
                "Full log: see the Console window.", "Let's run!");
        }

        [MenuItem(Menu + "2. Rebuild Scenes + Build Settings", priority = 20)]
        public static void RebuildScenes() => SceneBuilder.BuildAll();

        [MenuItem(Menu + "3. Rebuild Prefabs + Data Assets", priority = 21)]
        public static void RebuildAssets()
        {
            AssetFactory.CreateAll();
            AssetFactory.BuildRunnerPrefab();
            AssetDatabase.Refresh();
        }

        [MenuItem(Menu + "4. Configure Android Settings", priority = 22)]
        public static void ConfigureAndroid() => ProjectConfigurator.ConfigureAndroid();

        [MenuItem(Menu + "5. Validate Project", priority = 30)]
        public static void Validate()
        {
            var report = ValidationReport.RunAndCollect();
            EditorUtility.DisplayDialog("Dummy Surfer Validation", report, "OK");
        }
    }
}
