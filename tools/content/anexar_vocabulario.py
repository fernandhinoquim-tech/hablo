# -*- coding: utf-8 -*-
"""Anexa bancos de vocabulario para el contrarreloj (etapa 3).

Entrada: un JSON con "bancos": [ {id, title, level, pares: [{en, es}, ...]}, ... ].
Se añaden a app/src/main/assets/content/vocabulario.json (se crea si no existe).
Un id que ya exista se para; dentro de un banco no puede haber inglés ni
español repetido y hacen falta al menos 3 parejas. checkContent lo vuelve a
revisar al compilar.

    python tools/content/anexar_vocabulario.py contenido-nuevo/vocabulario-a1.json
"""
import io
import json
import os
import sys

RAIZ = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
VOCAB = os.path.join(RAIZ, "app", "src", "main", "assets", "content", "vocabulario.json")


def main(path):
    nuevos = json.load(io.open(path, encoding="utf-8"))["bancos"]
    actual = json.load(io.open(VOCAB, encoding="utf-8")) if os.path.exists(VOCAB) else {
        "_comentario": "Bancos de vocabulario del contrarreloj (etapa 3). Cada banco: id, title, level y pares {en, es} sin repetidos, minimo 3. Los escribe Cowork; se anexan con tools/content/anexar_vocabulario.py.",
        "bancos": []
    }
    ids = {b["id"] for b in actual["bancos"]}
    for b in nuevos:
        for c in ("id", "title", "level", "pares"):
            if not b.get(c):
                sys.exit(f"{b.get('id')}: falta {c}")
        if b["id"] in ids:
            sys.exit(f"{b['id']}: ya existe")
        ens, ess = set(), set()
        for i, p in enumerate(b["pares"]):
            en, es = (p.get("en") or "").strip(), (p.get("es") or "").strip()
            if not en or not es:
                sys.exit(f"{b['id']}: pareja {i + 1} sin en o sin es")
            if en.lower() in ens or es.lower() in ess:
                sys.exit(f"{b['id']}: pareja repetida: {en} / {es}")
            ens.add(en.lower()); ess.add(es.lower())
        if len(b["pares"]) < 3:
            sys.exit(f"{b['id']}: necesita al menos 3 parejas")
        ids.add(b["id"])
        actual["bancos"].append({"id": b["id"], "title": b["title"], "level": b["level"],
                                 "pares": [{"en": p["en"].strip(), "es": p["es"].strip()} for p in b["pares"]]})
    io.open(VOCAB, "w", encoding="utf-8", newline="\n").write(json.dumps(actual, ensure_ascii=False, indent=2) + "\n")
    total = sum(len(b["pares"]) for b in actual["bancos"])
    print(f"ok: {len(nuevos)} bancos anexados -> {len(actual['bancos'])} bancos, {total} parejas")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("uso: anexar_vocabulario.py <vocabulario.json>")
    main(sys.argv[1])
