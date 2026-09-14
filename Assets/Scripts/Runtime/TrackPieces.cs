using UnityEngine;

namespace DummySurfer
{
    /// <summary>Component attached to every obstacle root so the player can reason about hits.</summary>
    public class ObstacleComp : MonoBehaviour
    {
        public enum T { Train, Low, High }
        public T type;
        public float topY;
        public float halfW = 1.02f;
        public Vector3 center;
        public bool moving;
        public float moveSpeed;
    }

    /// <summary>Moving trains slide toward the player.</summary>
    public class MovingTrain : MonoBehaviour
    {
        public float speed = 9f;
        TrackSpawner owner;

        public void Init(TrackSpawner o) { owner = o; }

        void Update()
        {
            if (GameManager.I == null || GameManager.I.st != GameManager.St.Run) return;
            float dt = Time.deltaTime;
            transform.position += Vector3.back * (speed + PlayerController.I.EffSpeed * 0.15f) * dt;
            var oc = GetComponent<ObstacleComp>();
            if (oc != null) oc.center = transform.position;
            if (transform.position.z < PlayerController.I.z - 34f && owner != null) owner.Despawn(gameObject);
        }
    }

    /// <summary>Collectible coin (star-embossed disc).</summary>
    public class CoinComp : MonoBehaviour
    {
        public bool active = true;
        static readonly Transform[] tmp = new Transform[1];

        void Update()
        {
            var g = GameManager.I;
            var p = PlayerController.I;
            if (g == null || p == null || !active) return;
            transform.Rotate(0f, Time.deltaTime * 260f, 0f, Space.Self);
            if (g.st != GameManager.St.Run) return;

            if (g.tMagnet > 0f)
            {
                Vector3 target = new Vector3(p.x, p.y + 1.1f, p.z + 0.4f);
                float d = Vector3.Distance(transform.position, target);
                if (d < 7.5f)
                    transform.position = Vector3.MoveTowards(transform.position, target, (26f / Mathf.Max(2f, d)) * Time.deltaTime * 9f);
            }
            if (transform.position.z < p.z - 20f) { Despawn(); }
        }

        public void Collect()
        {
            if (!active) return;
            active = false;
            GameManager.I.AddCoin();
            Fx.Play("coin", 0.55f);
            Despawn();
        }

        public void Despawn()
        {
            active = false;
            var sp = GetComponentInParent<TrackSpawner>();
            if (sp != null) sp.Despawn(gameObject);
            else gameObject.SetActive(false);
        }
    }

    /// <summary>Powerup pickup (magnet / jetpack / x2 / hoverboard crate).</summary>
    public class PowerComp : MonoBehaviour
    {
        public enum K { Magnet, Jet, X2, Board }
        public K kind;
        public bool active = true;
        float t0;

        void OnEnable() { t0 = Time.time; }

        void Update()
        {
            if (!active) return;
            float b = Mathf.Sin((Time.time - t0) * 3.2f) * 0.18f;
            Vector3 lp = transform.localPosition;
            transform.localPosition = new Vector3(lp.x, Mathf.Sign(lp.y) * (Mathf.Abs(lp.y) + 0f) + b, lp.z);
            if (PlayerController.I != null && transform.position.z < PlayerController.I.z - 20f) Despawn();
        }

        public void Consume()
        {
            active = false;
            var sp = GetComponentInParent<TrackSpawner>();
            if (sp != null) sp.Despawn(gameObject);
            else gameObject.SetActive(false);
        }

        public void Despawn()
        {
            active = false;
            var sp = GetComponentInParent<TrackSpawner>();
            if (sp != null) sp.Despawn(gameObject);
            else gameObject.SetActive(false);
        }
    }
}
