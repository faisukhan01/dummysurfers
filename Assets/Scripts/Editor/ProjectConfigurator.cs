using System.IO;
using UnityEditor;
using UnityEngine;
using UnityEngine.Rendering;

namespace DummySurfer.EditorTools
{
    /// <summary>Folder layout + render pipeline + Android player settings.</summary>
    public static class ProjectConfigurator
    {
        public static void EnsureFolders()
        {
            string[] folders =
            {
                "Assets/Scenes", "Assets/Resources", "Assets/Resources/Mats", "Assets/Settings"
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

                var rendererData = ScriptableObject.CreateInstance<UnityEngine.Rendering.Universal.UniversalRendererData>();
                if (rendererData == null)
                {
                    Warn("URP types not found — project will run on the Built-in Render Pipeline.");
                    return;
                }

                AssetDatabase.CreateAsset(rendererData, "Assets/Settings/DS-Mobile-Renderer.asset");
                var pipeline = UnityEngine.Rendering.Universal.UniversalRenderPipelineAsset.Create(rendererData);

                if (pipeline == null)
                {
                    Warn("Could not create the URP asset via API.");
                    return;
                }

                AssetDatabase.CreateAsset(pipeline, "Assets/Settings/DS-Mobile-Pipeline.asset");

                SetProp(pipeline, "shadowDistance", 90f);
                SetProp(pipeline, "msaaSampleCount", 4);
                SetProp(pipeline, "renderScale", 1f);
                SetProp(pipeline, "supportsHDR", false);

                // deeper shadow config (property names vary per URP version — best effort)
                try
                {
                    var so = new SerializedObject(pipeline);
                    var sd = so.FindProperty("m_ShadowDistance");
                    if (sd != null) sd.floatValue = 90f;
                    var cc = so.FindProperty("m_CascadeCount");
                    if (cc != null) cc.intValue = 2;
                    var ms = so.FindProperty("m_MainLightShadowsSupported");
                    if (ms != null) ms.boolValue = true;
                    var as2 = so.FindProperty("m_Cascade2Split");
                    if (as2 != null) as2.floatValue = 0.35f;
                    so.ApplyModifiedProperties();
                }
                catch { Debug.LogWarning("[DummySurfer] URP deep shadow props not set (names differ)."); }

                GraphicsSettings.defaultRenderPipeline = pipeline;
                AssetDatabase.SaveAssets();
            }
            catch (System.Exception e)
            {
                Warn("URP setup skipped (" + e.Message + ").");
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
            PlayerSettings.defaultInterfaceOrientation = UIOrientation.Portrait;
            PlayerSettings.allowedAutorotateToPortrait = true;
            PlayerSettings.allowedAutorotateToPortraitUpsideDown = false;
            PlayerSettings.allowedAutorotateToLandscapeLeft = false;
            PlayerSettings.allowedAutorotateToLandscapeRight = false;

            // Active Input Handling → BOTH (legacy Input + Input System).
            // The game uses the legacy touch/mouse/keyboard API; "Both" keeps
            // uGUI StandaloneInputModule + swipes working everywhere.
            try
            {
                var prop = typeof(PlayerSettings).GetProperty("activeInputHandler",
                    System.Reflection.BindingFlags.Public |
                    System.Reflection.BindingFlags.NonPublic |
                    System.Reflection.BindingFlags.Static);
                if (prop != null && (int)prop.GetValue(null) != 2)
                {
                    prop.SetValue(null, 2);
                    Debug.Log("[DummySurfer] Active Input Handling set to 'Both'.");
                }
            }
            catch
            {
                Debug.LogWarning("[DummySurfer] Could not set Active Input Handling automatically.");
            }
        }

        private static void Warn(string msg)
        {
            Debug.LogWarning("[DummySurfer] " + msg);
        }
    }
}
