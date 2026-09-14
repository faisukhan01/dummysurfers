using System.Collections.Generic;
using UnityEngine;

namespace DummySurfer
{
    /// <summary>Builds every 3D prefab procedurally: trains, barriers, coins, powerups,
    /// buildings, poles, bridges, clouds, track chunks. Bright Subway-Surfers-style city.</summary>
    public static class WorldFactory
    {
        public static readonly Color[] TrainCols =
        {
            C(0xE8, 0x48, 0x48), C(0x4A, 0x7B, 0xD0), C(0xF2, 0xB2, 0x33), C(0x2F, 0xB8, 0xB0), C(0x9B, 0x59, 0xD0)
        };

        public static readonly Color[] BuildingCols =
        {
            C(0xF2, 0xD3, 0xA2), C(0xE8, 0x87, 0x5F), C(0x7F, 0xC8, 0xA9), C(0x8F, 0xB7, 0xE0),
            C(0xE8, 0xB4, 0xC8), C(0xF2, 0xE4, 0xC8), C(0x9A, 0x8F, 0xD0), C(0xD9, 0xA0, 0x5B)
        };

        public static readonly float[] TrainLens = { 12f, 18f, 24f };

        static GameObject Part(Transform parent, PrimitiveType t, Vector3 lp, Vector3 ls, Material m, string n, bool collider = false)
        {
            var go = GameObject.CreatePrimitive(t);
            if (!collider) Object.Destroy(go.GetComponent<Collider>());
            go.name = n;
            go.transform.SetParent(parent, false);
            go.transform.localPosition = lp;
            go.transform.localScale = ls;
            go.GetComponent<MeshRenderer>().sharedMaterial = m;
            return go;
        }

        static void SetLayerRecursive(GameObject go, int layer)
        {
            go.layer = layer;
            foreach (Transform c in go.transform) SetLayerRecursive(c.gameObject, layer);
        }

        static Color C(int r, int g, int b) { return new Color32((byte)r, (byte)g, (byte)b, 255); }
        static Color Darker(Color c, float f) { return Color.Lerp(c, Color.black, f); }

        static Material TexMat(Texture2D tex, float tx, float ty, bool alpha)
        {
            var m = Fx.MatTex(tex, alpha, tx, ty);
            return m;
        }

