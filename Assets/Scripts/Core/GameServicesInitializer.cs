using System;
using System.Threading.Tasks;
using UnityEngine;
using DummySurfer.Utilities;

namespace DummySurfer.Core
{
    /// <summary>
    /// Initializes Unity Gaming Services + anonymous Authentication (spec 2/7 IDENTITY).
    /// Runs in the background from boot; every network operation calls EnsureReadyAsync
    /// which retries with friendly error states (spec 10 ROOM FLOW: timeout, retry, errors).
    /// </summary>
    public static class GameServicesInitializer
    {
        public const string NotConfiguredHint =
            "Online play needs Unity Gaming Services: link the project in Edit > Project Settings > Services, " +
            "then enable Authentication and Relay in the Unity Dashboard (see docs/MULTIPLAYER_GUIDE.md).";

        public static bool ServicesInitialized { get; private set; }
        public static bool SignedIn { get; private set; }
        public static string LastError { get; private set; } = "";

        /// <summary>Fire-and-forget warmup from GameBootstrap. Safe to call multiple times.</summary>
        public static void Warmup()
        {
            _ = WarmupAsync();
        }

        private static async Task WarmupAsync()
        {
            try { await EnsureReadyAsync(); }
            catch { /* status already recorded */ }
        }

        /// <summary>Initializes UGS and signs in anonymously. Returns true when ready for Relay.</summary>
        public static async Task<bool> EnsureReadyAsync(int maxAttempts = 3)
        {
            if (SignedIn) return true;

            // 1) Initialize Unity Services
            if (!ServicesInitialized)
            {
                try
                {
                    await Unity.Services.Core.UnityServices.InitializeAsync()
                        .WithTimeout(20f, "Unity Services initialization");
                    ServicesInitialized = true;
                    LastError = "";
                }
                catch (Exception e)
                {
                    LastError = Friendly(e, "Services init");
                    Debug.LogWarning($"[Services] {LastError}");
                    return false;
                }
            }

            // 2) Anonymous sign-in with retries
            var auth = Unity.Services.Authentication.AuthenticationService.Instance;
            if (auth.IsSignedIn)
            {
                SignedIn = true;
                return true;
            }

            for (int attempt = 1; attempt <= maxAttempts; attempt++)
            {
                try
                {
                    await auth.SignInAnonymouslyAsync().WithTimeout(20f, "sign-in");
                    SignedIn = true;
                    LastError = "";
                    return true;
                }
                catch (Exception e)
                {
                    LastError = Friendly(e, $"sign-in (attempt {attempt}/{maxAttempts})");
                    Debug.LogWarning($"[Services] {LastError}");
                    if (attempt < maxAttempts)
                        await Task.Delay(600 * attempt);
                }
            }
            return SignedIn;
        }

        public static string Friendly(Exception e, string context)
        {
            string msg = e.Message;
            if (string.IsNullOrEmpty(msg)) msg = e.GetType().Name;
            if (msg.Contains("project") && (msg.Contains("not linked") || msg.Contains("invalid") || msg.Contains("401") || msg.Contains("403")))
                return NotConfiguredHint;
            return $"{context} failed: {msg}";
        }
    }
}
