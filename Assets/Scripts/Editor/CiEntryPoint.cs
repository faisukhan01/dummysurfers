using System.IO;
using System.Text;
using UnityEditor;
using UnityEngine;

namespace DummySurfer.EditorTools
{
    /// <summary>
    /// Batch-mode entry point for CI. The game is 100% procedural, so preparation is light:
    /// folders, URP, Android settings, physics layers, and "anchor" materials that force the
    /// shaders we need (URP Lit + unlit family) into the build so they survive stripping.
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
            log.AppendLine("[CiEntryPoint] OK  — URP configured.");

            ProjectConfigurator.ConfigureAndroid();
            log.AppendLine("[CiEntryPoint] OK  — Android player settings configured (portrait, IL2CPP, ARM64, input=Both).");

            EnsureLayers();
            log.AppendLine("[CiEntryPoint] OK  — physics layers ensured (8 Ground, 9 Train, 10 Obstacle, 11 Coin, 12 Power).");

            CreateAnchorMaterials();
            log.AppendLine("[CiEntryPoint] OK  — shader anchor materials created in Resources.");

            SceneBuilder.BuildAll();
            log.AppendLine("[CiEntryPoint] OK  — Main scene generated + build settings set.");

            AssetDatabase.Refresh();
            AssetDatabase.SaveAssets();

            if (EditorBuildSettings.scenes == null || EditorBuildSettings.scenes.Length == 0)
            {
                Debug.LogError("[CiEntryPoint] FATAL — no scenes in EditorBuildSettings after BuildAll().");
                EditorApplication.Exit(2);
                return;
            }

            Debug.Log(log.ToString());
            EditorApplication.Exit(0);
        }

        // ------------------------------------------------------- layers
        static void EnsureLayers()
        {
            var assets = AssetDatabase.LoadAllAssetsAtPath("ProjectSettings/TagManager.asset");
            if (assets == null || assets.Length == 0) return;
            var so = new SerializedObject(assets[0]);
            var layers = so.FindProperty("layers");
            string[] want = { "Ground", "Train", "Obstacle", "Coin", "Power" };
            for (int i = 0; i < want.Length; i++)
            {
                int idx = 8 + i;
                if (idx >= layers.arraySize) continue;
                var sp = layers.GetArrayElementAtIndex(idx);
                if (string.IsNullOrEmpty(sp.stringValue)) sp.stringValue = want[i];
            }
            so.ApplyModifiedPropertiesUncomitted();
            AssetDatabase.SaveAssets();
        }

        // ------------------------------------------------------- shader anchors
        static Shader FindUrpLit()
        {
            var s = AssetDatabase.LoadAssetAtPath<Shader>("Packages/com.unity.render-pipelines.universal/Shaders/Lit.shader");
            if (s != null) return s;
            try
            {
                var guids = AssetDatabase.FindAssets("Lit", new[] { "Packages/com.unity.render-pipelines.universal/Shaders" });
                foreach (var g in guids)
                {
                    var p = AssetDatabase.GUIDToAssetPath(g);
                    if (p != null && p.EndsWith("/Lit.shader"))
                    {
                        var sh = AssetDatabase.LoadAssetAtPath<Shader>(p);
                        if (sh != null) return sh;
                    }
                }
            }
            catch { }
            try
            {
                var rp = UnityEngine.Rendering.GraphicsSettings.defaultRenderPipeline;
                if (rp != null && rp.defaultShader != null) return rp.defaultShader;
            }
            catch { }
            return null;
        }

        static void CreateAnchorMaterials()
        {
            Directory.CreateDirectory("Assets/Resources/Mats");

            SaveMat("Assets/Resources/Mats/anchor_lit.mat", FindUrpLit() ?? Shader.Find("Unlit/Texture"));
            SaveMat("Assets/Resources/Mats/anchor_unlitcolor.mat", Shader.Find("Unlit/Color"));
            SaveMat("Assets/Resources/Mats/anchor_unlittex.mat", Shader.Find("Unlit/Texture"));
            SaveMat("Assets/Resources/Mats/anchor_unlitalpha.mat", Shader.Find("Unlit/Transparent"));
            AssetDatabase.SaveAssets();
        }

        static void SaveMat(string path, Shader shader)
        {
            if (shader == null)
            {
                Debug.LogWarning("[CiEntryPoint] Could not resolve shader for " + path);
                return;
            }
            AssetDatabase.DeleteAsset(path);
            var m = new Material(shader);
            AssetDatabase.CreateAsset(m, path);
            Debug.Log("[CiEntryPoint] anchor material " + path + " → shader " + shader.name);
        }

        // ------------------------------------------------------- APK build
        public static void BuildAndroid()
        {
            EditorUserBuildSettings.SwitchActiveBuildTarget(BuildTarget.Android);
            EditorUserBuildSettings.androidBuildSystem = AndroidBuildSystem.Gradle;
            EditorUserBuildSettings.buildAppBundle = false;
            EditorUserBuildSettings.exportAsGoogleAndroidProject = false;

            var ksPath = System.Environment.GetEnvironmentVariable("CI_KEYSTORE_PATH");
            if (!string.IsNullOrEmpty(ksPath) && File.Exists(ksPath))
            {
                PlayerSettings.Android.keystoreName = ksPath;
                PlayerSettings.Android.keystorePass =
                    System.Environment.GetEnvironmentVariable("CI_KEYSTORE_PASS") ?? string.Empty;
                PlayerSettings.Android.keyaliasName =
                    System.Environment.GetEnvironmentVariable("CI_KEYALIAS_NAME") ?? string.Empty;
                PlayerSettings.Android.keyaliasPass =
                    System.Environment.GetEnvironmentVariable("CI_KEYALIAS_PASS") ?? string.Empty;
                Debug.Log("[CiEntryPoint] Signing with CI keystore: " + ksPath);
            }
            else
            {
                Debug.LogWarning("[CiEntryPoint] No CI keystore env found — using Unity default debug keystore.");
            }

            var scenes = EditorBuildSettings.scenes;
            if (scenes == null || scenes.Length == 0)
            {
                Debug.LogError("[CiEntryPoint] FATAL — no scenes. Run PrepareCiBuild first.");
                EditorApplication.Exit(2);
                return;
            }

            var paths = new string[scenes.Length];
            for (int i = 0; i < scenes.Length; i++) paths[i] = scenes[i].path;
            Debug.Log("[CiEntryPoint] Building scenes: " + string.Join(", ", paths));

            var options = new BuildPlayerOptions
            {
                scenes = paths,
                locationPathName = "build/Android/DummySurfers.apk",
                target = BuildTarget.Android,
                options = BuildOptions.None
            };

            var report = BuildPipeline.BuildPlayer(options);
            var summary = report.summary;

            if (summary.result != BuildResult.Succeeded)
            {
                Debug.LogError("[CiEntryPoint] FATAL — Android build failed: result=" + summary.result +
                               " errors=" + summary.totalErrors);
                EditorApplication.Exit(2);
                return;
            }

            Debug.Log("[CiEntryPoint] APK built OK — " + summary.outputPath +
                      " (" + (summary.totalSize / (1024 * 1024)) + " MB)");
            EditorApplication.Exit(0);
        }
    }
}
