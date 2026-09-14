using System.Collections;
using UnityEngine;
using UnityEngine.UI;

namespace DummySurfer
{
    /// <summary>All UI screens, modeled 1:1 on the 10 reference screenshots:
    /// graffiti logo splash, illustrated loading w/ progress, Tap-to-Play menu,
    /// premium HUD, mission/pause cards + word hunt, orange high-score burst, results cards.</summary>
    public class UiScreens : MonoBehaviour
    {
        public static UiScreens I;

        // canvases
        Canvas cSplash, cLoad, cMenu, cHud, cPause, cHigh, cResults, cToast, cGear;

        // logo
        RectTransform logoBig, logoSmall;
        // splash / loading
        Image loadFill; Text loadPct, tipTxt; RectTransform stickerLoad;
        // menu
        Text tapPlay; RectTransform gearBtnMenu;
        // hud
        Text scoreT, coinT, multT, boardN, boostN, readyT;
        RectTransform hudRoot, readyGo;
        Image magFill, jetFill, x2Fill, brdFill;
        RectTransform magRow, jetRow, x2Row, brdRow;
        Button boardBtn, boostBtn;
        // pause
        Text pBank, pMult, pSet; RectTransform[] mCards = new RectTransform[3];
        Text[] mLabels = new Text[3]; Image[] mFills = new Image[3]; Text[] mCounts = new Text[3];
        Button[] mNow = new Button[3];
        RectTransform wordRow; Text[] wordBoxes = new Text[8];
        Button quitBtn, resumeBtn, closeMissionsBtn;
        // high
        Text hiScore; RectTransform raysImg, hiSticker;
        // results
        Text rBank, rScore, rCoins, rMult; Image adBtnImg; Button adBtn; RectTransform rewardRow; Button watchBtn; Text watchLabel;
        RectTransform stickerRes;
        // toast
        Text toastT; RectTransform toastBg; Coroutine toastCo;
        // gear
        Text soundT; public bool modalOpen;

        public Sprite Sticker { get; private set; }
        float t0;

        // ================================================= BUILD
        void Awake()
        {
            I = this;
            t0 = 0f;
            BuildSplash(); BuildLoading(); BuildHud(); BuildMenu(); BuildPause(); BuildHigh(); BuildResults(); BuildToast(); BuildGear();
            var g = GameManager.I;
            g.OnState += Route;
        }

        Canvas NewCanvas(string n, int order)
        {
            var go = new GameObject(n, typeof(Canvas), typeof(CanvasScaler), typeof(GraphicRaycaster));
            go.transform.SetParent(transform, false);
            var c = go.GetComponent<Canvas>();
            c.renderMode = RenderMode.ScreenSpaceOverlay;
            c.sortingOrder = order;
            var cs = go.GetComponent<CanvasScaler>();
            cs.uiScaleMode = CanvasScaler.ScaleMode.ScaleWithScreenSize;
            cs.referenceResolution = new Vector2(1080, 1920);
            cs.matchWidthOrHeight = 1f;
            return c;
        }

        Image FullBg(Transform parent, Color c, Sprite sp = null, Image.Type ty = Image.Type.Simple)
        {
            var rt = UiKit.Stretch(parent, "bg", Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);
            var img = UiKit.Img(rt, sp != null ? sp : Fx.SprPanel(), c, sp != null ? ty : Image.Type.Sliced);
            return img;
        }

        // ---- graffiti-style layered logo (yellow fill / orange rim / white ring / navy shadow + drips)
        RectTransform BuildLogo(Transform parent, float scale)
        {
            var root = UiKit.Node(parent, "logo", new Vector2(0.5f, 0.5f), new Vector2(940, 420), Vector2.zero);
            var l1 = UiKit.Node(root, "l1", new Vector2(0.5f, 0.5f), new Vector2(940, 200), new Vector2(0, 78));
            var l2 = UiKit.Node(root, "l2", new Vector2(0.5f, 0.5f), new Vector2(940, 230), new Vector2(0, -105));

            LayerText(l1, "DUMMY", 168);
            LayerText(l2, "SURFERS", 150);

            // paint drips under line 2
            float[] dx = { -330, -160, 30, 250, 360 };
            float[] dl = { 46, 78, 34, 92, 52 };
            for (int i = 0; i < dx.Length; i++)
            {
                var d = UiKit.Node(l2, "drip" + i, new Vector2(0.5f, 0.5f), new Vector2(16, dl[i]), new Vector2(dx[i], -108 - dl[i] * 0.4f));
                UiKit.Img(d, Fx.SprPanel(), GameManager.C.goldDeep, Image.Type.Sliced);
            }
            // sparkles
            var s1 = UiKit.Node(root, "sp1", new Vector2(0.5f, 0.5f), new Vector2(64, 64), new Vector2(370, 160));
            UiKit.Img(s1, Fx.SprStar(), Color.white);
            var s2 = UiKit.Node(root, "sp2", new Vector2(0.5f, 0.5f), new Vector2(40, 40), new Vector2(-400, -40));
            UiKit.Img(s2, Fx.SprStar(), Color.white);

            root.localScale = Vector3.one * scale;
            root.localRotation = Quaternion.Euler(0, 0, -2.5f);
            return root;
        }

        void LayerText(RectTransform line, string s, int size)
        {
            // navy drop
            var t3 = UiKit.Txt(line, s, size, GameManager.C.navy2, TextAnchor.MiddleCenter, 0, null, FontStyle.Bold, new Vector2(8, -10));
            // orange with white ring
            var t2 = UiKit.Txt(line, s, size, GameManager.C.orangeDeep, TextAnchor.MiddleCenter, 0, null, FontStyle.Bold, new Vector2(0, -4));
            var o1 = t2.gameObject.AddComponent<Outline>(); o1.effectColor = Color.white; o1.effectDistance = new Vector2(4, 4);
            var o2 = t2.gameObject.AddComponent<Outline>(); o2.effectColor = Color.white; o2.effectDistance = new Vector2(-4, -4);
            var o3 = t2.gameObject.AddComponent<Outline>(); o3.effectColor = Color.white; o3.effectDistance = new Vector2(4, -4);
            var o4 = t2.gameObject.AddComponent<Outline>(); o4.effectColor = Color.white; o4.effectDistance = new Vector2(-4, 4);
            // yellow with navy stroke
            var t1 = UiKit.Txt(line, s, size, GameManager.C.gold, TextAnchor.MiddleCenter, 10, GameManager.C.navy);
        }

