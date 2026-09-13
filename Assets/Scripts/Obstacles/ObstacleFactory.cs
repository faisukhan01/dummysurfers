using UnityEngine;
using DummySurfer.Data;
using DummySurfer.Utilities;

using DummySurfer.Track;
namespace DummySurfer.Obstacles
{
    /// <summary>Original obstacle visuals built from primitives (spec 10 OBSTACLE VALIDATION).</summary>
    public static class ObstacleFactory
    {
        public static ObstacleBase Build(ObstacleKind kind)
        {
            return kind switch
            {
                ObstacleKind.Train => BuildTrain(),
                ObstacleKind.Barrier => BuildBarrier(),
                ObstacleKind.Overhead => BuildOverhead(),
                ObstacleKind.Sign => BuildSign(),
                _ => null
            };
        }

        // ---------- helpers ----------

        private static GameObject Part(Transform parent, string name, PrimitiveType type,
            Vector3 localPos, Vector3 scale, Color color, bool unlit = false)
        {
            var go = GameObject.CreatePrimitive(type);
            go.name = name;
            var col = go.GetComponent<Collider>();
            if (col != null) DestroyInMode(col);
            go.transform.SetParent(parent, false);
            go.transform.localPosition = localPos;
            go.transform.localScale = scale;
            VisualStyles.AddMesh(go, color, unlit);
            return go;
        }

        private static void DestroyInMode(Object o)
        {
            if (Application.isPlaying) Destroy(o);
            else DestroyImmediate(o);
        }

        private static BoxCollider AddTrigger(GameObject root, Vector3 center, Vector3 size)
        {
            var col = root.AddComponent<BoxCollider>();
            col.isTrigger = true;
            col.center = center;
            col.size = size;
            return col;
        }

        // ---------- trains ----------

        private static TrainObstacle BuildTrain()
        {
            var root = new GameObject("Train");
            var ob = root.AddComponent<TrainObstacle>();
            ob.Kind = ObstacleKind.Train;
            ob.Lethal = true;

            Part(root.transform, "Body", PrimitiveType.Cube, new Vector3(0f, 1.45f, 0f), Vector3.one, VisualStyles.TrainRed);
            Part(root.transform, "Roof", PrimitiveType.Cube, new Vector3(0f, 2.85f, 0f), new Vector3(1.7f, 0.22f, 1f), VisualStyles.TrainBlue);
            Part(root.transform, "Skirt", PrimitiveType.Cube, new Vector3(0f, 0.22f, 0f), new Vector3(1.8f, 0.44f, 1f), VisualStyles.Sleeper);
            Part(root.transform, "Windows", PrimitiveType.Cube, new Vector3(0f, 1.9f, 0f), new Vector3(1.94f, 0.5f, 1f), VisualStyles.SkylineFar, unlit: true);
            Part(root.transform, "Face", PrimitiveType.Cube, new Vector3(0f, 1.5f, 0.52f), new Vector3(1.94f, 2.2f, 0.08f), VisualStyles.TrainBlue);

            AddTrigger(root, new Vector3(0f, 1.5f, 0f), new Vector3(1.9f, 3f, 1f));
            return ob;
        }

        private static BarrierObstacle BuildBarrier()
        {
            var root = new GameObject("Barrier");
            var ob = root.AddComponent<BarrierObstacle>();
            ob.Kind = ObstacleKind.Barrier;
            ob.Lethal = true;

            Part(root.transform, "PostL", PrimitiveType.Cube, new Vector3(-0.95f, 0.5f, 0f), new Vector3(0.12f, 1.0f, 0.12f), VisualStyles.RailMetal);
            Part(root.transform, "PostR", PrimitiveType.Cube, new Vector3(0.95f, 0.5f, 0f), new Vector3(0.12f, 1.0f, 0.12f), VisualStyles.RailMetal);
            Part(root.transform, "Bar", PrimitiveType.Cube, new Vector3(0f, 0.62f, 0f), new Vector3(2.0f, 0.34f, 0.18f), VisualStyles.HazardYellow);
            for (int i = 0; i < 4; i++)
                Part(root.transform, $"Stripe{i}", PrimitiveType.Cube,
                    new Vector3(-0.75f + i * 0.5f, 0.62f, -0.095f), new Vector3(0.22f, 0.36f, 0.01f), VisualStyles.SignWhite, unlit: true);

            AddTrigger(root, new Vector3(0f, 0.55f, 0f), new Vector3(2.0f, 1.05f, 0.5f));
            return ob;
        }

