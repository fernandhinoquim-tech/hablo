# -*- coding: utf-8 -*-
"""Quita de curriculum.json los `accept` que, con las contracciones igualadas, repiten
la respuesta o un accept anterior del mismo ejercicio (misma regla que checkContent
y validar2.py: sueltaEstricta). Hizo falta el 17-09 al añadir must've/should've/
would've/could've/might've/hadn't/hasn't/haven't a CONTRACCIONES: 214 accept de B1
pasaron a ser duplicados exactos. Se conserva el primero de cada forma.

    python tools/content/dedupe_accept.py [--aplicar]
"""
import io
import json
import os
import sys

RAIZ = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CURR = os.path.join(RAIZ, "app", "src", "main", "assets", "content", "curriculum.json")
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from validar2 import suelta  # noqa: E402
from migrar_a1 import escribir  # noqa: E402


def main():
    aplicar = "--aplicar" in sys.argv
    curr = json.load(io.open(CURR, encoding="utf-8"))
    quitados = 0
    for lv in curr["levels"]:
        for u in lv["units"]:
            for l in u["lessons"]:
                for e in l["exercises"]:
                    acc = e.get("accept")
                    # type: la respuesta es el audio (como en validar2.py)
                    base = e.get("audio") if e.get("type") == "type" else e.get("answer")
                    if not acc or base is None:
                        continue
                    vistas = {suelta(str(base))}
                    nuevos = []
                    for a in acc:
                        k = suelta(a)
                        if k in vistas:
                            quitados += 1
                            print(f"{e['id']}: fuera «{a}»")
                            continue
                        vistas.add(k)
                        nuevos.append(a)
                    e["accept"] = nuevos
    print(f"{quitados} accept duplicados")
    if aplicar:
        escribir(CURR, curr)
        print("escrito", CURR)


if __name__ == "__main__":
    main()
