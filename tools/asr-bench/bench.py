"""
Banco de pruebas del reconocedor de voz (solo en el PC).

Corre varios modelos de sherpa-onnx sobre las grabaciones reales que la app
guarda en el teléfono (Android/data/com.ferolabs.hablo/files/grabaciones) y
muestra, para cada una, qué entendió cada modelo y qué puntaje daría
scorePronunciation. Así se elige modelo y se afinan umbrales sin recompilar.

Pasos:
  1. pip install sherpa-onnx numpy
  2. powershell -File tools/asr-bench/fetch_models.ps1
  3. adb pull /sdcard/Android/data/com.ferolabs.hablo/files/grabaciones tools/asr-bench/corpus
  4. python tools/asr-bench/bench.py

La función prep() replica AudioPrep.analyze() de Audio.kt paso por paso. Si se
cambia una, cambiar la otra.

Nada de esto ve la frase esperada antes de reconocer: el objetivo solo se usa
para puntuar después, igual que en la app.
"""

import argparse
import glob
import math
import os
import re
import struct
import sys
import time
import wave

import numpy as np
import sherpa_onnx

HERE = os.path.dirname(os.path.abspath(__file__))

# ---------------------------------------------------------------------------
# Réplica de AudioPrep (Audio.kt)
# ---------------------------------------------------------------------------

SAMPLE_RATE = 16000
FRAME = SAMPLE_RATE // 50          # 20 ms
NOISE_PERCENTILE = 0.15
SPEECH_RATIO = 3.0
SPEECH_MIN_RMS = 0.004
PAD_BEFORE = 0.25
PAD_AFTER = 0.35
MIN_SPEECH_SECONDS = 0.15
MIN_PEAK = 0.005
MIN_SPEECH_RMS = 0.0015
MIN_SNR_DB = 5.0
MIN_SILENT_FRACTION = 0.25     # sin este mínimo de tramas calladas, el SNR no es fiable
PEAK_PERCENTILE = 0.99         # el "pico" ignora un golpe suelto
TARGET_PEAK = 0.5              # a 0.9 se recortarían picos de vocales; ver Audio.kt
MAX_GAIN = 30.0


def percentile_abs(x: np.ndarray, p: float) -> float:
    if x.size == 0:
        return 0.0
    a = np.sort(np.abs(x))
    return float(a[min(x.size - 1, int(x.size * p))])


