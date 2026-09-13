using UnityEngine;
using DummySurfer.Data;
using DummySurfer.Utilities;

namespace DummySurfer.Player
{
    public enum RunnerAnimState { Idle, Run, Jump, Slide, Stumble, Dead, Victory }

    /// <summary>
    /// Procedural animation for the primitive-built runner (no rigs/animations assets needed).
    /// Gives clean anticipation and recovery for jump/slide and an energetic run cycle,
    /// matching the "Feel" requirements of spec 4.3 — while remaining 100% original art.
    /// </summary>
    public sealed class RunnerVisualRig : MonoBehaviour
    {
        private Transform _visual;      // pitched/leaned root
        private Transform _torso, _head, _armL, _armR, _legL, _legR;
        private Quaternion _armLBase, _armRBase, _legLBase, _legRBase;

        private RunnerAnimState _state = RunnerAnimState.Idle;
        private float _cycle;           // run-cycle phase
        private float _blend;           // state pose blend 0..1
        private float _lean;            // lane-change lean -1..1
        private float _stumbleT;

        private static readonly Quaternion ArmBackIdle = Quaternion.Euler(-8f, 0f, 0f);

        public void Bind(Transform visual, Transform torso, Transform head,
                         Transform armL, Transform armR, Transform legL, Transform legR)
        {
            _visual = visual; _torso = torso; _head = head;
            _armL = armL; _armR = armR; _legL = legL; _legR = legR;
            if (_armL != null) _armLBase = _armL.localRotation;
            if (_armR != null) _armRBase = _armR.localRotation;
            if (_legL != null) _legLBase = _legL.localRotation;
            if (_legR != null) _legRBase = _legR.localRotation;
        }

        public void ApplyColors(CharacterStats stats)
        {
            SetColor(_torso, stats.primary);
            SetColor(_head, stats.skin);
            SetColor(_armL, stats.primary);
            SetColor(_armR, stats.primary);
            SetColor(_legL, stats.pants);
            SetColor(_legR, stats.pants);
            var cap = _head != null && _head.childCount > 0 ? _head.GetChild(0) : null;
            SetColor(cap, stats.secondary);
        }

        private static void SetColor(Transform t, Color c)
        {
            if (t == null) return;
            var r = t.GetComponent<MeshRenderer>();
            if (r != null) r.sharedMaterial = VisualStyles.Lit(c);
        }

        public void Play(RunnerAnimState state)
        {
            if (_state == state) return;
            _state = state;
            _blend = 0f;
            if (state == RunnerAnimState.Stumble) _stumbleT = 0f;
        }

        public void SetLean(float lean01) => _lean = Mathf.Clamp(lean01, -1f, 1f);

        /// <summary>Drive every frame from PlayerController (dt unscaled-safe).</summary>
        public void Tick(float speedNorm, float dt)
        {
            if (_visual == null) return;
            _blend = Mathf.MoveTowards(_blend, 1f, dt * 8f);
            float b = Mathf.SmoothStep(0f, 1f, _blend);

            float pitch = 0f, rootY = 0f;
            float armLx = 0f, armRx = 0f, legLx = 0f, legRx = 0f;

            switch (_state)
            {
                case RunnerAnimState.Idle:
                    _cycle += dt * 2f;
                    rootY = Mathf.Sin(_cycle * 2f) * 0.012f;
                    armLx = Mathf.Sin(_cycle * 2f) * 4f - 6f;
                    armRx = -armLx;
                    break;

                case RunnerAnimState.Run:
                {
                    float freq = Mathf.Lerp(9f, 15f, speedNorm);
                    _cycle += dt * freq;
                    float swing = Mathf.Sin(_cycle);
                    legLx = swing * 52f;
                    legRx = -swing * 52f;
                    armLx = -swing * 46f - 10f;
                    armRx = swing * 46f - 10f;
                    rootY = Mathf.Abs(Mathf.Cos(_cycle)) * 0.055f;
                    pitch = -6f - speedNorm * 5f;   // slight forward attack at speed
                    break;
                }

                case RunnerAnimState.Jump:
                    legLx = Mathf.LerpAngle(legLx, 68f, b);
                    legRx = Mathf.LerpAngle(legRx, 34f, b);
                    armLx = Mathf.LerpAngle(armLx, -120f, b);
                    armRx = Mathf.LerpAngle(armRx, -95f, b);
                    pitch = Mathf.LerpAngle(pitch, 8f, b);
                    break;

                case RunnerAnimState.Slide:
                    pitch = Mathf.LerpAngle(pitch, -74f, b);
                    rootY = Mathf.Lerp(rootY, -0.42f, b);
                    legLx = Mathf.LerpAngle(legLx, 10f, b);
                    legRx = Mathf.LerpAngle(legRx, 4f, b);
                    armLx = Mathf.LerpAngle(armLx, 30f, b);
                    armRx = Mathf.LerpAngle(armRx, 26f, b);
                    break;

                case RunnerAnimState.Stumble:
                    _stumbleT += dt;
                    float flail = Mathf.Sin(_stumbleT * 28f) * Mathf.Exp(-_stumbleT * 2.2f);
                    pitch = -14f * Mathf.Exp(-_stumbleT * 3f);
                    armLx = flail * 70f - 40f;
                    armRx = -flail * 70f - 40f;
                    rootY = -0.08f * Mathf.Exp(-_stumbleT * 3f);
                    break;

                case RunnerAnimState.Dead:
                    pitch = Mathf.LerpAngle(pitch, 86f, b);
                    rootY = Mathf.Lerp(rootY, -0.55f, b);
                    armLx = Mathf.LerpAngle(armLx, 60f, b);
                    armRx = Mathf.LerpAngle(armRx, -60f, b);
                    break;

                case RunnerAnimState.Victory:
                    _cycle += dt * 6f;
                    armLx = Mathf.LerpAngle(armLx, -160f + Mathf.Sin(_cycle) * 12f, b);
                    armRx = Mathf.LerpAngle(armRx, -160f - Mathf.Sin(_cycle) * 12f, b);
                    rootY = Mathf.Abs(Mathf.Sin(_cycle)) * 0.09f;
                    break;
            }

            // Apply pose (state-blended via direct targets; blending baked into target math above)
            _visual.localPosition = new Vector3(_visual.localPosition.x, rootY, _visual.localPosition.z);
            _visual.localRotation = Quaternion.Slerp(_visual.localRotation,
                Quaternion.Euler(pitch, 0f, -_lean * 14f), 1f - Mathf.Exp(-12f * dt));

            if (_armL != null) _armL.localRotation = Quaternion.Slerp(_armL.localRotation, _armLBase * Quaternion.Euler(armLx, 0f, 0f), 1f - Mathf.Exp(-14f * dt));
            if (_armR != null) _armR.localRotation = Quaternion.Slerp(_armR.localRotation, _armRBase * Quaternion.Euler(armRx, 0f, 0f), 1f - Mathf.Exp(-14f * dt));
            if (_legL != null) _legL.localRotation = Quaternion.Slerp(_legL.localRotation, _legLBase * Quaternion.Euler(legLx, 0f, 0f), 1f - Mathf.Exp(-14f * dt));
            if (_legR != null) _legR.localRotation = Quaternion.Slerp(_legR.localRotation, _legRBase * Quaternion.Euler(legRx, 0f, 0f), 1f - Mathf.Exp(-14f * dt));
        }

        public void Flash(Color c)
        {
            if (_torso == null) return;
            var r = _torso.GetComponent<MeshRenderer>();
            if (r != null) r.sharedMaterial = VisualStyles.Unlit(c);
        }
    }
}
