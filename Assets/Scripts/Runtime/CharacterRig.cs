using UnityEngine;

namespace DummySurfer
{
    /// <summary>Procedural cartoon characters (boy runner, inspector, dog) + pose engine.</summary>
    public class CharacterRig : MonoBehaviour
    {
        public Transform body, head, armL, armR, legL, legR, torso, tail, board, jet, flameL, flameR, bag;
        public string kind = "boy";
        float blink;

        // ------------------------------------------------ builder utils
        static GameObject Part(Transform parent, PrimitiveType t, Vector3 pos, Vector3 scl, Material mat, string name)
        {
            var go = GameObject.CreatePrimitive(t);
            Object.Destroy(go.GetComponent<Collider>());
            go.name = name;
            go.transform.SetParent(parent, false);
            go.transform.localPosition = pos;
            go.transform.localScale = scl;
            var mr = go.GetComponent<MeshRenderer>();
            mr.sharedMaterial = mat;
            mr.shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.On;
            mr.receiveShadows = false;
            return go;
        }

        static Transform Pivot(Transform parent, string name, Vector3 pos)
        {
            var go = new GameObject(name);
            go.transform.SetParent(parent, false);
            go.transform.localPosition = pos;
            return go.transform;
        }

        // ------------------------------------------------ BOY
        public static CharacterRig BuildBoy(Transform parent)
        {
            var root = new GameObject("Boy");
            root.transform.SetParent(parent, false);
            var rig = root.AddComponent<CharacterRig>();
            rig.kind = "boy";

            var skin = Fx.Mat(GMC(0xFF, 0xC9, 0xA0));
            var hoodie = Fx.Mat(GMC(0xF4, 0xF4, 0xF0));
            var jeans = Fx.Mat(GMC(0x5C, 0x7E, 0xB8));
            var capM = Fx.Mat(GMC(0xE8, 0x4B, 0x4B));
            var hairM = Fx.Mat(GMC(0x6B, 0x4A, 0x2F));
            var shoe = Fx.Mat(GMC(0xFA, 0xFA, 0xFA));
            var sole = Fx.Mat(GMC(0x35, 0xC4, 0x6A));
            var pack = Fx.Mat(GMC(0x2E, 0x4A, 0x8F));
            var dark = Fx.Mat(GMC(0x22, 0x2A, 0x3A));

            rig.body = Pivot(root.transform, "body", Vector3.zero);

            // legs
            rig.legL = Pivot(rig.body, "legL", new Vector3(-0.14f, 0.92f, 0));
            Part(rig.legL, PrimitiveType.Capsule, new Vector3(0, -0.23f, 0), new Vector3(0.19f, 0.25f, 0.19f), jeans, "thighL");
            Part(rig.legL, PrimitiveType.Cube, new Vector3(0, -0.46f, 0.05f), new Vector3(0.16f, 0.12f, 0.32f), shoe, "shoeL");
            Part(rig.legL, PrimitiveType.Cube, new Vector3(0, -0.51f, 0.05f), new Vector3(0.17f, 0.035f, 0.34f), sole, "soleL");
            rig.legR = Pivot(rig.body, "legR", new Vector3(0.14f, 0.92f, 0));
            Part(rig.legR, PrimitiveType.Capsule, new Vector3(0, -0.23f, 0), new Vector3(0.19f, 0.25f, 0.19f), jeans, "thighR");
            Part(rig.legR, PrimitiveType.Cube, new Vector3(0, -0.46f, 0.05f), new Vector3(0.16f, 0.12f, 0.32f), shoe, "shoeR");
            Part(rig.legR, PrimitiveType.Cube, new Vector3(0, -0.51f, 0.05f), new Vector3(0.17f, 0.035f, 0.34f), sole, "soleR");

            // torso
            rig.torso = Pivot(rig.body, "torso", new Vector3(0, 0.95f, 0));
            Part(rig.torso, PrimitiveType.Capsule, new Vector3(0, 0.28f, 0), new Vector3(0.50f, 0.30f, 0.34f), hoodie, "chest");
            Part(rig.torso, PrimitiveType.Cube, new Vector3(0, 0.46f, 0.14f), new Vector3(0.16f, 0.07f, 0.05f), capM, "collar");
            Part(rig.torso, PrimitiveType.Cube, new Vector3(0, 0.30f, 0.16f), new Vector3(0.03f, 0.34f, 0.02f), dark, "zip");
            // backpack + spray cans
            Part(rig.torso, PrimitiveType.Cube, new Vector3(0, 0.30f, -0.24f), new Vector3(0.34f, 0.40f, 0.15f), pack, "pack");
            Part(rig.torso, PrimitiveType.Cylinder, new Vector3(0.20f, 0.24f, -0.22f), new Vector3(0.09f, 0.07f, 0.09f), Fx.Mat(GMC(0xE8, 0x4B, 0x4B)), "can1");
            Part(rig.torso, PrimitiveType.Cylinder, new Vector3(0.20f, 0.14f, -0.22f), new Vector3(0.09f, 0.07f, 0.09f), Fx.Mat(GMC(0x35, 0xC4, 0xB6)), "can2");

            // arms
            rig.armL = Pivot(rig.body, "armL", new Vector3(-0.31f, 1.44f, 0));
            Part(rig.armL, PrimitiveType.Capsule, new Vector3(0, -0.18f, 0), new Vector3(0.16f, 0.14f, 0.16f), hoodie, "armuL");
            Part(rig.armL, PrimitiveType.Sphere, new Vector3(0, -0.38f, 0), new Vector3(0.16f, 0.16f, 0.16f), skin, "handL");
            rig.armR = Pivot(rig.body, "armR", new Vector3(0.31f, 1.44f, 0));
            Part(rig.armR, PrimitiveType.Capsule, new Vector3(0, -0.18f, 0), new Vector3(0.16f, 0.14f, 0.16f), hoodie, "armuR");
            rig.handR = Part(rig.armR, PrimitiveType.Sphere, new Vector3(0, -0.38f, 0), new Vector3(0.16f, 0.16f, 0.16f), skin, "handR");
            // spray can in hand (visible in menu spray pose)
            rig.bag = Part(rig.armR, PrimitiveType.Cylinder, new Vector3(0, -0.44f, 0.06f), new Vector3(0.10f, 0.08f, 0.10f), Fx.Mat(GMC(0xFF, 0x8A, 0x3D)), "spraycan");
            rig.bag.gameObject.SetActive(false);

            // head
            rig.head = Pivot(rig.body, "head", new Vector3(0, 1.56f, 0));
            Part(rig.head, PrimitiveType.Sphere, new Vector3(0, 0.20f, 0), new Vector3(0.46f, 0.46f, 0.44f), skin, "skull");
            Part(rig.head, PrimitiveType.Sphere, new Vector3(0, 0.27f, -0.06f), new Vector3(0.47f, 0.30f, 0.42f), hairM, "hair");
            Part(rig.head, PrimitiveType.Sphere, new Vector3(0, 0.33f, 0.02f), new Vector3(0.46f, 0.26f, 0.46f), capM, "cap");
            Part(rig.head, PrimitiveType.Cube, new Vector3(0, 0.30f, 0.24f), new Vector3(0.30f, 0.035f, 0.18f), capM, "brim");
            Part(rig.head, PrimitiveType.Sphere, new Vector3(-0.085f, 0.20f, 0.185f), new Vector3(0.09f, 0.10f, 0.05f), Fx.Mat(Color.white), "eyeL");
            Part(rig.head, PrimitiveType.Sphere, new Vector3(0.085f, 0.20f, 0.185f), new Vector3(0.09f, 0.10f, 0.05f), Fx.Mat(Color.white), "eyeR");
            Part(rig.head, PrimitiveType.Sphere, new Vector3(-0.085f, 0.20f, 0.215f), new Vector3(0.045f, 0.05f, 0.03f), dark, "pupL");
            Part(rig.head, PrimitiveType.Sphere, new Vector3(0.085f, 0.20f, 0.215f), new Vector3(0.045f, 0.05f, 0.03f), dark, "pupR");

            // hoverboard (hidden by default)
            rig.board = new GameObject("board").transform;
            rig.board.SetParent(root.transform, false);
            rig.board.localPosition = new Vector3(0, 0.10f, 0);
            Part(rig.board, PrimitiveType.Cube, Vector3.zero, new Vector3(0.88f, 0.07f, 0.44f), Fx.Mat(GMC(0xFF, 0xD2, 0x3E)), "deck");
            Part(rig.board, PrimitiveType.Cube, new Vector3(0, -0.02f, 0), new Vector3(0.80f, 0.05f, 0.36f), Fx.Mat(GMC(0xFF, 0x6B, 0x8E)), "stripe");
            Part(rig.board, PrimitiveType.Cylinder, new Vector3(-0.25f, -0.10f, 0), new Vector3(0.10f, 0.015f, 0.10f), dark, "glow1");
            Part(rig.board, PrimitiveType.Cylinder, new Vector3(0.25f, -0.10f, 0), new Vector3(0.10f, 0.015f, 0.10f), dark, "glow2");
            rig.board.gameObject.SetActive(false);

            // jetpack (hidden)
            rig.jet = new GameObject("jet").transform;
            rig.jet.SetParent(rig.body, false);
            Part(rig.jet, PrimitiveType.Cylinder, new Vector3(-0.15f, 1.28f, -0.28f), new Vector3(0.18f, 0.16f, 0.18f), Fx.Mat(GMC(0xB8, 0xC2, 0xCC)), "tank1");
            Part(rig.jet, PrimitiveType.Cylinder, new Vector3(0.15f, 1.28f, -0.28f), new Vector3(0.18f, 0.16f, 0.18f), Fx.Mat(GMC(0xB8, 0xC2, 0xCC)), "tank2");
            rig.flameL = Part(rig.jet, PrimitiveType.Sphere, new Vector3(-0.15f, 1.05f, -0.28f), new Vector3(0.20f, 0.5f, 0.20f), Fx.MatGlow(GMC(0xFF, 0x8A, 0x3D)), "fl1");
            rig.flameR = Part(rig.jet, PrimitiveType.Sphere, new Vector3(0.15f, 1.05f, -0.28f), new Vector3(0.20f, 0.5f, 0.20f), Fx.MatGlow(GMC(0xFF, 0xD2, 0x3E)), "fl2");
            rig.jet.gameObject.SetActive(false);

            return rig;
        }

