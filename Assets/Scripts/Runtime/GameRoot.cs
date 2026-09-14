using System.Collections;
using UnityEngine;
using UnityEngine.EventSystems;
using UnityEngine.Rendering;

namespace DummySurfer
{
    /// <summary>Composition root: builds camera, lighting, world, characters, UI, and
    /// drives the Splash → Loading → Menu flow (all 10 reference screens).</summary>
    public class GameRoot : MonoBehaviour
    {
        public static GameRoot I;
        PlayerController player;
        CharacterRig boy;
        UiScreens ui;

        void Awake()
        {
            I = this;
            Fx.Init(transform);

            var ggo = new GameObject("~Game");
            ggo.AddComponent<GameManager>();

            BuildSceneLook();

            var sp = new GameObject("~Spawner");
            sp.AddComponent<TrackSpawner>();

            // player
            var pgo = new GameObject("Player");
            player = pgo.AddComponent<PlayerController>();
            boy = CharacterRig.BuildBoy(pgo.transform);
            player.Attach(boy);

            // chaser
            var cgo = new GameObject("Chaser");
            cgo.AddComponent<Chaser>();
            var guard = CharacterRig.BuildInspector(cgo.transform);
            var dog = CharacterRig.BuildDog(cgo.transform);
            cgo.GetComponent<Chaser>().Attach(guard, dog);

            // UI + input plumbing
            if (EventSystem.current == null)
                new GameObject("EventSystem", typeof(EventSystem), typeof(StandaloneInputModule));
            new GameObject("~Input").AddComponent<InputManager>();
            ui = gameObject.AddComponent<UiScreens>();

            BuildMenuDressing();

            player.transform.rotation = Quaternion.Euler(0, 152f, 0);
            StartCoroutine(Boot());
        }

        /// <summary>Shared scene look: camera, sky dome, sun, fog, ambient.
        /// Static so the CI preview renderer can reuse it 1:1.</summary>
        public static void BuildSceneLook()
        {
            var camGo = new GameObject("MainCam", typeof(Camera), typeof(AudioListener), typeof(CameraRig));
            camGo.tag = "MainCamera";
            var cam = camGo.GetComponent<Camera>();
            cam.clearFlags = CameraClearFlags.SolidColor;
            cam.backgroundColor = GameManager.C.Hex(0x64C2F2);
            cam.nearClipPlane = 0.3f;
            cam.farClipPlane = 600f;
            cam.fieldOfView = 55f;
            cam.transform.position = new Vector3(-3.9f, 1.9f, -3.4f);
            cam.transform.rotation = Quaternion.LookRotation(new Vector3(0.3f, 1.35f, 1.2f) - cam.transform.position);

            // ---- gradient sky dome (unlit, fog-free, follows camera)
            var skyGo = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            Object.Destroy(skyGo.GetComponent<Collider>());
            skyGo.name = "~SkyDome";
            var skyM = new Material(Fx.TexShader);
            skyM.name = "skymat";
            skyM.mainTexture = Fx.TexSky();
            var skyMr = skyGo.GetComponent<MeshRenderer>();
            skyMr.sharedMaterial = skyM;
            skyMr.shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.Off;
            skyMr.receiveShadows = false;
            skyGo.transform.localScale = new Vector3(-300f, 300f, 300f); // inverted → visible from inside
            skyGo.transform.position = new Vector3(0f, 8f, 0f);
            CameraRig.Sky = skyGo.transform;

            var sun = new GameObject("Sun");
            var l = sun.AddComponent<Light>();
            l.type = LightType.Directional;
            sun.transform.rotation = Quaternion.Euler(46f, -35f, 0f);
            l.intensity = 1.18f;
            l.shadows = LightShadows.Soft;
            l.shadowStrength = 0.78f;
            l.shadowBias = 0.6f;
            l.color = new Color(1f, 0.965f, 0.9f);
            RenderSettings.sun = l;

            RenderSettings.fog = true;
            RenderSettings.fogColor = GameManager.C.Hex(0xC6E8F8);
            RenderSettings.fogMode = FogMode.Linear;
            RenderSettings.fogStartDistance = 75f;
            RenderSettings.fogEndDistance = 250f;
            RenderSettings.ambientMode = UnityEngine.Rendering.AmbientMode.Trilight;
            RenderSettings.ambientSkyColor = new Color(0.78f, 0.88f, 0.98f);
            RenderSettings.ambientEquatorColor = new Color(0.84f, 0.88f, 0.94f);
            RenderSettings.ambientGroundColor = new Color(0.58f, 0.64f, 0.55f);
        }

