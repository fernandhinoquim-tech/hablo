"""Desglose por sonido de rows.csv (lo escribe phoneme_eval.py)."""
import csv
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gop  # noqa: E402

rows = list(csv.DictReader(open(os.path.join(HERE, "corpus", "rows.csv"), encoding="utf-8")))
print(f"{'sonido':7} {'n':>3} {'err':>3} | AF: mejor umbral -> precisión/recall (MCC) | INS idem")
for snd in ["sh", "th", "h", "v", "rl", "final", "es", "ed"]:
    rs = [r for r in rows if r["sound"] == snd]
    if not rs:
        continue
    y = [int(r["label"]) for r in rs]
    out = f"{snd:7} {len(rs):3d} {sum(y):3d} |"
    for name, key, sign in (("AF", "af", 1), ("INS", "ins", -1)):
        sc = [sign * float(r[key]) for r in rs]
        if 0 < sum(y) < len(y):
            b = gop.best_threshold(sc, y)
            shown = b["tp"] + b["fp"]
            out += f" {name}: p{b['pct']:2d} -> {100 * b['tp'] / max(1, shown):3.0f}%/{100 * b['tp'] / max(1, b['tp'] + b['fn']):3.0f}% (MCC {b['mcc']:.2f}) |"
        else:
            out += f" {name}: - |"
    print(out)

print("\nAF de los errores plantados frente a los buenos, por sonido (más bajo = peor):")
for snd in ["sh", "th", "h", "v", "final", "rl"]:
    rs = [r for r in rows if r["sound"] == snd]
    bad = sorted(float(r["af"]) for r in rs if r["label"] == "1")
    good = sorted(float(r["af"]) for r in rs if r["label"] == "0")
    print(f"  {snd:6} plantados: {' '.join(f'{v:.1f}' for v in bad)}")
    print(f"  {'':6} buenos:    {' '.join(f'{v:.1f}' for v in good)}")
print("\nINS (más alto = inserción):")
for snd in ["es", "ed"]:
    rs = [r for r in rows if r["sound"] == snd]
    bad = sorted(float(r["ins"]) for r in rs if r["label"] == "1")
    good = sorted(float(r["ins"]) for r in rs if r["label"] == "0")
    print(f"  {snd:6} plantados: {' '.join(f'{v:.1f}' for v in bad)}")
    print(f"  {'':6} buenos:    {' '.join(f'{v:.1f}' for v in good)}")
