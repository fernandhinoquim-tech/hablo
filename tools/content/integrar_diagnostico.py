#!/usr/bin/env python3
"""Valida e integra el diagnóstico del Modo Aptis (etapa 5).

    python tools/content/integrar_diagnostico.py contenido-nuevo/aptis/diagnostico.json [--integrar]

Sin --integrar solo valida (las mismas reglas que parseDiagnostico en Aptis.kt y
checkContent en build.gradle.kts). Con --integrar copia el archivo a
app/src/main/assets/content/aptis-diagnostico.json y mueve el original a
contenido-nuevo/integrado/<fecha>-aptis-diagnostico/ para que no queden dos
copias vivas.
"""
import io
import json
import os
import re
import shutil
import sys
from datetime import date

NIVELES = {"A2", "B1", "B2"}


def validar(d):
    p = []
    sec_ids, tarea_ids = set(), set()

    def id_nuevo(where, tid):
        if not tid:
            p.append(f"{where}: falta id")
        elif tid in tarea_ids:
            p.append(f"{where}: id repetido {tid}")
        tarea_ids.add(tid)

    def nivel(where, o):
        if o.get("level") not in NIVELES:
            p.append(f"{where}: level {o.get('level')!r} no es A2, B1 ni B2")

    def opciones(where, o):
        opts = o.get("options") or []
        if len(opts) < 2:
            p.append(f"{where}: hacen falta al menos 2 opciones")
        if len(set(opts)) != len(opts):
            p.append(f"{where}: opciones repetidas")
        if o.get("answer") not in opts:
            p.append(f"{where}: answer fuera de options")

    def hueco(where, text):
        if len(str(text).split("___")) != 2:
            p.append(f"{where}: el texto necesita exactamente un hueco ___")

    core_umbral = (d.get("estimacion") or {}).get("core")
    if not isinstance(core_umbral, dict):
        p.append("falta 'estimacion.core'")
    else:
        for lvl in sorted(NIVELES):
            if not re.search(r"\d+ de \d+ en (A2|B1|B2)", str(core_umbral.get(lvl, ""))):
                p.append(f"estimacion.core.{lvl} tiene que decir 'N de M en <nivel>' (dice {core_umbral.get(lvl)!r})")
    secciones = d.get("secciones")
    if not isinstance(secciones, list) or not secciones:
        return p + ["falta 'secciones'"]
    for si, s in enumerate(secciones):
        where_s = f"sección {si + 1}"
        sid = s.get("id", "")
        if not sid or sid in sec_ids:
            p.append(f"{where_s}: id vacío o repetido")
        sec_ids.add(sid)
        for k in ("skill", "title"):
            if not s.get(k):
                p.append(f"{where_s}: falta {k}")
        if not isinstance(s.get("minutos"), int) or s["minutos"] <= 0:
            p.append(f"{where_s}: minutos tiene que ser mayor que 0")
        if sid == "core":
            if not isinstance(s.get("segundos_por_item"), int) or s["segundos_por_item"] <= 0:
                p.append(f"{where_s}: falta segundos_por_item")
            niveles = set()
            for i, it in enumerate(s.get("items") or []):
                w = f"{where_s}, ítem {i + 1}"
                id_nuevo(w, it.get("id")); nivel(w, it); hueco(w, it.get("text")); opciones(w, it)
                niveles.add(it.get("level"))
            for l in NIVELES:
                if l not in niveles:
                    p.append(f"{where_s}: no hay ítems de {l}")
        elif sid == "reading":
            tareas = s.get("tareas") or []
            if not tareas:
                p.append(f"{where_s}: sin tareas")
            for i, t in enumerate(tareas):
                w = f"{where_s}, tarea {i + 1}"
                id_nuevo(w, t.get("id")); nivel(w, t)
                tipo = t.get("tipo")
                if tipo == "completar":
                    hueco(w, t.get("text")); opciones(w, t)
                elif tipo == "ordenar":
                    if not t.get("primera"):
                        p.append(f"{w}: falta primera")
                    des, orden = t.get("desordenadas") or [], t.get("orden") or []
                    if len(des) < 2:
                        p.append(f"{w}: hacen falta al menos 2 frases desordenadas")
                    if len(set(des)) != len(des):
                        p.append(f"{w}: frases repetidas")
                    if sorted(des) != sorted(orden):
                        p.append(f"{w}: 'orden' no es una permutación de 'desordenadas'")
                elif tipo == "titulos":
                    parrafos, titulos, answer = t.get("parrafos") or [], t.get("titulos") or [], t.get("answer") or []
                    if len(parrafos) < 2:
                        p.append(f"{w}: hacen falta al menos 2 párrafos")
                    if len(titulos) <= len(parrafos):
                        p.append(f"{w}: tiene que sobrar al menos un título")
                    if len(set(titulos)) != len(titulos):
                        p.append(f"{w}: títulos repetidos")
                    if len(answer) != len(parrafos):
                        p.append(f"{w}: 'answer' necesita un título por párrafo")
                    if len(set(answer)) != len(answer) or any(a not in titulos for a in answer):
                        p.append(f"{w}: 'answer' con títulos repetidos o fuera de 'titulos'")
                else:
                    p.append(f"{w}: tipo desconocido {tipo!r}")
        elif sid == "listening":
            tareas = s.get("tareas") or []
            if not tareas:
                p.append(f"{where_s}: sin tareas")
            for i, t in enumerate(tareas):
                w = f"{where_s}, tarea {i + 1}"
                id_nuevo(w, t.get("id")); nivel(w, t); opciones(w, t)
                for k in ("audio", "pregunta"):
                    if not t.get(k):
                        p.append(f"{w}: falta {k}")
        elif sid in ("writing", "speaking"):
            tareas = s.get("tareas") or []
            if len(tareas) < 2:
                p.append(f"{where_s}: la estimación por IA necesita al menos 2 tareas")
            seg = "segundos" if sid == "writing" else "hablar_seg"
            for i, t in enumerate(tareas):
                w = f"{where_s}, tarea {i + 1}"
                id_nuevo(w, t.get("id")); nivel(w, t)
                for k in ("prompt_en", "prompt_es"):
                    if not t.get(k):
                        p.append(f"{w}: falta {k}")
                if not t.get("rubrica"):
                    p.append(f"{w}: falta la rúbrica")
                if not isinstance(t.get(seg), int) or t[seg] <= 0:
                    p.append(f"{w}: falta {seg}")
        else:
            p.append(f"{where_s}: sección desconocida {sid!r}")
    return p


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    src = sys.argv[1]
    d = json.load(io.open(src, encoding="utf-8"))
    problemas = validar(d)
    if problemas:
        print("NO PASA:")
        for x in problemas:
            print("  -", x)
        sys.exit(1)
    tareas = sum(len(s.get("items") or s.get("tareas") or []) for s in d["secciones"])
    print(f"OK: {len(d['secciones'])} secciones, {tareas} tareas, {d.get('duracion_min')} min")
    if "--integrar" in sys.argv:
        raiz = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
        dst = os.path.join(raiz, "app", "src", "main", "assets", "content", "aptis-diagnostico.json")
        shutil.copyfile(src, dst)
        carpeta = os.path.join(raiz, "contenido-nuevo", "integrado", f"{date.today().isoformat()}-aptis-diagnostico")
        os.makedirs(carpeta, exist_ok=True)
        shutil.move(src, os.path.join(carpeta, os.path.basename(src)))
        print(f"copiado a {dst} y archivado en {carpeta}")


if __name__ == "__main__":
    main()
