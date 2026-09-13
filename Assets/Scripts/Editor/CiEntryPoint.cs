using System.Text;
using UnityEditor;
using UnityEngine;

namespace DummySurfer.EditorTools
{
    /// <summary>
    /// Batch-mode entry point for CI (GitHub Actions / GameCI docker runners).
    /// Mirrors DummySurferSetupWizard.SetupEverything() but contains zero
    /// interactive dialogs / progress bars so it can run headless with
    ///   unity-editor -batchmode -nographics -quit
    ///     -executeMethod DummySurfer.EditorTools.CiEntryPoint.PrepareCiBuild
    /// </summary>
    public static class CiEntryPoint
    {
        public static void PrepareCiBuild()
        {
            var log = new StringBuilder();
            log.AppendLine("[CiEntryPoint] CI project preparation started…");

            ProjectConfigurator.EnsureFolders();
            log.AppendLine("[CiEntryPoint] OK  — folders ensured.");

            ProjectConfigurator.ConfigureUrp();
            log.AppendLine("[CiEntryPoint] OK  — URP assets + quality tiers configured.");

            ProjectConfigurator.ConfigureAndroid();
            log.AppendLine("[CiEntryPoint] OK  — Android player settings configured.");

            AssetFactory.CreateAll();
            log.AppendLine("[CiEntryPoint] OK  — tuning data assets created.");

            AssetFactory.BuildRunnerPrefab();
            log.AppendLine("[CiEntryPoint] OK  — network runner prefab built.");

            SceneBuilder.BuildAll();
            log.AppendLine("[CiEntryPoint] OK  — scenes generated + build settings set.");

            AssetDatabase.Refresh();
            AssetDatabase.SaveAssets();

            log.AppendLine(ValidationReport.RunAndCollect());
            Debug.Log(log.ToString());

            // Fail the CI step loudly if the scene list is still empty.
            if (EditorBuildSettings.scenes == null || EditorBuildSettings.scenes.Length == 0)
            {
                Debug.LogError("[CiEntryPoint] FATAL — no scenes in EditorBuildSettings after BuildAll().");
                EditorApplication.Exit(2);
            }

            EditorApplication.Exit(0);
        }
    }
}