        // ================================================= SPLASH (Ref 01)
        void BuildSplash()
        {
            cSplash = NewCanvas("Splash", 90);
            FullBg(cSplash.transform, GameManager.C.Hex(0x1EA7E2));
            logoBig = BuildLogo(cSplash.transform, 1.12f);
            var ver = UiKit.Txt(cSplash.transform, "DUMMY SURFERS", 30, new Color(1, 1, 1, 0.85f), TextAnchor.MiddleCenter, 0, null, FontStyle.Bold, new Vector2(0, -880));
            cSplash.gameObject.SetActive(true);
        }

        // ================================================= LOADING (Ref 04)
        void BuildLoading()
        {
            cLoad = NewCanvas("Loading", 85);
            FullBg(cLoad.transform, Color.white, Fx.SprGrad(GameManager.C.Hex(0x35B9F1), GameManager.C.Hex(0x7A5FD0)));
            var rays = UiKit.Node(cLoad.transform, "rays", new Vector2(0.5f, 0.5f), new Vector2(1700, 1700), Vector2.zero);
            UiKit.Img(rays, Fx.SprRays(), new Color(1f, 1f, 1f, 0.16f));

            // drifting clouds
            for (int i = 0; i < 3; i++)
            {
                var c = UiKit.Node(cLoad.transform, "cl" + i, new Vector2(0.5f * (0.2f + 0.3f * i), 0.78f - 0.06f * i), new Vector2(320 - i * 60, 160 - i * 30), Vector2.zero);
                UiKit.Img(c, Fx.SprCloud(), new Color(1, 1, 1, 0.9f));
            }
            // skyline strip
            var sky = UiKit.Node(cLoad.transform, "sky", new Vector2(0.5f, 0.5f), new Vector2(1400, 260), new Vector2(0, -700));
            var simg = UiKit.Img(sky, Sprite.Create(Fx.TexSkyline(), new Rect(0, 0, 512, 128), new Vector2(0.5f, 0f), 100f), GameManager.C.Hex(0x5F7FB8));
            simg.type = Image.Type.Tiled;

            logoSmall = BuildLogo(cLoad.transform, 0.72f);
            logoSmall.anchorMin = new Vector2(0.5f, 0.5f);
            logoSmall.anchorMax = new Vector2(0.5f, 0.5f);
            logoSmall.anchoredPosition = new Vector2(0, 640);

            stickerLoad = UiKit.Node(cLoad.transform, "stick", new Vector2(0.5f, 0.5f), new Vector2(640, 640), new Vector2(0, 60));
            UiKit.Img(stickerLoad, Fx.SprCircle(), new Color(1, 1, 1, 0.14f));
            var st = UiKit.Node(stickerLoad, "st", new Vector2(0.5f, 0.5f), new Vector2(600, 600), Vector2.zero);

            tipTxt = UiKit.Txt(cLoad.transform, "Tip: Swipe up to jump over trains!", 34, Color.white, TextAnchor.MiddleCenter, 6, GameManager.C.navy2, FontStyle.Bold, new Vector2(0, -430));

            Image pf;
            UiKit.ProgressBar(cLoad.transform, new Vector2(0.5f, 0.5f), new Vector2(0, -560), new Vector2(720, 70), GameManager.C.navy, GameManager.C.orange, out loadFill, out loadPct);

            UiKit.Txt(cLoad.transform, "1.0.0", 24, new Color(1, 1, 1, 0.8f), TextAnchor.MiddleLeft, 0, null, FontStyle.Normal, new Vector2(-470, -880), new Vector2(300, 40));
            UiKit.Txt(cLoad.transform, "DummySurfers", 24, new Color(1, 1, 1, 0.8f), TextAnchor.MiddleRight, 0, null, FontStyle.Normal, new Vector2(470, -880), new Vector2(300, 40));
            cLoad.gameObject.SetActive(false);
        }

        public void SetSticker(Sprite s)
        {
            Sticker = s;
            if (stickerLoad != null)
                UiKit.Img((RectTransform)stickerLoad.GetChild(1), s, Color.white);
        }

        public void SetProgress(float f)
        {
            if (loadFill != null) loadFill.fillAmount = Mathf.Clamp01(f);
            if (loadPct != null) loadPct.text = Mathf.RoundToInt(f * 100f) + "%";
        }

        // ================================================= HUD (Ref 03/07/08)
        void BuildHud()
        {
            cHud = NewCanvas("HUD", 60);
            var safe = new GameObject("safe", typeof(RectTransform));
            safe.transform.SetParent(cHud.transform, false);
            SafeArea.Apply(safe, Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);
            hudRoot = (RectTransform)safe.transform;

            // pause button (blue rounded + white bars)
            UiKit.IconButton(hudRoot, "pause", GameManager.C.blueBright, new Vector2(0.045f, 0.955f), new Vector2(112, 112), Vector2.zero,
                () => { Fx.Play("click"); PauseGame(); }, 0.52f);

            // score panel (navy + star + x + score)
            var sp = UiKit.Node(hudRoot, "scoreP", new Vector2(0.955f, 0.955f), new Vector2(360, 96), Vector2.zero);
            UiKit.Img(sp, Fx.SprPanel(), GameManager.C.navy, Image.Type.Sliced);
            var stIc = UiKit.Node(sp, "star", new Vector2(0.5f, 0.5f), new Vector2(56, 56), new Vector2(-138, 0));
            UiKit.Img(stIc, Fx.SprStar(), GameManager.C.gold);
            multT = UiKit.Txt(sp, "x1", 42, Color.white, TextAnchor.MiddleCenter, 4, GameManager.C.navy2, FontStyle.Bold, new Vector2(-80, 0));
            scoreT = UiKit.Txt(sp, "00000", 56, Color.white, TextAnchor.MiddleRight, 4, GameManager.C.navy2, FontStyle.Bold, new Vector2(36, 2));
            var srt = (RectTransform)scoreT.transform;
            srt.sizeDelta = new Vector2(210, 60);

            // coin pill under score
            Text cT;
            UiKit.CoinPill(hudRoot, out cT, new Vector2(0.955f, 0.885f), Vector2.zero, new Vector2(250, 82));
            coinT = cT;

            // GET READY
            readyGo = UiKit.Node(hudRoot, "ready", new Vector2(0.5f, 0.5f), new Vector2(900, 140), new Vector2(0, 260));
            readyT = UiKit.Txt(readyGo, "GO!", 96, GameManager.C.gold, TextAnchor.MiddleCenter, 10, GameManager.C.navy);
            readyGo.gameObject.SetActive(false);

            // powerup stock buttons (bottom-left)
            boardBtn = UiKit.IconButton(hudRoot, "board", new Color(0.10f, 0.16f, 0.32f, 0.85f), new Vector2(0.10f, 0.145f), new Vector2(120, 120), Vector2.zero,
                () => { PlayerController.I.ActivateBoard(); Fx.Play("click"); }, 0.58f);
            var bb = UiKit.Node(boardBtn.transform as RectTransform, "n", new Vector2(1f, 1f), new Vector2(48, 48), new Vector2(-6, -6));
            UiKit.Img(bb, Fx.SprCircle(), GameManager.C.gold);
            boardN = UiKit.Txt(bb, "2", 30, GameManager.C.navy, TextAnchor.MiddleCenter, 0, null, FontStyle.Bold);

            boostBtn = UiKit.IconButton(hudRoot, "rocket", new Color(0.10f, 0.16f, 0.32f, 0.85f), new Vector2(0.10f, 0.245f), new Vector2(120, 120), Vector2.zero,
                () => { PlayerController.I.HeadStart(); Fx.Play("click"); }, 0.58f);
            var gb = UiKit.Node(boostBtn.transform as RectTransform, "n", new Vector2(1f, 1f), new Vector2(48, 48), new Vector2(-6, -6));
            UiKit.Img(gb, Fx.SprCircle(), GameManager.C.gold);
            boostN = UiKit.Txt(gb, "1", 30, GameManager.C.navy, TextAnchor.MiddleCenter, 0, null, FontStyle.Bold);

            // active effect timers (above buttons)
            magRow = TimerRow(new Vector2(0.10f, 0.335f), "magnet", GameManager.C.red, out magFill);
            jetRow = TimerRow(new Vector2(0.10f, 0.395f), "rocket", GameManager.C.orange, out jetFill);
            x2Row = TimerRow(new Vector2(0.10f, 0.455f), "x2", GameManager.C.gold, out x2Fill);
            brdRow = TimerRow(new Vector2(0.10f, 0.515f), "board", GameManager.C.blueBright, out brdFill);

            cHud.gameObject.SetActive(false);
        }

