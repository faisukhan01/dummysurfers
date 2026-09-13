using System.Collections.Generic;

namespace DummySurfer.Utilities
{
    /// <summary>
    /// Deterministic PRNG (SplitMix64) used for ALL gameplay-critical generation.
    /// Spec 10 "DETERMINISTIC TRACK": never use gameplay-critical UnityEngine.Random on clients.
    /// Same seed => identical sequence on every device and runtime.
    /// </summary>
    public sealed class SeededRandom
    {
        private const ulong Golden = 0x9E3779B97F4A7C15UL;

        private ulong _state;

        public SeededRandom(ulong seed) { _state = seed; }

        public static ulong NewSeed()
        {
            var sys = new System.Random();
            return ((ulong)(uint)sys.Next()) << 32 | (uint)sys.Next();
        }

        /// <summary>Derive a stable per-chunk sub-seed from a shared seed.</summary>
        public static ulong ChunkSeed(ulong sharedSeed, int chunkIndex)
        {
            return sharedSeed ^ ((ulong)(uint)chunkIndex + 1UL) * Golden;
        }

        public ulong NextUInt64()
        {
            _state += Golden;
            ulong z = _state;
            z = (z ^ (z >> 30)) * 0xBF58476D1CE4E5B9UL;
            z = (z ^ (z >> 27)) * 0x94D049BB133111EBUL;
            return z ^ (z >> 31);
        }

        /// <summary>Uniform [0,1).</summary>
        public float NextFloat01()
        {
            // 53 bits of precision mapped to double, then cast (avoids modulo bias).
            return (float)((NextUInt64() >> 11) * (1.0 / 9007199254740992.0));
        }

        public float Range(float minInclusive, float maxExclusive)
            => minInclusive + (maxExclusive - minInclusive) * NextFloat01();

        /// <summary>Uniform int in [minInclusive, maxExclusive).</summary>
        public int RangeInt(int minInclusive, int maxExclusive)
            => minInclusive + (int)(NextFloat01() * (maxExclusive - minInclusive));

        /// <summary>True with probability p in [0,1].</summary>
        public bool Chance(float p) => NextFloat01() < p;

        public T Pick<T>(IReadOnlyList<T> list) => list[RangeInt(0, list.Count)];

        /// <summary>Pick an index from a weight list (weights may be zero; sum must be > 0).</summary>
        public int WeightedIndex(IReadOnlyList<float> weights)
        {
            float total = 0f;
            for (int i = 0; i < weights.Count; i++) total += weights[i];
            float roll = Range(0f, total);
            float acc = 0f;
            for (int i = 0; i < weights.Count; i++)
            {
                acc += weights[i];
                if (roll < acc) return i;
            }
            return weights.Count - 1;
        }

        public void Shuffle<T>(IList<T> list)
        {
            for (int i = list.Count - 1; i > 0; i--)
            {
                int j = RangeInt(0, i + 1);
                (list[i], list[j]) = (list[j], list[i]);
            }
        }
    }
}
