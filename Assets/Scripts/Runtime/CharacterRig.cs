using UnityEngine;

namespace DummySurfer
{
    /// <summary>Procedural chibi cartoon characters (boy runner, inspector, dog) + pose engine.
    /// Boy: big head, backwards cap, cream hoodie, spray-tank backpack, chunky sneakers.
    /// Articulated: hips, knees, shoulders, elbows — expressive SS-style run cycle.</summary>
    public class CharacterRig : MonoBehaviour
    {
        public Transform body, head, armL, armR, legL, legR, torso, tail, board, jet, flameL, flameR, bag;
        public Transform kneeL, kneeR, elbL, elbR;
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
            mr.receiveShadows = true;
            return go;
        }

        static Transform Pivot(Transform parent, string name, Vector3 pos)
        {
            var go = new GameObject(name);
            go.transform.SetParent(parent, false);
            go.transform.localPosition = pos;
            return go.transform;
        }

        static Color GMC(int r, int g, int b) { return new Color32((byte)r, (byte)g, (byte)b, 255); }

        // ------------------------------------------------ BOY
        public static CharacterRig BuildBoy(Transform parent)
        {
            var root = new GameObject("Boy");
            root.transform.SetParent(parent, false);
            var rig = root.AddComponent<CharacterRig>();
            rig.kind = "boy";

            var skin = Fx.Mat(GMC(0xFF, 0xC9, 0xA0));
            var hoodie = Fx.Mat(GMC(0xF5, 0xF1, 0xE4));
            var hoodieD = Fx.Mat(GMC(0xE4, 0xDE, 0xCE));
            var tee = Fx.Mat(GMC(0x2E, 0x4A, 0x8F));
            var jeans = Fx.Mat(GMC(0x3E, 0x55, 0x78));
            var jeansD = Fx.Mat(GMC(0x34, 0x48, 0x66));
            var capM = Fx.Mat(GMC(0xE8, 0x40, 0x40));
            var capW = Fx.Mat(GMC(0xFA, 0xFA, 0xFA));
            var hairM = Fx.Mat(GMC(0x6B, 0x4A, 0x2F));
            var white = Fx.Mat(GMC(0xFC, 0xFC, 0xFC));
            var red = Fx.Mat(GMC(0xE8, 0x40, 0x40));
            var pack = Fx.Mat(GMC(0xC8, 0xCD, 0xD6));
            var packD = Fx.Mat(GMC(0x9E, 0xA5, 0xB0));
            var dark = Fx.Mat(GMC(0x26, 0x2C, 0x3C));
            var smile = Fx.Mat(GMC(0x7A, 0x3A, 0x2A));

            rig.body = Pivot(root.transform, "body", Vector3.zero);

            // ============================ LEGS (hip → knee → shoe)
            for (int s = 0; s < 2; s++)
            {
                float sx = s == 0 ? -1f : 1f;
                var hip = Pivot(rig.body, "leg" + s, new Vector3(sx * 0.13f, 0.80f, 0));
                if (s == 0) rig.legL = hip; else rig.legR = hip;
                Part(hip, PrimitiveType.Capsule, new Vector3(0, -0.17f, 0), new Vector3(0.21f, 0.14f, 0.21f), jeans, "thigh");
                var knee = Pivot(hip, "knee", new Vector3(0, -0.35f, 0));
                if (s == 0) rig.kneeL = knee; else rig.kneeR = knee;
                Part(knee, PrimitiveType.Capsule, new Vector3(0, -0.14f, 0), new Vector3(0.18f, 0.12f, 0.18f), jeansD, "shin");
                // chunky sneaker
                Part(knee, PrimitiveType.Cube, new Vector3(0, -0.29f, 0.07f), new Vector3(0.18f, 0.11f, 0.35f), white, "shoe");
                Part(knee, PrimitiveType.Cube, new Vector3(0, -0.25f, 0.07f), new Vector3(0.19f, 0.05f, 0.30f), red, "stripe");
                Part(knee, PrimitiveType.Cube, new Vector3(0, -0.345f, 0.07f), new Vector3(0.19f, 0.05f, 0.37f), white, "sole");
            }

            // ============================ TORSO
            rig.torso = Pivot(rig.body, "torso", new Vector3(0, 0.82f, 0));
            Part(rig.torso, PrimitiveType.Capsule, new Vector3(0, 0.27f, 0), new Vector3(0.48f, 0.30f, 0.34f), hoodie, "chest");
            Part(rig.torso, PrimitiveType.Sphere, new Vector3(0, 0.12f, 0.10f), new Vector3(0.36f, 0.28f, 0.26f), hoodie, "belly");
            // navy tee at collar + hood bump
            Part(rig.torso, PrimitiveType.Cube, new Vector3(0, 0.47f, 0.12f), new Vector3(0.22f, 0.07f, 0.07f), tee, "collar");
            Part(rig.torso, PrimitiveType.Sphere, new Vector3(0, 0.42f, -0.16f), new Vector3(0.24f, 0.20f, 0.14f), hoodieD, "hood");
            // front pocket + strings
            Part(rig.torso, PrimitiveType.Cube, new Vector3(0, 0.15f, 0.175f), new Vector3(0.24f, 0.13f, 0.03f), hoodieD, "pocket");
            Part(rig.torso, PrimitiveType.Cylinder, new Vector3(-0.05f, 0.40f, 0.165f), new Vector3(0.03f, 0.05f, 0.03f), white, "strL");
            Part(rig.torso, PrimitiveType.Cylinder, new Vector3(0.05f, 0.40f, 0.165f), new Vector3(0.03f, 0.05f, 0.03f), white, "strR");

            // ============================ BACKPACK (hero piece — seen from behind)
            var packRoot = Pivot(rig.torso, "pack", new Vector3(0, 0.26f, -0.28f));
            Part(packRoot, PrimitiveType.Cube, Vector3.zero, new Vector3(0.40f, 0.46f, 0.20f), pack, "packBody");
            Part(packRoot, PrimitiveType.Cube, new Vector3(0, 0.14f, -0.11f), new Vector3(0.32f, 0.14f, 0.02f), packD, "pocket2");
            Part(packRoot, PrimitiveType.Cube, new Vector3(0, 0.20f, 0.0f), new Vector3(0.42f, 0.05f, 0.22f), packD, "lid");
            // spray cans sticking out
            var canR = Fx.Mat(GMC(0xE8, 0x40, 0x40));
            var canT = Fx.Mat(GMC(0x2F, 0xB8, 0xB0));
            var canO = Fx.Mat(GMC(0xFF, 0x8A, 0x3D));
            var c1 = Part(packRoot, PrimitiveType.Cylinder, new Vector3(-0.10f, 0.30f, 0.0f), new Vector3(0.11f, 0.07f, 0.11f), canR, "can1");
            c1.transform.localRotation = Quaternion.Euler(8f, 0, -6f);
            var c2 = Part(packRoot, PrimitiveType.Cylinder, new Vector3(0.02f, 0.32f, -0.02f), new Vector3(0.11f, 0.07f, 0.11f), canT, "can2");
            c2.transform.localRotation = Quaternion.Euler(-6f, 0, 4f);
            var c3 = Part(packRoot, PrimitiveType.Cylinder, new Vector3(0.13f, 0.29f, 0.02f), new Vector3(0.10f, 0.06f, 0.10f), canO, "can3");
            c3.transform.localRotation = Quaternion.Euler(14f, 0, 8f);
            Part(packRoot, PrimitiveType.Sphere, new Vector3(-0.10f, 0.375f, 0.0f), new Vector3(0.05f, 0.04f, 0.05f), white, "cap1");
            Part(packRoot, PrimitiveType.Sphere, new Vector3(0.02f, 0.395f, -0.02f), new Vector3(0.05f, 0.04f, 0.05f), white, "cap2");
            // shoulder straps
            Part(rig.torso, PrimitiveType.Cube, new Vector3(-0.14f, 0.30f, 0.155f), new Vector3(0.08f, 0.34f, 0.035f), dark, "strapL");
            Part(rig.torso, PrimitiveType.Cube, new Vector3(0.14f, 0.30f, 0.155f), new Vector3(0.08f, 0.34f, 0.035f), dark, "strapR");

            // ============================ ARMS (shoulder → elbow → hand)
            for (int s = 0; s < 2; s++)
            {
                float sx = s == 0 ? -1f : 1f;
                var sh = Pivot(rig.body, "arm" + s, new Vector3(sx * 0.30f, 1.34f, 0));
                if (s == 0) rig.armL = sh; else rig.armR = sh;
                Part(sh, PrimitiveType.Capsule, new Vector3(0, -0.13f, 0), new Vector3(0.15f, 0.11f, 0.15f), hoodie, "armu");
                var elb = Pivot(sh, "elbow", new Vector3(0, -0.26f, 0));
                if (s == 0) rig.elbL = elb; else rig.elbR = elb;
                Part(elb, PrimitiveType.Capsule, new Vector3(0, -0.10f, 0), new Vector3(0.125f, 0.09f, 0.125f), hoodieD, "forearm");
                Part(elb, PrimitiveType.Sphere, new Vector3(0, -0.21f, 0), new Vector3(0.14f, 0.14f, 0.14f), skin, "hand");
                if (s == 1)
                {
                    // spray can in right hand (menu pose)
                    rig.bag = Part(elb, PrimitiveType.Cylinder, new Vector3(0, -0.27f, 0.05f), new Vector3(0.10f, 0.09f, 0.10f), canO, "spraycan").transform;
                    rig.bag.gameObject.SetActive(false);
                }
            }

            // ============================ HEAD (big, chibi)
            rig.head = Pivot(rig.body, "head", new Vector3(0, 1.46f, 0));
            Part(rig.head, PrimitiveType.Sphere, new Vector3(0, 0.17f, 0), new Vector3(0.50f, 0.47f, 0.47f), skin, "skull");
            // ears
            Part(rig.head, PrimitiveType.Sphere, new Vector3(-0.235f, 0.14f, 0.0f), new Vector3(0.075f, 0.10f, 0.075f), skin, "earL");
            Part(rig.head, PrimitiveType.Sphere, new Vector3(0.235f, 0.14f, 0.0f), new Vector3(0.075f, 0.10f, 0.075f), skin, "earR");
            // hair: back mass + fringe
            Part(rig.head, PrimitiveType.Sphere, new Vector3(0, 0.22f, -0.10f), new Vector3(0.52f, 0.38f, 0.44f), hairM, "hairBack");
            Part(rig.head, PrimitiveType.Cube, new Vector3(0, 0.30f, 0.185f), new Vector3(0.34f, 0.07f, 0.10f), hairM, "fringe");
            // backwards cap: dome + back brim + white front panel
            Part(rig.head, PrimitiveType.Sphere, new Vector3(0, 0.315f, 0.0f), new Vector3(0.52f, 0.32f, 0.52f), capM, "cap");
            var brim = Part(rig.head, PrimitiveType.Cube, new Vector3(0, 0.30f, -0.28f), new Vector3(0.30f, 0.04f, 0.22f), capM, "brimBack");
            brim.transform.localRotation = Quaternion.Euler(-14f, 0, 0);
            Part(rig.head, PrimitiveType.Cube, new Vector3(0, 0.30f, 0.235f), new Vector3(0.22f, 0.11f, 0.03f), capW, "panel");
            // face (visible in menu / death cam)
            Part(rig.head, PrimitiveType.Sphere, new Vector3(-0.10f, 0.16f, 0.205f), new Vector3(0.10f, 0.115f, 0.05f), white, "eyeW");
            Part(rig.head, PrimitiveType.Sphere, new Vector3(0.10f, 0.16f, 0.205f), new Vector3(0.10f, 0.115f, 0.05f), white, "eyeW2");
            Part(rig.head, PrimitiveType.Sphere, new Vector3(-0.10f, 0.155f, 0.243f), new Vector3(0.05f, 0.055f, 0.03f), dark, "pupilL");
            Part(rig.head, PrimitiveType.Sphere, new Vector3(0.10f, 0.155f, 0.243f), new Vector3(0.05f, 0.055f, 0.03f), dark, "pupilR");
            Part(rig.head, PrimitiveType.Cube, new Vector3(-0.10f, 0.265f, 0.225f), new Vector3(0.11f, 0.028f, 0.02f), hairM, "browL");
            Part(rig.head, PrimitiveType.Cube, new Vector3(0.10f, 0.265f, 0.225f), new Vector3(0.11f, 0.028f, 0.02f), hairM, "browR");
            var mouth = Part(rig.head, PrimitiveType.Cube, new Vector3(0, 0.045f, 0.225f), new Vector3(0.13f, 0.035f, 0.02f), smile, "smile");
            mouth.transform.localRotation = Quaternion.Euler(0, 0, 0);

            // hoverboard (hidden by default)
            rig.board = new GameObject("board").transform;
            rig.board.SetParent(root.transform, false);
            rig.board.localPosition = new Vector3(0, 0.12f, 0);
            Part(rig.board, PrimitiveType.Cube, Vector3.zero, new Vector3(0.92f, 0.08f, 0.46f), Fx.Mat(GMC(0xFF, 0xD2, 0x3E)), "deck");
            Part(rig.board, PrimitiveType.Cube, new Vector3(0, -0.025f, 0), new Vector3(0.84f, 0.05f, 0.38f), Fx.Mat(GMC(0xFF, 0x6B, 0x8E)), "stripe");
            Part(rig.board, PrimitiveType.Cube, new Vector3(-0.34f, 0.055f, 0), new Vector3(0.22f, 0.05f, 0.40f), capM, "noseT");
            Part(rig.board, PrimitiveType.Cube, new Vector3(0.34f, 0.055f, 0), new Vector3(0.22f, 0.05f, 0.40f), capM, "noseB");
            rig.board.gameObject.SetActive(false);

            // jetpack (hidden)
            rig.jet = new GameObject("jet").transform;
            rig.jet.SetParent(rig.body, false);
            Part(rig.jet, PrimitiveType.Cylinder, new Vector3(-0.16f, 1.24f, -0.26f), new Vector3(0.18f, 0.17f, 0.18f), pack, "tank1");
            Part(rig.jet, PrimitiveType.Cylinder, new Vector3(0.16f, 1.24f, -0.26f), new Vector3(0.18f, 0.17f, 0.18f), pack, "tank2");
            Part(rig.jet, PrimitiveType.Cylinder, new Vector3(-0.16f, 1.42f, -0.26f), new Vector3(0.08f, 0.04f, 0.08f), dark, "n1");
            Part(rig.jet, PrimitiveType.Cylinder, new Vector3(0.16f, 1.42f, -0.26f), new Vector3(0.08f, 0.04f, 0.08f), dark, "n2");
            rig.flameL = Part(rig.jet, PrimitiveType.Sphere, new Vector3(-0.16f, 1.02f, -0.26f), new Vector3(0.20f, 0.5f, 0.20f), Fx.MatGlow(GMC(0xFF, 0x8A, 0x3D)), "fl1").transform;
            rig.flameR = Part(rig.jet, PrimitiveType.Sphere, new Vector3(0.16f, 1.02f, -0.26f), new Vector3(0.20f, 0.5f, 0.20f), Fx.MatGlow(GMC(0xFF, 0xD2, 0x3E)), "fl2").transform;
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
            var jacket = Fx.Mat(GMC(0x7A, 0x8A, 0x46));   // olive jacket (Ref 08)
            var jacketD = Fx.Mat(GMC(0x64, 0x72, 0x3A));
            var pants = Fx.Mat(GMC(0x4A, 0x55, 0x68));
            var boots = Fx.Mat(GMC(0x26, 0x29, 0x32));
            var capB = Fx.Mat(GMC(0x3E, 0x6F, 0xE0));     // blue cap
            var capD = Fx.Mat(GMC(0x2A, 0x4A, 0xA0));
            var grey = Fx.Mat(GMC(0x9A, 0xA0, 0xA8));
            var gold = Fx.MatGlow(GMC(0xFF, 0xD2, 0x3E));
            var dark = Fx.Mat(GMC(0x26, 0x2C, 0x3C));

            rig.body = Pivot(root.transform, "body", Vector3.zero);

            rig.legL = Pivot(rig.body, "legL", new Vector3(-0.17f, 0.82f, 0));
            Part(rig.legL, PrimitiveType.Capsule, new Vector3(0, -0.20f, 0), new Vector3(0.24f, 0.18f, 0.24f), pants, "thighL");
            Part(rig.legL, PrimitiveType.Cube, new Vector3(0, -0.42f, 0.05f), new Vector3(0.19f, 0.13f, 0.36f), boots, "bootL");
            rig.legR = Pivot(rig.body, "legR", new Vector3(0.17f, 0.82f, 0));
            Part(rig.legR, PrimitiveType.Capsule, new Vector3(0, -0.20f, 0), new Vector3(0.24f, 0.18f, 0.24f), pants, "thighR");
            Part(rig.legR, PrimitiveType.Cube, new Vector3(0, -0.42f, 0.05f), new Vector3(0.19f, 0.13f, 0.36f), boots, "bootR");

            rig.torso = Pivot(rig.body, "torso", new Vector3(0, 0.82f, 0));
            Part(rig.torso, PrimitiveType.Capsule, new Vector3(0, 0.30f, 0), new Vector3(0.64f, 0.30f, 0.46f), jacket, "chest");
            Part(rig.torso, PrimitiveType.Sphere, new Vector3(0, 0.22f, 0.10f), new Vector3(0.46f, 0.40f, 0.34f), jacket, "belly");
            Part(rig.torso, PrimitiveType.Cube, new Vector3(0, 0.04f, 0.19f), new Vector3(0.48f, 0.08f, 0.10f), dark, "belt");
            Part(rig.torso, PrimitiveType.Sphere, new Vector3(-0.15f, 0.40f, 0.22f), new Vector3(0.08f, 0.08f, 0.035f), gold, "badge");
            Part(rig.torso, PrimitiveType.Cube, new Vector3(0.13f, 0.34f, 0.23f), new Vector3(0.09f, 0.12f, 0.02f), jacketD, "pocket");

            rig.armL = Pivot(rig.body, "armL", new Vector3(-0.40f, 1.36f, 0));
            Part(rig.armL, PrimitiveType.Capsule, new Vector3(0, -0.19f, 0), new Vector3(0.19f, 0.13f, 0.19f), jacket, "armuL");
            Part(rig.armL, PrimitiveType.Sphere, new Vector3(0, -0.39f, 0), new Vector3(0.17f, 0.17f, 0.17f), skin, "handL");
            rig.armR = Pivot(rig.body, "armR", new Vector3(0.40f, 1.36f, 0));
            Part(rig.armR, PrimitiveType.Capsule, new Vector3(0, -0.19f, 0), new Vector3(0.19f, 0.13f, 0.19f), jacket, "armuR");
            Part(rig.armR, PrimitiveType.Sphere, new Vector3(0, -0.39f, 0), new Vector3(0.17f, 0.17f, 0.17f), skin, "handR");
            Part(rig.armR, PrimitiveType.Cylinder, new Vector3(0, -0.47f, 0.11f), new Vector3(0.05f, 0.16f, 0.05f), grey, "baton");

            rig.head = Pivot(rig.body, "head", new Vector3(0, 1.50f, 0));
            Part(rig.head, PrimitiveType.Sphere, new Vector3(0, 0.18f, 0), new Vector3(0.46f, 0.44f, 0.44f), skin, "skull");
            // big white mustache
            Part(rig.head, PrimitiveType.Cube, new Vector3(0, 0.07f, 0.21f), new Vector3(0.24f, 0.06f, 0.05f), white, "mustache");
            Part(rig.head, PrimitiveType.Sphere, new Vector3(-0.10f, 0.06f, 0.20f), new Vector3(0.06f, 0.05f, 0.04f), white, "moL");
            Part(rig.head, PrimitiveType.Sphere, new Vector3(0.10f, 0.06f, 0.20f), new Vector3(0.06f, 0.05f, 0.04f), white, "moR");
            // blue cap with dark peak
            Part(rig.head, PrimitiveType.Cylinder, new Vector3(0, 0.33f, 0), new Vector3(0.48f, 0.10f, 0.48f), capB, "capTop");
            var peak = Part(rig.head, PrimitiveType.Cube, new Vector3(0, 0.28f, 0.26f), new Vector3(0.34f, 0.045f, 0.20f), capD, "peak");
            peak.transform.localRotation = Quaternion.Euler(-8f, 0, 0);
            Part(rig.head, PrimitiveType.Sphere, new Vector3(-0.085f, 0.17f, 0.20f), new Vector3(0.085f, 0.095f, 0.05f), white, "eyeL");
            Part(rig.head, PrimitiveType.Sphere, new Vector3(0.085f, 0.17f, 0.20f), new Vector3(0.085f, 0.095f, 0.05f), white, "eyeR");
            Part(rig.head, PrimitiveType.Sphere, new Vector3(-0.085f, 0.165f, 0.235f), new Vector3(0.042f, 0.05f, 0.03f), dark, "pupL");
            Part(rig.head, PrimitiveType.Sphere, new Vector3(0.085f, 0.165f, 0.235f), new Vector3(0.042f, 0.05f, 0.03f), dark, "pupR");
            // angry brows
            Part(rig.head, PrimitiveType.Cube, new Vector3(-0.09f, 0.245f, 0.215f), new Vector3(0.11f, 0.03f, 0.02f), grey, "browL");
            Part(rig.head, PrimitiveType.Cube, new Vector3(0.09f, 0.245f, 0.215f), new Vector3(0.11f, 0.03f, 0.02f), grey, "browR");
            return rig;
        }

