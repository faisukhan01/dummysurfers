using UnityEngine;
using DummySurfer.Utilities;

namespace DummySurfer.Player
{
    /// <summary>
    /// The subway inspector and his dog — pure visual chase pressure, 100% procedural.
    /// Sprint right behind the player when the run starts, drift back as you clean,
    /// close in after a stumble, and rush in for the grab when you crash.
    /// </summary>
    public sealed class GuardChaser : MonoBehaviour
    {
        private enum Mode { Ready, Running, Alert, Grab }

        private Transform _player;
        private Mode _mode = Mode.Ready;

        private Transform _gArmL, _gArmR, _gLegL, _gLegR, _dogBody;
        private float _cycle;
        private float _alertTimer;
        private float _runTime;
        private float _xSmooth;
        private Vector3 _pos;

        private const float RunBehindStart = 4.4f;   // breathing down the neck at launch
        private const float RunBehindFar = 12.5f;    // falls back when you're clean
        private const float AlertBehind = 4.8f;      // closes in after a stumble
        private const float GrabBehind = 1.1f;       // the catch

        private float BehindNow
        {
            get
            {
                switch (_mode)
                {
                    case Mode.Ready: return 3.6f;
                    case Mode.Alert: return AlertBehind;
                    case Mode.Grab: return GrabBehind;
                    default:
                        // ease from close to far over ~5s of clean running
                        float t = Mathf.Clamp01(_runTime / 5f);
                        return Mathf.Lerp(RunBehindStart, RunBehindFar, t);
                }
            }
        }

        public static GuardChaser Create(Transform player)
        {
            var go = new GameObject("GuardChaser");
            var c = go.AddComponent<GuardChaser>();
            c._player = player;
            c.Build();
            return c;
        }

        private void Build()
        {
            // ---- Inspector ----
            var body = Part("GuardBody", PrimitiveType.Capsule, new Vector3(0f, 0.95f, 0f), new Vector3(0.62f, 0.5f, 0.5f), VisualStyles.GuardNavy);
            Part("GuardBelly", PrimitiveType.Cube, new Vector3(0f, 0.78f, 0.24f), new Vector3(0.5f, 0.5f, 0.24f), VisualStyles.GuardNavy);
            Part("GuardBelt", PrimitiveType.Cube, new Vector3(0f, 0.62f, 0f), new Vector3(0.56f, 0.12f, 0.46f), VisualStyles.HazardYellow);
            var head = Part("GuardHead", PrimitiveType.Sphere, new Vector3(0f, 1.62f, 0f), new Vector3(0.34f, 0.32f, 0.32f), new Color(0.93f, 0.74f, 0.58f));
            Part("GuardCap", PrimitiveType.Cylinder, new Vector3(0f, 0.16f, 0f), new Vector3(0.4f, 0.08f, 0.4f), VisualStyles.GuardNavy, parent: head.transform);
            Part("GuardCapPeak", PrimitiveType.Cube, new Vector3(0f, 0.13f, 0.2f), new Vector3(0.32f, 0.04f, 0.2f), VisualStyles.GuardNavy, parent: head.transform);

            _gArmL = Pivot("GuardArmL", new Vector3(-0.36f, 1.28f, 0f), new Vector3(0.16f, 0.4f, 0.16f), new Vector3(0f, -0.3f, 0f), VisualStyles.GuardNavy);
            _gArmR = Pivot("GuardArmR", new Vector3(0.36f, 1.28f, 0f), new Vector3(0.16f, 0.4f, 0.16f), new Vector3(0f, -0.3f, 0f), VisualStyles.GuardNavy);
            _gLegL = Pivot("GuardLegL", new Vector3(-0.16f, 0.55f, 0f), new Vector3(0.2f, 0.42f, 0.2f), new Vector3(0f, -0.34f, 0f), VisualStyles.Sleeper);
            _gLegR = Pivot("GuardLegR", new Vector3(0.16f, 0.55f, 0f), new Vector3(0.2f, 0.42f, 0.2f), new Vector3(0f, -0.34f, 0f), VisualStyles.Sleeper);

            // ---- Dog ----
            var dog = new GameObject("Dog");
            dog.transform.SetParent(transform, false);
            dog.transform.localPosition = new Vector3(0.55f, 0f, 0.3f);
            _dogBody = dog.transform;
            Part("DogBody", PrimitiveType.Capsule, new Vector3(0f, 0.34f, 0f), new Vector3(0.22f, 0.3f, 0.42f), VisualStyles.DogBrown, parent: dog.transform, rotX: 90f);
            Part("DogHead", PrimitiveType.Sphere, new Vector3(0f, 0.5f, 0.3f), new Vector3(0.2f, 0.2f, 0.2f), VisualStyles.DogBrown, parent: dog.transform);
            Part("DogSnout", PrimitiveType.Cube, new Vector3(0f, 0.46f, 0.42f), new Vector3(0.1f, 0.08f, 0.12f), new Color(0.55f, 0.35f, 0.2f), parent: dog.transform);
            Part("DogEarL", PrimitiveType.Cube, new Vector3(-0.07f, 0.62f, 0.26f), new Vector3(0.05f, 0.12f, 0.04f), new Color(0.55f, 0.35f, 0.2f), parent: dog.transform);
            Part("DogEarR", PrimitiveType.Cube, new Vector3(0.07f, 0.62f, 0.26f), new Vector3(0.05f, 0.12f, 0.04f), new Color(0.55f, 0.35f, 0.2f), parent: dog.transform);
            Part("DogTail", PrimitiveType.Cube, new Vector3(0f, 0.48f, -0.32f), new Vector3(0.05f, 0.05f, 0.22f), VisualStyles.DogBrown, parent: dog.transform, rotX: -30f);
        }

