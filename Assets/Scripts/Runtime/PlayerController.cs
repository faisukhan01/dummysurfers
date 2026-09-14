using UnityEngine;

namespace DummySurfer
{
    /// <summary>3-lane runner: swipe lanes, jump, roll, run on train roofs, stumble/bounce,
    /// hoverboard / jetpack / magnet / x2 powerups, crash detection.</summary>
    public class PlayerController : MonoBehaviour
    {
        public static PlayerController I;

        public CharacterRig rig;
        public float x, y, z;
        public int laneTo;
        int laneFrom = 1; // sentinel
        float laneT = 1f;
        public float vy;
        public bool grounded = true;
        float groundY, coyote;
        public float h = 1.75f;
        float rollT;
        float stumbleAnimT;
        bool jetWasOn;
        GameObject blob;   // SS-style blob shadow

        static readonly Collider[] buf = new Collider[12];
        static readonly Collider[] pbuf = new Collider[12];

        public float EffSpeed
        {
            get
            {
                var g = GameManager.I;
                return g.Speed * (g.tStumble > 0 ? 0.74f : 1f);
            }
        }

        void Awake() { I = this; }

        public void Attach(CharacterRig r)
        {
            rig = r; rig.transform.SetParent(transform, false); rig.transform.localPosition = Vector3.zero;
            // blob shadow quad (always under the runner, scales with height)
            blob = GameObject.CreatePrimitive(PrimitiveType.Quad);
            Object.Destroy(blob.GetComponent<Collider>());
            blob.name = "~BlobShadow";
            var mr = blob.GetComponent<MeshRenderer>();
            mr.sharedMaterial = Fx.MatTex(Fx.SprShadowBlob().texture, true);
            mr.shadowCastingMode = UnityEngine.Rendering.ShadowCastingMode.Off;
            mr.receiveShadows = false;
            blob.transform.rotation = Quaternion.Euler(90f, 0f, 0f);
            blob.transform.localScale = new Vector3(1.1f, 1.1f, 1f);
            blob.transform.position = new Vector3(0f, 0.045f, 0f);
        }

        // ============================================== INPUT ENTRY POINTS
        public void OnLane(int dir)
        {
            var g = GameManager.I;
            if (g.st != GameManager.St.Run) return;
            int target = Mathf.Clamp(laneTo + dir, -1, 1);
            if (target == laneTo && laneT >= 1f) return;
            laneFrom = Mathf.RoundToInt(x / LaneW);
            laneTo = target;
            laneT = 0f;
        }

        public const float LaneW = 2.2f;

        public void OnJump()
        {
            var g = GameManager.I;
            if (g.st != GameManager.St.Run) return;
            if (g.tJet > 0) return;
            if (grounded || coyote > 0f)
            {
                vy = 14.6f;
                grounded = false;
                coyote = 0f;
                rollT = 0f;
                h = 1.75f;
                g.AddJump();
                Fx.Play("jump", 0.7f);
            }
        }

        public void OnRoll()
        {
            var g = GameManager.I;
            if (g.st != GameManager.St.Run) return;
            if (g.tJet > 0) return;
            rollT = 0.72f;
            h = 0.85f;
            if (!grounded) vy = -22f;
            Fx.Play("roll", 0.6f);
        }

        public void ActivateBoard()
        {
            var g = GameManager.I;
            if (g.st != GameManager.St.Run || g.tBoard > 0 || g.boardCount <= 0) return;
            g.boardCount--;
            g.tBoard = 30f;
            rig.SetBoard(true);
            g.Save();
            Fx.Play("board");
        }

        public void HeadStart()
        {
            var g = GameManager.I;
            if (g.st != GameManager.St.Run || g.boostCount <= 0 || g.tJet > 0) return;
            g.boostCount--;
            g.tJet = 7f;
            rig.SetJet(true);
            vy = 0f;
            g.Save();
            Fx.Play("power");
        }