        RectTransform TimerRow(Vector2 anchor, string icon, Color col, out Image fill)
        {
            var row = UiKit.Node(hudRoot, "timer_" + icon, anchor, new Vector2(240, 56), Vector2.zero);
            UiKit.Img(row, Fx.SprPanel(), new Color(0.10f, 0.16f, 0.32f, 0.85f), Image.Type.Sliced);
            var ic = UiKit.Node(row, "ic", new Vector2(0.5f, 0.5f), new Vector2(42, 42), new Vector2(-88, 0));
            UiKit.Img(ic, Fx.SprIcon(icon), Color.white);
            var tr = UiKit.Node(row, "tr", new Vector2(0.5f, 0.5f), new Vector2(120, 16), new Vector2(18, 0));
            UiKit.Img(tr, Fx.SprPanel(), GameManager.C.navy2, Image.Type.Sliced);
            var fr = UiKit.Node(tr, "f", new Vector2(0f, 0.5f), new Vector2(112, 12), Vector2.zero);
            fr.pivot = new Vector2(0, 0.5f);
            fr.anchorMin = new Vector2(0, 0.5f); fr.anchorMax = new Vector2(0, 0.5f);
            fr.anchoredPosition = new Vector2(4, 0);
            fill = UiKit.FillImg(fr, Fx.SprPanel(), col);
            row.gameObject.SetActive(false);
            return row;
        }

        void PauseGame()
        {
            var g = GameManager.I;
            if (g.st != GameManager.St.Run) return;
            RefreshPause();
            g.SetState(GameManager.St.Pause);
        }

        // ================================================= MENU (Ref 05)
        void BuildMenu()
        {
            cMenu = NewCanvas("Menu", 55);
            UiKit.TapCatcher(cMenu.transform, () => { GameRoot.I.FromMenuTap(); });

            var best = UiKit.Node(cMenu.transform, "best", new Vector2(0.5f, 0.955f), new Vector2(340, 72), Vector2.zero);
            UiKit.Img(best, Fx.SprPanel(), new Color(0.10f, 0.16f, 0.32f, 0.8f), Image.Type.Sliced);
            UiKit.Txt(best, "BEST", 30, GameManager.C.gold, TextAnchor.MiddleLeft, 0, null, FontStyle.Bold, new Vector2(-100, 0));
            var bestT = UiKit.Txt(best, "0", 40, Color.white, TextAnchor.MiddleRight, 4, GameManager.C.navy2, FontStyle.Bold, new Vector2(56, 0));
            bestT.name = "bestT";

            gearBtnMenu = UiKit.Node(cMenu.transform, "gear", new Vector2(0.925f, 0.945f), new Vector2(92, 92), Vector2.zero);
            var gi = UiKit.Img(gearBtnMenu, Fx.SprCircle(), Color.white);
            gi.raycastTarget = true;
            var gg = UiKit.Node(gearBtnMenu, "g", new Vector2(0.5f, 0.5f), new Vector2(56, 56), Vector2.zero);
            UiKit.Img(gg, Fx.SprIcon("gear"), GameManager.C.navy);
            var gb = gearBtnMenu.gameObject.AddComponent<Button>();
            gb.targetGraphic = gi;
            gb.onClick.AddListener(() => { Fx.Play("click"); OpenGear(); });

            tapPlay = UiKit.Txt(cMenu.transform, "Tap to Play", 92, Color.white, TextAnchor.MiddleCenter, 12, GameManager.C.navy, FontStyle.Bold, new Vector2(0, -760));
            UiKit.Txt(cMenu.transform, "swipe to steer  ·  up = jump  ·  down = roll", 30, new Color(1, 1, 1, 0.92f), TextAnchor.MiddleCenter, 4, GameManager.C.navy2, FontStyle.Normal, new Vector2(0, -852));

            // version tag (bottom-right, matches GitHub release: unity-v1.0.<n> → 1.0.<n>)
            var verM = UiKit.Node(cMenu.transform, "verTag", new Vector2(1f, 0f), new Vector2(300, 40), new Vector2(-190, 60));
            UiKit.Txt(verM, "v" + Application.version, 26, new Color(1f, 1f, 1f, 0.75f), TextAnchor.MiddleRight, 0, null, FontStyle.Bold);

            cMenu.gameObject.SetActive(false);
        }

