using UnityEngine;

namespace DummySurfer.Obstacles
{
    /// <summary>
    /// Purely decorative far-rail train (spec 7.2 keep-local). Never gameplay-critical,
    /// never networked, no collider — it just sells the living rail-yard fantasy.
    /// </summary>
    public sealed class DecorTrain : MonoBehaviour
    {
        public float Speed { get; set; }

        private void Update()
        {
            if (Speed > 0f)
                transform.position += Vector3.forward * (Speed * Time.deltaTime);
        }
    }
}
