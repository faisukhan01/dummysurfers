using System;
using System.Collections;
using UnityEngine;

namespace DummySurfer
{
    /// <summary>Central game state: score / coins / missions / powerups / persistence.</summary>
    public class GameManager : MonoBehaviour
    {
        public enum St { Splash, Loading, Menu, Run, Pause, Dying, High, Results, Missions }

        public static GameManager I;
        public St st = St.Splash;
        public St stBeforeMissions = St.Menu;

        public event Action<St> OnState;

        // ---- run state
        public float dist;
        public int runScore, runCoins;
        public int runJumps;
        public float tMagnet, tJet, tX2, tBoard, tInv, tStumble;
        public bool newBest;

        // ---- persistent
        public int bank, best;
        public int multBase = 1;
        public int boardCount = 2, boostCount = 1;
        public int setIdx;
        public int lifeCoins, lifeJumps;
        public int snapCoins, snapJumps, snapBest;
        public int claimedBits;      // per-mission claimed flags
        public int bestSinceSnap;
        public int lettersOn;
        public int wordIdx;
        public bool sound = true;
        public bool adUsedScore, rewardUsed;

        public static readonly string[] Words = { "STARDUST", "HOVERBOARD", "MAGNETIC", "JETPACKS" };

        // ---- tuning
        public float Speed
        {
            get { return Mathf.Min(11f + dist * 0.011f, 27f); }
        }
        public int MultTotal { get { return multBase * (tX2 > 0 ? 2 : 1); } }
        public float ScoreF { get { return runScore; } }

        public int CoinsTarget { get { return new[] { 20, 50, 100, 180, 300 }[Mathf.Min(setIdx, 4)] * (setIdx >= 5 ? setIdx - 3 : 1); } }
        public int JumpsTarget { get { return new[] { 5, 10, 18, 28, 45 }[Mathf.Min(setIdx, 4)] * (setIdx >= 5 ? setIdx - 3 : 1); } }
        public int ScoreTarget { get { return new[] { 500, 1500, 3000, 5000, 8000 }[Mathf.Min(setIdx, 4)] * (setIdx >= 5 ? setIdx - 3 : 1); } }

        public int MissionProgress(int i)
        {
            if (i == 0) return lifeCoins - snapCoins;
            if (i == 1) return bestSinceSnap;
            return lifeJumps - snapJumps;
        }
        public int MissionTarget(int i) { return i == 0 ? CoinsTarget : (i == 1 ? ScoreTarget : JumpsTarget); }
        public bool MissionClaimed(int i) { return (claimedBits & (1 << i)) != 0; }
        public int CompleteNowCost { get { return 600 + 400 * setIdx; } }

        float scaleBackAt;
        const string PKEY = "ds_";

        void Awake()
        {
            I = this;
            Load();
            Application.targetFrameRate = 60;
            QualitySettings.vSyncCount = 0;
            Screen.sleepTimeout = SleepTimeout.NeverSleep;
        }

        public void SetState(St s)
        {
            st = s;
            Time.timeScale = s == St.Pause ? 0f : 1f;
            if (OnState != null) try { OnState(s); } catch { }
        }

        // ============================================== RUN LIFECYCLE
        public bool runJustStarted;

        public void StartRun()
        {
            dist = 0; runScore = 0; runCoins = 0; runJumps = 0;
            tMagnet = tJet = tX2 = tBoard = tInv = tStumble = 0;
            newBest = false;
            adUsedScore = false; rewardUsed = false;
            runJustStarted = true;
            SetState(St.Run);
        }

        public void AddCoin()
        {
            runCoins++; lifeCoins++;
            CheckLetters();
            if (!MissionClaimed(0) && MissionProgress(0) >= CoinsTarget) ClaimMission(0);
        }

        public void AddJump()
        {
            runJumps++; lifeJumps++;
            if (!MissionClaimed(2) && MissionProgress(2) >= JumpsTarget) ClaimMission(2);
        }

        void Update()
        {
            if (st != St.Run) { if (Time.timeScale < 1f && st != St.Pause && st != St.Dying) Time.timeScale = 1f; return; }
            float dt = Time.deltaTime;
            dist += PlayerController.I != null ? PlayerController.I.EffSpeed * dt : 12f * dt;
            runScore = Mathf.FloorToInt(runScore + Speed * multBase * (tX2 > 0 ? 2f : 1f) * dt);
            if (tMagnet > 0) tMagnet -= dt;
            if (tJet > 0) tJet -= dt;
            if (tX2 > 0) tX2 -= dt;
            if (tBoard > 0) tBoard -= dt;
            if (tInv > 0) tInv -= dt;
            if (tStumble > 0) tStumble -= dt;
        }

        public void Die()
        {
            if (st != St.Run) return;
            bestSinceSnap = Mathf.Max(bestSinceSnap, runScore);
            if (!MissionClaimed(1) && bestSinceSnap >= ScoreTarget) ClaimMission(1);
            SetState(St.Dying);
            StartCoroutine(DeathFlow());
        }

        IEnumerator DeathFlow()
        {
            Time.timeScale = 0.35f;
            yield return new WaitForSecondsRealtime(1.15f);
            Time.timeScale = 1f;
            Save();
            if (runScore > best) { best = runScore; newBest = true; SetState(St.High); }
            else SetState(St.Results);
        }

