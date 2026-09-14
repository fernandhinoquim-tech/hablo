# -*- coding: utf-8 -*-
"""Migra curriculum.json al esquema v2 e integra el A1 completo.

Esquema v2 (2026-09-14):
  - cada ejercicio lleva "id" único en todo el curso: <lección>e<n>  (a1u1l1e3)
  - en listen/translate, "answer" es el TEXTO de la opción correcta, no su índice
  - cada lección lleva "theory": {"title", "body", "trap"}, los tres obligatorios

Qué hace, en orden:
  1. A los 55 ejercicios existentes les pone id y convierte answer a texto.
  2. Inserta las 8 fichas de contenido-nuevo/teoria-lecciones-existentes.json.
  3. Anexa las 6 unidades de contenido-nuevo/a1-unidades-nuevas.json después
     de a1u3, con emoji y subtítulo para la pantalla de inicio.
  4. Escribe curriculum.json con un ejercicio por línea (fácil de revisar).

Es idempotente: si un ejercicio ya tiene id o answer en texto, lo deja igual.
"""
import io
import json
import os
import sys

RAIZ = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CURR = os.path.join(RAIZ, "app", "src", "main", "assets", "content", "curriculum.json")
NUEVO = os.path.join(RAIZ, "contenido-nuevo", "a1-unidades-nuevas.json")
TEORIA = os.path.join(RAIZ, "contenido-nuevo", "teoria-lecciones-existentes.json")

# Emoji y subtítulo por unidad nueva (la pantalla de inicio los muestra).
PORTADAS = {
    "a1u4": ("⏰", "Rutinas, do/does y dónde va «siempre»"),
    "a1u5": ("🏠", "There is/are, artículos y in/on/at"),
    "a1u6": ("🏃", "Presente continuo, y this/that/these/those"),
    "a1u7": ("🛍️", "Contar, pedir con would like y usar can"),
    "a1u8": ("📅", "Was/were, la -ed, irregulares y did"),
    "a1u9": ("✈️", "Going to, will y comparar"),
}

ORDEN_EJERCICIO = ["id", "type", "audio", "es", "text", "sound", "options", "answer", "extra", "meaning", "tip"]


def ordenar(e):
    """Mismo orden de claves en todos los ejercicios: id primero, tip al final."""
    out = {}
    for k in ORDEN_EJERCICIO:
        if k in e:
            out[k] = e[k]
    for k in e:
        if k not in out:
            out[k] = e[k]
    return out


def migrar_ejercicio(lid, i, e):
    e = dict(e)
    e.setdefault("id", f"{lid}e{i}")
    if e["type"] in ("listen", "translate") and isinstance(e.get("answer"), int):
        e["answer"] = e["options"][e["answer"]]
    return ordenar(e)


def escribir(path, curr):
    """JSON legible: estructura con sangría, cada ejercicio en UNA línea."""
    def j(x):
        return json.dumps(x, ensure_ascii=False)

    out = []
    out.append("{")
    out.append(f'  "version": {j(curr.get("version", 2))},')
    out.append('  "levels": [')
    for li, lv in enumerate(curr["levels"]):
        out.append("    {")
        for k in ("id", "title", "goal"):
            if k in lv:
                out.append(f'      "{k}": {j(lv[k])},')
        out.append('      "units": [')
        for ui, u in enumerate(lv["units"]):
            out.append("        {")
            for k in ("id", "emoji", "title", "subtitle"):
                if k in u:
                    out.append(f'          "{k}": {j(u[k])},')
            out.append('          "lessons": [')
            for lei, l in enumerate(u["lessons"]):
                out.append("            {")
                out.append(f'              "id": {j(l["id"])},')
                out.append(f'              "title": {j(l["title"])},')
                th = l["theory"]
                out.append('              "theory": {')
                out.append(f'                "title": {j(th["title"])},')
                out.append(f'                "body": {j(th["body"])},')
                out.append(f'                "trap": {j(th["trap"])}')
                out.append("              },")
                out.append('              "exercises": [')
                exs = l["exercises"]
                for ei, e in enumerate(exs):
                    coma = "," if ei < len(exs) - 1 else ""
                    out.append(f"                {j(ordenar(e))}{coma}")
                out.append("              ]")
                out.append("            }" + ("," if lei < len(u["lessons"]) - 1 else ""))
            out.append("          ]")
            out.append("        }" + ("," if ui < len(lv["units"]) - 1 else ""))
        out.append("      ]")
        out.append("    }" + ("," if li < len(curr["levels"]) - 1 else ""))
    out.append("  ]")
    out.append("}")
    io.open(path, "w", encoding="utf-8", newline="\n").write("\n".join(out) + "\n")


def main():
    curr = json.load(io.open(CURR, encoding="utf-8"))
    teoria = json.load(io.open(TEORIA, encoding="utf-8"))
    nuevo = json.load(io.open(NUEVO, encoding="utf-8"))

    # 1 y 2: las lecciones que ya existen
    migrados = 0
    for lv in curr["levels"]:
        for u in lv["units"]:
            for l in u["lessons"]:
                if "theory" not in l:
                    if l["id"] not in teoria:
                        sys.exit(f"la lección {l['id']} no tiene ficha en {TEORIA}")
                    l["theory"] = teoria[l["id"]]
                l["exercises"] = [migrar_ejercicio(l["id"], i + 1, e) for i, e in enumerate(l["exercises"])]
                migrados += len(l["exercises"])

    # 3: las unidades nuevas, después de a1u3
    a1 = curr["levels"][0]
    existentes = {u["id"] for u in a1["units"]}
    anexadas = 0
    for u in nuevo["units"]:
        if u["id"] in existentes:
            continue
        emoji, sub = PORTADAS.get(u["id"], ("📘", ""))
        unidad = {"id": u["id"], "emoji": emoji, "title": u["title"], "subtitle": sub, "lessons": u["lessons"]}
        a1["units"].append(unidad)
        anexadas += 1

    curr["version"] = 2
    escribir(CURR, curr)
    total = sum(len(l["exercises"]) for lv in curr["levels"] for u in lv["units"] for l in u["lessons"])
    lecciones = sum(len(u["lessons"]) for lv in curr["levels"] for u in lv["units"])
    print(f"ok: {migrados} ejercicios migrados, {anexadas} unidades anexadas -> {lecciones} lecciones, {total} ejercicios")


if __name__ == "__main__":
    main()
