# -*- coding: utf-8 -*-
"""Inserta unidades NUEVAS dentro de un nivel que ya existe, en una posición.

Entrada: uno o más JSON {"insertar": [{"despues_de": "<id de unidad>", "unit": {...}}]}.
La unidad entra justo después de "despues_de", en el mismo nivel. Se para si un id
de unidad, lección o ejercicio ya existe. Escribe EN SITIO con migrar_a1.escribir.

    python anexar_unidades.py app/src/main/assets/content/curriculum.json nuevo-a1u10-u11.json ...
"""
import io, json, os, sys

def _tools():
    d = os.path.dirname(os.path.abspath(__file__))
    while d != os.path.dirname(d):
        t = os.path.join(d, "tools", "content")
        if os.path.exists(os.path.join(t, "migrar_a1.py")):
            sys.path.insert(0, t)
            from migrar_a1 import escribir, ordenar
            return escribir, ordenar
        d = os.path.dirname(d)
    sys.exit("no encuentro tools/content/migrar_a1.py")

def main(curr_path, archivos):
    escribir, ordenar = _tools()
    curr = json.load(io.open(curr_path, encoding="utf-8"))
    uids = {u["id"] for lv in curr["levels"] for u in lv["units"]}
    lids = {l["id"] for lv in curr["levels"] for u in lv["units"] for l in u["lessons"]}
    eids = {e["id"] for lv in curr["levels"] for u in lv["units"] for l in u["lessons"] for e in l["exercises"]}
    for a in archivos:
        for ins in json.load(io.open(a, encoding="utf-8"))["insertar"]:
            u = ins["unit"]
            nivel = next((lv for lv in curr["levels"] if any(x["id"] == ins["despues_de"] for x in lv["units"])), None)
            if nivel is None: sys.exit(f"no existe la unidad {ins['despues_de']}")
            if u["id"] in uids: sys.exit(f"la unidad {u['id']} ya existe")
            for l in u["lessons"]:
                if l["id"] in lids: sys.exit(f"la lección {l['id']} ya existe")
                lids.add(l["id"])
                for e in l["exercises"]:
                    if e["id"] in eids: sys.exit(f"el ejercicio {e['id']} ya existe")
                    eids.add(e["id"])
                l["exercises"] = [ordenar(e) for e in l["exercises"]]
            pos = [x["id"] for x in nivel["units"]].index(ins["despues_de"])
            nivel["units"].insert(pos + 1, u)
            uids.add(u["id"])
            print(f"{u['id']} insertada después de {ins['despues_de']} ({len(u['lessons'])} lecciones)")
    escribir(curr_path, curr)
    n = sum(len(l["exercises"]) for lv in curr["levels"] for u in lv["units"] for l in u["lessons"])
    print(f"ok: {len(lids)} lecciones, {n} ejercicios")

if __name__ == "__main__":
    if len(sys.argv) < 3: sys.exit("uso: anexar_unidades.py <curriculum.json> <nuevo.json> [...]")
    main(sys.argv[1], sys.argv[2:])
