# -*- coding: utf-8 -*-
"""Anexa ejercicios a lecciones que ya existen.

Entrada: un JSON {id de lección: [ejercicios]} (como contenido-nuevo/adiciones_a1.json).
Cada ejercicio se AÑADE al final de `exercises` de su lección, con id numerado
a continuación de los que ya tiene (e1..e8 → e9, e10…). No borra ni reescribe
nada. Si un ejercicio ya trae id, se respeta; si el id choca, se para.

    python tools/content/anexar.py contenido-nuevo/adiciones_a1.json
"""
import io
import json
import os
import sys

RAIZ = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CURR = os.path.join(RAIZ, "app", "src", "main", "assets", "content", "curriculum.json")
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from migrar_a1 import escribir, ordenar  # noqa: E402


def main(path):
    adiciones = json.load(io.open(path, encoding="utf-8"))
    curr = json.load(io.open(CURR, encoding="utf-8"))
    lecciones = {l["id"]: l for lv in curr["levels"] for u in lv["units"] for l in u["lessons"]}
    todos = {e["id"] for l in lecciones.values() for e in l["exercises"]}

    faltan = [lid for lid in adiciones if lid not in lecciones]
    if faltan:
        sys.exit("lecciones que no existen: " + ", ".join(faltan))

    anexados = 0
    for lid, nuevos in adiciones.items():
        l = lecciones[lid]
        # El siguiente número: el mayor sufijo eN que ya tenga la lección, + 1.
        n = 0
        for e in l["exercises"]:
            suf = e["id"].rsplit("e", 1)[-1]
            if suf.isdigit():
                n = max(n, int(suf))
        for e in nuevos:
            e = dict(e)
            if "id" not in e:
                n += 1
                e["id"] = f"{lid}e{n}"
            if e["id"] in todos:
                sys.exit(f"{lid}: el id {e['id']} ya existe en el curso")
            todos.add(e["id"])
            l["exercises"].append(ordenar(e))
            anexados += 1

    escribir(CURR, curr)
    total = sum(len(l["exercises"]) for l in lecciones.values())
    print(f"ok: {anexados} ejercicios anexados a {len(adiciones)} lecciones -> {total} ejercicios en total")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("uso: anexar.py <adiciones.json>")
    main(sys.argv[1])
