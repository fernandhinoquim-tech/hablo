"""Valida las historias cortas (etapa 4)."""
import json,sys,re,collections
DICT="/tmp/hablo/app/src/main/assets/gop/cmudict.dict"
def known():
    k=set()
    for l in open(DICT,encoding="utf-8",errors="ignore"):
        if not l.startswith(";;;"): k.add(l.split(" ",1)[0].split("(")[0])
    return k
def pal(t): return [w for w in re.sub(r"[^a-z' ]","",t.lower().replace("-"," ")).split() if w]
def main(path):
    d=json.load(open(path,encoding="utf-8")); p=[]; K=known(); ids=set()
    for tanda in d["tandas"]:
        n=len(tanda["historias"])
        if not 3<=n<=12: p.append(f"tanda {tanda['id']}: {n} historias (el efecto maximo esta entre 3 y 12)")
        for h in tanda["historias"]:
            w=h.get("id","SIN ID")
            if w in ids: p.append(f"{w}: id repetido")
            ids.add(w)
            if not 6<=len(h["text"])<=10: p.append(f"{w}: {len(h['text'])} frases (se pidio 6-10)")
            for c in ("title","titleEs","trap"):
                if not str(h.get(c,"")).strip(): p.append(f"{w}: falta {c}")
            if len(h.get("preguntas",[]))!=3: p.append(f"{w}: hacen falta 3 preguntas")
            for i,q in enumerate(h.get("preguntas",[]),1):
                o=q.get("options") or []
                if len(o)!=3: p.append(f"{w} p{i}: 3 opciones")
                if len(set(o))!=len(o): p.append(f"{w} p{i}: opciones repetidas")
                if q.get("answer") not in o: p.append(f"{w} p{i}: answer fuera de options")
            r=h.get("retell") or {}
            if not r.get("prompt_es"): p.append(f"{w}: falta el retell (es la mitad del efecto)")
            if len(r.get("pistas",[]))<2: p.append(f"{w}: el retell necesita al menos 2 pistas")
            for g in h.get("glosario",[]):
                falta=[x for x in pal(g["en"]) if x not in K]
                if falta: p.append(f"{w}: glosario fuera de cmudict: {','.join(falta)}")
            if len(h.get("glosario",[]))<2: p.append(f"{w}: al menos 2 palabras al mazo")
    if p:
        print(f"PROBLEMAS ({len(p)}):"); [print("  -",x) for x in p[:30]]; return 1
    tot=sum(len(t["historias"]) for t in d["tandas"])
    print(f"OK: {len(d['tandas'])} tandas · {tot} historias")
    for t in d["tandas"]:
        pal_=sum(len(' '.join(h['text']).split()) for h in t["historias"])//len(t["historias"])
        print(f"   {t['id']:10} {t['level']} · {len(t['historias'])} historias · ~{pal_} palabras cada una")
    return 0
if __name__=="__main__": sys.exit(main(sys.argv[1]))
