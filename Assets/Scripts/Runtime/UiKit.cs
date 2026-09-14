using UnityEngine;
using UnityEngine.Events;
using UnityEngine.UI;

namespace DummySurfer
{
    /// <summary>uGUI building blocks: point-anchored nodes, rounded panels, outlined text, buttons.</summary>
    public static class UiKit
    {
        public static RectTransform Node(Transform parent, string name, Vector2 anchor, Vector2 size, Vector2 pos)
        {
            var go = new GameObject(name, typeof(RectTransform));
            go.transform.SetParent(parent, false);
            var rt = (RectTransform)go.transform;
            rt.anchorMin = anchor;
            rt.anchorMax = anchor;
            rt.pivot = new Vector2(0.5f, 0.5f);
            rt.sizeDelta = size;
            rt.anchoredPosition = pos;
            return rt;
        }

        public static RectTransform Stretch(Transform parent, string name, Vector2 aMin, Vector2 aMax, Vector2 oMin, Vector2 oMax)
        {
            var go = new GameObject(name, typeof(RectTransform));
            go.transform.SetParent(parent, false);
            var rt = (RectTransform)go.transform;
            rt.anchorMin = aMin;
            rt.anchorMax = aMax;
            rt.offsetMin = oMin;
            rt.offsetMax = oMax;
            return rt;
        }

        public static Image Img(RectTransform rt, Sprite sp, Color c, Image.Type type = Image.Type.Simple)
        {
            var img = rt.gameObject.AddComponent<Image>();
            img.sprite = sp;
            img.color = c;
            img.type = type;
            if (type == Image.Type.Sliced) { img.pixelsPerUnitMultiplier = 1f; }
            img.raycastTarget = false;
            return img;
        }

        public static Image FillImg(RectTransform rt, Sprite sp, Color c)
        {
            var img = Img(rt, sp, c, Image.Type.Filled);
            img.fillMethod = Image.FillMethod.Horizontal;
            img.fillOrigin = (int)Image.OriginHorizontal.Left;
            img.fillAmount = 0f;
            return img;
        }