        // ================================================= PAUSE / MISSIONS (Ref 06)
        void BuildPause()
        {
            cPause = NewCanvas("Pause", 70);
            FullBg(cPause.transform, new Color(GameManager.C.navy.r, GameManager.C.navy.g, GameManager.C.navy.b, 0.88f));

            // top navy band
            var band = new GameObject("band", typeof(RectTransform));
            band.transform.SetParent(cPause.transform, false);
            SafeArea.Apply(band, new Vector2(0, 1), new Vector2(1, 1), new Vector2(0, -150), Vector2.zero);
            UiKit.Img((RectTransform)band.transform, Fx.SprPanel(), GameManager.C.navy, Image.Type.Sliced);
            Text bT;
            UiKit.CoinPill(band.transform, out bT, new Vector2(0.5f, 0.5f), new Vector2(-290, 0), new Vector2(300, 84));
            pBank = bT;
            var plus = UiKit.IconButton(band.transform, "plus", GameManager.C.green, new Vector2(0.5f, 0.5f), new Vector2(64, 64), new Vector2(-110, 0),
                () => { GameManager.I.BankAdd(100); Fx.Play("reward", 0.8f); }, 0.6f);
            var stIc = UiKit.Node(band.transform, "star", new Vector2(0.5f, 0.5f), new Vector2(240, 64), new Vector2(180, 0));
            UiKit.Img(stIc, Fx.SprPanel(), GameManager.C.navy2, Image.Type.Sliced);
            var stG = UiKit.Node(stIc, "g", new Vector2(0.5f, 0.5f), new Vector2(44, 44), new Vector2(-66, 0));
            UiKit.Img(stG, Fx.SprStar(), GameManager.C.gold);
            pMult = UiKit.Txt(stIc, "x1", 38, Color.white, TextAnchor.MiddleLeft, 3, GameManager.C.navy2, FontStyle.Bold, new Vector2(24, 0));

            // mission set banner
            var banner = UiKit.Node(cPause.transform, "banner", new Vector2(0.5f, 0.5f), new Vector2(940, 130), new Vector2(0, 560));
            UiKit.Img(banner, Fx.SprPanel(), GameManager.C.blueBanner, Image.Type.Sliced);
            var av = UiKit.Node(banner, "av", new Vector2(0.5f, 0.5f), new Vector2(104, 104), new Vector2(-390, 0));
            UiKit.Img(av, Fx.SprCircle(), Color.white);
            var avi = UiKit.Node(av, "i", new Vector2(0.5f, 0.5f), new Vector2(94, 94), Vector2.zero);
            UiKit.Img(avi, Fx.SprCircle(), GameManager.C.sky);
            pSet = UiKit.Txt(banner, "MISSION SET 1", 52, Color.white, TextAnchor.MiddleCenter, 6, GameManager.C.navy2, FontStyle.Bold, new Vector2(30, 0));
            var plus1 = UiKit.Node(banner, "p1", new Vector2(0.5f, 0.5f), new Vector2(110, 64), new Vector2(390, 0));
            UiKit.Img(plus1, Fx.SprPanel(), GameManager.C.gold, Image.Type.Sliced);
            UiKit.Txt(plus1, "+1", 38, GameManager.C.navy, TextAnchor.MiddleCenter, 0, null, FontStyle.Bold);

            UiKit.Txt(cPause.transform, "Score Multiplier: 1/5", 42, GameManager.C.Hex(0x6C7BF0), TextAnchor.MiddleCenter, 0, null, FontStyle.Bold, new Vector2(0, 470));
            UiKit.Txt(cPause.transform, "Complete the missions below to increase your score multiplier!", 27, GameManager.C.Hex(0xC9D6F5), TextAnchor.MiddleCenter, 0, null, FontStyle.Normal, new Vector2(0, 424));

            // mission cards
            string[] icons = { "x2", "star", "board" };
            for (int i = 0; i < 3; i++)
            {
                int idx = i;
                var card = UiKit.Node(cPause.transform, "card" + i, new Vector2(0.5f, 0.5f), new Vector2(940, 180), new Vector2(0, 296 - i * 208));
                UiKit.Img(card, Fx.SprPanel(), GameManager.C.cardBlue, Image.Type.Sliced);
                mLabels[i] = UiKit.Txt(card, "Pick up 20 Coins", 38, Color.white, TextAnchor.UpperLeft, 5, GameManager.C.navy2, FontStyle.Bold, new Vector2(-260, 44));
                Image f; Text c2;
                UiKit.ProgressBar(card, new Vector2(0.5f, 0.5f), new Vector2(-160, -38), new Vector2(560, 54), GameManager.C.navy, GameManager.C.Hex(0x5A8DEE), out f, out c2);
                mFills[i] = f; mCounts[i] = c2;
                var rew = UiKit.Node(card, "rew", new Vector2(0.5f, 0.5f), new Vector2(220, 66), new Vector2(320, 30));
                UiKit.Img(rew, Fx.SprPanel(), GameManager.C.green, Image.Type.Sliced);
                var ric = UiKit.Node(rew, "c", new Vector2(0.5f, 0.5f), new Vector2(44, 44), new Vector2(-72, 0));
                UiKit.Img(ric, Fx.SprCoin(), Color.white);
                UiKit.Txt(rew, "1500", 34, Color.white, TextAnchor.MiddleCenter, 0, null, FontStyle.Bold, new Vector2(16, 0));
                mNow[i] = UiKit.Btn(UiKit.Node(card, "now", new Vector2(0.5f, 0.5f), new Vector2(220, 58), new Vector2(320, -42)),
                    GameManager.C.greenDark, "Complete now", 25, () => { CompleteNow(idx); }, GameManager.C.white, 3);
                mCards[i] = card;
            }

            // word hunt
            var wh = UiKit.Node(cPause.transform, "wh", new Vector2(0.5f, 0.5f), new Vector2(940, 210), new Vector2(0, -390));
            UiKit.Img(wh, Fx.SprPanel(), GameManager.C.Hex(0x2E8B3E), Image.Type.Sliced);
            UiKit.Txt(wh, "WORD HUNT", 44, GameManager.C.gold, TextAnchor.UpperLeft, 0, null, FontStyle.Bold, new Vector2(-330, 62));
            UiKit.Txt(wh, "COLLECT LETTERS", 24, Color.white, TextAnchor.UpperLeft, 0, null, FontStyle.Normal, new Vector2(-318, 22));
            wordRow = UiKit.Node(wh, "wr", new Vector2(0.5f, 0.5f), new Vector2(700, 84), new Vector2(-60, -46));
            for (int i = 0; i < 8; i++)
            {
                var bx = UiKit.Node(wordRow, "w" + i, new Vector2(0.5f, 0.5f), new Vector2(74, 74), new Vector2(-315 + i * 90, 0));
                UiKit.Img(bx, Fx.SprPanel(), GameManager.C.navy, Image.Type.Sliced);
                wordBoxes[i] = UiKit.Txt(bx, "?", 40, new Color(1, 1, 1, 0.35f), TextAnchor.MiddleCenter, 0, null, FontStyle.Bold);
            }
            var mb = UiKit.Node(wh, "mb", new Vector2(0.5f, 0.5f), new Vector2(190, 110), new Vector2(340, -20));
            UiKit.Img(mb, Fx.SprPanel(), GameManager.C.Hex(0x24702F), Image.Type.Sliced);
            UiKit.Txt(mb, "Mini Box", 26, Color.white, TextAnchor.MiddleCenter, 0, null, FontStyle.Bold);
            UiKit.Txt(mb, "+500", 30, GameManager.C.gold, TextAnchor.MiddleCenter, 0, null, FontStyle.Bold, new Vector2(0, -26));

            // bottom band: quit/resume
            var bb2 = new GameObject("bband", typeof(RectTransform));
            bb2.transform.SetParent(cPause.transform, false);
            SafeArea.Apply(bb2, new Vector2(0, 0), new Vector2(1, 0), Vector2.zero, new Vector2(0, 230));
            UiKit.Img((RectTransform)bb2.transform, Fx.SprPanel(), GameManager.C.navy, Image.Type.Sliced);
            quitBtn = UiKit.Btn(UiKit.Node(bb2.transform, "quit", new Vector2(0.5f, 0.5f), new Vector2(360, 112), new Vector2(-260, 0)),
                GameManager.C.red, "QUIT", 46, () => { Fx.Play("click"); GameRoot.I.GoHome(); });
            resumeBtn = UiKit.Btn(UiKit.Node(bb2.transform, "resume", new Vector2(0.5f, 0.5f), new Vector2(460, 112), new Vector2(230, 0)),
                GameManager.C.green, "RESUME", 46, () => { Fx.Play("click"); GameManager.I.SetState(GameManager.St.Run); });
            closeMissionsBtn = UiKit.Btn(UiKit.Node(bb2.transform, "close", new Vector2(0.5f, 0.5f), new Vector2(460, 112), new Vector2(0, 0)),
                GameManager.C.blue, "CLOSE", 46, () => { Fx.Play("click"); GameManager.I.SetState(GameManager.I.stBeforeMissions); });
            closeMissionsBtn.gameObject.SetActive(false);

            cPause.gameObject.SetActive(false);
        }