def prep(raw: np.ndarray):
    """Devuelve (audio_preparado, medidas, motivo_de_rechazo_o_None)."""
    x = raw.astype(np.float32) - np.float32(raw.mean())
    peak = percentile_abs(x, PEAK_PERCENTILE)
    max_abs = float(np.abs(x).max()) if x.size else 0.0
    frames = max(1, x.size // FRAME)
    rms = np.zeros(frames, dtype=np.float32)
    for f in range(frames):
        seg = x[f * FRAME:min(x.size, (f + 1) * FRAME)]
        rms[f] = math.sqrt(float((seg * seg).sum()) / max(1, seg.size))
    s = np.sort(rms)
    noise = max(1e-5, float(s[min(frames - 1, int(frames * NOISE_PERCENTILE))]))
    threshold = max(noise * SPEECH_RATIO, SPEECH_MIN_RMS)
    voiced = np.where(rms > threshold)[0]
    total = x.size / SAMPLE_RATE
    if voiced.size == 0:
        m = dict(dur=total, voz=0.0, pico=peak, max=max_abs, rmsVoz=0.0, piso=noise,
                 snr=0.0, fiable=False, ganancia=1.0)
        return x, m, "TOO_SHORT"
    first, last = int(voiced[0]), int(voiced[-1])
    speech_rms = math.sqrt(float((rms[voiced] ** 2).sum()) / voiced.size)
    snr = 20.0 * math.log10(speech_rms / noise)
    snr_reliable = (frames - voiced.size) / frames >= MIN_SILENT_FRACTION
    a = max(0, first * FRAME - int(PAD_BEFORE * SAMPLE_RATE))
    b = min(x.size, (last + 1) * FRAME + int(PAD_AFTER * SAMPLE_RATE))
    cut = x[a:b]
    cut_peak = percentile_abs(cut, PEAK_PERCENTILE)
    gain = min(MAX_GAIN, TARGET_PEAK / cut_peak) if cut_peak > 0 else 1.0
    prepared = np.clip(cut * np.float32(gain), -1.0, 1.0).astype(np.float32)
    m = dict(dur=total, voz=voiced.size * FRAME / SAMPLE_RATE, pico=peak, max=max_abs,
             rmsVoz=speech_rms, piso=noise, snr=snr, fiable=snr_reliable, ganancia=gain)
    reason = None
    if m["voz"] < MIN_SPEECH_SECONDS:
        reason = "TOO_SHORT"
    elif peak < MIN_PEAK or speech_rms < MIN_SPEECH_RMS:
        reason = "TOO_QUIET"
    elif snr_reliable and snr < MIN_SNR_DB:
        reason = "TOO_NOISY"
    return prepared, m, reason


# ---------------------------------------------------------------------------
# Réplica de scorePronunciation (Pronunciation.kt)
# ---------------------------------------------------------------------------

def normalize(text: str) -> str:
    t = text.lower().replace("’", "'")
    t = "".join(c for c in t if c.isalnum() or c == " " or c == "'")
    return re.sub(r"\s+", " ", t.strip())


def words(text):
    return [w for w in normalize(text).split(" ") if w]


def levenshtein(a, b):
    if a == b:
        return 0
    prev = list(range(len(b) + 1))
    for i in range(1, len(a) + 1):
        cur = [i] + [0] * len(b)
        for j in range(1, len(b) + 1):
            cost = 0 if a[i - 1] == b[j - 1] else 1
            cur[j] = min(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
        prev = cur
    return prev[len(b)]


def similar(a, b):
    n = max(len(a), len(b))
    return n == 0 or (1.0 - levenshtein(a, b) / n) >= 0.6


def score(target, heard):
    t, h = words(target), words(heard)
    if not t:
        return 0, []
    if not h:
        return 0, [(w, "MAL") for w in t]
    dp = [[0] * (len(h) + 1) for _ in range(len(t) + 1)]
    for i in range(len(t) - 1, -1, -1):
        for j in range(len(h) - 1, -1, -1):
            dp[i][j] = dp[i + 1][j + 1] + 1 if t[i] == h[j] else max(dp[i + 1][j], dp[i][j + 1])
    matched = [False] * len(t)
    i = j = 0
    while i < len(t) and j < len(h):
        if t[i] == h[j]:
            matched[i] = True
            i += 1
            j += 1
        elif dp[i + 1][j] >= dp[i][j + 1]:
            i += 1
        else:
            j += 1
    scored = []
    pts = 0
    for k, w in enumerate(t):
        if matched[k]:
            s = "BIEN"
            pts += 100
        elif any(similar(x, w) for x in h):
            s = "DUDOSO"
            pts += 50
        else:
            s = "MAL"
        scored.append((w, s))
    return pts // len(t), scored


# ---------------------------------------------------------------------------
# Modelos
# ---------------------------------------------------------------------------

def find(dirpath, *needles):
    for f in sorted(glob.glob(os.path.join(dirpath, "**", "*"), recursive=True)):
        name = os.path.basename(f).lower()
        if all(n in name for n in needles) and os.path.isfile(f):
            return f
    return None


def load_models(models_dir, only=None):
    """Devuelve [(nombre corto, reconocedor)] para los paquetes que existan."""
    out = []
    for pkg in sorted(os.listdir(models_dir)):
        d = os.path.join(models_dir, pkg)
        if not os.path.isdir(d):
            continue
        short = pkg.replace("sherpa-onnx-", "")
        if only and not any(o in short for o in only):
            continue
        t0 = time.time()
        try:
            if "moonshine" in pkg and find(d, "decoder_model_merged"):
                # Moonshine v2 (paquetes "quantized-2026-..."): dos archivos .ort.
                # Necesita sherpa-onnx >= 1.13.8 (el AAR 1.13.2 de la app no lo carga).
                rec = sherpa_onnx.OfflineRecognizer.from_moonshine_v2(
                    encoder=find(d, "encoder_model"),
                    decoder=find(d, "decoder_model_merged"),
                    tokens=find(d, "tokens.txt"),
                    num_threads=4,
                )
            elif "moonshine" in pkg:
                rec = sherpa_onnx.OfflineRecognizer.from_moonshine(
                    preprocessor=find(d, "preprocess", ".onnx"),
                    encoder=find(d, "encode", ".onnx"),
                    uncached_decoder=find(d, "uncached_decode", ".onnx"),
                    cached_decoder=find(d, "cached_decode", ".onnx"),
                    tokens=find(d, "tokens.txt"),
                    num_threads=4,
                )
            elif "whisper" in pkg:
                rec = sherpa_onnx.OfflineRecognizer.from_whisper(
                    encoder=find(d, "encoder.int8.onnx"),
                    decoder=find(d, "decoder.int8.onnx"),
                    tokens=find(d, "tokens.txt"),
                    language="en",
                    task="transcribe",
                    num_threads=4,
                )
            elif "ctc" in pkg:
                rec = sherpa_onnx.OfflineRecognizer.from_nemo_ctc(
                    model=find(d, "model", ".onnx"),
                    tokens=find(d, "tokens.txt"),
                    num_threads=4,
                )
            else:
                print(f"  (se salta {pkg}: no sé cargarlo)")
                continue
        except Exception as e:  # noqa: BLE001
            print(f"  (no cargó {pkg}: {e})")
            continue
        print(f"  {short}: cargado en {time.time() - t0:.1f}s")
        out.append((short, rec))
    return out


def transcribe(rec, audio: np.ndarray):
    s = rec.create_stream()
    s.accept_waveform(SAMPLE_RATE, audio)
    t0 = time.time()
    rec.decode_stream(s)
    return s.result.text.strip(), time.time() - t0


# ---------------------------------------------------------------------------

def read_wav(path):
    with wave.open(path, "rb") as w:
        assert w.getframerate() == SAMPLE_RATE and w.getnchannels() == 1 and w.getsampwidth() == 2, path
        n = w.getnframes()
        data = struct.unpack("<%dh" % n, w.readframes(n))
    return np.asarray(data, dtype=np.float32) / 32768.0


def read_sidecar(wav_path):
    txt = wav_path[:-4] + ".txt"
    info = {"frase": "", "resultado": "", "medidas": ""}
    if os.path.exists(txt):
        with open(txt, encoding="utf-8") as f:
            for line in f:
                k, _, v = line.partition(":")
                if k.strip() in info:
                    info[k.strip()] = v.strip()
    return info


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--corpus", default=os.path.join(HERE, "corpus"))
    ap.add_argument("--models", default=os.path.join(HERE, "models"))
    ap.add_argument("--only", nargs="*", help="solo modelos cuyo nombre contenga esto")
    ap.add_argument("--raw", action="store_true", help="también correr sobre el audio crudo, sin prep()")
    args = ap.parse_args()

    wavs = sorted(glob.glob(os.path.join(args.corpus, "*.wav")))
    if not wavs:
        print("no hay grabaciones en", args.corpus)
        sys.exit(1)

    print("cargando modelos...")
    models = load_models(args.models, args.only)
    if not models:
        print("no hay modelos en", args.models)
        sys.exit(1)

    totals = {name: {"pts": 0, "n": 0, "ms": 0.0} for name, _ in models}

    for wav in wavs:
        raw = read_wav(wav)
        info = read_sidecar(wav)
        prepared, m, reason = prep(raw)
        print("=" * 100)
        print(f"{os.path.basename(wav)}   frase: {info['frase']}")
        print(f"  app dijo:   {info['resultado']}")
        print(f"  medidas app: {info['medidas']}")
        print("  medidas PC:  dur=%.2fs voz=%.2fs pico=%.3f max=%.3f rmsVoz=%.4f piso=%.4f snr=%.1fdB%s ganancia=x%.1f  -> %s"
              % (m["dur"], m["voz"], m["pico"], m["max"], m["rmsVoz"], m["piso"], m["snr"],
                 "" if m["fiable"] else "(no fiable)", m["ganancia"], reason or "OK"))
        for name, rec in models:
            text, secs = transcribe(rec, prepared)
            pct, scored = score(info["frase"], text)
            bad = " ".join(w for w, s in scored if s != "BIEN")
            print(f"  {name:<45} {pct:3d}%  {secs*1000:5.0f}ms  '{text}'"
                  + (f"   [no calzó: {bad}]" if bad else ""))
            if reason is None and info["frase"]:
                totals[name]["pts"] += pct
                totals[name]["n"] += 1
                totals[name]["ms"] += secs * 1000
            if args.raw:
                text_r, secs_r = transcribe(rec, raw)
                pct_r, _ = score(info["frase"], text_r)
                print(f"  {name + ' (crudo)':<45} {pct_r:3d}%  {secs_r*1000:5.0f}ms  '{text_r}'")

    print("=" * 100)
    print("promedio sobre las grabaciones que pasaron la puerta (ojo: un promedio alto NO es")
    print("mejor si las frases mal dichas a propósito también suben; hay que leer fila por fila):")
    for name, t in totals.items():
        if t["n"]:
            print(f"  {name:<45} {t['pts'] / t['n']:5.1f}%   {t['ms'] / t['n']:6.0f} ms/frase   ({t['n']} grabaciones)")


if __name__ == "__main__":
    main()
