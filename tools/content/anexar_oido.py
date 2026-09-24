# -*- coding: utf-8 -*-
"""Añade pares mínimos YA MEDIDOS a los bloques de oido.json (y cambia títulos).

Entrada: {"pares": {bloque: [{a, b, frase}, ...]}, "titulos": {bloque: "a / b"}}.
Los pares tienen que haber pasado antes `oir_oido.py` (≥ 3 de 4 voces): este
script no mide, solo añade. Se para si un bloque no existe, si un par ya está
en su bloque, si el bloque pasa de 10 pares o si un título no es un par del
bloque. Escribe con el formato del archivo (sangría 1, salto final).

    python tools/content/anexar_oido.py adiciones.json
"""
import io
import json
import os
import sys

RAIZ = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OIDO = os.path.join(RAIZ, "app", "src", "main", "assets", "content", "oido.json")
MAX = 10


def main(path):
    add = json.load(io.open(path, encoding="utf-8"))
    datos = json.load(io.open(OIDO, encoding="utf-8"))
    bloques = {b["id"]: b for b in datos["bloques"]}
    for bid, pares in add.get("pares", {}).items():
        if bid not in bloques:
            sys.exit(f"el bloque {bid} no existe")
        b = bloques[bid]
        ya = {frozenset((p["a"], p["b"])) for p in b["pares"]}
        for p in pares:
            if set(p) != {"a", "b", "frase"} or "___" not in p["frase"]:
                sys.exit(f"{bid}: par mal formado {p}")
            if frozenset((p["a"], p["b"])) in ya:
                sys.exit(f"{bid}: {p['a']}/{p['b']} ya está")
            ya.add(frozenset((p["a"], p["b"])))
            b["pares"].append(p)
        if len(b["pares"]) > MAX:
            sys.exit(f"{bid}: quedaría con {len(b['pares'])} pares (máximo {MAX})")
    for bid, titulo in add.get("titulos", {}).items():
        b = bloques[bid]
        t = tuple(x.strip() for x in titulo.split("/"))
        if t not in {(p["a"], p["b"]) for p in b["pares"]}:
            sys.exit(f"{bid}: el título {titulo} no es un par del bloque")
        b["title"] = titulo
    io.open(OIDO, "w", encoding="utf-8", newline="\n").write(json.dumps(datos, ensure_ascii=False, indent=1) + "\n")
    print("ok: " + " · ".join(f"{b['id']} {len(b['pares'])}" for b in datos["bloques"]))


if __name__ == "__main__":
    main(sys.argv[1])