        // ------------------------------------------------ DOG
        public static CharacterRig BuildDog(Transform parent)
        {
            var root = new GameObject("Dog");
            root.transform.SetParent(parent, false);
            root.transform.localScale = new Vector3(0.85f, 0.85f, 0.85f);
            var rig = root.AddComponent<CharacterRig>();
            rig.kind = "dog";

            var fur = Fx.Mat(GMC(0xD9, 0xB3, 0x80));
            var furD = Fx.Mat(GMC(0xB0, 0x8C, 0x5E));
            var dark = Fx.Mat(GMC(0x26, 0x2C, 0x3C));
            var collar = Fx.Mat(GMC(0xE8, 0x40, 0x40));

            rig.body = Pivot(root.transform, "body", Vector3.zero);
            Part(rig.body, PrimitiveType.Cube, new Vector3(0, 0.42f, 0), new Vector3(0.30f, 0.28f, 0.64f), fur, "torso");
            Part(rig.body, PrimitiveType.Cube, new Vector3(0, 0.58f, 0.34f), new Vector3(0.26f, 0.24f, 0.24f), fur, "head");
            Part(rig.body, PrimitiveType.Cube, new Vector3(0, 0.54f, 0.50f), new Vector3(0.13f, 0.11f, 0.12f), furD, "snout");
            Part(rig.body, PrimitiveType.Sphere, new Vector3(0, 0.58f, 0.545f), new Vector3(0.05f, 0.045f, 0.04f), dark, "nose");
            Part(rig.body, PrimitiveType.Cube, new Vector3(-0.09f, 0.72f, 0.32f), new Vector3(0.06f, 0.11f, 0.05f), furD, "earL");
            Part(rig.body, PrimitiveType.Cube, new Vector3(0.09f, 0.72f, 0.32f), new Vector3(0.06f, 0.11f, 0.05f), furD, "earR");
            Part(rig.body, PrimitiveType.Sphere, new Vector3(-0.07f, 0.60f, 0.47f), new Vector3(0.05f, 0.05f, 0.04f), dark, "eyeL");
            Part(rig.body, PrimitiveType.Sphere, new Vector3(0.07f, 0.60f, 0.47f), new Vector3(0.05f, 0.05f, 0.04f), dark, "eyeR");
            Part(rig.body, PrimitiveType.Cube, new Vector3(0, 0.44f, 0.26f), new Vector3(0.28f, 0.08f, 0.10f), collar, "collar");
            Part(rig.body, PrimitiveType.Sphere, new Vector3(0, 0.40f, 0.26f), new Vector3(0.05f, 0.05f, 0.04f), goldDog(), "tag");

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

        static Material _goldDog;
        static Material goldDog() { if (_goldDog == null) _goldDog = Fx.MatGlow(GMC(0xFF, 0xD2, 0x3E)); return _goldDog; }

        // ================================================== POSE ENGINE
        public float phase;

        void ZeroPose()
        {
            body.localScale = Vector3.one;
            body.localRotation = Quaternion.identity;
            if (legL != null) legL.localRotation = Quaternion.identity;
            if (legR != null) legR.localRotation = Quaternion.identity;
            if (kneeL != null) kneeL.localRotation = Quaternion.identity;
            if (kneeR != null) kneeR.localRotation = Quaternion.identity;
            if (armL != null) armL.localRotation = Quaternion.identity;
            if (armR != null) armR.localRotation = Quaternion.identity;
            if (elbL != null) elbL.localRotation = Quaternion.identity;
            if (elbR != null) elbR.localRotation = Quaternion.identity;
            if (head != null) head.localRotation = Quaternion.identity;
            if (torso != null) torso.localRotation = Quaternion.identity;
            if (tail != null) tail.localRotation = Quaternion.identity;
        }

        public void Pose(string mode, float t, float speedF)
        {
            if (body == null) return;
            float s = Mathf.Clamp01(speedF);
            ZeroPose();

            if (kind == "dog")
            {
                float p = phase;
                legL.localRotation = Quaternion.Euler(Mathf.Sin(p) * 52f, 0, 0);
                legR.localRotation = Quaternion.Euler(Mathf.Sin(p + Mathf.PI) * 52f, 0, 0);
                body.localPosition = new Vector3(0, Mathf.Abs(Mathf.Sin(p)) * 0.045f, 0);
                if (tail != null) tail.localRotation = Quaternion.Euler(0, Mathf.Sin(p * 2.2f) * 28f, 30f);
                return;
            }

            switch (mode)
            {
                case "idle":
                {
                    float b = Mathf.Sin(t * 2.2f);
                    body.localPosition = new Vector3(0, b * 0.012f, 0);
                    armL.localRotation = Quaternion.Euler(0, 0, 7f + b * 3f);
                    armR.localRotation = Quaternion.Euler(-28f + Mathf.Sin(t * 3.1f) * 8f, 0, -13f);
                    if (elbR != null) elbR.localRotation = Quaternion.Euler(-55f, 0, 0);
                    head.localRotation = Quaternion.Euler(Mathf.Sin(t * 1.3f) * 4f, Mathf.Sin(t * 0.7f) * 10f, 0);
                    if (bag != null) bag.gameObject.SetActive(true);
                    break;
                }
                case "spray":
                {
                    float b = Mathf.Sin(t * 2.0f);
                    body.localPosition = new Vector3(0, b * 0.010f, 0);
                    armR.localRotation = Quaternion.Euler(-96f + Mathf.Sin(t * 9f) * 6f, 0, -8f);
                    if (elbR != null) elbR.localRotation = Quaternion.Euler(-18f, 0, 0);
                    armL.localRotation = Quaternion.Euler(0, 0, 11f);
                    head.localRotation = Quaternion.Euler(-6f, -16f, 0);
                    if (bag != null) bag.gameObject.SetActive(true);
                    break;
                }
                case "run":
                {
                    float p = phase;
                    float amp = 0.5f + 0.5f * s;
                    float hipL = Mathf.Sin(p) * 62f * amp;
                    float hipR = Mathf.Sin(p + Mathf.PI) * 62f * amp;
                    legL.localRotation = Quaternion.Euler(hipL, 0, 0);
                    legR.localRotation = Quaternion.Euler(hipR, 0, 0);
                    // knee bends when the leg swings back / lifts
                    float kbL = Mathf.Clamp01(-Mathf.Sin(p - 0.6f)) * 95f * amp;
                    float kbR = Mathf.Clamp01(-Mathf.Sin(p + Mathf.PI - 0.6f)) * 95f * amp;
                    if (kneeL != null) kneeL.localRotation = Quaternion.Euler(kbL, 0, 0);
                    if (kneeR != null) kneeR.localRotation = Quaternion.Euler(kbR, 0, 0);
                    // arms opposite
                    float shL = Mathf.Sin(p + Mathf.PI) * 50f * amp;
                    float shR = Mathf.Sin(p) * 50f * amp;
                    armL.localRotation = Quaternion.Euler(shL, 0, 6f);
                    armR.localRotation = Quaternion.Euler(shR, 0, -6f);
                    if (elbL != null) elbL.localRotation = Quaternion.Euler(-70f - Mathf.Abs(Mathf.Sin(p + Mathf.PI)) * 25f, 0, 0);
                    if (elbR != null) elbR.localRotation = Quaternion.Euler(-70f - Mathf.Abs(Mathf.Sin(p)) * 25f, 0, 0);
                    body.localPosition = new Vector3(0, Mathf.Abs(Mathf.Sin(p)) * 0.055f * s, 0);
                    body.localRotation = Quaternion.Euler(4f + s * 6f, 0, 0);
                    head.localRotation = Quaternion.Euler(-7f, 0, 0);
                    if (bag != null) bag.gameObject.SetActive(false);
                    break;
                }
                case "jump":
                {
                    legL.localRotation = Quaternion.Euler(-48f, 0, 10f);
                    legR.localRotation = Quaternion.Euler(22f, 0, -10f);
                    if (kneeL != null) kneeL.localRotation = Quaternion.Euler(78f, 0, 0);
                    if (kneeR != null) kneeR.localRotation = Quaternion.Euler(38f, 0, 0);
                    armL.localRotation = Quaternion.Euler(-128f, 0, 16f);
                    armR.localRotation = Quaternion.Euler(-148f, 0, -16f);
                    if (elbL != null) elbL.localRotation = Quaternion.Euler(-40f, 0, 0);
                    if (elbR != null) elbR.localRotation = Quaternion.Euler(-40f, 0, 0);
                    body.localRotation = Quaternion.Euler(-6f, 0, 0);
                    break;
                }
                case "roll":
                {
                    body.localScale = new Vector3(1f, 0.60f, 1f);
                    body.localPosition = new Vector3(0, -0.08f, 0);
                    body.localRotation = Quaternion.Euler(-phase * 620f, 0, 0);
                    legL.localRotation = Quaternion.Euler(-78f, 0, 0);
                    legR.localRotation = Quaternion.Euler(-78f, 0, 0);
                    if (kneeL != null) kneeL.localRotation = Quaternion.Euler(105f, 0, 0);
                    if (kneeR != null) kneeR.localRotation = Quaternion.Euler(105f, 0, 0);
                    armL.localRotation = Quaternion.Euler(-95f, 0, 0);
                    armR.localRotation = Quaternion.Euler(-95f, 0, 0);
                    if (elbL != null) elbL.localRotation = Quaternion.Euler(-75f, 0, 0);
                    if (elbR != null) elbR.localRotation = Quaternion.Euler(-75f, 0, 0);
                    break;
                }
                case "board":
                {
                    float p = phase * 0.5f;
                    legL.localRotation = Quaternion.Euler(-26f + Mathf.Sin(p) * 5f, 0, 14f);
                    legR.localRotation = Quaternion.Euler(22f, 0, -14f);
                    if (kneeL != null) kneeL.localRotation = Quaternion.Euler(42f, 0, 0);
                    if (kneeR != null) kneeR.localRotation = Quaternion.Euler(34f, 0, 0);
                    armL.localRotation = Quaternion.Euler(0, 0, 46f);
                    armR.localRotation = Quaternion.Euler(0, 0, -46f);
                    if (elbL != null) elbL.localRotation = Quaternion.Euler(-22f, 0, 0);
                    if (elbR != null) elbR.localRotation = Quaternion.Euler(-22f, 0, 0);
                    body.localRotation = Quaternion.Euler(0, 0, Mathf.Sin(p) * 5f);
                    head.localRotation = Quaternion.Euler(-4f, 0, Mathf.Sin(p) * 4f);
                    break;
                }
                case "jet":
                {
                    float p = phase * 0.35f;
                    legL.localRotation = Quaternion.Euler(14f + Mathf.Sin(p) * 12f, 0, 5f);
                    legR.localRotation = Quaternion.Euler(18f + Mathf.Sin(p + 1f) * 12f, 0, -5f);
                    if (kneeL != null) kneeL.localRotation = Quaternion.Euler(32f, 0, 0);
                    if (kneeR != null) kneeR.localRotation = Quaternion.Euler(28f, 0, 0);
                    armL.localRotation = Quaternion.Euler(-46f, 0, 22f);
                    armR.localRotation = Quaternion.Euler(-46f, 0, -22f);
                    if (elbL != null) elbL.localRotation = Quaternion.Euler(-26f, 0, 0);
                    if (elbR != null) elbR.localRotation = Quaternion.Euler(-26f, 0, 0);
                    body.localRotation = Quaternion.Euler(-9f, 0, 0);
                    if (flameL != null) flameL.localScale = new Vector3(0.2f, 0.35f + Mathf.Abs(Mathf.Sin(t * 30f)) * 0.3f, 0.2f);
                    if (flameR != null) flameR.localScale = new Vector3(0.2f, 0.35f + Mathf.Abs(Mathf.Cos(t * 28f)) * 0.3f, 0.2f);
                    break;
                }
                case "stumble":
                {
                    float p = phase;
                    body.localRotation = Quaternion.Euler(16f, 0, Mathf.Sin(p * 2f) * 17f);
                    legL.localRotation = Quaternion.Euler(32f, 0, 0);
                    legR.localRotation = Quaternion.Euler(-22f, 0, 0);
                    if (kneeL != null) kneeL.localRotation = Quaternion.Euler(50f, 0, 0);
                    armL.localRotation = Quaternion.Euler(Mathf.Sin(p * 6f) * 85f - 45f, 0, 32f);
                    armR.localRotation = Quaternion.Euler(Mathf.Sin(p * 6f + 2f) * 85f - 45f, 0, -32f);
                    head.localRotation = Quaternion.Euler(-10f, 0, Mathf.Sin(p * 3f) * 8f);
                    break;
                }
                case "dead":
                {
                    body.localRotation = Quaternion.Euler(-84f, 0, 0);
                    body.localPosition = new Vector3(0, -0.25f, 0.3f);
                    legL.localRotation = Quaternion.Euler(22f, 0, 12f);
                    legR.localRotation = Quaternion.Euler(-12f, 0, -16f);
                    if (kneeL != null) kneeL.localRotation = Quaternion.Euler(28f, 0, 0);
                    armL.localRotation = Quaternion.Euler(-34f, 0, 42f);
                    armR.localRotation = Quaternion.Euler(-22f, 0, -42f);
                    break;
                }
                case "grab":
                {
                    float reach = Mathf.Clamp01(t * 3f);
                    armL.localRotation = Quaternion.Euler(-115f * reach, 0, 12f);
                    armR.localRotation = Quaternion.Euler(-130f * reach, 0, -12f);
                    if (elbL != null) elbL.localRotation = Quaternion.Euler(-30f * reach, 0, 0);
                    if (elbR != null) elbR.localRotation = Quaternion.Euler(-30f * reach, 0, 0);
                    legL.localRotation = Quaternion.Euler(Mathf.Sin(phase) * 42f, 0, 0);
                    legR.localRotation = Quaternion.Euler(Mathf.Sin(phase + Mathf.PI) * 42f, 0, 0);
                    if (kneeL != null) kneeL.localRotation = Quaternion.Euler(Mathf.Clamp01(-Mathf.Sin(phase - 0.6f)) * 80f, 0, 0);
                    if (kneeR != null) kneeR.localRotation = Quaternion.Euler(Mathf.Clamp01(-Mathf.Sin(phase + Mathf.PI - 0.6f)) * 80f, 0, 0);
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