        // ================================================= TRAIN
        public static GameObject Train(int colIdx, float len, bool ramp, bool moving)
        {
            var col = TrainCols[colIdx % TrainCols.Length];
            var root = new GameObject("train");
            var bodyM = Fx.Mat(col);
            var roofM = Fx.Mat(Darker(col, 0.32f));
            var sideTex = Fx.TexTrainSide(col);
            var frontTex = Fx.TexTrainFront(col);

            var body = Part(root.transform, PrimitiveType.Cube, new Vector3(0, 1.6f, 0), new Vector3(2.05f, 2.9f, len), bodyM, "body", true);
            body.layer = Fx.LTrain;
            Part(root.transform, PrimitiveType.Cube, new Vector3(0, 3.12f, 0), new Vector3(2.14f, 0.16f, len * 0.99f), roofM, "roof");

            // textured sides (512px = 4m)
            var sideM = TexMat(sideTex, Mathf.Max(1f, len / 4f), 1f, false);
            var sl = Part(root.transform, PrimitiveType.Quad, new Vector3(-1.033f, 1.6f, 0), new Vector3(len, 2.9f, 1), sideM, "sideL");
            sl.transform.localRotation = Quaternion.Euler(0, -90, 0);
            var sr = Part(root.transform, PrimitiveType.Quad, new Vector3(1.033f, 1.6f, 0), new Vector3(len, 2.9f, 1), sideM, "sideR");
            sr.transform.localRotation = Quaternion.Euler(0, 90, 0);

            // textured ends (both ends get a face — SS style)
            var frontM = Fx.MatTex(frontTex, false);
            var fr = Part(root.transform, PrimitiveType.Quad, new Vector3(0, 1.6f, -len / 2f - 0.012f), new Vector3(2.05f, 2.9f, 1), frontM, "front");
            fr.transform.localRotation = Quaternion.Euler(0, 180, 0);
            var bk = Part(root.transform, PrimitiveType.Quad, new Vector3(0, 1.6f, len / 2f + 0.012f), new Vector3(2.05f, 2.9f, 1), frontM, "back");

            // bogies (dark wheel skirts)
            var bogieM = Fx.Mat(C(0x2E, 0x31, 0x3A));
            Part(root.transform, PrimitiveType.Cube, new Vector3(0, 0.26f, -len / 2f + 1.7f), new Vector3(1.7f, 0.52f, 2.6f), bogieM, "bogF");
            Part(root.transform, PrimitiveType.Cube, new Vector3(0, 0.26f, len / 2f - 1.7f), new Vector3(1.7f, 0.52f, 2.6f), bogieM, "bogB");

            // roof AC units
            var acM = Fx.Mat(C(0xB6, 0xBD, 0xC6));
            int nAc = Mathf.Max(1, Mathf.FloorToInt(len / 8f));
            for (int i = 0; i < nAc; i++)
            {
                float az = -len / 2f + (i + 0.5f) * (len / nAc);
                Part(root.transform, PrimitiveType.Cube, new Vector3(0, 3.28f, az), new Vector3(1.1f, 0.24f, 1.7f), acM, "ac" + i);
            }

            if (ramp)
            {
                float ang = Mathf.Atan2(3.16f, 4.4f) * Mathf.Rad2Deg;
                var rm = Fx.Mat(Darker(col, 0.5f));
                var wedge = Part(root.transform, PrimitiveType.Cube, new Vector3(0, 1.58f, -len / 2f - 2.2f), new Vector3(2.05f, 0.24f, 5.4f), rm, "ramp", true);
                wedge.transform.localRotation = Quaternion.Euler(-ang, 0, 0);
                wedge.layer = Fx.LGround; // walkable slope: ground ray rides it, front-hit check ignores it
                Part(root.transform, PrimitiveType.Cube, new Vector3(0, 0.9f, -len / 2f - 4.15f), new Vector3(1.9f, 1.8f, 0.25f), rm, "support");
                // chevron stripes on the ramp nose
                var ch = Part(root.transform, PrimitiveType.Quad, new Vector3(0, 1.62f, -len / 2f - 4.86f), new Vector3(2.05f, 0.5f, 1), TexMat(Fx.TexStripes(), 2f, 0.5f, false), "chev");
                ch.transform.localRotation = Quaternion.Euler(0, 180, 0);
            }

            var oc = root.AddComponent<ObstacleComp>();
            oc.type = ObstacleComp.T.Train;
            oc.topY = 3.1f;
            oc.halfW = 1.02f;

            if (moving)
            {
                root.AddComponent<MovingTrain>();
                // headlight glow
                var gl = Part(root.transform, PrimitiveType.Quad, new Vector3(0, 0.9f, -len / 2f - 0.05f), new Vector3(1.6f, 1.0f, 1), Fx.MatGlow(C(0xFF, 0xF3, 0xB0)), "light");
                gl.transform.localRotation = Quaternion.Euler(0, 180, 0);
            }
            return root;
        }

        // ================================================= BARRIERS
        public static GameObject Barrier(bool high)
        {
            var root = new GameObject(high ? "barHi" : "barLo");
            var postM = Fx.Mat(C(0x4A, 0x55, 0x68));
            var stripeM = TexMat(Fx.TexStripes(), 2f, 1f, false);

            if (!high)
            {
                Part(root.transform, PrimitiveType.Cube, new Vector3(-0.92f, 0.5f, 0), new Vector3(0.11f, 1.0f, 0.11f), postM, "pL", true).layer = Fx.LObstacle;
                Part(root.transform, PrimitiveType.Cube, new Vector3(0.92f, 0.5f, 0), new Vector3(0.11f, 1.0f, 0.11f), postM, "pR", true).layer = Fx.LObstacle;
                Part(root.transform, PrimitiveType.Cube, new Vector3(0, 0.72f, 0), new Vector3(1.95f, 0.55f, 0.12f), stripeM, "bar");
                Part(root.transform, PrimitiveType.Cube, new Vector3(0, 0.12f, 0), new Vector3(1.95f, 0.14f, 0.10f), postM, "foot");
                var bc = root.AddComponent<BoxCollider>();
                bc.center = new Vector3(0, 0.5f, 0);
                bc.size = new Vector3(2.05f, 1.0f, 0.34f);
                SetLayerRecursive(root, Fx.LObstacle);
                var oc = root.AddComponent<ObstacleComp>();
                oc.type = ObstacleComp.T.Low; oc.topY = 1.0f; oc.halfW = 1.02f;
            }
            else
            {
                Part(root.transform, PrimitiveType.Cube, new Vector3(-0.92f, 1.3f, 0), new Vector3(0.11f, 2.6f, 0.11f), postM, "pL", true);
                Part(root.transform, PrimitiveType.Cube, new Vector3(0.92f, 1.3f, 0), new Vector3(0.11f, 2.6f, 0.11f), postM, "pR", true);
                Part(root.transform, PrimitiveType.Cube, new Vector3(0, 2.12f, 0), new Vector3(1.95f, 0.85f, 0.12f), stripeM, "panel");
                Part(root.transform, PrimitiveType.Cube, new Vector3(0, 1.52f, 0), new Vector3(1.95f, 0.14f, 0.10f), postM, "duckbar");
                Part(root.transform, PrimitiveType.Cube, new Vector3(0, 2.62f, 0), new Vector3(2.2f, 0.12f, 0.3f), postM, "top");
                var bc = root.AddComponent<BoxCollider>();
                bc.center = new Vector3(0, 1.85f, 0);
                bc.size = new Vector3(2.05f, 1.5f, 0.34f);
                SetLayerRecursive(root, Fx.LObstacle);
                var oc = root.AddComponent<ObstacleComp>();
                oc.type = ObstacleComp.T.High; oc.topY = 2.6f; oc.halfW = 1.02f;
            }
            return root;
        }