        void CompleteNow(int i)
        {
            Fx.Play("click");
            if (GameManager.I.CompleteNow(i)) RefreshPause();
            else UiScreens.I.Toast("NOT ENOUGH COINS", GameManager.C.red);
        }

        public void RefreshPause()
        {
            var g = GameManager.I;
            pBank.text = g.bank.ToString("N0");
            pMult.text = "x" + g.multBase;
            pSet.text = "MISSION SET " + (g.setIdx + 1);
            string[] labels =
            {
                "Pick up  <color=#3EAE3E>" + g.CoinsTarget + "</color>  Coins",
                "Score <color=#3EAE3E>" + g.ScoreTarget + "</color> points in\none run",
                "Jump  <color=#3EAE3E>" + g.JumpsTarget + "</color>  times"
            };
            int[] targets = { g.CoinsTarget, g.ScoreTarget, g.JumpsTarget };
            int[] prog = { g.MissionProgress(0), g.MissionProgress(1), g.MissionProgress(2) };
            for (int i = 0; i < 3; i++)
            {
                mLabels[i].text = labels[i];
                float f = Mathf.Clamp01((float)prog[i] / targets[i]);
                mFills[i].fillAmount = f;
                mCounts[i].text = Mathf.Min(prog[i], targets[i]) + "/" + targets[i];
                bool done = g.MissionClaimed(i);
                mNow[i].interactable = !done && g.bank >= g.CompleteNowCost;
                mNow[i].gameObject.GetComponent<Image>().color = done ? GameManager.C.grey : (mNow[i].interactable ? GameManager.C.greenDark : new Color(0.35f, 0.4f, 0.45f));
                mNow[i].GetComponentInChildren<Text>().text = done ? "DONE" : "Complete now";
            }
            string word = GameManager.Words[g.wordIdx % GameManager.Words.Length];
            for (int i = 0; i < 8; i++)
            {
                if (i >= word.Length) { wordBoxes[i].text = ""; continue; }
                bool on = i < g.lettersOn;
                wordBoxes[i].text = on ? word[i].ToString() : "?";
                wordBoxes[i].color = on ? Color.white : new Color(1, 1, 1, 0.35f);
            }
        }

        // ================================================= HIGH SCORE (Ref 09)
        void BuildHigh()
        {
            cHigh = NewCanvas("High", 80);
            FullBg(cHigh.transform, GameManager.C.orange);
            raysImg = UiKit.Node(cHigh.transform, "rays", new Vector2(0.5f, 0.5f), new Vector2(2200, 2200), Vector2.zero);
            UiKit.Img(raysImg, Fx.SprRays(), new Color(1f, 0.92f, 0.6f, 0.55f));
            UiKit.TapCatcher(cHigh.transform, () => { Fx.Play("click"); GameManager.I.SetState(GameManager.St.Results); });
            UiKit.Txt(cHigh.transform, "New High Score!", 64, GameManager.C.Hex(0x1B2A6B), TextAnchor.MiddleCenter, 0, null, FontStyle.Bold, new Vector2(0, 640));
            hiScore = UiKit.Txt(cHigh.transform, "0", 190, GameManager.C.Hex(0x1B2A6B), TextAnchor.MiddleCenter, 0, null, FontStyle.Bold, new Vector2(0, 330));
            var ho = hiScore.gameObject.AddComponent<Outline>(); ho.effectColor = Color.white; ho.effectDistance = new Vector2(5, -5);
            hiSticker = UiKit.Node(cHigh.transform, "stick", new Vector2(0.5f, 0.5f), new Vector2(680, 680), new Vector2(0, -160));
            UiKit.Txt(cHigh.transform, "Tap to continue", 48, Color.white, TextAnchor.MiddleCenter, 8, GameManager.C.Hex(0xB35C00), FontStyle.Bold, new Vector2(0, -800));
            cHigh.gameObject.SetActive(false);
        }

