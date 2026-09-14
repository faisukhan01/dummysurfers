using UnityEngine;
using UnityEngine.EventSystems;

namespace DummySurfer
{
    /// <summary>Touch / mouse / keyboard input: swipes steer, tap jumps, double-tap hoverboard.</summary>
    public class InputManager : MonoBehaviour
    {
        public static InputManager I;

        Vector2 start;
        float startT;
        bool swiped;
        float lastTap = -99f;

        public void Poke() { }

        void Awake() { I = this; }

        bool OverUi(int fingerId)
        {
            return EventSystem.current != null && EventSystem.current.IsPointerOverGameObject(fingerId);
        }

        void Update()
        {
            var g = GameManager.I;
            if (g == null) return;

            if (g.st == GameManager.St.Menu)
            {
                if (TapAny(-999)) GameRoot.I.FromMenuTap();
                return;
            }
            if (g.st == GameManager.St.High)
            {
                if (TapAny(-999)) { Fx.Play("click"); g.SetState(GameManager.St.Results); }
                return;
            }
            if (g.st != GameManager.St.Run) { return; }

            // ---- touches
            for (int i = 0; i < Input.touchCount; i++)
            {
                Touch t = Input.touches[i];
                if (t.phase == TouchPhase.Began) { start = t.position; startT = Time.unscaledTime; swiped = false; }
                else if (t.phase == TouchPhase.Moved || t.phase == TouchPhase.Stationary)
                {
                    if (swiped) continue;
                    Vector2 d = t.position - start;
                    if (d.magnitude < 52f) continue;
                    swiped = true;
                    if (OverUi(t.fingerId)) continue;
                    if (Mathf.Abs(d.x) > Mathf.Abs(d.y)) PlayerController.I.OnLane(d.x > 0 ? 1 : -1);
                    else if (d.y > 0) PlayerController.I.OnJump();
                    else PlayerController.I.OnRoll();
                }
                else if (t.phase == TouchPhase.Ended || t.phase == TouchPhase.Canceled)
                {
                    float dur = Time.unscaledTime - startT;
                    if (!swiped && dur < 0.26f && !OverUi(t.fingerId))
                    {
                        if (Time.unscaledTime - lastTap < 0.28f)
                        {
                            PlayerController.I.ActivateBoard();
                            lastTap = -99f;
                        }
                        else
                        {
                            PlayerController.I.OnJump();
                            lastTap = Time.unscaledTime;
                        }
                    }
                    swiped = false;
                }
            }

            // ---- mouse (editor / testing)
            if (Input.GetMouseButtonDown(0)) { start = Input.mousePosition; startT = Time.unscaledTime; swiped = false; }
            if (Input.GetMouseButton(0) && !swiped)
            {
                Vector2 d = (Vector2)Input.mousePosition - start;
                if (d.magnitude >= 52f)
                {
                    swiped = true;
                    if (!OverUi(-1))
                    {
                        if (Mathf.Abs(d.x) > Mathf.Abs(d.y)) PlayerController.I.OnLane(d.x > 0 ? 1 : -1);
                        else if (d.y > 0) PlayerController.I.OnJump();
                        else PlayerController.I.OnRoll();
                    }
                }
            }
            if (Input.GetMouseButtonUp(0))
            {
                float dur = Time.unscaledTime - startT;
                if (!swiped && dur < 0.26f && !OverUi(-1))
                {
                    if (Time.unscaledTime - lastTap < 0.28f) { PlayerController.I.ActivateBoard(); lastTap = -99f; }
                    else { PlayerController.I.OnJump(); lastTap = Time.unscaledTime; }
                }
                swiped = false;
            }

            // ---- keyboard
            if (Input.GetKeyDown(KeyCode.LeftArrow) || Input.GetKeyDown(KeyCode.A)) PlayerController.I.OnLane(-1);
            if (Input.GetKeyDown(KeyCode.RightArrow) || Input.GetKeyDown(KeyCode.D)) PlayerController.I.OnLane(1);
            if (Input.GetKeyDown(KeyCode.UpArrow) || Input.GetKeyDown(KeyCode.W) || Input.GetKeyDown(KeyCode.Space)) PlayerController.I.OnJump();
            if (Input.GetKeyDown(KeyCode.DownArrow) || Input.GetKeyDown(KeyCode.S)) PlayerController.I.OnRoll();
            if (Input.GetKeyDown(KeyCode.E)) PlayerController.I.ActivateBoard();
            if (Input.GetKeyDown(KeyCode.Q)) PlayerController.I.HeadStart();
        }

        bool TapAny(int ignore)
        {
            for (int i = 0; i < Input.touchCount; i++)
            {
                Touch t = Input.touches[i];
                if (t.phase == TouchPhase.Ended && (t.position - start).magnitude < 60f) return true;
                if (t.phase == TouchPhase.Began) { start = t.position; }
            }
            if (Input.GetMouseButtonDown(0)) return true;
            return false;
        }
    }
}
