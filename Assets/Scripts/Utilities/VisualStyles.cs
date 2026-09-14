using System.Collections.Generic;
using UnityEngine;
using UnityEngine.Rendering;

namespace DummySurfer.Utilities
{
    /// <summary>
    /// Central art direction: one cohesive palette, flat stylized materials.
    /// All visuals are original, procedurally-built Unity primitives (IP boundary honored).
    /// </summary>
    public static class VisualStyles
    {
        // ---- UI palette (bright, subway-street energy) ----
        public static readonly Color Background   = Hex("#0F2436");  // deep sky navy (cards)
        public static readonly Color Surface      = Hex("#1C3A54");
        public static readonly Color SurfaceHi    = Hex("#2B4E6E");
        public static readonly Color Primary      = Hex("#FF7A1A");  // spray-can orange
        public static readonly Color PrimaryDark  = Hex("#C25A0E");
        public static readonly Color Accent       = Hex("#25D3C2");  // mint teal
        public static readonly Color Danger       = Hex("#E5383B");
        public static readonly Color TextPrimary  = Hex("#FFFFFF");
        public static readonly Color TextMuted    = Hex("#CFE3F0");
        public static readonly Color Gold         = Hex("#FFC61A");

        // ---- World palette: bright sunny day, colorful city ----
        public static readonly Color SkyTop       = Hex("#35B9F1");  // vivid mid-morning sky
        public static readonly Color FogColor     = Hex("#A8DFF6");  // airy horizon haze
        public static readonly Color Asphalt      = Hex("#9AA0A8");  // light gravel runway
        public static readonly Color RailMetal    = Hex("#C7CFD9");
        public static readonly Color Sleeper      = Hex("#8A5A33");  // warm wood sleepers
        public static readonly Color Concrete     = Hex("#D6DCE4");
        public static readonly Color Skyline      = Hex("#F4A85C");  // warm brick towers
        public static readonly Color SkylineFar   = Hex("#7FC4E8");  // hazy distant blocks
        public static readonly Color TrainRed     = Hex("#E8452C");
        public static readonly Color TrainBlue    = Hex("#1F7AC4");
        public static readonly Color HazardYellow = Hex("#FFC61A");
        public static readonly Color SignWhite    = Hex("#FFFFFF");

        // ---- Extra Subway-street accents ----
        public static readonly Color GrassGreen   = Hex("#5FC96A");  // trackside turf strips
        public static readonly Color TrainYellow  = Hex("#F7B32B");  // second train livery
        public static readonly Color GuardNavy    = Hex("#2B3A55");  // inspector uniform
        public static readonly Color DogBrown     = Hex("#8A5A33");

        public static Color Hex(string hex)
        {
            ColorUtility.TryParseHtmlString(hex, out var c);
            return c;
        }

        private static Shader _lit, _unlit;
        private static readonly Dictionary<int, Material> MatCache = new Dictionary<int, Material>(32);

        /// <summary>Lit shader that works under URP or BiRP (defensive: templates may differ).
        /// Also survives headless -nographics batchmode where Shader.Find can miss URP names.</summary>
        public static Shader LitShader()
        {
            if (_lit == null)
            {
                try
                {
                    var rp = GraphicsSettings.currentRenderPipeline;
                    if (rp != null)
                    {
                        _lit = Shader.Find("Universal Render Pipeline/Simple Lit");
                        if (_lit == null) _lit = Shader.Find("Universal Render Pipeline/Lit");
                        if (_lit == null) { var d = rp.defaultShader; if (d != null) _lit = d; }
                    }
                }
                catch (System.Exception e) { Debug.LogWarning("[VisualStyles] URP lit shader probe failed headlessly: " + e.Message); }
                if (_lit == null) _lit = Shader.Find("Legacy Shaders/Diffuse");
                if (_lit == null) _lit = Shader.Find("Sprites/Default");
                if (_lit == null) _lit = Shader.Find("UI/Default");
                if (_lit == null) Debug.LogWarning("[VisualStyles] No lit shader could be resolved headlessly.");
                else Debug.Log("[VisualStyles] LitShader resolved: " + _lit.name);
            }
            return _lit;
        }

