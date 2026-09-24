# -*- coding: utf-8 -*-
"""¿Se distinguen los pares de la pantalla de Oído tal como los dice Piper?

Hermano de oir_pares.py, pero para `oido.json` (bloques de pares mínimos con
su propia frase portadora neutra, "Now I say ___ again."). En vez de una tabla
a mano con el fonema distintivo de cada palabra, se calculan los fonemas
esperados de las dos palabras con CMUdict (tabla ARPAbet→IPA de
phoneme_eval.py) y se mira a cuál de las dos se parece más lo que se oyó.

Dos jueces por cada (voz, palabra), porque ninguno es una persona:
1. El modelo de FONEMAS (wav2vec2 L2-ARCTIC, sin modelo de lenguaje): se
   alinea lo transcrito con la secuencia esperada de la frase entera
   (Levenshtein con traza), se lee el tramo que cae sobre la palabra y se
   comprueba que está más cerca (distancia de edición) de la palabra dicha
   que de su pareja. Ciego a la d final (la oye t: ride→ɹaɪt en 4 de 4) y
   flojo con b/v (oye ð) y con las vocales de Grace.
2. PARAKEET 0.6B (dictado de palabras, con modelo de lenguaje): ¿escribe la
   palabra dicha? La portadora es neutra, así que si la escribe es porque la
   oyó. Ciego a lo que su modelo de lenguaje "arregla".
Una palabra cuenta como bien dicha con una voz si la reconoce CUALQUIERA de
los dos; un par pasa con una voz si pasan sus dos palabras; se descarta el
par que no pasa con al menos 3 de las 4 voces (criterio de Cowork, 17-09).
Sin el segundo juez (--sin-parakeet) el modelo de fonemas solo descartaba
la mitad de los 147 pares, casi todo por su propio oído (t/d final, b/v).

    python tools/content/oir_oido.py [oido.json] [--solo bloque-id] [--salida informe.txt] [--sin-parakeet]
"""
import io
import json
import os
import re
import sys

import numpy as np

AQUI = os.path.dirname(os.path.abspath(__file__))
RAIZ = os.path.dirname(os.path.dirname(AQUI))
sys.path.insert(0, AQUI)
sys.path.insert(0, os.path.join(RAIZ, "tools", "asr-bench"))
import oir_pares            # noqa: E402  (tts, resample, Fonemas, VOCES)
import phoneme_eval         # noqa: E402  (expected_phones)
import bench                # noqa: E402  (load_models, words)

OIDO = os.path.join(RAIZ, "app", "src", "main", "assets", "content", "oido.json")
NO_FONEMA = {"[UNK]", "<unk>", "|", " ", "ˌ", "ˈ", "<s>", "</s>"}


class FonemasLista(oir_pares.Fonemas):
    """Como Fonemas.transcribe, pero devuelve la lista de símbolos (t ͡ ʃ son tres)."""

    def simbolos(self, x):
        if self.normalize:
            x = (x - x.mean()) / np.sqrt(x.var() + 1e-7)
        logits = self.sess.run(None, {"wav": x.astype(np.float32)[None, :]})[0][0]
        ids = logits.argmax(-1)
        out, prev = [], None
        for i in ids:
            i = int(i)
            if i != prev and i != self.blank:
                s = self.id2sym[i]
                if s not in NO_FONEMA:
                    out.append(s)
            prev = i
        return out


def alinear(esperado, oido):
    """Levenshtein con traza. Devuelve lista de (op, i, j): op en match/sub/del/ins."""
    n, m = len(esperado), len(oido)
    d = np.zeros((n + 1, m + 1), dtype=np.int32)
    d[:, 0] = np.arange(n + 1)
    d[0, :] = np.arange(m + 1)
    for i in range(1, n + 1):
        for j in range(1, m + 1):
            c = 0 if esperado[i - 1] == oido[j - 1] else 1
            d[i, j] = min(d[i - 1, j - 1] + c, d[i - 1, j] + 1, d[i, j - 1] + 1)
    ops, i, j = [], n, m
    while i > 0 or j > 0:
        if i > 0 and j > 0 and d[i, j] == d[i - 1, j - 1] + (0 if esperado[i - 1] == oido[j - 1] else 1):
            ops.append(("match" if esperado[i - 1] == oido[j - 1] else "sub", i - 1, j - 1))
            i, j = i - 1, j - 1
        elif i > 0 and d[i, j] == d[i - 1, j] + 1:
            ops.append(("del", i - 1, None))
            i -= 1
        else:
            ops.append(("ins", i, j - 1))     # insertado ANTES del esperado i
            j -= 1
    ops.reverse()
    return ops


