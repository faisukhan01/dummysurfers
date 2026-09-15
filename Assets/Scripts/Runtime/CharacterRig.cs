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

        // ================================================== BOY (hero)
        public static CharacterRig BuildBoy(Transform parent)
        {
            var root = new GameObject("Boy");
            root.transform.SetParent(parent, false);
            var rig = root.AddComponent<CharacterRig>();
            rig.kind = "boy";

            var skin = AnimeMesh.Shade(C(0xFF, 0xC8, 0x9C));
            var nose = AnimeMesh.Shade(C(0xF0, 0xAE, 0x84));
            var hair = AnimeMesh.Shade(C(0x4A, 0x2A, 0x16));
            var capR = AnimeMesh.Shade(C(0xE2, 0x3B, 0x3B));
            var capD = AnimeMesh.Shade(C(0xC2, 0x2F, 0x2F));
            var capW = AnimeMesh.Shade(C(0xFB, 0xFA, 0xF6));
            var tee = AnimeMesh.Shade(C(0xFA, 0xF7, 0xEF));
            var slv = AnimeMesh.Shade(C(0x3F, 0x6B, 0xB5));
            var slvD = AnimeMesh.Shade(C(0x33, 0x58, 0x9A));
            var jeans = AnimeMesh.Shade(C(0x4C, 0x6F, 0xB0));
            var cuff = AnimeMesh.Shade(C(0x6C, 0x8F, 0xCB));
            var shoeR = AnimeMesh.Shade(C(0xD8, 0x35, 0x3A));
            var shoeW = AnimeMesh.Shade(C(0xF5, 0xF3, 0xEC));
            var pack = AnimeMesh.Shade(C(0xF2, 0x80, 0x2F));
            var packD = AnimeMesh.Shade(C(0xD9, 0x6A, 0x22));
            var iris = AnimeMesh.Shade(C(0x3A, 0x24, 0x14));
            var pupil = AnimeMesh.Shade(C(0x17, 0x10, 0x0B));
            var brow = AnimeMesh.Shade(C(0x33, 0x20, 0x1A));
            var mouth = AnimeMesh.Shade(C(0x8A, 0x32, 0x2B));
            var blush = AnimeMesh.Shade(C(0xF5, 0xA9, 0xA0));

            rig.body = Pivot(root.transform, "body", Vector3.zero);

            // ============================ LEGS (hip -> knee -> chunky sneaker)
            for (int s = 0; s < 2; s++)
            {
                float sx = s == 0 ? -1f : 1f;
                string side = s == 0 ? "L" : "R";

                var hip = Pivot(rig.body, "leg" + s, new Vector3(sx * 0.135f, 0.71f, 0f));
                if (s == 0) rig.legL = hip; else rig.legR = hip;

                // thigh (jeans, continuous lathe)
                var bT = new AnimeMesh.Build(0f);
                int mJ = bT.Mat(jeans);
                bT.Rev(mJ, Vector3.zero, Quaternion.identity, new Vector3(1f, 1f, 0.88f),
                       new[] { new Vector2(0.05f, 0.128f), new Vector2(-0.08f, 0.121f), new Vector2(-0.20f, 0.104f), new Vector2(-0.30f, 0.098f) });
                bT.Done(hip, "thigh" + side);

                // shin + rolled cuff + chunky sneaker (bend with the knee)
                var knee = Pivot(hip, "knee", new Vector3(0f, -0.26f, 0f));
                if (s == 0) rig.kneeL = knee; else rig.kneeR = knee;
                var bS = new AnimeMesh.Build(0f);
                int mJ2 = bS.Mat(jeans), mC = bS.Mat(cuff), mR = bS.Mat(shoeR), mW = bS.Mat(shoeW);
                bS.Ball(mJ2, new Vector3(0f, 0.005f, 0f), new Vector3(0.096f, 0.096f, 0.088f), Quaternion.identity);
                bS.Rev(mJ2, Vector3.zero, Quaternion.identity, new Vector3(1f, 1f, 0.88f),
                       new[] { new Vector2(0.0f, 0.092f), new Vector2(-0.14f, 0.086f), new Vector2(-0.22f, 0.082f) });
                bS.Rev(mC, Vector3.zero, Quaternion.identity, new Vector3(1f, 1f, 0.88f),
                       new[] { new Vector2(-0.13f, 0.099f), new Vector2(-0.19f, 0.095f), new Vector2(-0.235f, 0.088f) });
                // sneaker — smooth overlapping ellipsoids (red upper, white toe/sole)
                bS.Ball(mW, new Vector3(0f, -0.24f, -0.06f), new Vector3(0.055f, 0.05f, 0.06f), Quaternion.identity);
                bS.Ball(mR, new Vector3(0f, -0.245f, 0.03f), new Vector3(0.10f, 0.075f, 0.14f), Quaternion.identity);
                bS.Ball(mW, new Vector3(0f, -0.265f, 0.145f), new Vector3(0.082f, 0.06f, 0.075f), Quaternion.identity);
                bS.Ball(mW, new Vector3(0f, -0.30f, 0.045f), new Vector3(0.115f, 0.032f, 0.19f), Quaternion.identity);
                bS.Done(knee, "shin" + side);
            }

            // ============================ TORSO (tee over jeans + backpack)
            rig.torso = Pivot(rig.body, "torso", new Vector3(0f, 0.75f, 0f));
            {
                var b = new AnimeMesh.Build(0f);
                int mJ = b.Mat(jeans), mT = b.Mat(tee), mS = b.Mat(skin), mP = b.Mat(pack), mPD = b.Mat(packD);
                // pelvis bridge between the thigh tops
                b.Ball(mJ, new Vector3(0f, -0.07f, 0f), new Vector3(0.155f, 0.115f, 0.135f), Quaternion.identity);
                // white tee — continuous egg, hem overhangs the jeans
                b.Rev(mT, Vector3.zero, Quaternion.identity, new Vector3(1f, 1f, 0.84f),
                      new[] { new Vector2(-0.06f, 0.205f), new Vector2(0.02f, 0.185f), new Vector2(0.12f, 0.178f), new Vector2(0.26f, 0.196f), new Vector2(0.38f, 0.215f), new Vector2(0.46f, 0.205f), new Vector2(0.505f, 0.16f), new Vector2(0.535f, 0.09f) });
                // collar + neck
                b.Rev(mT, Vector3.zero, Quaternion.identity, new Vector3(1f, 1f, 0.9f),
                      new[] { new Vector2(0.495f, 0.098f), new Vector2(0.525f, 0.092f), new Vector2(0.545f, 0.088f) });
                b.Rev(mS, Vector3.zero, Quaternion.identity, Vector3.one,
                      new[] { new Vector2(0.44f, 0.075f), new Vector2(0.54f, 0.07f), new Vector2(0.62f, 0.078f) });
                // round orange backpack + pocket + straps (hero piece from behind)
                b.Ball(mP, new Vector3(0f, 0.24f, -0.235f), new Vector3(0.235f, 0.26f, 0.13f), Quaternion.identity);
                b.Ball(mPD, new Vector3(0f, 0.11f, -0.325f), new Vector3(0.15f, 0.10f, 0.05f), Quaternion.identity);
                var st = Quaternion.Euler(-24f, 0f, 0f);
                b.Ball(mPD, new Vector3(-0.12f, 0.395f, 0f), new Vector3(0.035f, 0.115f, 0.04f), st);
                b.Ball(mPD, new Vector3(0.12f, 0.395f, 0f), new Vector3(0.035f, 0.115f, 0.04f), st);
                b.Done(rig.torso, "torsoGroup");
            }

            // ============================ ARMS (shoulder -> elbow -> hand)
            for (int s = 0; s < 2; s++)
            {
                float sx = s == 0 ? -1f : 1f;
                string side = s == 0 ? "L" : "R";

                var sh = Pivot(rig.body, "arm" + s, new Vector3(sx * 0.275f, 1.25f, 0.01f));
                if (s == 0) rig.armL = sh; else rig.armR = sh;

                // raglan blue sleeve + white shoulder cap
                var bU = new AnimeMesh.Build(0f);
                int mSl = bU.Mat(slv), mSd = bU.Mat(slvD);
                bU.Ball(mSl, new Vector3(-sx * 0.02f, 0.005f, 0f), new Vector3(0.105f, 0.105f, 0.10f), Quaternion.identity);
                bU.Rev(mSl, Vector3.zero, Quaternion.identity, new Vector3(1f, 1f, 0.92f),
                       new[] { new Vector2(0.02f, 0.102f), new Vector2(-0.08f, 0.094f), new Vector2(-0.17f, 0.088f) });
                bU.Rev(mSd, Vector3.zero, Quaternion.identity, new Vector3(1f, 1f, 0.92f),
                       new[] { new Vector2(-0.155f, 0.092f), new Vector2(-0.185f, 0.088f), new Vector2(-0.205f, 0.082f) });
                bU.Done(sh, "upperArm" + side);

                var elb = Pivot(sh, "elbow", new Vector3(0f, -0.205f, 0f));
                if (s == 0) rig.elbL = elb; else rig.elbR = elb;
                var bF = new AnimeMesh.Build(0f);
                int mSk = bF.Mat(skin);
                bF.Ball(mSk, new Vector3(0f, 0.005f, 0f), new Vector3(0.078f, 0.078f, 0.074f), Quaternion.identity);
                bF.Rev(mSk, Vector3.zero, Quaternion.identity, new Vector3(1f, 1f, 0.92f),
                       new[] { new Vector2(0.0f, 0.074f), new Vector2(-0.10f, 0.066f), new Vector2(-0.15f, 0.060f) });
                bF.Ball(mSk, new Vector3(0f, -0.20f, 0.008f), new Vector3(0.088f, 0.095f, 0.088f), Quaternion.identity);
                bF.Done(elb, "foreArm" + side);
                if (s == 1) rig.handR = elb;
            }

            // spray can in the right hand (menu poses only)
            rig.bag = Pivot(rig.elbR, "bag", Vector3.zero);
            {
                var b = new AnimeMesh.Build(0f);
                int mP = b.Mat(pack), mW = b.Mat(shoeW), mD = b.Mat(pupil);
                b.Rev(mP, new Vector3(0f, -0.26f, 0.05f), Quaternion.identity, Vector3.one,
                      new[] { new Vector2(-0.07f, 0.070f), new Vector2(0f, 0.078f), new Vector2(0.07f, 0.070f) });
                b.Ball(mW, new Vector3(0f, -0.185f, 0.05f), new Vector3(0.045f, 0.03f, 0.045f), Quaternion.identity);
                b.Ball(mD, new Vector3(0f, -0.155f, 0.05f), new Vector3(0.018f, 0.022f, 0.018f), Quaternion.identity);
                b.Done(rig.bag, "sprayCan");
            }
            rig.bag.gameObject.SetActive(false);

            // ============================ HEAD (the hero — huge chibi head)
            rig.head = Pivot(rig.body, "head", new Vector3(0f, 1.37f, 0f));
            {
                var b = new AnimeMesh.Build(0f);
                int mSk = b.Mat(skin), mH = b.Mat(hair), mC = b.Mat(capR), mCD = b.Mat(capD), mCW = b.Mat(capW);
                int mN = b.Mat(nose), mBr = b.Mat(brow), mM = b.Mat(mouth), mB = b.Mat(blush), mW = b.Mat(capW);

                // skull — egg with a chin
                b.Rev(mSk, new Vector3(0f, 0.10f, 0.01f), Quaternion.identity, new Vector3(1f, 1f, 0.965f),
                      new[] { new Vector2(-0.145f, 0.024f), new Vector2(-0.10f, 0.135f), new Vector2(-0.045f, 0.23f), new Vector2(0.01f, 0.31f), new Vector2(0.09f, 0.365f), new Vector2(0.17f, 0.398f), new Vector2(0.25f, 0.405f), new Vector2(0.33f, 0.372f), new Vector2(0.41f, 0.292f), new Vector2(0.47f, 0.18f), new Vector2(0.51f, 0.06f) });
                // ears
                b.Ball(mSk, new Vector3(-0.375f, 0.155f, 0.01f), new Vector3(0.045f, 0.062f, 0.045f), Quaternion.identity);
                b.Ball(mSk, new Vector3(0.375f, 0.155f, 0.01f), new Vector3(0.045f, 0.062f, 0.045f), Quaternion.identity);

                // hair — helmet shell hugging the skull, WIDE face opening at the front
                b.Ball(mH, new Vector3(0f, 0.235f, -0.01f), new Vector3(0.425f, 0.41f, 0.425f), Quaternion.identity, 22, 168f, 372f, 126f);
                // spiky crown — hair escaping under the cap rim (Jake style)
                for (int i = 0; i < 9; i++)
                {
                    float th = 160f + i * (220f / 8f);
                    float ra = th * Mathf.Deg2Rad;
                    var dir = new Vector3(Mathf.Cos(ra) * 1.0f, 0.38f + 0.10f * (i % 3), Mathf.Sin(ra) * 1.0f).normalized;
                    float len = 0.13f + 0.035f * ((i * 7) % 3);
                    b.Spike(mH, new Vector3(Mathf.Cos(ra) * 0.385f, 0.33f + 0.05f * (i % 2), Mathf.Sin(ra) * 0.385f), dir, len, 0.042f);
                }
                // nape spikes down the back
                for (int i = 0; i < 5; i++)
                {
                    float th = 205f + i * 32.5f;
                    float ra = th * Mathf.Deg2Rad;
                    b.Spike(mH, new Vector3(Mathf.Cos(ra) * 0.355f, 0.12f, Mathf.Sin(ra) * 0.355f),
                            new Vector3(Mathf.Cos(ra) * 0.35f, -1f, Mathf.Sin(ra) * 0.35f), 0.115f, 0.034f);
                }
                // side tufts in front of the ears (kept high so cheeks stay clear)
                b.Ball(mH, new Vector3(-0.33f, 0.24f, 0.05f), new Vector3(0.045f, 0.085f, 0.06f), Quaternion.identity);
                b.Ball(mH, new Vector3(0.33f, 0.24f, 0.05f), new Vector3(0.045f, 0.085f, 0.06f), Quaternion.identity);

                // cap — dome over the hair, stiff brim, white front panel, button
                b.Ball(mC, new Vector3(0f, 0.24f, -0.02f), new Vector3(0.455f, 0.40f, 0.445f), Quaternion.identity, 22, 0f, 360f, 70f);
                b.Ball(mCD, new Vector3(0f, 0.315f, 0.415f), new Vector3(0.17f, 0.03f, 0.125f), Quaternion.Euler(-16f, 0f, 0f));
                b.Ball(mCW, new Vector3(0f, 0.40f, 0.385f), new Vector3(0.135f, 0.095f, 0.05f), Quaternion.Euler(-20f, 0f, 0f));
                b.Ball(mCD, new Vector3(0f, 0.645f, -0.02f), new Vector3(0.042f, 0.042f, 0.042f), Quaternion.identity);

                // brows, nose, smile with tooth, blush
                b.Ball(mBr, new Vector3(-0.165f, 0.30f, 0.345f), new Vector3(0.09f, 0.024f, 0.028f), Quaternion.Euler(0f, 10f, 8f));
                b.Ball(mBr, new Vector3(0.165f, 0.30f, 0.345f), new Vector3(0.09f, 0.024f, 0.028f), Quaternion.Euler(0f, -10f, -8f));
                b.Ball(mN, new Vector3(0f, 0.145f, 0.40f), new Vector3(0.032f, 0.024f, 0.026f), Quaternion.identity);
                b.Ball(mM, new Vector3(0f, 0.075f, 0.39f), new Vector3(0.05f, 0.032f, 0.02f), Quaternion.identity);
                b.Ball(mW, new Vector3(0f, 0.092f, 0.397f), new Vector3(0.032f, 0.012f, 0.01f), Quaternion.identity);
                b.Ball(mB, new Vector3(-0.255f, 0.045f, 0.30f), new Vector3(0.052f, 0.028f, 0.012f), Quaternion.Euler(0f, 38f, 0f));
                b.Ball(mB, new Vector3(0.255f, 0.045f, 0.30f), new Vector3(0.052f, 0.028f, 0.012f), Quaternion.Euler(0f, -38f, 0f));
                b.Done(rig.head, "headGroup");

                // eyes — separate groups so they can blink (scale Y)
                var blinkList = new List<Transform>();
                var blinkBaseList = new List<Vector3>();
                for (int s = 0; s < 2; s++)
                {
                    float sx = s == 0 ? -1f : 1f;
                    var eg = Pivot(rig.head, "eyeGrp" + s, new Vector3(sx * 0.165f, 0.185f, 0.345f));
                    eg.localRotation = Quaternion.Euler(0f, sx * 6f, 0f);
                    var be = new AnimeMesh.Build(0f); be.cullBack = true; // winding probe: eyes keep default culling
                    int mWh = be.Mat(capW), mIr = be.Mat(iris), mPu = be.Mat(pupil);
                    be.Ball(mWh, Vector3.zero, new Vector3(0.10f, 0.125f, 0.05f), Quaternion.identity);
                    be.Ball(mIr, new Vector3(0f, -0.005f, 0.028f), new Vector3(0.075f, 0.10f, 0.026f), Quaternion.identity);
                    be.Ball(mPu, new Vector3(0f, -0.005f, 0.042f), new Vector3(0.032f, 0.048f, 0.014f), Quaternion.identity);
                    be.Ball(mWh, new Vector3(-0.022f, 0.035f, 0.05f), new Vector3(0.024f, 0.028f, 0.012f), Quaternion.identity);
                    be.Ball(mWh, new Vector3(0.028f, -0.035f, 0.046f), new Vector3(0.011f, 0.013f, 0.008f), Quaternion.identity);
                    be.Done(eg, "eye" + s);
                    blinkList.Add(eg);
                    blinkBaseList.Add(Vector3.one);
                }
                rig.blinkers = blinkList.ToArray();
                rig.blinkBase = blinkBaseList.ToArray();
                rig.glints = null; // glints flatten with the eye group while blinking
            }

            // ============================ hoverboard (hidden by default)
            rig.board = Pivot(root.transform, "board", new Vector3(0f, 0.10f, 0f));
            {
                var b = new AnimeMesh.Build(0f);
                int mC = b.Mat(capR), mP = b.Mat(pack);
                b.Ball(mC, Vector3.zero, new Vector3(0.47f, 0.05f, 0.24f), Quaternion.identity, 20);
                b.Ball(mP, new Vector3(0f, 0.006f, 0f), new Vector3(0.405f, 0.05f, 0.215f), Quaternion.identity, 20);
                b.Done(rig.board, "hoverboard");
            }
            rig.board.gameObject.SetActive(false);

            // ============================ jetpack (hidden by default)
            rig.jet = Pivot(rig.body, "jet", Vector3.zero);
            {
                var b = new AnimeMesh.Build(0f);
                int mP = b.Mat(pack), mPD = b.Mat(packD);
                var prof = new[] { new Vector2(-0.17f, 0.02f), new Vector2(-0.14f, 0.11f), new Vector2(0.0f, 0.13f), new Vector2(0.14f, 0.11f), new Vector2(0.17f, 0.02f) };
                b.Rev(mP, new Vector3(-0.17f, 1.24f, -0.28f), Quaternion.identity, Vector3.one, prof, 16);
                b.Rev(mP, new Vector3(0.17f, 1.24f, -0.28f), Quaternion.identity, Vector3.one, prof, 16);
                b.Ball(mPD, new Vector3(-0.17f, 1.415f, -0.28f), new Vector3(0.09f, 0.06f, 0.09f), Quaternion.identity);
                b.Ball(mPD, new Vector3(0.17f, 1.415f, -0.28f), new Vector3(0.09f, 0.06f, 0.09f), Quaternion.identity);
                b.Done(rig.jet, "jetpack");
            }
            var fl = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            Object.Destroy(fl.GetComponent<Collider>());
            fl.name = "flameL";
            fl.transform.SetParent(rig.jet, false);
            fl.transform.localPosition = new Vector3(-0.17f, 0.98f, -0.28f);
            fl.transform.localScale = new Vector3(0.09f, 0.24f, 0.09f);
            fl.GetComponent<MeshRenderer>().sharedMaterial = Fx.MatGlow(C(0xFF, 0xB0, 0x54));
            rig.flameL = fl.transform;
            var fr = GameObject.CreatePrimitive(PrimitiveType.Sphere);
            Object.Destroy(fr.GetComponent<Collider>());
            fr.name = "flameR";
            fr.transform.SetParent(rig.jet, false);
            fr.transform.localPosition = new Vector3(0.17f, 0.98f, -0.28f);
            fr.transform.localScale = new Vector3(0.09f, 0.24f, 0.09f);
            fr.GetComponent<MeshRenderer>().sharedMaterial = Fx.MatGlow(C(0xFF, 0xD2, 0x4A));
            rig.flameR = fr.transform;
            rig.jet.gameObject.SetActive(false);

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
