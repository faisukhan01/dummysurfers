using UnityEngine;

namespace DummySurfer.Track
{
    /// <summary>
    /// Guarantees every generated pattern keeps at least one valid path
    /// (spec 6.2: "Never generate an impossible obstacle sequence").
    /// Model: per row, staying in a lane is survivable for every kind except Train
    /// (hard block); runners may move ±1 lane between consecutive rows.
    /// BFS over rows x lanes proves a survivable lane sequence exists.
    /// </summary>
    public static class PatternValidator
    {
        public static bool IsLaneBlockedHere(ObstacleKind kind)
            => kind == ObstacleKind.Train;

        public static bool HasSurvivablePath(ObstacleKind[,] grid, int rows, int lanes = 3)
        {
            if (rows <= 0) return true;

            var reach = new bool[lanes];
            var next = new bool[lanes];
            for (int l = 0; l < lanes; l++) reach[l] = true;   // enter chunk in any lane

            for (int r = 0; r < rows; r++)
            {
                bool any = false;
                for (int l = 0; l < lanes; l++)
                {
                    next[l] = false;
                    if (IsLaneBlockedHere(grid[r, l])) continue;
                    if (reach[l]) next[l] = true;
                    if (l > 0 && reach[l - 1]) next[l] = true;
                    if (l < lanes - 1 && reach[l + 1]) next[l] = true;
                    if (next[l]) any = true;
                }
                if (!any) return false;
                (reach, next) = (next, reach);
            }
            return reach[0] || reach[1] || reach[2];
        }

        /// <summary>Diagnostics: does any row block all three lanes outright?</summary>
        public static bool HasFullyBlockedRow(ObstacleKind[,] grid, int rows)
        {
            for (int r = 0; r < rows; r++)
            {
                if (grid[r, 0] == ObstacleKind.Train &&
                    grid[r, 1] == ObstacleKind.Train &&
                    grid[r, 2] == ObstacleKind.Train) return true;
            }
            return false;
        }
    }
}
