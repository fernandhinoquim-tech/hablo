"""
Evalúa GOP (Goodness of Pronunciation) con un reconocedor de fonemas
(exportado con phoneme_export.py) sobre el corpus, siguiendo el plan de
CLAUDE.md ("Plan de trabajo GOP"):

  1. puntuación por fonema = NÚMERO (gop.py: GOP-FA, GOP-AF, INS), no el
     binario del argmax;
  2. umbral = percentil del puntaje que maximiza el MCC sobre la partición de
     calibración; se reportan percentil, MCC y matriz de confusión;
  3. el veredicto se limita al sonido del ejercicio (etiqueta "sound"): en un
     drill de th solo θ/ð cuentan; lo demás se calcula y no se muestra;
  4. se reporta la precisión de lo que se MOSTRARÍA:
        de N correcciones mostradas, M eran errores reales -> X %
        (barra: >= 66 %; por debajo de 33 % es peor que no corregir)
     (Silpachai et al. 2024, LLT);
  5. calibración y verificación son particiones distintas (--verify).

Etiquetas: en una toma mala, el fonema plantado es error (1) y el resto de
fonemas objetivo se asumen bien (0); en una toma buena, todos los fonemas
objetivo se asumen bien (0). Es la verdad-terreno disponible; es imperfecta.

Uso:
  python tools/asr-bench/phoneme_eval.py [--model models/phoneme/<nombre>] [--fp32]
      [--verify PATRON ...]   # grabaciones (por nombre) que van a verificación
"""

import argparse
import glob
import json
import os
import re
import sys
import time

import numpy as np
import onnxruntime as ort

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import bench  # noqa: E402
import gop    # noqa: E402

ARPA_TO_IPA = {
    "AA": ["ɑ"], "AE": ["æ"], "AH": ["ʌ"], "AO": ["ɔ"], "AW": ["a", "ʊ"], "AY": ["a", "ɪ"],
    "B": ["b"], "CH": ["tʃ"], "D": ["d"], "DH": ["ð"], "EH": ["ɛ"], "ER": ["ɚ"], "EY": ["e", "ɪ"],
    "F": ["f"], "G": ["ɡ"], "HH": ["h"], "IH": ["ɪ"], "IY": ["i"], "JH": ["dʒ"], "K": ["k"],
    "L": ["l"], "M": ["m"], "N": ["n"], "NG": ["ŋ"], "OW": ["o", "ʊ"], "OY": ["ɔ", "ɪ"],
    "P": ["p"], "R": ["ɹ"], "S": ["s"], "SH": ["ʃ"], "T": ["t"], "TH": ["θ"], "UH": ["ʊ"],
    "UW": ["u"], "V": ["v"], "W": ["w"], "Y": ["j"], "Z": ["z"], "ZH": ["ʒ"],
}
VOWELS = set("ɑæʌɔaʊɪeiɚoɛuə")

# Frases del corpus del 12-09 que ya no están en drills.json (sesión).
EXTRA_SOUNDS = {
    "the ship is very cheap": "sh", "i think this is the third one": "th",
    "he has a happy home": "h", "very best very good": "v",
    "i walked and talked and asked": "ed", "she needs six books": "final",
    "can you speak spanish": "es", "it's a beautiful world": "rl",
}

# Grabaciones del 12-09 que no son tomas buenas ni plantadas (fuera de guion).
SKIP = {"20260912-010346.wav", "20260912-010613.wav", "20260912-010619.wav"}

# Error plantado por (grabación, palabra): tipo e índice del fonema.
#   sub: sustitución del fonema idx;  ins: inserción antes del fonema idx;
#   syl: sílaba extra al final (= inserción antes del último fonema).
ERROR_PHONES = {
    ("20260912-010316.wav", "ship"): ("sub", 0),
    ("20260912-010452.wav", "ship"): ("sub", 0),
    ("20260912-010329.wav", "think"): ("sub", 0),
    ("20260912-010329.wav", "third"): ("sub", 0),
    ("20260912-010514.wav", "think"): ("sub", 0),
    ("20260912-010514.wav", "third"): ("sub", 0),
    ("20260912-010535.wav", "walked"): ("syl", -1),
    ("20260912-010535.wav", "talked"): ("syl", -1),
    ("20260912-010535.wav", "asked"): ("syl", -1),
    ("20260912-010554.wav", "speak"): ("ins", 0),
    ("20260912-010554.wav", "spanish"): ("ins", 0),
}


def load_session_errors(path):
    """Errores plantados de la sesión (session-errors.json), mismo formato."""
    if not os.path.exists(path):
        return {}
    with open(path, encoding="utf-8") as f:
        d = json.load(f)
    return {(k.split("|")[0], k.split("|")[1]): tuple(v) for k, v in d.items()}


