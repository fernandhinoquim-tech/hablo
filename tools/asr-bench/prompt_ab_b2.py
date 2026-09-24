# -*- coding: utf-8 -*-
"""A/B del estilo por nivel en escenarios B2 (24-09): regla vieja ("simple English
A1-A2, two short sentences") contra la nueva ("natural B2 ... at most three sentences").
Mide: si corrige cuando debe y solo cuando debe, y cuanto/como habla."""
import io, json, re, sys, urllib.request

KEY = io.open(r"C:\Users\ferna\Hablo-modelos\claude.key", encoding="utf-8").read().strip()
URL = "https://api.anthropic.com/v1/messages"
PD = r"C:\Users\ferna\AndroidStudioProjects\Hablo\app\build\prompts"
NUEVA = "Speak natural B2 English at normal speed (idioms and complex sentences are fine; do not simplify for a beginner), at most three sentences"
VIEJA = "Write simple English (A1-A2 vocabulary), at most two short sentences"

TURNOS = {
    "defender_investigacion": [
        ("In a nutshell, my research looks at how small farmers in Colombia adapt to droughts, and what this means in practice is better advice for local governments.", False),
        ("My investigation is about water use in rural areas and the results depend of the rainfall.", True),
        ("That's a fair question. One limitation is that our sample is small, which is why we are adding two more regions next year.", False),
    ],
    "negociar_b2": [
        ("I understand your position, but we would need a better price if we're committing to a two-year contract.", False),
        ("I'm agree with the price, but the delivery must to be faster.", True),
    ],
    "debate_b2": [
        ("I see your point, although I'd argue that remote work actually makes people more productive.", False),
        ("Actually I am working in a university, so I have many experience with remote teams.", True),
    ],
}
MODELOS = ["claude-sonnet-5", "claude-haiku-4-5"]
CORRIDAS = int(sys.argv[1]) if len(sys.argv) > 1 else 2


def chat(model, system, opening, user):
    body = {"model": model, "max_tokens": 400, "stream": False,
            "system": [{"type": "text", "text": system}],
            "messages": [{"role": "assistant", "content": opening}, {"role": "user", "content": user}]}
    if model.startswith("claude-sonnet-5"):
        body["thinking"] = {"type": "disabled"}
    req = urllib.request.Request(URL, data=json.dumps(body).encode("utf-8"), headers={
        "x-api-key": KEY, "anthropic-version": "2023-06-01", "content-type": "application/json"})
    with urllib.request.urlopen(req, timeout=90) as r:
        d = json.loads(r.read().decode("utf-8"))
    return "".join(b.get("text", "") for b in d["content"])


def ingles(txt):
    return "\n".join(l for l in txt.splitlines() if not l.strip().startswith("CORRECCI")).strip()


res = {}
for esc, turnos in TURNOS.items():
    nuevo = io.open(f"{PD}\\{esc}.txt", encoding="utf-8").read()
    assert NUEVA in nuevo
    P = {"viejo": nuevo.replace(NUEVA, VIEJA), "nuevo": nuevo}
    opening = io.open(f"{PD}\\{esc}.opening.txt", encoding="utf-8").read().strip()
    for modelo in MODELOS:
        for version in ("viejo", "nuevo"):
            for turno, debe in turnos:
                for k in range(CORRIDAS):
                    txt = chat(modelo, P[version], opening, turno)
                    corrigio = "CORRECCIÓN:" in txt or "You can say" in txt
                    en = ingles(txt)
                    palabras = len(re.findall(r"[A-Za-z']+", en))
                    largas = len([w for w in re.findall(r"[A-Za-z]+", en) if len(w) >= 8])
                    r = res.setdefault((modelo, version), {"ok": 0, "n": 0, "pal": 0, "largas": 0})
                    r["ok"] += (corrigio == debe); r["n"] += 1; r["pal"] += palabras; r["largas"] += largas
                    if k == 0:
                        print(f"\n[{modelo} · {version} · {esc}] ALUMNO: {turno}\n  -> {txt.strip()}")

print("\n==== RESUMEN")
for (m, v), r in res.items():
    print(f"{m:18} {v:6} decisiones bien {r['ok']}/{r['n']} · palabras/resp {r['pal']/r['n']:.0f} · palabras largas/resp {r['largas']/r['n']:.1f}")
