using System.Collections.Generic;
using UnityEngine;

namespace DummySurfer
{
    /// <summary>Procedural chibi anime characters (boy runner, inspector, dog) + pose engine.
    /// Boy "v5": continuous lathe-surface body (no primitive stacking), baked cel shading
    /// via AnimeMesh ramp UVs + inverted-hull ink outlines. Jake-style outfit: red cap over
    /// spiky brown hair, big glossy anime eyes with highlights, white tee over jeans with
    /// rolled cuffs, chunky red/white sneakers, round orange backpack, spray can for menus.
    /// Articulated: hips, knees, shoulders, elbows — SS-style run cycle with squash &amp;
    /// stretch, bounce, head bob and auto-blinking. Same pivot skeleton as v4, so every
    /// gameplay system (PlayerController / Chaser / cameras) works unchanged.</summary>
    public class CharacterRig : MonoBehaviour
    {
        public Transform body, head, armL, armR, legL, legR, torso, tail, board, jet, flameL, flameR, bag;
        public Transform kneeL, kneeR, elbL, elbR, handR;
        public string kind = "boy";

        Transform[] blinkers;          // eye groups (blink by scaling Y)
        Vector3[] blinkBase;
        Transform[] glints;            // eye highlights (hide while blinking)

        static Transform Pivot(Transform parent, string name, Vector3 pos)
        {
            var go = new GameObject(name);
            go.transform.SetParent(parent, false);
            go.transform.localPosition = pos;
            return go.transform;
        }

        static Color C(int r, int g, int b) { return new Color32((byte)r, (byte)g, (byte)b, 255); }