        // ================================================= RESULTS (Ref 10)
        void BuildResults()
        {
            cResults = NewCanvas("Results", 75);
            FullBg(cResults.transform, GameManager.C.Hex(0xEEF2F8));

            // top navy band
            var band = new GameObject("band", typeof(RectTransform));
            band.transform.SetParent(cResults.transform, false);
            SafeArea.Apply(band, new Vector2(0, 1), new Vector2(1, 1), new Vector2(0, -140), Vector2.zero);
            UiKit.Img((RectTransform)band.transform, Fx.SprPanel(), GameManager.C.navy, Image.Type.Sliced);
            Text bT;
            UiKit.CoinPill(band.transform, out bT, new Vector2(0.5f, 0.5f), new Vector2(-250, 0), new Vector2(300, 84));
            rBank = bT;
            UiKit.IconButton(band.transform, "plus", GameManager.C.green, new Vector2(0.5f, 0.5f), new Vector2(64, 64), new Vector2(-70, 0),
                () => { GameManager.I.BankAdd(100); Fx.Play("reward", 0.8f); }, 0.6f);
            var stIc = UiKit.Node(band.transform, "star", new Vector2(0.5f, 0.5f), new Vector2(220, 64), new Vector2(170, 0));
            UiKit.Img(stIc, Fx.SprPanel(), GameManager.C.navy2, Image.Type.Sliced);
            var stG = UiKit.Node(stIc, "g", new Vector2(0.5f, 0.5f), new Vector2(44, 44), new Vector2(-66, 0));
            UiKit.Img(stG, Fx.SprStar(), GameManager.C.gold);
            rMult = UiKit.Txt(stIc, "x1", 38, Color.white, TextAnchor.MiddleLeft, 3, GameManager.C.navy2, FontStyle.Bold, new Vector2(24, 0));
            var gear = UiKit.IconButton(band.transform, "gear", GameManager.C.navy2, new Vector2(0.5f, 0.5f), new Vector2(64, 64), new Vector2(380, 0),
                () => { Fx.Play("click"); OpenGear(); }, 0.6f);

            // character sticker
            stickerRes = UiKit.Node(cResults.transform, "stick", new Vector2(0.27f, 0.52f), new Vector2(700, 700), Vector2.zero);

            // score card
            var sc = UiKit.Node(cResults.transform, "scoreCard", new Vector2(0.71f, 0.66f), new Vector2(460, 240), Vector2.zero);
            var head = UiKit.Node(sc, "h", new Vector2(0.5f, 1f), new Vector2(460, 84), new Vector2(0, -42));
            UiKit.Img(head, Fx.SprPanel(), GameManager.C.blueBanner, Image.Type.Sliced);
            UiKit.Txt(head, "Score", 42, Color.white, TextAnchor.MiddleCenter, 0, null, FontStyle.Bold);
            var body = UiKit.Node(sc, "b", new Vector2(0.5f, 0f), new Vector2(460, 140), new Vector2(0, 70));
            UiKit.Img(body, Fx.SprPanel(), Color.white, Image.Type.Sliced);
            var st2 = UiKit.Node(body, "s", new Vector2(0.5f, 0.5f), new Vector2(56, 56), new Vector2(-150, 0));
            UiKit.Img(st2, Fx.SprStar(), GameManager.C.gold);
            rScore = UiKit.Txt(body, "0", 72, GameManager.C.Hex(0x2B4FA0), TextAnchor.MiddleCenter, 0, null, FontStyle.Bold, new Vector2(30, 0));

            // coins card
            var cc = UiKit.Node(cResults.transform, "coinCard", new Vector2(0.71f, 0.50f), new Vector2(460, 210), Vector2.zero);
            var ch = UiKit.Node(cc, "h", new Vector2(0.5f, 1f), new Vector2(460, 76), new Vector2(0, -38));
            UiKit.Img(ch, Fx.SprPanel(), GameManager.C.blueBanner, Image.Type.Sliced);
            UiKit.Txt(ch, "Coins", 40, Color.white, TextAnchor.MiddleCenter, 0, null, FontStyle.Bold);
            var cb = UiKit.Node(cc, "b", new Vector2(0.5f, 0f), new Vector2(460, 120), new Vector2(0, 60));
            UiKit.Img(cb, Fx.SprPanel(), Color.white, Image.Type.Sliced);
            var ci2 = UiKit.Node(cb, "c", new Vector2(0.5f, 0.5f), new Vector2(56, 56), new Vector2(-110, 0));
            UiKit.Img(ci2, Fx.SprCoin(), Color.white);
            rCoins = UiKit.Txt(cb, "0", 62, GameManager.C.goldDeep, TextAnchor.MiddleCenter, 0, null, FontStyle.Bold, new Vector2(40, 0));

            // +500 ad row
            var ad = UiKit.Node(cResults.transform, "adRow", new Vector2(0.71f, 0.395f), new Vector2(460, 100), Vector2.zero);
            UiKit.Img(ad, Fx.SprPanel(), Color.white, Image.Type.Sliced);
            UiKit.Txt(ad, "+500", 44, GameManager.C.Hex(0x2B4FA0), TextAnchor.MiddleLeft, 0, null, FontStyle.Bold, new Vector2(-120, 0));
            var ac = UiKit.Node(ad, "c", new Vector2(0.5f, 0.5f), new Vector2(52, 52), new Vector2(-30, 0));
            UiKit.Img(ac, Fx.SprCoin(), Color.white);
            adBtnImg = null;
            adBtn = UiKit.IconButton(ad, "tv", GameManager.C.green, new Vector2(0.5f, 0.5f), new Vector2(88, 88), new Vector2(155, 0),
                () =>
                {
                    if (GameManager.I.adUsedScore) return;
                    GameManager.I.adUsedScore = true;
                    GameManager.I.BankAdd(500);
                    Fx.Play("reward");
                    Toast("REWARD  +500", GameManager.C.green);
                    adBtn.interactable = false;
                    adBtn.gameObject.GetComponent<Image>().color = GameManager.C.grey;
                }, 0.58f);

            // unlocks chip
            var ul = UiKit.Node(cResults.transform, "unlock", new Vector2(0.71f, 0.335f), new Vector2(460, 76), Vector2.zero);
            UiKit.Img(ul, Fx.SprPanel(), Color.white, Image.Type.Sliced);
            UiKit.Txt(ul, "Unlocks at:", 32, GameManager.C.Hex(0x2B4FA0), TextAnchor.MiddleLeft, 0, null, FontStyle.Bold, new Vector2(-110, 0));
            UiKit.Txt(ul, "x" + (Mathf.Min(GameManager.I.multBase + 1, 5)) + " ★", 34, GameManager.C.goldDeep, TextAnchor.MiddleRight, 0, null, FontStyle.Bold, new Vector2(120, 0));

            // get rewards banner
            rewardRow = UiKit.Node(cResults.transform, "reward", new Vector2(0.5f, 0.20f), new Vector2(980, 150), Vector2.zero);
            UiKit.Img(rewardRow, Fx.SprPanel(), GameManager.C.orange, Image.Type.Sliced);
            UiKit.Txt(rewardRow, "Get Rewards", 46, Color.white, TextAnchor.MiddleLeft, 0, null, FontStyle.Bold, new Vector2(-290, 14));
            UiKit.Txt(rewardRow, "Double your fun!", 24, new Color(1, 1, 1, 0.85f), TextAnchor.MiddleLeft, 0, null, FontStyle.Normal, new Vector2(-250, -34));
            watchLabel = null;
            watchBtn = UiKit.Btn(UiKit.Node(rewardRow, "wv", new Vector2(0.5f, 0.5f), new Vector2(300, 92), new Vector2(280, 0)),
                GameManager.C.green, "Watch Video", 32,
                () =>
                {
                    if (GameManager.I.rewardUsed) return;
                    GameManager.I.rewardUsed = true;
                    GameManager.I.BankAdd(200);
                    Fx.Play("reward");
                    Toast("REWARD  +200", GameManager.C.green);
                    watchBtn.interactable = false;
                    watchBtn.gameObject.GetComponent<Image>().color = GameManager.C.grey;
                }, GameManager.C.white, 3);
            var tvIc = UiKit.Node(watchBtn.transform as RectTransform, "tv", new Vector2(0.5f, 0.5f), new Vector2(40, 40), new Vector2(115, 26));
            UiKit.Img(tvIc, Fx.SprIcon("tv"), Color.white);

            // bottom nav
            var nav = new GameObject("nav", typeof(RectTransform));
            nav.transform.SetParent(cResults.transform, false);
            SafeArea.Apply(nav, new Vector2(0, 0), new Vector2(1, 0), Vector2.zero, new Vector2(0, 210));
            UiKit.Img((RectTransform)nav.transform, Fx.SprPanel(), GameManager.C.navy, Image.Type.Sliced);
            UiKit.IconButton(nav.transform, "home", GameManager.C.blueBright, new Vector2(0.5f, 0.5f), new Vector2(130, 130), new Vector2(-370, 0),
                () => { Fx.Play("click"); GameRoot.I.GoHome(); }, 0.56f);
            UiKit.IconButton(nav.transform, "list", GameManager.C.blueBright, new Vector2(0.5f, 0.5f), new Vector2(130, 130), new Vector2(-180, 0),
                () => { Fx.Play("click"); ShowMissions(); }, 0.56f);
            UiKit.Btn(UiKit.Node(nav.transform, "play", new Vector2(0.5f, 0.5f), new Vector2(440, 130), new Vector2(150, 0)),
                GameManager.C.green, "PLAY", 60, () => { Fx.Play("click"); GameRoot.I.FromResultsPlay(); });

            // version tag (bottom-left, matches GitHub release: unity-v1.0.<n> → 1.0.<n>)
            var ver = UiKit.Node(cResults.transform, "verTag", new Vector2(0f, 0f), new Vector2(300, 40), new Vector2(180, 262));
            UiKit.Txt(ver, "v" + Application.version, 26, GameManager.C.Hex(0x8A93A6), TextAnchor.MiddleLeft, 0, null, FontStyle.Bold);

            cResults.gameObject.SetActive(false);
        }

