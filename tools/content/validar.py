"""Valida curriculum.json con las mismas reglas que checkContent (Gradle) y
Content.kt. Escrito por Claude Cowork para revisar el A1; sirve para revisar
cualquier lote nuevo antes de integrarlo:

    python tools/content/validar.py app/src/main/assets/content/curriculum.json

Diferencia con checkContent: aqui "tip" es obligatorio (el contenido nuevo lo
trae siempre); en la app es opcional porque 32 ejercicios viejos no lo tienen."""
import json, sys, os, re

DICT = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), "app", "src", "main", "assets", "gop", "cmudict.dict")
SOUNDS = {"sh","th","h","v","ed","final","es","rl","general"}
TYPES  = {"listen","translate","build","type","speak","write","cloze","shadow","minimalPair"}

def known_words():
    k = set()
    with open(DICT, encoding="utf-8", errors="ignore") as f:
        for line in f:
            if line.startswith(";;;"): continue
            w = line.split(" ", 1)[0]
            k.add(w.split("(")[0])
    return k

CONTRACCIONES = [("i'm","i am"),("you're","you are"),("we're","we are"),("they're","they are"),
    ("isn't","is not"),("aren't","are not"),("wasn't","was not"),("weren't","were not"),
    ("don't","do not"),("doesn't","does not"),("didn't","did not"),("can't","can not"),("cannot","can not"),
    ("couldn't","could not"),("won't","will not"),("wouldn't","would not"),("shouldn't","should not"),
    ("i'll","i will"),("you'll","you will"),("he'll","he will"),("she'll","she will"),("it'll","it will"),
    ("we'll","we will"),("they'll","they will"),("i've","i have"),("you've","you have"),("we've","we have"),
    ("they've","they have"),("let's","let us")]

def suelta(text):
    """Como normalizeAnswer() en la app, y ademas expande contracciones: I'm = I am."""
    t = text.lower().replace("\u2019", "'")
    t = "".join(c for c in t if c.isalnum() or c in " '")
    t = " " + " ".join(t.split()) + " "
    for corta, larga in CONTRACCIONES:
        t = t.replace(" " + corta + " ", " " + larga + " ")
    return t.strip()

def spoken(text):
    t = text.lower().replace("’", "'").replace("-", " ")
    t = "".join(c for c in t if c.isalnum() or c in " '")
    return [w for w in t.split(" ") if w.strip()]