        // ================================================== BOY (hero — realistic human)
        public static CharacterRig BuildBoy(Transform parent)
        {
            var root = new GameObject("Boy");
            root.transform.SetParent(parent, false);
            var rig = root.AddComponent<CharacterRig>();
            rig.kind = "boy";

            // ---- skeletal pivots — MUST mirror HumanRig.RestPos (bone order + world positions) ----
            rig.body = Pivot(root.transform, "body", Vector3.zero);
            rig.torso = Pivot(rig.body, "torso", new Vector3(0f, 1.02f, 0f));
            rig.head = Pivot(rig.torso, "head", new Vector3(0f, 0.54f, 0f)); // world rest (0, 1.56, 0)

            var armT = new Transform[2]; var elbT = new Transform[2]; var handT = new Transform[2];
            var legT = new Transform[2]; var kneeT = new Transform[2]; var footT = new Transform[2];
            for (int s = 0; s < 2; s++)
            {
                float sx = s == 0 ? -1f : 1f;
                string side = s == 0 ? "L" : "R";

                var arm = Pivot(rig.body, "arm" + side, new Vector3(sx * 0.205f, 1.445f, 0f));
                var elb = Pivot(arm, "elbow" + side, new Vector3(sx * 0.010f, -0.300f, 0f));
                var hand = Pivot(elb, "hand" + side, new Vector3(sx * 0.007f, -0.215f, 0f));
                armT[s] = arm; elbT[s] = elb; handT[s] = hand;

                var leg = Pivot(rig.body, "leg" + side, new Vector3(sx * 0.105f, 0.960f, 0f));
                var knee = Pivot(leg, "knee" + side, new Vector3(0f, -0.460f, 0f));
                var foot = Pivot(knee, "foot" + side, new Vector3(0f, -0.425f, 0f));
                legT[s] = leg; kneeT[s] = knee; footT[s] = foot;
            }
            rig.armL = armT[0]; rig.armR = armT[1];
            rig.elbL = elbT[0]; rig.elbR = elbT[1];
            rig.legL = legT[0]; rig.legR = legT[1];
            rig.kneeL = kneeT[0]; rig.kneeR = kneeT[1];
            rig.handR = handT[1];

            // ---- one continuous skinned body + static head (procedural realistic human) ----
            Transform[] bones =
            {
                rig.body, rig.torso,
                armT[0], elbT[0], handT[0],
                armT[1], elbT[1], handT[1],
                legT[0], kneeT[0], footT[0],
                legT[1], kneeT[1], footT[1],
            };
            HumanRig.Build(bones, rig.head);

            // ---- blinkable eyes (decals over the static face; scale-Y blink like v5) ----
            var eyeDark = AnimeMesh.Shade(C(0x24, 0x1A, 0x12));
            var eyeWhite = AnimeMesh.Shade(C(0xE8, 0xE4, 0xDA));
            var blinkList = new List<Transform>();
            var blinkBaseList = new List<Vector3>();
            for (int s = 0; s < 2; s++)
            {
                float sx = s == 0 ? -1f : 1f;
                var eg = Pivot(rig.head, "eyeGrp" + s, new Vector3(sx * 0.037f, 0.092f, 0.100f));
                var be = new AnimeMesh.Build(0f); be.cullBack = true;
                int mI = be.Mat(eyeDark), mG = be.Mat(eyeWhite);
                be.Ball(mI, Vector3.zero, new Vector3(0.019f, 0.012f, 0.008f), Quaternion.identity);
                be.Ball(mG, new Vector3(-sx * 0.007f, 0.004f, 0.005f), new Vector3(0.006f, 0.005f, 0.004f), Quaternion.identity);
                be.Done(eg, "eye" + s);
                blinkList.Add(eg);
                blinkBaseList.Add(Vector3.one);
            }
            rig.blinkers = blinkList.ToArray();
            rig.blinkBase = blinkBaseList.ToArray();
            rig.glints = null;

            // ---- hoverboard (hidden gameplay prop, kept from v5) ----
            rig.board = Pivot(root.transform, "board", new Vector3(0f, 0.10f, 0f));
            {
                var b = new AnimeMesh.Build(0f);
                int mC = b.Mat(AnimeMesh.Shade(C(0xC0, 0x39, 0x2F))), mP = b.Mat(AnimeMesh.Shade(C(0xC9, 0x7B, 0x2D)));
                b.Ball(mC, Vector3.zero, new Vector3(0.47f, 0.05f, 0.24f), Quaternion.identity, 20);
                b.Ball(mP, new Vector3(0f, 0.006f, 0f), new Vector3(0.405f, 0.05f, 0.215f), Quaternion.identity, 20);
                b.Done(rig.board, "hoverboard");
            }
            rig.board.gameObject.SetActive(false);

            // ---- jetpack (hidden gameplay prop) ----
            rig.jet = Pivot(rig.body, "jet", Vector3.zero);
            {
                var b = new AnimeMesh.Build(0f);
                int mP = b.Mat(AnimeMesh.Shade(C(0xC9, 0x7B, 0x2D))), mPD = b.Mat(AnimeMesh.Shade(C(0xA8, 0x60, 0x1F)));
                var prof = new[] { new Vector2(-0.17f, 0.02f), new Vector2(-0.14f, 0.11f), new Vector2(0.0f, 0.13f), new Vector2(0.14f, 0.11f), new Vector2(0.17f, 0.02f) };
                b.Rev(mP, new Vector3(-0.17f, 1.24f, -0.22f), Quaternion.identity, Vector3.one, prof, 16);
                b.Rev(mP, new Vector3(0.17f, 1.24f, -0.22f), Quaternion.identity, Vector3.one, prof, 16);
                b.Ball(mPD, new Vector3(-0.17f, 1.415f, -0.22f), new Vector3(0.09f, 0.06f, 0.09f), Quaternion.identity);
                b.Ball(mPD, new Vector3(0.17f, 1.415f, -0.22f), new Vector3(0.09f, 0.06f, 0.09f), Quaternion.identity);
                b.Done(rig.jet, "jetpack");
            }
            var fl = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            Object.Destroy(fl.GetComponent<Collider>());
            fl.name = "flameL";
            fl.transform.SetParent(rig.jet, false);
            fl.transform.localPosition = new Vector3(-0.17f, 0.98f, -0.22f);
            fl.transform.localScale = new Vector3(0.09f, 0.24f, 0.09f);
            fl.GetComponent<MeshRenderer>().sharedMaterial = Fx.MatGlow(C(0xFF, 0xB0, 0x54));
            rig.flameL = fl.transform;
            var fr = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            Object.Destroy(fr.GetComponent<Collider>());
            fr.name = "flameR";
            fr.transform.SetParent(rig.jet, false);
            fr.transform.localPosition = new Vector3(0.17f, 0.98f, -0.22f);
            fr.transform.localScale = new Vector3(0.09f, 0.24f, 0.09f);
            fr.GetComponent<MeshRenderer>().sharedMaterial = Fx.MatGlow(C(0xFF, 0xD2, 0x4A));
            rig.flameR = fr.transform;
            rig.jet.gameObject.SetActive(false);

            // ---- spray can in the right hand (menu poses only) ----
            rig.bag = Pivot(rig.elbR, "bag", Vector3.zero);
            {
                var b = new AnimeMesh.Build(0f);
                int mP = b.Mat(AnimeMesh.Shade(C(0xC9, 0x7B, 0x2D))), mW = b.Mat(AnimeMesh.Shade(C(0xE8, 0xE4, 0xDA))), mD = b.Mat(AnimeMesh.Shade(C(0x24, 0x1A, 0x12)));
                b.Rev(mP, new Vector3(0f, -0.24f, 0.05f), Quaternion.identity, Vector3.one,
                      new[] { new Vector2(-0.06f, 0.060f), new Vector2(0f, 0.066f), new Vector2(0.06f, 0.060f) });
                b.Ball(mW, new Vector3(0f, -0.19f, 0.05f), new Vector3(0.040f, 0.026f, 0.040f), Quaternion.identity);
                b.Ball(mD, new Vector3(0f, -0.165f, 0.05f), new Vector3(0.016f, 0.020f, 0.016f), Quaternion.identity);
                b.Done(rig.bag, "sprayCan");
            }
            rig.bag.gameObject.SetActive(false);

            return rig;
        }

