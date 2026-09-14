using System.Collections.Generic;
using UnityEngine;

namespace DummySurfer
{
    /// <summary>
    /// Procedural art + audio foundation. Everything the game shows is generated
    /// in code at runtime: rounded UI sprites, icons, textures (train sides,
    /// graffiti, track, building windows), materials and sound effects.
    /// No imported assets → identical visuals in CI builds and editor.
    /// </summary>
    public static class Fx
    {
        // ------------------------------------------------------- layers
        public const int LGround = 8, LTrain = 9, LObstacle = 10, LCoin = 11, LPower = 12;
        public static readonly int MaskGround = (1 << LGround) | (1 << LTrain);
        public static readonly int MaskBlock = (1 << LTrain) | (1 << LObstacle);
        public static readonly int MaskPickup = (1 << LCoin) | (1 << LPower);

        // ------------------------------------------------------- fonts / shaders
        public static Font Font;
        public static Shader LitShader;      // URP Lit (or Unlit fallback)
        public static Shader ColorShader;    // flat color, always works
        public static Shader TexShader;      // unlit textured, always works
        public static Shader AlphaShader;    // unlit transparent, always works

        // ------------------------------------------------------- caches
        static readonly Dictionary<string, Material> Mats = new Dictionary<string, Material>();
        static readonly Dictionary<string, Sprite> Sprites = new Dictionary<string, Sprite>();
        static readonly Dictionary<string, AudioClip> Clips = new Dictionary<string, AudioClip>();
        public static AudioSource Sfx;
        public static bool Sound = true;

        static System.Random rng = new System.Random(1234);

