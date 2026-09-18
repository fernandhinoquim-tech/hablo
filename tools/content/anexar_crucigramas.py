# -*- coding: utf-8 -*-
"""Suma crucigramas a app/src/main/assets/content/crucigramas.json (lista "crucigramas").

Entrada: un JSON con "crucigramas" (los genera Cowork con generar.py contra el
vocabulario.json ya ampliado; validar_cruci.py y checkContent los revisan). Un id
que ya exista se para. Cada rejilla queda en una línea, como las de Cowork.

    python tools/content/anexar_crucigramas.py contenido-nuevo/vocab-b1/crucigramas-b1.json
"""
import io
import json
import os
import sys

RAIZ = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DEST = os.path.join(RAIZ, "app", "src", "main", "assets", "content", "crucigramas.json")


def main(path):
    nuevos = json.load(io.open(path, encoding="utf-8"))["crucigramas"]
    actual = json.load(io.open(DEST, encoding="utf-8"))
    ids = {c["id"] for c in actual["crucigramas"]}
    for c in nuevos:
        if c["id"] in ids:
            sys.exit(f"el crucigrama {c['id']} ya existe")
        ids.add(c["id"])
    actual["crucigramas"].extend(nuevos)
    lineas = ["{", f'  "_comentario": {json.dumps(actual.get("_comentario", ""), ensure_ascii=False)},', '  "crucigramas": [']
    for i, c in enumerate(actual["crucigramas"]):
        lineas.append("    " + json.dumps(c, ensure_ascii=False) + ("," if i < len(actual["crucigramas"]) - 1 else ""))
    lineas += ["  ]", "}", ""]
    io.open(DEST, "w", encoding="utf-8", newline="\n").write("\n".join(lineas))
    print(f"ok: {len(nuevos)} crucigramas anexados -> {len(actual['crucigramas'])}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("uso: anexar_crucigramas.py <crucigramas.json>")
    main(sys.argv[1])