def expected_phones(phrase, symbols):
    import cmudict
    d = cmudict.dict()
    out = []
    for w in bench.words(phrase):
        phones = []
        for part in w.split("-"):
            if part not in d:
                sys.exit(f"CMUdict no tiene '{part}' (frase: {phrase})")
            for arpa in d[part][0]:
                for sym in ARPA_TO_IPA[re.sub(r"\d", "", arpa)]:
                    if sym not in symbols:
                        sym = {"ʌ": "ə", "ə": "ʌ", "ɚ": "ɝ"}.get(sym, sym)
                    if sym not in symbols and len(sym) == 2 and "͡" in symbols:
                        # africadas como tres tokens: t ͡ ʃ (convención L2-ARCTIC)
                        phones += [sym[0], "͡", sym[1]]
                        continue
                    phones.append(sym)
        out.append((w, phones))
    return out


def greedy(logp, id2sym, blank):
    ids = logp.argmax(-1)
    out, prev = [], None
    for i in ids:
        i = int(i)
        if i != prev and i != blank and id2sym[i] not in ("[UNK]", "<unk>", "|", " ", "ˌ", "ˈ", "͡"):
            out.append(id2sym[i])
        prev = i
    return "".join(out)


def targets_for(sound, exp_words):
    """[(idx_palabra, idx_fonema, tipo)] que el drill de este sonido puede marcar."""
    out = []
    for wi, (w, ps) in enumerate(exp_words):
        if sound == "sh":
            # ʃ sueltas, y la t que abre una africada t͡ʃ
            out += [(wi, i, "sub") for i, p in enumerate(ps)
                    if p in ("ʃ", "tʃ") and not (i >= 2 and ps[i - 1] == "͡")]
            out += [(wi, i, "sub") for i, p in enumerate(ps) if p == "t" and i + 1 < len(ps) and ps[i + 1] == "͡"]
        elif sound == "th":
            out += [(wi, i, "sub") for i, p in enumerate(ps) if p in ("θ", "ð")]
        elif sound == "h":
            out += [(wi, i, "sub") for i, p in enumerate(ps) if p == "h"]
        elif sound == "v":
            out += [(wi, i, "sub") for i, p in enumerate(ps) if p == "v"]
        elif sound == "rl":
            out += [(wi, i, "sub") for i, p in enumerate(ps) if p in ("ɹ", "l")]
        elif sound == "final":
            if ps and ps[-1] not in VOWELS:
                out.append((wi, len(ps) - 1, "sub"))
        elif sound == "es":
            if len(ps) >= 2 and ps[0] == "s" and ps[1] not in VOWELS:
                out.append((wi, 0, "ins"))
        elif sound == "ed":
            if w.endswith("ed") and ps and ps[-1] in ("t", "d"):
                out.append((wi, len(ps) - 1, "ins"))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", default=os.path.join(HERE, "models", "phoneme", "wav2vec2-large-xlsr-53-l2-arctic-phoneme"))
    ap.add_argument("--fp32", action="store_true")
    ap.add_argument("--corpus", default=os.path.join(HERE, "corpus"))
    ap.add_argument("--planted", default=os.path.join(HERE, "planted.txt"))
    ap.add_argument("--session-errors", default=os.path.join(HERE, "session-errors.json"))
    ap.add_argument("--verify", nargs="*", default=[], help="fragmentos de nombre de las grabaciones de verificación")
    ap.add_argument("--quiet", action="store_true")
    args = ap.parse_args()

    with open(os.path.join(args.model, "vocab.json"), encoding="utf-8") as f:
        id2sym = {int(k): v for k, v in json.load(f).items()}
    sym2id = {v: k for k, v in id2sym.items()}
    symbols = set(id2sym.values())
    blank = next(i for i, s in id2sym.items() if s in ("[PAD]", "<pad>"))
    phone_ids = [i for i, s in id2sym.items() if s not in ("[PAD]", "<pad>", "[UNK]", "<unk>", "|", " ", "ˌ", "ˈ", "͡", "<s>", "</s>")]
    info = json.load(open(os.path.join(args.model, "export.json"), encoding="utf-8"))

    so = ort.SessionOptions()
    so.intra_op_num_threads = 4
    path = os.path.join(args.model, "model.onnx" if args.fp32 else "model.int8.onnx")
    sess = ort.InferenceSession(path, so, providers=["CPUExecutionProvider"])
    print(f"modelo: {info['model']}  ({'fp32' if args.fp32 else 'int8'}, {os.path.getsize(path) / 1e6:.0f} MB)")

    planted = bench.read_planted(args.planted)
    errors = dict(ERROR_PHONES)
    errors.update(load_session_errors(args.session_errors))
    sound_of = dict(bench.SOUND_OF_PHRASE)
    sound_of.update(EXTRA_SOUNDS)

    # filas: (partición, grabación, palabra, fonema, tipo, etiqueta, fa, af, ins)
    rows = []
    ms_total, n = 0.0, 0
    for wav in sorted(glob.glob(os.path.join(args.corpus, "*.wav"))):
        base = os.path.basename(wav)
        if base in SKIP:
            continue
        raw = bench.read_wav(wav)
        info_w = bench.read_sidecar(wav)
        prepared, m, reason = bench.prep(raw)
        if reason is not None or not info_w["frase"]:
            continue
        sound = sound_of.get(bench.normalize(info_w["frase"]), "general")
        if sound == "general":
            continue
        x = prepared.astype(np.float32)
        if info["do_normalize"]:
            x = (x - x.mean()) / np.sqrt(x.var() + 1e-7)
        t0 = time.time()
        logits = sess.run(None, {"wav": x[None, :]})[0][0]
        ms_total += (time.time() - t0) * 1000
        n += 1
        logp = gop.log_softmax(logits.astype(np.float64))

        exp_words = expected_phones(info_w["frase"], symbols)
        ids = [sym2id[p] for _, ps in exp_words for p in ps]
        offsets = np.cumsum([0] + [len(ps) for _, ps in exp_words])
        fa, _ = gop.gop_fa(logp, ids, blank, phone_ids)
        af, ins = gop.gop_af(logp, ids, blank, phone_ids)
        afx = [min(a, -i) for a, i in zip(af, ins)]   # base - mejor edición simple (sub, borrado o inserción)

        part = "verify" if any(v in base for v in args.verify) else "calib"
        is_bad_take = base in planted
        if not args.quiet:
            print("=" * 100)
            print(f"{base}  [{sound}] {'MALA' if is_bad_take else 'buena'} ({part})  frase: {info_w['frase']}")
            print("  oído: " + greedy(logp, id2sym, blank))
        line = "  objetivo:"
        for wi, pi, kind in targets_for(sound, exp_words):
            w, ps = exp_words[wi]
            k = int(offsets[wi] + pi)
            spec = errors.get((base, w))
            label = 0
            if spec:
                skind, sidx = spec
                sidx = sidx if sidx >= 0 else len(ps) + sidx
                if skind == "syl":
                    skind = "ins"
                label = int(skind == kind and sidx == pi)
            rows.append((part, base, w, ps[pi], kind, label, fa[k], af[k], ins[k], afx[k]))
            line += f"  {w}/{ps[pi]}({kind}){'*' if label else ''} FA={fa[k]:.2f} AF={af[k]:.1f} INS={ins[k]:.1f}"
        if not args.quiet:
            print(line)

    print("=" * 100)
    print(f"latencia media PC: {ms_total / max(1, n):.0f} ms por grabación; {len(rows)} fonemas objetivo evaluados")

    def report(title, sel, score_fn):
        cal = [r for r in rows if r[0] == "calib" and sel(r)]
        ver = [r for r in rows if r[0] == "verify" and sel(r)]
        if not cal:
            return
        s_cal = [score_fn(r) for r in cal]
        y_cal = [r[5] for r in cal]
        if sum(y_cal) == 0 or sum(y_cal) == len(y_cal):
            print(f"\n{title}: calibración sin las dos clases ({sum(y_cal)} errores de {len(y_cal)}); no se puede elegir umbral")
            return
        b = gop.best_threshold(s_cal, y_cal)
        shown = b["tp"] + b["fp"]
        prec = 100 * b["tp"] / shown if shown else 0.0
        rec = 100 * b["tp"] / max(1, b["tp"] + b["fn"])
        print(f"\n{title}")
        print(f"  calibración: {len(cal)} fonemas objetivo ({sum(y_cal)} errores plantados)")
        print(f"  umbral = percentil {b['pct']} del puntaje ({b['thr']:.2f}); MCC = {b['mcc']:.2f}")
        print(f"  matriz: TP={b['tp']} FP={b['fp']} TN={b['tn']} FN={b['fn']}")
        print(f"  de {shown} correcciones mostradas, {b['tp']} eran errores reales -> {prec:.0f} %   (barra >= 66 %; < 33 % peor que nada)   recall {rec:.0f} %")
        if ver:
            s_v = np.asarray([score_fn(r) for r in ver])
            y_v = np.asarray([r[5] for r in ver])
            pred = (s_v < b["thr"]).astype(int)
            tp = int(((pred == 1) & (y_v == 1)).sum()); fp = int(((pred == 1) & (y_v == 0)).sum())
            tn = int(((pred == 0) & (y_v == 0)).sum()); fn = int(((pred == 0) & (y_v == 1)).sum())
            shown = tp + fp
            print(f"  VERIFICACIÓN ({len(ver)} fonemas, {int(y_v.sum())} errores, umbral fijo): TP={tp} FP={fp} TN={tn} FN={fn}  MCC={gop.mcc(tp, fp, tn, fn):.2f}")
            print(f"    de {shown} correcciones mostradas, {tp} eran errores reales -> {100 * tp / shown if shown else 0:.0f} %   recall {100 * tp / max(1, tp + fn):.0f} %")

    report("GOP-FA  (solo objetivos de sustitución: sh, th, h, v, rl, final)", lambda r: r[4] == "sub", lambda r: r[6])
    report("GOP-AF  (solo objetivos de sustitución)", lambda r: r[4] == "sub", lambda r: r[7])
    report("-INS    (solo objetivos de inserción: es, ed)", lambda r: r[4] == "ins", lambda r: -r[8])
    report("GOP-AFX (TODOS los objetivos; base - mejor edición simple: sustituir, borrar o insertar)", lambda r: True, lambda r: r[9])


if __name__ == "__main__":
    main()
