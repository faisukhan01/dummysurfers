using System.Collections.Generic;
using System.IO;
using UnityEngine;

namespace DummySurfer
{
    /// <summary>
    /// Loads the procedurally generated CARTOON hero (HumanMesh.bytes, v8 "Jake-style") and
    /// builds it in-engine. The file contains two meshes: a SKINNED body (hoodie torso + arms
    /// + jeans legs + big sneakers + backpack, one continuous surface with blended joint
    /// weights) and a STATIC head (oversized cartoon skull, cap, hair, big expressive face).
    /// Material zones are baked as per-vertex material ids; lighting matches AnimeMesh (baked
    /// NdotL in UV.x sampled through the cel ramp by the URP Unlit shader). Bone order and rest
    /// positions mirror the python generator exactly.
    /// </summary>
    public static class HumanRig
    {
        static TextAsset _asset;
        static TextAsset Asset()
        {
            if (_asset == null) _asset = Resources.Load<TextAsset>("HumanMesh");
            return _asset;
        }

        // palette MUST match gen_cartoon.py MAT_COLORS (v8 cartoon hero)
        static readonly Color32[] Pal =
        {
            new Color32(0xF5,0xC2,0x9E,255), // 0 skin
            new Color32(0x4A,0x2C,0x17,255), // 1 hair
            new Color32(0xF6,0xF2,0xE7,255), // 2 hoodie (cream)
            new Color32(0x4E,0x6E,0x9E,255), // 3 jeans (denim)
            new Color32(0xFB,0xFB,0xF6,255), // 4 shoe (white)
            new Color32(0xE9,0xE4,0xD8,255), // 5 sole
            new Color32(0xEE,0x8A,0x3C,255), // 6 backpack (orange)
            new Color32(0x33,0x31,0x3E,255), // 7 straps (dark)
            new Color32(0x2A,0x21,0x1B,255), // 8 eye dark
            new Color32(0xA8,0x58,0x4E,255), // 9 mouth
            new Color32(0xD9,0x3B,0x2F,255), // 10 cap red
            new Color32(0xA9,0x2A,0x22,255), // 11 cap dark (brim)
            new Color32(0xF8,0xF4,0xEA,255), // 12 badge white
            new Color32(0xFE,0xFE,0xFA,255), // 13 teeth / eye white
            new Color32(0x8F,0xAF,0xD9,255), // 14 rolled cuff (light denim)
            new Color32(0xE2,0xA6,0x7F,255), // 15 skin shade
        };

        // rest world positions, order = bone index in the file (mirrors gen_cartoon.py REST)
        public static readonly Vector3[] RestPos =
        {
            new Vector3(0f, 0f, 0f),            // 0 body
            new Vector3(0f, 0.840f, 0f),        // 1 torso (hips)
            new Vector3(-0.252f, 1.235f, 0f),   // 2 armL
            new Vector3(-0.258f, 0.925f, 0f),   // 3 elbL
            new Vector3(-0.262f, 0.665f, 0f),   // 4 handL
            new Vector3(0.252f, 1.235f, 0f),    // 5 armR
            new Vector3(0.258f, 0.925f, 0f),    // 6 elbR
            new Vector3(0.262f, 0.665f, 0f),    // 7 handR
            new Vector3(-0.118f, 0.780f, 0f),   // 8 legL
            new Vector3(-0.118f, 0.415f, 0f),   // 9 kneeL
            new Vector3(-0.118f, 0.075f, 0f),   // 10 footL
            new Vector3(0.118f, 0.780f, 0f),    // 11 legR
            new Vector3(0.118f, 0.415f, 0f),    // 12 kneeR
            new Vector3(0.118f, 0.075f, 0f),    // 13 footR
        };

        class RawMesh
        {
            public int nv, nt;
            public Vector3[] pos, nrm;
            public Vector2[] uv;
            public int[] bidx;
            public byte[] bwt;
            public byte[] mat;
            public int[] tris;
        }

        static RawMesh ReadMesh(BinaryReader r)
        {
            var m = new RawMesh();
            m.nv = r.ReadInt32();
            m.pos = new Vector3[m.nv]; m.nrm = new Vector3[m.nv]; m.uv = new Vector2[m.nv];
            m.bidx = new int[m.nv * 4]; m.bwt = new byte[m.nv * 4]; m.mat = new byte[m.nv];
            for (int i = 0; i < m.nv; i++)
            {
                m.pos[i] = new Vector3(r.ReadSingle(), r.ReadSingle(), r.ReadSingle());
                m.nrm[i] = new Vector3(r.ReadSingle(), r.ReadSingle(), r.ReadSingle());
                m.uv[i] = new Vector2(r.ReadSingle(), r.ReadSingle());
                for (int k = 0; k < 4; k++) m.bidx[i * 4 + k] = r.ReadByte();
                for (int k = 0; k < 4; k++) m.bwt[i * 4 + k] = r.ReadByte();
                m.mat[i] = r.ReadByte();
            }
            m.nt = r.ReadInt32();
            m.tris = new int[m.nt * 3];
            for (int i = 0; i < m.nt * 3; i++) m.tris[i] = r.ReadInt32();
            return m;
        }

        /// <summary>Groups triangles per material zone (first-appearance order) and builds
        /// private Cull-Off instances of the cel-shaded materials (winding-proof, v5.2-proven).</summary>
        static Material[] ZoneMaterials(RawMesh m, List<int> zone, Dictionary<int, List<int>> trisByZone)
        {
            var mats = new Material[zone.Count];
            for (int i = 0; i < zone.Count; i++)
            {
                var baseMat = AnimeMesh.Shade(Pal[zone[i]]);
                var mi = new Material(baseMat);
                mi.name = baseMat.name + "_nc";
                try { mi.SetFloat("_Cull", 0f); } catch { }
                try { mi.SetInt("_Cull", 0); } catch { }
                try { mi.SetFloat("_CullMode", 0f); } catch { }
                mats[i] = mi;
            }
            return mats;
        }

