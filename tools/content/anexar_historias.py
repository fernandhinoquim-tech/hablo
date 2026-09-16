# -*- coding: utf-8 -*-
"""Anexa tandas de historias (etapa 4) a app/src/main/assets/content/historias.json.

Entrada: un JSON con "tandas": [ {id, level, title, historias: [...]}, ... ]
(como contenido-nuevo/historias/historias.json). Cada historia: id, level,
title, titleEs, text (6-10 frases), glosario (>= 2 pares en/es), preguntas (3,
con options de 3 y answer entre ellas), retell {prompt_es, pistas (>= 2)}, trap.
Una tanda tiene entre 3 y 12 historias (donde el efecto es máximo). Un id de
tanda o de historia que ya exista se para. checkContent lo revisa al compilar.

    python tools/content/anexar_historias.py contenido-nuevo/historias/historias.json
"""
import io
import json
import os
import sys

RAIZ = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DEST = os.path.join(RAIZ, "app", "src", "main", "assets", "content", "historias.json")


def main(path):
    nuevas = json.load(io.open(path, encoding="utf-8"))["tandas"]
    actual = json.load(io.open(DEST, encoding="utf-8")) if os.path.exists(DEST) else {
        "_comentario": "Historias cortas con preguntas y retell (etapa 4). Tandas de 3 a 12; el retell NO es opcional. Las escribe Cowork; se anexan con tools/content/anexar_historias.py.",
        "tandas": []
    }
    ids_t = {t["id"] for t in actual["tandas"]}
    ids_h = {h["id"] for t in actual["tandas"] for h in t["historias"]}
    for t in nuevas:
        if t["id"] in ids_t:
            sys.exit(f"tanda {t['id']}: ya existe")
        if not 3 <= len(t["historias"]) <= 12:
            sys.exit(f"tanda {t['id']}: {len(t['historias'])} historias (3-12)")
        for h in t["historias"]:
            if h["id"] in ids_h:
                sys.exit(f"historia {h['id']}: ya existe")
            for c in ("id", "level", "title", "titleEs", "text", "glosario", "preguntas", "retell", "trap"):
                if not h.get(c):
                    sys.exit(f"{h.get('id')}: falta {c}")
            if not 6 <= len(h["text"]) <= 10:
                sys.exit(f"{h['id']}: {len(h['text'])} frases (6-10)")
            if len(h["preguntas"]) != 3:
                sys.exit(f"{h['id']}: 3 preguntas")
            for q in h["preguntas"]:
                if len(q["options"]) != 3 or q["answer"] not in q["options"]:
                    sys.exit(f"{h['id']}: pregunta mal formada: {q.get('q')}")
            if not h["retell"].get("prompt_es") or len(h["retell"].get("pistas", [])) < 2:
                sys.exit(f"{h['id']}: retell incompleto")
            if len(h["glosario"]) < 2:
                sys.exit(f"{h['id']}: al menos 2 palabras de glosario")
            ids_h.add(h["id"])
        ids_t.add(t["id"])
        actual["tandas"].append({"id": t["id"], "level": t["level"], "title": t["title"], "historias": t["historias"]})
    io.open(DEST, "w", encoding="utf-8", newline="\n").write(json.dumps(actual, ensure_ascii=False, indent=1) + "\n")
    total = sum(len(t["historias"]) for t in actual["tandas"])
    print(f"ok: {len(nuevas)} tandas anexadas -> {len(actual['tandas'])} tandas, {total} historias")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("uso: anexar_historias.py <historias.json>")
    main(sys.argv[1])