        // ===================================================== INIT
        public static void Init(Transform audioHost)
        {
            if (Font == null)
            {
                try { Font = Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf"); } catch { }
                if (Font == null) { try { Font = Resources.GetBuiltinResource<Font>("Arial.ttf"); } catch { } }
            }

            Material anchor = Resources.Load<Material>("Mats/anchor_lit");
            Material anchorUc = Resources.Load<Material>("Mats/anchor_unlitcolor");
            Material anchorTx = Resources.Load<Material>("Mats/anchor_unlittex");
            Material anchorAl = Resources.Load<Material>("Mats/anchor_unlitalpha");

            LitShader = anchor != null ? anchor.shader : null;
            if (LitShader == null || LitShader.name.Contains("Unlit")) { /* keep, still valid */ }
            ColorShader = anchorUc != null ? anchorUc.shader : null;
            TexShader = anchorTx != null ? anchorTx.shader : null;
            AlphaShader = anchorAl != null ? anchorAl.shader : null;

            if (ColorShader == null) ColorShader = Shader.Find("Unlit/Color");
            if (TexShader == null) TexShader = Shader.Find("Unlit/Texture");
            if (AlphaShader == null) AlphaShader = Shader.Find("Unlit/Transparent");

            var go = new GameObject("~Sfx");
            go.transform.SetParent(audioHost, false);
            Sfx = go.AddComponent<AudioSource>();
            Sfx.playOnAwake = false;
        }

        // ===================================================== MATERIALS
        public static Material Mat(Color c)
        {
            string key = "c" + ColorKey(c);
            Material m;
            if (Mats.TryGetValue(key, out m) && m != null) return m;
            Shader sh = LitShader;
            if (sh != null && sh.name.Contains("Lit")) m = new Material(sh);
            else m = new Material(ColorShader);
            SetColor(m, c);
            Try(() => { m.SetFloat("_Smoothness", 0.35f); m.SetFloat("_Metallic", 0f); });
            Mats[key] = m;
            return m;
        }

        public static Material MatGlow(Color c)
        {
            string key = "g" + ColorKey(c);
            Material m;
            if (Mats.TryGetValue(key, out m) && m != null) return m;
            Shader sh = LitShader;
            m = (sh != null && sh.name.Contains("Lit")) ? new Material(sh) : new Material(ColorShader);
            SetColor(m, c);
            Try(() => m.SetFloat("_Smoothness", 0.8f));
            Try(() => { m.EnableKeyword("_EMISSION"); m.SetColor("_EmissionColor", c * 0.6f); });
            Mats[key] = m;
            return m;
        }

        public static Material MatTex(Texture2D tex, bool transparent, float tx = 1f, float ty = 1f)
        {
            string key = (transparent ? "t" : "x") + tex.name + "_" + tx.ToString("0.##") + "_" + ty.ToString("0.##");
            Material m;
            if (Mats.TryGetValue(key, out m) && m != null) return m;
            m = new Material(transparent ? AlphaShader : TexShader);
            m.mainTexture = tex;
            if (Mathf.Abs(tx - 1f) > 0.01f || Mathf.Abs(ty - 1f) > 0.01f) m.mainTextureScale = new Vector2(tx, ty);
            Mats[key] = m;
            return m;
        }

        static void SetColor(Material m, Color c)
        {
            Try(() => m.SetColor("_BaseColor", c));
            Try(() => m.SetColor("_Color", c));
            if (!m.HasProperty("_BaseColor") && !m.HasProperty("_Color")) { /* unlit/color uses _Color */ }
        }

        static string ColorKey(Color c)
        {
            return ColorUtility.ToHtmlStringRGBA(c);
        }

        static void Try(System.Action a) { try { a(); } catch { } }

        // ===================================================== TEXTURES
        static Texture2D Tex(string name, int w, int h)
        {
            var t = new Texture2D(w, h, TextureFormat.RGBA32, false);
            t.name = name;
            t.filterMode = FilterMode.Bilinear;
            t.wrapMode = TextureWrapMode.Clamp;
            return t;
        }

        static void Blit(Texture2D t, Color32[] px) { t.SetPixels32(px); t.Apply(false, false); }

        static float SdRound(float x, float y, float cx, float cy, float hw, float hh, float r)
        {
            float dx = Mathf.Abs(x - cx) - (hw - r);
            float dy = Mathf.Abs(y - cy) - (hh - r);
            float ox = Mathf.Max(dx, 0f), oy = Mathf.Max(dy, 0f);
            return Mathf.Sqrt(ox * ox + oy * oy) + Mathf.Min(Mathf.Max(dx, dy), 0f) - r;
        }

        static float SdCircle(float x, float y, float cx, float cy, float r)
        {
            float dx = x - cx, dy = y - cy;
            return Mathf.Sqrt(dx * dx + dy * dy) - r;
        }

        static float Aa(float sd) { return Mathf.Clamp01(1.5f - sd); } // 1 inside

        // ---- rounded panel (9-slice)
        public static Sprite SprPanel()
        {
            Sprite s; if (Get("panel", out s)) return s;
            int w = 72, h = 72, r = 20;
            var t = Tex("panel", w, h);
            var px = new Color32[w * h];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                {
                    float sd = SdRound(x + 0.5f, y + 0.5f, w / 2f, h / 2f, w / 2f - 2, h / 2f - 2, r);
                    byte a = (byte)(255 * Aa(sd));
                    px[y * w + x] = new Color32(255, 255, 255, a);
                }
            Blit(t, px);
            var sp = Sprite.Create(t, new Rect(0, 0, w, h), new Vector2(0.5f, 0.5f), 100f, 0, SpriteMeshType.FullRect, new Vector4(26, 26, 26, 26));
            Sprites["panel"] = sp;
            return sp;
        }

        // ---- circle
        public static Sprite SprCircle()
        {
            Sprite s; if (Get("circle", out s)) return s;
            int w = 96;
            var t = Tex("circle", w, w);
            var px = new Color32[w * w];
            for (int y = 0; y < w; y++)
                for (int x = 0; x < w; x++)
                {
                    float sd = SdCircle(x + 0.5f, y + 0.5f, w / 2f, w / 2f, w / 2f - 2);
                    px[y * w + x] = new Color32(255, 255, 255, (byte)(255 * Aa(sd)));
                }
            Blit(t, px);
            var sp = Sprite.Create(t, new Rect(0, 0, w, w), new Vector2(0.5f, 0.5f), 100f);
            Sprites["circle"] = sp; return sp;
        }

        // ---- 5-point star
        public static Sprite SprStar()
        {
            Sprite s; if (Get("star", out s)) return s;
            int w = 96; float R = w * 0.48f, r = w * 0.20f;
            var t = Tex("star", w, w);
            var px = new Color32[w * w];
            for (int y = 0; y < w; y++)
                for (int x = 0; x < w; x++)
                {
                    float dx = x + 0.5f - w / 2f, dy = y + 0.5f - w / 2f;
                    float len = Mathf.Sqrt(dx * dx + dy * dy);
                    float a = Mathf.Atan2(dy, dx);
                    float seg = Mathf.Repeat(a / (Mathf.PI / 5f), 2f);
                    float rr = Mathf.Lerp(R, r, Mathf.Abs(seg - 1f));
                    px[y * w + x] = new Color32(255, 255, 255, (byte)(255 * Aa(len - rr)));
                }
            Blit(t, px);
            var sp = Sprite.Create(t, new Rect(0, 0, w, w), new Vector2(0.5f, 0.5f), 100f);
            Sprites["star"] = sp; return sp;
        }

        // ---- coin (gold disc + star)
        public static Sprite SprCoin()
        {
            Sprite s; if (Get("coin", out s)) return s;
            int w = 128; float R = w * 0.48f;
            var cGold = C(0xFF, 0xD2, 0x3E); var cRim = C(0xE8, 0x9C, 0x0A);
            var cIn = C(0xFF, 0xE4, 0x7A); var cStar = C(0xFF, 0xEF, 0xB4);
            var t = Tex("coin", w, w);
            var px = new Color32[w * w];
            for (int y = 0; y < w; y++)
                for (int x = 0; x < w; x++)
                {
                    float dx = x + 0.5f - w / 2f, dy = y + 0.5f - w / 2f;
                    float len = Mathf.Sqrt(dx * dx + dy * dy);
                    float sd = len - R;
                    if (sd > 1.5f) { px[y * w + x] = new Color32(0, 0, 0, 0); continue; }
                    Color32 c;
                    if (len > R - 9) c = cRim;
                    else
                    {
                        float a = Mathf.Atan2(dy, dx);
                        float seg = Mathf.Repeat(a / (Mathf.PI / 5f), 2f);
                        float rr = Mathf.Lerp(R * 0.56f, R * 0.24f, Mathf.Abs(seg - 1f));
                        float sdStar = len - rr;
                        c = sdStar < 0 ? cStar : cIn;
                        // subtle top shading
                        if (dy > R * 0.3f) c = Color32.Lerp(c, new Color32(255, 255, 255, 255), 0.18f);
                    }
                    byte alpha = (byte)(255 * Aa(sd));
                    px[y * w + x] = new Color32(c.r, c.g, c.b, alpha);
                }
            Blit(t, px);
            var sp = Sprite.Create(t, new Rect(0, 0, w, w), new Vector2(0.5f, 0.5f), 100f);
            Sprites["coin"] = sp; return sp;
        }

        // ---- soft radial glow
        public static Sprite SprGlow()
        {
            Sprite s; if (Get("glow", out s)) return s;
            int w = 128; float R = w * 0.5f;
            var t = Tex("glow", w, w);
            var px = new Color32[w * w];
            for (int y = 0; y < w; y++)
                for (int x = 0; x < w; x++)
                {
                    float dx = x + 0.5f - w / 2f, dy = y + 0.5f - w / 2f;
                    float len = Mathf.Sqrt(dx * dx + dy * dy) / R;
                    byte a = (byte)(255 * Mathf.Clamp01(1f - len) * Mathf.Clamp01(1f - len));
                    px[y * w + x] = new Color32(255, 255, 255, a);
                }
            Blit(t, px);
            var sp = Sprite.Create(t, new Rect(0, 0, w, w), new Vector2(0.5f, 0.5f), 100f);
            Sprites["glow"] = sp; return sp;
        }

        // ---- sun rays wedge wheel (for highscore bg)
        public static Sprite SprRays()
        {
            Sprite s; if (Get("rays", out s)) return s;
            int w = 256; float R = w * 0.72f;
            var t = Tex("rays", w, w);
            t.wrapMode = TextureWrapMode.Repeat;
            var px = new Color32[w * w];
            for (int y = 0; y < w; y++)
                for (int x = 0; x < w; x++)
                {
                    float dx = x + 0.5f - w / 2f, dy = y + 0.5f - w / 2f;
                    float len = Mathf.Sqrt(dx * dx + dy * dy);
                    float a = Mathf.Repeat(Mathf.Atan2(dy, dx) / (2f * Mathf.PI), 1f);
                    float band = Mathf.SmoothStep(0.02f, 0.06f, Mathf.Abs(a - 0.5f));
                    byte al = (byte)(255 * band * Mathf.Clamp01(1.3f - len / R));
                    px[y * w + x] = new Color32(255, 255, 255, al);
                }
            Blit(t, px);
            var sp = Sprite.Create(t, new Rect(0, 0, w, w), new Vector2(0.5f, 0.5f), 100f);
            Sprites["rays"] = sp; return sp;
        }

        // ---- magnet icon (red horseshoe)
        public static Sprite SprIcon(string kind)
        {
            Sprite s; if (Get("ic_" + kind, out s)) return s;
            int w = 96;
            var t = Tex("ic_" + kind, w, w);
            var px = new Color32[w * w];
            float R = w * 0.36f, th = w * 0.13f;
            for (int y = 0; y < w; y++)
                for (int x = 0; x < w; x++)
                {
                    float dx = x + 0.5f - w / 2f, dy = y + 0.5f - w / 2f;
                    float len = Mathf.Sqrt(dx * dx + dy * dy);
                    byte a = 0; byte rr = 255, gg = 255, bb = 255;
                    float ang = Mathf.Atan2(dy, dx) * Mathf.Rad2Deg; // -180..180
                    if (kind == "magnet")
                    {
                        float ring = Mathf.Abs(len - R);
                        bool opening = dy > 0 && Mathf.Abs(dx) < R * 0.52f;
                        if (ring < th && !opening)
                        {
                            a = 255;
                            if (dy > R * 0.45f) { rr = 240; gg = 240; bb = 240; } // white tips
                            else { rr = 235; gg = 61; bb = 54; }
                        }
                    }
                    else if (kind == "rocket")
                    {
                        float body = SdRound(x, y, w / 2f, w * 0.42f, w * 0.11f, w * 0.30f, w * 0.10f);
                        float nose = SdCircle(x, y, w / 2f, w * 0.74f, w * 0.11f);
                        float finL = SdRound(x, y, w * 0.32f, w * 0.16f, w * 0.05f, w * 0.12f, w * 0.03f);
                        float finR = SdRound(x, y, w * 0.68f, w * 0.16f, w * 0.05f, w * 0.12f, w * 0.03f);
                        float flame = SdRound(x, y, w / 2f, w * 0.06f, w * 0.06f, w * 0.08f, w * 0.04f);
                        float sd = Mathf.Min(Mathf.Min(body, nose), Mathf.Min(finL, finR));
                        if (sd < 0) { a = 255; rr = 236; gg = 240; bb = 244; }
                        if (finL < 0 || finR < 0) { rr = 226; gg = 88; bb = 34; }
                        if (flame < 0) { a = 255; rr = 255; gg = 160; bb = 40; }
                    }
                    else if (kind == "board")
                    {
                        float co = Mathf.Cos(-0.5f), si = Mathf.Sin(-0.5f);
                        float rx = dx * co - dy * si, ry = dx * si + dy * co;
                        float deck = SdRound(rx, ry, 0, 0, w * 0.36f, w * 0.09f, w * 0.08f);
                        if (deck < 0) { a = 255; rr = 255; gg = 193; bb = 37; }
                        float w1 = SdCircle(x, y, w * 0.30f, w * 0.28f, w * 0.055f);
                        float w2 = SdCircle(x, y, w * 0.70f, w * 0.28f, w * 0.055f);
                        if (w1 < 0 || w2 < 0) { a = 255; rr = 40; gg = 46; bb = 70; }
                    }
                    else if (kind == "home")
                    {
                        float body = SdRound(x, y, w / 2f, w * 0.38f, w * 0.26f, w * 0.20f, w * 0.03f);
                        float dx2 = Mathf.Abs(x + 0.5f - w / 2f);
                        bool roof = y > w * 0.56f && y < w * 0.82f && dx2 < (w * 0.82f - y) * 1.05f;
                        float door = SdRound(x, y, w / 2f, w * 0.30f, w * 0.07f, w * 0.11f, w * 0.02f);
                        if (body < 0 || roof) { if (door > -1) { a = 255; rr = 255; gg = 255; bb = 255; } }
                        if (door < 0) a = 0;
                    }
                    else if (kind == "list")
                    {
                        float b1 = SdRound(x, y, w / 2f, w * 0.70f, w * 0.30f, w * 0.045f, w * 0.04f);
                        float b2 = SdRound(x, y, w / 2f, w * 0.50f, w * 0.30f, w * 0.045f, w * 0.04f);
                        float b3 = SdRound(x, y, w / 2f, w * 0.30f, w * 0.30f, w * 0.045f, w * 0.04f);
                        if (b1 < 0 || b2 < 0 || b3 < 0) { a = 255; rr = 255; gg = 255; bb = 255; }
                    }
                    else if (kind == "gear")
                    {
                        float aa = Mathf.Atan2(dy, dx);
                        float tooth = Mathf.SmoothStep(0.32f, 0.5f, Mathf.Repeat(aa / (2f * Mathf.PI) * 12f, 1f) > 0.5f ? 1f - Mathf.Repeat(aa / (2f * Mathf.PI) * 12f, 1f) : Mathf.Repeat(aa / (2f * Mathf.PI) * 12f, 1f));
                        float rr2 = R * 0.82f + tooth * R * 0.30f;
                        float sd = len - rr2;
                        float hole = len - R * 0.34f;
                        if (sd < 0 && hole > 0) { a = 255; rr = 255; gg = 255; bb = 255; }
                    }
                    else if (kind == "tv")
                    {
                        float body = SdRound(x, y, w / 2f, w * 0.45f, w * 0.28f, w * 0.20f, w * 0.05f);
                        float tri = x > w * 0.44f && x < w * 0.60f && y > w * 0.34f && y < w * 0.56f && (x - w * 0.44f) > (y - w * 0.34f) * 0.7f;
                        if (body < 0) { a = 255; rr = 255; gg = 255; bb = 255; }
                        if (tri) { rr = 40; gg = 160; bb = 80; }
                        // antennas
                        float l1 = Mathf.Abs((x - w * 0.38f) * 0.7f - (y - w * 0.62f));
                        float l2 = Mathf.Abs((x - w * 0.62f) * -0.7f - (y - w * 0.62f));
                        if ((l1 < 2 && x > w * 0.34f && x < w * 0.48f && y > w * 0.6f && y < w * 0.78f) ||
                            (l2 < 2 && x > w * 0.52f && x < w * 0.66f && y > w * 0.6f && y < w * 0.78f))
                        { a = 255; rr = 255; gg = 255; bb = 255; }
                    }
                    else if (kind == "pause")
                    {
                        float b1 = SdRound(x, y, w * 0.36f, w / 2f, w * 0.07f, w * 0.26f, w * 0.04f);
                        float b2 = SdRound(x, y, w * 0.64f, w / 2f, w * 0.07f, w * 0.26f, w * 0.04f);
                        if (b1 < 0 || b2 < 0) { a = 255; rr = 255; gg = 255; bb = 255; }
                    }
                    else if (kind == "x2")
                    {
                        float sd = len - R * 0.95f;
                        float a2 = Mathf.Atan2(dy, dx);
                        float seg = Mathf.Repeat(a2 / (Mathf.PI / 5f), 2f);
                        float rr3 = Mathf.Lerp(R * 0.95f, R * 0.40f, Mathf.Abs(seg - 1f));
                        if (len - rr3 < 0) { a = 255; rr = 255; gg = 214; bb = 64; }
                    }
                    else if (kind == "plus")
                    {
                        float b1 = SdRound(x, y, w / 2f, w / 2f, w * 0.26f, w * 0.07f, w * 0.05f);
                        float b2 = SdRound(x, y, w / 2f, w / 2f, w * 0.07f, w * 0.26f, w * 0.05f);
                        if (b1 < 0 || b2 < 0) { a = 255; rr = 255; gg = 255; bb = 255; }
                    }
                    px[y * w + x] = new Color32(rr, gg, bb, a);
                }
            Blit(t, px);
            var sp = Sprite.Create(t, new Rect(0, 0, w, w), new Vector2(0.5f, 0.5f), 100f);
            Sprites["ic_" + kind] = sp; return sp;
        }

        // ---- vertical gradient (fullscreen bg)
        public static Sprite SprGrad(Color top, Color bottom)
        {
            string key = "grad" + ColorUtility.ToHtmlStringRGBA(top) + ColorUtility.ToHtmlStringRGBA(bottom);
            Sprite s; if (Sprites.TryGetValue(key, out s) && s != null) return s;
            int w = 32, h = 128;
            var t = Tex(key, w, h);
            var px = new Color32[w * h];
            for (int y = 0; y < h; y++)
            {
                Color32 c = Color32.Lerp(bottom, top, y / (float)(h - 1));
                for (int x = 0; x < w; x++) px[y * w + x] = c;
            }
            Blit(t, px);
            var sp = Sprite.Create(t, new Rect(0, 0, w, h), new Vector2(0.5f, 0.5f), 100f);
            Sprites[key] = sp; return sp;
        }

        // ---- diagonal striped barrier band
        public static Texture2D TexStripes()
        {
            Texture2D t; if (GetTex("stripes", out t)) return t;
            int w = 256, h = 128;
            t = Tex("stripes", w, h);
            t.wrapMode = TextureWrapMode.Repeat;
            var px = new Color32[w * h];
            var y1 = C(0xFF, 0xC9, 0x33); var y2 = C(0xE6, 0x4B, 0x3C);
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                {
                    float v = Mathf.Repeat((x + y) / 44f, 1f);
                    px[y * w + x] = v < 0.5f ? y1 : y2;
                }
            Blit(t, px);
            return t;
        }

        // ---- track tile: gravel + ballast lanes + sleepers (tiles 8m x 4m)
        public static Texture2D TexTrack()
        {
            Texture2D t; if (GetTex("track", out t)) return t;
            int w = 256, h = 256;
            t = Tex("track", w, h);
            t.wrapMode = TextureWrapMode.Repeat;
            var px = new Color32[w * h];
            var gravel = C(0x8f, 0x8a, 0x7c);
            var ballast = C(0xa5, 0x9e, 0x8d);
            var sleep = C(0x5d, 0x48, 0x33);
            var sleep2 = C(0x51, 0x3e, 0x2b);
            var rail = C(0x4a, 0x4a, 0x50);
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                {
                    float fx = x / (float)w;
                    Color32 c = gravel;
                    // 3 lanes centered at 1/6,3/6,5/6
                    float lane = -1f;
                    for (int i = 0; i < 3; i++)
                    {
                        float lc = (i * 2f + 1f) / 6f;
                        if (Mathf.Abs(fx - lc) < 0.145f) lane = fx - lc;
                    }
                    if (lane >= -1f)
                    {
                        c = ballast;
                        // sleepers every 42px
                        int band = (y + (int)(lane * 6)) % 42;
                        if (band < 13) c = (band % 3 == 0) ? sleep2 : sleep;
                        // rail shadows
                        if (Mathf.Abs(Mathf.Abs(lane) - 0.058f) < 0.012f) c = rail;
                    }
                    // speckle
                    int n = (x * 7 + y * 13) % 97;
                    if (n < 4) c = Color32.Lerp(c, new Color32(255, 255, 255, 255), 0.08f);
                    px[y * w + x] = c;
                }
            Blit(t, px);
            return t;
        }

        // ---- building windows (per base color)
        public static Texture2D TexWindows(Color baseCol, int seed)
        {
            string key = "win" + ColorUtility.ToHtmlStringRGB(baseCol) + seed;
            Texture2D t;
            if (GetTex(key, out t)) return t;
            int w = 128, h = 256;
            t = Tex(key, w, h);
            t.wrapMode = TextureWrapMode.Repeat;
            var px = new Color32[w * h];
            var wall = (Color32)baseCol;
            var dark = Color32.Lerp(wall, new Color32(20, 24, 40, 255), 0.35f);
            var lit = C(0xFF, 0xEE, 0xB0); var off = C(0x2a, 0x36, 0x54);
            var sys = new System.Random(seed);
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                {
                    Color32 c = ((x / 8 + y / 8) % 2 == 0) ? wall : Color32.Lerp(wall, dark, 0.25f);
                    px[y * w + x] = c;
                }
            for (int r = 0; r < 7; r++)
                for (int col = 0; col < 3; col++)
                {
                    int x0 = 14 + col * 38, y0 = 20 + r * 34;
                    bool on = sys.NextDouble() < 0.55;
                    var wc = on ? lit : off;
                    for (int yy = 0; yy < 22; yy++)
                        for (int xx = 0; xx < 26; xx++)
                        {
                            int X = x0 + xx, Y = y0 + yy;
                            if (X < 0 || X >= w || Y < 0 || Y >= h) continue;
                            bool edge = xx == 0 || yy == 0 || xx == 25 || yy == 21;
                            px[Y * w + X] = edge ? dark : wc;
                        }
                }
            // awning strip at bottom
            for (int y = 0; y < 18; y++)
                for (int x = 0; x < w; x++)
                {
                    var ac = (x / 16) % 2 == 0 ? C(0xE8, 0x4B, 0x4B) : C(0xFF, 0xF1, 0xDD);
                    px[y * w + x] = ac;
                }
            Blit(t, px);
            return t;
        }

        // ---- train side texture (per color)
        public static Texture2D TexTrainSide(Color col)
        {
            string key = "trn" + ColorUtility.ToHtmlStringRGB(col);
            Texture2D t; if (GetTex(key, out t)) return t;
            int w = 256, h = 128;
            t = Tex(key, w, h);
            t.wrapMode = TextureWrapMode.Repeat;
            var px = new Color32[w * h];
            var body = (Color32)col;
            var dark = Color32.Lerp(body, new Color32(10, 12, 20, 255), 0.4f);
            var lite = Color32.Lerp(body, new Color32(255, 255, 255, 255), 0.25f);
            var glass = C(0xBF, 0xE9, 0xFF);
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                {
                    Color32 c = body;
                    if (y < 10) c = dark;                 // skirt
                    else if (y > h - 8) c = dark;         // roof edge
                    else if (y > h - 14) c = lite;
                    // window band
                    if (y > 62 && y < 100)
                    {
                        int m = x % 44;
                        if (m > 6 && m < 38) c = glass;
                        if (y > 94) c = Color32.Lerp(glass, body, 0.4f);
                    }
                    // yellow stripe
                    if (y > 26 && y < 34) c = C(0xFF, 0xD2, 0x3E);
                    px[y * w + x] = c;
                }
            Blit(t, px);
            return t;
        }

        // ---- train front
        public static Texture2D TexTrainFront(Color col)
        {
            string key = "trf" + ColorUtility.ToHtmlStringRGB(col);
            Texture2D t; if (GetTex(key, out t)) return t;
            int w = 128, h = 128;
            t = Tex(key, w, h);
            var px = new Color32[w * h];
            var body = (Color32)col;
            var glass = C(0x9f, 0xd8, 0xf2);
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                {
                    Color32 c = body;
                    if (y > 60 && y < 112 && x > 14 && x < 114) c = glass;
                    if (y < 12) c = Color32.Lerp(body, new Color32(10, 10, 16, 255), 0.5f);
                    if (y > 20 && y < 28) c = C(0xFF, 0xD2, 0x3E);
                    // headlights
                    if (SdCircle(x, y, 22, 16, 7) < 0 || SdCircle(x, y, 106, 16, 7) < 0) c = C(0xFF, 0xF3, 0xB0);
                    px[y * w + x] = c;
                }
            Blit(t, px);
            return t;
        }

        // ---- graffiti wall (blobs + drips + speckles)
        public static Texture2D TexGraffiti(Color wallCol, int seed)
        {
            string key = "graf" + ColorUtility.ToHtmlStringRGB(wallCol) + seed;
            Texture2D t; if (GetTex(key, out t)) return t;
            int w = 256, h = 128;
            t = Tex(key, w, h);
            t.wrapMode = TextureWrapMode.Repeat;
            var px = new Color32[w * h];
            var wall = (Color32)wallCol;
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                {
                    int n = (x / 10 * 31 + y / 10 * 17) % 5;
                    px[y * w + x] = Color32.Lerp(wall, Color32.Lerp(wall, new Color32(0, 0, 0, 255), 0.12f), n / 8f);
                }
            var sys = new System.Random(seed);
            Color32[] cols = { C(0x35, 0xC4, 0xB6), C(0xFF, 0x8A, 0x3D), C(0x9B, 0x59, 0xD0), C(0xFF, 0xD2, 0x3E), C(0x4A, 0x90, 0xD9), C(0xFF, 0x6B, 0x8E) };
            for (int b = 0; b < 7; b++)
            {
                float cx = (float)(sys.NextDouble() * w), cy = 30 + (float)sys.NextDouble() * (h - 55);
                float rw = 18 + (float)sys.NextDouble() * 26, rh = 12 + (float)sys.NextDouble() * 16;
                var fill = cols[sys.Next(cols.Length)];
                // outline
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < w; x++)
                    {
                        float ex = (x - cx) / (rw + 5), ey = (y - cy) / (rh + 5);
                        float inO = ex * ex + ey * ey;
                        if (inO < 1) px[y * w + x] = new Color32(255, 255, 255, 255);
                    }
                // fill
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < w; x++)
                    {
                        float ex = (x - cx) / rw, ey = (y - cy) / rh;
                        if (ex * ex + ey * ey < 1) px[y * w + x] = fill;
                    }
                // highlight
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < w; x++)
                    {
                        float ex = (x - cx + rw * 0.25f) / (rw * 0.4f), ey = (y - cy + rh * 0.3f) / (rh * 0.4f);
                        if (ex * ex + ey * ey < 1)
                        {
                            var c0 = px[y * w + x];
                            px[y * w + x] = Color32.Lerp(c0, new Color32(255, 255, 255, 255), 0.45f);
                        }
                    }
                // drips
                int dn = 2 + sys.Next(3);
                for (int d = 0; d < dn; d++)
                {
                    float dx = cx + (float)(sys.NextDouble() - 0.5) * rw * 1.4f;
                    float dl = 12 + (float)sys.NextDouble() * 34;
                    for (int y = (int)cy; y < Mathf.Min(h, cy + dl); y++)
                        for (int x = (int)dx - 2; x <= (int)dx + 2; x++)
                        {
                            int X = ((x % w) + w) % w;
                            if (y < h) px[y * w + X] = fill;
                        }
                }
            }
            // speckles
            for (int i = 0; i < 260; i++)
            {
                int x = sys.Next(w), y = sys.Next(h);
                var c0 = px[y * w + x];
                px[y * w + x] = Color32.Lerp(c0, new Color32(255, 255, 255, 255), 0.35f);
            }
            Blit(t, px);
            return t;
        }

        // ---- far skyline silhouette
        public static Texture2D TexSkyline()
        {
            Texture2D t; if (GetTex("skyline", out t)) return t;
            int w = 512, h = 128;
            t = Tex("skyline", w, h);
            t.wrapMode = TextureWrapMode.Repeat;
            var px = new Color32[w * h];
            var sys = new System.Random(77);
            var col = new Color32(255, 255, 255, 255);
            int x = 0;
            while (x < w)
            {
                int bw = 14 + sys.Next(34);
                int bh = 22 + sys.Next(86);
                for (int xx = x; xx < Mathf.Min(w, x + bw); xx++)
                    for (int yy = 0; yy < Mathf.Min(h, bh); yy++)
                        px[yy * w + xx] = col;
                if (sys.NextDouble() < 0.3)
                {
                    int ax = x + bw / 2, ah = bh + 10 + sys.Next(14);
                    for (int yy = bh; yy < Mathf.Min(h, ah); yy++) px[yy * w + ax] = col;
                }
                x += bw + sys.Next(4);
            }
            Blit(t, px);
            return t;
        }

        // ---- clouds puff
        public static Sprite SprCloud()
        {
            Sprite s; if (Get("cloud", out s)) return s;
            int w = 128, h = 64;
            var t = Tex("cloud", w, h);
            var px = new Color32[w * h];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                {
                    float d = Mathf.Min(
                        Mathf.Min(SdCircle(x, y, 34, 24, 20), SdCircle(x, y, 66, 30, 26)),
                        SdCircle(x, y, 96, 24, 18));
                    d = Mathf.Min(d, SdRound(x, y, 64, 18, 52, 10, 10));
                    px[y * w + x] = new Color32(255, 255, 255, (byte)(255 * Aa(d)));
                }
            Blit(t, px);
            var sp = Sprite.Create(t, new Rect(0, 0, w, h), new Vector2(0.5f, 0.5f), 100f);
            Sprites["cloud"] = sp; return sp;
        }

        static bool Get(string k, out Sprite s) { return Sprites.TryGetValue(k, out s) && s != null; }
        static bool GetTex(string k, out Texture2D t) { return Sprites.TryGetValue(k, out var s) && s != null && (t = s.texture) != null; }

        public static Color32 C(int r, int g, int b) { return new Color32((byte)r, (byte)g, (byte)b, 255); }

        // ===================================================== AUDIO
        public static void Play(string clip, float vol = 1f)
        {
            if (!Sound || Sfx == null) return;
            AudioClip c;
            if (!Clips.TryGetValue(clip, out c) || c == null) { c = BuildClip(clip); Clips[clip] = c; }
            if (c != null) Sfx.PlayOneShot(c, vol);
        }

        static AudioClip BuildClip(string kind)
        {
            const int SR = 22050;
            float dur = 0.3f;
            switch (kind)
            {
                case "coin": dur = 0.14f; break;
                case "jump": dur = 0.22f; break;
                case "roll": dur = 0.16f; break;
                case "click": dur = 0.06f; break;
                case "crash": dur = 0.45f; break;
                case "stumble": dur = 0.2f; break;
                case "power": dur = 0.35f; break;
                case "whistle": dur = 0.5f; break;
                case "letter": dur = 0.18f; break;
                case "reward": dur = 0.5f; break;
                case "board": dur = 0.3f; break;
            }
            int n = (int)(SR * dur);
            float[] d = new float[n];
            var sys = new System.Random(99);
            for (int i = 0; i < n; i++)
            {
                float t01 = i / (float)n;
                float t = i / (float)SR;
                float env = Mathf.Sin(Mathf.PI * Mathf.Clamp01(t01 * 1.15f));
                env *= env;
                float v = 0f;
                switch (kind)
                {
                    case "coin":
                    {
                        float f = t01 < 0.45f ? 1046f : 1568f;
                        v = Mathf.Sin(2 * Mathf.PI * f * t) * 0.6f;
                        break;
                    }
                    case "jump": v = Mathf.Sin(2 * Mathf.PI * Mathf.Lerp(280f, 660f, t01) * t) * 0.5f; break;
                    case "roll": v = (float)(sys.NextDouble() * 2 - 1) * 0.28f * Mathf.Lerp(1f, 0.2f, t01); break;
                    case "click": v = Mathf.Sin(2 * Mathf.PI * 1200f * t) * 0.5f; break;
                    case "crash":
                    {
                        float noise = (float)(sys.NextDouble() * 2 - 1);
                        float thud = Mathf.Sin(2 * Mathf.PI * 85f * t) * Mathf.Clamp01(1.6f - t01 * 2.4f);
                        v = noise * Mathf.Lerp(1f, 0.15f, t01) * 0.7f + thud * 0.7f;
                        break;
                    }
                    case "stumble": v = Mathf.Sin(2 * Mathf.PI * Mathf.Lerp(420f, 190f, t01) * t) * 0.55f; break;
                    case "power":
                    {
                        float[] seq = { 523f, 659f, 784f, 1046f };
                        float f = seq[Mathf.Clamp((int)(t01 * 4), 0, 3)];
                        v = Mathf.Sin(2 * Mathf.PI * f * t) * 0.5f;
                        break;
                    }
                    case "whistle":
                    {
                        float f = t01 < 0.5f ? 950f : 1150f;
                        v = (Mathf.Sign(Mathf.Sin(2 * Mathf.PI * f * t)) * 0.5f + Mathf.Sin(2 * Mathf.PI * f * t) * 0.5f) * 0.42f;
                        break;
                    }
                    case "letter": v = Mathf.Sin(2 * Mathf.PI * 1320f * t) * 0.5f; break;
                    case "reward":
                    {
                        float[] seq = { 523f, 659f, 784f, 1046f, 1318f };
                        float f = seq[Mathf.Clamp((int)(t01 * 5), 0, 4)];
                        v = Mathf.Sin(2 * Mathf.PI * f * t) * 0.5f;
                        break;
                    }
                    case "board": v = Mathf.Sin(2 * Mathf.PI * Mathf.Lerp(200f, 500f, t01) * t) * 0.4f + (float)(sys.NextDouble() * 2 - 1) * 0.06f; break;
                }
                d[i] = Mathf.Clamp(v * env, -1f, 1f);
            }
            var c = AudioClip.Create(kind, n, 1, SR, false);
            c.SetData(d, 0);
            return c;
        }
    }
}
