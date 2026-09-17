# -*- coding: utf-8 -*-
"""Reemplaza parejas en bancos que ya existen de vocabulario.json (por banco + inglés anterior).
Uso: python parchar_vocab.py <vocabulario.json> <parche-vocab.json>   (escribe en sitio, mismo formato que anexar_vocabulario.py)"""
import io, json, sys
def main(vpath, ppath):
    v = json.load(io.open(vpath, encoding="utf-8")); p = json.load(io.open(ppath, encoding="utf-8"))
    bancos = {b["id"]: b for b in v["bancos"]}
    for r in p["reemplazos"]:
        b = bancos.get(r["banco"]) or sys.exit(f"no existe el banco {r['banco']}")
        par = next((x for x in b["pares"] if x["en"] == r["en_antes"]), None) or sys.exit(f"{r['banco']}: no está {r['en_antes']!r}")
        par["en"], par["es"] = r["en"], r["es"]
        print(f"{r['banco']}: {r['en_antes']} -> {r['en']} = {r['es']}")
    io.open(vpath, "w", encoding="utf-8", newline="\n").write(json.dumps(v, ensure_ascii=False, indent=2) + "\n")
if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