        public void ShowMissions()
        {
            var g = GameManager.I;
            g.stBeforeMissions = g.st;
            RefreshPause();
            quitBtn.gameObject.SetActive(false);
            resumeBtn.gameObject.SetActive(false);
            closeMissionsBtn.gameObject.SetActive(true);
            g.SetState(GameManager.St.Missions);
        }

        // ================================================= TOASTS
        void BuildToast()
        {
            cToast = NewCanvas("Toast", 95);
            toastBg = UiKit.Node(cToast.transform, "toast", new Vector2(0.5f, 1f), new Vector2(640, 100), new Vector2(0, -80));
            UiKit.Img(toastBg, Fx.SprPanel(), GameManager.C.green, Image.Type.Sliced);
            toastT = UiKit.Txt(toastBg, "", 44, Color.white, TextAnchor.MiddleCenter, 5, GameManager.C.navy2, FontStyle.Bold);
            toastBg.gameObject.SetActive(false);
        }

        public void Toast(string msg, Color col)
        {
            if (toastCo != null) StopCoroutine(toastCo);
            toastCo = StartCoroutine(ToastCo(msg, col));
        }

        IEnumerator ToastCo(string msg, Color col)
        {
            toastBg.gameObject.SetActive(true);
            toastBg.GetComponent<Image>().color = col;
            toastT.text = msg;
            float t = 0;
            while (t < 0.25f) { t += Time.unscaledDeltaTime; toastBg.anchoredPosition = new Vector2(0, Mathf.Lerp(-140, -120, t / 0.25f)); yield return null; }
            toastBg.anchoredPosition = new Vector2(0, -120);
            yield return new WaitForSecondsRealtime(1.5f);
            float f = 0;
            while (f < 0.3f) { f += Time.unscaledDeltaTime; toastBg.anchoredPosition = new Vector2(0, Mathf.Lerp(-120, -60, f / 0.3f)); yield return null; }
            toastBg.gameObject.SetActive(false);
        }

        // ================================================= GEAR
        void BuildGear()
        {
            cGear = NewCanvas("Gear", 96);
            FullBg(cGear.transform, new Color(0, 0, 0, 0.55f));
            UiKit.TapCatcher(cGear.transform, () => CloseGear());
            var card = UiKit.Node(cGear.transform, "card", new Vector2(0.5f, 0.5f), new Vector2(760, 760), Vector2.zero);
            UiKit.Img(card, Fx.SprPanel(), Color.white, Image.Type.Sliced);
            UiKit.Txt(card, "SETTINGS", 54, GameManager.C.navy, TextAnchor.MiddleCenter, 0, null, FontStyle.Bold, new Vector2(0, 300));
            var sb = UiKit.Btn(UiKit.Node(card, "sound", new Vector2(0.5f, 0.5f), new Vector2(520, 100), new Vector2(0, 170)),
                GameManager.C.green, "SOUND: ON", 40, ToggleSound);
            soundT = sb.GetComponentInChildren<Text>();
            UiKit.Txt(card, "HOW TO PLAY", 36, GameManager.C.Hex(0x2B4FA0), TextAnchor.MiddleCenter, 0, null, FontStyle.Bold, new Vector2(0, 60));
            UiKit.Txt(card, "Swipe left / right to change lanes\nSwipe up or tap to jump\nSwipe down to roll under barriers\nDouble-tap to ride your hoverboard\nGrab coins, magnets, jetpacks & x2!", 30,
                GameManager.C.navy2, TextAnchor.UpperCenter, 0, null, FontStyle.Normal, new Vector2(0, -80), new Vector2(680, 260));
            UiKit.Btn(UiKit.Node(card, "close", new Vector2(0.5f, 0.5f), new Vector2(360, 96), new Vector2(0, -270)),
                GameManager.C.blue, "CLOSE", 40, () => { Fx.Play("click"); CloseGear(); });
            cGear.gameObject.SetActive(false);
        }

