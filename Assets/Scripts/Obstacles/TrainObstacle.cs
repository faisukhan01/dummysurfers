using UnityEngine;

namespace DummySurfer.Obstacles
{
    /// <summary>Lethal full-block train: the only obstacle that must be dodged by lane switch.</summary>
    public sealed class TrainObstacle : ObstacleBase
    {
        public override void OnSpawned(Vector3 localPosition, float length)
        {
            base.OnSpawned(localPosition, length);
            // Scale the visual body along Z with the pattern span.
            var body = transform.Find("Body");
            if (body != null)
            {
                var s = body.localScale;
                body.localScale = new Vector3(s.x, s.y, length);
                body.localPosition = new Vector3(0f, 1.45f, length * 0.5f);
            }
            var roof = transform.Find("Roof");
            if (roof != null)
            {
                roof.localScale = new Vector3(1.7f, 0.22f, length * 0.98f);
                roof.localPosition = new Vector3(0f, 2.85f, length * 0.5f);
            }
            var skirt = transform.Find("Skirt");
            if (skirt != null)
            {
                skirt.localScale = new Vector3(1.8f, 0.44f, length * 0.98f);
                skirt.localPosition = new Vector3(0f, 0.22f, length * 0.5f);
            }
            var windows = transform.Find("Windows");
            if (windows != null)
            {
                windows.localScale = new Vector3(1.94f, 0.5f, length * 0.86f);
                windows.localPosition = new Vector3(0f, 1.9f, length * 0.5f);
            }
            var stripe = transform.Find("Stripe");
            if (stripe != null)
            {
                stripe.localScale = new Vector3(1.96f, 0.16f, length * 0.86f);
                stripe.localPosition = new Vector3(0f, 1.52f, length * 0.5f);
            }
            var face = transform.Find("Face");
            if (face != null) face.localPosition = new Vector3(0f, 1.5f, length + 0.04f);

            var glass = transform.Find("FaceGlass");
            if (glass != null) glass.localPosition = new Vector3(0f, 1.95f, length + 0.09f);

            var col = GetComponent<BoxCollider>();
            if (col != null)
            {
                col.center = new Vector3(0f, 1.5f, length * 0.5f);
                col.size = new Vector3(1.9f, 3f, length);
            }
        }
    }
}