        // ================================================== INSPECTOR (guard)
        public static CharacterRig BuildInspector(Transform parent)
        {
            var root = new GameObject("Inspector");
            root.transform.SetParent(parent, false);
            var rig = root.AddComponent<CharacterRig>();
            rig.kind = "guard";

            var skin = AnimeMesh.Shade(C(0xFF, 0xC9, 0xA0));
            var nose = AnimeMesh.Shade(C(0xF0, 0xB0, 0x88));
            var jacket = AnimeMesh.Shade(C(0x7A, 0x8A, 0x46));
            var pants = AnimeMesh.Shade(C(0x4A, 0x55, 0x68));
            var boots = AnimeMesh.Shade(C(0x26, 0x29, 0x32));
            var dark = AnimeMesh.Shade(C(0x26, 0x2C, 0x3C));
            var capB = AnimeMesh.Shade(C(0x3E, 0x6F, 0xE0));
            var capD = AnimeMesh.Shade(C(0x2A, 0x4A, 0xA0));
            var grey = AnimeMesh.Shade(C(0x9A, 0xA0, 0xA8));
            var gold = AnimeMesh.Shade(C(0xFF, 0xD2, 0x3E));
            var white = AnimeMesh.Shade(C(0xFA, 0xFA, 0xFA));

            rig.body = Pivot(root.transform, "body", Vector3.zero);

            // legs — pants + boots in one continuous group per hip
            for (int s = 0; s < 2; s++)
            {
                float sx = s == 0 ? -1f : 1f;
                string side = s == 0 ? "L" : "R";
                var hip = Pivot(rig.body, "leg" + (s == 0 ? "L" : "R"), new Vector3(sx * 0.17f, 0.82f, 0f));
                if (s == 0) rig.legL = hip; else rig.legR = hip;
                var bT = new AnimeMesh.Build(0f);
                int mP = bT.Mat(pants), mB = bT.Mat(boots), mD = bT.Mat(dark);
                bT.Rev(mP, Vector3.zero, Quaternion.identity, new Vector3(1f, 1f, 0.9f),
                       new[] { new Vector2(0.06f, 0.16f), new Vector2(-0.14f, 0.15f), new Vector2(-0.26f, 0.142f) });
                bT.Ball(mB, new Vector3(0f, -0.30f, 0.05f), new Vector3(0.15f, 0.11f, 0.24f), Quaternion.identity);
                bT.Ball(mD, new Vector3(0f, -0.385f, 0.06f), new Vector3(0.16f, 0.04f, 0.26f), Quaternion.identity);
                bT.Done(hip, "leg" + side);
            }

            // torso — big belly, jacket, belt, badge
            rig.torso = Pivot(rig.body, "torso", new Vector3(0f, 0.82f, 0f));
            {
                var b = new AnimeMesh.Build(0f);
                int mJ = b.Mat(jacket), mP = b.Mat(pants), mD = b.Mat(dark), mG = b.Mat(gold);
                b.Ball(mP, new Vector3(0f, -0.12f, 0f), new Vector3(0.30f, 0.15f, 0.28f), Quaternion.identity);
                b.Ball(mJ, new Vector3(0f, 0.24f, 0f), new Vector3(0.42f, 0.40f, 0.40f), Quaternion.identity);
                b.Ball(mJ, new Vector3(0f, 0.50f, 0.01f), new Vector3(0.37f, 0.30f, 0.37f), Quaternion.identity);
                b.Ball(mD, new Vector3(0f, 0.03f, 0f), new Vector3(0.425f, 0.062f, 0.405f), Quaternion.identity);
                b.Ball(mG, new Vector3(0f, 0.03f, 0.40f), new Vector3(0.06f, 0.05f, 0.03f), Quaternion.identity);
                b.Ball(mG, new Vector3(-0.14f, 0.54f, 0.345f), new Vector3(0.055f, 0.055f, 0.03f), Quaternion.Euler(0f, -18f, 0f));
                b.Done(rig.torso, "torsoGroup");
            }

            // arms
            for (int s = 0; s < 2; s++)
            {
                float sx = s == 0 ? -1f : 1f;
                string side = s == 0 ? "L" : "R";
                var sh = Pivot(rig.body, "arm" + (s == 0 ? "L" : "R"), new Vector3(sx * 0.40f, 1.36f, 0f));
                if (s == 0) rig.armL = sh; else rig.armR = sh;
                var bU = new AnimeMesh.Build(0f);
                int mJ = bU.Mat(jacket);
                bU.Ball(mJ, Vector3.zero, new Vector3(0.15f, 0.15f, 0.14f), Quaternion.identity);
                bU.Rev(mJ, Vector3.zero, Quaternion.identity, new Vector3(1f, 1f, 0.92f),
                       new[] { new Vector2(0.02f, 0.145f), new Vector2(-0.16f, 0.132f), new Vector2(-0.28f, 0.124f) });
                bU.Done(sh, "upperArm" + side);

                var elb = Pivot(sh, "elbow", new Vector3(0f, -0.30f, 0f));
                if (s == 0) rig.elbL = elb; else rig.elbR = elb;
                var bF = new AnimeMesh.Build(0f);
                int mSk = bF.Mat(skin);
                bF.Rev(mSk, Vector3.zero, Quaternion.identity, new Vector3(1f, 1f, 0.92f),
                       new[] { new Vector2(0.01f, 0.118f), new Vector2(-0.12f, 0.106f) });
                bF.Ball(mSk, new Vector3(0f, -0.22f, 0f), new Vector3(0.135f, 0.135f, 0.13f), Quaternion.identity);
                if (s == 1)
                {
                    int mGy = bF.Mat(grey), mDk = bF.Mat(dark);
                    bF.Rev(mGy, new Vector3(0f, -0.42f, 0.10f), Quaternion.Euler(20f, 0f, 0f), Vector3.one,
                           new[] { new Vector2(-0.16f, 0.026f), new Vector2(0.16f, 0.026f) });
                    bF.Ball(mDk, new Vector3(0f, -0.575f, 0.155f), new Vector3(0.034f, 0.034f, 0.034f), Quaternion.identity);
                    rig.handR = elb;
                }
                bF.Done(elb, "foreArm" + side);
            }

            // head — round, mustache, blue cap
            rig.head = Pivot(rig.body, "head", new Vector3(0f, 1.50f, 0f));
            {
                var b = new AnimeMesh.Build(0f);
                int mSk = b.Mat(skin), mN = b.Mat(nose), mW = b.Mat(white), mD = b.Mat(dark);
                int mC = b.Mat(capB), mCD = b.Mat(capD), mGy = b.Mat(grey);
                b.Ball(mSk, new Vector3(0f, 0.18f, 0.01f), new Vector3(0.44f, 0.42f, 0.42f), Quaternion.identity);
                b.Ball(mSk, new Vector3(-0.42f, 0.16f, 0.01f), new Vector3(0.045f, 0.06f, 0.045f), Quaternion.identity);
                b.Ball(mSk, new Vector3(0.42f, 0.16f, 0.01f), new Vector3(0.045f, 0.06f, 0.045f), Quaternion.identity);
                // bushy white mustache (three puffs)
                b.Ball(mW, new Vector3(-0.075f, 0.10f, 0.365f), new Vector3(0.09f, 0.045f, 0.05f), Quaternion.identity);
                b.Ball(mW, new Vector3(0.075f, 0.10f, 0.365f), new Vector3(0.09f, 0.045f, 0.05f), Quaternion.identity);
                b.Ball(mW, new Vector3(0f, 0.115f, 0.375f), new Vector3(0.06f, 0.04f, 0.045f), Quaternion.identity);
                b.Ball(mN, new Vector3(0f, 0.17f, 0.395f), new Vector3(0.055f, 0.05f, 0.05f), Quaternion.identity);
                // eyes
                b.Ball(mW, new Vector3(-0.09f, 0.26f, 0.355f), new Vector3(0.06f, 0.07f, 0.03f), Quaternion.identity);
                b.Ball(mW, new Vector3(0.09f, 0.26f, 0.355f), new Vector3(0.06f, 0.07f, 0.03f), Quaternion.identity);
                b.Ball(mD, new Vector3(-0.09f, 0.255f, 0.375f), new Vector3(0.028f, 0.034f, 0.02f), Quaternion.identity);
                b.Ball(mD, new Vector3(0.09f, 0.255f, 0.375f), new Vector3(0.028f, 0.034f, 0.02f), Quaternion.identity);
                b.Ball(mGy, new Vector3(-0.09f, 0.345f, 0.345f), new Vector3(0.085f, 0.028f, 0.03f), Quaternion.Euler(0f, 8f, 8f));
                b.Ball(mGy, new Vector3(0.09f, 0.345f, 0.345f), new Vector3(0.085f, 0.028f, 0.03f), Quaternion.Euler(0f, -8f, -8f));
                // blue cap with dark peak
                b.Ball(mC, new Vector3(0f, 0.42f, 0f), new Vector3(0.46f, 0.26f, 0.46f), Quaternion.identity, 20, 0f, 360f, 95f);
                b.Ball(mCD, new Vector3(0f, 0.375f, 0.36f), new Vector3(0.26f, 0.035f, 0.16f), Quaternion.Euler(-10f, 0f, 0f));
                b.Ball(mCD, new Vector3(0f, 0.675f, 0f), new Vector3(0.045f, 0.045f, 0.045f), Quaternion.identity);
                b.Done(rig.head, "headGroup");
            }

            return rig;
        }

