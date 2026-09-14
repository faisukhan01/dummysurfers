using UnityEngine;

namespace DummySurfer
{
    /// <summary>Only object placed in the scene. Boots the whole game at runtime
    /// (players), but stays inert during headless CI scene generation.</summary>
    public class SceneBootstrap : MonoBehaviour
    {
        void Awake()
        {
#if UNITY_EDITOR
            if (!UnityEditor.EditorApplication.isPlaying) return; // CI scene-gen: don't boot
#endif
            if (GameObject.Find("~GameRoot") == null)
            {
                var go = new GameObject("~GameRoot");
                go.AddComponent<GameRoot>();
            }
        }
    }
}
