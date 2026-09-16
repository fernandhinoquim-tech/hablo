# -*- coding: utf-8 -*-
"""Anexa escenarios de conversación a scenarios.json.

Entrada: un JSON con "scenarios": [...] (como contenido-nuevo/escenarios-a2-b1.json).
Cada escenario se AÑADE al final del array; si un id ya existe, se para. No
toca helpCommon ni los escenarios que ya estaban. checkContent valida el
resultado al compilar.

    python tools/content/anexar_escenarios.py contenido-nuevo/escenarios-a2-b1.json
"""
import io
import json
import os
import sys

RAIZ = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SCEN = os.path.join(RAIZ, "app", "src", "main", "assets", "content", "scenarios.json")
CAMPOS = ["id", "title", "emoji", "level", "goalEs", "role", "opening", "targets", "watch", "help"]


def main(path):
    nuevos = json.load(io.open(path, encoding="utf-8"))["scenarios"]
    actual = json.load(io.open(SCEN, encoding="utf-8"))
    ids = {s["id"] for s in actual["scenarios"]}
    for s in nuevos:
        faltan = [c for c in CAMPOS if c != "help" and not s.get(c)]
        if faltan:
            sys.exit(f"{s.get('id')}: faltan {faltan}")
        if s["id"] in ids:
            sys.exit(f"{s['id']}: ya existe")
        ids.add(s["id"])
        actual["scenarios"].append({c: s[c] for c in CAMPOS if c in s})
    io.open(SCEN, "w", encoding="utf-8", newline="\n").write(json.dumps(actual, ensure_ascii=False, indent=2) + "\n")
    niveles = {}
    for s in actual["scenarios"]:
        niveles[s["level"]] = niveles.get(s["level"], 0) + 1
    print(f"ok: {len(nuevos)} escenarios anexados -> {len(actual['scenarios'])} en total " +
          "(" + " · ".join(f"{k}: {v}" for k, v in niveles.items()) + ")")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("uso: anexar_escenarios.py <escenarios.json>")
    main(sys.argv[1])
