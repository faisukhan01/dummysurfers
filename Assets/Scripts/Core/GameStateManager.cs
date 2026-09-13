using UnityEngine;
using DummySurfer.Utilities;

namespace DummySurfer.Core
{
    /// <summary>Global app states (spec 5.2 GameStateManager).</summary>
    public enum GameState
    {
        Boot,
        Menu,
        Lobby,
        Countdown,
        Running,
        Paused,
        Results,
        Disconnected
    }

    /// <summary>
    /// Thin state broadcaster — publishes transitions, owns pause time-scaling for offline play.
    /// It deliberately contains no gameplay logic (no giant GameManager, spec 10).
    /// </summary>
    public sealed class GameStateManager : PersistentManager<GameStateManager>
    {
        public GameState Current { get; private set; } = GameState.Boot;

        public bool IsOnlineRun { get; set; }

        protected override void OnManagerAwake()
        {
            ServiceRegistry.Register(this);
        }

        public void Set(GameState next)
        {
            if (next == Current) return;

            // Offline pause freezes simulation; online pause is overlay-only (competitive fairness).
            if (next == GameState.Paused && !IsOnlineRun) Time.timeScale = 0f;
            if (Current == GameState.Paused && Time.timeScale == 0f) Time.timeScale = 1f;

            Current = next;
            GameEvents.PublishGameStateChanged(next);
        }

        public void ResetToMenu()
        {
            IsOnlineRun = false;
            if (Time.timeScale == 0f) Time.timeScale = 1f;
            Set(GameState.Menu);
        }

        public void SetDisconnected()
        {
            if (Time.timeScale == 0f) Time.timeScale = 1f;
            Set(GameState.Disconnected);
        }
    }
}