        // ================================================= COIN
        public static GameObject Coin()
        {
            var root = new GameObject("coin");
            var gold = Fx.MatGlow(C(0xF7, 0xB9, 0x2B));
            var disc = Part(root.transform, PrimitiveType.Cylinder, Vector3.zero, new Vector3(0.80f, 0.012f, 0.80f), gold, "disc");
            disc.transform.localRotation = Quaternion.Euler(90f, 0, 0);
            var coinTex = Fx.SprCoin().texture;
            var cm = Fx.MatTex(coinTex, true);
            var qf = Part(root.transform, PrimitiveType.Quad, new Vector3(0, 0, 0.012f), new Vector3(0.86f, 0.86f, 1), cm, "cf");
            qf.transform.localRotation = Quaternion.Euler(0, 180, 0);
            Part(root.transform, PrimitiveType.Quad, new Vector3(0, 0, -0.012f), new Vector3(0.86f, 0.86f, 1), cm, "cb");
            var sc = root.AddComponent<SphereCollider>();
            sc.isTrigger = true;
            sc.radius = 0.55f;
            SetLayerRecursive(root, Fx.LCoin);
            root.AddComponent<CoinComp>();
            return root;
        }

        // ================================================= POWERUP
        public static GameObject Power(PowerComp.K kind)
        {
            var root = new GameObject("pow_" + kind);
            var glowM = Fx.MatTex(Fx.SprGlow().texture, true);
            Sprite icon;
            Color ringCol;
            switch (kind)
            {
                case PowerComp.K.Magnet: icon = Fx.SprIcon("magnet"); ringCol = GameManager.C.red; break;
                case PowerComp.K.Jet: icon = Fx.SprIcon("rocket"); ringCol = GameManager.C.orange; break;
                case PowerComp.K.X2: icon = Fx.SprIcon("x2"); ringCol = GameManager.C.gold; break;
                default: icon = Fx.SprIcon("board"); ringCol = GameManager.C.blueBright; break;
            }
            var g1 = Part(root.transform, PrimitiveType.Quad, Vector3.zero, new Vector3(1.7f, 1.7f, 1), glowM, "glow");
            var g2 = Part(root.transform, PrimitiveType.Quad, Vector3.zero, new Vector3(1.7f, 1.7f, 1), glowM, "glow2");
            g2.transform.localRotation = Quaternion.Euler(0, 90, 0);
            var ring = Part(root.transform, PrimitiveType.Cylinder, Vector3.zero, new Vector3(1.06f, 0.03f, 1.06f), Fx.Mat(ringCol), "ring");
            ring.transform.localRotation = Quaternion.Euler(90f, 0, 0);
            var icM = Fx.MatTex(icon.texture, true);
            var iq = Part(root.transform, PrimitiveType.Quad, new Vector3(0, 0, 0.03f), new Vector3(0.72f, 0.72f, 1), icM, "icon");
            iq.transform.localRotation = Quaternion.Euler(0, 180, 0);
            Part(root.transform, PrimitiveType.Quad, new Vector3(0, 0, -0.03f), new Vector3(0.72f, 0.72f, 1), icM, "iconB");

            var sc = root.AddComponent<SphereCollider>();
            sc.isTrigger = true;
            sc.radius = 0.85f;
            SetLayerRecursive(root, Fx.LPower);
            var pc = root.AddComponent<PowerComp>();
            pc.kind = kind;
            return root;
        }

