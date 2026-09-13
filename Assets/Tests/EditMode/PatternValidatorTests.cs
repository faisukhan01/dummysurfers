using NUnit.Framework;
using UnityEngine;
using DummySurfer.Track;

namespace DummySurfer.Tests
{
    public class PatternValidatorTests
    {
        private static ObstacleKind[,] Grid(int rows)
        {
            var g = new ObstacleKind[rows, 3];
            for (int r = 0; r < rows; r++)
                for (int l = 0; l < 3; l++)
                    g[r, l] = ObstacleKind.None;
            return g;
        }

        [Test]
        public void EmptyGrid_IsAlwaysValid()
        {
            var g = Grid(12);
            Assert.IsTrue(PatternValidator.HasSurvivablePath(g, 12));
        }

        [Test]
        public void FullyBlockedRow_IsInvalid()
        {
            var g = Grid(8);
            g[4, 0] = ObstacleKind.Train;
            g[4, 1] = ObstacleKind.Train;
            g[4, 2] = ObstacleKind.Train;
            Assert.IsFalse(PatternValidator.HasSurvivablePath(g, 8));
            Assert.IsTrue(PatternValidator.HasFullyBlockedRow(g, 8));
        }

        [Test]
        public void TwoBlockedLanes_IsValid_ThroughFreeLane()
        {
            var g = Grid(8);
            g[3, 0] = ObstacleKind.Train;
            g[4, 0] = ObstacleKind.Train;
            g[3, 2] = ObstacleKind.Train;
            g[4, 2] = ObstacleKind.Train;
            Assert.IsTrue(PatternValidator.HasSurvivablePath(g, 8));
        }

        [Test]
        public void TrainsCannotBePassedByStaying()
        {
            // Zig-zag trains that require an unreachable lane move
            var g = Grid(7);
            g[2, 0] = ObstacleKind.Train; g[2, 1] = ObstacleKind.Train; // must be in lane 2
            g[4, 1] = ObstacleKind.Train; g[4, 2] = ObstacleKind.Train; // must be in lane 0
            // rows 3 between them allow lane 2 -> 0? distance is 2 lanes across 1 row: not allowed.
            Assert.IsFalse(PatternValidator.HasSurvivablePath(g, 7));
        }

        [Test]
        public void Generator_AlwaysProducesSurvivablePatterns()
        {
            // 150 different seeds — the validator-repair loop inside the generator must hold.
            for (int seed = 0; seed < 150; seed++)
            {
                for (int chunkIndex = 0; chunkIndex < 40; chunkIndex += 4)
                {
                    var p = PatternGenerator.Generate((ulong)seed * 7919UL, chunkIndex);
                    Assert.IsTrue(PatternValidator.HasSurvivablePath(p.Grid, p.Rows),
                        $"unsurvivable pattern: seed {seed}, chunk {chunkIndex}");
                    Assert.IsFalse(PatternValidator.HasFullyBlockedRow(p.Grid, p.Rows),
                        $"fully blocked row: seed {seed}, chunk {chunkIndex}");
                }
            }
        }

        [Test]
        public void Generator_IsDeterministic()
        {
            var a = PatternGenerator.Generate(0xDEADBEEFUL, 5);
            var b = PatternGenerator.Generate(0xDEADBEEFUL, 5);
            Assert.AreEqual(a.Rows, b.Rows);
            for (int r = 0; r < a.Rows; r++)
                for (int l = 0; l < 3; l++)
                    Assert.AreEqual(a.Get(r, l), b.Get(r, l), $"grid mismatch at {r},{l}");
            Assert.AreEqual(a.Coins.Count, b.Coins.Count);
            Assert.AreEqual(a.HasPowerup, b.HasPowerup);
        }
    }
}
