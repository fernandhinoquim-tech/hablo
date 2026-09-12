"""
Escribe los umbrales de GOP que usa la app (app/src/main/assets/gop/thresholds.json)
a partir de rows.csv (lo deja phoneme_eval.py sobre el corpus etiquetado).

Por sonido:
  score  : qué puntaje se usa ("af" = GOP-AF del fonema; "ins" = -INS, inserción antes)
  red    : umbral que maximiza el MCC en calibración -> por debajo, MAL (rojo)
  yellow : el umbral más permisivo cuya precisión estricta sigue >= 33 % (la
           cota "peor que nada" de Silpachai 2024), nunca por debajo de red
           -> por debajo, DUDOSO (amarillo). Castigar de más, pero en amarillo.
  Se eligen en calibración, se reportan en verificación, y para la app se
  reajustan con todo lo etiquetado.

Solo se escriben los sonidos habilitados (--sounds). Los demás no muestran
veredicto hasta tener mejor verdad-terreno (ver CLAUDE.md).

Uso: python tools/asr-bench/calibrate.py --sounds sh h
"""

import argparse
import csv
import json
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gop  # noqa: E402

SCORE_OF = {"sh": "ins", "h": "af", "th": "af", "v": "af", "final": "af", "rl": "af", "es": "ins", "ed": "ins"}
OUT = os.path.normpath(os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "gop", "thresholds.json"))


def fit(rs):
    """Umbrales sobre un conjunto de filas: red = MCC máximo; yellow = el umbral
    más permisivo cuya precisión estricta sigue >= 33 % (no peor que nada)."""
    y = np.array([int(r["label"]) for r in rs])
    sc = np.array([r["sc"] for r in rs])
    b = gop.best_threshold(list(sc), list(y))
    yellow = b["thr"]
    for pct in range(99, 0, -1):
        thr = float(np.percentile(sc, pct))
        pred = sc < thr
        shown = int(pred.sum())
        if shown and ((pred & (y == 1)).sum() / shown) >= 0.33 and thr >= b["thr"]:
            yellow = thr
            break
    return b, yellow


def evaluate(rs, red, yellow):
    y = np.array([int(r["label"]) for r in rs])
    sc = np.array([r["sc"] for r in rs])
    out = {}
    for name, thr in (("rojo", red), ("amarillo", yellow)):
        pred = sc < thr
        shown = int(pred.sum())
        tp = int((pred & (y == 1)).sum())
        out[name] = (shown, tp, tp / shown if shown else 0.0, tp / max(1, int(y.sum())))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sounds", nargs="+", required=True)
    ap.add_argument("--rows", default=os.path.join(HERE, "corpus", "rows.csv"))
    args = ap.parse_args()

    rows = list(csv.DictReader(open(args.rows, encoding="utf-8")))
    out = {}
    for snd in args.sounds:
        key = SCORE_OF[snd]
        sign = -1 if key == "ins" else 1
        rs = [dict(r, sc=sign * float(r[key])) for r in rows if r["sound"] == snd]
        cal = [r for r in rs if r["part"] == "calib"]
        ver = [r for r in rs if r["part"] == "verify"]
        if sum(int(r["label"]) for r in cal) < 3:
            print(f"{snd}: sin datos suficientes en calibración")
            continue
        b, yellow = fit(cal)
        print(f"{snd} ({key}): calibración red<{b['thr']:.2f} yellow<{yellow:.2f}")
        for part, rr in (("  calibración", cal), ("  VERIFICACIÓN", ver)):
            if not rr:
                continue
            ev = evaluate(rr, b["thr"], yellow)
            print(f"{part}: rojo {ev['rojo'][1]}/{ev['rojo'][0]} mostradas = {ev['rojo'][2]:.0%} (recall {ev['rojo'][3]:.0%}); "
                  f"amarillo {ev['amarillo'][1]}/{ev['amarillo'][0]} = {ev['amarillo'][2]:.0%} (recall {ev['amarillo'][3]:.0%})")
        # para la app: ajuste final con todo lo etiquetado (ya verificado arriba)
        b_all, yellow_all = fit(rs)
        ev = evaluate(rs, b_all["thr"], yellow_all)
        out[snd] = {
            "score": key, "red": round(b_all["thr"], 3), "yellow": round(yellow_all, 3),
            "red_precision": round(ev["rojo"][2], 2), "red_recall": round(ev["rojo"][3], 2),
            "yellow_precision": round(ev["amarillo"][2], 2), "yellow_recall": round(ev["amarillo"][3], 2),
            "n": len(rs), "n_errors": sum(int(r["label"]) for r in rs),
        }
        print(f"  para la app (todo): red<{b_all['thr']:.2f} ({ev['rojo'][2]:.0%} de {ev['rojo'][0]}), yellow<{yellow_all:.2f} ({ev['amarillo'][2]:.0%} de {ev['amarillo'][0]})")

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump({"_comentario": "Generado por tools/asr-bench/calibrate.py; no editar a mano. Puntaje por debajo de red = MAL, por debajo de yellow = DUDOSO.",
                   "model": "wav2vec2-large-xlsr-53-l2-arctic-phoneme", "sounds": out}, f, ensure_ascii=False, indent=2)
    print("->", OUT)


if __name__ == "__main__":
    main()
