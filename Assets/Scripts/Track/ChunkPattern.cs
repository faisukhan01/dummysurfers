using System;
using System.Collections.Generic;
using DummySurfer.Powerups;

namespace DummySurfer.Track
{
    public enum ObstacleKind : byte
    {
        None = 0,
        Train,      // full block — must switch lanes
        Barrier,    // low — must jump (or switch)
        Overhead,   // gantry — must slide (or switch)
        Sign        // non-lethal — stumble penalty
    }

    /// <summary>What a runner must do to survive this obstacle while staying in its lane.</summary>
    public enum ObstacleReaction : byte
    {
        None = 0,
        LaneSwitch,
        Jump,
        Slide,
        Stumble
    }

    public struct CoinRun
    {
        public int Lane;
        public int StartRow;
        public int Count;
        public bool Arc;      // raised arc (e.g. over a barrier)
    }

    /// <summary>
    /// Deterministic chunk content (spec 6.1). Generated as a pure function of
    /// (sharedSeed, chunkIndex, difficultyParameters) — identical on both clients.
    /// </summary>
    public sealed class ChunkPattern
    {
        public int Rows;
        public float RowSpacing;
        public ObstacleKind[,] Grid;          // [row, lane]
        public readonly List<CoinRun> Coins = new List<CoinRun>(4);
        public bool HasPowerup;
        public int PowerupLane;
        public int PowerupRow;
        public PowerupType Powerup;

        public ObstacleKind Get(int row, int lane) => Grid[row, lane];

        public static ObstacleReaction ReactionFor(ObstacleKind kind) => kind switch
        {
            ObstacleKind.Train => ObstacleReaction.LaneSwitch,
            ObstacleKind.Barrier => ObstacleReaction.Jump,
            ObstacleKind.Overhead => ObstacleReaction.Slide,
            ObstacleKind.Sign => ObstacleReaction.Stumble,
            _ => ObstacleReaction.None
        };
    }
}