        // ================================================= BUILDING
        public static GameObject Building(int colIdx, float w, float h, float d, int seed, bool billboard)
        {
            var col = BuildingCols[colIdx % BuildingCols.Length];
            var root = new GameObject("bld");
            var winM = TexMat(Fx.TexWindows(col, seed), Mathf.Max(1f, w / 6f), Mathf.Max(1f, h / 6f), false);
            Part(root.transform, PrimitiveType.Cube, new Vector3(0, h / 2f, 0), new Vector3(w, h, d), winM, "body");
            Part(root.transform, PrimitiveType.Cube, new Vector3(0, h + 0.15f, 0), new Vector3(w + 0.4f, 0.3f, d + 0.4f), Fx.Mat(Darker(col, 0.45f)), "roof");

            var sys = new System.Random(seed * 977 + (int)h);
            // rooftop water tank
            if (sys.NextDouble() < 0.45)
            {
                var tankM = Fx.Mat(C(0x8E, 0x97, 0xA6));
                var tank = Part(root.transform, PrimitiveType.Cylinder, new Vector3(w * 0.22f, h + 0.75f, d * 0.2f), new Vector3(0.9f, 0.5f, 0.9f), tankM, "tank");
                Part(root.transform, PrimitiveType.Cylinder, new Vector3(w * 0.22f, h + 1.15f, d * 0.2f), new Vector3(0.12f, 0.3f, 0.12f), tankM, "tankleg");
            }
            // rooftop AC box
            if (sys.NextDouble() < 0.6)
                Part(root.transform, PrimitiveType.Cube, new Vector3(-w * 0.2f, h + 0.5f, -d * 0.15f), new Vector3(1.3f, 0.5f, 1.0f), Fx.Mat(C(0xB9, 0xC0, 0xC8)), "ac");

            if (billboard)
            {
                var bbM = Fx.MatTex(Fx.SprIcon("x2").texture, true);
                var bb = Part(root.transform, PrimitiveType.Quad, new Vector3(0, h + 1.4f, -d / 2f - 0.06f), new Vector3(Mathf.Min(w * 0.7f, 4f), 2.4f, 1), bbM, "bill");
                bb.transform.localRotation = Quaternion.Euler(0, 180, 0);
                Part(root.transform, PrimitiveType.Cube, new Vector3(0, h + 0.5f, -d / 2f + 0.1f), new Vector3(0.15f, 1.4f, 0.15f), Fx.Mat(C(0x8A, 0x93, 0xA6)), "pole");
            }
            return root;
        }

        // ================================================= SMALL PROPS
        public static GameObject Pole()
        {
            var root = new GameObject("pole");
            var m = Fx.Mat(C(0x6E, 0x76, 0x86));
            Part(root.transform, PrimitiveType.Cylinder, new Vector3(0, 3.5f, 0), new Vector3(0.14f, 3.4f, 0.14f), m, "post", true);
            Part(root.transform, PrimitiveType.Cube, new Vector3(0.55f, 6.6f, 0), new Vector3(1.4f, 0.09f, 0.09f), m, "arm");
            Part(root.transform, PrimitiveType.Sphere, new Vector3(1.1f, 6.5f, 0), new Vector3(0.16f, 0.16f, 0.16f), Fx.MatGlow(C(0xFF, 0xF3, 0xB0)), "lamp");
            return root;
        }

        public static GameObject Bridge()
        {
            var root = new GameObject("bridge");
            var m = Fx.Mat(C(0x8E, 0x97, 0xA8));
            Part(root.transform, PrimitiveType.Cube, new Vector3(-5.6f, 3.1f, 0), new Vector3(0.9f, 6.2f, 1.0f), m, "pL", true);
            Part(root.transform, PrimitiveType.Cube, new Vector3(5.6f, 3.1f, 0), new Vector3(0.9f, 6.2f, 1.0f), m, "pR", true);
            Part(root.transform, PrimitiveType.Cube, new Vector3(0, 6.6f, 0), new Vector3(12.6f, 1.2f, 3.2f), m, "deck");
            var grafM = Fx.MatTex(Fx.TexGraffiti(C(0x9A, 0xA3, 0xB2), 5), false);
            var g = Part(root.transform, PrimitiveType.Quad, new Vector3(0, 5.7f, -1.62f), new Vector3(11.4f, 1.9f, 1), grafM, "graf");
            Part(root.transform, PrimitiveType.Cube, new Vector3(0, 7.3f, 0), new Vector3(12.9f, 0.25f, 3.5f), Fx.Mat(C(0x4A, 0x55, 0x68)), "cap");
            return root;
        }

