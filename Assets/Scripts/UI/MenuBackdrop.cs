using UnityEngine;
using DummySurfer.Core;
using DummySurfer.Data;
using DummySurfer.Player;
using DummySurfer.Utilities;

namespace DummySurfer.UI
{
    /// <summary>
    /// Menu scene dressing: sky camera drift, original skyline strip, and an idle runner
    /// doing a light jog on the spot. Everything procedural, cohesive with the game world.
    /// </summary>
    public sealed class MenuBackdrop : MonoBehaviour
    {
        private Camera _cam;
        private RunnerVisualRig _rig;
        private float _t;

        private void Start()
        {
            GameBootstrap.EnsureAll();
            GameStateManager.Ensure().Set(GameState.Menu);

            if (Camera.main == null)
            {
                var go = new GameObject("MenuCamera");
                go.tag = "MainCamera";
                _cam = go.AddComponent<Camera>();
                go.AddComponent<AudioListener>();
            }
            else _cam = Camera.main;

            _cam.clearFlags = CameraClearFlags.SolidColor;
            _cam.backgroundColor = VisualStyles.SkyTop;
            _cam.transform.position = new Vector3(0f, 3.4f, -8.5f);
            _cam.transform.rotation = Quaternion.Euler(4f, 0f, 0f);

            RenderSettings.fog = true;
            RenderSettings.fogMode = FogMode.Linear;
            RenderSettings.fogColor = VisualStyles.FogColor;
            RenderSettings.fogStartDistance = 24f;
            RenderSettings.fogEndDistance = 90f;
            RenderSettings.ambientLight = new Color(0.62f, 0.72f, 0.82f);
            RenderSettings.ambientMode = UnityEngine.Rendering.AmbientMode.Flat;

            if (FindObjectsByType<Light>(FindObjectsSortMode.None).Length == 0)
            {
                var sunGo = new GameObject("Sun");
                var sun = sunGo.AddComponent<Light>();
                sun.type = LightType.Directional;
                sun.color = new Color(1f, 0.96f, 0.88f);
                sun.intensity = 1.25f;
                sunGo.transform.rotation = Quaternion.Euler(50f, -25f, 0f);
            }

            BuildSkyline();
            BuildIdleRunner();
        }

        private void BuildSkyline()
        {
            var root = new GameObject("MenuSkyline");
            var rng = new SeededRandom(7777UL);
            for (int side = -1; side <= 1; side += 2)
            {
                float z = -6f;
                while (z < 60f)
                {
                    float w = rng.Range(3f, 8f);
                    float h = rng.Range(6f, 30f);
                    float d = rng.Range(3f, 6f);
                    var b = GameObject.CreatePrimitive(PrimitiveType.Cube);
                    Object.Destroy(b.GetComponent<Collider>());
                    b.transform.SetParent(root.transform, false);
                    b.transform.position = new Vector3(side * rng.Range(11f, 26f), h * 0.5f - 2f, z + d * 0.5f);
                    b.transform.localScale = new Vector3(w, h, d);
                    VisualStyles.AddMesh(b, rng.Chance(0.5f) ? VisualStyles.Skyline : VisualStyles.SkylineFar);
                    z += d + rng.Range(2f, 6f);
                }
            }
        }

        private void BuildIdleRunner()
        {
            var go = RunnerFactory.BuildOfflineRunner(CharacterSelector.GetSelectedStats());
            go.name = "MenuRunner";
            go.transform.position = new Vector3(0f, 0f, 0f);
            var pc = go.GetComponent<PlayerController>();
            pc.enabled = false;   // pure visual jog
            _rig = go.GetComponent<RunnerVisualRig>();
            _rig.Play(RunnerAnimState.Run);   // alive: jogging in place, not frozen

            // A small platform strip under the runner for grounding.
            var strip = GameObject.CreatePrimitive(PrimitiveType.Cube);
            Object.Destroy(strip.GetComponent<Collider>());
            strip.name = "Strip";
            strip.transform.position = new Vector3(0f, -0.12f, 0f);
            strip.transform.localScale = new Vector3(7.6f, 0.24f, 26f);
            VisualStyles.AddMesh(strip, VisualStyles.Asphalt);
        }

        private void Update()
        {
            _t += Time.unscaledDeltaTime;
            if (_cam != null)
            {
                _cam.transform.position = new Vector3(Mathf.Sin(_t * 0.12f) * 0.6f, 3.4f + Mathf.Sin(_t * 0.2f) * 0.12f, -8.5f);
                _cam.transform.rotation = Quaternion.Euler(4f, Mathf.Sin(_t * 0.08f) * 2.5f, 0f);
            }
            if (_rig != null) _rig.Tick(0.16f, Time.unscaledDeltaTime);
        }
    }
}
