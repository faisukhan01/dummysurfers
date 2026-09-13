using UnityEngine;

namespace DummySurfer.Utilities
{
    /// <summary>
    /// Base for singleton managers that persist across scene loads.
    /// Duplicate instances self-destroy; Ensure() lazily creates the manager,
    /// so any scene can be opened directly and still boot correctly.
    /// </summary>
    public abstract class PersistentManager<T> : MonoBehaviour where T : PersistentManager<T>
    {
        public static T Instance { get; protected set; }

        protected virtual void Awake()
        {
            if (Instance != null && Instance != this)
            {
                Destroy(gameObject);
                return;
            }
            Instance = (T)this;
            OnManagerAwake();
        }

        protected virtual void OnManagerAwake() { }

        protected virtual void OnDestroy()
        {
            if (Instance == this) Instance = null;
        }

        /// <summary>Get-or-create the manager instance at runtime (idempotent).</summary>
        public static T Ensure()
        {
            if (Instance != null) return Instance;
            var go = new GameObject(typeof(T).Name);
            var component = go.AddComponent<T>(); // Awake assigns Instance
            return component;
        }
    }
}
