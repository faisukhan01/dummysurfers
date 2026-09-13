using UnityEngine;
using DummySurfer.Player;

using DummySurfer.Track;
namespace DummySurfer.Obstacles
{
    /// <summary>
    /// Pooled obstacle base (spec 6.3). Trigger colliders forward contact to the local player;
    /// the player decides lethal vs. stumble vs. shield (spec 3.1).
    /// </summary>
    public abstract class ObstacleBase : MonoBehaviour
    {
        public ObstacleKind Kind { get; protected set; }
        public bool Lethal { get; protected set; } = true;
        public float Length { get; protected set; } = 1f;

        public virtual void OnSpawned(Vector3 localPosition, float length)
        {
            Length = length;
            transform.localPosition = localPosition;
            transform.localRotation = Quaternion.identity;
        }

        public virtual void OnReturned() { }

        protected virtual void OnTriggerEnter(Collider other)
        {
            var player = other.GetComponentInParent<PlayerController>();
            if (player != null) player.OnHitBy(this);
        }
    }
}
