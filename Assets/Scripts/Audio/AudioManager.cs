using UnityEngine;
using DummySurfer.Core;
using DummySurfer.Data;
using DummySurfer.Utilities;

namespace DummySurfer.Audio
{
    /// <summary>
    /// Music/SFX playback (spec 5.2 AudioManager): procedural fallback clips, persisted volumes,
    /// click feedback, ducking while paused. Uses the AudioBank clip when present, otherwise
    /// generates an original placeholder (see ProceduralAudio).
    /// </summary>
    public sealed class AudioManager : PersistentManager<AudioManager>
    {
        private AudioSource _music;
        private AudioSource _sfxA, _sfxB;
        private AudioBank _bank;
        private float _musicVol = 0.7f, _sfxVol = 0.9f;
        private MusicId _current = MusicId.None;

        public float MusicVolume => _musicVol;
        public float SfxVolume => _sfxVol;

        protected override void OnManagerAwake()
        {
            ServiceRegistry.Register(this);
            DontDestroyOnLoad(gameObject);

            _bank = AudioBank.Runtime;
            _music = gameObject.AddComponent<AudioSource>();
            _music.loop = true;
            _music.playOnAwake = false;
            _sfxA = gameObject.AddComponent<AudioSource>();
            _sfxB = gameObject.AddComponent<AudioSource>();

            _musicVol = PlayerPrefs.GetFloat(Constants.Prefs.MusicVolume, 0.7f);
            _sfxVol = PlayerPrefs.GetFloat(Constants.Prefs.SfxVolume, 0.9f);
            ApplyVolumes();
        }

        public void SetMusicVolume(float v)
        {
            _musicVol = Mathf.Clamp01(v);
            PlayerPrefs.SetFloat(Constants.Prefs.MusicVolume, _musicVol);
            ApplyVolumes();
        }

        public void SetSfxVolume(float v)
        {
            _sfxVol = Mathf.Clamp01(v);
            PlayerPrefs.SetFloat(Constants.Prefs.SfxVolume, _sfxVol);
        }

        private void ApplyVolumes()
        {
            if (_music != null) _music.volume = _musicVol * (GameStateManager.Ensure().Current == GameState.Paused ? 0.4f : 1f);
        }

        public void PlayMusic(MusicId id)
        {
            if (_music == null || id == _current) return;
            _current = id;
            AudioClip clip = id switch
            {
                MusicId.Menu => _bank.menuMusic != null ? _bank.menuMusic : ProceduralAudio.Get("music_menu"),
                MusicId.Run => _bank.runMusic != null ? _bank.runMusic : ProceduralAudio.Get("music_run"),
                _ => null
            };
            if (clip == null) { _music.Stop(); return; }
            _music.clip = clip;
            _music.volume = _musicVol;
            _music.Play();
        }

        public void StopMusic()
        {
            _current = MusicId.None;
            if (_music != null) _music.Stop();
        }

        public void PlaySfx(SfxId id, float volumeScale = 1f)
        {
            if (_sfxA == null || _sfxVol <= 0.001f) return;
            AudioClip clip = ResolveClip(id);
            if (clip == null) return;
            var src = _sfxA.isPlaying ? _sfxB : _sfxA;
            src.pitch = id == SfxId.Coin ? Random.Range(0.96f, 1.08f) : 1f;   // satisfying coin pitch variation
            src.PlayOneShot(clip, _sfxVol * volumeScale);
        }

        private AudioClip ResolveClip(SfxId id) => id switch
        {
            SfxId.Coin => _bank.coin != null ? _bank.coin : ProceduralAudio.Get("coin"),
            SfxId.Jump => _bank.jump != null ? _bank.jump : ProceduralAudio.Get("jump"),
            SfxId.SlideWhoosh => _bank.slide != null ? _bank.slide : ProceduralAudio.Get("slide"),
            SfxId.Crash => _bank.crash != null ? _bank.crash : ProceduralAudio.Get("crash"),
            SfxId.Stumble => _bank.stumble != null ? _bank.stumble : ProceduralAudio.Get("stumble"),
            SfxId.Powerup => _bank.powerup != null ? _bank.powerup : ProceduralAudio.Get("powerup"),
            SfxId.ShieldBreak => _bank.shieldBreak != null ? _bank.shieldBreak : ProceduralAudio.Get("shieldbreak"),
            SfxId.CountdownTick => _bank.countdownTick != null ? _bank.countdownTick : ProceduralAudio.Get("tick"),
            SfxId.Go => _bank.go != null ? _bank.go : ProceduralAudio.Get("go"),
            SfxId.Win => _bank.win != null ? _bank.win : ProceduralAudio.Get("win"),
            SfxId.Lose => _bank.lose != null ? _bank.lose : ProceduralAudio.Get("lose"),
            SfxId.UiClick => _bank.uiClick != null ? _bank.uiClick : ProceduralAudio.Get("click"),
            _ => null
        };

        private void OnEnable()
        {
            GameEvents.GameStateChanged += OnGameStateChanged;
        }

        private void OnDisable()
        {
            GameEvents.GameStateChanged -= OnGameStateChanged;
        }

        private void OnGameStateChanged(Core.GameState s)
        {
            ApplyVolumes();
            if (s == Core.GameState.Running) PlayMusic(MusicId.Run);
            else if (s == Core.GameState.Menu || s == Core.GameState.Lobby) PlayMusic(MusicId.Menu);
        }
    }
}
