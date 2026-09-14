"""Valida curriculum.json con las mismas reglas que checkContent (Gradle) y
Content.kt. Escrito por Claude Cowork para revisar el A1; sirve para revisar
cualquier lote nuevo antes de integrarlo:

    python tools/content/validar.py app/src/main/assets/content/curriculum.json

Diferencia con checkContent: aqui "tip" es obligatorio (el contenido nuevo lo
trae siempre); en la app es opcional porque 32 ejercicios viejos no lo tienen."""
import json, sys, os, re

DICT = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), "app", "src", "main", "assets", "gop", "cmudict.dict")
SOUNDS = {"sh","th","h","v","ed","final","es","rl","general"}
TYPES  = {"listen","translate","build","type","speak"}

def known_words():
    k = set()
    with open(DICT, encoding="utf-8", errors="ignore") as f:
        for line in f:
            if line.startswith(";;;"): continue
            w = line.split(" ", 1)[0]
            k.add(w.split("(")[0])
    return k

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