        public static Shader UnlitShader()
        {
            if (_unlit == null)
            {
                try
                {
                    var rp = GraphicsSettings.currentRenderPipeline;
                    if (rp != null)
                    {
                        _unlit = Shader.Find("Universal Render Pipeline/Unlit");
                        if (_unlit == null) { var d = rp.defaultShader; if (d != null) _unlit = d; }
                    }
                }
                catch (System.Exception e) { Debug.LogWarning("[VisualStyles] URP unlit shader probe failed headlessly: " + e.Message); }
                if (_unlit == null) _unlit = Shader.Find("Unlit/Color");
                if (_unlit == null) _unlit = Shader.Find("Sprites/Default");
                if (_unlit == null) _unlit = Shader.Find("UI/Default");
                if (_unlit == null) Debug.LogWarning("[VisualStyles] No unlit shader could be resolved headlessly.");
                else Debug.Log("[VisualStyles] UnlitShader resolved: " + _unlit.name);
            }
            return _unlit;
        }

        /// <summary>Cached flat lit material for the given color.</summary>
        public static Material Lit(Color c)
        {
            int key = Hash(c, true);
            if (MatCache.TryGetValue(key, out var m)) return m;
            var shader = LitShader();
            if (shader == null) return null;
            m = new Material(shader);
            if (m.HasProperty("_BaseColor")) m.SetColor("_BaseColor", c);
            if (m.HasProperty("_Color")) m.SetColor("_Color", c);
            if (m.HasProperty("_Smoothness")) m.SetFloat("_Smoothness", 0.05f);
            if (m.HasProperty("_Glossiness")) m.SetFloat("_Glossiness", 0.05f);
            MatCache[key] = m;
            return m;
        }

        /// <summary>Cached unlit material (fog still applies via shader; used for UI-ish world colors and lamps).</summary>
        public static Material Unlit(Color c)
        {
            int key = Hash(c, false);
            if (MatCache.TryGetValue(key, out var m)) return m;
            var shader = UnlitShader();
            if (shader == null) return null;
            m = new Material(shader);
            if (m.HasProperty("_BaseColor")) m.SetColor("_BaseColor", c);
            if (m.HasProperty("_Color")) m.SetColor("_Color", c);
            MatCache[key] = m;
            return m;
        }

        private static int Hash(Color c, bool lit)
        {
            unchecked
            {
                int h = 17;
                h = h * 31 + Mathf.RoundToInt(c.r * 255);
                h = h * 31 + Mathf.RoundToInt(c.g * 255);
                h = h * 31 + Mathf.RoundToInt(c.b * 255);
                h = h * 31 + (lit ? 1 : 0);
                return h;
            }
        }

        /// <summary>Shared MeshRenderer creation helper for runtime-built primitives.
        /// NOTE: primitives created via GameObject.CreatePrimitive already carry a
        /// MeshRenderer — AddComponent on top of that returns null, so always
        /// GetComponent first.</summary>
        public static MeshRenderer AddMesh(GameObject go, Color color, bool unlit = false)
        {
            if (go == null) return null;
            var r = go.GetComponent<MeshRenderer>();
            if (r == null) r = go.AddComponent<MeshRenderer>();
            if (r == null) return null;
            var mat = unlit ? Unlit(color) : Lit(color);
            if (mat != null) r.sharedMaterial = mat;
            return r;
        }

        public static MeshFilter AddMeshFilter(GameObject go, PrimitiveType type)
        {
            var filter = go.GetComponent<MeshFilter>();
            if (filter == null) filter = go.AddComponent<MeshFilter>();
            if (filter == null) return filter;
            switch (type)
            {
                case PrimitiveType.Cube: filter.sharedMesh = CubeMesh; break;
                case PrimitiveType.Sphere: filter.sharedMesh = SphereMesh; break;
                case PrimitiveType.Capsule: filter.sharedMesh = CapsuleMesh; break;
                case PrimitiveType.Cylinder: filter.sharedMesh = CylinderMesh; break;
                case PrimitiveType.Quad: filter.sharedMesh = QuadMesh; break;
                case PrimitiveType.Plane: filter.sharedMesh = PlaneMesh; break;
            }
            return filter;
        }

        public static Mesh CubeMesh => GetBuiltin(PrimitiveType.Cube);
        public static Mesh SphereMesh => GetBuiltin(PrimitiveType.Sphere);
        public static Mesh CapsuleMesh => GetBuiltin(PrimitiveType.Capsule);
        public static Mesh CylinderMesh => GetBuiltin(PrimitiveType.Cylinder);
        public static Mesh QuadMesh => GetBuiltin(PrimitiveType.Quad);
        public static Mesh PlaneMesh => GetBuiltin(PrimitiveType.Plane);

        private static Mesh GetBuiltin(PrimitiveType t)
        {
            var go = GameObject.CreatePrimitive(t);
            var mesh = go.GetComponent<MeshFilter>().sharedMesh;
            Object.Destroy(go);
            return mesh;
        }
    }
}
