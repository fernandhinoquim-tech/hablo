# -*- coding: utf-8 -*-
"""A/B del prompt de correccion: saca el prompt REAL de ScreenConversation.kt (git HEAD = viejo,
working tree = nuevo) y prueba turnos correctos que no deben corregirse y errores que si."""
import io, json, re, subprocess, sys, time, urllib.request

KEY = io.open(r"C:\Users\ferna\Hablo-modelos\claude.key", encoding="utf-8").read().strip()
URL = "https://api.anthropic.com/v1/messages"
REPO = r"C:\Users\ferna\AndroidStudioProjects\Hablo"
KT = "app/src/main/java/com/ferolabs/hablo/ScreenConversation.kt"
GIT = r"C:\Users\ferna\AppData\Local\GitHubDesktop\app-3.6.4\resources\app\git\cmd\git.exe"

SC = {"level": "A1",
      "role": "You are chatting with a new student at a language exchange in a park. You are friendly and curious.",
      "targets": ["My name is ...", "I'm from ...", "I'm ... years old", "I'm a ... / I work as a ..."],
      "watch": ["'I have 25 years' → se dice 'I'm 25 years old'", "'My sister is doctor' → hace falta el artículo: 'is a doctor'",
                "'I am agree' → 'I agree', sin am", "'How do you call yourself?' → 'What's your name?'"]}
OPENING = "Hi! I don't think we've met. I'm Mia. What's your name?"

def prompt_from(src):
    body = src[src.index("private fun buildSystemPrompt"):]
    body = body[:body.index("\n}\n")]
    out = []
    for m in re.finditer(r'append(Line)?\((".*?")\)\n', body):
        lit = m.group(2)
        # Kotlin string -> texto
        t = lit[1:-1].replace('\\"', '"')
        t = t.replace("${teacher.name}", "Mia").replace("$origin", "from the United States")
        t = t.replace("${scenario.level}", SC["level"]).replace("${scenario.role}", SC["role"])
        t = t.replace('${scenario.targets.joinToString("; ")}', "; ".join(SC["targets"]))
        t = t.replace('${scenario.watch.joinToString(" | ")}', " | ".join(SC["watch"]))
        t = t.replace("$CORRECTION_MARK", "CORRECCIÓN:")
        assert "$" not in t, t
        out.append(t)
    for m in re.finditer(r'appendLine\(\)\n', body):
        pass
    # Reconstruir respetando appendLine() vacios: mas simple, re-recorrer secuencialmente
    seq = []
    for m in re.finditer(r'append(Line)?\((""|".*?")?\)\n', body):
        if m.group(2) is None:
            seq.append("")
        else:
            seq.append(out.pop(0))
    return "\n".join(seq)

def chat(model, system, user):
    body = {"model": model, "max_tokens": 300, "stream": False,
            "system": [{"type": "text", "text": system}],
            "messages": [{"role": "assistant", "content": OPENING}, {"role": "user", "content": user}]}
    if model.startswith("claude-sonnet-5"):
        body["thinking"] = {"type": "disabled"}
    req = urllib.request.Request(URL, data=json.dumps(body).encode("utf-8"), headers={
        "x-api-key": KEY, "anthropic-version": "2023-06-01", "content-type": "application/json"})
    with urllib.request.urlopen(req, timeout=60) as r:
        d = json.loads(r.read().decode("utf-8"))
    return "".join(b.get("text", "") for b in d["content"])

viejo = subprocess.run([GIT, "show", "HEAD:" + KT], cwd=REPO, capture_output=True).stdout.decode("utf-8")
nuevo = io.open(REPO + "/" + KT, encoding="utf-8").read()
P = {"viejo": prompt_from(viejo), "nuevo": prompt_from(nuevo)}

TURNOS = [
    ("I am forty", False),
    ("My name is Fero and I am forty years old", False),
    ("I'm 40 and I am from Colombia", False),
    ("I do not like coffee, I like tea", False),
    ("I have forty years", True),
    ("My sister is doctor and she work in a bank", True),
    ("I am agree with you", True),
]
MODELOS = ["claude-haiku-4-5", "claude-sonnet-5"]
CORRIDAS = int(sys.argv[1]) if len(sys.argv) > 1 else 3

res = {}
for modelo in MODELOS:
    for version in ("viejo", "nuevo"):
        for turno, debe in TURNOS:
            for k in range(CORRIDAS):
                txt = chat(modelo, P[version], turno)
                corrigio = "CORRECCIÓN:" in txt or "You can say" in txt
                ok = corrigio == debe
                res.setdefault((modelo, version), []).append(ok)
                print(f"{modelo:16s} {version:5s} {'OK ' if ok else 'MAL'} [{turno}] -> {txt.replace(chr(10), ' / ')[:170]}", flush=True)
print()
for (modelo, version), lista in res.items():
    print(f"{modelo:16s} {version:5s}: {sum(lista)}/{len(lista)} turnos bien decididos")
