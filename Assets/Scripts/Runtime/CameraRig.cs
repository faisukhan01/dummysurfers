using UnityEngine;

namespace DummySurfer
{
    /// <summary>Camera: cinematic menu orbit → gameplay follow → death close-up.</summary>
    public class CameraRig : MonoBehaviour
    {
        public enum CamMode { Menu, Follow, Death }
        public static CameraRig I;
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
            look = new Vector3(0.4f, 1.4f, 1.4f);
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
                float sway = Mathf.Sin(menuT * 0.35f) * 0.35f;
                tp = new Vector3(-3.9f + sway, 1.95f + Mathf.Sin(menuT * 0.5f) * 0.1f, -3.2f);
                tl = new Vector3(0.35f, 1.35f, 1.6f);
                lerp = 2f;
            }
            else if (mode == CamMode.Death)
            {
                tp = new Vector3(p.x + 2.9f, 1.9f, p.z - 3.2f);
                tl = new Vector3(p.x, 1.0f, p.z + 0.2f);
                lerp = 5f;
            }
            else
            {
                tp = new Vector3(p.x * 0.42f, 4.35f + p.y * 0.32f, p.z - 7.8f);
                tl = new Vector3(p.x * 0.62f, 1.85f + p.y * 0.55f, p.z + 9.5f);
                lerp = 8f;
            }

            pos = Vector3.Lerp(pos, tp, Mathf.Min(1f, dt * lerp));
            look = Vector3.Lerp(look, tl, Mathf.Min(1f, dt * lerp));
            transform.position = pos + (shake > 0f ? Random.insideUnitSphere * shake * 0.22f : Vector3.zero);
            transform.LookAt(look);
            if (shake > 0f) shake = Mathf.Max(0f, shake - dt * 1.6f);

            float fovT = mode == CamMode.Menu ? 48f : 64f;
            cam.fieldOfView = Mathf.Lerp(cam.fieldOfView, fovT, dt * 3f);
        }
    }
}