        // ============================================== LOOP
        void Update()
        {
            var g = GameManager.I;
            if (g == null || rig == null) return;
            float dt = Time.deltaTime;

            // ---- blob shadow follows the ground under the runner
            if (blob != null)
            {
                bool show = g.st == GameManager.St.Run || g.st == GameManager.St.Dying || g.st == GameManager.St.Menu;
                blob.SetActive(show);
                blob.transform.position = new Vector3(x, groundY + 0.045f, z);
                float air = Mathf.Max(0f, y - groundY);
                float sc = Mathf.Clamp(1.15f - air * 0.12f, 0.45f, 1.15f);
                blob.transform.localScale = new Vector3(sc, sc, 1f);
            }

            if (g.st == GameManager.St.Menu || g.st == GameManager.St.Splash || g.st == GameManager.St.Loading
                || g.st == GameManager.St.High || g.st == GameManager.St.Results || g.st == GameManager.St.Missions)
            {
                // idle breathing handled by GameRoot camera pose; nothing physics-y
                return;
            }

            bool running = g.st == GameManager.St.Run;
            bool dying = g.st == GameManager.St.Dying;

            if (running)
            {
                z += EffSpeed * dt;

                // ---- lateral
                if (laneT < 1f)
                {
                    laneT = Mathf.Min(1f, laneT + dt * 7.5f);
                    float e = laneT * laneT * (3f - 2f * laneT);
                    x = Mathf.Lerp(laneFrom * LaneW, laneTo * LaneW, e);
                }

                // ---- roll timer
                if (rollT > 0f)
                {
                    rollT -= dt;
                    if (rollT <= 0f) h = 1.75f;
                }

                // ---- vertical / ground
                if (g.tJet > 0)
                {
                    grounded = false;
                    y = Mathf.MoveTowards(y, 7.2f, 16f * dt);
                    vy = 0f;
                }
                else
                {
                    RaycastHit hit;
                    Vector3 o = new Vector3(x, y + 1.8f, z + 0.4f);
                    if (Physics.Raycast(o, Vector3.down, out hit, 60f, Fx.MaskGround, QueryTriggerInteraction.Collide))
                        groundY = hit.point.y;
                    else groundY = 0f;

                    if (grounded)
                    {
                        if (groundY < y - 0.35f) { grounded = false; vy = 0f; }
                        else y = Mathf.MoveTowards(y, groundY, 30f * dt);
                    }
                    if (!grounded)
                    {
                        vy += -42f * dt;
                        y += vy * dt;
                        if (y <= groundY && vy <= 0f)
                        {
                            y = groundY; vy = 0f; grounded = true;
                            if (g.tJet <= 0f && rig != null) Fx.Play("roll", 0.25f);
                        }
                    }
                    coyote = grounded ? 0.12f : Mathf.Max(0f, coyote - dt);
                }

                // ---- jetpack flames toggle
                bool jetOn = g.tJet > 0f;
                if (jetOn != jetWasOn) { rig.SetJet(jetOn); jetWasOn = jetOn; }
                if (g.tBoard <= 0f && rig.board != null && rig.board.gameObject.activeSelf) rig.SetBoard(false);

                // ---- obstacle collisions
                if (g.tJet <= 0f && g.tInv <= 0f) CheckObstacles(g);

                // ---- pickups
                CheckPickups(g);
            }

            if (dying && g.tJet > 0) g.tJet = 0f;

            // ---- pose
            float sf = running ? Mathf.Clamp01(EffSpeed / 20f) : 0.4f;
            string mode;
            if (dying) mode = "dead";
            else if (stumbleAnimT > 0f) { stumbleAnimT -= dt; mode = "stumble"; }
            else if (g.tJet > 0) mode = "jet";
            else if (rollT > 0f) mode = "roll";
            else if (!grounded) mode = "jump";
            else if (g.tBoard > 0) mode = "board";
            else mode = "run";

            if (mode == "run" || mode == "roll" || mode == "board") rig.phase += dt * (mode == "roll" ? 1f : EffSpeed * 1.5f);
            if (mode == "jet") rig.phase += dt * 4f;
            rig.Pose(mode, Time.time, sf);

            transform.position = new Vector3(x, y, z);
        }

