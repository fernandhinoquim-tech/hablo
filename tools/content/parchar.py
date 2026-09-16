# -*- coding: utf-8 -*-
"""Aplica un parche a ejercicios que ya existen, por id.

Entrada: un JSON {id de ejercicio: {campo: valor, ...}} (como
contenido-nuevo/parche-accept.json). Cada campo del parche REEMPLAZA el del
ejercicio (por ejemplo "accept" entero, o "answer"). Las claves que empiezan
por "_" son comentarios y se ignoran. No toca nada que no esté en el parche.

    python tools/content/parchar.py contenido-nuevo/parche-accept.json
"""
import io
import json
import os
import sys

RAIZ = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CURR = os.path.join(RAIZ, "app", "src", "main", "assets", "content", "curriculum.json")
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from migrar_a1 import escribir  # noqa: E402


def main(path):
    parche = json.load(io.open(path, encoding="utf-8"))
    curr = json.load(io.open(CURR, encoding="utf-8"))
    ejercicios = {e["id"]: e for lv in curr["levels"] for u in lv["units"] for l in u["lessons"] for e in l["exercises"]}

    cambios = {k: v for k, v in parche.items() if not k.startswith("_")}
    faltan = [eid for eid in cambios if eid not in ejercicios]
    if faltan:
        sys.exit("ejercicios que no existen: " + ", ".join(faltan))

    for eid, campos in cambios.items():
        e = ejercicios[eid]
        for campo, valor in campos.items():
            antes = e.get(campo)
            e[campo] = valor
            print(f"{eid}.{campo}: {json.dumps(antes, ensure_ascii=False)} -> {json.dumps(valor, ensure_ascii=False)}")

    escribir(CURR, curr)
    print(f"ok: {len(cambios)} ejercicios parchados")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("uso: parchar.py <parche.json>")
    main(sys.argv[1])
