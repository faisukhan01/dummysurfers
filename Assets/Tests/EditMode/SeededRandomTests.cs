using NUnit.Framework;
using DummySurfer.Utilities;

namespace DummySurfer.Tests
{
    /// <summary>Deterministic generation is the backbone of fair multiplayer — lock it down.</summary>
    public class SeededRandomTests
    {
        [Test]
        public void SameSeed_ProducesIdenticalSequences()
        {
            var a = new SeededRandom(123456789UL);
            var b = new SeededRandom(123456789UL);
            for (int i = 0; i < 1000; i++)
                Assert.AreEqual(a.NextUInt64(), b.NextUInt64(), $"diverged at draw {i}");
        }

        [Test]
        public void DifferentSeeds_Differ()
        {
            var a = new SeededRandom(1UL);
            var b = new SeededRandom(2UL);
            bool differs = false;
            for (int i = 0; i < 64; i++)
                if (a.NextUInt64() != b.NextUInt64()) { differs = true; break; }
            Assert.IsTrue(differs);
        }

        [Test]
        public void Range_StaysInBounds()
        {
            var rng = new SeededRandom(42UL);
            for (int i = 0; i < 10000; i++)
            {
                float f = rng.Range(-3.5f, 7.25f);
                Assert.That(f, Is.InRange(-3.5f, 7.25f));
                int n = rng.RangeInt(0, 3);
                Assert.That(n, Is.InRange(0, 2));
            }
        }

        [Test]
        public void ChunkSeed_IsStablePerIndex()
        {
            Assert.AreEqual(SeededRandom.ChunkSeed(999UL, 7), SeededRandom.ChunkSeed(999UL, 7));
            Assert.AreNotEqual(SeededRandom.ChunkSeed(999UL, 7), SeededRandom.ChunkSeed(999UL, 8));
        }

        [Test]
        public void WeightedIndex_ExtremeWeights()
        {
            var rng = new SeededRandom(7UL);
            int zeros = 0, ones = 0;
            for (int i = 0; i < 500; i++)
            {
                int idx = rng.WeightedIndex(new float[] { 0f, 1f });
                if (idx == 0) zeros++;
                else ones++;
            }
            Assert.AreEqual(0, zeros, "zero-weight entries must never be picked");
            Assert.Greater(ones, 0);
        }
    }
}
