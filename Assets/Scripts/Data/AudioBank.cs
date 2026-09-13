using UnityEngine;

namespace DummySurfer.Data
{
    /// <summary>
    /// Optional named audio clips (spec 10 AUDIO). The game ships with fully procedural
    /// placeholder SFX/music (see ProceduralAudio), so this bank can stay empty.
    /// Drop your own original/licensed clips here to instantly upgrade the mix.
    /// </summary>
    [CreateAssetMenu(menuName = "Dummy Surfer/Audio Bank", fileName = "AudioBank")]
    public class AudioBank : ScriptableObject
    {
        [Header("Music")]
        public AudioClip menuMusic;
        public AudioClip runMusic;

        [Header("SFX")]
        public AudioClip coin;
        public AudioClip jump;
        public AudioClip slide;
        public AudioClip crash;
        public AudioClip stumble;
        public AudioClip powerup;
        public AudioClip shieldBreak;
        public AudioClip countdownTick;
        public AudioClip go;
        public AudioClip win;
        public AudioClip lose;
        public AudioClip uiClick;

        private static AudioBank _runtime;
        public static AudioBank Runtime
        {
            get
            {
                if (_runtime != null) return _runtime;
                _runtime = Resources.Load<AudioBank>("Config/AudioBank");
                if (_runtime == null)
                {
                    _runtime = CreateInstance<AudioBank>();
                    _runtime.name = "AudioBank (runtime defaults)";
                }
                return _runtime;
            }
        }
    }
}