        void ToggleSound()
        {
            var g = GameManager.I;
            g.sound = !g.sound;
            Fx.Sound = g.sound;
            g.Save();
            soundT.text = "SOUND: " + (g.sound ? "ON" : "OFF");
            Fx.Play("click");
        }

        public void OpenGear()
        {
            var s = GameManager.I.st;
            if (s != GameManager.St.Menu && s != GameManager.St.Results && s != GameManager.St.Pause && s != GameManager.St.Missions) return;
            modalOpen = true; cGear.gameObject.SetActive(true); soundT.text = "SOUND: " + (GameManager.I.sound ? "ON" : "OFF");
        }
        public void CloseGear() { modalOpen = false; cGear.gameObject.SetActive(false); }

        public void OnLetter()
        {
            Fx.Play("letter", 0.9f);
            var g = GameManager.I;
            string word = GameManager.Words[g.wordIdx % GameManager.Words.Length];
            if (g.lettersOn <= word.Length) Toast("LETTER  " + word[g.lettersOn - 1] + "!", GameManager.C.blueBright);
        }

        // ================================================= ROUTING
        void Route(GameManager.St st)
        {
            cSplash.gameObject.SetActive(st == GameManager.St.Splash);
            cLoad.gameObject.SetActive(st == GameManager.St.Loading);
            cMenu.gameObject.SetActive(st == GameManager.St.Menu);
            cHud.gameObject.SetActive(st == GameManager.St.Run || st == GameManager.St.Pause || st == GameManager.St.Dying);
            cPause.gameObject.SetActive(st == GameManager.St.Pause || st == GameManager.St.Missions);
            cHigh.gameObject.SetActive(st == GameManager.St.High);
            cResults.gameObject.SetActive(st == GameManager.St.Results);

            if (st == GameManager.St.High)
            {
                hiScore.text = GameManager.I.runScore.ToString("N0");
                if (Sticker != null) UiKit.Img(hiSticker, Sticker, Color.white);
            }
            if (st == GameManager.St.Results) RefreshResults();
            if (st == GameManager.St.Menu)
            {
                foreach (var t in cMenu.GetComponentsInChildren<Text>())
                    if (t.name == "bestT") t.text = GameManager.I.best.ToString("N0");
                modalOpen = false;
                CloseGear();
            }
            if (st == GameManager.St.Pause) { RefreshPause(); quitBtn.gameObject.SetActive(true); resumeBtn.gameObject.SetActive(true); closeMissionsBtn.gameObject.SetActive(false); }
            if (st == GameManager.St.Run && GameManager.I.runJustStarted)
            {
                GameManager.I.runJustStarted = false;
                StartCoroutine(ReadyFlash());
            }
        }

        IEnumerator ReadyFlash()
        {
            readyGo.gameObject.SetActive(true);
            readyT.text = "READY?";
            readyT.color = Color.white;
            float t = 0;
            while (t < 0.7f) { t += Time.unscaledDeltaTime; yield return null; }
            readyT.text = "GO!";
            readyT.color = GameManager.C.gold;
            t = 0;
            while (t < 0.6f) { t += Time.unscaledDeltaTime; yield return null; }
            readyGo.gameObject.SetActive(false);
        }

        void RefreshResults()
        {
            var g = GameManager.I;
            rBank.text = g.bank.ToString("N0");
            rMult.text = "x" + g.multBase;
            rScore.text = g.runScore.ToString("N0");
            rCoins.text = g.runCoins.ToString("N0");
            if (Sticker != null) UiKit.Img(stickerRes, Sticker, Color.white);
            adBtn.interactable = !g.adUsedScore;
            adBtn.gameObject.GetComponent<Image>().color = g.adUsedScore ? GameManager.C.grey : GameManager.C.green;
            watchBtn.interactable = !g.rewardUsed;
            watchBtn.gameObject.GetComponent<Image>().color = g.rewardUsed ? GameManager.C.grey : GameManager.C.green;
        }

        // ================================================= FRAME ANIM
        void Update()
        {
            t0 += Time.unscaledDeltaTime;
            if (cSplash.gameObject.activeSelf && logoBig != null)
                logoBig.localScale = Vector3.one * (1.12f + Mathf.Sin(t0 * 2.2f) * 0.015f);
            if (cMenu.gameObject.activeSelf && tapPlay != null)
                tapPlay.transform.localScale = Vector3.one * (1f + Mathf.Sin(t0 * 4.2f) * 0.05f);
            if (cHigh.gameObject.activeSelf && raysImg != null)
                raysImg.localRotation = Quaternion.Euler(0, 0, t0 * 8f);
            if (cLoad.gameObject.activeSelf)
            {
                logoSmall.localScale = Vector3.one * (0.72f + Mathf.Sin(t0 * 2.4f) * 0.012f);
            }

            if (cHud.gameObject.activeSelf)
            {
                var g = GameManager.I;
                scoreT.text = Mathf.Clamp(g.runScore, 0, 999999).ToString("D5");
                coinT.text = g.runCoins.ToString("N0");
                multT.text = "x" + g.MultTotal;
                boardN.text = g.boardCount.ToString();
                boostN.text = g.boostCount.ToString();
                SetTimer(magRow, magFill, g.tMagnet, 12f);
                SetTimer(jetRow, jetFill, g.tJet, 7f);
                SetTimer(x2Row, x2Fill, g.tX2, 15f);
                SetTimer(brdRow, brdFill, g.tBoard, 30f);
            }
        }

        void SetTimer(RectTransform row, Image fill, float t, float max)
        {
            bool on = t > 0f;
            if (row.gameObject.activeSelf != on) row.gameObject.SetActive(on);
            if (on) fill.fillAmount = Mathf.Clamp01(t / max);
        }
    }
}
