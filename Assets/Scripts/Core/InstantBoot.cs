using UnityEngine;
using UnityEngine.SceneManagement;
using DummySurfer.Utilities;

namespace DummySurfer.Core
{
    /// <summary>
    /// Boot scene is now a pure hop: load the Game scene on frame one so the player is
    /// in the subway within a second of tapping the app icon. Unity Gaming Services warmup
    /// (started by GameBootstrap) keeps running in the background for online play later.
    /// </summary>
    public sealed class InstantBoot : MonoBehaviour
    {
        private bool _jumped;

        private void Start()
        {
            Jump();
        }

        private void Update()
        {
            Jump(); // safety: if the first jump was blocked by scene ops, keep trying
        }

        private void Jump()
        {
            if (_jumped) return;
            if (SceneManager.GetActiveScene().name != Constants.SceneBoot) { _jumped = true; return; }
            _jumped = true;
            SceneManager.LoadScene(Constants.SceneGame);
        }
    }
}
