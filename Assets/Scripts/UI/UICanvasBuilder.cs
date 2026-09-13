using UnityEngine;
using UnityEngine.Events;
using UnityEngine.UI;

namespace DummySurfer.UI
{
    /// <summary>
    /// Builds premium flat mobile UI from code (large touch targets ≥110px, strong hierarchy,
    /// no generic gradient clutter — spec 4.2). Legacy uGUI Text is used deliberately:
    /// zero asset dependencies, works on a fresh clone with no TMP essentials import.
    /// </summary>
    public static class UICanvasBuilder
    {
        private static Font _font;
        public static Font DefaultFont()
        {
            if (_font != null) return _font;
            try { _font = Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf"); }
            catch { _font = Resources.GetBuiltinResource<Font>("Arial.ttf"); }
            return _font;
        }

        public static RectTransform Rect(Transform parent, string name,
            Vector2 anchorMin, Vector2 anchorMax, Vector2 offsetMin, Vector2 offsetMax)
        {
            var go = new GameObject(name, typeof(RectTransform));
            var rt = (RectTransform)go.transform;
            rt.SetParent(parent, false);
            rt.anchorMin = anchorMin;
            rt.anchorMax = anchorMax;
            rt.offsetMin = offsetMin;
            rt.offsetMax = offsetMax;
            return rt;
        }

        public static RectTransform Centered(Transform parent, string name, Vector2 anchor,
            Vector2 position, Vector2 size)
        {
            var rt = Rect(parent, name, anchor, anchor, Vector2.zero, Vector2.zero);
            rt.anchoredPosition = position;
            rt.sizeDelta = size;
            return rt;
        }

        public static Image Panel(Transform parent, string name, Vector2 anchor,
            Vector2 position, Vector2 size, Color color)
        {
            var rt = Centered(parent, name, anchor, position, size);
            var img = rt.gameObject.AddComponent<Image>();
            img.color = color;
            return img;
        }

        public static Image StretchPanel(Transform parent, string name, Color color)
        {
            var rt = Rect(parent, name, Vector2.zero, Vector2.one, Vector2.zero, Vector2.zero);
            var img = rt.gameObject.AddComponent<Image>();
            img.color = color;
            return img;
        }

        public static Text Text(Transform parent, string name, string content, int size, Color color,
            TextAnchor align = TextAnchor.MiddleCenter, Vector2 anchor = default,
            Vector2 position = default, Vector2 dims = default, bool bold = false)
        {
            var rt = Centered(parent, name, anchor, position, dims);
            var t = rt.gameObject.AddComponent<Text>();
            t.font = DefaultFont();
            t.text = content;
            t.fontSize = size;
            t.color = color;
            t.alignment = align;
            t.fontStyle = bold ? FontStyle.Bold : FontStyle.Normal;
            t.horizontalOverflow = HorizontalWrapMode.Overflow;
            t.verticalOverflow = VerticalWrapMode.Overflow;
            t.raycastTarget = false;
            return t;
        }

        public static Button Button(Transform parent, string label, Vector2 position, Vector2 size,
            Color bg, Color fg, int fontSize = 46, UnityAction onClick = null, bool bold = true)
        {
            var img = Panel(parent, "Btn_" + label, new Vector2(0.5f, 0.5f), position, size, bg);
            var btn = img.gameObject.AddComponent<Button>();
            var colors = btn.colors;
            colors.pressedColor = new Color(bg.r * 0.82f, bg.g * 0.82f, bg.b * 0.82f);
            colors.disabledColor = new Color(bg.r, bg.g, bg.b, 0.4f);
            btn.colors = colors;
            btn.onClick.AddListener(() => { if (Audio.AudioManager.Instance != null) Audio.AudioManager.Instance.PlaySfx(Audio.SfxId.UiClick, 0.7f); onClick?.Invoke(); });

            var t = Text(img.transform, "Label", label, fontSize, fg, TextAnchor.MiddleCenter,
                new Vector2(0.5f, 0.5f), Vector2.zero, new Vector2(-24f, -8f), bold);
            t.horizontalOverflow = HorizontalWrapMode.Wrap;
            return btn;
        }

        public static Slider Slider(Transform parent, Vector2 position, Vector2 size, float value)
        {
            var rt = Centered(parent, "Slider", new Vector2(0.5f, 0.5f), position, size);
            var slider = rt.gameObject.AddComponent<Slider>();

            var bg = Panel(rt, "BG", new Vector2(0.5f, 0.5f), Vector2.zero, size, VisualStyles.SurfaceHi);
            bg.rectTransform.anchorMin = new Vector2(0f, 0.35f);
            bg.rectTransform.anchorMax = new Vector2(1f, 0.65f);
            bg.rectTransform.anchoredPosition = Vector2.zero;
            bg.rectTransform.sizeDelta = Vector2.zero;

            var fillArea = Rect(rt, "FillArea", new Vector2(0f, 0.25f), new Vector2(1f, 0.75f), Vector2.zero, Vector2.zero);
            var fill = Panel(fillArea, "Fill", new Vector2(0f, 0f), Vector2.zero, Vector2.zero, VisualStyles.Primary);
            fill.rectTransform.anchorMin = Vector2.zero;
            fill.rectTransform.anchorMax = new Vector2(value, 1f);
            fill.rectTransform.anchoredPosition = Vector2.zero;
            fill.rectTransform.sizeDelta = Vector2.zero;

            var handleArea = Rect(rt, "HandleArea", new Vector2(0f, 0f), new Vector2(1f, 1f), Vector2.zero, Vector2.zero);
            var handle = Panel(handleArea, "Handle", new Vector2(0f, 0f), Vector2.zero, new Vector2(28f, 0f), VisualStyles.TextPrimary);
            handle.rectTransform.sizeDelta = new Vector2(28f, 0f);

            slider.fillRect = fill.rectTransform;
            slider.handleRect = handle.rectTransform;
            slider.targetGraphic = handle;
            slider.direction = Slider.Direction.LeftToRight;
            slider.value = value;
            return slider;
        }

        public static Toggle Toggle(Transform parent, string label, Vector2 position, Vector2 size,
            bool value, UnityAction<bool> onChanged)
        {
            var rt = Centered(parent, "Toggle_" + label, new Vector2(0.5f, 0.5f), position, size);
            var bg = rt.gameObject.AddComponent<Image>();
            bg.color = VisualStyles.SurfaceHi;

            var check = Panel(rt, "Check", new Vector2(0f, 0.5f), new Vector2(28f, 0f), new Vector2(36f, 36f), VisualStyles.Accent);
            var toggle = rt.gameObject.AddComponent<Toggle>();
            toggle.targetGraphic = bg;
            toggle.graphic = check;
            toggle.isOn = value;
            toggle.onValueChanged.AddListener(onChanged);

            Text(rt, "Label", label, 40, VisualStyles.TextPrimary, TextAnchor.MiddleLeft,
                new Vector2(1f, 0.5f), new Vector2(36f, 0f), new Vector2(size.x - 110f, size.y), true);
            return toggle;
        }

        public static InputField InputField(Transform parent, string placeholder, Vector2 position,
            Vector2 size, int charLimit, UnityAction<string> onChanged)
        {
            var img = Panel(parent, "Input", new Vector2(0.5f, 0.5f), position, size, VisualStyles.SurfaceHi);
            var field = img.gameObject.AddComponent<InputField>();

            var textRt = Rect(img.transform, "Text", new Vector2(0f, 0f), new Vector2(1f, 1f),
                new Vector2(20f, 6f), new Vector2(-20f, -6f));
            var text = textRt.gameObject.AddComponent<Text>();
            text.font = DefaultFont();
            text.fontSize = 54;
            text.color = VisualStyles.TextPrimary;
            text.alignment = TextAnchor.MiddleCenter;
            text.supportRichText = false;

            var phRt = Rect(img.transform, "Placeholder", new Vector2(0f, 0f), new Vector2(1f, 1f),
                new Vector2(20f, 6f), new Vector2(-20f, -6f));
            var ph = phRt.gameObject.AddComponent<Text>();
            ph.font = DefaultFont();
            ph.fontSize = 40;
            ph.color = VisualStyles.TextMuted;
            ph.alignment = TextAnchor.MiddleCenter;
            ph.text = placeholder;
            ph.fontStyle = FontStyle.Italic;

            field.textComponent = text;
            field.placeholder = ph;
            field.characterLimit = charLimit;
            field.contentType = InputField.ContentType.UpperCase;
            field.onValueChanged.AddListener(onChanged);
            return field;
        }
    }
}