        void CheckObstacles(GameManager g)
        {
            int n = Physics.OverlapBoxNonAlloc(
                new Vector3(x, y + h * 0.5f, z + 0.30f),
                new Vector3(0.30f, h * 0.5f, 0.45f),
                buf, Quaternion.identity, Fx.MaskBlock, QueryTriggerInteraction.Collide);

            for (int i = 0; i < n; i++)
            {
                var oc = buf[i].GetComponentInParent<ObstacleComp>();
                if (oc == null) continue;

                bool changingLane = laneT < 0.92f && laneFrom != laneTo;
                bool sideHit = Mathf.Abs(x - oc.center.x) > oc.halfW + 0.05f;

                if (oc.type == ObstacleComp.T.Train)
                {
                    if (y >= oc.topY - 0.35f) continue;              // running on the roof
                    if (sideHit && changingLane) { Bounce(g); return; }
                    FrontHit(g);
                    return;
                }
                if (oc.type == ObstacleComp.T.Low)
                {
                    if (y > oc.topY - 0.14f) continue;               // jumped over
                    if (sideHit && changingLane) { Bounce(g); return; }
                    FrontHit(g);
                    return;
                }
                if (oc.type == ObstacleComp.T.High)
                {
                    if (h <= 1.0f) continue;                          // rolled under
                    if (y > 3.2f) continue;
                    FrontHit(g);
                    return;
                }
            }
        }

        void Bounce(GameManager g)
        {
            int tmp = laneFrom; laneFrom = laneTo; laneTo = tmp;
            laneT = Mathf.Clamp01(1f - laneT);
            OnStumble(g);
        }

        void OnStumble(GameManager g)
        {
            if (g.tStumble > 0f) { Die(g); return; }
            g.tStumble = 3.6f;
            stumbleAnimT = 0.55f;
            Fx.Play("stumble", 0.9f);
            if (CameraRig.I != null) CameraRig.I.Shake(0.35f);
        }

        void FrontHit(GameManager g)
        {
            if (g.tInv > 0f) return;
            if (g.tBoard > 0f)
            {
                g.tBoard = 0f;
                rig.SetBoard(false);
                g.tInv = 1.8f;
                Fx.Play("crash", 0.5f);
                if (CameraRig.I != null) CameraRig.I.Shake(0.5f);
                return;
            }
            Die(g);
        }

        void Die(GameManager g)
        {
            Fx.Play("crash");
            if (CameraRig.I != null) { CameraRig.I.Shake(0.9f); CameraRig.I.mode = CameraRig.CamMode.Death; }
            g.Die();
        }

        void CheckPickups(GameManager g)
        {
            Vector3 c = new Vector3(x, y + 0.9f, z);
            if (g.tMagnet > 0f) c.y += 1.5f;
            float rad = g.tMagnet > 0f ? 1.7f : 1.25f;
            int n = Physics.OverlapSphereNonAlloc(c, rad, pbuf, Fx.MaskPickup, QueryTriggerInteraction.Collide);
            for (int i = 0; i < n; i++)
            {
                var coin = pbuf[i].GetComponent<CoinComp>();
                if (coin != null && coin.active) { coin.Collect(); continue; }
                var pw = pbuf[i].GetComponent<PowerComp>();
                if (pw != null && pw.active) ApplyPower(g, pw);
            }
        }

        void ApplyPower(GameManager g, PowerComp pw)
        {
            pw.Consume();
            Fx.Play("power");
            switch (pw.kind)
            {
                case PowerComp.K.Magnet: g.tMagnet = 12f; UiScreens.I.Toast("MAGNET!", GameManager.C.red); break;
                case PowerComp.K.Jet: g.tJet = 6f; rig.SetJet(true); vy = 0f; UiScreens.I.Toast("JETPACK!", GameManager.C.orange); break;
                case PowerComp.K.X2: g.tX2 = 15f; UiScreens.I.Toast("SCORE x2", GameManager.C.gold); break;
                case PowerComp.K.Board: g.boardCount++; g.Save(); UiScreens.I.Toast("+1 HOVERBOARD", GameManager.C.blueBright); break;
            }
        }

        // ============================================== RESET
        public void ResetRun()
        {
            x = 0f; y = 0f; z = 0f;
            laneTo = 0; laneFrom = 0; laneT = 1f;
            vy = 0f; grounded = true; h = 1.75f; rollT = 0f; coyote = 0f;
            jetWasOn = false;
            rig.SetBoard(false); rig.SetJet(false);
            if (CameraRig.I != null) CameraRig.I.mode = CameraRig.CamMode.Follow;
            transform.position = Vector3.zero;
            rig.Pose("run", 0f, 1f);
        }
    }
}
