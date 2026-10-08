"""Valida un banco de pista de Aptis (Writing, Reading, Listening o Speaking)."""
import json,sys,collections
NIV={"A1","A2","B1","B2"}
def main(path):
    d=json.load(open(path,encoding="utf-8")); p=[]; ids=set()
    for parte in d["partes"]:
        for c in ("id","title","aptis","descripcion"):
            if not str(parte.get(c,"")).strip(): p.append(f"parte {parte.get('id')}: falta {c}")
        for t in parte["tareas"]:
            w=t.get("id","SIN ID")
            if w in ids: p.append(f"{w}: id repetido")
            ids.add(w)
            if t.get("level") not in NIV: p.append(f"{w}: level invalido {t.get('level')!r}")
            for c in ("promptEn","promptEs"):
                if not str(t.get(c,"")).strip(): p.append(f"{w}: falta {c}")
            r=t.get("rubrica") or []
            if len(r)<3: p.append(f"{w}: la rubrica necesita al menos 3 criterios (tiene {len(r)})")
            for x in r:
                if not x.strip().endswith("?"): p.append(f"{w}: criterio sin signo de pregunta: {x!r}")
            if parte.get("palabras") and not t.get("palabras"): p.append(f"{w}: falta el rango de palabras")
            if not t.get("segundos"): p.append(f"{w}: falta el tiempo en segundos")
    if p:
        print(f"PROBLEMAS ({len(p)}):"); [print("  -",x) for x in p[:25]]; return 1
    tot=sum(len(x["tareas"]) for x in d["partes"])
    print(f"OK: {len(d['partes'])} partes · {tot} tareas")
    for parte in d["partes"]:
        c=collections.Counter(t["level"] for t in parte["tareas"])
        mins=sum(t["segundos"] for t in parte["tareas"])//60
        print(f"   {parte['id']:8} {parte['aptis']:22} {len(parte['tareas'])} tareas {dict(sorted(c.items()))}  ~{mins} min de banco")
    return 0
if __name__=="__main__": sys.exit(main(sys.argv[1]))
