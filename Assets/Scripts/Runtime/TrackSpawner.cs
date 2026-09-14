using System.Collections.Generic;
using UnityEngine;

namespace DummySurfer
{
    /// <summary>Endless chunk streaming: solvable obstacle patterns, coin runs, powerups,
    /// buildings / poles / bridges décor, clouds. Fully pooled.</summary>
    public class TrackSpawner : MonoBehaviour
    {
        public static TrackSpawner I;

        const float Seg = 48f;
        float nextZ;
        int segIdx;
        int runSeed;
        float powerNextZ = 130f;
        float jetRowZ;

        class SegRec { public float endZ; public List<GameObject> objs = new List<GameObject>(); }

        Dictionary<string, GameObject> prefabs = new Dictionary<string, GameObject>();
        Dictionary<string, Queue<GameObject>> pools = new Dictionary<string, Queue<GameObject>>();
        Dictionary<GameObject, string> keyOf = new Dictionary<GameObject, string>();
        List<GameObject> clouds = new List<GameObject>();
        List<SegRec> active = new List<SegRec>();

        void Awake() { I = this; }

        // ============================================== PREFAB / POOL
        GameObject GetPrefab(string key, System.Func<GameObject> make)
        {
            GameObject p;
            if (!prefabs.TryGetValue(key, out p) || p == null)
            {
                p = make();
                p.SetActive(false);
                p.transform.SetParent(transform, false);
                prefabs[key] = p;
            }
            return p;
        }

        GameObject Spawn(string key, System.Func<GameObject> make, Vector3 pos)
        {
            Queue<GameObject> q;
            if (!pools.TryGetValue(key, out q)) { q = new Queue<GameObject>(); pools[key] = q; }
            GameObject go;
            if (q.Count > 0) { go = q.Dequeue(); go.SetActive(true); }
            else
            {
                go = Instantiate(GetPrefab(key, make));
                go.transform.SetParent(transform, true);
                go.SetActive(true);
            }
            go.transform.position = pos;
            go.transform.rotation = Quaternion.identity;
            keyOf[go] = key;
            return go;
        }

        public void Despawn(GameObject go)
        {
            if (go == null || !go.activeSelf) return;
            string key;
            if (!keyOf.TryGetValue(go, out key)) { Destroy(go); return; }
            keyOf.Remove(go);
            go.SetActive(false);
            pools[key].Enqueue(go);
        }

        // ============================================== LIFECYCLE
        public void ResetWorld()
        {
            foreach (var s in active)
                foreach (var o in s.objs) if (o != null) Despawn(o);
            active.Clear();
            runSeed = UnityEngine.Random.Range(1, 999999);
            segIdx = 0;
            nextZ = -Seg;   // start one segment BEHIND the player so the camera never sees the void
            powerNextZ = 130f;
            jetRowZ = 40f;
            if (clouds.Count == 0)
            {
                for (int i = 0; i < 8; i++)
                {
                    var c = Spawn("cloud", WorldFactory.Cloud,
                        new Vector3(UnityEngine.Random.Range(-16f, 16f), UnityEngine.Random.Range(12f, 24f), i * 42f));
                    clouds.Add(c);
                }
            }
        }

        void Update()
        {
            var g = GameManager.I;
            var p = PlayerController.I;
            if (g == null || p == null) return;
            if (g.st != GameManager.St.Run && g.st != GameManager.St.Dying && g.st != GameManager.St.Menu) return;

            float pz = p.z;
            int guard = 0;
            while (nextZ < pz + 250f && guard++ < 12) { SpawnSegment(nextZ); nextZ += Seg; }

            for (int i = active.Count - 1; i >= 0; i--)
            {
                if (active[i].endZ < pz - 48f)
                {
                    foreach (var o in active[i].objs) if (o != null) Despawn(o);
                    active.RemoveAt(i);
                }
            }

            // clouds drift
            foreach (var c in clouds)
            {
                if (c == null) continue;
                if (c.transform.position.z < pz - 30f)
                {
                    c.transform.position = new Vector3(UnityEngine.Random.Range(-16f, 16f), UnityEngine.Random.Range(12f, 24f), pz + 300f);
                }
            }

            // jetpack coin rows in the sky
            if (g.st == GameManager.St.Run && g.tJet > 0f)
            {
                while (jetRowZ < pz + 80f)
                {
                    int lane = UnityEngine.Random.Range(-1, 2);
                    for (int i = 0; i < 6; i++) Coin(lane, jetRowZ + i * 2.3f, 7.2f);
                    jetRowZ += 24f;
                }
            }
        }