        public static GameObject Cloud()
        {
            var root = new GameObject("cloud");
            var m = Fx.Mat(C(0xFF, 0xFF, 0xFF));
            var s1 = Part(root.transform, PrimitiveType.Sphere, new Vector3(-1.6f, 0, 0), new Vector3(3.2f, 1.5f, 2.2f), m, "a");
            var s2 = Part(root.transform, PrimitiveType.Sphere, new Vector3(0.6f, 0.4f, 0.3f), new Vector3(4.4f, 1.9f, 2.6f), m, "b");
            var s3 = Part(root.transform, PrimitiveType.Sphere, new Vector3(2.8f, 0, -0.2f), new Vector3(2.6f, 1.3f, 2.0f), m, "c");
            s1.GetComponent<MeshRenderer>().shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.Off;
            s2.GetComponent<MeshRenderer>().shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.Off;
            s3.GetComponent<MeshRenderer>().shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.Off;
            return root;
        }

        // ================================================= TRACK CHUNK
        public const float TrackW = 8.4f;

        public static GameObject TrackChunk(float len)
        {
            var root = new GameObject("chunk");

            // ballast bed — texture IS the rails + sleepers (1:1 map, no repeat-x)
            var trackM = TexMat(Fx.TexTrack(), 1f, len / 3.2f, false);
            var bed = Part(root.transform, PrimitiveType.Cube, new Vector3(0, -0.05f, 0), new Vector3(TrackW, 0.1f, len), trackM, "bed", true);
            bed.layer = Fx.LGround;

            // raised concrete curbs at bed edges
            var curbM = Fx.Mat(C(0xCF, 0xD4, 0xDA));
            Part(root.transform, PrimitiveType.Cube, new Vector3(-TrackW / 2f + 0.16f, 0.09f, 0), new Vector3(0.32f, 0.38f, len), curbM, "curbL");
            Part(root.transform, PrimitiveType.Cube, new Vector3(TrackW / 2f - 0.16f, 0.09f, 0), new Vector3(0.32f, 0.38f, len), curbM, "curbR");

            // graffiti walls (close, SS-tunnel feel) — texture repeats every 4m
            var wallM = Fx.Mat(C(0xC3, 0xC9, 0xD4));
            var grafM = TexMat(Fx.TexGraffiti(C(0xC3, 0xC9, 0xD4), 9), len / 4f, 1f, false);
            for (int s = 0; s < 2; s++)
            {
                float sx = s == 0 ? -1f : 1f;
                var wall = Part(root.transform, PrimitiveType.Cube, new Vector3(sx * 5.15f, 1.3f, 0), new Vector3(0.4f, 2.6f, len), wallM, "wall" + s);
                var q = Part(root.transform, PrimitiveType.Quad, new Vector3(sx * 4.93f, 1.25f, 0), new Vector3(len, 2.2f, 1), grafM, "graf" + s);
                q.transform.localRotation = Quaternion.Euler(0, sx > 0 ? -90f : 90f, 0);
                // wall cap
                Part(root.transform, PrimitiveType.Cube, new Vector3(sx * 5.15f, 2.68f, 0), new Vector3(0.56f, 0.16f, len), Fx.Mat(C(0x8E, 0x97, 0xA6)), "cap" + s);
            }

            // grass + walkway beyond walls
            var grassM = Fx.Mat(C(0x7E, 0xC8, 0x50));
            var grassM2 = Fx.Mat(C(0x6F, 0xBA, 0x46));
            Part(root.transform, PrimitiveType.Cube, new Vector3(-7.6f, -0.09f, 0), new Vector3(4.2f, 0.1f, len), grassM, "grassL");
            Part(root.transform, PrimitiveType.Cube, new Vector3(7.6f, -0.09f, 0), new Vector3(4.2f, 0.1f, len), grassM2, "grassR");
            var walkM = Fx.Mat(C(0xC9, 0xCE, 0xD6));
            Part(root.transform, PrimitiveType.Cube, new Vector3(-10.4f, -0.05f, 0), new Vector3(2.4f, 0.12f, len), walkM, "walkL");
            Part(root.transform, PrimitiveType.Cube, new Vector3(10.4f, -0.05f, 0), new Vector3(2.4f, 0.12f, len), walkM, "walkR");

            return root;
        }

        public static GameObject GraffitiPanel(float w, float h, Color baseCol, int seed)
        {
            var root = new GameObject("graf");
            var m = Fx.MatTex(Fx.TexGraffiti(baseCol, seed), false);
            Part(root.transform, PrimitiveType.Quad, Vector3.zero, new Vector3(w, h, 1), m, "q");
            return root;
        }
    }
}