        private static OverheadHazard BuildOverhead()
        {
            var root = new GameObject("Overhead");
            var ob = root.AddComponent<OverheadHazard>();
            ob.Kind = ObstacleKind.Overhead;
            ob.Lethal = true;

            Part(root.transform, "PostL", PrimitiveType.Cube, new Vector3(-1.05f, 1.4f, 0f), new Vector3(0.15f, 2.8f, 0.15f), VisualStyles.RailMetal);
            Part(root.transform, "PostR", PrimitiveType.Cube, new Vector3(1.05f, 1.4f, 0f), new Vector3(0.15f, 2.8f, 0.15f), VisualStyles.RailMetal);
            Part(root.transform, "Panel", PrimitiveType.Cube, new Vector3(0f, 2.15f, 0f), new Vector3(2.1f, 1.3f, 0.55f), VisualStyles.HazardYellow);
            Part(root.transform, "Chevron", PrimitiveType.Cube, new Vector3(0f, 1.62f, 0.29f), new Vector3(1.9f, 0.16f, 0.01f), VisualStyles.SignWhite, unlit: true);

            // Gap below y=1.15 → slide clears it
            AddTrigger(root, new Vector3(0f, 1.85f, 0f), new Vector3(2.1f, 1.4f, 0.6f));
            return ob;
        }

        private static StumbleSign BuildSign()
        {
            var root = new GameObject("Sign");
            var ob = root.AddComponent<StumbleSign>();
            ob.Kind = ObstacleKind.Sign;
            ob.Lethal = false;

            Part(root.transform, "Post", PrimitiveType.Cube, new Vector3(0f, 0.5f, 0f), new Vector3(0.09f, 1.0f, 0.09f), VisualStyles.RailMetal);
            Part(root.transform, "Board", PrimitiveType.Cube, new Vector3(0f, 1.15f, 0f), new Vector3(0.95f, 0.65f, 0.07f), VisualStyles.SignWhite);
            Part(root.transform, "Band", PrimitiveType.Cube, new Vector3(0f, 1.15f, -0.045f), new Vector3(0.8f, 0.16f, 0.01f), VisualStyles.Danger, unlit: true);

            AddTrigger(root, new Vector3(0f, 0.7f, 0f), new Vector3(0.95f, 1.35f, 0.4f));
            return ob;
        }

        // ---------- decorative far-rail train (local only, no collider) ----------

        public static DecorTrain BuildDecorTrain(GameConfig cfg)
        {
            var root = new GameObject("DecorTrain");
            var dt = root.AddComponent<DecorTrain>();
            Color body = Random.value < 0.5f ? VisualStyles.TrainRed : VisualStyles.TrainBlue;
            float len = 16f;
            Part(root.transform, "Body", PrimitiveType.Cube, new Vector3(0f, 0f, 0f), new Vector3(1.9f, 2.6f, len), body);
            Part(root.transform, "Roof", PrimitiveType.Cube, new Vector3(0f, 1.4f, 0f), new Vector3(1.7f, 0.2f, len * 0.96f), VisualStyles.SkylineFar);
            Part(root.transform, "Windows", PrimitiveType.Cube, new Vector3(0f, 0.45f, 0f), new Vector3(1.94f, 0.5f, len * 0.9f), VisualStyles.SkylineFar, unlit: true);
            return dt;
        }
    }
}