        private static GameObject Part(string name, PrimitiveType type, Vector3 pos, Vector3 scale,
            Color color, Transform parent = null, float rotX = 0f)
        {
            var go = GameObject.CreatePrimitive(type);
            go.name = name;
            var col = go.GetComponent<Collider>();
            if (col != null) Destroy(col);
            go.transform.SetParent(parent != null ? parent : null, false);
            go.transform.localPosition = pos;
            go.transform.localRotation = Quaternion.Euler(rotX, 0f, 0f);
            go.transform.localScale = scale;
            VisualStyles.AddMesh(go, color);
            return go;
        }

        private Transform Pivot(string name, Vector3 pos, Vector3 limbScale, Vector3 limbOffset, Color color)
        {
            var pivot = new GameObject(name).transform;
            pivot.SetParent(transform, false);
            pivot.localPosition = pos;
            Part(name + "_Limb", PrimitiveType.Capsule, limbOffset, limbScale, color, parent: pivot);
            return pivot;
        }

        // ---------------- API ----------------

        public void EnterReady()
        {
            _mode = Mode.Ready;
            _runTime = 0f;
            SnapBehind();
        }

        public void RunStart()
        {
            _mode = Mode.Running;
            _runTime = 0f;
        }

        public void OnPlayerStumbled()
        {
            if (_mode == Mode.Running)
            {
                _mode = Mode.Alert;
                _alertTimer = 2.6f;
            }
        }

        public void Grab()
        {
            _mode = Mode.Grab;
        }

        private void SnapBehind()
        {
            if (_player == null) return;
            _xSmooth = _player.position.x;
            _pos = new Vector3(_xSmooth * 0.7f, 0f, _player.position.z - BehindNow);
            transform.position = _pos;
        }

        private void Update()
        {
            if (_player == null) return;
            float dt = Time.deltaTime;
            if (dt <= 0f) return;

            if (_mode == Mode.Alert)
            {
                _alertTimer -= dt;
                if (_alertTimer <= 0f && _mode == Mode.Alert) { _mode = Mode.Running; _runTime = 2.2f; }
            }
            if (_mode == Mode.Running) _runTime += dt;

            // Follow player lane with weight, keep the chase offset
            _xSmooth = Mathf.Lerp(_xSmooth, _player.position.x * 0.75f, 1f - Mathf.Exp(-7f * dt));
            Vector3 target = new Vector3(_xSmooth, 0f, _player.position.z - BehindNow);
            float speed = _mode == Mode.Grab ? 16f : (_mode == Mode.Alert ? 13f : 10.5f);
            _pos.z = Mathf.MoveTowards(transform.position.z, target.z, speed * dt);
            _pos.x = Mathf.Lerp(transform.position.x, target.x, 1f - Mathf.Exp(-6f * dt));

            float bob = _mode == Mode.Ready
                ? Mathf.Sin(Time.unscaledTime * 2.4f) * 0.02f
                : Mathf.Abs(Mathf.Cos(_cycle)) * 0.07f;
            transform.position = new Vector3(_pos.x, bob, _pos.z);

            // ---- limb animation ----
            if (_mode == Mode.Ready)
            {
                _cycle += dt * 2f;
                float idle = Mathf.Sin(_cycle * 2f) * 5f;
                SetPivot(_gArmL, idle - 6f); SetPivot(_gArmR, -idle - 6f);
                SetPivot(_gLegL, 0f); SetPivot(_gLegR, 0f);
                if (_dogBody != null) _dogBody.localPosition = new Vector3(0.55f, 0f, 0.3f);
                return;
            }

            float freq = _mode == Mode.Grab ? 17f : 13.5f;
            _cycle += dt * freq;
            float swing = Mathf.Sin(_cycle);
            float armRaise = _mode == Mode.Grab ? -95f : 0f;
            SetPivot(_gArmL, -swing * 42f + armRaise);
            SetPivot(_gArmR, swing * 42f + armRaise);
            SetPivot(_gLegL, swing * 48f);
            SetPivot(_gLegR, -swing * 48f);

            if (_dogBody != null)
            {
                var dp = _dogBody.localPosition;
                dp.y = Mathf.Abs(Mathf.Cos(_cycle * 1.3f)) * 0.09f;
                dp.x = 0.55f + Mathf.Sin(_cycle * 0.7f) * 0.08f;
                _dogBody.localPosition = dp;
                _dogBody.localRotation = Quaternion.Euler(Mathf.Sin(_cycle) * 4f, 0f, 0f);
            }
        }

        private static void SetPivot(Transform t, float xAngle)
        {
            if (t != null) t.localRotation = Quaternion.Euler(xAngle, 0f, 0f);
        }
    }
}
