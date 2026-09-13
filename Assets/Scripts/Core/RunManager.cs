using UnityEngine;
using DummySurfer.Utilities;

namespace DummySurfer.Core
{
    /// <summary>Immutable result snapshot of one run (single-player or the local half of a match).</summary>
    public struct RunSummary
    {
        public int Distance;
        public int Coins;
        public int BestDistance;
        public int BestCoins;
        public bool NewBest;
    }

    /// <summary>
    /// Owns the LOCAL player's run state: distance, coins, personal bests.
    /// Works identically offline and as the local half of an online match.
    /// </summary>
    public sealed class RunManager : PersistentManager<RunManager>
    {
        public float Distance { get; private set; }
        public int Coins { get; private set; }
        public bool RunActive { get; private set; }

        public int BestDistance { get; private set; }
        public int BestCoins { get; private set; }

        private int _publishedDistance = -1;
        private float _runStartRealtime;

        public float RunSeconds => Time.realtimeSinceStartup - _runStartRealtime;

        protected override void OnManagerAwake()
        {
            ServiceRegistry.Register(this);
            BestDistance = PlayerPrefs.GetInt(Constants.Prefs.BestDistance, 0);
            BestCoins = PlayerPrefs.GetInt(Constants.Prefs.BestCoins, 0);
        }

        public void BeginRun()
        {
            Distance = 0f;
            Coins = 0;
            RunActive = true;
            _publishedDistance = -1;
            _runStartRealtime = Time.realtimeSinceStartup;
            PlayerPrefs.SetInt(Constants.Prefs.TotalRuns, PlayerPrefs.GetInt(Constants.Prefs.TotalRuns, 0) + 1);
            GameEvents.PublishRunStarted();
            GameEvents.PublishCoinsChanged(0);
        }

        /// <summary>Called every frame by the local player; publishes only on integer change (minimized UI redraws).</summary>
        public void ReportDistance(float zMeters)
        {
            if (!RunActive) return;
            Distance = zMeters;
            int m = Mathf.FloorToInt(zMeters);
            if (m != _publishedDistance)
            {
                _publishedDistance = m;
                GameEvents.PublishDistanceChanged(m);
            }
        }

        public void AddCoins(int amount)
        {
            if (!RunActive || amount <= 0) return;
            Coins += amount;
            GameEvents.PublishCoinsChanged(Coins);
        }

        public RunSummary EndRun()
        {
            RunActive = false;
            int dist = Mathf.FloorToInt(Distance);
            bool newBest = dist > BestDistance || (dist == BestDistance && Coins > BestCoins);
            if (dist > BestDistance) BestDistance = dist;
            if (Coins > BestCoins) BestCoins = Coins;
            PlayerPrefs.SetInt(Constants.Prefs.BestDistance, BestDistance);
            PlayerPrefs.SetInt(Constants.Prefs.BestCoins, BestCoins);
            PlayerPrefs.Save();

            var summary = new RunSummary
            {
                Distance = dist,
                Coins = Coins,
                BestDistance = BestDistance,
                BestCoins = BestCoins,
                NewBest = newBest
            };
            GameEvents.PublishRunEnded(summary);
            return summary;
        }

        public void ResetForRematch()
        {
            Distance = 0f;
            Coins = 0;
            RunActive = false;
            _publishedDistance = -1;
            GameEvents.PublishCoinsChanged(0);
            GameEvents.PublishDistanceChanged(0);
        }
    }
}
