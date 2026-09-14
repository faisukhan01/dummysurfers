using UnityEngine;

namespace DummySurfer
{
    /// <summary>Camera: cinematic menu orbit → gameplay follow → death close-up.</summary>
    public class CameraRig : MonoBehaviour
    {
        public enum CamMode { Menu, Follow, Death }
        public static CameraRig I;
        public static Transform Sky;          // gradient sky dome follows camera x/z
        public CamMode mode = CamMode.Menu;

        Camera cam;
        float shake;
        Vector3 pos, look;
        float menuT;

        void Awake()
        {
            I = this;
            cam = GetComponent<Camera>();
            pos = transform.position;
            look = new Vector3(0.3f, 1.35f, 1.2f);
        }

        public void Shake(float s) { shake = Mathf.Max(shake, s); }

        void LateUpdate()
        {
            var g = GameManager.I;
            var p = PlayerController.I;
            if (g == null || p == null) return;
            float dt = Time.deltaTime;
            menuT += dt;

            Vector3 tp, tl;
            float lerp = 4.5f;

            if (mode == CamMode.Menu)
            {
                float sway = Mathf.Sin(menuT * 0.32f) * 0.4f;
                tp = new Vector3(-3.6f + sway, 1.85f + Mathf.Sin(menuT * 0.45f) * 0.08f, -3.3f);
                tl = new Vector3(0.3f, 1.3f, 1.3f);
                lerp = 2f;
            }
            else if (mode == CamMode.Death)
            {
                tp = new Vector3(p.x + 2.6f, 1.8f, p.z - 3.0f);
                tl = new Vector3(p.x, 1.0f, p.z + 0.2f);
                lerp = 5f;
            }
            else
            {
                // SS-style: low behind, tight follow, character low in frame
                tp = new Vector3(p.x * 0.55f, 3.55f + p.y * 0.34f, p.z - 6.6f);
                tl = new Vector3(p.x * 0.75f, 1.55f + p.y * 0.6f, p.z + 9.0f);
                lerp = 8.5f;
            }

            pos = Vector3.Lerp(pos, tp, Mathf.Min(1f, dt * lerp));
            look = Vector3.Lerp(look, tl, Mathf.Min(1f, dt * lerp));
            transform.position = pos + (shake > 0f ? Random.insideUnitSphere * shake * 0.22f : Vector3.zero);
            transform.LookAt(look);
            if (shake > 0f) shake = Mathf.Max(0f, shake - dt * 1.6f);

            if (Sky != null)
                Sky.position = new Vector3(transform.position.x, 8f, transform.position.z);

            float speedF = p != null ? Mathf.Clamp01(p.EffSpeed / 27f) : 0.5f;
            float fovT = mode == CamMode.Menu ? 46f : (mode == CamMode.Death ? 52f : Mathf.Lerp(55f, 61f, speedF));
            cam.fieldOfView = Mathf.Lerp(cam.fieldOfView, fovT, dt * 3f);
        }
    }
}
