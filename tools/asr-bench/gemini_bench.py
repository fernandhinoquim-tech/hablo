"""Compara modelos Gemini en latencia y en criterio de correccion, con los
mensajes reales que el dictado produjo en la sesion de Fero."""
import io, json, sys, time, urllib.request, urllib.error

KEY = io.open(r"C:\Users\ferna\Hablo-modelos\gemini.key", encoding="utf-8").read().strip()
BASE = "https://generativelanguage.googleapis.com/v1beta"

SYSTEM = """You are Emma, a warm English teacher from England. You are role-playing with a Spanish-speaking beginner (level A1).
Situation: You are a barista in a small café. The student is a customer ordering a drink and something to eat.
Rules:
- Stay in character and REPLY to what the student said, as the character would. Write simple English (A1-A2 vocabulary), at most two short sentences, and end with a question or an invitation so the student keeps talking.
- The student's messages come from a speech recognizer, which often mishears a Spanish accent: 'As model please' means 'a small, please', 'Marion' means 'medium', 'thoughts with Buddha' means 'toast with butter'. ALWAYS guess the most likely meaning from the context and answer THAT, in character, confidently. Never say you didn't understand unless it is truly impossible, and even then offer a guess: 'Do you mean ...?'.
- NEVER correct a strange word, a misspelling or a word that does not fit: those are the recognizer's mistakes, not the student's. Only correct GRAMMAR mistakes typical of Spanish speakers that the student clearly produced: verb forms ('he work'), missing articles ('I am doctor'), 'I have 25 years', 'I'm agree', word order, false friends ('actually' for 'currently'). If in doubt, do not correct.
- When you do correct: (1) inside your English reply, say the correct sentence in a friendly way: You can say: '...'. (2) Then, on a separate FINAL line starting with "CORRECCIÓN:", explain it in Spanish in one short sentence (what they said, the correct form, why). Only one correction per turn; if there is no real mistake, add nothing.
- ALWAYS answer with at least one complete English sentence. Vary your wording. If the student changes the subject, follow them naturally and bring the conversation back later.
- Never use lists, emojis, or the word CORRECCIÓN inside the English part. Do not translate your English into Spanish."""

OPENING = "Hi there! Welcome to the café. What can I get you today?"
TURNS = [
    ("As model please", "mal oído: 'a small, please' -> NO corregir"),
    ("I want a coffee with milk and a thoughts with Buddha", "mal oído: toast with butter -> NO corregir"),
    ("I have 25 years and I work in a bank", "error real: I have 25 years -> SÍ corregir"),
    ("What nine is it", "mal oído: what time is it -> NO corregir, responder la hora"),
    ("My sister is doctor, she work in the hospital", "error real: is a doctor / works -> SÍ corregir (uno)"),
]


def chat(model, contents, cfg):
    body = {"system_instruction": {"parts": [{"text": SYSTEM}]}, "contents": contents, "generationConfig": cfg}
    req = urllib.request.Request(f"{BASE}/models/{model}:streamGenerateContent?alt=sse&key={KEY}",
                                 data=json.dumps(body).encode("utf-8"),
                                 headers={"Content-Type": "application/json; charset=utf-8"}, method="POST")
    t0 = time.time()
    first = None
    text = []
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            for raw in r:
                line = raw.decode("utf-8").rstrip("\n")
                if not line.startswith("data:"):
                    continue
                payload = line[5:].strip()
                if not payload:
                    continue
                obj = json.loads(payload)
                for c in obj.get("candidates", []):
                    if c.get("finishReason") not in (None, "STOP"):
                        print("   !! finishReason:", c.get("finishReason"), json.dumps(obj)[:400])
                    for p in c.get("content", {}).get("parts", []):
                        if p.get("text"):
                            if first is None:
                                first = time.time() - t0
                            text.append(p["text"])
        return 200, "".join(text), first, time.time() - t0
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")[:300], None, time.time() - t0


def run(model, cfg):
    print(f"\n===== {model}  cfg={cfg}")
    contents = [{"role": "model", "parts": [{"text": OPENING}]}]
    for user, note in TURNS:
        contents.append({"role": "user", "parts": [{"text": user}]})
        code, text, first, total = chat(model, contents, cfg)
        if code != 200:
            print(f"  HTTP {code}: {text}")
            return
        contents.append({"role": "model", "parts": [{"text": text}]})
        flag = "CORRECCIÓN" in text
        print(f"  [{note}]")
        print(f"  yo:  {user}")
        print(f"  ella ({first:.2f}s / {total:.2f}s){' [corr]' if flag else ''}: {text.strip()}")


models = sys.argv[1:] or ["gemini-3.8-flash"]
for m in models:
    cfg = {"temperature": 0.7, "maxOutputTokens": 300}
    if m.startswith("gemini-3") and not m.endswith("lite"):
        cfg["thinkingConfig"] = {"thinkingBudget": 0}
    run(m, cfg)
