# -*- coding: utf-8 -*-
"""Anexa un nivel completo a levels[] de curriculum.json.

Entrada: un JSON con un objeto "level" (como contenido-nuevo/a2-nivel-completo.json).
Si ya existe un nivel con ese id, se para: los niveles no se pisan.

    python tools/content/anexar_nivel.py contenido-nuevo/a2-nivel-completo.json
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
    nuevo = json.load(io.open(path, encoding="utf-8"))["level"]
    curr = json.load(io.open(CURR, encoding="utf-8"))
    if any(lv["id"] == nuevo["id"] for lv in curr["levels"]):
        sys.exit(f"el nivel {nuevo['id']} ya existe en curriculum.json")
    ids = {e["id"] for lv in curr["levels"] for u in lv["units"] for l in u["lessons"] for e in l["exercises"]}
    lids = {l["id"] for lv in curr["levels"] for u in lv["units"] for l in u["lessons"]}
    for u in nuevo["units"]:
        for l in u["lessons"]:
            if l["id"] in lids:
                sys.exit(f"la lección {l['id']} ya existe")
            for e in l["exercises"]:
                if e["id"] in ids:
                    sys.exit(f"el ejercicio {e['id']} ya existe")
                ids.add(e["id"])
            l["exercises"] = [ordenar(e) for e in l["exercises"]]
    curr["levels"].append(nuevo)
    escribir(CURR, curr)
    lecciones = sum(len(u["lessons"]) for lv in curr["levels"] for u in lv["units"])
    ejercicios = sum(len(l["exercises"]) for lv in curr["levels"] for u in lv["units"] for l in u["lessons"])
    print(f"ok: nivel {nuevo['id']} anexado -> {len(curr['levels'])} niveles, {lecciones} lecciones, {ejercicios} ejercicios")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("uso: anexar_nivel.py <nivel.json>")
    main(sys.argv[1])