        public void ClaimMission(int i)
        {
            if (MissionClaimed(i)) return;
            claimedBits |= (1 << i);
            UiScreens.I.Toast("MISSION COMPLETE", C.green);
            Fx.Play("power");
            if (claimedBits == 7)
            {
                setIdx++;
                multBase = Mathf.Min(1 + setIdx, 5);
                bank += 500 + 250 * setIdx;
                claimedBits = 0;
                snapCoins = lifeCoins; snapJumps = lifeJumps; bestSinceSnap = 0;
                UiScreens.I.Toast("MULTIPLIER x" + multBase, C.gold);
            }
            Save();
        }

        public bool CompleteNow(int i)
        {
            if (MissionClaimed(i) || bank < CompleteNowCost) return false;
            bank -= CompleteNowCost;
            if (i == 0) lifeCoins = snapCoins + CoinsTarget;
            if (i == 1) bestSinceSnap = ScoreTarget;
            if (i == 2) lifeJumps = snapJumps + JumpsTarget;
            ClaimMission(i);
            return true;
        }

        void CheckLetters()
        {
            int wordLen = Words[wordIdx % Words.Length].Length;
            if (lettersOn >= wordLen) return;
            int need = 22 + lettersOn * 6;
            if (lifeCoins - snapCoins >= 0 && lifeCoins % need == 0)
            {
                lettersOn++;
                if (UiScreens.I != null) UiScreens.I.OnLetter();
                if (lettersOn >= wordLen)
                {
                    bank += 500;
                    UiScreens.I.Toast("WORD DONE  +500", C.gold);
                    lettersOn = 0;
                    wordIdx = (wordIdx + 1) % Words.Length;
                }
                Save();
            }
        }

        public void BankAdd(int n) { bank += n; Save(); }

        // ============================================== PERSISTENCE
        public void Save()
        {
            PlayerPrefs.SetInt(PKEY + "bank", bank);
            PlayerPrefs.SetInt(PKEY + "best", best);
            PlayerPrefs.SetInt(PKEY + "mult", multBase);
            PlayerPrefs.SetInt(PKEY + "board", boardCount);
            PlayerPrefs.SetInt(PKEY + "boost", boostCount);
            PlayerPrefs.SetInt(PKEY + "set", setIdx);
            PlayerPrefs.SetInt(PKEY + "lc", lifeCoins);
            PlayerPrefs.SetInt(PKEY + "lj", lifeJumps);
            PlayerPrefs.SetInt(PKEY + "sc", snapCoins);
            PlayerPrefs.SetInt(PKEY + "sj", snapJumps);
            PlayerPrefs.SetInt(PKEY + "sb", bestSinceSnap);
            PlayerPrefs.SetInt(PKEY + "cb", claimedBits);
            PlayerPrefs.SetInt(PKEY + "lt", lettersOn);
            PlayerPrefs.SetInt(PKEY + "wi", wordIdx);
            PlayerPrefs.SetInt(PKEY + "snd", sound ? 1 : 0);
            PlayerPrefs.Save();
        }

        void Load()
        {
            bank = PlayerPrefs.GetInt(PKEY + "bank", 0);
            best = PlayerPrefs.GetInt(PKEY + "best", 0);
            multBase = PlayerPrefs.GetInt(PKEY + "mult", 1);
            boardCount = PlayerPrefs.GetInt(PKEY + "board", 2);
            boostCount = PlayerPrefs.GetInt(PKEY + "boost", 1);
            setIdx = PlayerPrefs.GetInt(PKEY + "set", 0);
            lifeCoins = PlayerPrefs.GetInt(PKEY + "lc", 0);
            lifeJumps = PlayerPrefs.GetInt(PKEY + "lj", 0);
            snapCoins = PlayerPrefs.GetInt(PKEY + "sc", 0);
            snapJumps = PlayerPrefs.GetInt(PKEY + "sj", 0);
            bestSinceSnap = PlayerPrefs.GetInt(PKEY + "sb", 0);
            claimedBits = PlayerPrefs.GetInt(PKEY + "cb", 0);
            lettersOn = PlayerPrefs.GetInt(PKEY + "lt", 0);
            wordIdx = PlayerPrefs.GetInt(PKEY + "wi", 0);
            sound = PlayerPrefs.GetInt(PKEY + "snd", 1) == 1;
            Fx.Sound = sound;
        }

        // ============================================== COLORS (SS-like palette)
        public static class C
        {
            public static readonly Color navy = Hex(0x1B2A52);
            public static readonly Color navy2 = Hex(0x26324F);
            public static readonly Color cardBlue = Hex(0xB9CDF3);
            public static readonly Color cardBlue2 = Hex(0xAFC8F0);
            public static readonly Color blue = Hex(0x2E9BF0);
            public static readonly Color blueBright = Hex(0x3FA9F5);
            public static readonly Color blueBanner = Hex(0x4C8DE8);
            public static readonly Color green = Hex(0x3EAE3E);
            public static readonly Color greenDark = Hex(0x2E8B2E);
            public static readonly Color red = Hex(0xD9483B);
            public static readonly Color gold = Hex(0xFFC933);
            public static readonly Color goldDeep = Hex(0xFFB800);
            public static readonly Color orange = Hex(0xFFA51F);
            public static readonly Color orangeDeep = Hex(0xF5891E);
            public static readonly Color white = Hex(0xFFFFFF);
            public static readonly Color sky = Hex(0x35B9F1);
            public static readonly Color purple = Hex(0x6A5ACD);
            public static readonly Color grey = Hex(0x8A93A6);
            public static readonly Color yellow = Hex(0xFFD83D);

            public static Color Hex(int v)
            {
                float r = ((v >> 16) & 0xFF) / 255f;
                float g = ((v >> 8) & 0xFF) / 255f;
                float b = (v & 0xFF) / 255f;
                return new Color(r, g, b, 1f);
            }
        }
    }
}