        // ================================================== DOG
        public static CharacterRig BuildDog(Transform parent)
        {
            var root = new GameObject("Dog");
            root.transform.SetParent(parent, false);
            root.transform.localScale = new Vector3(0.85f, 0.85f, 0.85f);
            var rig = root.AddComponent<CharacterRig>();
            rig.kind = "dog";

            var fur = AnimeMesh.Shade(C(0xD9, 0xB3, 0x80));
            var furD = AnimeMesh.Shade(C(0xB0, 0x8C, 0x5E));
            var dark = AnimeMesh.Shade(C(0x26, 0x2C, 0x3C));
            var collar = AnimeMesh.Shade(C(0xE8, 0x40, 0x40));
            var gold = AnimeMesh.Shade(C(0xFF, 0xD2, 0x3E));

            rig.body = Pivot(root.transform, "body", Vector3.zero);
            {
                var b = new AnimeMesh.Build(0f);
                int mF = b.Mat(fur), mFD = b.Mat(furD), mD = b.Mat(dark), mC = b.Mat(collar), mG = b.Mat(gold);
                b.Ball(mF, new Vector3(0f, 0.42f, 0f), new Vector3(0.16f, 0.15f, 0.34f), Quaternion.identity);
                b.Ball(mF, new Vector3(0f, 0.45f, 0.14f), new Vector3(0.16f, 0.15f, 0.18f), Quaternion.identity);
                b.Ball(mF, new Vector3(0f, 0.585f, 0.30f), new Vector3(0.15f, 0.145f, 0.15f), Quaternion.identity);
                b.Ball(mFD, new Vector3(0f, 0.545f, 0.425f), new Vector3(0.07f, 0.055f, 0.085f), Quaternion.identity);
                b.Ball(mD, new Vector3(0f, 0.575f, 0.50f), new Vector3(0.042f, 0.038f, 0.04f), Quaternion.identity);
                var eL = Quaternion.Euler(0f, 0f, 25f);
                var eR = Quaternion.Euler(0f, 0f, -25f);
                b.Ball(mFD, new Vector3(-0.085f, 0.70f, 0.28f), new Vector3(0.05f, 0.10f, 0.045f), eL);
                b.Ball(mFD, new Vector3(0.085f, 0.70f, 0.28f), new Vector3(0.05f, 0.10f, 0.045f), eR);
                b.Ball(mD, new Vector3(-0.06f, 0.60f, 0.435f), new Vector3(0.035f, 0.04f, 0.025f), Quaternion.identity);
                b.Ball(mD, new Vector3(0.06f, 0.60f, 0.435f), new Vector3(0.035f, 0.04f, 0.025f), Quaternion.identity);
                b.Ball(mC, new Vector3(0f, 0.475f, 0.245f), new Vector3(0.125f, 0.032f, 0.125f), Quaternion.identity);
                b.Ball(mG, new Vector3(0f, 0.425f, 0.31f), new Vector3(0.042f, 0.042f, 0.02f), Quaternion.identity);
                b.Done(rig.body, "dogBody");
            }

            // four legs — front pair animated
            for (int s = 0; s < 2; s++)
            {
                float sx = s == 0 ? -1f : 1f;
                var leg = Pivot(rig.body, "leg" + s, new Vector3(sx * 0.09f, 0.34f, 0.22f));
                if (s == 0) rig.legL = leg; else rig.legR = leg;
                var b = new AnimeMesh.Build(0f);
                int mF = b.Mat(fur), mFD = b.Mat(furD);
                b.Rev(mF, Vector3.zero, Quaternion.identity, Vector3.one,
                      new[] { new Vector2(0.02f, 0.075f), new Vector2(-0.14f, 0.066f) });
                b.Ball(mFD, new Vector3(0f, -0.185f, 0.012f), new Vector3(0.07f, 0.05f, 0.085f), Quaternion.identity);
                b.Done(leg, "fleg" + s);

                var bl = Pivot(rig.body, "bleg" + s, new Vector3(sx * 0.09f, 0.34f, -0.22f));
                var bb = new AnimeMesh.Build(0f);
                int mF2 = bb.Mat(fur), mFD2 = bb.Mat(furD);
                bb.Rev(mF2, Vector3.zero, Quaternion.identity, Vector3.one,
                       new[] { new Vector2(0.02f, 0.075f), new Vector2(-0.14f, 0.066f) });
                bb.Ball(mFD2, new Vector3(0f, -0.185f, 0.012f), new Vector3(0.07f, 0.05f, 0.085f), Quaternion.identity);
                bb.Done(bl, "bleg" + s);
            }

            rig.tail = Pivot(rig.body, "tail", new Vector3(0f, 0.52f, -0.32f));
            {
                var b = new AnimeMesh.Build(0f);
                b.Spike(b.Mat(fur), Vector3.zero, new Vector3(0f, 0.55f, -0.85f), 0.24f, 0.045f);
                b.Done(rig.tail, "tail");
            }
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
                    if (bag != null) bag.gameObject.SetActive(false);
                    break;
                }
                case "spray":
                {
                    float b = Mathf.Sin(t * 2.0f);
                    body.localPosition = new Vector3(0f, b * 0.010f, 0f);
                    armR.localRotation = Quaternion.Euler(-72f + Mathf.Sin(t * 9f) * 6f, 0f, -6f);
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
