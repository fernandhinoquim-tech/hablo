"""
Etiqueta las grabaciones de la sesión (session-drills.json) a partir de los
.txt del corpus: por cada frase de la sesión, la penúltima grabación es la
toma BUENA y la última la toma MALA (si Fero repitió, valen las dos últimas).

Escribe:
  planted.txt          (se agregan las tomas malas con su palabra)
  session-errors.json  {"<wav>|<palabra>": [tipo, idx]}  para phoneme_eval.py
  session-good.txt     (las tomas buenas; las repeticiones descartadas no
                        cuentan como buenas ni como malas)
y muestra la lista de grabaciones de VERIFICACIÓN (frases 4 y 5 de cada
sonido), que se pasan a phoneme_eval.py --verify.

Uso:  python tools/asr-bench/session_labels.py [--desde 20260913]
"""

import argparse
import glob
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import bench  # noqa: E402

# frase -> (palabra con el error, tipo, idx del fonema). Ver session-drills.json.
#   sub: sustitución/borrado del fonema idx (-1 = el último);  syl: sílaba
#   extra al final (-ed);  ins: inserción antes del fonema idx.
SESSION_ERRORS = {
    "the ship is very cheap": ("ship", "sub", 0),
    "she sells fresh fish": ("she", "sub", 0),
    "wash your shoes": ("wash", "sub", -1),
    "show me the shop": ("shop", "sub", 0),
    "i wish i had cash": ("wish", "sub", -1),
    "i think this is the third one": ("think", "sub", 0),
    "thank you very much": ("thank", "sub", 0),
    "my birthday is on thursday": ("thursday", "sub", 0),
    "nothing is free": ("nothing", "sub", 2),
    "both teeth hurt": ("teeth", "sub", -1),
    "he has a happy home": ("home", "sub", 0),
    "how are you": ("how", "sub", 0),
    "her hat is here": ("hat", "sub", 0),
    "i hope you are hungry": ("hope", "sub", 0),
    "hello my name is ana": ("hello", "sub", 0),
    "very good": ("very", "sub", 0),
    "we live in a village": ("village", "sub", 0),
    "give me the video": ("video", "sub", 0),
    "seven or eleven": ("seven", "sub", 2),
    "i never drive at night": ("drive", "sub", -1),
    "i walked to school": ("walked", "syl", -1),
    "she worked all day": ("worked", "syl", -1),
    "we watched a movie": ("watched", "syl", -1),
    "he stopped the car": ("stopped", "syl", -1),
    "they laughed a lot": ("laughed", "syl", -1),
    "i speak english": ("speak", "ins", 0),
    "the school is big": ("school", "ins", 0),
    "my brother is a student": ("student", "ins", 0),
    "stop right now": ("stop", "ins", 0),
    "it's a special day": ("special", "ins", 0),
    "i want some milk": ("milk", "sub", -1),
    "the desk is old": ("desk", "sub", -1),
    "he asked for help": ("help", "sub", -1),
    "it's cold outside": ("cold", "sub", -1),
    "just a second": ("just", "sub", -1),
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--corpus", default=os.path.join(HERE, "corpus"))
    ap.add_argument("--desde", default="20260913", help="solo grabaciones cuyo nombre empiece así (fecha de la sesión)")
    args = ap.parse_args()

    with open(os.path.join(HERE, "session-drills.json"), encoding="utf-8") as f:
        drills = json.load(f)["drills"]
    order = {bench.normalize(d["text"]): i for i, d in enumerate(drills)}

    by_phrase = {}
    for wav in sorted(glob.glob(os.path.join(args.corpus, "*.wav"))):
        base = os.path.basename(wav)
        if not base.startswith(args.desde):
            continue
        info = bench.read_sidecar(wav)
        key = bench.normalize(info["frase"])
        if key in order:
            by_phrase.setdefault(key, []).append(base)

    planted_lines, errors, verify, good = [], {}, [], []
    missing = []
    for key, idx in sorted(order.items(), key=lambda kv: kv[1]):
        takes = by_phrase.get(key, [])
        if len(takes) < 2:
            missing.append(f"{idx + 1:2d}. {drills[idx]['text']}  ({len(takes)} toma{'s' if len(takes) != 1 else ''})")
            continue
        good_take, bad_take = takes[-2], takes[-1]
        word, kind, pidx = SESSION_ERRORS[key]
        planted_lines.append(f"{bad_take} {word}   # sesión: {drills[idx]['text']}")
        errors[f"{bad_take}|{word}"] = [kind, pidx]
        good.append(good_take)
        # frases 4 y 5 de cada sonido (posición 3 y 4 dentro del bloque de 5) -> verificación
        if idx % 5 >= 3:
            verify += [good_take, bad_take]

    with open(os.path.join(HERE, "planted.txt"), "a", encoding="utf-8") as f:
        f.write("\n# --- sesión " + args.desde + " (toma mala de cada frase) ---\n")
        f.write("\n".join(planted_lines) + "\n")
    path = os.path.join(HERE, "session-errors.json")
    old = {}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            old = json.load(f)
    old.update(errors)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(old, f, ensure_ascii=False, indent=1)

    with open(os.path.join(HERE, "session-good.txt"), "a", encoding="utf-8") as f:
        f.write("\n".join(good) + "\n")

    print(f"frases completas: {len(planted_lines)} de {len(order)}")
    if missing:
        print("frases sin las dos tomas (no se etiquetan):")
        print("  " + "\n  ".join(missing))
    print(f"\nverificación ({len(verify)} grabaciones, frases 4-5 de cada sonido):")
    print("  --verify " + " ".join(v[:-4] for v in verify))


if __name__ == "__main__":
    main()
