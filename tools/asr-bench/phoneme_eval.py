"""
Evalúa un reconocedor de FONEMAS (exportado con phoneme_export.py) sobre el
corpus, para compararlo con el enfoque actual de palabras.

Para cada grabación:
  1. mismo prep() que la app (bench.py), más la normalización media 0 /
     varianza 1 que espera wav2vec2;
  2. el modelo devuelve logits por trama (20 ms) sobre el alfabeto de fonemas;
  3. decodificación CTC voraz -> secuencia de fonemas OÍDOS;
  4. fonemas ESPERADOS de la frase con el CMU Pronouncing Dictionary (ARPAbet)
     traducidos al alfabeto del modelo;
  5. alineación por edición entre esperado y oído -> cada fonema esperado
     queda BIEN (calzó), SUB (salió otro), DEL (no salió); las inserciones se
     cuentan aparte (p. ej. la "e" de "espeak").

Con planted.txt mide lo mismo que bench.py: de los errores plantados,
cuántos pasaron como BIEN (sobrecorrección) — aquí a nivel de fonema, con
ERROR_PHONES diciendo qué fonema tenía que salir mal.

Uso:
  python tools/asr-bench/phoneme_eval.py [--model models/phoneme/<nombre>] [--fp32]
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
import bench  # noqa: E402  (prep, read_wav, read_sidecar, normalize, read_planted)

# ---------------------------------------------------------------------------
# ARPAbet (CMUdict) -> símbolos del modelo. Convención L2-ARCTIC / TIMIT-IPA:
# diptongos como dos símbolos, africadas con ligadura, schwa = ʌ si el
# alfabeto no tiene ə.
# ---------------------------------------------------------------------------
ARPA_TO_IPA = {
    "AA": ["ɑ"], "AE": ["æ"], "AH": ["ʌ"], "AO": ["ɔ"], "AW": ["a", "ʊ"], "AY": ["a", "ɪ"],
    "B": ["b"], "CH": ["tʃ"], "D": ["d"], "DH": ["ð"], "EH": ["ɛ"], "ER": ["ɚ"], "EY": ["e", "ɪ"],
    "F": ["f"], "G": ["ɡ"], "HH": ["h"], "IH": ["ɪ"], "IY": ["i"], "JH": ["dʒ"], "K": ["k"],
    "L": ["l"], "M": ["m"], "N": ["n"], "NG": ["ŋ"], "OW": ["o", "ʊ"], "OY": ["ɔ", "ɪ"],
    "P": ["p"], "R": ["ɹ"], "S": ["s"], "SH": ["ʃ"], "T": ["t"], "TH": ["θ"], "UH": ["ʊ"],
    "UW": ["u"], "V": ["v"], "W": ["w"], "Y": ["j"], "Z": ["z"], "ZH": ["ʒ"],
}

# Qué fonema (índice dentro de la palabra, 0-based) tenía que salir mal en cada
# error plantado, y cómo. Extiende planted.txt con el detalle fonético.
#   sub  = sustitución del fonema esperado en esa posición
#   ins  = inserción ANTES del fonema en esa posición (p. ej. "e" antes de s)
#   syl  = sílaba extra al final (-ed dicho como "ed")
ERROR_PHONES = {
    ("20260912-010316.wav", "ship"): ("sub", 0),      # ʃ -> tʃ
    ("20260912-010452.wav", "ship"): ("sub", 0),
    ("20260912-010329.wav", "think"): ("sub", 0),     # θ -> t
    ("20260912-010329.wav", "third"): ("sub", 0),
    ("20260912-010514.wav", "think"): ("sub", 0),
    ("20260912-010514.wav", "third"): ("sub", 0),
    ("20260912-010535.wav", "walked"): ("syl", -1),   # -t -> -ɪd
    ("20260912-010535.wav", "talked"): ("syl", -1),
    ("20260912-010535.wav", "asked"): ("syl", -1),
    ("20260912-010554.wav", "speak"): ("ins", 0),     # ɛ antes de s
    ("20260912-010554.wav", "spanish"): ("ins", 0),
}


def expected_phones(phrase, symbols):
    """[(palabra, [fonemas])] con CMUdict; revienta si falta una palabra."""
    import cmudict
    d = cmudict.dict()
    out = []
    for w in bench.words(phrase):
        parts = w.split("-") if "-" in w else [w]
        phones = []
        for part in parts:
            if part not in d:
                sys.exit(f"CMUdict no tiene '{part}' (frase: {phrase})")
            for arpa in d[part][0]:
                arpa = re.sub(r"\d", "", arpa)
                for sym in ARPA_TO_IPA[arpa]:
                    if sym not in symbols:
                        # el modelo no tiene ese símbolo: se aproxima
                        sym = {"ʌ": "ə", "ə": "ʌ", "ɚ": "ɝ"}.get(sym, sym)
                    phones.append(sym)
        out.append((w, phones))
    return out


def greedy_ctc(logits, id2sym, blank_ids):
    ids = logits.argmax(-1)
    out, prev = [], None
    for i in ids:
        i = int(i)
        if i != prev and i not in blank_ids:
            out.append(id2sym[i])
        prev = i
    # ligaduras: "t", "͡", "ʃ" -> "tʃ"; marcas de acento y espacios fuera
    merged = []
    k = 0
    while k < len(out):
        s = out[k]
        if s == "͡" and merged and k + 1 < len(out):
            merged[-1] = merged[-1] + out[k + 1]
            k += 2
            continue
        if s in ("ˌ", "ˈ", "|", " ", "[UNK]", "<unk>"):
            k += 1
            continue
        merged.append(s)
        k += 1
    return merged


def align(exp, rec):
    """Alineación por edición. Devuelve lista de (op, e, r): op en BIEN/SUB/DEL/INS."""
    n, m = len(exp), len(rec)
    D = np.zeros((n + 1, m + 1), dtype=np.int32)
    D[:, 0] = np.arange(n + 1)
    D[0, :] = np.arange(m + 1)
    for i in range(1, n + 1):
        for j in range(1, m + 1):
            D[i, j] = min(D[i - 1, j] + 1, D[i, j - 1] + 1, D[i - 1, j - 1] + (exp[i - 1] != rec[j - 1]))
    i, j, ops = n, m, []
    while i > 0 or j > 0:
        if i > 0 and j > 0 and D[i, j] == D[i - 1, j - 1] + (exp[i - 1] != rec[j - 1]):
            ops.append(("BIEN" if exp[i - 1] == rec[j - 1] else "SUB", exp[i - 1], rec[j - 1]))
            i, j = i - 1, j - 1
        elif i > 0 and D[i, j] == D[i - 1, j] + 1:
            ops.append(("DEL", exp[i - 1], "-"))
            i -= 1
        else:
            ops.append(("INS", "-", rec[j - 1]))
            j -= 1
    return ops[::-1]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", default=os.path.join(HERE, "models", "phoneme", "wav2vec2-large-xlsr-53-l2-arctic-phoneme"))
    ap.add_argument("--fp32", action="store_true")
    ap.add_argument("--corpus", default=os.path.join(HERE, "corpus"))
    ap.add_argument("--planted", default=os.path.join(HERE, "planted.txt"))
    args = ap.parse_args()

    with open(os.path.join(args.model, "vocab.json"), encoding="utf-8") as f:
        id2sym = {int(k): v for k, v in json.load(f).items()}
    symbols = set(id2sym.values())
    blank_ids = {i for i, s in id2sym.items() if s in ("[PAD]", "<pad>")}
    info = json.load(open(os.path.join(args.model, "export.json"), encoding="utf-8"))

    so = ort.SessionOptions()
    so.intra_op_num_threads = 4
    path = os.path.join(args.model, "model.onnx" if args.fp32 else "model.int8.onnx")
    sess = ort.InferenceSession(path, so, providers=["CPUExecutionProvider"])
    print(f"modelo: {info['model']}  ({'fp32' if args.fp32 else 'int8'}, {os.path.getsize(path) / 1e6:.0f} MB)")

    planted = bench.read_planted(args.planted)
    wavs = sorted(glob.glob(os.path.join(args.corpus, "*.wav")))
    fixed, caught, good_flags, good_words, ms_total, n = [], [], 0, 0, 0.0, 0

    for wav in wavs:
        base = os.path.basename(wav)
        raw = bench.read_wav(wav)
        info_w = bench.read_sidecar(wav)
        prepared, m, reason = bench.prep(raw)
        if reason is not None or not info_w["frase"]:
            continue
        x = prepared.astype(np.float32)
        if info["do_normalize"]:
            x = (x - x.mean()) / np.sqrt(x.var() + 1e-7)
        t0 = time.time()
        logits = sess.run(None, {"wav": x[None, :]})[0][0]
        ms = (time.time() - t0) * 1000
        ms_total += ms
        n += 1
        rec = greedy_ctc(logits, id2sym, blank_ids)
        exp_words = expected_phones(info_w["frase"], symbols)
        exp_flat = [p for _, ps in exp_words for p in ps]
        ops = align(exp_flat, rec)

        # repartir las operaciones por palabra esperada
        per_word = {w: [] for w, _ in exp_words}
        word_of_index = []
        for w, ps in exp_words:
            word_of_index += [w] * len(ps)
        k = 0
        pending_ins = []
        for op, e, r in ops:
            if op == "INS":
                pending_ins.append(r)
                continue
            w = word_of_index[k]
            if pending_ins:
                per_word[w].append(("INS", "-", "".join(pending_ins)))
                pending_ins = []
            per_word[w].append((op, e, r))
            k += 1
        if pending_ins and exp_words:
            per_word[exp_words[-1][0]].append(("INS", "-", "".join(pending_ins)))

        print("=" * 100)
        print(f"{base}   frase: {info_w['frase']}   ({ms:.0f} ms)")
        print("  esperado: " + "  ".join(f"{w}=" + "".join(ps) for w, ps in exp_words))
        print("  oído:     " + "".join(rec))
        line = "  veredicto:"
        for w, _ in exp_words:
            ops_w = per_word[w]
            bad = [f"{e}>{r}" if op == "SUB" else (f"-{e}" if op == "DEL" else f"+{r}") for op, e, r in ops_w if op != "BIEN"]
            line += f"  {w}" + ("✓" if not bad else "✗[" + " ".join(bad) + "]")
        print(line)

        if base in planted:
            for w in planted[base]:
                kind, idx = ERROR_PHONES.get((base, w), ("sub", 0))
                ops_w = [o for o in per_word.get(w, []) if o[0] != "INS"]
                ins_w = [o for o in per_word.get(w, []) if o[0] == "INS"]
                if kind == "sub":
                    flagged = idx < len(ops_w) and ops_w[idx][0] != "BIEN"
                elif kind == "ins":
                    flagged = bool(ins_w)
                else:  # syl: algo sobró o cambió al final de la palabra
                    flagged = bool(ins_w) or (ops_w and ops_w[-1][0] != "BIEN")
                (caught if flagged else fixed).append(f"{w}({base[9:15]})")
        else:
            for w, _ in exp_words:
                good_words += 1
                if any(op != "BIEN" for op, _, _ in per_word[w]):
                    good_flags += 1

    tot = len(fixed) + len(caught)
    print("=" * 100)
    print(f"SOBRECORRECCIÓN A NIVEL DE FONEMA: {len(fixed)}/{tot} errores plantados pasaron como BIEN")
    print(f"  arregló: {', '.join(fixed) or '-'}")
    print(f"  atrapó:  {', '.join(caught) or '-'}")
    print(f"PALABRAS BIEN DICHAS con algún fonema marcado: {good_flags}/{good_words} "
          f"({100 * good_flags / max(1, good_words):.0f} %; parte puede ser acento real)")
    print(f"latencia media PC: {ms_total / max(1, n):.0f} ms por grabación")


if __name__ == "__main__":
    main()
