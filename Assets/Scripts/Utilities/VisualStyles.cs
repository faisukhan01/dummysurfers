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
        // ---- UI palette ----
        public static readonly Color Background   = Hex("#12151C");
        public static readonly Color Surface      = Hex("#1C212B");
        public static readonly Color SurfaceHi    = Hex("#262D3A");
        public static readonly Color Primary      = Hex("#FF7A2F");  // signal orange
        public static readonly Color PrimaryDark  = Hex("#B4501A");
        public static readonly Color Accent       = Hex("#2FD5C8");  // teal
        public static readonly Color Danger       = Hex("#E5484D");
        public static readonly Color TextPrimary  = Hex("#F2F4F8");
        public static readonly Color TextMuted    = Hex("#9AA3B2");
        public static readonly Color Gold         = Hex("#FFC93C");

        // ---- World palette ----
        public static readonly Color SkyTop       = Hex("#141A28");
        public static readonly Color FogColor     = Hex("#111826");
        public static readonly Color Asphalt      = Hex("#232830");
        public static readonly Color RailMetal    = Hex("#6E7887");
        public static readonly Color Sleeper      = Hex("#1A1E26");
        public static readonly Color Concrete     = Hex("#39414F");
        public static readonly Color Skyline      = Hex("#161C2A");
        public static readonly Color SkylineFar   = Hex("#131826");
        public static readonly Color TrainRed     = Hex("#C4402F");
        public static readonly Color TrainBlue    = Hex("#2E6FA3");
        public static readonly Color HazardYellow = Hex("#F2C230");
        public static readonly Color SignWhite    = Hex("#D8DEE9");

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
                var rp = GraphicsSettings.currentRenderPipeline;
                if (rp != null)
                {
                    _lit = Shader.Find("Universal Render Pipeline/Simple Lit");
                    if (_lit == null) _lit = Shader.Find("Universal Render Pipeline/Lit");
                    if (_lit == null) _lit = rp.defaultShader;
                }
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
                var rp = GraphicsSettings.currentRenderPipeline;
                if (rp != null)
                {
                    _unlit = Shader.Find("Universal Render Pipeline/Unlit");
                    if (_unlit == null) _unlit = rp.defaultShader;
                }
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

        /// <summary>Shared MeshRenderer creation helper for runtime-built primitives.</summary>
        public static MeshRenderer AddMesh(GameObject go, Color color, bool unlit = false)
        {
            var r = go.AddComponent<MeshRenderer>();
            var mat = unlit ? Unlit(color) : Lit(color);
            if (mat != null) r.sharedMaterial = mat;
            // NOTE: renderer shadow flags intentionally skipped — setting them
            // can throw inside headless -nographics batchmode.
            return r;
        }

        public static MeshFilter AddMeshFilter(GameObject go, PrimitiveType type)
        {
            var filter = go.AddComponent<MeshFilter>();
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
