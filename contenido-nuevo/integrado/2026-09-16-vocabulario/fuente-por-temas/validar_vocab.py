"""Valida el banco de vocabulario del contrarreloj.
El fallo grave de un juego de parejas: que dos respuestas sirvan a la vez."""
import json,sys,collections,re
DICT="/tmp/hablo/app/src/main/assets/gop/cmudict.dict"
NIV={"A1","A2","B1"}
# parejas de ingles que son casi sinonimos: no pueden estar en el MISMO tema
SINON=[{"store","shop"},{"movie","film"},{"couch","sofa"},{"trash","garbage"},
 {"flat","apartment"},{"lift","elevator"},{"holiday","vacation"},{"mum","mom"},
 {"begin","start"},{"big","large"},{"small","little"},{"buy","purchase"},
 {"quick","fast"},{"happy","glad"},{"sick","ill"},{"job","work"},{"speak","talk"}]
def known():
    k=set()
    for line in open(DICT,encoding="utf-8",errors="ignore"):
        if not line.startswith(";;;"): k.add(line.split(" ",1)[0].split("(")[0])
    return k
def pal(t): return [w for w in re.sub(r"[^a-z' ]","",t.lower().replace("-"," ")).split() if w]
def main(path):
    d=json.load(open(path,encoding="utf-8")); p=[]; K=known()
    todos=[(t["id"],x) for t in d["temas"] for x in t["pairs"]]
    en=collections.Counter(x["en"].lower() for _,x in todos)
    es=collections.Counter(x["es"].lower() for _,x in todos)
    for w,n in en.items():
        if n>1: p.append(f"ingles repetido en todo el banco: {w!r} ({n} veces)")
    for w,n in es.items():
        if n>1: p.append(f"espanol repetido en todo el banco: {w!r} ({n} veces)")
    for t in d["temas"]:
        ing={x["en"].lower() for x in t["pairs"]}
        for s in SINON:
            if len(s & ing)>1: p.append(f"tema {t['id']}: casi sinonimos juntos {sorted(s & ing)} — en una ronda las dos servirian")
        for x in t["pairs"]:
            if x.get("level") not in NIV: p.append(f"{t['id']}/{x['en']}: level invalido {x.get('level')!r}")
            if not x.get("es","").strip(): p.append(f"{t['id']}/{x['en']}: falta es")
            falta=[w for w in pal(x["en"]) if w not in K]
            if falta: p.append(f"{t['id']}/{x['en']}: fuera de cmudict: {','.join(falta)}")
    if p:
        print(f"PROBLEMAS ({len(p)}):"); [print("  -",x) for x in p[:40]]; return 1
    c=collections.Counter(x["level"] for _,x in todos)
    print(f"OK: {len(d['temas'])} temas · {len(todos)} pares · niveles {dict(sorted(c.items()))}")
    for t in d["temas"]: print(f"   {t['emoji']} {t['id']:12} {len(t['pairs']):3} pares  {t['title']}")
    return 0
if __name__=="__main__": sys.exit(main(sys.argv[1]))