        void BuildMenuDressing()
        {
            var root = new GameObject("~MenuDressing").transform;

            var t1 = WorldFactory.Train(0, 16f, false, false);
            t1.transform.SetParent(root);
            t1.transform.position = new Vector3(PlayerController.LaneW, 0f, 18f);
            var oc = t1.GetComponent<ObstacleComp>();
            oc.center = t1.transform.position + Vector3.up * 1.5f;

            var t2 = WorldFactory.Train(2, 24f, false, false);
            t2.transform.SetParent(root);
            t2.transform.position = new Vector3(-PlayerController.LaneW, 0f, 64f);
            var oc2 = t2.GetComponent<ObstacleComp>();
            oc2.center = t2.transform.position + Vector3.up * 1.5f;

            var wall = WorldFactory.GraffitiPanel(15f, 6.4f, GameManager.C.Hex(0xB7BEC9), 3);
            wall.transform.SetParent(root);
            wall.transform.position = new Vector3(4.9f, 3.0f, 18f);
            wall.transform.rotation = Quaternion.Euler(0, -90f, 0);

            // spray bag + cans near the boy
            var bagM = Fx.Mat(GameManager.C.Hex(0x2E4A8F));
            var bag = GameObject.CreatePrimitive(UnityEngine.PrimitiveType.Cube);
            Object.Destroy(bag.GetComponent<Collider>());
            bag.name = "spraybag";
            bag.transform.SetParent(root);
            bag.transform.position = new Vector3(1.05f, 0.16f, 0.95f);
            bag.transform.rotation = Quaternion.Euler(0, 24f, 0);
            bag.transform.localScale = new Vector3(0.52f, 0.3f, 0.36f);
            bag.GetComponent<MeshRenderer>().sharedMaterial = bagM;
            var can1 = GameObject.CreatePrimitive(UnityEngine.PrimitiveType.Cylinder);
            Object.Destroy(can1.GetComponent<Collider>());
            can1.name = "can1";
            can1.transform.SetParent(root);
            can1.transform.position = new Vector3(0.78f, 0.4f, 0.8f);
            can1.transform.localScale = new Vector3(0.12f, 0.1f, 0.12f);
            can1.GetComponent<MeshRenderer>().sharedMaterial = Fx.Mat(GameManager.C.Hex(0xE84B4B));
            var can2 = GameObject.CreatePrimitive(UnityEngine.PrimitiveType.Cylinder);
            Object.Destroy(can2.GetComponent<Collider>());
            can2.name = "can2";
            can2.transform.SetParent(root);
            can2.transform.position = new Vector3(0.95f, 0.38f, 1.15f);
            can2.transform.localScale = new Vector3(0.11f, 0.09f, 0.11f);
            can2.GetComponent<MeshRenderer>().sharedMaterial = Fx.Mat(GameManager.C.Hex(0x35C4B6));
        }

        IEnumerator Boot()
        {
            GameManager.I.SetState(GameManager.St.Splash);
            yield return new WaitForSecondsRealtime(1.25f);
            GameManager.I.SetState(GameManager.St.Loading);
            yield return SnapshotRoutine();
            float t = 0;
            while (t < 1f)
            {
                t += Time.unscaledDeltaTime / 2.1f;
                ui.SetProgress(Mathf.Min(1f, t));
                yield return null;
            }
            ui.SetProgress(1f);
            yield return new WaitForSecondsRealtime(0.25f);
            EnterMenu();
        }

        IEnumerator SnapshotRoutine()
        {
            CharacterRig rig = null;
            Camera sc = null;
            RenderTexture rt = null;
            try
            {
                rig = CharacterRig.BuildBoy(null);
                rig.transform.position = new Vector3(500f, 0f, 500f);
                rig.Pose("run", 0f, 1f);
                rig.phase = 2.15f;

                var go = new GameObject("SnapCam");
                sc = go.AddComponent<Camera>();
                sc.transform.position = new Vector3(500f, 1.05f, 495.9f);
                sc.transform.rotation = Quaternion.Euler(0f, 0f, 0f);
                sc.clearFlags = CameraClearFlags.SolidColor;
                sc.backgroundColor = new Color(0f, 0f, 0f, 0f);
                sc.fieldOfView = 30f;
                sc.aspect = 1f;
                rt = new RenderTexture(512, 512, 24, RenderTextureFormat.ARGB32);
                sc.targetTexture = rt;
                sc.Render();
                RenderTexture.active = rt;
                var tex = new Texture2D(512, 512, TextureFormat.RGBA32, false);
                tex.ReadPixels(new Rect(0, 0, 512, 512), 0, 0);
                tex.Apply();
                RenderTexture.active = null;
                sc.targetTexture = null;
                rt.Release();
                ui.SetSticker(Sprite.Create(tex, new Rect(0, 0, 512, 512), new Vector2(0.5f, 0.44f), 100f));
            }
            catch { }
            if (sc != null) Destroy(sc.gameObject);
            if (rig != null) Destroy(rig.gameObject);
            yield break;
        }

        void EnterMenu()
        {
            TrackSpawner.I.ResetWorld();
            player.ResetRun();
            player.transform.rotation = Quaternion.Euler(0, 152f, 0);
            CameraRig.I.mode = CameraRig.CamMode.Menu;
            GameManager.I.SetState(GameManager.St.Menu);
        }

        public void FromMenuTap()
        {
            var g = GameManager.I;
            if (g.st != GameManager.St.Menu || (ui != null && ui.modalOpen)) return;
            StartRunInternal();
        }

        public void FromResultsPlay()
        {
            var g = GameManager.I;
            if (g.st != GameManager.St.Results) return;
            StartRunInternal();
        }

        void StartRunInternal()
        {
            TrackSpawner.I.ResetWorld();
            player.ResetRun();
            player.transform.rotation = Quaternion.identity;
            if (Chaser.I != null) Chaser.I.Hide();
            GameManager.I.SetState(GameManager.St.Run);
            Fx.Play("whistle", 0.9f);
        }

        public void GoHome()
        {
            var g = GameManager.I;
            TrackSpawner.I.ResetWorld();
            player.ResetRun();
            player.transform.rotation = Quaternion.Euler(0, 152f, 0);
            if (Chaser.I != null) Chaser.I.Hide();
            CameraRig.I.mode = CameraRig.CamMode.Menu;
            g.Save();
            g.SetState(GameManager.St.Menu);
        }

        void Update()
        {
            var g = GameManager.I;
            if (boy == null) return;
            if (g.st == GameManager.St.Menu || g.st == GameManager.St.Splash || g.st == GameManager.St.Loading)
            {
                float m = Mathf.Repeat(Time.time, 9f);
                boy.Pose(m < 5.5f ? "spray" : "idle", Time.time, 0f);
            }
        }
    }
}
