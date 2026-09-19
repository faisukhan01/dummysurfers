using System.Linq;
using UnityEditor;
using UnityEngine;

namespace DummySurfer
{
    /// <summary>
    /// CI-side extraction: copies the first AnimationClip out of the user's uploaded Mixamo FBX
    /// into Assets/Resources/Anim/Gesture.anim, so runtime code can Resources.Load it and play
    /// it through a legacy Animation component. Runs automatically after every domain reload,
    /// which covers fresh CI clones (import happens before script compilation).
    /// </summary>
    [InitializeOnLoad]
    public static class MixamoClipExport
    {
        const string FbxPath = "Assets/Resources/Models/Mixamo/MixamoGesture.fbx";
        const string OutDir = "Assets/Resources/Anim";
        const string OutPath = OutDir + "/Gesture.anim";

        static MixamoClipExport() { Export(); }

        public static void Export()
        {
            try
            {
                if (!System.IO.File.Exists(FbxPath)) return;
                if (!AssetDatabase.IsValidFolder(OutDir))
                {
                    System.IO.Directory.CreateDirectory(OutDir);
                    AssetDatabase.Refresh();
                }
                if (System.IO.File.Exists(OutPath)) return;   // already exported

                var clip = AssetDatabase.LoadAllAssetsAtPath(FbxPath)
                                     .OfType<AnimationClip>()
                                     .FirstOrDefault(c => !c.name.StartsWith("__Preview"));
                if (clip == null)
                {
                    Debug.LogWarning("[MixamoClipExport] no AnimationClip found in " + FbxPath);
                    return;
                }
                var clone = Object.Instantiate(clip);
                clone.name = "Gesture";
                AssetDatabase.CreateAsset(clone, OutPath);
                AssetDatabase.SaveAssets();
                Debug.Log("[MixamoClipExport] exported '" + clip.name + "' (" + clip.length.ToString("F2") + "s) -> " + OutPath);
            }
            catch (System.Exception e)
            {
                Debug.LogWarning("[MixamoClipExport] failed: " + e.Message);
            }
        }
    }
}