        public static Text Txt(Transform parent, string s, int size, Color col, TextAnchor align,
            int outlineW = 0, Color? outlineCol = null, FontStyle style = FontStyle.Bold, Vector2? pos = null, Vector2? size = null)
        {
            var go = new GameObject("t_" + s, typeof(RectTransform));
            go.transform.SetParent(parent, false);
            var rt = (RectTransform)go.transform;
            if (pos != null) { rt.anchorMin = rt.anchorMax = new Vector2(0.5f, 0.5f); rt.anchoredPosition = pos.Value; }
            if (size != null) rt.sizeDelta = size.Value;
            var t = go.AddComponent<Text>();
            try { t.font = Fx.Font != null ? Fx.Font : Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf"); } catch { }
            t.text = s;
            t.fontSize = size;
            t.color = col;
            t.alignment = align;
            t.fontStyle = style;
            t.supportRichText = true;
            t.horizontalOverflow = HorizontalWrapMode.Overflow;
            t.verticalOverflow = VerticalWrapMode.Overflow;
            t.raycastTarget = false;
            if (outlineW > 0)
            {
                var oc = outlineCol ?? GameManager.C.navy;
                for (int i = 0; i < outlineW; i += 2)
                {
                    var o = go.AddComponent<Outline>();
                    o.effectColor = oc;
                    o.effectDistance = new Vector2(Mathf.Min(2, outlineW - i), -Mathf.Min(2, outlineW - i));
                }
                var o2 = go.AddComponent<Outline>();
                o2.effectColor = oc;
                o2.effectDistance = new Vector2(-Mathf.Min(2, outlineW), Mathf.Min(2, outlineW));
            }
            return t;
        }

        public static Button Btn(RectTransform rt, Color col, string label, int labelSize, UnityAction onClick,
            Color? labelCol = null, int outline = 6)
        {
            var img = Img(rt, Fx.SprPanel(), col, Image.Type.Sliced);
            img.raycastTarget = true;
            if (!string.IsNullOrEmpty(label))
                Txt(rt, label, labelSize, labelCol ?? GameManager.C.white, TextAnchor.MiddleCenter, outline, GameManager.C.navy);
            var b = rt.gameObject.AddComponent<Button>();
            b.targetGraphic = img;
            if (onClick != null) b.onClick.AddListener(onClick);
            var cimg = img;
            var cb = b;
            // subtle press feedback
            cb.transition = Selectable.Transition.ColorTint;
            var colors = cb.colors;
            colors.pressedColor = new Color(0.8f, 0.8f, 0.8f, 1f);
            colors.fadeDuration = 0.06f;
            cb.colors = colors;
            return b;
        }

        public static Button TapCatcher(Transform parent, UnityAction onClick)
        {
            var rt = Stretch(parent, "tapCatcher", Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);
            var img = rt.gameObject.AddComponent<Image>();
            img.color = new Color(0, 0, 0, 0);
            img.raycastTarget = true;
            var b = rt.gameObject.AddComponent<Button>();
            b.targetGraphic = img;
            b.onClick.AddListener(onClick);
            return b;
        }

        /// <summary>Icon button: colored rounded square with a white glyph.</summary>
        public static Button IconButton(Transform parent, string glyph, Color col, Vector2 anchor, Vector2 size, Vector2 pos, UnityAction onClick, float glyphScale = 0.5f)
        {
            var rt = Node(parent, "ib_" + glyph, anchor, size, pos);
            var img = Img(rt, Fx.SprPanel(), col, Image.Type.Sliced);
            img.raycastTarget = true;
            var g = Node(rt, "g", new Vector2(0.5f, 0.5f), size * glyphScale, Vector2.zero);
            Img(g, Fx.SprIcon(glyph), Color.white);
            var b = rt.gameObject.AddComponent<Button>();
            b.targetGraphic = img;
            b.onClick.AddListener(onClick);
            return b;
        }

        /// <summary>Coins pill: navy rounded + coin icon + count.</summary>
        public static RectTransform CoinPill(Transform parent, out Text count, Vector2 anchor, Vector2 pos, Vector2 size)
        {
            var rt = Node(parent, "coinPill", anchor, size, pos);
            Img(rt, Fx.SprPanel(), GameManager.C.navy, Image.Type.Sliced);
            var ic = Node(rt, "ic", new Vector2(0.5f, 0.5f), new Vector2(size.y * 0.62f, size.y * 0.62f), new Vector2(-size.x * 0.5f + size.y * 0.55f, 0));
            Img(ic, Fx.SprCoin(), Color.white);
            count = Txt(rt, "0", Mathf.RoundToInt(size.y * 0.5f), Color.white, TextAnchor.MiddleLeft, 4, GameManager.C.navy2);
            var crt = (RectTransform)count.transform;
            crt.anchorMin = crt.anchorMax = new Vector2(0.5f, 0.5f);
            crt.sizeDelta = new Vector2(size.x - size.y * 0.9f, size.y);
            crt.anchoredPosition = new Vector2(size.y * 0.16f, 0);
            return rt;
        }

        public static RectTransform ProgressBar(Transform parent, Vector2 anchor, Vector2 pos, Vector2 size, Color track, Color fillCol, out Image fill, out Text label)
        {
            var rt = Node(parent, "pbar", anchor, size, pos);
            Img(rt, Fx.SprPanel(), track, Image.Type.Sliced);
            var frt = Node(rt, "fill", new Vector2(0f, 0.5f), size, Vector2.zero);
            frt.anchorMin = new Vector2(0f, 0.5f);
            frt.anchorMax = new Vector2(0f, 0.5f);
            frt.pivot = new Vector2(0f, 0.5f);
            frt.anchoredPosition = new Vector2(6f, 0f);
            frt.sizeDelta = new Vector2(size.x - 12f, size.y - 10f);
            fill = FillImg(frt, Fx.SprPanel(), fillCol);
            fill.type = Image.Type.Sliced; // sliced can't fill; use filled below
            fill.type = Image.Type.Filled;
            fill.fillMethod = Image.FillMethod.Horizontal;
            label = Txt(rt, "0", Mathf.RoundToInt(size.y * 0.55f), Color.white, TextAnchor.MiddleCenter, 3, GameManager.C.navy2);
            var lrt = (RectTransform)label.transform;
            lrt.anchorMin = lrt.anchorMax = new Vector2(0.5f, 0.5f);
            lrt.sizeDelta = size;
            lrt.anchoredPosition = Vector2.zero;
            return rt;
        }
    }

    /// <summary>Keeps a node inside the device safe area (notches / home indicator).</summary>
    public class SafeArea : MonoBehaviour
    {
        RectTransform rt;
        Vector2 aMin, aMax;
        Vector2 oMin, oMax;
        Rect last;

        public static void Apply(GameObject go, Vector2 anchorMin, Vector2 anchorMax, Vector2 offMin, Vector2 offMax)
        {
            var sa = go.AddComponent<SafeArea>();
            sa.rt = (RectTransform)go.transform;
            sa.aMin = anchorMin; sa.aMax = anchorMax; sa.oMin = offMin; sa.oMax = offMax;
            sa.Refresh();
        }

        void Update()
        {
            if (Screen.safeArea != last) Refresh();
        }

        void Refresh()
        {
            if (rt == null) return;
            last = Screen.safeArea;
            var sa = Screen.safeArea;
            float w = Screen.width, h = Screen.height;
            Vector2 lo = new Vector2(sa.x / w, sa.y / h);
            Vector2 hi = new Vector2((sa.x + sa.width) / w, (sa.y + sa.height) / h);
            rt.anchorMin = new Vector2(aMin.x + (aMax.x - aMin.x) * lo.x, aMin.y + (aMax.y - aMin.y) * lo.y);
            rt.anchorMax = new Vector2(aMin.x + (aMax.x - aMin.x) * hi.x, aMin.y + (aMax.y - aMin.y) * hi.y);
            rt.offsetMin = oMin;
            rt.offsetMax = oMax;
        }
    }
}
