"""
Segunda opinión sobre las etiquetas "buena" del corpus.

Las tomas buenas se etiquetan como bien dichas porque Fero intentó decirlas
bien, pero es un alumno: parte de sus tomas buenas traen el error de verdad
(la th, la v, la consonante final). Si GOP las marca, cuentan como falsa
alarma cuando quizá no lo son. Esto no lo puede zanjar nadie sin un fonetista
oyendo; lo más cercano que tenemos es el acuerdo entre modelos independientes.

Para cada fonema objetivo de una toma buena, pregunta a tres reconocedores de
PALABRAS de familias distintas (Moonshine base, Parakeet 0.6B, Whisper small)
si la palabra salió bien. Luego recalcula la precisión de GOP contando como
"probablemente real" toda marca sobre una palabra que al menos dos de los
tres reconocedores tampoco entendieron.

Uso: python tools/asr-bench/label_check.py   (después de phoneme_eval.py, que deja rows.csv)
"""

import csv
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import bench  # noqa: E402
import gop    # noqa: E402

FRAGS = ["moonshine-base-en-quantized", "parakeet-tdt-0.6b", "whisper-small"]


def main():
    rows = list(csv.DictReader(open(os.path.join(HERE, "corpus", "rows.csv"), encoding="utf-8")))
    models = bench.load_models(os.path.join(HERE, "models"), FRAGS)
    assert len(models) == 3, [n for n, _ in models]

    # palabra bien reconocida por cada modelo, por grabación
    ok = {}  # (wav, word) -> número de modelos que la dieron BIEN
    for wav in sorted({r["wav"] for r in rows}):
        raw = bench.read_wav(os.path.join(HERE, "corpus", wav))
        prepared, _, _ = bench.prep(raw)
        frase = bench.read_sidecar(os.path.join(HERE, "corpus", wav))["frase"]
        for _, rec in models:
            text, _ = bench.transcribe(rec, prepared)
            _, scored = bench.score(frase, text)
            for w, st in scored:
                ok[(wav, w)] = ok.get((wav, w), 0) + (st == "BIEN")

    print("Precisión de GOP por sonido con dos lecturas de las falsas alarmas:")
    print("  estricta = toda marca en toma buena es falsa alarma")
    print("  ajustada = una marca en toma buena cuenta como acierto si >= 2 de 3 reconocedores")
    print("             de palabras tampoco entendieron esa palabra (proxy de error real)")
    print(f"{'sonido':7} {'puntaje':7} {'umbral':>7} {'mostradas':>9} {'estricta':>9} {'ajustada':>9} {'recall':>7}   dudosas-de-verdad")
    for snd in ["sh", "th", "h", "v", "final", "es", "ed"]:
        rs = [r for r in rows if r["sound"] == snd]
        y = np.array([int(r["label"]) for r in rs])
        if y.sum() == 0 or y.sum() == len(y):
            continue
        for name, key, sign in (("AF", "af", 1), ("INS", "ins", -1)):
            sc = np.array([sign * float(r[key]) for r in rs])
            b = gop.best_threshold(list(sc), list(y))
            pred = sc < b["thr"]
            shown = int(pred.sum())
            tp = int((pred & (y == 1)).sum())
            fp_rows = [r for r, p, yy in zip(rs, pred, y) if p and yy == 0]
            likely_real = [r for r in fp_rows if ok.get((r["wav"], r["word"]), 3) <= 1]
            strict = 100 * tp / max(1, shown)
            adjusted = 100 * (tp + len(likely_real)) / max(1, shown)
            rec = 100 * tp / max(1, int(y.sum()))
            print(f"{snd:7} {name:7} {b['thr']:7.2f} {shown:9d} {strict:8.0f}% {adjusted:8.0f}% {rec:6.0f}%   "
                  + ", ".join(f"{r['word']}({r['wav'][9:15]})" for r in likely_real))


if __name__ == "__main__":
    main()
