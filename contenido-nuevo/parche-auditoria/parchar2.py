# -*- coding: utf-8 -*-
"""Como parchar.py, pero las claves pueden ser id de EJERCICIO o id de LECCIÓN.
En una lección solo se admite "theory" (objeto completo title/body/trap), que
REEMPLAZA la ficha. En un ejercicio cada campo REEMPLAZA el suyo. Claves "_" = comentarios
(también dentro de cada entrada). Uso: parchar2.py <curriculum.json> <parche.json> [...]"""
import io, json, sys
def aplicar(curr, parche, log):
    lecciones={l["id"]:l for lv in curr["levels"] for u in lv["units"] for l in u["lessons"]}
    ejercicios={e["id"]:e for l in lecciones.values() for e in l["exercises"]}
    for k,campos in parche.items():
        if k.startswith("_"): continue
        campos={c:v for c,v in campos.items() if not c.startswith("_")}
        if k in ejercicios:
            e=ejercicios[k]
            for c,v in campos.items():
                if c in ("id","type"): raise SystemExit(f"{k}: no se cambia {c}")
                log.append(f"{k}.{c}: {json.dumps(e.get(c),ensure_ascii=False)} -> {json.dumps(v,ensure_ascii=False)}")
                e[c]=v
        elif k in lecciones:
            if set(campos)!={"theory"}: raise SystemExit(f"{k}: en una lección solo se parcha theory")
            t=campos["theory"]
            if set(t)!={"title","body","trap"} or not all(str(t[x]).strip() for x in t): raise SystemExit(f"{k}: theory necesita title, body y trap no vacíos")
            log.append(f"{k}.theory reemplazada")
            lecciones[k]["theory"]=t
        else:
            raise SystemExit(f"no existe: {k}")
def _escribir():
    """El escribir() del proyecto (orden de claves, un ejercicio por línea), buscando tools/content hacia arriba."""
    import os
    d=os.path.dirname(os.path.abspath(__file__))
    while d!=os.path.dirname(d):
        t=os.path.join(d,"tools","content")
        if os.path.exists(os.path.join(t,"migrar_a1.py")):
            sys.path.insert(0,t)
            from migrar_a1 import escribir
            return escribir
        d=os.path.dirname(d)
    return None
if __name__=="__main__":
    curr=json.load(io.open(sys.argv[1],encoding="utf-8")); log=[]
    for p in sys.argv[2:]: aplicar(curr, json.load(io.open(p,encoding="utf-8")), log)
    print("\n".join(log)); print(f"ok: {len(log)} cambios")
    salida=sys.argv[1]+".parchado.json"
    esc=_escribir()
    if esc: esc(salida, curr); print(f"escrito con migrar_a1.escribir en {salida}")
    else:
        json.dump(curr, io.open(salida,"w",encoding="utf-8"), ensure_ascii=False, indent=1); print(f"escrito (sin migrar_a1) en {salida}")
