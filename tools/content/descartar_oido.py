# -*- coding: utf-8 -*-
"""Quita de oido.json los pares que oir_oido.py descartó y escribe el informe para Cowork.

    python tools/content/descartar_oido.py <informe de oir_oido.py> [--aplicar]

Sin --aplicar solo imprime qué quitaría y cuántos pares quedan por bloque.
Con --aplicar reescribe app/src/main/assets/content/oido.json (un bloque se
queda con lo que pase; si baja de 4 pares se avisa y NO se toca: eso lo
decide Cowork) y deja el informe en DESCARTES-OIDO.md al lado del informe.
"""
import io
import json
import os
import re
import sys

RAIZ = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OIDO = os.path.join(RAIZ, "app", "src", "main", "assets", "content", "oido.json")
MINIMO = 4


def main():
    args = sys.argv[1:]
    aplicar = "--aplicar" in args
    args = [a for a in args if a != "--aplicar"]
    informe = args[0]
    lineas = io.open(informe, encoding="utf-8").read().splitlines()
    k = lineas.index("DESCARTADOS:") if "DESCARTADOS:" in lineas else len(lineas)
    descartes = {}
    for l in lineas[k + 1:]:
        m = re.match(r"\s+(\S+) (\S+)/(\S+): (.*)", l)
        if m:
            descartes.setdefault(m.group(1), []).append((m.group(2), m.group(3), m.group(4)))
    d = json.load(io.open(OIDO, encoding="utf-8"))
    salida = ["# Pares de Oído descartados (medidos con `oir_oido.py`, 17-09-2026)", "",
              "Criterio: un par pasa con una voz si sus DOS palabras las reconoce el modelo de fonemas "
              "(tramo alineado más cerca de la palabra dicha que de su pareja) O Parakeet 0.6B (escribe la palabra); "
              "se descarta el que no pasa con al menos 3 de las 4 voces. Es el oído de dos modelos, no de una persona.", ""]
    total = 0
    for b in d["bloques"]:
        malos = descartes.get(b["id"], [])
        if not malos:
            continue
        quedan = [p for p in b["pares"] if not any(p["a"] == a and p["b"] == bb for a, bb, _ in malos)]
        nota = "" if len(quedan) >= MINIMO else f"  ⚠ quedarían {len(quedan)} (< {MINIMO}): NO se toca, hay que reponer antes"
        print(f"{b['id']:22} {len(b['pares'])} → {len(quedan)} pares{nota}")
        salida.append(f"## {b['id']} · {b['title']} ({len(b['pares'])} → {len(quedan)} pares)")
        for a, bb, por in malos:
            salida.append(f"- **{a} / {bb}**: {por}")
        salida.append("")
        total += len(malos)
        if aplicar and len(quedan) >= MINIMO:
            b["pares"] = quedan
    print(f"{total} pares descartados")
    if aplicar:
        io.open(OIDO, "w", encoding="utf-8", newline="\n").write(json.dumps(d, ensure_ascii=False, indent=1) + "\n")
        dest = os.path.join(os.path.dirname(informe), "DESCARTES-OIDO.md")
        io.open(dest, "w", encoding="utf-8", newline="\n").write("\n".join(salida) + "\n")
        print(f"escrito {OIDO} y {dest}")


if __name__ == "__main__":
    main()