def tramo(esperado, oido, ini, fin):
    """Símbolos oídos que caen sobre esperado[ini:fin], con las inserciones de los bordes."""
    out = []
    for op, i, j in alinear(esperado, oido):
        if op in ("match", "sub") and ini <= i < fin:
            out.append(oido[j])
        elif op == "ins" and ini <= i <= fin:
            out.append(oido[j])
    return out


def distancia(a, b):
    """Distancia de edición entre dos listas de símbolos."""
    n, m = len(a), len(b)
    d = list(range(m + 1))
    for i in range(1, n + 1):
        prev, d[0] = d[0], i
        for j in range(1, m + 1):
            cur = d[j]
            d[j] = min(d[j] + 1, d[j - 1] + 1, prev + (0 if a[i - 1] == b[j - 1] else 1))
            prev = cur
    return d[m]


def resta(a, b):
    """Multiconjunto a − b, en orden (solo para imprimir el contraste)."""
    b = list(b)
    out = []
    for s in a:
        if s in b:
            b.remove(s)
        else:
            out.append(s)
    return out


def main():
    args = sys.argv[1:]
    salida = None
    solo = None
    if "--salida" in args:
        k = args.index("--salida"); salida = args[k + 1]; del args[k:k + 2]
    if "--solo" in args:
        k = args.index("--solo"); solo = args[k + 1]; del args[k:k + 2]
    con_parakeet = "--sin-parakeet" not in args
    args = [a for a in args if a != "--sin-parakeet"]
    path = args[0] if args else OIDO
    bloques = json.load(io.open(path, encoding="utf-8"))["bloques"]
    if solo:
        bloques = [b for b in bloques if b["id"] == solo]

    fon = FonemasLista()
    simbolos = set(fon.id2sym.values())
    motores = {v: oir_pares.tts(v) for v in oir_pares.VOCES}
    parakeet = None
    if con_parakeet:
        cargados = bench.load_models(os.path.join(RAIZ, "tools", "asr-bench", "models"), only=["parakeet-tdt-0.6b"])
        parakeet = cargados[0][1] if cargados else None
        if parakeet is None:
            print("! no está parakeet-tdt-0.6b en tools/asr-bench/models: solo juzga el modelo de fonemas")
    lineas = []

    def dicta(x):
        st = parakeet.create_stream(); st.accept_waveform(16000, x); parakeet.decode_stream(st)
        return st.result.text.strip()

    # Parakeet escribe con su modelo de lenguaje: "there" sale como "their"/"they're" y
    # "thirteen" como "$13". Se compara por PRONUNCIACIÓN (CMUdict) y las cifras 0-100
    # se pasan a letras antes; si no, dos pares buenos se descartaban por el juez.
    import cmudict
    CMU = cmudict.dict()
    UNIDADES = ["zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
                "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"]
    DECENAS = {20: "twenty", 30: "thirty", 40: "forty", 50: "fifty", 60: "sixty", 70: "seventy", 80: "eighty", 90: "ninety"}

    # Ordinales (24-09): Parakeet escribe "thirteenth" como "13th" y, tras un mes, como
    # "June 13" (así se escriben las fechas en inglés). Se comparan por el NÚMERO: "13th",
    # "13" y "thirteenth" son lo mismo, y siguen distintos de "30th"/"thirtieth", que es
    # el contraste del bloque. Sin esto se descartaban 13th/30th y 14th/40th con las
    # cuatro voces bien oídas.
    ORDINAL = {"first": "one", "second": "two", "third": "three", "fifth": "five", "eighth": "eight",
               "ninth": "nine", "twelfth": "twelve"}

    def cardinal(w):
        if w in ORDINAL:
            return ORDINAL[w]
        if w.endswith("ieth") and w[:-4] + "y" in DECENAS.values():
            return w[:-4] + "y"
        if w.endswith("th") and w[:-2] in UNIDADES:
            return w[:-2]
        return w

    def en_letras(tok):
        m = re.fullmatch(r"(\d+)(st|nd|rd|th)", tok)
        if m:
            tok = m.group(1)
        if not tok.isdigit() or int(tok) > 100:
            return [tok]
        n = int(tok)
        if n < 20:
            return [UNIDADES[n]]
        if n == 100:
            return ["one", "hundred"]
        return [DECENAS[n - n % 10]] + ([UNIDADES[n % 10]] if n % 10 else [])

    def suena_igual(w1, w2):
        if w1 == w2 or cardinal(w1) == cardinal(w2):
            return True
        p1 = {tuple(re.sub(r"\d", "", ph) for ph in pr) for pr in CMU.get(w1, [])}
        p2 = {tuple(re.sub(r"\d", "", ph) for ph in pr) for pr in CMU.get(w2, [])}
        return bool(p1 & p2)

    def escrita(w, texto):
        """¿Parakeet escribió la palabra (o algo que suena igual)?"""
        pal = [t for tok in bench.words(texto.replace("$", " ")) for t in en_letras(tok)]
        objetivo = bench.words(w)
        if not objetivo:
            return False
        return any(all(suena_igual(pal[i + k], objetivo[k]) for k in range(len(objetivo)))
                   for i in range(len(pal) - len(objetivo) + 1))

    def out(s=""):
        print(s, flush=True)
        lineas.append(s)

    def esperado_frase(frase):
        """[(palabra, fonemas)] de la frase; None si CMUdict no la tiene."""
        try:
            return phoneme_eval.expected_phones(frase, simbolos)
        except SystemExit as e:
            out(f"    ! {e}")
            return None

    descartes = []
    total_ok = 0
    total = 0
    for b in bloques:
        out(f"== {b['id']} · {b['title']} · {b['level']} · sound={b['sound']}")
        for p in b["pares"]:
            a, bb, frase = p["a"], p["b"], p["frase"]
            esp = {}
            for w in (a, bb):
                e = esperado_frase(frase.replace("___", w))
                if e is None:
                    esp = None
                    break
                esp[w] = e
            if esp is None:
                descartes.append((b["id"], a, bb, "fuera de CMUdict"))
                continue
            fa = next(ph for w, ph in esp[a] if w == a.lower())
            fb = next(ph for w, ph in esp[bb] if w == bb.lower())
            da, db = resta(fa, fb), resta(fb, fa)
            if fa == fb:
                out(f"  {a}/{bb}: CMUdict les da los MISMOS fonemas ({''.join(fa)}): no se puede medir")
                descartes.append((b["id"], a, bb, f"mismos fonemas en CMUdict: {''.join(fa)}"))
                continue
            voces_ok = 0
            fon_ok = 0
            par_ok = 0
            detalle = []
            for v in oir_pares.VOCES:
                oidos = {}
                ok_voz = True
                ok_fon = True
                ok_par = True
                for w, mio, otro in ((a, fa, fb), (bb, fb, fa)):
                    plano = []
                    ini = fin = None
                    for pw, ph in esp[w]:
                        if pw == w.lower() and ini is None:
                            ini = len(plano)
                            fin = ini + len(ph)
                        plano += ph
                    audio = motores[v].generate(frase.replace("___", w), sid=0, speed=1.0)
                    x = oir_pares.resample(np.asarray(audio.samples, dtype=np.float32), audio.sample_rate)
                    oido = fon.simbolos(x)
                    tr = tramo(plano, oido, ini, fin)
                    # Juez 1: el tramo está más cerca de la palabra dicha que de su pareja.
                    ok1 = distancia(tr, mio) < distancia(tr, otro)
                    # Juez 2: Parakeet escribe la palabra dicha (y no la pareja).
                    texto = dicta(x) if parakeet is not None else ""
                    ok2 = parakeet is not None and escrita(w, texto)
                    oidos[w] = ("".join(tr), ok1, texto, ok2)
                    ok_fon = ok_fon and ok1
                    ok_par = ok_par and ok2
                    ok_voz = ok_voz and (ok1 or ok2)
                voces_ok += ok_voz
                fon_ok += ok_fon
                par_ok += ok_par
                def marca(w):
                    t, ok1, texto, ok2 = oidos[w]
                    return f"{w}→{t}{'✓' if ok1 else '✗'}/«{texto}»{'✓' if ok2 else '✗'}"
                detalle.append(f"{v}: {marca(a)} {marca(bb)}")
            total += 1
            veredicto = "ok" if voces_ok == 4 else ("ok (3/4)" if voces_ok == 3 else f"DESCARTAR {voces_ok}/4")
            if voces_ok >= 3:
                total_ok += 1
            else:
                descartes.append((b["id"], a, bb, f"{voces_ok}/4 (fonemas {fon_ok}/4, parakeet {par_ok}/4) · " + " | ".join(detalle)))
            out(f"  {a}/{bb} [{''.join(da) or '∅'} · {''.join(db) or '∅'}] {veredicto} (fonemas {fon_ok}/4, parakeet {par_ok}/4)   " + " | ".join(detalle))
    out()
    out(f"Pares que pasan (≥ 3 de 4 voces): {total_ok} de {total}")
    if descartes:
        out("DESCARTADOS:")
        for bid, a, bb, por in descartes:
            out(f"  {bid} {a}/{bb}: {por}")
    if salida:
        io.open(salida, "w", encoding="utf-8", newline="\n").write("\n".join(lineas) + "\n")


if __name__ == "__main__":
    main()
