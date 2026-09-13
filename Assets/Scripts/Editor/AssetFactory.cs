using UnityEditor;
using UnityEngine;
using DummySurfer.Data;
using DummySurfer.Player;

namespace DummySurfer.EditorTools
{
    /// <summary>
    /// Creates tuning ScriptableObjects (spec 5.3) and the single networked player prefab
    /// that Netcode requires as an asset (Assets/Resources/NetworkRunner.prefab).
    /// </summary>
    public static class AssetFactory
    {
        public static void CreateAll()
        {
            CreateIfMissing<GameConfig>("Assets/ScriptableObjects/Resources/Config/GameConfig.asset");
            CreateIfMissing<DifficultyCurve>("Assets/ScriptableObjects/Resources/Config/DifficultyCurve.asset");
            CreateIfMissing<ObstacleCatalog>("Assets/ScriptableObjects/Resources/Config/ObstacleCatalog.asset");
            CreateIfMissing<AudioBank>("Assets/ScriptableObjects/Resources/Config/AudioBank.asset");

            var juno = CreateIfMissing<CharacterStats>("Assets/ScriptableObjects/Resources/Characters/Juno.asset");
            juno.displayName = "JUNO";
            EditorUtility.SetDirty(juno);

            var kai = CreateIfMissing<CharacterStats>("Assets/ScriptableObjects/Resources/Characters/Kai.asset");
            kai.displayName = "KAI";
            kai.laneMult = 1.12f;
            kai.jumpMult = 1.05f;
            kai.speedMult = 0.99f;
            kai.primary = new Color(0.90f, 0.28f, 0.61f);
            kai.secondary = new Color(1f, 0.79f, 0.24f);
            EditorUtility.SetDirty(kai);

            AssetDatabase.SaveAssets();
        }

        private static T CreateIfMissing<T>(string path) where T : ScriptableObject
        {
            var asset = AssetDatabase.LoadAssetAtPath<T>(path);
            if (asset != null) return asset;
            asset = ScriptableObject.CreateInstance<T>();
            AssetDatabase.CreateAsset(asset, path);
            return asset;
        }

        public static void BuildRunnerPrefab()
        {
            EnsureFolder("Assets/Resources");
            string path = "Assets/Resources/NetworkRunner.prefab";

            var temp = RunnerFactory.BuildNetworkRunner(CharacterStats.Load(0));
            temp.name = "NetworkRunner";
            var saved = PrefabUtility.SaveAsPrefabAsset(temp, path);
            Object.DestroyImmediate(temp);

            if (saved != null)
                Debug.Log("[DummySurfer] NetworkRunner prefab saved to " + path);
        }

        private static void EnsureFolder(string path)
        {
            if (AssetDatabase.IsValidFolder(path)) return;
            if (path == "Assets") return;
            var parent = path.Substring(0, path.LastIndexOf('/'));
            EnsureFolder(parent);
            AssetDatabase.CreateFolder(parent, path.Substring(path.LastIndexOf('/') + 1));
        }
    }
}
