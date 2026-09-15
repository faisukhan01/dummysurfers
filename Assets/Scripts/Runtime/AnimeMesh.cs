using System.Collections.Generic;
using UnityEngine;

namespace DummySurfer
{
    /// <summary>
    /// Procedural anime-character mesh toolkit (replaces stacked primitives).
    /// Surfaces are smooth "lathe" revolve surfaces built from radius profiles, so a
    /// whole limb or torso is ONE continuous skin — no balloon gaps between parts.
    /// Lighting is BAKED into UV.x (per-vertex NdotL) and looked up in a cel ramp
    /// texture by the URP Unlit shader → deterministic hand-shaded TV-cartoon look,
    /// zero dependency on scene lights (same philosophy as the rest of the game art).
    /// Every build also emits an inverted-hull OUTLINE submesh → real anime ink lines.
    /// </summary>
    public static class AnimeMesh
    {
        // ---------------------------------------------------------- shading
        static Texture2D _ramp;
        public static Texture2D Ramp()
        {
            if (_ramp != null) return _ramp;
            var t = new Texture2D(128, 4, TextureFormat.RGBA32, false);
            t.name = "AnimeRamp";
            t.filterMode = FilterMode.Bilinear;
            t.wrapMode = TextureWrapMode.Clamp;
            var px = new Color32[128 * 4];
            for (int x = 0; x < 128; x++)
            {
                float u = x / 127f;
                // soft cel: shadow plateau → smooth rolloff → light plateau
                float s = Mathf.SmoothStep(0.30f, 0.74f, u);
                // gentle core-shadow dip just before the terminator
                float core = 1f - 0.10f * Mathf.Clamp01(1f - Mathf.Abs(u - 0.26f) / 0.16f);
                float r = Mathf.Lerp(0.545f, 1.00f, s) * core;
                float g = Mathf.Lerp(0.565f, 0.985f, s) * core;
                float b = Mathf.Lerp(0.675f, 0.955f, s) * core;
                var c = new Color32((byte)(Mathf.Clamp01(r) * 255f), (byte)(Mathf.Clamp01(g) * 255f), (byte)(Mathf.Clamp01(b) * 255f), 255);
                for (int y = 0; y < 4; y++) px[y * 128 + x] = c;
            }
            t.SetPixels32(px);
            t.Apply(false, false);
            _ramp = t;
            return t;
        }

        static Material ShadeRaw(Color c)
        {
            Material m = Fx.UnlitUrpShader != null ? new Material(Fx.UnlitUrpShader) : new Material(Fx.TexShader);
            m.mainTexture = Ramp();
            AnimeUtil.TryColor(m, c);
            try { m.SetFloat("_Smoothness", 0f); } catch { }
            m.name = "anime_" + ColorUtility.ToHtmlStringRGBA(c);
            return m;
        }

        static readonly Dictionary<int, Material> ShadeCache = new Dictionary<int, Material>();

        /// <summary>Cached cel-shaded material for a flat color.</summary>
        public static Material Shade(Color c)
        {
            int key = ((int)(byte)(Mathf.Clamp01(c.r) * 255f))
                    | (((int)(byte)(Mathf.Clamp01(c.g) * 255f)) << 8)
                    | (((int)(byte)(Mathf.Clamp01(c.b) * 255f)) << 16);
            Material m;
            if (ShadeCache.TryGetValue(key, out m) && m != null) return m;
            m = ShadeRaw(c);
            ShadeCache[key] = m;
            return m;
        }

        static Material _ink;
        /// <summary>Warm dark ink for outline hulls.</summary>
        public static Material Ink()
        {
            if (_ink != null) return _ink;
            _ink = ShadeRaw(new Color32(0x2B, 0x1E, 0x16, 255));
            _ink.name = "anime_ink";
            return _ink;
        }

        // ---------------------------------------------------------- builder
        struct Vtx { public Vector3 p, n; public Vector2 uv; }

