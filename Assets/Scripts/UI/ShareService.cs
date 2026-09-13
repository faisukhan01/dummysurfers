using UnityEngine;
using DummySurfer.Multiplayer;
using DummySurfer.Utilities;

namespace DummySurfer.UI
{
    /// <summary>Android native share sheet for the room code, with clipboard fallback everywhere.</summary>
    public static class ShareService
    {
        public static void ShareRoomCode(string code)
        {
            string message = $"Join my Dummy Surfer room!\nRoom code: {code}";
#if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                using (var intent = new AndroidJavaObject("android.content.Intent"))
                {
                    intent.Call<AndroidJavaObject>("setAction", "android.content.Intent.ACTION_SEND");
                    intent.Call<AndroidJavaObject>("setType", "text/plain");
                    intent.Call<AndroidJavaObject>("putExtra", "android.content.Intent.EXTRA_TEXT", message);

                    using (var player = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
                    {
                        var activity = player.GetStatic<AndroidJavaObject>("currentActivity");
                        using (var chooser = intent.Call<AndroidJavaObject>("createChooser", message, "Share room code"))
                        {
                            activity.Call("startActivity", chooser);
                        }
                    }
                }
            }
            catch (System.Exception)
            {
                CopyFallback(code);
            }
#else
            CopyFallback(code);
#endif
        }

        private static void CopyFallback(string code)
        {
            GUIUtility.systemCopyBuffer = code;
            GameEvents.PublishToast("Invite copied to clipboard!");
        }
    }
}
