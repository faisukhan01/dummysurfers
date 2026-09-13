using UnityEngine;
using DummySurfer.Data;
using DummySurfer.Multiplayer;
using DummySurfer.Powerups;
using DummySurfer.Utilities;

namespace DummySurfer.Player
{
    /// <summary>
    /// Builds the original runners entirely from Unity primitives at runtime or in-editor
    /// (used by the setup wizard to save the NetworkRunner prefab). Distinct silhouettes,
    /// streetwear colorways, zero external art — fully original (IP boundary honored).
    /// </summary>
    public static class RunnerFactory
    {
        public static GameObject BuildOfflineRunner(CharacterStats stats)
        {
            var root = BuildBase("Runner_Offline", stats);
            return root;
        }

        public static GameObject BuildNetworkRunner(CharacterStats stats)
        {
            var root = BuildBase("Runner_Networked", stats);
            root.AddComponent<Unity.Netcode.NetworkObject>();
            root.AddComponent<NetworkPlayerSync>();
            return root;
        }

        private static GameObject BuildBase(string name, CharacterStats stats)
        {
            var root = new GameObject(name);

            var rb = root.AddComponent<Rigidbody>();
            rb.isKinematic = true;
            rb.useGravity = false;

            var col = root.AddComponent<CapsuleCollider>();
            col.radius = 0.32f;
            col.height = 1.7f;
            col.center = new Vector3(0f, 0.85f, 0f);
            col.isTrigger = false;

            root.AddComponent<PlayerController>();
            root.AddComponent<PowerupController>();
            var rig = root.AddComponent<RunnerVisualRig>();

            BuildVisual(root.transform, stats, rig);
            return root;
        }

        private static void BuildVisual(Transform root, CharacterStats stats, RunnerVisualRig rig)
        {
            var visual = new GameObject("Visual").transform;
            visual.SetParent(root, false);

            var hips = new GameObject("Hips").transform;
            hips.SetParent(visual, false);
            hips.localPosition = new Vector3(0f, 0.82f, 0f);

            var torso = MakePart("Torso", PrimitiveType.Capsule, hips, new Vector3(0f, 0.36f, 0f),
                new Vector3(0.34f, 0.36f, 0.30f), stats.primary);
            var head = MakePart("Head", PrimitiveType.Sphere, hips, new Vector3(0f, 0.92f, 0f),
                new Vector3(0.26f, 0.26f, 0.26f), stats.skin);

            // Cap (child of head so rig colors it): dome + brim
            var cap = MakePart("Cap", PrimitiveType.Cylinder, head, new Vector3(0f, 0.10f, 0f),
                new Vector3(0.30f, 0.05f, 0.30f), stats.secondary);
            MakePart("Brim", PrimitiveType.Cube, cap.transform, new Vector3(0f, -0.02f, 0.17f),
                new Vector3(0.26f, 0.03f, 0.18f), stats.secondary);

            var armL = MakePivot("ArmL", hips, new Vector3(-0.27f, 0.62f, 0f),
                PrimitiveType.Capsule, new Vector3(0.20f, 0.26f, 0.20f), new Vector3(0f, -0.30f, 0f), stats.primary);
            var armR = MakePivot("ArmR", hips, new Vector3(0.27f, 0.62f, 0f),
                PrimitiveType.Capsule, new Vector3(0.20f, 0.26f, 0.20f), new Vector3(0f, -0.30f, 0f), stats.primary);

            var legL = MakePivot("LegL", hips, new Vector3(-0.11f, -0.04f, 0f),
                PrimitiveType.Capsule, new Vector3(0.24f, 0.30f, 0.24f), new Vector3(0f, -0.38f, 0f), stats.pants);
            var legR = MakePivot("LegR", hips, new Vector3(0.11f, -0.04f, 0f),
                PrimitiveType.Capsule, new Vector3(0.24f, 0.30f, 0.24f), new Vector3(0f, -0.38f, 0f), stats.pants);

            // Backpack gives a strong back silhouette
            MakePart("Backpack", PrimitiveType.Cube, visual, new Vector3(0f, 1.02f, -0.22f),
                new Vector3(0.42f, 0.52f, 0.22f), stats.secondary);

            rig.Bind(visual, torso.transform, head.transform, armL, armR, legL, legR);
            rig.ApplyColors(stats);
        }

        private static Transform MakePivot(string name, Transform parent, Vector3 pos,
            PrimitiveType limbType, Vector3 limbScale, Vector3 limbOffset, Color color)
        {
            var pivot = new GameObject(name).transform;
            pivot.SetParent(parent, false);
            pivot.localPosition = pos;
            MakePart(name + "_Limb", limbType, pivot, limbOffset, limbScale, color);
            return pivot;
        }

        private static GameObject MakePart(string name, PrimitiveType type, Transform parent,
            Vector3 localPos, Vector3 scale, Color color)
        {
            var go = GameObject.CreatePrimitive(type);
            go.name = name;
            RemoveCollider(go);
            go.transform.SetParent(parent, false);
            go.transform.localPosition = localPos;
            go.transform.localScale = scale;
            VisualStyles.AddMesh(go, color);
            return go;
        }

        private static void RemoveCollider(GameObject go)
        {
            var c = go.GetComponent<Collider>();
            if (c == null) return;
            if (Application.isPlaying) Destroy(c);
            else DestroyImmediate(c);
        }
    }
}
