using System.Collections.Generic;
using UnityEngine;

namespace DummySurfer
{
    /// <summary>Procedural chibi cartoon characters (boy runner, inspector, dog) + pose engine.
    /// Boy "v4": huge round head, red cap with forward brim, scalloped bowl-cut fringe,
    /// big glossy eyes with highlights, blush, open smile with tooth — white tee under an
    /// open denim vest, rolled-cuff jeans, chunky black/white sneakers, round orange backpack.
    /// 100% smooth primitives (spheres / capsules / cylinders) — ZERO cubes, TV-cartoon look.
    /// Articulated: hips, knees, shoulders, elbows — expressive SS-style run cycle with
    /// squash & stretch, bounce, head bob and auto-blinking.</summary>
    public class CharacterRig : MonoBehaviour
    {
        public Transform body, head, armL, armR, legL, legR, torso, tail, board, jet, flameL, flameR, bag;
        public Transform kneeL, kneeR, elbL, elbR, handR;
        public string kind = "boy";

        Transform[] blinkers;          // eye ellipsoids (blink by scaling Y)
        Vector3[] blinkBase;
        Transform[] glints;            // eye highlights (hide while blinking)

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

        static GameObject Sph(Transform parent, Vector3 pos, Vector3 scl, Material mat, string name, float rotX = 0f, float rotZ = 0f)
        {
            var go = Part(parent, PrimitiveType.Sphere, pos, scl, mat, name);
            if (rotX != 0f || rotZ != 0f) go.transform.localRotation = Quaternion.Euler(rotX, 0f, rotZ);
            return go;
        }

        static GameObject Cap(Transform parent, Vector3 pos, Vector3 scl, Material mat, string name, float rotX = 0f, float rotZ = 0f)
        {
            var go = Part(parent, PrimitiveType.Capsule, pos, scl, mat, name);
            if (rotX != 0f || rotZ != 0f) go.transform.localRotation = Quaternion.Euler(rotX, 0f, rotZ);
            return go;
        }

