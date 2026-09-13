using System;
using UnityEngine;
using UnityEngine.InputSystem;

namespace DummySurfer.Player
{
    /// <summary>
    /// Touch swipe + keyboard/mouse input on the Unity Input System (spec 2 INPUT),
    /// no .inputactions asset required. Fires at most once per gesture; the swipe origin
    /// re-arms so chained swipes in one continuous touch are supported.
    /// Keyboard mapping: A/←, D/→ lanes · W/↑/Space jump · S/↓ slide · Esc/P pause.
    /// </summary>
    public sealed class SwipeInputReader : MonoBehaviour
    {
        public event Action<int> SwipedHorizontal;   // -1 left, +1 right
        public event Action SwipedUp;
        public event Action SwipedDown;
        public event Action PausePressed;

        private Vector2 _origin;
        private float _pressTime;
        private bool _pressing;
        private bool _gestureFired;

        private float Threshold => DummySurfer.Data.GameConfig.Runtime.swipeThresholdPx;
        private float MaxDuration => DummySurfer.Data.GameConfig.Runtime.swipeMaxDuration;

        private void Update()
        {
            PollKeyboard();
            PollTouch();
        }

        private void PollKeyboard()
        {
            var kb = Keyboard.current;
            if (kb == null) return;

            if (kb.leftArrowKey.wasPressedThisFrame || kb.aKey.wasPressedThisFrame) SwipedHorizontal?.Invoke(-1);
            if (kb.rightArrowKey.wasPressedThisFrame || kb.dKey.wasPressedThisFrame) SwipedHorizontal?.Invoke(1);
            if (kb.upArrowKey.wasPressedThisFrame || kb.wKey.wasPressedThisFrame || kb.spaceKey.wasPressedThisFrame) SwipedUp?.Invoke();
            if (kb.downArrowKey.wasPressedThisFrame || kb.sKey.wasPressedThisFrame) SwipedDown?.Invoke();
            if (kb.escapeKey.wasPressedThisFrame || kb.pKey.wasPressedThisFrame) PausePressed?.Invoke();
        }

        private void PollTouch()
        {
            bool pressed = false;
            Vector2 pos = default;

            var touch = Touchscreen.current?.primaryTouch;
            if (touch != null && touch.press.isPressed)
            {
                pressed = true;
                pos = touch.position.ReadValue();
            }
            else
            {
                var mouse = Mouse.current;
                if (mouse != null && mouse.leftButton.isPressed)
                {
                    pressed = true;
                    pos = mouse.position.ReadValue();
                }
            }

            if (pressed)
            {
                if (!_pressing)
                {
                    _pressing = true;
                    _gestureFired = false;
                    _origin = pos;
                    _pressTime = Time.unscaledTime;
                }
                else if (!_gestureFired)
                {
                    Vector2 delta = pos - _origin;
                    if (delta.magnitude >= Threshold && Time.unscaledTime - _pressTime <= MaxDuration)
                    {
                        _gestureFired = true;
                        FireSwipe(delta);
                    }
                    else if (Time.unscaledTime - _pressTime > MaxDuration)
                    {
                        // stale press: re-arm so a later drag still works
                        _origin = pos;
                        _pressTime = Time.unscaledTime;
                    }
                }
                else if (Vector2.Distance(pos, _origin) >= Threshold * 1.6f)
                {
                    // allow chained swipes after the gesture fired
                    _origin = pos;
                    _pressTime = Time.unscaledTime;
                    _gestureFired = false;
                }
            }
            else
            {
                _pressing = false;
                _gestureFired = false;
            }
        }

        private void FireSwipe(Vector2 delta)
        {
            if (Mathf.Abs(delta.x) >= Mathf.Abs(delta.y)) SwipedHorizontal?.Invoke(delta.x > 0 ? 1 : -1);
            else if (delta.y > 0) SwipedUp?.Invoke();
            else SwipedDown?.Invoke();
        }
    }
}
