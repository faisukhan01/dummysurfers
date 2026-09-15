using System.IO;
using System.Text;
using UnityEditor;
using UnityEditor.Build.Reporting;
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
            so.ApplyModifiedPropertiesWithoutUndo();
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
            SaveMat("Assets/Resources/Mats/anchor_urpunlit.mat", FindUrpUnlit() ?? Shader.Find("Unlit/Texture"));
            SaveMat("Assets/Resources/Mats/anchor_unlitcolor.mat", Shader.Find("Unlit/Color"));
            SaveMat("Assets/Resources/Mats/anchor_unlittex.mat", Shader.Find("Unlit/Texture"));
            SaveMat("Assets/Resources/Mats/anchor_unlitalpha.mat", Shader.Find("Unlit/Transparent"));
            AssetDatabase.SaveAssets();
        }

        static Shader FindUrpUnlit()
        {
            var s = AssetDatabase.LoadAssetAtPath<Shader>("Packages/com.unity.render-pipelines.universal/Shaders/Unlit.shader");
            if (s != null) return s;
            return Shader.Find("Universal Render Pipeline/Unlit");
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

            // Stamp the release version into the APK so the phone shows the
            // same version as the GitHub release tag (unity-v1.0.<run_number>).
            var verName = System.Environment.GetEnvironmentVariable("CI_VERSION_NAME");
            if (string.IsNullOrEmpty(verName)) verName = "1.0";
            PlayerSettings.bundleVersion = verName;

            var verCode = 1;
            var verCodeRaw = System.Environment.GetEnvironmentVariable("CI_VERSION_CODE");
            if (!string.IsNullOrEmpty(verCodeRaw)) int.TryParse(verCodeRaw, out verCode);
            if (verCode < 1) verCode = 1;
            PlayerSettings.Android.bundleVersionCode = verCode;
            Debug.Log($"[CiEntryPoint] APK version: name={verName} code={verCode}");

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

        // ------------------------------------------------------- visual previews
        /// <summary>
        /// Renders the actual game world with the actual character/camera code to PNGs,
        /// uploaded as a CI artifact. Used to self-review visual quality without a device.
        /// </summary>
        public static void CapturePreviews()
        {
            Debug.Log("[Preview] Building preview scene…");
            try { Directory.CreateDirectory("build/previews"); } catch { }

            DummySurfer.Fx.Init(null);
            DummySurfer.GameRoot.BuildSceneLook();

            var cam = Camera.main;
            if (cam == null)
            {
                Debug.LogError("[Preview] no camera after BuildSceneLook");
                EditorApplication.Exit(3);
                return;
            }

            try
            {
                // ---------- world ----------
                for (int i = -1; i < 3; i++)
                {
                    var ch = DummySurfer.WorldFactory.TrackChunk(48f);
                    ch.transform.position = new Vector3(0, 0, i * 48f + 24f);
                }
                // gameplay props
                var tr1 = DummySurfer.WorldFactory.Train(0, 18f, false, false);
                tr1.transform.position = new Vector3(2.2f, 0f, 34f);
                var tr2 = DummySurfer.WorldFactory.Train(2, 12f, true, false);
                tr2.transform.position = new Vector3(-2.2f, 0f, 62f);
                var bar = DummySurfer.WorldFactory.Barrier(false);
                bar.transform.position = new Vector3(0f, 0f, 21f);
                for (int i = 0; i < 7; i++)
                {
                    var c = DummySurfer.WorldFactory.Coin();
                    c.transform.position = new Vector3(0f, 1.1f, 9f + i * 2.3f);
                }
                var pw = DummySurfer.WorldFactory.Power(DummySurfer.PowerComp.K.Magnet);
                pw.transform.position = new Vector3(-2.2f, 1.35f, 28f);
                // décor
                var sys = new System.Random(5);
                for (int i = 0; i < 6; i++)
                {
                    float z = 6f + i * 16f;
                    int col = sys.Next(8);
                    float h = new[] { 10f, 14f, 19f, 23f }[sys.Next(4)];
                    var bl = DummySurfer.WorldFactory.Building(col, 7f + sys.Next(3), h, 8f, seed: col * 31 + i, billboard: sys.Next(3) == 0);
                    bl.transform.position = new Vector3(-13.5f - (float)sys.NextDouble() * 3f, 0f, z);
                    bl.transform.rotation = Quaternion.Euler(0, 90f, 0);
                    int col2 = sys.Next(8);
                    float h2 = new[] { 10f, 14f, 19f, 23f }[sys.Next(4)];
                    var b2 = DummySurfer.WorldFactory.Building(col2, 7f + sys.Next(3), h2, 8f, seed: col2 * 17 + i, billboard: false);
                    b2.transform.position = new Vector3(13.5f + (float)sys.NextDouble() * 3f, 0f, z + 8f);
                    b2.transform.rotation = Quaternion.Euler(0, -90f, 0);
                    var pole = DummySurfer.WorldFactory.Pole();
                    pole.transform.position = new Vector3(sys.Next(2) == 0 ? -8.6f : 8.6f, 0f, z + 4f);
                }
                var cl = DummySurfer.WorldFactory.Cloud();
                cl.transform.position = new Vector3(-6f, 14f, 40f);
                var cl2 = DummySurfer.WorldFactory.Cloud();
                cl2.transform.position = new Vector3(7f, 17f, 70f);

                // ---------- characters ----------
                var boy = DummySurfer.CharacterRig.BuildBoy(null);
                boy.Pose("run", 0f, 1f);
                boy.phase = 2.15f;
                var guard = DummySurfer.CharacterRig.BuildInspector(null);
                guard.transform.position = new Vector3(0.4f, 0f, -2.7f);
                guard.transform.rotation = Quaternion.Euler(0, 8f, 0);
                guard.Pose("run", 0f, 1f);
                guard.phase = 1.1f;
                var dog = DummySurfer.CharacterRig.BuildDog(null);
                dog.transform.position = new Vector3(-0.9f, 0f, -2.2f);
                dog.Pose("run", 0f, 1f);
                dog.phase = 0.4f;

                // ---------- shot A: gameplay view ----------
                var rt = new RenderTexture(720, 1520, 24, RenderTextureFormat.ARGB32);
                cam.targetTexture = rt;
                cam.transform.position = new Vector3(0f, 3.75f, -6.6f);
                cam.transform.rotation = Quaternion.LookRotation(new Vector3(0f, 1.72f, 9f) - cam.transform.position);
                cam.Render();
                SavePng(rt, "build/previews/gameplay.png");

                // ---------- shot B: menu view (spray pose + graffiti wall) ----------
                boy.transform.rotation = Quaternion.Euler(0, 208f, 0); // face the menu camera
                boy.Pose("spray", 1.5f, 0f);
                var wall = DummySurfer.WorldFactory.GraffitiPanel(15f, 6.4f, new Color32(0xB7, 0xBE, 0xC9, 255), 3);
                wall.transform.position = new Vector3(4.9f, 3.0f, 14f);
                wall.transform.rotation = Quaternion.Euler(0, -90f, 0);
                cam.transform.position = new Vector3(-2.7f, 1.85f, -2.6f);
                cam.transform.rotation = Quaternion.LookRotation(new Vector3(0.35f, 1.22f, 0.9f) - cam.transform.position);
                cam.Render();
                SavePng(rt, "build/previews/menu.png");

                // ---------- shot C: hero portrait (character QA close-up) ----------
                boy.transform.rotation = Quaternion.Euler(0, 20f, 0);
                boy.Pose("idle", 1.0f, 0f);
                cam.transform.position = new Vector3(1.9f, 1.75f, 3.6f);
                cam.transform.rotation = Quaternion.LookRotation(new Vector3(0f, 1.30f, 0.05f) - cam.transform.position);
                cam.Render();
                SavePng(rt, "build/previews/portrait.png");

                // ---------- shot D: back view (what the player actually sees) ----------
                boy.transform.rotation = Quaternion.Euler(0, 0f, 0);
                boy.Pose("run", 0f, 1f);
                boy.phase = 2.15f;
                cam.transform.position = new Vector3(0f, 2.1f, -3.4f);
                cam.transform.rotation = Quaternion.LookRotation(new Vector3(0f, 1.35f, 1.0f) - cam.transform.position);
                cam.Render();
                SavePng(rt, "build/previews/back.png");

                rt.Release();
                Debug.Log("[Preview] done");
            }
            catch (System.Exception e)
            {
                Debug.LogError("[Preview] failed (non-fatal): " + e);
            }
            EditorApplication.Exit(0);
        }

        static void SavePng(RenderTexture rt, string path)
        {
            RenderTexture.active = rt;
            var tex = new Texture2D(rt.width, rt.height, TextureFormat.RGBA32, false);
            tex.ReadPixels(new Rect(0, 0, rt.width, rt.height), 0, 0);
            tex.Apply();
            RenderTexture.active = null;
            File.WriteAllBytes(path, tex.EncodeToPNG());
            Object.DestroyImmediate(tex);
            Debug.Log("[Preview] saved " + path);
        }
    }
}