        public Transform handR;

        // ------------------------------------------------ INSPECTOR
        public static CharacterRig BuildInspector(Transform parent)
        {
            var root = new GameObject("Inspector");
            root.transform.SetParent(parent, false);
            var rig = root.AddComponent<CharacterRig>();
            rig.kind = "guard";

            var skin = Fx.Mat(GMC(0xFF, 0xC9, 0xA0));
            var uni = Fx.Mat(GMC(0x2E, 0x4A, 0x8F));
            var uniD = Fx.Mat(GMC(0x24, 0x3B, 0x74));
            var boots = Fx.Mat(GMC(0x22, 0x25, 0x2E));
            var grey = Fx.Mat(GMC(0x9A, 0xA0, 0xA8));
            var gold = Fx.MatGlow(GMC(0xFF, 0xD2, 0x3E));

            rig.body = Pivot(root.transform, "body", Vector3.zero);

            rig.legL = Pivot(rig.body, "legL", new Vector3(-0.17f, 0.85f, 0));
            Part(rig.legL, PrimitiveType.Capsule, new Vector3(0, -0.22f, 0), new Vector3(0.23f, 0.22f, 0.23f), uni, "thighL");
            Part(rig.legL, PrimitiveType.Cube, new Vector3(0, -0.44f, 0.05f), new Vector3(0.18f, 0.12f, 0.34f), boots, "bootL");
            rig.legR = Pivot(rig.body, "legR", new Vector3(0.17f, 0.85f, 0));
            Part(rig.legR, PrimitiveType.Capsule, new Vector3(0, -0.22f, 0), new Vector3(0.23f, 0.22f, 0.23f), uni, "thighR");
            Part(rig.legR, PrimitiveType.Cube, new Vector3(0, -0.44f, 0.05f), new Vector3(0.18f, 0.12f, 0.34f), boots, "bootR");

            rig.torso = Pivot(rig.body, "torso", new Vector3(0, 0.85f, 0));
            Part(rig.torso, PrimitiveType.Capsule, new Vector3(0, 0.32f, 0), new Vector3(0.62f, 0.30f, 0.44f), uni, "chest");
            Part(rig.torso, PrimitiveType.Sphere, new Vector3(0, 0.26f, 0.12f), new Vector3(0.40f, 0.34f, 0.30f), uni, "belly");
            Part(rig.torso, PrimitiveType.Cube, new Vector3(0, 0.10f, 0.17f), new Vector3(0.44f, 0.07f, 0.10f), uniD, "belt");
            Part(rig.torso, PrimitiveType.Sphere, new Vector3(-0.14f, 0.42f, 0.20f), new Vector3(0.07f, 0.07f, 0.03f), gold, "badge");

            rig.armL = Pivot(rig.body, "armL", new Vector3(-0.38f, 1.38f, 0));
            Part(rig.armL, PrimitiveType.Capsule, new Vector3(0, -0.20f, 0), new Vector3(0.18f, 0.15f, 0.18f), uni, "armuL");
            Part(rig.armL, PrimitiveType.Sphere, new Vector3(0, -0.40f, 0), new Vector3(0.17f, 0.17f, 0.17f), skin, "handL");
            rig.armR = Pivot(rig.body, "armR", new Vector3(0.38f, 1.38f, 0));
            Part(rig.armR, PrimitiveType.Capsule, new Vector3(0, -0.20f, 0), new Vector3(0.18f, 0.15f, 0.18f), uni, "armuR");
            Part(rig.armR, PrimitiveType.Sphere, new Vector3(0, -0.40f, 0), new Vector3(0.17f, 0.17f, 0.17f), skin, "handR");
            Part(rig.armR, PrimitiveType.Cylinder, new Vector3(0, -0.46f, 0.12f), new Vector3(0.05f, 0.16f, 0.05f), grey, "baton");

            rig.head = Pivot(rig.body, "head", new Vector3(0, 1.48f, 0));
            Part(rig.head, PrimitiveType.Sphere, new Vector3(0, 0.19f, 0), new Vector3(0.48f, 0.48f, 0.46f), skin, "skull");
            Part(rig.head, PrimitiveType.Cube, new Vector3(0, 0.08f, 0.20f), new Vector3(0.20f, 0.05f, 0.04f), grey, "mustache");
            Part(rig.head, PrimitiveType.Cylinder, new Vector3(0, 0.38f, 0), new Vector3(0.60f, 0.02f, 0.60f), uniD, "hatbrim");
            Part(rig.head, PrimitiveType.Cylinder, new Vector3(0, 0.47f, 0), new Vector3(0.40f, 0.09f, 0.40f), uniD, "hattop");
            Part(rig.head, PrimitiveType.Sphere, new Vector3(-0.085f, 0.20f, 0.20f), new Vector3(0.08f, 0.09f, 0.05f), Fx.Mat(Color.white), "eyeL");
            Part(rig.head, PrimitiveType.Sphere, new Vector3(0.085f, 0.20f, 0.20f), new Vector3(0.08f, 0.09f, 0.05f), Fx.Mat(Color.white), "eyeR");
            Part(rig.head, PrimitiveType.Sphere, new Vector3(-0.085f, 0.20f, 0.228f), new Vector3(0.04f, 0.045f, 0.03f), Fx.Mat(GMC(0x22, 0x2A, 0x3A)), "pupL");
            Part(rig.head, PrimitiveType.Sphere, new Vector3(0.085f, 0.20f, 0.228f), new Vector3(0.04f, 0.045f, 0.03f), Fx.Mat(GMC(0x22, 0x2A, 0x3A)), "pupR");
            return rig;
        }

