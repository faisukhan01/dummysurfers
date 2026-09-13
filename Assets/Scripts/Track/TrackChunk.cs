using System.Collections.Generic;
using UnityEngine;
using DummySurfer.Collectibles;
using DummySurfer.Core;
using DummySurfer.Data;
using DummySurfer.Obstacles;
using DummySurfer.Utilities;

namespace DummySurfer.Track
{
    /// <summary>
    /// One reusable track segment (spec 6.1): floor, rails, platforms, gantries, lamps and a
    /// deterministic skyline. Base geometry is built once per pooled instance; per-index decor
    /// and gameplay content are (re)populated from the shared seed, then fully cleared on recycle.
    /// </summary>
    public sealed class TrackChunk : MonoBehaviour
    {
        public int ChunkIndex { get; private set; } = -1;
        public float Length { get; internal set; }

        private ObstacleManager _om;
        private CollectibleManager _cm;
        private readonly List<ObstacleBase> _obstacles = new List<ObstacleBase>(24);
        private readonly List<Coin> _coins = new List<Coin>(48);
        private readonly List<PowerupPickup> _powerups = new List<PowerupPickup>(2);
        private Transform[] _buildingsLeft, _buildingsRight;
        private const int BuildingsPerSide = 14;

        // ---------------- construction ----------------

        public static TrackChunk Build(Transform parent, GameConfig cfg)
        {
            var go = new GameObject("TrackChunk");
            go.transform.SetParent(parent, false);
            var chunk = go.AddComponent<TrackChunk>();
            chunk.Length = cfg.chunkLength;

            float len = cfg.chunkLength;

            // Floor
            Box(go.transform, "Floor", new Vector3(0f, -0.2f, len * 0.5f), new Vector3(7.8f, 0.4f, len), VisualStyles.Asphalt);

            // Sleepers (single wide strip every 3.6 m)
            for (float z = 1.8f; z < len; z += 3.6f)
                Box(go.transform, "Sleeper", new Vector3(0f, 0.015f, z), new Vector3(6.6f, 0.07f, 0.5f), VisualStyles.Sleeper);

            // Rails per lane
            for (int lane = 0; lane < Constants.LaneCount; lane++)
            {
                float x = Constants.LaneX(lane, cfg.laneWidth);
                Box(go.transform, $"RailL{lane}", new Vector3(x - 0.45f, 0.09f, len * 0.5f), new Vector3(0.09f, 0.12f, len), VisualStyles.RailMetal);
                Box(go.transform, $"RailR{lane}", new Vector3(x + 0.45f, 0.09f, len * 0.5f), new Vector3(0.09f, 0.12f, len), VisualStyles.RailMetal);
            }

            // Side platforms + painted edge
            for (int side = -1; side <= 1; side += 2)
            {
                float x = side * 5.1f;
                Box(go.transform, "Platform", new Vector3(x, 0.25f, len * 0.5f), new Vector3(2.6f, 0.5f, len), VisualStyles.Concrete);
                Box(go.transform, "EdgeStripe", new Vector3(x - side * 1.2f, 0.51f, len * 0.5f), new Vector3(0.18f, 0.03f, len), VisualStyles.HazardYellow, unlit: true);

                // Pillars + crossbeams + cables
                for (float z = 7.5f; z < len; z += 15f)
                {
                    Box(go.transform, "Pillar", new Vector3(x, 2.4f, z), new Vector3(0.5f, 4.8f, 0.5f), VisualStyles.Concrete);
                    Box(go.transform, "Beam", new Vector3(side * 2.6f, 4.7f, z), new Vector3(5.4f, 0.32f, 0.4f), VisualStyles.Concrete);
                    Box(go.transform, "LampPole", new Vector3(x, 1.6f, z + 3f), new Vector3(0.1f, 1.2f, 0.1f), VisualStyles.RailMetal);
                    Box(go.transform, "LampHead", new Vector3(x - side * 0.35f, 2.2f, z + 3f), new Vector3(0.5f, 0.14f, 0.24f), new Color(1f, 0.92f, 0.65f), unlit: true);
                }
                Box(go.transform, "Cable", new Vector3(x - side * 1.6f, 4.35f, len * 0.5f), new Vector3(0.04f, 0.04f, len), VisualStyles.Sleeper);
            }

            // Skyline building pools (re-randomized per index)
            chunk._buildingsLeft = new Transform[BuildingsPerSide];
            chunk._buildingsRight = new Transform[BuildingsPerSide];
            for (int i = 0; i < BuildingsPerSide; i++)
            {
                chunk._buildingsLeft[i] = Box(go.transform, $"BL{i}", Vector3.zero, Vector3.zero, VisualStyles.Skyline).transform;
                chunk._buildingsRight[i] = Box(go.transform, $"BR{i}", Vector3.zero, Vector3.zero, VisualStyles.SkylineFar).transform;
            }

            return chunk;
        }

