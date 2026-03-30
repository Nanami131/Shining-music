#!/usr/bin/env python3
"""
基于 Essentia 分析音频特征。
用法: python3 analyze_audio.py <audio_file_path>
输出: JSON 格式的 6 维音频特征 (归一化到 0-1)
"""
import sys
import json
import numpy as np

def analyze(audio_path):
    import essentia.standard as es

    loader = es.MonoLoader(filename=audio_path, sampleRate=44100)
    audio = loader()

    # --- Tempo (BPM) ---
    rhythm_extractor = es.RhythmExtractor2013(method="multifeature")
    bpm, beats, beats_confidence, _, _ = rhythm_extractor(audio)
    # 归一化：60-200 BPM → 0-1
    tempo_norm = max(0.0, min(1.0, (bpm - 60) / 140))

    # --- Energy (RMS) ---
    rms_values = []
    frame_gen = es.FrameGenerator(audio, frameSize=2048, hopSize=1024)
    rms_algo = es.RMS()
    for frame in frame_gen:
        rms_values.append(rms_algo(frame))
    rms_mean = float(np.mean(rms_values))
    # 典型 MP3 的 RMS 在 0.02(轻柔) ~ 0.40(极响) 范围
    energy_norm = max(0.0, min(1.0, rms_mean / 0.40))

    # --- Danceability ---
    danceable = es.Danceability()
    dance_val, _ = danceable(audio)
    # Essentia Danceability 输出约 0.0~2.5，典型流行乐 0.8~1.5
    dance_norm = max(0.0, min(1.0, dance_val / 2.0))

    # --- Acousticness (基于频谱特征) ---
    # 使用 spectral flatness 作为代理：高 flatness = 噪声/电子，低 = 谐波/原声
    flatness_values = []
    frame_gen2 = es.FrameGenerator(audio, frameSize=2048, hopSize=1024)
    spectrum_algo = es.Spectrum(size=2048)
    flatness_algo = es.Flatness()
    for frame in frame_gen2:
        spec = spectrum_algo(frame)
        flatness_values.append(flatness_algo(spec))
    flatness_mean = float(np.mean(flatness_values))
    # 低 flatness = 高 acousticness
    acousticness_norm = max(0.0, min(1.0, 1.0 - flatness_mean * 10))

    # --- Speechiness (人声占比) ---
    # 使用 zero crossing rate 作为粗略代理
    zcr_values = []
    frame_gen3 = es.FrameGenerator(audio, frameSize=2048, hopSize=1024)
    zcr_algo = es.ZeroCrossingRate()
    for frame in frame_gen3:
        zcr_values.append(zcr_algo(frame))
    zcr_mean = float(np.mean(zcr_values))
    # 人声 ZCR 通常 0.04-0.12
    speechiness_norm = max(0.0, min(1.0, zcr_mean / 0.15))

    # --- Valence (音频正面情绪) ---
    # 基于调性：大调倾向正面，小调倾向负面
    key_algo = es.KeyExtractor()
    key, scale, key_strength = key_algo(audio)
    if scale == "major":
        valence_base = 0.65
    else:
        valence_base = 0.35
    # 结合 BPM 和能量微调
    valence_norm = max(0.0, min(1.0, valence_base + (tempo_norm - 0.5) * 0.1 + (energy_norm - 0.5) * 0.1))

    return {
        "tempo": round(tempo_norm, 3),
        "energy": round(energy_norm, 3),
        "danceability": round(dance_norm, 3),
        "acousticness": round(acousticness_norm, 3),
        "valence": round(valence_norm, 3),
        "speechiness": round(speechiness_norm, 3),
        "raw": {
            "bpm": round(bpm, 1),
            "rms_mean": round(rms_mean, 4),
            "danceability_raw": round(dance_val, 4),
            "flatness_mean": round(flatness_mean, 6),
            "zcr_mean": round(zcr_mean, 4),
            "key": key,
            "scale": scale,
            "key_strength": round(key_strength, 3),
        }
    }

def main():
    if len(sys.argv) < 2:
        print("Usage: python3 analyze_audio.py <audio_file_path>", file=sys.stderr)
        sys.exit(1)
    result = analyze(sys.argv[1])
    print(json.dumps(result, ensure_ascii=False, indent=2))

if __name__ == "__main__":
    main()
