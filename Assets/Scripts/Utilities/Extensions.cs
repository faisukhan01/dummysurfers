using System.Collections.Generic;
using UnityEngine;

namespace DummySurfer.Utilities
{
    public static class Extensions
    {
        /// <summary>Frame-rate independent exponential approach.</summary>
        public static float Approach(this float current, float target, float rate, float dt)
            => Mathf.Lerp(current, target, 1f - Mathf.Exp(-rate * dt));

        public static Vector3 WithX(this Vector3 v, float x) { v.x = x; return v; }
        public static Vector3 WithY(this Vector3 v, float y) { v.y = y; return v; }
        public static Vector3 WithZ(this Vector3 v, float z) { v.z = z; return v; }

        public static Color WithAlpha(this Color c, float a) { c.a = a; return c; }

        public static void DestroyAllChildren(this Transform t)
        {
            for (int i = t.childCount - 1; i >= 0; i--)
                Object.Destroy(t.GetChild(i).gameObject);
        }

        public static void SetLayerRecursive(Transform root, int layer)
        {
            root.gameObject.layer = layer;
            for (int i = 0; i < root.childCount; i++) SetLayerRecursive(root.GetChild(i), layer);
        }

        public static string FormatDistance(float meters) => $"{Mathf.FloorToInt(meters)} m";

        public static string FormatCoins(int coins) => coins.ToString();

        /// <summary>Basic async timeout wrapper — used to guarantee friendly failure states
        /// on all network operations (spec 3.2 / 10 ROOM FLOW: timeout, retry, error states).</summary>
        public static async System.Threading.Tasks.Task<T> WithTimeout<T>(this System.Threading.Tasks.Task<T> task, float seconds, string opName = "operation")
        {
            var timeout = System.Threading.Tasks.Task.Delay(TimeSpan.FromSeconds(seconds));
            var finished = await System.Threading.Tasks.Task.WhenAny(task, timeout);
            if (finished != task)
                throw new TimeoutException($"'{opName}' timed out after {seconds:0}s. Check your connection and try again.");
            return await task;
        }
    }
}
