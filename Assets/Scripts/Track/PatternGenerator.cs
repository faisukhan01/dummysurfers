using DummySurfer.Core;
using DummySurfer.Data;
using DummySurfer.Powerups;
using DummySurfer.Utilities;

namespace DummySurfer.Track
{
    /// <summary>
    /// Deterministic chunk pattern generator (spec 6.1/6.2). All randomness comes from
    /// SeededRandom(sharedSeed, chunkIndex) — never UnityEngine.Random — so both clients
    /// receive the exact same logical track. Every pattern is validated to keep at least
    /// one survivable path before activation.
    /// </summary>
    public static class PatternGenerator
    {
        public static ChunkPattern Generate(ulong sharedSeed, int chunkIndex)
        {
            var cfg = GameConfig.Runtime;
            var curve = DifficultyCurve.Runtime;
            var catalog = ObstacleCatalog.Runtime;
            var diff = DifficultyManager.Ensure();

            var rng = new SeededRandom(SeededRandom.ChunkSeed(sharedSeed, chunkIndex));
            float d = diff.ContentDifficulty01(chunkIndex);

            float spacing = curve.Lerp(curve.rowSpacingStart, curve.rowSpacingEnd, d);
            int rows = Mathf.Max(6, Mathf.FloorToInt(cfg.chunkLength / spacing)) - 2;

            var pattern = new ChunkPattern
            {
                Rows = rows,
                RowSpacing = spacing,
                Grid = new ObstacleKind[rows, Constants.LaneCount]
            };

            int safeEntryRows = 1;                  // breathing room at chunk start
            int lastTrainEnd = -10;

            for (int r = safeEntryRows; r < rows - 1; r++)
            {
                if (r <= lastTrainEnd + 1) continue;   // always ≥2 free rows after a train

                float obstacleChance = curve.Lerp(curve.obstacleChanceStart, curve.obstacleChanceEnd, d);
                if (!rng.Chance(obstacleChance)) continue;

                // Weighted archetype selection (data-driven weights, spec 5.3)
                var weights = new float[4]
                {
                    catalog.Lerp(catalog.trainWeight, d),
                    catalog.Lerp(catalog.barrierWeight, d),
                    catalog.Lerp(catalog.overheadWeight, d),
                    catalog.Lerp(catalog.signWeight, d)
                };
                var kind = (ObstacleKind)(rng.WeightedIndex(weights) + 1);

                if (kind == ObstacleKind.Train)
                {
                    int span = rng.RangeInt(catalog.trainRowsMin, catalog.trainRowsMax + 1);
                    span = Mathf.Min(span, rows - 2 - r);
                    if (span < 1) continue;

                    int lane = rng.RangeInt(0, Constants.LaneCount);
                    for (int t = 0; t < span; t++) pattern.Grid[r + t, lane] = ObstacleKind.Train;
                    lastTrainEnd = r + span;
                }
                else
                {
                    double doubleChance = curve.Lerp(curve.doubleBarrierChanceStart, curve.doubleBarrierChanceEnd, d);
                    if (rng.Chance((float)doubleChance))
                    {
                        // Two lanes blocked with passable-action kinds, one lane always free.
                        int free = rng.RangeInt(0, Constants.LaneCount);
                        for (int l = 0; l < Constants.LaneCount; l++)
                        {
                            if (l == free) continue;
                            pattern.Grid[r, l] = rng.Chance(0.5f) ? ObstacleKind.Barrier : ObstacleKind.Overhead;
                        }
                    }
                    else
                    {
                        int lane = rng.RangeInt(0, Constants.LaneCount);
                        pattern.Grid[r, lane] = kind;
                        // Occasionally pair a sign next to a real obstacle on another lane.
                        if (kind != ObstacleKind.Sign && rng.Chance(curve.signChance * 0.5f))
                        {
                            int other = (lane + rng.RangeInt(1, Constants.LaneCount)) % Constants.LaneCount;
                            if (pattern.Grid[r, other] == ObstacleKind.None)
                                pattern.Grid[r, other] = ObstacleKind.Sign;
                        }
                    }
                }
            }

            ValidateAndRepair(pattern, rng);

            GenerateCoins(pattern, rng, catalog);
            GeneratePowerup(pattern, rng, cfg);

            return pattern;
        }

        /// <summary>Spec 6.2: validate patterns before activation; repair deterministically if needed.</summary>
        private static void ValidateAndRepair(ChunkPattern p, SeededRandom rng)
        {
            int guard = 0;
            while (!PatternValidator.HasSurvivablePath(p.Grid, p.Rows) && guard++ < 24)
            {
                // Deterministically unblock: find the earliest row with ≥2 trains and clear one.
                for (int r = 0; r < p.Rows; r++)
                {
                    int trains = 0;
                    for (int l = 0; l < Constants.LaneCount; l++)
                        if (p.Grid[r, l] == ObstacleKind.Train) trains++;
                    if (trains >= 2)
                    {
                        int lane = rng.RangeInt(0, Constants.LaneCount);
                        for (int rr = r; rr < p.Rows; rr++)
                            if (p.Grid[rr, lane] == ObstacleKind.Train) p.Grid[rr, lane] = ObstacleKind.None;
                        break;
                    }
                }
            }
            if (!PatternValidator.HasSurvivablePath(p.Grid, p.Rows))
            {
                // Absolute fallback: wipe every train — pattern becomes trivially survivable.
                for (int r = 0; r < p.Rows; r++)
                    for (int l = 0; l < Constants.LaneCount; l++)
                        if (p.Grid[r, l] == ObstacleKind.Train) p.Grid[r, l] = ObstacleKind.None;
            }
        }

        private static void GenerateCoins(ChunkPattern p, SeededRandom rng, ObstacleCatalog catalog)
        {
            int runs = rng.RangeInt(1, 3);
            for (int i = 0; i < runs; i++)
            {
                int lane = rng.RangeInt(0, Constants.LaneCount);
                int start = rng.RangeInt(1, Mathf.Max(2, p.Rows - 5));
                int count = rng.RangeInt(catalog.coinRunMin, catalog.coinRunMax + 1);

                // Skip if the lane is hard-blocked anywhere in the run (unless arc over one barrier)
                bool blocked = false, arc = false;
                for (int r = start; r < Mathf.Min(start + count, p.Rows); r++)
                {
                    var k = p.Grid[r, lane];
                    if (k == ObstacleKind.Train || k == ObstacleKind.Overhead) { blocked = true; break; }
                    if (k == ObstacleKind.Barrier) arc = true;
                }
                if (blocked) continue;

                p.Coins.Add(new CoinRun { Lane = lane, StartRow = start, Count = count, Arc = arc && rng.Chance(catalog.coinArcChance) });
            }
        }

        private static void GeneratePowerup(ChunkPattern p, SeededRandom rng, GameConfig cfg)
        {
            if (!rng.Chance(cfg.powerupChancePerChunk)) return;
            for (int attempt = 0; attempt < 6; attempt++)
            {
                int lane = rng.RangeInt(0, Constants.LaneCount);
                int row = rng.RangeInt(3, Mathf.Max(4, p.Rows - 3));
                if (p.Grid[row, lane] == ObstacleKind.None)
                {
                    p.HasPowerup = true;
                    p.PowerupLane = lane;
                    p.PowerupRow = row;
                    p.Powerup = (PowerupType)rng.RangeInt(1, 4); // Magnet, Shield, Multiplier
                    return;
                }
            }
        }
    }
}