def validate(path):
    p = []
    d = json.load(open(path, encoding="utf-8"))
    known = known_words()
    lids, eids = set(), set()
    n_les = n_ex = 0
    for lv in d["levels"]:
        for u in lv["units"]:
            for l in u["lessons"]:
                n_les += 1
                lid = l.get("id","?")
                if lid in lids: p.append(f"leccion {lid}: id repetido")
                lids.add(lid)
                th = l.get("theory")
                if not isinstance(th, dict):
                    p.append(f"leccion {lid}: falta \"theory\"")
                else:
                    for k in ("title","body","trap"):
                        if not str(th.get(k,"")).strip():
                            p.append(f"leccion {lid}: theory.{k} vacio")
                for i, e in enumerate(l.get("exercises",[]), 1):
                    n_ex += 1
                    w = f"leccion {lid}, ejercicio {i}"
                    eid = e.get("id")
                    if not eid: p.append(f"{w}: falta \"id\"")
                    elif eid in eids: p.append(f"{w}: id repetido ({eid})")
                    else: eids.add(eid)
                    t = e.get("type")
                    if t not in TYPES: p.append(f"{w}: tipo \"{t}\" desconocido"); continue
                    if t in ("listen","translate"):
                        opts = e.get("options") or []
                        if len(opts) < 2: p.append(f"{w}: menos de 2 opciones")
                        if len(set(opts)) != len(opts): p.append(f"{w}: opciones repetidas")
                        a = e.get("answer")
                        if not isinstance(a, str): p.append(f"{w}: \"answer\" debe ser texto, no {type(a).__name__}")
                        elif a not in opts: p.append(f"{w}: answer no esta entre las opciones -> {a!r}")
                        if t == "listen":
                            if not e.get("audio"): p.append(f"{w}: falta \"audio\"")
                            elif e.get("audio") != a: p.append(f"{w}: en listen, audio y answer deben ser iguales")
                        else:
                            if not e.get("es"): p.append(f"{w}: falta \"es\"")
                    elif t == "build":
                        a = e.get("answer","")
                        if not a: p.append(f"{w}: falta \"answer\"")
                        if not e.get("es"): p.append(f"{w}: falta \"es\"")
                        base = set(spoken(a))
                        for x in e.get("extra",[]):
                            if x.lower().strip(".,!?") in base:
                                p.append(f"{w}: extra \"{x}\" ya esta en la respuesta")
                        if len(set(e.get("extra",[]))) != len(e.get("extra",[])):
                            p.append(f"{w}: palabras extra repetidas")
                    elif t == "type":
                        if not e.get("audio"): p.append(f"{w}: falta \"audio\"")
                        if not e.get("meaning"): p.append(f"{w}: falta \"meaning\"")
                    elif t in ("write", "cloze"):
                        a = e.get("answer", "")
                        if not isinstance(a, str) or not a.strip(): p.append(f"{w}: falta \"answer\"")
                        if not e.get("es"): p.append(f"{w}: falta \"es\"")
                        acc = e.get("accept", [])
                        if not isinstance(acc, list): p.append(f"{w}: \"accept\" debe ser una lista")
                        else:
                            vistas = {suelta(a)} if isinstance(a, str) else set()
                            for x in acc:
                                if not str(x).strip(): p.append(f"{w}: \"accept\" tiene una entrada vacia")
                                elif suelta(x) in vistas: p.append(f"{w}: \"accept\" repite la respuesta o se repite -> {x!r}")
                                else: vistas.add(suelta(x))
                        if t == "cloze":
                            txt = e.get("text", "")
                            if txt.count("___") != 1: p.append(f"{w}: \"text\" tiene que tener exactamente un hueco ___ (tiene {txt.count('___')})")
                    elif t == "shadow":
                        txt = e.get("text", "")
                        if not txt: p.append(f"{w}: falta \"text\"")
                        miss = [x for x in spoken(txt) if x not in known]
                        if miss: p.append(f"{w}: fuera de cmudict: {', '.join(miss)}")
                    elif t == "minimalPair":
                        opts = e.get("options") or []
                        a = e.get("answer")
                        if len(opts) < 2: p.append(f"{w}: menos de 2 opciones")
                        if len(set(opts)) != len(opts): p.append(f"{w}: opciones repetidas")
                        if a not in opts: p.append(f"{w}: answer no esta entre las opciones -> {a!r}")
                        for x in opts:
                            if " " in str(x).strip(): p.append(f"{w}: las opciones de un par minimo son palabras sueltas -> {x!r}")
                            miss = [y for y in spoken(str(x)) if y not in known]
                            if miss: p.append(f"{w}: fuera de cmudict: {', '.join(miss)}")
                        sent = e.get("sentence")
                        if sent and isinstance(a, str) and a.lower() not in sent.lower():
                            p.append(f"{w}: \"sentence\" no contiene la palabra {a!r}")
                    elif t == "speak":
                        s = e.get("sound")
                        if s is None: p.append(f"{w}: falta \"sound\"")
                        elif s not in SOUNDS: p.append(f"{w}: sound \"{s}\" no existe")
                        txt = e.get("text","")
                        if not txt: p.append(f"{w}: falta \"text\"")
                        miss = [x for x in spoken(txt) if x not in known]
                        if miss: p.append(f"{w}: fuera de cmudict: {', '.join(miss)}")
                    if not str(e.get("tip","")).strip():
                        p.append(f"{w}: falta \"tip\"")
    return p, n_les, n_ex

if __name__ == "__main__":
    probs, nl, ne = validate(sys.argv[1])
    if probs:
        print(f"PROBLEMAS ({len(probs)}):")
        for x in probs: print("  -", x)
        sys.exit(1)
    print(f"OK: {nl} lecciones, {ne} ejercicios, todo valido")
