using UnityEngine;
using DummySurfer.Utilities;

namespace DummySurfer.UI
{
    /// <summary>
    /// Base for all screens: full-stretch rect, built from code once, toggled by UIManager.
    /// </summary>
    public abstract class UIScreen : MonoBehaviour
    {
        protected RectTransform Root;
        protected UIManager UI;

        public void Init(UIManager ui, RectTransform root)
        {
            UI = ui;
            Root = root;
            Build();
            HideInstant();
        }

        protected abstract void Build();

        public virtual void Show()
        {
            Root.gameObject.SetActive(true);
            OnShown();
        }

        public virtual void HideInstant()
        {
            Root.gameObject.SetActive(false);
        }

        protected virtual void OnShown() { }

        protected Text AddText(string content, int size, Color color, Vector2 position,
            Vector2 size, TextAnchor align = TextAnchor.MiddleCenter, bool bold = false)
            => UICanvasBuilder.Text(Root, "T_" + content, content, size, color, align,
                new Vector2(0.5f, 0.5f), position, size, bold);

        protected Button AddButton(string label, Vector2 position, Vector2 size, Color bg,
            Color fg, int fontSize = 46, UnityEngine.Events.UnityAction onClick = null)
            => UICanvasBuilder.Button(Root, label, position, size, bg, fg, fontSize, onClick);

        protected Image AddPanel(Vector2 position, Vector2 size, Color color)
            => UICanvasBuilder.Panel(Root, "Panel", new Vector2(0.5f, 0.5f), position, size, color);
    }
}