        static void SplitZones(RawMesh m, List<int> zone, Dictionary<int, List<int>> trisByZone)
        {
            for (int t = 0; t < m.nt; t++)
            {
                int z = m.mat[m.tris[t * 3]];
                List<int> l;
                if (!trisByZone.TryGetValue(z, out l)) { l = new List<int>(64); trisByZone[z] = l; zone.Add(z); }
                l.Add(t * 3);
            }
        }

        static Mesh BuildMeshGeometry(RawMesh m, Vector3 offset, List<int> zone, Dictionary<int, List<int>> trisByZone,
                                      UnityEngine.BoneWeight[] weights, Matrix4x4[] bindposes)
        {
            var verts = new Vector3[m.nv];
            for (int i = 0; i < m.nv; i++) verts[i] = m.pos[i] + offset;
            var mesh = new Mesh();
            mesh.name = "human_mesh";
            mesh.SetVertices(new List<Vector3>(verts));
            mesh.SetNormals(new List<Vector3>(m.nrm));
            mesh.SetUVs(0, new List<Vector2>(m.uv));
            mesh.subMeshCount = zone.Count;
            for (int i = 0; i < zone.Count; i++)
            {
                var l = trisByZone[zone[i]];
                var tri = new int[l.Count * 3];
                for (int k = 0; k < l.Count; k++)
                {
                    tri[k * 3] = m.tris[l[k]];
                    tri[k * 3 + 1] = m.tris[l[k] + 1];
                    tri[k * 3 + 2] = m.tris[l[k] + 2];
                }
                mesh.SetTriangles(tri, i, false);
            }
            if (weights != null) mesh.boneWeights = weights;
            if (bindposes != null) mesh.bindposes = bindposes;
            mesh.RecalculateBounds();
            return mesh;
        }

        /// <summary>Builds the skinned body under bones[0] and the static head under headParent.
        /// bones order MUST be: body, torso, armL, elbL, handL, armR, elbR, handR,
        /// legL, kneeL, footL, legR, kneeR, footR.</summary>
        public static void Build(Transform[] bones, Transform headParent)
        {
            var data = Asset();
            if (data == null || bones == null || bones.Length != RestPos.Length) return;

            using (var s = new MemoryStream(data.bytes))
            using (var r = new BinaryReader(s))
            {
                r.ReadBytes(4); // magic "HM01"

                // ---------------- skinned body ----------------
                var body = ReadMesh(r);

                var zone = new List<int>();
                var trisByZone = new Dictionary<int, List<int>>();
                SplitZones(body, zone, trisByZone);

                var weights = new UnityEngine.BoneWeight[body.nv];
                for (int i = 0; i < body.nv; i++)
                {
                    var bw = new UnityEngine.BoneWeight();
                    float w0 = body.bwt[i * 4] / 255f, w1 = body.bwt[i * 4 + 1] / 255f;
                    float w2 = body.bwt[i * 4 + 2] / 255f, w3 = body.bwt[i * 4 + 3] / 255f;
                    bw.boneIndex0 = body.bidx[i * 4]; bw.weight0 = w0;
                    bw.boneIndex1 = body.bidx[i * 4 + 1]; bw.weight1 = w1;
                    bw.boneIndex2 = body.bidx[i * 4 + 2]; bw.weight2 = w2;
                    bw.boneIndex3 = body.bidx[i * 4 + 3]; bw.weight3 = w3;
                    weights[i] = bw;
                }

                var bindposes = new Matrix4x4[RestPos.Length];
                for (int i = 0; i < RestPos.Length; i++)
                    bindposes[i] = Matrix4x4.Translate(-RestPos[i]);

                var bodyMats = ZoneMaterials(body, zone, trisByZone);
                var bodyMesh = BuildMeshGeometry(body, Vector3.zero, zone, trisByZone, weights, bindposes);

                var go = new GameObject("HumanBody");
                go.transform.SetParent(bones[0], false);
                var smr = go.AddComponent<SkinnedMeshRenderer>();
                smr.sharedMesh = bodyMesh;
                smr.bones = bones;
                smr.rootBone = bones[0];
                smr.sharedMaterials = bodyMats;
                smr.updateWhenOffscreen = true;
                smr.shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.Off;
                smr.receiveShadows = false;

                // ---------------- static head ----------------
                var head = ReadMesh(r);
                var hzone = new List<int>();
                var htris = new Dictionary<int, List<int>>();
                SplitZones(head, hzone, htris);
                var headMats = ZoneMaterials(head, hzone, htris);
                // head mesh is in body-space; the head pivot rests at (0, 1.30, 0) — rebase into pivot-local space
                var headOffset = new Vector3(0f, -1.30f, 0f);
                var headMesh = BuildMeshGeometry(head, headOffset, hzone, htris, null, null);

                var hgo = new GameObject("HumanHead");
                hgo.transform.SetParent(headParent, false);
                var mf = hgo.AddComponent<MeshFilter>();
                mf.sharedMesh = headMesh;
                var mr = hgo.AddComponent<MeshRenderer>();
                mr.sharedMaterials = headMats;
                mr.shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.Off;
                mr.receiveShadows = false;
            }
        }
    }
}
