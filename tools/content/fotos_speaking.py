# -*- coding: utf-8 -*-
"""Convierte las fotos de Aptis Speaking a WebP y las conecta a sus tareas.

Entrada: una carpeta con las fotos que generó Fero con Gemini (por defecto
`fotos-speaking/` en la raíz del repo), con los nombres del prompt de Cowork:
`s-009.png` para la parte 2 (describir una foto) y `s-017_1.png` +
`s-017_2.png` para la parte 3 (comparar dos). Vale .png, .jpg, .jpeg o .webp.

Para cada tarea de `aptis-speaking.json` que trae "foto":
  1. busca su foto (o sus dos fotos) en la carpeta; si falta una, se para;
  2. la reduce a 1280 px de ancho (en el teléfono se ve a lo ancho de la
     pantalla, ~1330 px; más no se nota) y la guarda como WebP en
     app/src/main/assets/images/speaking/, bajando la calidad hasta que pese
     menos de 380 KB (checkContent pone el tope en 400);
  3. escribe "imagenes": ["images/speaking/s-009.webp"] justo después de "foto".
     Es la ruta que exigen checkContent y Aptis.kt (^images/...).

Es idempotente: correrlo otra vez rehace los WebP y deja el JSON igual.

    python tools/content/fotos_speaking.py [carpeta]
"""
import io
import json
import os
import sys

from PIL import Image

RAIZ = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(RAIZ, "app", "src", "main", "assets")
JSON = os.path.join(ASSETS, "content", "aptis-speaking.json")
DEST = os.path.join(ASSETS, "images", "speaking")
ANCHO = 1280
TOPE = 380 * 1024
EXTS = (".png", ".jpg", ".jpeg", ".webp")


def buscar(carpeta, nombre):
    for ext in EXTS:
        p = os.path.join(carpeta, nombre + ext)
        if os.path.exists(p):
            return p
    return None


def convertir(origen, destino):
    im = Image.open(origen).convert("RGB")
    if im.width > ANCHO:
        im = im.resize((ANCHO, round(im.height * ANCHO / im.width)), Image.LANCZOS)
    for calidad in range(82, 40, -6):
        buf = io.BytesIO()
        im.save(buf, "WEBP", quality=calidad, method=6)
        if buf.tell() <= TOPE:
            open(destino, "wb").write(buf.getvalue())
            return im.size, calidad, buf.tell()
    sys.exit(f"{origen}: ni con calidad 46 baja de {TOPE // 1024} KB")


def con_imagenes(tarea, rutas):
    """La misma tarea con "imagenes" justo después de "foto" (el orden de claves se conserva)."""
    nueva = {}
    for k, v in tarea.items():
        if k == "imagenes":
            continue
        nueva[k] = v
        if k == "foto":
            nueva["imagenes"] = rutas
    return nueva


def main(carpeta):
    datos = json.load(io.open(JSON, encoding="utf-8"))
    os.makedirs(DEST, exist_ok=True)
    total = 0
    for parte in datos["partes"]:
        comparar = parte["aptis"].startswith("Parte 3")
        for i, t in enumerate(parte["tareas"]):
            if not t.get("foto"):
                continue
            nombres = [t["id"] + "_1", t["id"] + "_2"] if comparar else [t["id"]]
            rutas = []
            for n in nombres:
                origen = buscar(carpeta, n)
                if origen is None:
                    sys.exit(f"falta la foto {n} ({' / '.join(n + e for e in EXTS)}) en {carpeta}")
                size, calidad, peso = convertir(origen, os.path.join(DEST, n + ".webp"))
                print(f"{n:9} {os.path.basename(origen):14} -> {n}.webp {size[0]}x{size[1]} calidad {calidad} · {peso // 1024} KB")
                rutas.append(f"images/speaking/{n}.webp")
                total += 1
            parte["tareas"][i] = con_imagenes(t, rutas)
    # Mismo formato que el archivo de Cowork (sangría 2, sin salto final): así el diff son solo las "imagenes".
    texto = json.dumps(datos, ensure_ascii=False, indent=2)
    io.open(JSON, "w", encoding="utf-8", newline="\n").write(texto)
    print(f"ok: {total} fotos en assets/images/speaking/ y aptis-speaking.json conectado")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(RAIZ, "fotos-speaking"))