        /// <summary>Accumulates surface geometry into one mesh with one submesh per
        /// material plus a trailing OUTLINE submesh (inverted hull).</summary>
        public sealed class Build
        {
            readonly List<Vtx> v = new List<Vtx>(512);
            readonly List<List<int>> slots = new List<List<int>>();
            readonly List<Material> mats = new List<Material>();
            readonly List<Vtx> ov = new List<Vtx>(512);
            readonly List<int> ot = new List<int>();
            readonly float ink;

            static readonly Vector3 L = new Vector3(0.42f, 0.80f, 0.43f).normalized;

            public Build(float outlineWidth = 0.011f) { ink = outlineWidth; }

            public int Mat(Material m)
            {
                int i = mats.IndexOf(m);
                if (i >= 0) return i;
                mats.Add(m);
                slots.Add(new List<int>(256));
                return mats.Count - 1;
            }

            /// <summary>Revolve a radius profile (y, r) around the local Y axis.
            /// Angles in degrees, a1 may exceed a0 + 360 for wrapped shells.</summary>
            public void Rev(int mat, Vector3 pos, Quaternion rot, Vector3 scl,
                            Vector2[] prof, int seg = 20, float a0 = 0f, float a1 = 360f)
            {
                int rings = prof.Length;
                int b = v.Count;
                var tris = slots[mat];
                for (int i = 0; i < rings; i++)
                {
                    // profile tangent → outward 2D normal
                    int ip = Mathf.Max(0, i - 1), inx = Mathf.Min(rings - 1, i + 1);
                    float dy = prof[inx].x - prof[ip].x;
                    float dr = prof[inx].y - prof[ip].y;
                    float nl = Mathf.Sqrt(dr * dr + dy * dy);
                    if (nl < 1e-5f) { dr = 1f; dy = 0f; nl = 1f; }
                    float nr = -dy / nl, ny = dr / nl; // rot90ccw of (dr, dy)

                    for (int j = 0; j <= seg; j++)
                    {
                        float a = Mathf.Deg2Rad * Mathf.Lerp(a0, a1, j / (float)seg);
                        float ca = Mathf.Cos(a), sa = Mathf.Sin(a);
                        var pLocal = new Vector3(prof[i].y * ca * scl.x, prof[i].x * scl.y, prof[i].y * sa * scl.z);
                        var nrm = new Vector3(nr * ca / Mathf.Max(0.05f, scl.x), ny / Mathf.Max(0.05f, scl.y), nr * sa / Mathf.Max(0.05f, scl.z));
                        nrm = rot * nrm;
                        nrm.Normalize();
                        var w = rot * pLocal + pos;
                        float ndl = Mathf.Clamp01(Vector3.Dot(nrm, L) * 0.5f + 0.5f);
                        v.Add(new Vtx { p = w, n = nrm, uv = new Vector2(ndl, i / (float)(rings - 1)) });
                        ov.Add(new Vtx { p = w + nrm * ink, n = nrm, uv = new Vector2(0.5f, 0.5f) });
                    }
                }
                for (int i = 0; i < rings - 1; i++)
                {
                    for (int j = 0; j < seg; j++)
                    {
                        int a = b + i * (seg + 1) + j;
                        int bb = a + 1, c = a + seg + 1, d = c + 1;
                        // Unity shows the face whose vertices wind clockwise seen from
                        // outside; that flips when the profile runs downward (Balls and
                        // top-down limbs), so auto-detect the profile direction.
                        bool asc = prof[rings - 1].x >= prof[0].x;
                        if (asc)
                        {
                            tris.Add(a); tris.Add(bb); tris.Add(d);
                            tris.Add(a); tris.Add(d); tris.Add(c);
                            // mirror quad on the outline hull, winding flipped
                            int oa = a, ob = bb, oc = c, od = d;
                            ot.Add(od); ot.Add(ob); ot.Add(oa);
                            ot.Add(oc); ot.Add(od); ot.Add(oa);
                        }
                        else
                        {
                            tris.Add(a); tris.Add(d); tris.Add(bb);
                            tris.Add(a); tris.Add(c); tris.Add(d);
                            int oa = a, ob = bb, oc = c, od = d;
                            ot.Add(ob); ot.Add(od); ot.Add(oa);
                            ot.Add(od); ot.Add(oc); ot.Add(oa);
                        }
                    }
                }
            }

