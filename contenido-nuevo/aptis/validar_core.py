"""Valida los bancos del Core de Aptis: gramatica y vocabulario."""
import json,sys,collections
NIV={"A2","B1","B2","C1"}
SUB={"synonym","definition","usage","collocation"}
def main(path):
    d=json.load(open(path,encoding="utf-8")); p=[]; ids=set()
    items=d["items"]
    for it in items:
        w=it.get("id","SIN ID")
        if not it.get("id"): p.append("item sin id")
        elif it["id"] in ids: p.append(f"{w}: id repetido")
        else: ids.add(it["id"])
        if it.get("level") not in NIV: p.append(f"{w}: level invalido {it.get('level')!r}")
        o=it.get("options") or []
        if len(o)!=3: p.append(f"{w}: Aptis usa 3 opciones, tiene {len(o)}")
        if len(set(x.strip().lower() for x in o))!=len(o): p.append(f"{w}: opciones repetidas")
        a=it.get("answer")
        if a not in o: p.append(f"{w}: answer no esta en options -> {a!r}")
        if not str(it.get("why","")).strip(): p.append(f"{w}: falta \"why\" (la explicacion en espanol)")
        k=d["kind"]
        if k=="grammar":
            if "___" not in it.get("text",""): p.append(f"{w}: el texto necesita un hueco ___")
            if it.get("text","").count("___")!=1: p.append(f"{w}: exactamente un hueco")
            if not it.get("point"): p.append(f"{w}: falta \"point\"")
        else:
            s=it.get("sub")
            if s not in SUB: p.append(f"{w}: sub invalido {s!r}")
            if s in ("synonym","definition","collocation") and not it.get("prompt"): p.append(f"{w}: falta prompt")
            if s=="usage" and it.get("text","").count("___")!=1: p.append(f"{w}: usage necesita un hueco ___")
    if p:
        print(f"PROBLEMAS ({len(p)}):"); [print("  -",x) for x in p]; return 1
    c=collections.Counter(i["level"] for i in items)
    extra=collections.Counter(i.get("sub") or i.get("point","") for i in items)
    print(f"OK: {len(items)} items · niveles {dict(c)}")
    if d["kind"]=="vocabulary": print("   subtipos:",dict(collections.Counter(i['sub'] for i in items)))
    return 0
if __name__=="__main__": sys.exit(main(sys.argv[1]))
