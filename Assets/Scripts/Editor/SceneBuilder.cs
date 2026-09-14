using System.IO;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;

namespace DummySurfer.EditorTools
{
    /// <summary>Builds the single Main scene: one SceneBootstrap object.
    /// The entire game is created procedurally at runtime by GameRoot.</summary>
    public static class SceneBuilder
    {
        public static void BuildAll()
        {
            var scene = EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);

            var go = new GameObject("SceneBootstrap");
            go.AddComponent<DummySurfer.SceneBootstrap>();

            Directory.CreateDirectory("Assets/Scenes");
            EditorSceneManager.SaveScene(scene, "Assets/Scenes/Main.unity");

            EditorBuildSettings.scenes = new[]
            {
                new EditorBuildSettingsScene("Assets/Scenes/Main.unity", true)
            };

            Debug.Log("[SceneBuilder] Main.unity built — scenes in build settings: " + EditorBuildSettings.scenes.Length);
        }
    }
}