            /// <summary>Ellipsoid (optionally partial in longitude a0..a1 and height phiMax).</summary>
            public void Ball(int mat, Vector3 center, Vector3 radius, Quaternion rot,
                             int seg = 20, float a0 = 0f, float a1 = 360f, float phiMax = 180f)
            {
                const int K = 11;
                var prof = new Vector2[K];
                float pm = Mathf.Clamp(phiMax, 2f, 180f) * Mathf.Deg2Rad;
                for (int k = 0; k < K; k++)
                {
                    float phi = pm * k / (K - 1);
                    prof[k] = new Vector2(radius.y * Mathf.Cos(phi), Mathf.Max(0.004f, radius.x * Mathf.Sin(phi)));
                }
                var scl = new Vector3(1f, 1f, Mathf.Clamp(radius.z / Mathf.Max(0.001f, radius.x), 0.2f, 5f));
                Rev(mat, center, rot, scl, prof, seg, a0, a1);
            }

            /// <summary>Spiky cone (anime hair spike): base at pos, tip len along dir.</summary>
            public void Spike(int mat, Vector3 basePos, Vector3 dir, float len, float baseR, int seg = 10)
            {
                var d = dir.sqrMagnitude < 1e-6f ? Vector3.up : dir.normalized;
                var rot = Quaternion.FromToRotation(Vector3.up, d);
                var prof = new Vector2[5];
                for (int i = 0; i < 5; i++)
                {
                    float t = i / 4f;
                    float r = baseR * (1f - t) * (1f - 0.25f * t); // slight concave
                    prof[i] = new Vector2(len * t, Mathf.Max(0.0035f, r));
                }
                Rev(mat, basePos, rot, Vector3.one, prof, seg, 0f, 360f);
            }

            /// <summary>Materialize: mesh + renderer under a new GameObject.</summary>
            public GameObject Done(Transform parent, string name)
            {
                if (mats.Count == 0) Mat(Shade(Color.white));

                var all = new List<Vector3>(v.Count + ov.Count);
                var alln = new List<Vector3>(v.Count + ov.Count);
                var allu = new List<Vector2>(v.Count + ov.Count);
                foreach (var q in v) { all.Add(q.p); alln.Add(q.n); allu.Add(q.uv); }
                int obase = all.Count;
                foreach (var q in ov) { all.Add(q.p); alln.Add(q.n); allu.Add(q.uv); }

                var mesh = new Mesh();
                mesh.name = name + "_mesh";
                mesh.SetVertices(all);
                mesh.SetNormals(alln);
                mesh.SetUVs(0, allu);
                mesh.subMeshCount = slots.Count + 1;
                for (int i = 0; i < slots.Count; i++) mesh.SetTriangles(slots[i], i, false);
                var otris = new List<int>(ot.Count);
                foreach (var idx in ot) otris.Add(idx + obase);
                mesh.SetTriangles(otris, slots.Count, false);
                mesh.RecalculateBounds();

                var go = new GameObject(name);
                go.transform.SetParent(parent, false);
                var mf = go.AddComponent<MeshFilter>();
                mf.sharedMesh = mesh;
                var mr = go.AddComponent<MeshRenderer>();
                var marr = new Material[mats.Count + 1];
                for (int i = 0; i < mats.Count; i++) marr[i] = mats[i];
                marr[mats.Count] = Ink();
                mr.sharedMaterials = marr;
                mr.shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.Off;
                mr.receiveShadows = false;
                return go;
            }
        }
    }

    static class AnimeUtil
    {
        public static void TryColor(Material m, Color c)
        {
            try { if (m.HasProperty("_BaseColor")) m.SetColor("_BaseColor", c); } catch { }
            try { if (m.HasProperty("_Color")) m.SetColor("_Color", c); } catch { }
        }
    }
}
