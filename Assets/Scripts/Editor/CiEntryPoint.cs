using System;
using System.IO;
using System.Linq;
using System.Text;
using UnityEditor;
using UnityEditor.Build.Reporting;
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

        /// <summary>
        /// Headless Android APK build used by CI. Assumes PrepareCiBuild ran
        /// earlier in the same container (scenes exist in EditorBuildSettings).
        /// Signing: uses a CI keystore passed via environment variables when
        /// provided, otherwise falls back to Unity's default debug keystore.
        /// </summary>
        public static void BuildAndroid()
        {
            EditorUserBuildSettings.SwitchActiveBuildTarget(BuildTarget.Android);
            EditorUserBuildSettings.androidBuildSystem = AndroidBuildSystem.Gradle;
            EditorUserBuildSettings.buildAppBundle = false;          // APK, not .aab
            EditorUserBuildSettings.exportAsGoogleAndroidProject = false;

            // Optional CI signing keystore (mounted inside the container).
            var ksPath = Environment.GetEnvironmentVariable("CI_KEYSTORE_PATH");
            if (!string.IsNullOrEmpty(ksPath) && File.Exists(ksPath))
            {
                PlayerSettings.Android.keystoreName = ksPath;
                PlayerSettings.Android.keystorePass =
                    Environment.GetEnvironmentVariable("CI_KEYSTORE_PASS") ?? string.Empty;
                PlayerSettings.Android.keyaliasName =
                    Environment.GetEnvironmentVariable("CI_KEYALIAS_NAME") ?? string.Empty;
                PlayerSettings.Android.keyaliasPass =
                    Environment.GetEnvironmentVariable("CI_KEYALIAS_PASS") ?? string.Empty;
                Debug.Log("[CiEntryPoint] Signing with CI keystore: " + ksPath);
            }
            else
            {
                Debug.LogWarning("[CiEntryPoint] No CI keystore env found — using Unity default debug keystore.");
            }

            var scenes = EditorBuildSettings.scenes
                .Where(s => s.enabled)
                .Select(s => s.path)
                .ToArray();

            if (scenes.Length == 0)
            {
                Debug.LogError("[CiEntryPoint] FATAL — no enabled scenes in EditorBuildSettings. Run PrepareCiBuild first.");
                EditorApplication.Exit(2);
                return;
            }

            Debug.Log("[CiEntryPoint] Building scenes: " + string.Join(", ", scenes));

            var options = new BuildPlayerOptions
            {
                scenes = scenes,
                locationPathName = "build/Android/DummySurfers.apk",
                target = BuildTarget.Android,
                options = BuildOptions.None
            };

            var report = BuildPipeline.BuildPlayer(options);
            var summary = report.summary;

            if (summary.result != BuildResult.Succeeded)
            {
                Debug.LogError("[CiEntryPoint] FATAL — Android build failed: result=" + summary.result +
                               " errors=" + summary.totalErrors + " warnings=" + summary.totalWarnings);
                EditorApplication.Exit(2);
                return;
            }

            Debug.Log("[CiEntryPoint] APK built OK — " + summary.outputPath +
                      " (" + (summary.totalSize / (1024 * 1024)) + " MB)");
            EditorApplication.Exit(0);
        }
    }
}
