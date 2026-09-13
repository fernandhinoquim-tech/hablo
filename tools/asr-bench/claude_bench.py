"""Compara Haiku 4.5 y Sonnet 5 como profesora de Hablo, con las frases reales
que el dictado produjo en la sesion de Fero.

Mide: tiempo hasta la primera palabra, tiempo total, tokens (y por tanto coste)
y si corrige lo que debe. Usa HTTP crudo con streaming SSE, igual que lo hara
la app (HttpURLConnection + org.json, sin SDK).

    python tools/asr-bench/claude_bench.py claude-haiku-4-5 claude-sonnet-5
"""
import io, json, sys, time, urllib.request, urllib.error

KEY = io.open(r"C:\Users\ferna\Hablo-modelos\claude.key", encoding="utf-8").read().strip()
URL = "https://api.anthropic.com/v1/messages"
VERSION = "2023-06-01"

# Precio por millon de tokens (USD), leido de la documentacion oficial 2026-09-13.
PRECIO = {
    "claude-haiku-4-5": (1.0, 5.0),
    "claude-sonnet-5": (2.0, 10.0),
    "claude-opus-5": (5.0, 25.0),
}

SYSTEM = """You are Emma, a warm English teacher from England. You are role-playing with a Spanish-speaking beginner (level A1).
Situation: You are a barista in a small cafe. The student is a customer ordering a drink and something to eat.
Rules:
- Stay in character and REPLY to what the student said, as the character would. Write simple English (A1-A2 vocabulary), at most two short sentences, and end with a question or an invitation so the student keeps talking.
- The student's messages come from a speech recognizer, which often mishears a Spanish accent: 'As model please' means 'a small, please', 'Marion' means 'medium', 'thoughts with Buddha' means 'toast with butter', 'What nine is it' means 'what time is it'. ALWAYS guess the most likely meaning from the context and answer THAT, in character, confidently. Never say you didn't understand unless it is truly impossible, and even then offer a guess: 'Do you mean ...?'.
- NEVER correct a strange word, a misspelling or a word that does not fit: those are the recognizer's mistakes, not the student's. Only correct GRAMMAR mistakes typical of Spanish speakers that the student clearly produced: verb forms ('he work'), missing articles ('I am doctor'), 'I have 25 years', 'I'm agree', word order, false friends ('actually' for 'currently'). If in doubt, do not correct.
- When you do correct: (1) inside your English reply, say the correct sentence in a friendly way: You can say: '...'. (2) Then, on a separate FINAL line starting with "CORRECCION:", explain it in Spanish in one short sentence (what they said, the correct form, why). Only one correction per turn; if there is no real mistake, add nothing.
- ALWAYS answer with at least one complete English sentence. Vary your wording. If the student changes the subject, follow them naturally and bring the conversation back later.
- Never use lists, emojis, or the word CORRECCION inside the English part. Do not translate your English into Spanish."""

OPENING = "Hi there! Welcome to the cafe. What can I get you today?"
TURNS = [
    ("As model please", "mal oido: 'a small, please' -> NO corregir"),
    ("I want a coffee with milk and a thoughts with Buddha", "mal oido: toast with butter -> NO corregir"),
    ("I have 25 years and I work in a bank", "error real: I have 25 years -> SI corregir"),
    ("What nine is it", "mal oido: what time is it -> NO corregir, dar la hora"),
    ("My sister is doctor, she work in the hospital", "error real: is a doctor / works -> SI corregir (uno)"),
]


def chat(model, messages, system_cache=True):
    """Una llamada con streaming SSE. Devuelve (texto, ms primera palabra, ms total, uso)."""
    system = [{"type": "text", "text": SYSTEM}]
    if system_cache:
        system[0]["cache_control"] = {"type": "ephemeral"}
    body = {
        "model": model,
        "max_tokens": 400,
        "stream": True,
        "system": system,
        "messages": messages,
    }
    # Sin razonar: en una charla corta pesa mas el segundo de espera.
    if model.startswith("claude-sonnet-5") or model.startswith("claude-opus"):
        body["thinking"] = {"type": "disabled"}
    req = urllib.request.Request(
        URL,
        data=json.dumps(body).encode("utf-8"),
        headers={
            "content-type": "application/json",
            "x-api-key": KEY,
            "anthropic-version": VERSION,
        },
        method="POST",
    )
    t0 = time.time()
    first = None
    out = []
    usage = {}
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            for raw in r:
                line = raw.decode("utf-8").rstrip("\n")
                if not line.startswith("data:"):
                    continue
                obj = json.loads(line[5:].strip())
                t = obj.get("type")
                if t == "content_block_delta" and obj.get("delta", {}).get("type") == "text_delta":
                    if first is None:
                        first = time.time() - t0
                    out.append(obj["delta"]["text"])
                elif t == "message_start":
                    usage.update(obj["message"].get("usage", {}))
                elif t == "message_delta":
                    usage.update(obj.get("usage", {}))
    except urllib.error.HTTPError as e:
        return None, e.code, e.read().decode("utf-8", "replace")[:400], {}
    return "".join(out), first, time.time() - t0, usage


def run(model):
    pin, pout = PRECIO.get(model, (0, 0))
    print(f"\n===== {model}  (${pin}/M entrada, ${pout}/M salida)")
    messages = [{"role": "assistant", "content": OPENING}]
    tot_in = tot_out = tot_cache_w = tot_cache_r = 0
    for user, nota in TURNS:
        messages.append({"role": "user", "content": user})
        text, first, total, usage = chat(model, messages)
        if text is None:
            print(f"  HTTP {first}: {total}")
            return
        messages.append({"role": "assistant", "content": text})
        tot_in += usage.get("input_tokens", 0)
        tot_out += usage.get("output_tokens", 0)
        tot_cache_w += usage.get("cache_creation_input_tokens", 0)
        tot_cache_r += usage.get("cache_read_input_tokens", 0)
        corr = " [corr]" if "CORRECCI" in text.upper() else ""
        print(f"  [{nota}]")
        print(f"  yo:   {user}")
        print(f"  ella  ({first:.2f}s / {total:.2f}s){corr}: {text.strip()}")
    coste = (tot_in * pin + tot_cache_w * pin * 1.25 + tot_cache_r * pin * 0.1 + tot_out * pout) / 1e6
    print(f"  --- {len(TURNS)} turnos: entrada {tot_in} (cache escrito {tot_cache_w}, leido {tot_cache_r}), "
          f"salida {tot_out} -> ${coste:.5f} = ${coste/len(TURNS):.5f} por turno")
    print(f"      a 30 turnos diarios: ${coste/len(TURNS)*30*30:.2f} al mes")


for m in sys.argv[1:] or ["claude-haiku-4-5", "claude-sonnet-5"]:
    run(m)
