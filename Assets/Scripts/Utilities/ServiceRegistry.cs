using System;
using System.Collections.Generic;

namespace DummySurfer.Utilities
{
    /// <summary>
    /// Minimal, allocation-free service locator. Managers register themselves on create;
    /// consumers resolve by type. Prevents both FindObjectOfType spam and one giant GameManager.
    /// </summary>
    public static class ServiceRegistry
    {
        private static readonly Dictionary<Type, object> Map = new Dictionary<Type, object>(16);

        public static void Register<T>(T service) where T : class
        {
            Map[typeof(T)] = service;
        }

        public static void Unregister<T>() where T : class
        {
            if (Map.TryGetValue(typeof(T), out var current) && current is UnityEngine.Object u && u == null)
                Map.Remove(typeof(T));
        }

        public static T Get<T>() where T : class
        {
            return Map.TryGetValue(typeof(T), out var s) ? s as T : null;
        }

        public static bool TryGet<T>(out T service) where T : class
        {
            service = Get<T>();
            return service != null;
        }

        public static void ClearNonUnity()
        {
            var keys = new List<Type>();
            foreach (var kv in Map)
                if (kv.Value is not UnityEngine.Object)
                    keys.Add(kv.Key);
            foreach (var k in keys) Map.Remove(k);
        }
    }
}
