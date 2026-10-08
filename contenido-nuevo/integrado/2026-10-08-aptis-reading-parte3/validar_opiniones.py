"""Valida un banco de Reading parte 3 (tipo "opiniones") para el Modo Aptis de Hablo.

Uso: python3 validar_opiniones.py <banco.json> [carpeta de assets/content para ids repetidos]
Imprime "OK ..." o la lista de problemas.
"""
import json, sys, re, os, glob, collections

LV = {"A1", "A2", "B1", "B2"}
PALABRAS = {"A2": (28, 55), "B1": (50, 85), "B2": (62, 100)}  # examen real: ~70-80 por persona (libro oficial de práctica)
PREG = {"A2": 4, "B1": 7, "B2": 7}
ESPANA = re.compile(r"\b(piso|m[oó]vil|ordenador|coger|vale|vosotros|ten[eé]is|zumo|conducir)\b", re.I)


def ngramas(s, n=4):
    w = re.findall(r"[a-z']+", s.lower())
    return {" ".join(w[i:i + n]) for i in range(len(w) - n + 1)}


def main(f, assets=None):
    d = json.load(open(f, encoding="utf-8"))
    P = []
    ids = set()
    lv = collections.Counter()
    externos = set()
    if assets:
        for g in glob.glob(os.path.join(assets, "aptis-*.json")):
            externos |= set(re.findall(r'"id"\s*:\s*"([^"]+)"', open(g, encoding="utf-8").read()))
    for p in d["partes"]:
        for k in ("id", "aptis", "title", "descripcion"):
            if not p.get(k):
                P.append(f"parte: falta {k}")
        for t in p["tareas"]:
            w = t.get("id")
            if w in ids or w in externos:
                P.append(f"{w}: id repetido")
            ids.add(w)
            L = t.get("level")
            if L not in LV:
                P.append(f"{w}: level")
                continue
            lv[L] += 1
            if t.get("tipo") != "opiniones":
                P.append(f"{w}: tipo")
            for k in ("instruccion", "tema", "why"):
                if not str(t.get(k, "")).strip():
                    P.append(f"{w}: falta {k}")
            if ESPANA.search(t.get("why", "")):
                P.append(f"{w}: español de España en why")
            per = t.get("personas", [])
            nombres = [x.get("nombre", "") for x in per]
            if len(per) != 4 or len(set(nombres)) != 4 or any(not n for n in nombres):
                P.append(f"{w}: hacen falta 4 personas con nombres distintos")
            textos = {x["nombre"]: x.get("texto", "") for x in per}
            lo, hi = PALABRAS.get(L, (0, 999))
            for n, tx in textos.items():
                nw = len(tx.split())
                if not (lo <= nw <= hi):
                    P.append(f"{w}: {n} tiene {nw} palabras (nivel {L}: {lo}-{hi})")
            pr, an, ci = t.get("preguntas", []), t.get("answer", []), t.get("citas", [])
            if len(pr) != PREG.get(L) or len(an) != len(pr) or len(ci) != len(pr):
                P.append(f"{w}: preguntas/answer/citas: {len(pr)}/{len(an)}/{len(ci)} (nivel {L} pide {PREG.get(L)})")
                continue
            if len(set(pr)) != len(pr):
                P.append(f"{w}: preguntas repetidas")
            uso = collections.Counter(an)
            for n in nombres:
                if uso[n] == 0:
                    P.append(f"{w}: {n} no es respuesta de ninguna pregunta")
                if uso[n] > 3:
                    P.append(f"{w}: {n} es respuesta de {uso[n]} preguntas (máx 3)")
            for i, (q, a, c) in enumerate(zip(pr, an, ci), 1):
                if a not in textos:
                    P.append(f"{w} p{i}: answer «{a}» no es una persona")
                    continue
                if not q.strip().endswith("?"):
                    P.append(f"{w} p{i}: la pregunta no termina en ?")
                if c not in textos[a]:
                    P.append(f"{w} p{i}: la cita no está textual en el texto de {a}")
                for n, tx in textos.items():
                    if n != a and c in tx:
                        P.append(f"{w} p{i}: la cita también está en el texto de {n}")
                comun = ngramas(q) & ngramas(textos[a])
                if comun:
                    P.append(f"{w} p{i}: la pregunta copia del texto: {sorted(comun)[:2]}")
    print("\n".join(P) if P else f"OK {len(ids)} tareas {dict(sorted(lv.items()))}")


main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)
