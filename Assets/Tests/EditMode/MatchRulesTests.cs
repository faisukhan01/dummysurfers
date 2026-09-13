using NUnit.Framework;
using DummySurfer.Multiplayer;

namespace DummySurfer.Tests
{
    /// <summary>Locks the spec 3.3 result table in code.</summary>
    public class MatchRulesTests
    {
        [Test]
        public void HostAlive_ClientDead_HostWins()
        {
            Assert.AreEqual(MatchOutcome.HostWins,
                MatchRules.Resolve(true, false, 100, 500, 10, 99));
        }

        [Test]
        public void ClientAlive_HostDead_ClientWins()
        {
            Assert.AreEqual(MatchOutcome.ClientWins,
                MatchRules.Resolve(false, true, 900, 10, 50, 1));
        }

        [Test]
        public void BothAlive_NotResolved()
        {
            Assert.AreEqual(MatchOutcome.None,
                MatchRules.Resolve(true, true, 0, 0, 0, 0));
        }

        [Test]
        public void BothDead_GreaterDistanceWins()
        {
            Assert.AreEqual(MatchOutcome.HostWins,
                MatchRules.Resolve(false, false, 750, 300, 1, 999));
            Assert.AreEqual(MatchOutcome.ClientWins,
                MatchRules.Resolve(false, false, 300, 750, 999, 1));
        }

        [Test]
        public void BothDead_EqualDistance_MoreCoinsWins()
        {
            Assert.AreEqual(MatchOutcome.HostWins,
                MatchRules.Resolve(false, false, 500, 500, 40, 12));
            Assert.AreEqual(MatchOutcome.ClientWins,
                MatchRules.Resolve(false, false, 500, 500, 12, 40));
        }

        [Test]
        public void BothDead_AllEqual_Draw()
        {
            Assert.AreEqual(MatchOutcome.Draw,
                MatchRules.Resolve(false, false, 500, 500, 40, 40));
        }

        [Test]
        public void FirstDeath_WindowElapsed_SurvivorWins_EvenWithLowerDistance()
        {
            // Simulates the window expiry path in MatchStateManager: survivor wins
            // regardless of stats — mirrors "one dies while other alive -> survivor wins".
            bool hostAlive = true;
            bool clientAlive = false;
            var outcome = MatchRules.Resolve(hostAlive, clientAlive, 100, 900, 0, 50);
            Assert.AreEqual(MatchOutcome.HostWins, outcome);
        }
    }
}
