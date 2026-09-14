using UnityEngine;

namespace DummySurfer
{
    /// <summary>Inspector + dog chase: sprint close at start, close in on stumble, grab on bust.</summary>
    public class Chaser : MonoBehaviour
    {
        public static Chaser I;
        public CharacterRig guard, dog;
        float gap = 6.2f;
        float xCur;
        bool whistleDone;

        void Awake() { I = this; }

        public void Attach(CharacterRig g, CharacterRig d)
        {
            guard = g; dog = d;
            if (g != null) g.transform.SetParent(transform, false);
            if (d != null) { d.transform.SetParent(transform, false); d.transform.localPosition = new Vector3(1.0f, 0f, -0.9f); }
            Hide();
        }

        public void Hide()
        {
            gap = 40f;
            transform.position = new Vector3(0, 0, -60f);
            whistleDone = false;
        }

        void Update()
        {
            var g = GameManager.I;
            var p = PlayerController.I;
            if (g == null || p == null || guard == null) return;
            if (g.st == GameManager.St.Pause) return;

            float target;
            if (g.st == GameManager.St.Run)
            {
                if (!whistleDone && p.z > 1f) { whistleDone = true; Fx.Play("whistle", 0.8f); }
                // visible sprint at the start, close in on stumble, drop back when clean
                target = p.z < 40f ? 8.2f : (g.tStumble > 0f ? 5.5f : 30f);
            }
            else if (g.st == GameManager.St.Dying) target = 1.15f;
            else { transform.position = new Vector3(0, 0, p.z - 60f); return; }

            gap = Mathf.Lerp(gap, target, Time.deltaTime * (g.st == GameManager.St.Dying ? 6f : 2.2f));
            xCur = Mathf.Lerp(xCur, p.x, Time.deltaTime * 3.5f);
            float gz = p.z - gap;

            bool vis = gap < 12.5f;
            guard.gameObject.SetActive(vis);
            if (dog != null) dog.gameObject.SetActive(vis && gap < 9.5f);
            transform.position = new Vector3(xCur, 0f, gz);

            string mode = g.st == GameManager.St.Dying ? "grab" : "run";
            float speedF = g.st == GameManager.St.Run ? Mathf.Clamp01(p.EffSpeed / 20f) : 0.6f;
            guard.phase += Time.deltaTime * p.EffSpeed * 1.55f;
            guard.Pose(mode, Time.time, speedF);
            if (dog != null && dog.gameObject.activeSelf)
            {
                dog.phase += Time.deltaTime * p.EffSpeed * 1.8f;
                dog.Pose("run", Time.time, speedF);
            }
        }
    }
}
