# -*- coding: utf-8 -*-
"""¿Se distinguen los pares mínimos tal como los dice Piper?

Aviso de Cowork (2026-09-14): "can/can't y los pares de consonante final
dependen de cómo los sintetice Piper. Óyelos una vez en el teléfono; si la voz
se come la t final, hay que reproducir la sentence en vez de la palabra suelta".

Esto es oírlos con un oído medible. Se sintetiza cada palabra de cada
`minimalPair` con las cuatro voces de la app y se le pide al modelo de FONEMAS
(wav2vec2 L2-ARCTIC, el mismo que juzga la pronunciación en el teléfono) que
transcriba lo que suena, sin modelo de lenguaje: así se ve si la /t/ de
"can't" está o no, si "three" lleva θ y "tree" lleva t, etc.

El dictado de palabras (Moonshine) NO sirve para esto: con una palabra suelta
de medio segundo y sin contexto inventa ("can" -> "ten", "think" -> "famed").
Se probó primero y se descartó.

Medido el 2026-09-14 con los 12 pares de A1: con la palabra SUELTA el fonema
distintivo aparece en 63 de 96 (voz × palabra); con la portadora "The word is
{w}." en 78 de 96, y con "Listen: {w}." en 79. Piper con una palabra de medio
segundo es inestable (Emma dijo "bed" como un balbuceo de un segundo); con
una frase corta delante, no. Por eso la app reproduce "The word is {w}." y
este script mide igual. Veredicto por palabra: el fonema distintivo tiene que
aparecer con al menos tres de las cuatro voces. Hallazgo aparte: "live" es
heterónimo y Piper lo lee /laɪv/ (vivo, en directo): el par live/leave no
sirve con estas voces; hay que usar otro (sit/seat, fill/feel…).

    python tools/content/oir_pares.py
"""
import io
import json
import os
import sys

import numpy as np
import onnxruntime as ort
import sherpa_onnx

RAIZ = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CURR = os.path.join(RAIZ, "app", "src", "main", "assets", "content", "curriculum.json")
TTS = os.path.join(RAIZ, "app", "src", "main", "assets", "tts")
FONEMAS = os.path.join(RAIZ, "tools", "asr-bench", "models", "phoneme", "wav2vec2-large-xlsr-53-l2-arctic-phoneme")
VOCES = ["emma", "sophie", "mia", "grace"]
PORTADORA = "The word is {w}."   # la misma que usa ScreenLesson para minimalPair

# Qué fonema (o secuencia) distingue cada palabra de su pareja. Se escribe con
# los símbolos del modelo (IPA de L2-ARCTIC; la africada tʃ sale como "t͡ʃ").
# Si una palabra no está aquí, solo se imprime lo que se oyó.
DISTINTIVO = {
    "ship": "ɪ", "sheep": "i", "cheap": "t͡ʃ",
    "bad": "æ", "bed": "ɛ",
    "live": "lɪ", "leave": "li",
    "watch": "t͡ʃ", "wash": "ʃ",
    "three": "θ", "tree": "t",
    "chair": "t͡ʃ", "share": "ʃ",
    "think": "θ", "sink": "s",
    "much": "ʌ", "match": "æ",
    "can": "n", "can't": "nt",
    "walked": "wɔ|wɑ", "worked": "wɚ|wɝ",
    "want": "ɑ", "won't": "oʊ",
    # A2
    "since": "sɪn", "sense": "sɛn",
    "ban": "b", "van": "v",
    "walk": "wɔ|wɑ", "work": "wɚ|wɝ",
}


def tts(voz):
    return sherpa_onnx.OfflineTts(
        sherpa_onnx.OfflineTtsConfig(
            model=sherpa_onnx.OfflineTtsModelConfig(
                vits=sherpa_onnx.OfflineTtsVitsModelConfig(
                    model=os.path.join(TTS, voz, "model.onnx"),
                    tokens=os.path.join(TTS, voz, "tokens.txt"),
                    data_dir=os.path.join(TTS, "espeak-ng-data"),
                ),
                num_threads=2,
            )
        )
    )