        // ------------------------------------------------ DOG
        public static CharacterRig BuildDog(Transform parent)
        {
            var root = new GameObject("Dog");
            root.transform.SetParent(parent, false);
            root.transform.localScale = new Vector3(0.8f, 0.8f, 0.8f);
            var rig = root.AddComponent<CharacterRig>();
            rig.kind = "dog";

            var fur = Fx.Mat(GMC(0xD9, 0xB3, 0x80));
            var furD = Fx.Mat(GMC(0xB0, 0x8C, 0x5E));
            var dark = Fx.Mat(GMC(0x22, 0x2A, 0x3A));

            rig.body = Pivot(root.transform, "body", Vector3.zero);
            Part(rig.body, PrimitiveType.Cube, new Vector3(0, 0.42f, 0), new Vector3(0.30f, 0.28f, 0.64f), fur, "torso");
            Part(rig.body, PrimitiveType.Cube, new Vector3(0, 0.58f, 0.34f), new Vector3(0.26f, 0.24f, 0.24f), fur, "head");
            Part(rig.body, PrimitiveType.Cube, new Vector3(0, 0.54f, 0.50f), new Vector3(0.13f, 0.11f, 0.12f), furD, "snout");
            Part(rig.body, PrimitiveType.Cube, new Vector3(-0.09f, 0.72f, 0.32f), new Vector3(0.06f, 0.11f, 0.05f), furD, "earL");
            Part(rig.body, PrimitiveType.Cube, new Vector3(0.09f, 0.72f, 0.32f), new Vector3(0.06f, 0.11f, 0.05f), furD, "earR");
            Part(rig.body, PrimitiveType.Sphere, new Vector3(-0.07f, 0.60f, 0.47f), new Vector3(0.05f, 0.05f, 0.04f), dark, "eyeL");
            Part(rig.body, PrimitiveType.Sphere, new Vector3(0.07f, 0.60f, 0.47f), new Vector3(0.05f, 0.05f, 0.04f), dark, "eyeR");
            Part(rig.body, PrimitiveType.Cube, new Vector3(0, 0.44f, 0.26f), new Vector3(0.28f, 0.08f, 0.10f), Fx.Mat(GMC(0xE8, 0x4B, 0x4B)), "collar");

            rig.legL = Pivot(rig.body, "legFL", new Vector3(-0.10f, 0.32f, 0.24f));
            Part(rig.legL, PrimitiveType.Cube, new Vector3(0, -0.14f, 0), new Vector3(0.07f, 0.30f, 0.07f), fur, "flegL");
            rig.legR = Pivot(rig.body, "legFR", new Vector3(0.10f, 0.32f, 0.24f));
            Part(rig.legR, PrimitiveType.Cube, new Vector3(0, -0.14f, 0), new Vector3(0.07f, 0.30f, 0.07f), fur, "flegR");
            var bl = Pivot(rig.body, "legBL", new Vector3(-0.10f, 0.32f, -0.24f));
            Part(bl, PrimitiveType.Cube, new Vector3(0, -0.14f, 0), new Vector3(0.07f, 0.30f, 0.07f), fur, "blegL");
            var br = Pivot(rig.body, "legBR", new Vector3(0.10f, 0.32f, -0.24f));
            Part(br, PrimitiveType.Cube, new Vector3(0, -0.14f, 0), new Vector3(0.07f, 0.30f, 0.07f), fur, "blegR");

            rig.tail = Pivot(rig.body, "tail", new Vector3(0, 0.52f, -0.32f));
            Part(rig.tail, PrimitiveType.Cube, new Vector3(0, 0.06f, -0.10f), new Vector3(0.05f, 0.05f, 0.26f), fur, "tail");
            return rig;
        }