        static GameObject Cyl(Transform parent, Vector3 pos, Vector3 scl, Material mat, string name, float rotX = 0f)
        {
            var go = Part(parent, PrimitiveType.Cylinder, pos, scl, mat, name);
            if (rotX != 0f) go.transform.localRotation = Quaternion.Euler(rotX, 0f, 0f);
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

        // gradient strengths tuned per material family (baked vertical shading)
        static Material GMat(Color c, float top, float bot) { return Fx.ShadedMat(c, top, bot); }
        static Material Flat(Color c) { return Fx.ShadedMat(c, 1f, 1f); }

        // ------------------------------------------------ BOY (hero)
        public static CharacterRig BuildBoy(Transform parent)
        {
            var root = new GameObject("Boy");
            root.transform.SetParent(parent, false);
            var rig = root.AddComponent<CharacterRig>();
            rig.kind = "boy";

            var skin = GMat(GMC(0xFF, 0xCD, 0xA5), 1.14f, 0.74f);
            var hair = GMat(GMC(0x3A, 0x24, 0x1B), 1.45f, 0.55f);
            var cap = GMat(GMC(0xE8, 0x45, 0x45), 1.28f, 0.60f);
            var capD = GMat(GMC(0xC2, 0x35, 0x35), 1.30f, 0.60f);
            var tee = GMat(GMC(0xFA, 0xF8, 0xF1), 1.10f, 0.80f);
            var vest = GMat(GMC(0x4E, 0x71, 0xA8), 1.22f, 0.60f);
            var vestD = GMat(GMC(0x3F, 0x5D, 0x90), 1.20f, 0.60f);
            var jeans = GMat(GMC(0x58, 0x78, 0xB5), 1.18f, 0.62f);
            var cuff = GMat(GMC(0x70, 0x90, 0xC8), 1.12f, 0.75f);
            var shoe = GMat(GMC(0x2E, 0x2E, 0x36), 1.45f, 0.55f);
            var sole = GMat(GMC(0xF6, 0xF4, 0xEE), 1.10f, 0.86f);
            var pack = GMat(GMC(0xF2, 0x7E, 0x3F), 1.22f, 0.62f);
            var packD = GMat(GMC(0xD9, 0x64, 0x27), 1.20f, 0.62f);
            var eye = GMat(GMC(0x2B, 0x1A, 0x12), 1.25f, 0.85f);
            var brow = GMat(GMC(0x33, 0x20, 0x1A), 1.30f, 0.70f);
            var blush = GMat(GMC(0xFF, 0x9D, 0xA6), 1.12f, 0.95f);
            var nose = GMat(GMC(0xF4, 0xB1, 0x83), 1.08f, 0.80f);
            var mouth = GMat(GMC(0x8A, 0x32, 0x2B), 1.20f, 0.80f);
            var white = Flat(GMC(0xFF, 0xFF, 0xFF));

            rig.body = Pivot(root.transform, "body", Vector3.zero);

            // ============================ LEGS (hip -> knee -> chunky sneaker)
            for (int s = 0; s < 2; s++)
            {
                float sx = s == 0 ? -1f : 1f;
                var hip = Pivot(rig.body, "leg" + s, new Vector3(sx * 0.135f, 0.71f, 0f));
                if (s == 0) rig.legL = hip; else rig.legR = hip;
                Cap(hip, new Vector3(0f, -0.13f, 0f), new Vector3(0.13f, 0.12f, 0.13f), jeans, "thigh");
                var knee = Pivot(hip, "knee", new Vector3(0f, -0.26f, 0f));
                if (s == 0) rig.kneeL = knee; else rig.kneeR = knee;
                Sph(knee, Vector3.zero, new Vector3(0.105f, 0.105f, 0.105f), jeans, "kneeJoint");
                Cap(knee, new Vector3(0f, -0.11f, 0f), new Vector3(0.10f, 0.10f, 0.10f), jeans, "shin");
                Cyl(knee, new Vector3(0f, -0.195f, 0f), new Vector3(0.118f, 0.03f, 0.118f), cuff, "cuff");
                // chunky sneaker — all ellipsoids
                Sph(knee, new Vector3(0f, -0.235f, 0.015f), new Vector3(0.115f, 0.085f, 0.175f), shoe, "shoeUpper");
                Sph(knee, new Vector3(0f, -0.25f, 0.14f), new Vector3(0.095f, 0.065f, 0.09f), sole, "toeCap");
                Sph(knee, new Vector3(0f, -0.29f, 0.045f), new Vector3(0.12f, 0.038f, 0.195f), sole, "sole");
                Sph(knee, new Vector3(0f, -0.21f, -0.105f), new Vector3(0.08f, 0.05f, 0.05f), sole, "heel");
            }

            // ============================ TORSO (white tee egg + open denim vest + round backpack)
            rig.torso = Pivot(rig.body, "torso", new Vector3(0f, 0.75f, 0f));
            Sph(rig.torso, new Vector3(0f, 0.17f, 0.01f), new Vector3(0.335f, 0.295f, 0.25f), tee, "tee");
            Sph(rig.torso, new Vector3(0f, -0.015f, 0.005f), new Vector3(0.30f, 0.10f, 0.235f), jeans, "hips");
            Sph(rig.torso, new Vector3(-0.19f, 0.28f, 0.015f), new Vector3(0.105f, 0.22f, 0.21f), vest, "vestL");
            Sph(rig.torso, new Vector3(0.19f, 0.28f, 0.015f), new Vector3(0.105f, 0.22f, 0.21f), vest, "vestR");
            Sph(rig.torso, new Vector3(-0.20f, 0.525f, 0.005f), new Vector3(0.115f, 0.065f, 0.115f), vest, "shoulderL");
            Sph(rig.torso, new Vector3(0.20f, 0.525f, 0.005f), new Vector3(0.115f, 0.065f, 0.115f), vest, "shoulderR");
            Sph(rig.torso, new Vector3(0f, 0.32f, -0.15f), new Vector3(0.27f, 0.22f, 0.115f), vest, "vestBack");
            Sph(rig.torso, new Vector3(0f, 0.55f, -0.095f), new Vector3(0.23f, 0.055f, 0.075f), vestD, "collar");
            // round orange backpack (hero piece from behind)
            Sph(rig.torso, new Vector3(0f, 0.30f, -0.265f), new Vector3(0.25f, 0.28f, 0.135f), pack, "pack");
            Sph(rig.torso, new Vector3(0f, 0.21f, -0.385f), new Vector3(0.165f, 0.12f, 0.05f), packD, "packPocket");
            Cap(rig.torso, new Vector3(-0.12f, 0.44f, 0.145f), new Vector3(0.042f, 0.10f, 0.042f), vestD, "strapL", -22f, 0f);
            Cap(rig.torso, new Vector3(0.12f, 0.44f, 0.145f), new Vector3(0.042f, 0.10f, 0.042f), vestD, "strapR", -22f, 0f);

            // ============================ ARMS (shoulder -> elbow -> mitten)
            for (int s = 0; s < 2; s++)
            {
                float sx = s == 0 ? -1f : 1f;
                var sh = Pivot(rig.body, "arm" + s, new Vector3(sx * 0.275f, 1.25f, 0.01f));
                if (s == 0) rig.armL = sh; else rig.armR = sh;
                Cap(sh, new Vector3(0f, -0.10f, 0f), new Vector3(0.105f, 0.10f, 0.105f), tee, "sleeve");
                Sph(sh, new Vector3(0f, -0.19f, 0f), new Vector3(0.10f, 0.10f, 0.10f), tee, "sleeveHem");
                var elb = Pivot(sh, "elbow", new Vector3(0f, -0.205f, 0f));
                if (s == 0) rig.elbL = elb; else rig.elbR = elb;
                Sph(elb, Vector3.zero, new Vector3(0.082f, 0.082f, 0.082f), skin, "elbowJoint");
                Cap(elb, new Vector3(0f, -0.09f, 0f), new Vector3(0.082f, 0.078f, 0.082f), skin, "forearm");
                var hand = Sph(elb, new Vector3(0f, -0.19f, 0.005f), new Vector3(0.09f, 0.095f, 0.09f), skin, "hand");
                if (s == 1) rig.handR = hand.transform;
            }

            // spray can in the right hand (menu poses only)
            rig.bag = Pivot(rig.elbR, "bag", Vector3.zero);
            Cyl(rig.bag, new Vector3(0f, -0.26f, 0.05f), new Vector3(0.08f, 0.07f, 0.08f), pack, "can");
            Cyl(rig.bag, new Vector3(0f, -0.175f, 0.05f), new Vector3(0.042f, 0.028f, 0.042f), sole, "canTop");
            rig.bag.gameObject.SetActive(false);

            // ============================ HEAD (the hero — huge chibi head)
            rig.head = Pivot(rig.body, "head", new Vector3(0f, 1.37f, 0f));
            Sph(rig.head, new Vector3(0f, 0f, 0.01f), new Vector3(0.445f, 0.43f, 0.435f), skin, "skull");
            Sph(rig.head, new Vector3(-0.39f, -0.01f, -0.03f), new Vector3(0.05f, 0.075f, 0.06f), skin, "earL");
            Sph(rig.head, new Vector3(0.39f, -0.01f, -0.03f), new Vector3(0.05f, 0.075f, 0.06f), skin, "earR");
            // hair — bowl cut hugging the skull, tucked under the cap
            Sph(rig.head, new Vector3(0f, 0.07f, -0.055f), new Vector3(0.462f, 0.415f, 0.44f), hair, "hairBack");
            for (int i = 0; i < 9; i++)
            {
                float bx = -0.208f + i * 0.052f;
                var f = Sph(rig.head,
                    new Vector3(bx, 0.115f - Mathf.Abs(bx) * 0.06f, 0.415f - bx * bx * 1.1f),
                    new Vector3(0.075f, 0.055f, 0.065f), hair, "fringe" + i,
                    0f, -bx * 45f);
            }
            Sph(rig.head, new Vector3(-0.345f, 0.02f, 0.11f), new Vector3(0.062f, 0.088f, 0.078f), hair, "tuftL");
            Sph(rig.head, new Vector3(0.345f, 0.02f, 0.11f), new Vector3(0.062f, 0.088f, 0.078f), hair, "tuftR");
            Sph(rig.head, new Vector3(0f, -0.10f, -0.33f), new Vector3(0.20f, 0.09f, 0.085f), hair, "backTuft");
            Sph(rig.head, new Vector3(-0.17f, -0.05f, -0.31f), new Vector3(0.09f, 0.08f, 0.075f), hair, "backTuftL");
            Sph(rig.head, new Vector3(0.17f, -0.05f, -0.31f), new Vector3(0.09f, 0.08f, 0.075f), hair, "backTuftR");
            // cap — round dome wrapping the skull, forward brim, button on top
            Sph(rig.head, new Vector3(0f, 0.27f, -0.005f), new Vector3(0.44f, 0.30f, 0.435f), cap, "capDome");
            Sph(rig.head, new Vector3(0f, 0.12f, 0.46f), new Vector3(0.30f, 0.032f, 0.10f), cap, "capBrim").transform.localRotation = Quaternion.Euler(-14f, 0f, 0f);
            Sph(rig.head, new Vector3(0f, 0.585f, -0.005f), new Vector3(0.055f, 0.05f, 0.055f), capD, "capButton");
            // face — big glossy eyes, brows, blush, nose, open smile
            var blinkList = new List<Transform>();
            var blinkBaseList = new List<Vector3>();
            var glintList = new List<Transform>();
            for (int s = 0; s < 2; s++)
            {
                float sx = s == 0 ? -1f : 1f;
                var e = Sph(rig.head, new Vector3(sx * 0.18f, 0.02f, 0.385f), new Vector3(0.115f, 0.14f, 0.05f), eye, "eye" + s);
                blinkList.Add(e.transform);
                blinkBaseList.Add(e.transform.localScale);
                var g1 = Sph(rig.head, new Vector3(sx * 0.15f, 0.065f, 0.415f), new Vector3(0.034f, 0.038f, 0.022f), white, "glint");
                var g2 = Sph(rig.head, new Vector3(sx * 0.205f, -0.03f, 0.412f), new Vector3(0.016f, 0.018f, 0.012f), white, "glint2");
                glintList.Add(g1.transform);
                glintList.Add(g2.transform);
            }
            rig.blinkers = blinkList.ToArray();
            rig.blinkBase = blinkBaseList.ToArray();
            rig.glints = glintList.ToArray();
            Sph(rig.head, new Vector3(-0.18f, 0.085f, 0.395f), new Vector3(0.11f, 0.032f, 0.045f), brow, "browL").transform.localRotation = Quaternion.Euler(0f, 0f, 7f);
            Sph(rig.head, new Vector3(0.18f, 0.085f, 0.395f), new Vector3(0.11f, 0.032f, 0.045f), brow, "browR").transform.localRotation = Quaternion.Euler(0f, 0f, -7f);
            Sph(rig.head, new Vector3(-0.255f, -0.075f, 0.325f), new Vector3(0.07f, 0.042f, 0.026f), blush, "blushL").transform.localRotation = Quaternion.Euler(0f, 38f, 0f);
            Sph(rig.head, new Vector3(0.255f, -0.075f, 0.325f), new Vector3(0.07f, 0.042f, 0.026f), blush, "blushR").transform.localRotation = Quaternion.Euler(0f, -38f, 0f);
            Sph(rig.head, new Vector3(0f, -0.03f, 0.435f), new Vector3(0.04f, 0.032f, 0.036f), nose, "nose");
            Sph(rig.head, new Vector3(0f, -0.075f, 0.415f), new Vector3(0.09f, 0.038f, 0.026f), mouth, "smile");
            Sph(rig.head, new Vector3(0f, -0.062f, 0.426f), new Vector3(0.045f, 0.012f, 0.011f), white, "tooth");

            // ============================ hoverboard (hidden by default)
            rig.board = Pivot(root.transform, "board", new Vector3(0f, 0.10f, 0f));
            Sph(rig.board, Vector3.zero, new Vector3(0.47f, 0.045f, 0.24f), cap, "deck");
            Sph(rig.board, new Vector3(0f, -0.005f, 0f), new Vector3(0.40f, 0.03f, 0.20f), pack, "deckStripe");
            rig.board.gameObject.SetActive(false);

            // ============================ jetpack (hidden by default)
            rig.jet = Pivot(rig.body, "jet", Vector3.zero);
            Cap(rig.jet, new Vector3(-0.17f, 1.24f, -0.28f), new Vector3(0.14f, 0.17f, 0.14f), pack, "tankL");
            Cap(rig.jet, new Vector3(0.17f, 1.24f, -0.28f), new Vector3(0.14f, 0.17f, 0.14f), pack, "tankR");
            rig.flameL = Sph(rig.jet, new Vector3(-0.17f, 0.98f, -0.28f), new Vector3(0.09f, 0.24f, 0.09f), Fx.MatGlow(GMC(0xFF, 0xB0, 0x54)), "flameL").transform;
            rig.flameR = Sph(rig.jet, new Vector3(0.17f, 0.98f, -0.28f), new Vector3(0.09f, 0.24f, 0.09f), Fx.MatGlow(GMC(0xFF, 0xD2, 0x4A)), "flameR").transform;
            rig.jet.gameObject.SetActive(false);

            return rig;
        }

        // ------------------------------------------------ INSPECTOR (rounded)
        public static CharacterRig BuildInspector(Transform parent)
        {
            var root = new GameObject("Inspector");
            root.transform.SetParent(parent, false);
            var rig = root.AddComponent<CharacterRig>();
            rig.kind = "guard";

            var skin = GMat(GMC(0xFF, 0xC9, 0xA0), 1.14f, 0.74f);
            var jacket = GMat(GMC(0x7A, 0x8A, 0x46), 1.22f, 0.58f);
            var jacketD = GMat(GMC(0x64, 0x72, 0x3A), 1.18f, 0.60f);
            var pants = GMat(GMC(0x4A, 0x55, 0x68), 1.18f, 0.62f);
            var boots = GMat(GMC(0x26, 0x29, 0x32), 1.45f, 0.55f);
            var capB = GMat(GMC(0x3E, 0x6F, 0xE0), 1.28f, 0.60f);
            var capD = GMat(GMC(0x2A, 0x4A, 0xA0), 1.30f, 0.60f);
            var grey = GMat(GMC(0x9A, 0xA0, 0xA8), 1.15f, 0.75f);
            var gold = Fx.MatGlow(GMC(0xFF, 0xD2, 0x3E));
            var dark = GMat(GMC(0x26, 0x2C, 0x3C), 1.30f, 0.70f);
            var white = Flat(GMC(0xFA, 0xFA, 0xFA));

            rig.body = Pivot(root.transform, "body", Vector3.zero);

            rig.legL = Pivot(rig.body, "legL", new Vector3(-0.17f, 0.82f, 0f));
            Cap(rig.legL, new Vector3(0f, -0.20f, 0f), new Vector3(0.24f, 0.18f, 0.24f), pants, "thighL");
            Sph(rig.legL, new Vector3(0f, -0.42f, 0.05f), new Vector3(0.19f, 0.13f, 0.30f), boots, "bootL");
            Sph(rig.legL, new Vector3(0f, -0.475f, 0.06f), new Vector3(0.20f, 0.045f, 0.33f), dark, "soleL");
            rig.legR = Pivot(rig.body, "legR", new Vector3(0.17f, 0.82f, 0f));
            Cap(rig.legR, new Vector3(0f, -0.20f, 0f), new Vector3(0.24f, 0.18f, 0.24f), pants, "thighR");
            Sph(rig.legR, new Vector3(0f, -0.42f, 0.05f), new Vector3(0.19f, 0.13f, 0.30f), boots, "bootR");
            Sph(rig.legR, new Vector3(0f, -0.475f, 0.06f), new Vector3(0.20f, 0.045f, 0.33f), dark, "soleR");

            rig.torso = Pivot(rig.body, "torso", new Vector3(0f, 0.82f, 0f));
            Sph(rig.torso, new Vector3(0f, 0.28f, 0.02f), new Vector3(0.55f, 0.42f, 0.42f), jacket, "belly");
            Sph(rig.torso, new Vector3(0f, 0.44f, 0.03f), new Vector3(0.50f, 0.30f, 0.40f), jacket, "chest");
            Sph(rig.torso, new Vector3(0f, 0.10f, 0.02f), new Vector3(0.50f, 0.09f, 0.40f), dark, "belt");
            Sph(rig.torso, new Vector3(-0.16f, 0.44f, 0.21f), new Vector3(0.08f, 0.08f, 0.035f), gold, "badge");
            Sph(rig.torso, new Vector3(0.16f, 0.36f, 0.22f), new Vector3(0.10f, 0.11f, 0.05f), jacketD, "pocket");

            rig.armL = Pivot(rig.body, "armL", new Vector3(-0.40f, 1.36f, 0f));
            Cap(rig.armL, new Vector3(0f, -0.19f, 0f), new Vector3(0.19f, 0.14f, 0.19f), jacket, "armuL");
            Sph(rig.armL, new Vector3(0f, -0.38f, 0f), new Vector3(0.17f, 0.17f, 0.17f), skin, "handL");
            rig.armR = Pivot(rig.body, "armR", new Vector3(0.40f, 1.36f, 0f));
            Cap(rig.armR, new Vector3(0f, -0.19f, 0f), new Vector3(0.19f, 0.14f, 0.19f), jacket, "armuR");
            var handR = Sph(rig.armR, new Vector3(0f, -0.38f, 0f), new Vector3(0.17f, 0.17f, 0.17f), skin, "handR");
            rig.handR = handR.transform;
            Cyl(rig.armR, new Vector3(0f, -0.48f, 0.10f), new Vector3(0.05f, 0.16f, 0.05f), grey, "baton", 20f);

            rig.head = Pivot(rig.body, "head", new Vector3(0f, 1.50f, 0f));
            Sph(rig.head, new Vector3(0f, 0.18f, 0f), new Vector3(0.46f, 0.44f, 0.44f), skin, "skull");
            // bushy white mustache (three puffs)
            Sph(rig.head, new Vector3(-0.075f, 0.06f, 0.215f), new Vector3(0.10f, 0.045f, 0.05f), white, "moL");
            Sph(rig.head, new Vector3(0.075f, 0.06f, 0.215f), new Vector3(0.10f, 0.045f, 0.05f), white, "moR");
            Sph(rig.head, new Vector3(0f, 0.075f, 0.22f), new Vector3(0.06f, 0.04f, 0.045f), white, "moC");
            Sph(rig.head, new Vector3(0f, 0.12f, 0.225f), new Vector3(0.055f, 0.045f, 0.05f), GMat(GMC(0xF0, 0xB0, 0x88), 1.08f, 0.8f), "nose");
            Sph(rig.head, new Vector3(-0.09f, 0.20f, 0.20f), new Vector3(0.075f, 0.085f, 0.04f), white, "eyeL");
            Sph(rig.head, new Vector3(0.09f, 0.20f, 0.20f), new Vector3(0.075f, 0.085f, 0.04f), white, "eyeR");
            Sph(rig.head, new Vector3(-0.09f, 0.195f, 0.235f), new Vector3(0.035f, 0.042f, 0.02f), dark, "pupL");
            Sph(rig.head, new Vector3(0.09f, 0.195f, 0.235f), new Vector3(0.035f, 0.042f, 0.02f), dark, "pupR");
            Sph(rig.head, new Vector3(-0.09f, 0.275f, 0.215f), new Vector3(0.10f, 0.03f, 0.05f), grey, "browL").transform.localRotation = Quaternion.Euler(0f, 0f, 8f);
            Sph(rig.head, new Vector3(0.09f, 0.275f, 0.215f), new Vector3(0.10f, 0.03f, 0.05f), grey, "browR").transform.localRotation = Quaternion.Euler(0f, 0f, -8f);
            // blue cap with dark peak
            Sph(rig.head, new Vector3(0f, 0.40f, 0f), new Vector3(0.48f, 0.22f, 0.48f), capB, "capTop");
            Sph(rig.head, new Vector3(0f, 0.335f, 0.28f), new Vector3(0.30f, 0.04f, 0.18f), capD, "peak").transform.localRotation = Quaternion.Euler(-10f, 0f, 0f);
            Sph(rig.head, new Vector3(0f, 0.60f, 0f), new Vector3(0.05f, 0.045f, 0.05f), capD, "capButton");
            return rig;
        }

        // ------------------------------------------------ DOG (rounded)
        public static CharacterRig BuildDog(Transform parent)
        {
            var root = new GameObject("Dog");
            root.transform.SetParent(parent, false);
            root.transform.localScale = new Vector3(0.85f, 0.85f, 0.85f);
            var rig = root.AddComponent<CharacterRig>();
            rig.kind = "dog";

            var fur = GMat(GMC(0xD9, 0xB3, 0x80), 1.18f, 0.66f);
            var furD = GMat(GMC(0xB0, 0x8C, 0x5E), 1.15f, 0.62f);
            var dark = GMat(GMC(0x26, 0x2C, 0x3C), 1.30f, 0.70f);
            var collar = GMat(GMC(0xE8, 0x40, 0x40), 1.28f, 0.60f);
            var gold = Fx.MatGlow(GMC(0xFF, 0xD2, 0x3E));

            rig.body = Pivot(root.transform, "body", Vector3.zero);
            Sph(rig.body, new Vector3(0f, 0.42f, 0f), new Vector3(0.16f, 0.15f, 0.34f), fur, "torso");
            Sph(rig.body, new Vector3(0f, 0.58f, 0.30f), new Vector3(0.15f, 0.14f, 0.15f), fur, "head");
            Sph(rig.body, new Vector3(0f, 0.545f, 0.425f), new Vector3(0.07f, 0.06f, 0.08f), furD, "snout");
            Sph(rig.body, new Vector3(0f, 0.575f, 0.495f), new Vector3(0.045f, 0.04f, 0.04f), dark, "nose");
            Sph(rig.body, new Vector3(-0.085f, 0.70f, 0.28f), new Vector3(0.05f, 0.10f, 0.045f), furD, "earL").transform.localRotation = Quaternion.Euler(0f, 0f, 25f);
            Sph(rig.body, new Vector3(0.085f, 0.70f, 0.28f), new Vector3(0.05f, 0.10f, 0.045f), furD, "earR").transform.localRotation = Quaternion.Euler(0f, 0f, -25f);
            Sph(rig.body, new Vector3(-0.06f, 0.60f, 0.435f), new Vector3(0.035f, 0.04f, 0.025f), dark, "eyeL");
            Sph(rig.body, new Vector3(0.06f, 0.60f, 0.435f), new Vector3(0.035f, 0.04f, 0.025f), dark, "eyeR");
            Cyl(rig.body, new Vector3(0f, 0.47f, 0.26f), new Vector3(0.125f, 0.025f, 0.125f), collar, "collar");
            Sph(rig.body, new Vector3(0f, 0.42f, 0.31f), new Vector3(0.045f, 0.045f, 0.02f), gold, "tag");

            rig.legL = Pivot(rig.body, "legFL", new Vector3(-0.09f, 0.34f, 0.22f));
            Cap(rig.legL, new Vector3(0f, -0.13f, 0f), new Vector3(0.075f, 0.13f, 0.075f), fur, "flegL");
            Sph(rig.legL, new Vector3(0f, -0.27f, 0.01f), new Vector3(0.07f, 0.055f, 0.08f), furD, "pawL");
            rig.legR = Pivot(rig.body, "legFR", new Vector3(0.09f, 0.34f, 0.22f));
            Cap(rig.legR, new Vector3(0f, -0.13f, 0f), new Vector3(0.075f, 0.13f, 0.075f), fur, "flegR");
            Sph(rig.legR, new Vector3(0f, -0.27f, 0.01f), new Vector3(0.07f, 0.055f, 0.08f), furD, "pawR");
            var bl = Pivot(rig.body, "legBL", new Vector3(-0.09f, 0.34f, -0.22f));
            Cap(bl, new Vector3(0f, -0.13f, 0f), new Vector3(0.075f, 0.13f, 0.075f), fur, "blegL");
            Sph(bl, new Vector3(0f, -0.27f, 0.01f), new Vector3(0.07f, 0.055f, 0.08f), furD, "bpawL");
            var br = Pivot(rig.body, "legBR", new Vector3(0.09f, 0.34f, -0.22f));
            Cap(br, new Vector3(0f, -0.13f, 0f), new Vector3(0.075f, 0.13f, 0.075f), fur, "blegR");
            Sph(br, new Vector3(0f, -0.27f, 0.01f), new Vector3(0.07f, 0.055f, 0.08f), furD, "bpawR");

            rig.tail = Pivot(rig.body, "tail", new Vector3(0f, 0.52f, -0.32f));
            Cap(rig.tail, new Vector3(0f, 0.05f, -0.10f), new Vector3(0.045f, 0.14f, 0.045f), fur, "tail", -40f, 0f);
            return rig;
        }

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

        void Blink(float t)
        {
            if (blinkers == null) return;
            float cyc = t % 3.4f;
            bool closed = cyc < 0.13f;
            for (int i = 0; i < blinkers.Length; i++)
            {
                var b = blinkBase[i];
                blinkers[i].localScale = new Vector3(b.x, closed ? b.y * 0.12f : b.y, b.z);
            }
            if (glints != null)
                for (int i = 0; i < glints.Length; i++)
                {
                    if (glints[i] != null && glints[i].gameObject.activeSelf == closed)
                        glints[i].gameObject.SetActive(!closed);
                }
        }

        public void Pose(string mode, float t, float speedF)
        {
            if (body == null) return;
            float s = Mathf.Clamp01(speedF);
            ZeroPose();
            Blink(t);

            if (kind == "dog")
            {
                float p = phase;
                legL.localRotation = Quaternion.Euler(Mathf.Sin(p) * 52f, 0f, 0f);
                legR.localRotation = Quaternion.Euler(Mathf.Sin(p + Mathf.PI) * 52f, 0f, 0f);
                body.localPosition = new Vector3(0f, Mathf.Abs(Mathf.Sin(p)) * 0.045f, 0f);
                if (tail != null) tail.localRotation = Quaternion.Euler(0f, Mathf.Sin(p * 2.2f) * 28f, 30f);
                return;
            }

            switch (mode)
            {
                case "idle":
                {
                    float b = Mathf.Sin(t * 2.1f);
                    body.localPosition = new Vector3(0f, b * 0.012f, 0f);
                    armL.localRotation = Quaternion.Euler(2f, 0f, 10f + b * 3f);
                    armR.localRotation = Quaternion.Euler(-8f + Mathf.Sin(t * 2.6f) * 6f, 0f, -10f);
                    if (elbR != null) elbR.localRotation = Quaternion.Euler(-26f, 0f, 0f);
                    head.localRotation = Quaternion.Euler(Mathf.Sin(t * 1.2f) * 3f, Mathf.Sin(t * 0.65f) * 13f, 0f);
                    if (bag != null) bag.gameObject.SetActive(true);
                    break;
                }
                case "spray":
                {
                    float b = Mathf.Sin(t * 2.0f);
                    body.localPosition = new Vector3(0f, b * 0.010f, 0f);
                    armR.localRotation = Quaternion.Euler(-98f + Mathf.Sin(t * 9f) * 6f, 0f, -6f);
                    if (elbR != null) elbR.localRotation = Quaternion.Euler(-16f, 0f, 0f);
                    armL.localRotation = Quaternion.Euler(0f, 0f, 12f);
                    head.localRotation = Quaternion.Euler(-5f, -15f, 0f);
                    if (bag != null) bag.gameObject.SetActive(true);
                    break;
                }
                case "run":
                {
                    float p = phase;
                    float amp = 0.55f + 0.45f * s;
                    legL.localRotation = Quaternion.Euler(Mathf.Sin(p) * 62f * amp, 0f, 0f);
                    legR.localRotation = Quaternion.Euler(Mathf.Sin(p + Mathf.PI) * 62f * amp, 0f, 0f);
                    float kbL = Mathf.Max(0f, -Mathf.Sin(p - 0.85f)) * 105f * amp;
                    float kbR = Mathf.Max(0f, -Mathf.Sin(p + Mathf.PI - 0.85f)) * 105f * amp;
                    if (kneeL != null) kneeL.localRotation = Quaternion.Euler(kbL, 0f, 0f);
                    if (kneeR != null) kneeR.localRotation = Quaternion.Euler(kbR, 0f, 0f);
                    float swL = Mathf.Sin(p + Mathf.PI);
                    float swR = Mathf.Sin(p);
                    armL.localRotation = Quaternion.Euler(2f + swL * 26f * amp, 0f, 9f);
                    armR.localRotation = Quaternion.Euler(2f + swR * 26f * amp, 0f, -9f);
                    if (elbL != null) elbL.localRotation = Quaternion.Euler(-76f, 0f, 0f);
                    if (elbR != null) elbR.localRotation = Quaternion.Euler(-76f, 0f, 0f);
                    float bounce = Mathf.Abs(Mathf.Sin(p));
                    body.localPosition = new Vector3(0f, bounce * 0.05f * s, 0f);
                    body.localScale = new Vector3(1f + bounce * 0.018f * s, 1f - bounce * 0.04f * s, 1f + bounce * 0.018f * s);
                    body.localRotation = Quaternion.Euler(7f + 4f * s, 0f, 0f);
                    torso.localRotation = Quaternion.Euler(4f, 0f, 0f);
                    head.localRotation = Quaternion.Euler(-6f + Mathf.Sin(p * 2f) * 2.5f, 0f, Mathf.Sin(p) * 3f);
                    if (bag != null) bag.gameObject.SetActive(false);
                    break;
                }
                case "jump":
                {
                    legL.localRotation = Quaternion.Euler(-52f, 0f, 8f);
                    legR.localRotation = Quaternion.Euler(18f, 0f, -8f);
                    if (kneeL != null) kneeL.localRotation = Quaternion.Euler(88f, 0f, 0f);
                    if (kneeR != null) kneeR.localRotation = Quaternion.Euler(34f, 0f, 0f);
                    armL.localRotation = Quaternion.Euler(-150f, 0f, 18f);
                    armR.localRotation = Quaternion.Euler(-165f, 0f, -18f);
                    if (elbL != null) elbL.localRotation = Quaternion.Euler(-30f, 0f, 0f);
                    if (elbR != null) elbR.localRotation = Quaternion.Euler(-30f, 0f, 0f);
                    body.localRotation = Quaternion.Euler(-8f, 0f, 0f);
                    body.localScale = new Vector3(0.98f, 1.04f, 0.98f);
                    head.localRotation = Quaternion.Euler(-10f, 0f, 0f);
                    break;
                }
                case "roll":
                {
                    body.localScale = new Vector3(0.8f, 0.52f, 0.8f);
                    body.localPosition = new Vector3(0f, -0.10f, 0f);
                    body.localRotation = Quaternion.Euler(-phase * 620f, 0f, 0f);
                    legL.localRotation = Quaternion.Euler(-92f, 0f, 0f);
                    legR.localRotation = Quaternion.Euler(-92f, 0f, 0f);
                    if (kneeL != null) kneeL.localRotation = Quaternion.Euler(118f, 0f, 0f);
                    if (kneeR != null) kneeR.localRotation = Quaternion.Euler(118f, 0f, 0f);
                    armL.localRotation = Quaternion.Euler(-118f, 0f, 0f);
                    armR.localRotation = Quaternion.Euler(-118f, 0f, 0f);
                    if (elbL != null) elbL.localRotation = Quaternion.Euler(-96f, 0f, 0f);
                    if (elbR != null) elbR.localRotation = Quaternion.Euler(-96f, 0f, 0f);
                    head.localRotation = Quaternion.Euler(26f, 0f, 0f);
                    break;
                }
                case "board":
                {
                    float p = phase * 0.5f;
                    legL.localRotation = Quaternion.Euler(-26f + Mathf.Sin(p) * 6f, 0f, 16f);
                    legR.localRotation = Quaternion.Euler(24f, 0f, -16f);
                    if (kneeL != null) kneeL.localRotation = Quaternion.Euler(46f, 0f, 0f);
                    if (kneeR != null) kneeR.localRotation = Quaternion.Euler(36f, 0f, 0f);
                    armL.localRotation = Quaternion.Euler(-8f, 0f, 52f);
                    armR.localRotation = Quaternion.Euler(-8f, 0f, -52f);
                    if (elbL != null) elbL.localRotation = Quaternion.Euler(-24f, 0f, 0f);
                    if (elbR != null) elbR.localRotation = Quaternion.Euler(-24f, 0f, 0f);
                    body.localRotation = Quaternion.Euler(0f, 0f, Mathf.Sin(p) * 5f);
                    head.localRotation = Quaternion.Euler(-6f, 0f, Mathf.Sin(p) * 5f);
                    break;
                }
                case "jet":
                {
                    float p = phase * 0.35f;
                    legL.localRotation = Quaternion.Euler(12f + Mathf.Sin(p) * 14f, 0f, 5f);
                    legR.localRotation = Quaternion.Euler(17f + Mathf.Sin(p + 1.1f) * 14f, 0f, -5f);
                    if (kneeL != null) kneeL.localRotation = Quaternion.Euler(34f, 0f, 0f);
                    if (kneeR != null) kneeR.localRotation = Quaternion.Euler(30f, 0f, 0f);
                    armL.localRotation = Quaternion.Euler(-38f, 0f, 26f);
                    armR.localRotation = Quaternion.Euler(-38f, 0f, -26f);
                    if (elbL != null) elbL.localRotation = Quaternion.Euler(-78f, 0f, 0f);
                    if (elbR != null) elbR.localRotation = Quaternion.Euler(-78f, 0f, 0f);
                    body.localRotation = Quaternion.Euler(-10f, 0f, 0f);
                    head.localRotation = Quaternion.Euler(-12f, 0f, 0f);
                    if (flameL != null) flameL.localScale = new Vector3(0.09f, 0.17f + Mathf.Abs(Mathf.Sin(t * 26f)) * 0.19f, 0.09f);
                    if (flameR != null) flameR.localScale = new Vector3(0.09f, 0.17f + Mathf.Abs(Mathf.Cos(t * 24f)) * 0.19f, 0.09f);
                    break;
                }
                case "stumble":
                {
                    float p = phase;
                    body.localRotation = Quaternion.Euler(16f, 0f, Mathf.Sin(p * 2f) * 15f);
                    legL.localRotation = Quaternion.Euler(30f, 0f, 0f);
                    legR.localRotation = Quaternion.Euler(-22f, 0f, 0f);
                    if (kneeL != null) kneeL.localRotation = Quaternion.Euler(52f, 0f, 0f);
                    armL.localRotation = Quaternion.Euler(Mathf.Sin(p * 6f) * 85f - 55f, 0f, 34f);
                    armR.localRotation = Quaternion.Euler(Mathf.Sin(p * 6f + 2f) * 85f - 55f, 0f, -34f);
                    head.localRotation = Quaternion.Euler(0f, 0f, Mathf.Sin(p * 3f) * 9f);
                    break;
                }
                case "dead":
                {
                    body.localRotation = Quaternion.Euler(-84f, 0f, 0f);
                    body.localPosition = new Vector3(0f, -0.24f, 0.28f);
                    legL.localRotation = Quaternion.Euler(20f, 0f, 12f);
                    legR.localRotation = Quaternion.Euler(-12f, 0f, -16f);
                    if (kneeL != null) kneeL.localRotation = Quaternion.Euler(26f, 0f, 0f);
                    armL.localRotation = Quaternion.Euler(-38f, 0f, 46f);
                    armR.localRotation = Quaternion.Euler(-24f, 0f, -46f);
                    break;
                }
                case "grab":
                {
                    float reach = Mathf.Clamp01(t * 3f);
                    armL.localRotation = Quaternion.Euler(-115f * reach, 0f, 12f);
                    armR.localRotation = Quaternion.Euler(-130f * reach, 0f, -12f);
                    if (elbL != null) elbL.localRotation = Quaternion.Euler(-30f * reach, 0f, 0f);
                    if (elbR != null) elbR.localRotation = Quaternion.Euler(-30f * reach, 0f, 0f);
                    float p = phase;
                    legL.localRotation = Quaternion.Euler(Mathf.Sin(p) * 46f, 0f, 0f);
                    legR.localRotation = Quaternion.Euler(Mathf.Sin(p + Mathf.PI) * 46f, 0f, 0f);
                    if (kneeL != null) kneeL.localRotation = Quaternion.Euler(Mathf.Max(0f, -Mathf.Sin(p - 0.85f)) * 86f, 0f, 0f);
                    if (kneeR != null) kneeR.localRotation = Quaternion.Euler(Mathf.Max(0f, -Mathf.Sin(p + Mathf.PI - 0.85f)) * 86f, 0f, 0f);
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
