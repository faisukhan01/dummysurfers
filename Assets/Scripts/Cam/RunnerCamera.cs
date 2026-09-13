using UnityEngine;
using DummySurfer.Data;
using DummySurfer.Utilities;

namespace DummySurfer.Cam
{
    /// <summary>
    /// Third-person runner camera (spec 4.3 / 10 CAMERA): smooth follow, controlled
    /// height/distance, subtle speed-based FOV, minimal shake. Remote runner stays readable
    /// because the local player stays near screen center by construction.
    /// </summary>
    public sealed class RunnerCamera : MonoBehaviour
    {
        public static RunnerCamera Main { get; private set; }

        public Transform Target { get; set; }
        public float SpeedNorm { get; set; }

        private GameConfig _cfg;
        private Vector3 _pos;
        private float _fov;
        private float _shakeAmp, _shakeTime, _noiseSeed;

        private void Awake()
        {
            Main = this;
            _cfg = GameConfig.Runtime;
            _pos = transform.position;
            _fov = _cfg.camBaseFov;
            _noiseSeed = Random.value * 100f;
        }

        private void OnDestroy()
        {
            if (Main == this) Main = null;
        }

        public void SnapToTarget()
        {
            if (Target == null) return;
            _pos = DesiredPosition();
            transform.position = _pos;
            transform.LookAt(LookPoint());
        }

        private Vector3 DesiredPosition()
        {
            Vector3 t = Target != null ? Target.position : Vector3.zero;
            return new Vector3(t.x * 0.42f, _cfg.camHeight, t.z - _cfg.camBack);
        }

        private Vector3 LookPoint()
        {
            Vector3 t = Target != null ? Target.position : Vector3.zero;
            return new Vector3(t.x * 0.6f, 1.4f, t.z + 5.5f);
        }

        private void LateUpdate()
        {
            if (Target == null) return;
            float dt = Time.unscaledDeltaTime;
            if (dt <= 0f) return;

            _pos = _pos.Approach(DesiredPosition(), 9f, dt);

            if (_shakeTime > 0f)
            {
                _shakeTime -= dt;
                float n1 = Mathf.PerlinNoise(_noiseSeed, Time.unscaledTime * 24f) - 0.5f;
                float n2 = Mathf.PerlinNoise(_noiseSeed + 7f, Time.unscaledTime * 24f) - 0.5f;
                _pos += new Vector3(n1, n2, 0f) * _shakeAmp;
                _shakeAmp = Mathf.Lerp(_shakeAmp, 0f, 1f - Mathf.Exp(-6f * dt));
            }

            transform.position = _pos;
            transform.LookAt(LookPoint());

            float fovTarget = _cfg.camBaseFov + SpeedNorm * _cfg.camSpeedFov;
            _fov = Mathf.Lerp(_fov, fovTarget, 1f - Mathf.Exp(-3f * dt));
            var cam = GetComponent<Camera>();
            if (cam != null && Mathf.Abs(cam.fieldOfView - _fov) > 0.01f)
                cam.fieldOfView = _fov;
        }

        public void Shake(float amplitude, float seconds)
        {
            _shakeAmp = Mathf.Max(_shakeAmp, amplitude);
            _shakeTime = Mathf.Max(_shakeTime, seconds);
        }
    }
}