        static Color GMC(int r, int g, int b) { return new Color32((byte)r, (byte)g, (byte)b, 255); }

        // ================================================== POSE ENGINE
        public float phase;

        public void Pose(string mode, float t, float speedF)
        {
            if (body == null) return;
            float s = Mathf.Clamp01(speedF);

            // reset
            body.localScale = Vector3.one;
            body.localRotation = Quaternion.identity;
            if (legL != null) legL.localRotation = Quaternion.identity;
            if (legR != null) legR.localRotation = Quaternion.identity;
            if (armL != null) armL.localRotation = Quaternion.identity;
            if (armR != null) armR.localRotation = Quaternion.identity;
            if (head != null) head.localRotation = Quaternion.identity;
            if (torso != null) torso.localRotation = Quaternion.identity;
            if (tail != null) tail.localRotation = Quaternion.identity;

            if (kind == "dog")
            {
                float p = phase;
                legL.localRotation = Quaternion.Euler(Mathf.Sin(p) * 50f, 0, 0);
                legR.localRotation = Quaternion.Euler(Mathf.Sin(p + Mathf.PI) * 50f, 0, 0);
                body.localPosition = new Vector3(0, Mathf.Abs(Mathf.Sin(p)) * 0.04f, 0);
                if (tail != null) tail.localRotation = Quaternion.Euler(0, Mathf.Sin(p * 2.2f) * 28f, 30f);
                return;
            }

            switch (mode)
            {
                case "idle":
                {
                    float b = Mathf.Sin(t * 2.2f);
                    body.localPosition = new Vector3(0, b * 0.012f, 0);
                    armL.localRotation = Quaternion.Euler(0, 0, 6f + b * 3f);
                    armR.localRotation = Quaternion.Euler(-30f + Mathf.Sin(t * 3.1f) * 8f, 0, -12f);
                    head.localRotation = Quaternion.Euler(Mathf.Sin(t * 1.3f) * 4f, Mathf.Sin(t * 0.7f) * 10f, 0);
                    if (bag != null) bag.gameObject.SetActive(true);
                    break;
                }
                case "spray":
                {
                    float b = Mathf.Sin(t * 2.0f);
                    body.localPosition = new Vector3(0, b * 0.010f, 0);
                    armR.localRotation = Quaternion.Euler(-95f + Mathf.Sin(t * 9f) * 6f, 0, -6f);
                    armL.localRotation = Quaternion.Euler(0, 0, 10f);
                    head.localRotation = Quaternion.Euler(-6f, -18f, 0);
                    if (bag != null) bag.gameObject.SetActive(true);
                    break;
                }
                case "run":
                {
                    float p = phase;
                    float sw = Mathf.Sin(p), sw2 = Mathf.Sin(p + Mathf.PI);
                    legL.localRotation = Quaternion.Euler(sw * 58f * (0.5f + 0.5f * s), 0, 0);
                    legR.localRotation = Quaternion.Euler(sw2 * 58f * (0.5f + 0.5f * s), 0, 0);
                    armL.localRotation = Quaternion.Euler(sw2 * 44f * (0.5f + 0.5f * s), 0, 6f);
                    armR.localRotation = Quaternion.Euler(sw * 44f * (0.5f + 0.5f * s), 0, -6f);
                    body.localPosition = new Vector3(0, Mathf.Abs(Mathf.Sin(p)) * 0.05f * s, 0);
                    body.localRotation = Quaternion.Euler(4f + s * 4f, 0, 0);
                    head.localRotation = Quaternion.Euler(-6f, 0, 0);
                    if (bag != null) bag.gameObject.SetActive(false);
                    break;
                }
                case "jump":
                {
                    legL.localRotation = Quaternion.Euler(-42f, 0, 8f);
                    legR.localRotation = Quaternion.Euler(18f, 0, -8f);
                    armL.localRotation = Quaternion.Euler(-120f, 0, 14f);
                    armR.localRotation = Quaternion.Euler(-140f, 0, -14f);
                    body.localRotation = Quaternion.Euler(-6f, 0, 0);
                    break;
                }
                case "roll":
                {
                    body.localScale = new Vector3(1f, 0.62f, 1f);
                    body.localPosition = new Vector3(0, -0.06f, 0);
                    body.localRotation = Quaternion.Euler(-phase * 620f, 0, 0);
                    legL.localRotation = Quaternion.Euler(-70f, 0, 0);
                    legR.localRotation = Quaternion.Euler(-70f, 0, 0);
                    armL.localRotation = Quaternion.Euler(-90f, 0, 0);
                    armR.localRotation = Quaternion.Euler(-90f, 0, 0);
                    break;
                }
                case "board":
                {
                    float p = phase * 0.5f;
                    legL.localRotation = Quaternion.Euler(-24f + Mathf.Sin(p) * 5f, 0, 12f);
                    legR.localRotation = Quaternion.Euler(20f, 0, -12f);
                    armL.localRotation = Quaternion.Euler(0, 0, 42f);
                    armR.localRotation = Quaternion.Euler(0, 0, -42f);
                    body.localRotation = Quaternion.Euler(0, 0, Mathf.Sin(p) * 4f);
                    break;
                }
                case "jet":
                {
                    float p = phase * 0.35f;
                    legL.localRotation = Quaternion.Euler(12f + Mathf.Sin(p) * 10f, 0, 4f);
                    legR.localRotation = Quaternion.Euler(16f + Mathf.Sin(p + 1f) * 10f, 0, -4f);
                    armL.localRotation = Quaternion.Euler(-40f, 0, 18f);
                    armR.localRotation = Quaternion.Euler(-40f, 0, -18f);
                    body.localRotation = Quaternion.Euler(-8f, 0, 0);
                    if (flameL != null) flameL.localScale = new Vector3(0.2f, 0.35f + Mathf.Abs(Mathf.Sin(t * 30f)) * 0.3f, 0.2f);
                    if (flameR != null) flameR.localScale = new Vector3(0.2f, 0.35f + Mathf.Abs(Mathf.Cos(t * 28f)) * 0.3f, 0.2f);
                    break;
                }
                case "stumble":
                {
                    float p = phase;
                    body.localRotation = Quaternion.Euler(14f, 0, Mathf.Sin(p * 2f) * 16f);
                    legL.localRotation = Quaternion.Euler(30f, 0, 0);
                    legR.localRotation = Quaternion.Euler(-20f, 0, 0);
                    armL.localRotation = Quaternion.Euler(Mathf.Sin(p * 6f) * 80f - 40f, 0, 30f);
                    armR.localRotation = Quaternion.Euler(Mathf.Sin(p * 6f + 2f) * 80f - 40f, 0, -30f);
                    break;
                }
                case "dead":
                {
                    body.localRotation = Quaternion.Euler(-84f, 0, 0);
                    body.localPosition = new Vector3(0, -0.25f, 0.3f);
                    legL.localRotation = Quaternion.Euler(20f, 0, 10f);
                    legR.localRotation = Quaternion.Euler(-10f, 0, -14f);
                    armL.localRotation = Quaternion.Euler(-30f, 0, 40f);
                    armR.localRotation = Quaternion.Euler(-20f, 0, -40f);
                    break;
                }
                case "grab":
                {
                    float reach = Mathf.Clamp01(t * 3f);
                    armL.localRotation = Quaternion.Euler(-110f * reach, 0, 12f);
                    armR.localRotation = Quaternion.Euler(-125f * reach, 0, -12f);
                    legL.localRotation = Quaternion.Euler(Mathf.Sin(phase) * 40f, 0, 0);
                    legR.localRotation = Quaternion.Euler(Mathf.Sin(phase + Mathf.PI) * 40f, 0, 0);
                    break;
                }
            }
        }

        public void SetBoard(bool on)
        {
            if (board != null) board.gameObject.SetActive(on);
        }

        public void SetJet(bool on)
        {
            if (jet != null) jet.gameObject.SetActive(on);
        }
    }
}
