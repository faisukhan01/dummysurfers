using System.IO;
using UnityEditor;
using UnityEngine;
using UnityEngine.Rendering;

namespace DummySurfer.EditorTools
{
    /// <summary>Folder layout + render pipeline + Android player settings (spec 5.1, 8, 10).</summary>
    public static class ProjectConfigurator
    {
        public static void EnsureFolders()
        {
            string[] folders =
            {
                "Assets/Scenes", "Assets/Prefabs", "Assets/ScriptableObjects",
                "Assets/ScriptableObjects/Resources", "Assets/ScriptableObjects/Resources/Config",
                "Assets/ScriptableObjects/Resources/Characters", "Assets/Resources"
            };
            foreach (var f in folders)
                if (!Directory.Exists(f))
                    AssetDatabase.CreateFolder(Path.GetDirectoryName(f).Replace('\\', '/'), Path.GetFileName(f));
        }

        public static void ConfigureUrp()
        {
            try
            {
                var existing = AssetDatabase.LoadAssetAtPath<RenderPipelineAsset>("Assets/Settings/DS-Mobile-Pipeline.asset");
                if (existing != null)
                {
                    GraphicsSettings.defaultRenderPipeline = existing;
                    return;
                }

                var rendererData = ScriptableObject.CreateInstance("UniversalRendererData");
                if (rendererData == null)
                {
                    Warn("URP types not found — project will run on the Built-in Render Pipeline. " +
                         "Gameplay is unaffected; assign a URP asset manually later for the intended look.");
                    return;
                }

                AssetDatabase.CreateAsset(rendererData, "Assets/Settings/DS-Mobile-Renderer.asset");
                var createMethod = typeof(UnityEngine.Rendering.Universal.UniversalRenderPipelineAsset)
                    .GetMethod("Create", new[] { rendererData.GetType() });
                var pipeline = createMethod?.Invoke(null, new object[] { rendererData }) as RenderPipelineAsset;

                if (pipeline == null)
                {
                    Warn("Could not create the URP asset via API — assign one manually (docs/SETUP_GUIDE.md).");
                    return;
                }

                AssetDatabase.CreateAsset(pipeline, "Assets/Settings/DS-Mobile-Pipeline.asset");

                // Mobile-friendly defaults (spec 10 PERFORMANCE)
                SetProp(pipeline, "shadowDistance", 45f);
                SetProp(pipeline, "msaaSampleCount", 2);
                SetProp(pipeline, "renderScale", 1f);
                SetProp(pipeline, "supportsHDR", false);

                GraphicsSettings.defaultRenderPipeline = pipeline;
                AssetDatabase.SaveAssets();
            }
            catch (System.Exception e)
            {
                Warn("URP setup skipped (" + e.Message + "). The game still runs on Built-in RP.");
            }
        }

        private static void SetProp(Object asset, string name, object value)
        {
            var p = asset.GetType().GetProperty(name);
            if (p != null && p.CanWrite) p.SetValue(asset, value);
        }

        public static void ConfigureAndroid()
        {
            PlayerSettings.companyName = "DummySurferTeam";
            PlayerSettings.productName = "Dummy Surfer";
            PlayerSettings.SetApplicationIdentifier(BuildTargetGroup.Android, "com.dummysurfer.university");
            PlayerSettings.colorSpace = ColorSpace.Linear;
            PlayerSettings.SetScriptingBackend(UnityEditor.Build.NamedBuildTarget.Android, ScriptingImplementation.IL2CPP);
            PlayerSettings.Android.targetArchitectures = AndroidArchitecture.ARM64;
            PlayerSettings.Android.minSdkVersion = AndroidSdkVersions.AndroidApiLevel24;
            PlayerSettings.Android.forceInternetPermission = true;   // Relay requires internet
            PlayerSettings.defaultInterfaceOrientation = UIOrientation.Portrait;
            PlayerSettings.allowedAutorotateToPortrait = true;
            PlayerSettings.allowedAutorotateToPortraitUpsideDown = false;
            PlayerSettings.allowedAutorotateToLandscapeLeft = false;
            PlayerSettings.allowedAutorotateToLandscapeRight = false;

            // Active Input Handling → Input System Package (new). Internal API; best effort.
            try
            {
                var prop = typeof(PlayerSettings).GetProperty("activeInputHandler",
                    System.Reflection.BindingFlags.Public |
                    System.Reflection.BindingFlags.NonPublic |
                    System.Reflection.BindingFlags.Static);
                if (prop != null && (int)prop.GetValue(null) != 1)
                {
                    prop.SetValue(null, 1);
                    Debug.Log("[DummySurfer] Active Input Handling set to 'Input System Package'. " +
                              "If prompted, restart the editor for it to take effect.");
                }
            }
            catch
            {
                Debug.LogWarning("[DummySurfer] Set Player Settings > Other Settings > Active Input Handling " +
                                 "to 'Input System Package (new)' manually.");
            }
        }

        private static void Warn(string msg)
        {
            Debug.LogWarning("[DummySurfer] " + msg);
        }
    }
}