def resample(x, sr_in, sr_out=16000):
    if sr_in == sr_out:
        return x
    n = int(len(x) * sr_out / sr_in)
    return np.interp(np.linspace(0, len(x) - 1, n), np.arange(len(x)), x).astype(np.float32)


class Fonemas:
    def __init__(self):
        info = json.load(open(os.path.join(FONEMAS, "export.json"), encoding="utf-8"))
        vocab = json.load(open(os.path.join(FONEMAS, "vocab.json"), encoding="utf-8"))
        self.id2sym = {int(v): k for k, v in vocab.items()} if all(isinstance(v, int) for v in vocab.values()) \
            else {int(k): v for k, v in vocab.items()}
        self.blank = next(i for i, s in self.id2sym.items() if s in ("[PAD]", "<pad>"))
        self.normalize = info.get("do_normalize", True)
        so = ort.SessionOptions()
        so.intra_op_num_threads = 4
        self.sess = ort.InferenceSession(os.path.join(FONEMAS, "model.int8.onnx"), so, providers=["CPUExecutionProvider"])

    def transcribe(self, x):
        if self.normalize:
            x = (x - x.mean()) / np.sqrt(x.var() + 1e-7)
        logits = self.sess.run(None, {"wav": x.astype(np.float32)[None, :]})[0][0]
        ids = logits.argmax(-1)
        out, prev = [], None
        for i in ids:
            i = int(i)
            if i != prev and i != self.blank:
                s = self.id2sym[i]
                if s not in ("[UNK]", "<unk>", "|", " ", "ˌ", "ˈ"):
                    out.append(s)
            prev = i
        return "".join(out)


def main():
    c = json.load(io.open(CURR, encoding="utf-8"))
    pares = [(l["id"], e["options"], e["answer"], e.get("sentence"))
             for lv in c["levels"] for u in lv["units"] for l in u["lessons"]
             for e in l["exercises"] if e["type"] == "minimalPair"]
    fon = Fonemas()
    motores = {v: tts(v) for v in VOCES}
    # Transcripción de la portadora sola, para quitarla del principio.
    base = PORTADORA.replace("{w}", "").replace(".", "").strip() + "."
    prefijo = {}
    for v in VOCES:
        a = motores[v].generate(base, sid=0, speed=1.0)
        prefijo[v] = fon.transcribe(resample(np.asarray(a.samples, dtype=np.float32), a.sample_rate))
    print(f"{'lección':8s} {'palabra':7s} {'busca':5s} " + " ".join(f"{v:>12s}" for v in VOCES) + "   veredicto")
    problemas = []
    for lid, opciones, _, sentence in pares:
        for palabra in opciones:
            clave = DISTINTIVO.get(palabra)
            oidos = []
            for v in VOCES:
                a = motores[v].generate(PORTADORA.replace("{w}", palabra), sid=0, speed=1.0)
                t = fon.transcribe(resample(np.asarray(a.samples, dtype=np.float32), a.sample_rate))
                k = 0
                while k < min(len(prefijo[v]), len(t)) and prefijo[v][k] == t[k]:
                    k += 1
                oidos.append(t[max(0, k - 2):])
            if clave is None:
                veredicto = "(sin clave)"
            else:
                tiene = sum(1 for o in oidos if any(alt in o for alt in clave.split("|")))
                veredicto = "ok" if tiene == 4 else ("ok (3/4)" if tiene == 3 else f"FALLA {tiene}/4")
                if tiene < 3:
                    problemas.append((lid, palabra, clave, oidos, sentence))
            print(f"{lid:8s} {palabra:7s} {(clave or '-'):5s} " + " ".join(f"{o[:12]:>12s}" for o in oidos) + f"   {veredicto}")
    print()
    if problemas:
        print("Palabras cuyo fonema distintivo NO aparece en lo que dice Piper (reproducir la sentence, o cambiar el par):")
        for lid, palabra, clave, oidos, sentence in problemas:
            print(f"  {lid} «{palabra}» busca «{clave}» -> {oidos} | sentence: {sentence}")
    else:
        print("Todos los pares suenan con su fonema distintivo en al menos tres de las cuatro voces.")


if __name__ == "__main__":
    main()