        private static GameObject Box(Transform parent, string name, Vector3 pos, Vector3 scale, Color color, bool unlit = false)
        {
            var go = GameObject.CreatePrimitive(PrimitiveType.Cube);
            go.name = name;
            Object.Destroy(go.GetComponent<Collider>());
            go.transform.SetParent(parent, false);
            go.transform.localPosition = pos;
            go.transform.localScale = scale;
            VisualStyles.AddMesh(go, color, unlit);
            return go;
        }

        // ---------------- lifecycle ----------------

        public void Populate(int index, ulong sharedSeed, ObstacleManager om, CollectibleManager cm)
        {
            ChunkIndex = index;
            _om = om;
            _cm = cm;
            transform.position = Vector3.forward * (index * Length);

            RandomizeSkyline(sharedSeed, index);

            var pattern = PatternGenerator.Generate(sharedSeed, index);
            var cfg = GameConfig.Runtime;

            // Obstacles
            for (int r = 0; r < pattern.Rows; r++)
            {
                for (int lane = 0; lane < Constants.LaneCount; lane++)
                {
                    var kind = pattern.Get(r, lane);
                    if (kind == ObstacleKind.None) continue;

                    // Trains span consecutive rows; spawn only on their first row.
                    if (kind == ObstacleKind.Train && r > 0 && pattern.Get(r - 1, lane) == ObstacleKind.Train)
                        continue;

                    int spanRows = 1;
                    if (kind == ObstacleKind.Train)
                        while (r + spanRows < pattern.Rows && pattern.Get(r + spanRows, lane) == ObstacleKind.Train)
                            spanRows++;

                    float zLocal = (r + 1.5f) * pattern.RowSpacing;
                    float length = spanRows * pattern.RowSpacing * (kind == ObstacleKind.Train ? 1f : 0.28f);
                    var ob = _om.Spawn(kind, transform, new Vector3(Constants.LaneX(lane, cfg.laneWidth), 0f, zLocal), length);
                    if (ob != null) _obstacles.Add(ob);
                }
            }

            // Coins
            foreach (var run in pattern.Coins)
            {
                for (int i = 0; i < run.Count; i++)
                {
                    float t = run.Count <= 1 ? 0.5f : i / (float)(run.Count - 1);
                    float y = run.Arc ? 1.0f + Mathf.Sin(t * Mathf.PI) * 1.5f : 1.0f;
                    float zLocal = (run.StartRow + 0.5f + i) * pattern.RowSpacing;
                    if (zLocal > Length - 1f) break;
                    var coin = _cm.SpawnCoin(transform,
                        new Vector3(Constants.LaneX(run.Lane, cfg.laneWidth), y, zLocal));
                    if (coin != null) _coins.Add(coin);
                }
            }

            // Power-up
            if (pattern.HasPowerup)
            {
                float zLocal = (pattern.PowerupRow + 1.5f) * pattern.RowSpacing;
                var pu = _cm.SpawnPowerup(pattern.Powerup, transform,
                    new Vector3(Constants.LaneX(pattern.PowerupLane, cfg.laneWidth), 1.1f, zLocal));
                if (pu != null) _powerups.Add(pu);
            }
        }

        private void RandomizeSkyline(ulong sharedSeed, int index)
        {
            var rng = new SeededRandom(SeededRandom.ChunkSeed(sharedSeed, index) ^ 0xDEC0A5171C17UL);
            RandomizeSide(_buildingsLeft, 1f, rng);
            RandomizeSide(_buildingsRight, -1f, rng);
        }

        private static void RandomizeSide(Transform[] buildings, float sideSign, SeededRandom rng)
        {
            float z = 2f;
            for (int i = 0; i < buildings.Length; i++)
            {
                bool visible = rng.Chance(0.82f) && z < 58f;
                if (!visible)
                {
                    buildings[i].localScale = Vector3.zero;
                    continue;
                }
                float w = rng.Range(2.5f, 6.5f);
                float h = rng.Range(5f, 26f);
                float d = rng.Range(2.5f, 5f);
                float x = sideSign * rng.Range(10.5f, 24f);
                buildings[i].localPosition = new Vector3(x, h * 0.5f, z + d * 0.5f);
                buildings[i].localScale = new Vector3(w, h, d);
                z += d + rng.Range(1.5f, 5f);
            }
        }

        /// <summary>Returns every spawned object to its pool; chunk returns clean (spec 6.3).</summary>
        public void Clear()
        {
            for (int i = 0; i < _obstacles.Count; i++) _om.Return(_obstacles[i]);
            for (int i = 0; i < _coins.Count; i++) _cm.ReturnCoin(_coins[i]);
            for (int i = 0; i < _powerups.Count; i++) _cm.ReturnPowerup(_powerups[i]);
            _obstacles.Clear();
            _coins.Clear();
            _powerups.Clear();
        }
    }
}
