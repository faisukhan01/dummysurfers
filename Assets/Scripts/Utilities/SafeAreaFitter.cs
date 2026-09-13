using UnityEngine;

namespace DummySurfer.Utilities
{
    /// <summary>
    /// Expands a RectTransform into the device safe area (notches, punch-holes, gesture bars).
    /// Attached to UI roots at runtime; re-applies on resolution/orientation changes.
    /// </summary>
    [RequireComponent(typeof(RectTransform))]
    public class SafeAreaFitter : MonoBehaviour
    {
        private Rect _applied;
        private ScreenOrientation _orientation;

        private void OnEnable()
        {
            Apply();
        }

        private void Update()
        {
            if (Screen.orientation != _orientation || _applied != Screen.safeArea)
                Apply();
        }

        private void Apply()
        {
            var rt = (RectTransform)transform;
            Rect safe = Screen.safeArea;
            if (safe.width <= 0 || safe.height <= 0) return;

            Vector2 min = safe.position;
            Vector2 max = safe.position + safe.size;
            min.x /= Screen.width; min.y /= Screen.height;
            max.x /= Screen.width; max.y /= Screen.height;

            rt.anchorMin = min;
            rt.anchorMax = max;
            rt.offsetMin = Vector2.zero;
            rt.offsetMax = Vector2.zero;

            _applied = safe;
            _orientation = Screen.orientation;
        }
    }
}
