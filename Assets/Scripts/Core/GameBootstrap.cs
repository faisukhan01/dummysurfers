using UnityEngine;
using UnityEngine.SceneManagement;
using DummySurfer.Utilities;

namespace DummySurfer.Core
{
    /// <summary>
    /// Composition root (spec 5.2 GameBootstrap). Creates every persistent manager exactly once,
    /// applies platform performance defaults, warms up Unity Gaming Services in the background,
    /// and advances the Boot scene to the main menu. Idempotent: any scene can be opened directly.
    /// </summary>
    public sealed class GameBootstrap : PersistentManager<GameBootstrap>
    {
        private bool _menuQueued;

        protected override void OnManagerAwake()
        {
            ServiceRegistry.Register(this);
            DontDestroyOnLoad(gameObject);

            var perf = PerformanceMonitor.Ensure();
            perf.SetTier(PlayerPrefs.GetInt(Constants.Prefs.Quality, 2), save: false);

            GameStateManager.Ensure();
            RunManager.Ensure();
            DifficultyManager.Ensure();
            PoolManager.Ensure();
            Audio.AudioManager.Ensure();
            UI.UIManager.Ensure();
            Multiplayer.MultiplayerManager.Ensure();

            GameServicesInitializer.Warmup();

            SceneManager.sceneLoaded += OnSceneLoaded;
        }

        private void OnSceneLoaded(Scene scene, LoadSceneMode mode)
        {
            if (scene.name == Constants.SceneBoot && !_menuQueued)
            {
                _menuQueued = true;
                StartCoroutine(BootToMenu());
            }
        }

        private System.Collections.IEnumerator BootToMenu()
        {
            float minSplash = 1.4f;
            float deadline = Time.unscaledTime + 7f;      // never hang forever on services
            yield return new WaitForSecondsRealtime(minSplash);

            while (!GameServicesInitializer.ServicesInitialized && Time.unscaledTime < deadline)
                yield return new WaitForSecondsRealtime(0.1f);

            yield return new WaitForSecondsRealtime(0.2f);
            if (SceneManager.GetActiveScene().name == Constants.SceneBoot)
                SceneManager.LoadScene(Constants.SceneMenu);
        }

        protected override void OnDestroy()
        {
            SceneManager.sceneLoaded -= OnSceneLoaded;
            base.OnDestroy();
        }

        /// <summary>Call from any scene entry point to guarantee the full stack exists.</summary>
        public static void EnsureAll() => Ensure();
    }
}
