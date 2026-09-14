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
    /// Builds the three scenes from code and registers build settings.
    /// The GAME scene is scene 0: launching the app opens straight into the bright subway
    /// run (SubwayGameLauncher). Boot is a one-frame hop to Game; MainMenu remains reachable
    /// from pause. Scenes stay intentionally thin — all content is runtime-built.
    /// </summary>
    public static class SceneBuilder
    {
        // Bright sunny-day values baked into the scenes themselves (URP-friendly).
        private static readonly Color Sky = new Color(0.208f, 0.725f, 0.945f);      // #35B9F1
        private static readonly Color SunColor = new Color(1f, 0.96f, 0.88f);

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
            bootstrap.AddComponent<InstantBoot>();

            var camGo = new GameObject("SplashCamera");
            var cam = camGo.AddComponent<Camera>();
            cam.clearFlags = CameraClearFlags.SolidColor;
            cam.backgroundColor = Sky;
            camGo.AddComponent<AudioListener>();
            camGo.tag = "MainCamera";

            var sun = new GameObject("Sun");
            var light = sun.AddComponent<Light>();
            light.type = LightType.Directional;
            light.color = SunColor;
            light.intensity = 1.25f;
            sun.transform.rotation = Quaternion.Euler(48f, -32f, 0f);

            EditorSceneManager.SaveScene(scene, "Assets/Scenes/Boot.unity");
        }

        private static void BuildMainMenu()
        {
            var scene = EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);

            var sun = new GameObject("Sun");
            var light = sun.AddComponent<Light>();
            light.type = LightType.Directional;
            light.color = SunColor;
            light.intensity = 1.25f;
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
            light.color = SunColor;
            light.intensity = 1.3f;
            light.shadows = LightShadows.Soft;
            sun.transform.rotation = Quaternion.Euler(48f, -32f, 0f);

            var camGo = new GameObject("RunnerCameraRig");
            var cam = camGo.AddComponent<Camera>();
            cam.clearFlags = CameraClearFlags.SolidColor;
            cam.backgroundColor = Sky;
            cam.nearClipPlane = 0.1f;
            cam.farClipPlane = 300f;
            camGo.AddComponent<AudioListener>();
            camGo.AddComponent<RunnerCamera>();
            camGo.tag = "MainCamera";

            var launcher = new GameObject("SubwayGame");
            launcher.AddComponent<SubwayGameLauncher>();

            EditorSceneManager.SaveScene(scene, "Assets/Scenes/Game.unity");
        }

        private static void SetBuildSettings()
        {
            EditorBuildSettings.scenes = new[]
            {
                new EditorBuildSettingsScene("Assets/Scenes/Game.unity", true),      // scene 0: play instantly
                new EditorBuildSettingsScene("Assets/Scenes/Boot.unity", true),
                new EditorBuildSettingsScene("Assets/Scenes/MainMenu.unity", true),
            };
        }
    }
}