        // ============================================== SEGMENTS
        void SpawnSegment(float z0)
        {
            var seg = new SegRec { endZ = z0 + Seg };
            active.Add(seg);
            var rnd = new System.Random(runSeed + segIdx * 7919);
            int si = segIdx;
            segIdx++;

            // track chunk
            seg.objs.Add(TrackOrPooled("chunk", z0));

            // décor: buildings, poles, occasional bridge
            for (int side = 0; side < 2; side++)
            {
                float sx = side == 0 ? -1f : 1f;
                int nB = 2 + (si % 2);
                for (int b = 0; b < nB; b++)
                {
                    int col = rnd.Next(8);
                    float w = 6f + rnd.Next(3);
                    float h = new[] { 9f, 13f, 18f, 22f }[rnd.Next(4)];
                    float bd = 7f + rnd.Next(4);
                    float bz = z0 + 4f + b * (Seg / nB) + (float)rnd.NextDouble() * 6f;
                    float bx = sx * (12.5f + (float)rnd.NextDouble() * 5f + bd * 0.4f);
                    var bl = Spawn("bld" + col + "_" + (int)w + "_" + (int)h + (rnd.Next(3) == 0 ? "_bb" : ""),
                        () => WorldFactory.Building(col, w, h, bd, seed: col * 31 + (int)w * 7, billboard: rnd.Next(3) == 0),
                        new Vector3(bx, 0f, bz));
                    bl.transform.rotation = Quaternion.Euler(0, sx > 0 ? -90f : 90f, 0);
                    seg.objs.Add(bl);
                }
                // poles every 24m
                var pole = Spawn("pole", WorldFactory.Pole, new Vector3(sx * 8.6f, 0f, z0 + 12f + (si % 2) * 24f));
                pole.transform.rotation = Quaternion.Euler(0, sx > 0 ? 180f : 0f, 0);
                seg.objs.Add(pole);
            }
            if (si % 5 == 3)
            {
                var br = Spawn("bridge", WorldFactory.Bridge, new Vector3(0f, 0f, z0 + Seg * 0.5f));
                seg.objs.Add(br);
            }

            // ---------- obstacle patterns (always ≥1 survivable lane) ----------
            if (si <= 1) // safe intro: welcome coins
            {
                int lane = 0;
                for (int i = 0; i < 8; i++) seg.objs.Add(Coin(lane, z0 + 10f + i * 2.3f, 1.1f));
                return;
            }

            float d = Mathf.Min(1f, z0 / 1400f);
            double roll = rnd.NextDouble();
            int pattern;
            if (d < 0.12f) pattern = roll < 0.55 ? 0 : (roll < 0.85 ? 1 : 3);
            else if (d < 0.3f) pattern = roll < 0.16 ? 0 : (roll < 0.42 ? 1 : (roll < 0.62 ? 2 : (roll < 0.8 ? 3 : (roll < 0.92 ? 4 : 6))));
            else pattern = roll < 0.08 ? 0 : (roll < 0.26 ? 1 : (roll < 0.42 ? 2 : (roll < 0.54 ? 3 : (roll < 0.66 ? 4 : (roll < 0.78 ? 5 : (roll < 0.9 ? 6 : 7))))));

            float mid = z0 + Seg * 0.5f;
            switch (pattern)
            {
                case 0: // coin line, safe
                {
                    int lane = rnd.Next(3) - 1;
                    for (int i = 0; i < 9; i++) seg.objs.Add(Coin(lane, z0 + 8f + i * 2.4f, 1.1f));
                    break;
                }
                case 1: // one train, coins beside
                {
                    int tl = rnd.Next(3) - 1;
                    float len = WorldFactory.TrainLens[rnd.Next(3)];
                    seg.objs.Add(Train(tl, z0 + 4f, len, false, false));
                    int cl = tl == 0 ? rnd.Next(2) * 2 - 1 : 0;
                    for (int i = 0; i < 8; i++) seg.objs.Add(Coin(cl, z0 + 6f + i * 2.4f, 1.1f));
                    break;
                }
                case 2: // two trains, free lane coins
                {
                    int free = rnd.Next(3) - 1;
                    for (int l = -1; l <= 1; l++)
                        if (l != free) seg.objs.Add(Train(l, z0 + 4f, WorldFactory.TrainLens[rnd.Next(2) + 1], false, false));
                    for (int i = 0; i < 8; i++) seg.objs.Add(Coin(free, z0 + 6f + i * 2.4f, 1.1f));
                    break;
                }
                case 3: // low barriers + coin arcs
                {
                    int n = d > 0.4f ? 2 : 1;
                    var lanes = Lanes(rnd, n);
                    foreach (var l in lanes)
                    {
                        seg.objs.Add(Barrier(l, mid, false));
                        for (int i = 0; i < 5; i++)
                        {
                            float t = i / 4f;
                            float cz = mid - 4.6f + t * 9.2f;
                            float cy = 1.1f + Mathf.Sin(t * Mathf.PI) * 1.35f;
                            seg.objs.Add(Coin(l, cz, cy));
                        }
                    }
                    int cl = FreeLane(lanes);
                    for (int i = 0; i < 6; i++) seg.objs.Add(Coin(cl, mid - 6f + i * 2.4f, 1.1f));
                    break;
                }
                case 4: // high barriers (roll) + coins under
                {
                    int n = d > 0.4f ? 2 : 1;
                    var lanes = Lanes(rnd, n);
                    foreach (var l in lanes)
                    {
                        seg.objs.Add(Barrier(l, mid, true));
                        for (int i = 0; i < 4; i++) seg.objs.Add(Coin(l, mid - 3f + i * 2.0f, 0.7f));
                    }
                    break;
                }
                case 5: // ramp train → roof run → gap
                {
                    int l = rnd.Next(3) - 1;
                    float len = WorldFactory.TrainLens[1];
                    seg.objs.Add(Train(l, z0 + 2f, len, true, false));
                    for (int i = 0; i < 7; i++) seg.objs.Add(Coin(l, z0 + 9f + i * 2.3f, 4.15f));
                    if (d > 0.3f)
                    {
                        seg.objs.Add(Train(l, z0 + 2f + len + 7f, WorldFactory.TrainLens[0], false, false));
                        for (int i = 0; i < 5; i++) seg.objs.Add(Coin(l, z0 + 2f + len + 9f + i * 2.3f, 4.15f));
                    }
                    int cl = l == 0 ? rnd.Next(2) * 2 - 1 : 0;
                    for (int i = 0; i < 5; i++) seg.objs.Add(Coin(cl, mid + i * 2.4f, 1.1f));
                    break;
                }
                case 6: // moving train!
                {
                    int ml = rnd.Next(3) - 1;
                    var mt = Train(ml, PlayerController.I.z + 215f, WorldFactory.TrainLens[1], false, true);
                    mt.GetComponent<MovingTrain>().Init(this);
                    seg.objs.Add(mt);
                    int cl = ml == 0 ? rnd.Next(2) * 2 - 1 : 0;
                    for (int i = 0; i < 7; i++) seg.objs.Add(Coin(cl, z0 + 8f + i * 2.4f, 1.1f));
                    if (d > 0.5f)
                    {
                        int bl = ml == 0 ? (cl == 1 ? -1 : 1) : (ml == -1 ? 1 : -1);
                        seg.objs.Add(Barrier(bl, mid, rnd.NextDouble() < 0.5));
                    }
                    break;
                }
                default: // gauntlet: low → high staggered + trains far side
                {
                    seg.objs.Add(Barrier(-1, z0 + 12f, false));
                    seg.objs.Add(Barrier(1, z0 + 20f, true));
                    seg.objs.Add(Barrier(0, z0 + 30f, false));
                    for (int i = 0; i < 5; i++) seg.objs.Add(Coin(-1, z0 + 8f + i * 2.2f, 1.1f + (i > 1 && i < 4 ? 1.2f : 0f)));
                    for (int i = 0; i < 4; i++) seg.objs.Add(Coin(0, z0 + 26f + i * 2.2f, 1.1f));
                    break;
                }
            }

            // powerups
            if (z0 >= powerNextZ)
            {
                powerNextZ = z0 + 210f + (float)rnd.NextDouble() * 90f;
                var kinds = new[] { PowerComp.K.Magnet, PowerComp.K.Jet, PowerComp.K.X2, PowerComp.K.Board };
                var k = kinds[rnd.Next(kinds.Length)];
                int pl = rnd.Next(3) - 1;
                seg.objs.Add(Power(pl, mid + 4f, k));
            }
        }

