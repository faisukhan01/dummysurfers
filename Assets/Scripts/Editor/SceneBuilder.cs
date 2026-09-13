using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using DummySurfer.Core;
using DummySurfer.Cam;
using DummySurfer.Multiplayer;
using DummySurfer.Track;
using DummySurfer.UI;

namespace DummySurfer.EditorTools
{
    /// <summary>
    /// Builds the three scenes from code (Boot → MainMenu → Game) and registers build settings.
    /// Scenes stay intentionally thin: all heavy content is created at runtime by the
    /// bootstrap/controllers, so these files are small, mergeable and robust.
    /// </summary>
    public static class SceneBuilder
    {
        public static void BuildAll()
        {
            BuildBoot();
            BuildMainMenu();
            BuildGame();
            SetBuildSettings();
            AssetDatabase.SaveAssets();
        }

        private static void BuildBoot()
        {
            var scene = EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);

            var bootstrap = new GameObject("GameBootstrap");
            bootstrap.AddComponent<GameBootstrap>();

            var camGo = new GameObject("SplashCamera");
            var cam = camGo.AddComponent<Camera>();
            cam.clearFlags = CameraClearFlags.SolidColor;
            cam.backgroundColor = VisualStylesSky();
            camGo.AddComponent<AudioListener>();
            camGo.tag = "MainCamera";

            EditorSceneManager.SaveScene(scene, "Assets/Scenes/Boot.unity");
        }

        private static void BuildMainMenu()
        {
            var scene = EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);

            var sun = new GameObject("Sun");
            var light = sun.AddComponent<Light>();
            light.type = LightType.Directional;
            sun.transform.rotation = Quaternion.Euler(50f, -25f, 0f);

            var backdrop = new GameObject("MenuBackdrop");
            backdrop.AddComponent<MenuBackdrop>();

            var lobby = new GameObject("LobbyState");
            lobby.AddComponent<Unity.Netcode.NetworkObject>();
            lobby.AddComponent<LobbyState>();

            EditorSceneManager.SaveScene(scene, "Assets/Scenes/MainMenu.unity");
        }

        private static void BuildGame()
        {
            var scene = EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);

            var sun = new GameObject("Sun");
            var light = sun.AddComponent<Light>();
            light.type = LightType.Directional;
            light.shadows = LightShadows.Soft;
            sun.transform.rotation = Quaternion.Euler(52f, -30f, 0f);

            var camGo = new GameObject("RunnerCameraRig");
            var cam = camGo.AddComponent<Camera>();
            cam.clearFlags = CameraClearFlags.SolidColor;
            cam.backgroundColor = VisualStylesSky();
            cam.nearClipPlane = 0.1f;
            cam.farClipPlane = 260f;
            camGo.AddComponent<AudioListener>();
            camGo.AddComponent<RunnerCamera>();
            camGo.tag = "MainCamera";

            var runner = new GameObject("SceneRunner");
            runner.AddComponent<RunSceneController>();

            var match = new GameObject("MatchState");
            match.AddComponent<Unity.Netcode.NetworkObject>();
            match.AddComponent<MatchStateManager>();

            EditorSceneManager.SaveScene(scene, "Assets/Scenes/Game.unity");
        }

        private static void SetBuildSettings()
        {
            EditorBuildSettings.scenes = new[]
            {
                new EditorBuildSettingsScene("Assets/Scenes/Boot.unity", true),
                new EditorBuildSettingsScene("Assets/Scenes/MainMenu.unity", true),
                new EditorBuildSettingsScene("Assets/Scenes/Game.unity", true),
            };
        }

        private static Color VisualStylesSky() => new Color(0.078f, 0.102f, 0.157f);
    }
}
