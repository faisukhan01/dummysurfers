using System.Collections.Generic;
using UnityEngine;

namespace DummySurfer.Audio
{
    /// <summary>
    /// Generates original placeholder audio at runtime (spec 10 AUDIO: original/licensed, lightweight).
    /// The game is fully audible with ZERO audio files; drop real clips into AudioBank to override.
    /// </summary>
    public static class ProceduralAudio
    {
        private const int Rate = 22050;
        private static readonly Dictionary<string, AudioClip> Cache = new Dictionary<string, AudioClip>(16);

        public static AudioClip Get(string key)
        {
            if (Cache.TryGetValue(key, out var clip)) return clip;
            clip = key switch
            {
                "coin" => Blip(1320f, 0.09f, 0.5f, 660f),
                "jump" => Chirp(300f, 780f, 0.22f, 0.4f),
                "slide" => NoiseSweep(0.25f, 0.3f),
                "crash" => NoiseBurst(0.5f, 0.85f, 90f),
                "stumble" => NoiseBurst(0.2f, 0.4f, 220f),
                "powerup" => Arp(new[] { 523f, 659f, 784f, 1046f }, 0.08f, 0.35f),
                "shieldbreak" => Chirp(900f, 220f, 0.3f, 0.5f),
                "tick" => Blip(880f, 0.1f, 0.45f),
                "go" => Blip(1174f, 0.25f, 0.5f),
                "win" => Arp(new[] { 523f, 659f, 784f, 1046f, 784f, 1046f }, 0.11f, 0.4f),
                "lose" => Arp(new[] { 392f, 330f, 262f }, 0.18f, 0.4f),
                "click" => Blip(660f, 0.05f, 0.3f),
                "music_menu" => MusicLoop(new[] { 262f, 330f, 392f, 330f, 220f, 262f, 330f, 262f }, 0.32f, 0.16f, wave: 0f),
                "music_run" => MusicLoop(new[] { 220f, 220f, 294f, 220f, 247f, 247f, 294f, 330f }, 0.17f, 0.2f, wave: 1f),
                _ => null
            };
            Cache[key] = clip;
            return clip;
        }

        private static AudioClip Blip(float freq, float dur, float amp, float endFreq = -1f)
        {
            int n = (int)(Rate * dur);
            var data = new float[n];
            float phase = 0f;
            for (int i = 0; i < n; i++)
            {
                float t = i / (float)n;
                float f = endFreq > 0f ? Mathf.Lerp(freq, endFreq, t) : freq;
                phase += 2f * Mathf.PI * f / Rate;
                float env = Mathf.Min(1f, t * 40f) * (1f - t);
                data[i] = Mathf.Sin(phase) * amp * env;
            }
            return Make($"sfx_{freq}_{dur}", data);
        }

        private static AudioClip Chirp(float f0, float f1, float dur, float amp)
        {
            int n = (int)(Rate * dur);
            var data = new float[n];
            float phase = 0f;
            for (int i = 0; i < n; i++)
            {
                float t = i / (float)n;
                phase += 2f * Mathf.PI * Mathf.Lerp(f0, f1, t * t) / Rate;
                float env = Mathf.Min(1f, t * 30f) * (1f - t * 0.7f);
                data[i] = Mathf.Sin(phase) * amp * env;
            }
            return Make($"chirp_{f0}_{f1}", data);
        }

        private static AudioClip NoiseBurst(float dur, float amp, float lowpass)
        {
            int n = (int)(Rate * dur);
            var data = new float[n];
            var rng = new System.Random();
            float prev = 0f, alpha = Mathf.Clamp01(lowpass / Rate);
            for (int i = 0; i < n; i++)
            {
                float t = i / (float)n;
                float white = (float)(rng.NextDouble() * 2.0 - 1.0);
                prev += alpha * (white - prev);
                data[i] = prev * amp * (1f - t) * (1f - t);
            }
            return Make($"noise_{dur}_{lowpass}", data);
        }

        private static AudioClip NoiseSweep(float dur, float amp)
        {
            int n = (int)(Rate * dur);
            var data = new float[n];
            var rng = new System.Random();
            float prev = 0f;
            for (int i = 0; i < n; i++)
            {
                float t = i / (float)n;
                float alpha = Mathf.Lerp(0.5f, 0.05f, t);
                float white = (float)(rng.NextDouble() * 2.0 - 1.0);
                prev += alpha * (white - prev);
                data[i] = prev * amp * Mathf.Sin(t * Mathf.PI);
            }
            return Make("sweep", data);
        }

        private static AudioClip Arp(float[] notes, float step, float amp)
        {
            int n = (int)(Rate * step * notes.Length);
            var data = new float[n];
            for (int i = 0; i < n; i++)
            {
                int noteIdx = Mathf.Min(i / (int)(Rate * step), notes.Length - 1);
                float tInNote = (i % (int)(Rate * step)) / (float)(Rate * step);
                float env = Mathf.Min(1f, tInNote * 30f) * (1f - tInNote * 0.8f);
                data[i] = Mathf.Sin(2f * Mathf.PI * notes[noteIdx] * i / Rate) * amp * env;
            }
            return Make("arp", data);
        }

        /// <summary>Simple original chiptune loop (square/sine blend), seamless by construction.</summary>
        private static AudioClip MusicLoop(float[] notes, float step, float amp, float wave)
        {
            int steps = notes.Length;
            int noteLen = (int)(Rate * step);
            int n = noteLen * steps;
            var data = new float[n];
            float bassPhase = 0f;
            for (int s = 0; s < steps; s++)
            {
                float f = notes[s];
                for (int i = 0; i < noteLen; i++)
                {
                    int idx = s * noteLen + i;
                    float t = i / (float)noteLen;
                    float ph = 2f * Mathf.PI * f * i / Rate;
                    float tone = Mathf.Lerp(Mathf.Sin(ph), Mathf.Sign(Mathf.Sin(ph)) * 0.6f, wave);
                    float env = Mathf.Min(1f, t * 24f) * (1f - t * 0.55f);
                    bassPhase += 2f * Mathf.PI * f * 0.25f / Rate;
                    float bass = Mathf.Sin(bassPhase) * 0.5f;
                    data[idx] = (tone * amp + bass * amp * 0.7f) * env;
                }
            }
            return Make($"music_{notes[0]}_{wave}", data);
        }

        private static AudioClip Make(string name, float[] data)
        {
            var clip = AudioClip.Create(name, data.Length, 1, Rate, false);
            clip.SetData(data, 0);
            return clip;
        }
    }
}