        static int[] Lanes(System.Random rnd, int n)
        {
            var all = new List<int> { -1, 0, 1 };
            var res = new List<int>();
            for (int i = 0; i < n; i++) { int k = rnd.Next(all.Count); res.Add(all[k]); all.RemoveAt(k); }
            return res.ToArray();
        }

        static int FreeLane(int[] used)
        {
            for (int l = -1; l <= 1; l++)
            {
                bool u = false;
                foreach (var x in used) if (x == l) u = true;
                if (!u) return l;
            }
            return 0;
        }

        // ============================================== SPAWN HELPERS
        GameObject TrackOrPooled(string key, float z)
        {
            var go = Spawn(key, () => WorldFactory.TrackChunk(Seg), new Vector3(0, 0, z + Seg * 0.5f));
            return go;
        }

        GameObject Train(int lane, float zStart, float len, bool ramp, bool moving)
        {
            string key = "tr" + laneless(lane) + "_" + (int)len + (ramp ? "r" : "") + (moving ? "m" : "");
            int ci = Mathf.Abs((lane + 2 + (int)len) % 4);
            var go = Spawn(key, () => WorldFactory.Train(ci, len, ramp, moving),
                new Vector3(lane * PlayerController.LaneW, 0f, zStart + len * 0.5f + (ramp ? 4.4f : 0f)));
            var oc = go.GetComponent<ObstacleComp>();
            oc.center = go.transform.position + new Vector3(0, 1.5f, 0);
            return go;
        }

        string laneless(int l) { return l == 0 ? "c" : (l < 0 ? "l" : "r"); }

        GameObject Barrier(int lane, float z, bool high)
        {
            var go = Spawn(high ? "barH" : "barL", () => WorldFactory.Barrier(high),
                new Vector3(lane * PlayerController.LaneW, 0f, z));
            var oc = go.GetComponent<ObstacleComp>();
            oc.center = go.transform.position;
            return go;
        }

        GameObject Coin(int lane, float z, float y)
        {
            var go = Spawn("coin", WorldFactory.Coin, new Vector3(lane * PlayerController.LaneW, y, z));
            var cc = go.GetComponent<CoinComp>();
            cc.active = true;
            return go;
        }

        GameObject Power(int lane, float z, PowerComp.K k)
        {
            var go = Spawn("pow" + k, () => WorldFactory.Power(k),
                new Vector3(lane * PlayerController.LaneW, 1.35f, z));
            var pc = go.GetComponent<PowerComp>();
            pc.active = true;
            return go;
        }
    }
}
