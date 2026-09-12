"""
Exporta un reconocedor de FONEMAS wav2vec2 (Hugging Face) a ONNX y lo
cuantiza a int8, para poder correrlo en el PC (phoneme_eval.py) y, si vale la
pena, en el teléfono con el mismo onnxruntime que trae sherpa-onnx.

Uso:
  python tools/asr-bench/phoneme_export.py mrrubino/wav2vec2-large-xlsr-53-l2-arctic-phoneme
  python tools/asr-bench/phoneme_export.py vitouphy/wav2vec2-xls-r-300m-timit-phoneme

Deja en tools/asr-bench/models/phoneme/<nombre>/: model.onnx, model.int8.onnx,
vocab.json y export.json (tamaños, tiempos, normalización esperada).

torch y transformers solo hacen falta aquí, en el PC. La app no los usa.
"""

import json
import os
import sys
import time

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_ROOT = os.path.join(HERE, "models", "phoneme")


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    name = sys.argv[1]
    short = name.split("/")[-1]
    out = os.path.join(OUT_ROOT, short)
    os.makedirs(out, exist_ok=True)

    import torch
    from transformers import Wav2Vec2ForCTC, AutoConfig

    print(f"descargando/cargando {name} ...")
    t0 = time.time()
    model = Wav2Vec2ForCTC.from_pretrained(name).eval()
    cfg = AutoConfig.from_pretrained(name)
    params = sum(p.numel() for p in model.parameters())
    print(f"  {params / 1e6:.0f} M parámetros, {time.time() - t0:.0f}s")

    # vocab: id -> símbolo
    from transformers import AutoTokenizer
    try:
        tok = AutoTokenizer.from_pretrained(name)
        vocab = tok.get_vocab()
    except Exception:
        import urllib.request
        vocab = json.loads(urllib.request.urlopen(
            f"https://huggingface.co/{name}/raw/main/vocab.json").read().decode("utf-8"))
    id2sym = {int(i): s for s, i in vocab.items()}
    with open(os.path.join(out, "vocab.json"), "w", encoding="utf-8") as f:
        json.dump({str(k): v for k, v in sorted(id2sym.items())}, f, ensure_ascii=False, indent=0)

    # ¿el procesador normaliza la onda (media 0, varianza 1)? Hay que hacer lo
    # mismo en la app antes de llamar al modelo.
    do_normalize = True
    try:
        from transformers import Wav2Vec2FeatureExtractor
        fe = Wav2Vec2FeatureExtractor.from_pretrained(name)
        do_normalize = bool(fe.do_normalize)
    except Exception:
        pass

    # Exportar: entrada [1, T] float32 (onda 16 kHz), salida logits [1, T', V]
    class Wrapper(torch.nn.Module):
        def __init__(self, m):
            super().__init__()
            self.m = m

        def forward(self, wav):
            return self.m(wav).logits

    wrapper = Wrapper(model)
    dummy = torch.randn(1, 16000 * 3)
    fp32 = os.path.join(out, "model.onnx")
    print("exportando a ONNX ...")
    t0 = time.time()
    try:
        torch.onnx.export(
            wrapper, (dummy,), fp32,
            input_names=["wav"], output_names=["logits"],
            dynamic_axes={"wav": {0: "b", 1: "t"}, "logits": {0: "b", 1: "frames"}},
            opset_version=17, dynamo=False,
        )
    except TypeError:
        torch.onnx.export(
            wrapper, (dummy,), fp32,
            input_names=["wav"], output_names=["logits"],
            dynamic_axes={"wav": {0: "b", 1: "t"}, "logits": {0: "b", 1: "frames"}},
            opset_version=17,
        )
    print(f"  {time.time() - t0:.0f}s, {os.path.getsize(fp32) / 1e6:.0f} MB")
    # Si el exportador dejó pesos externos, se juntan en un solo archivo.
    try:
        import onnx
        m = onnx.load(fp32)
        onnx.save_model(m, fp32, save_as_external_data=False)
    except Exception as e:
        print("  (no se pudo consolidar:", e, ")")

    print("cuantizando a int8 ...")
    t0 = time.time()
    from onnxruntime.quantization import quantize_dynamic, QuantType
    int8 = os.path.join(out, "model.int8.onnx")
    quantize_dynamic(fp32, int8, weight_type=QuantType.QUInt8)
    print(f"  {time.time() - t0:.0f}s, {os.path.getsize(int8) / 1e6:.0f} MB")

    # Latencia en el PC sobre 3 s de audio, 4 hilos.
    import onnxruntime as ort
    times = {}
    for label, path in (("fp32", fp32), ("int8", int8)):
        so = ort.SessionOptions()
        so.intra_op_num_threads = 4
        sess = ort.InferenceSession(path, so, providers=["CPUExecutionProvider"])
        x = np.random.randn(1, 16000 * 3).astype(np.float32)
        sess.run(None, {"wav": x})
        t0 = time.time()
        for _ in range(3):
            sess.run(None, {"wav": x})
        times[label] = (time.time() - t0) / 3
        print(f"  {label}: {times[label] * 1000:.0f} ms por 3 s de audio (PC, 4 hilos)")

    info = {
        "model": name, "params_M": round(params / 1e6), "vocab_size": len(id2sym),
        "do_normalize": do_normalize, "fp32_MB": round(os.path.getsize(fp32) / 1e6),
        "int8_MB": round(os.path.getsize(int8) / 1e6),
        "pc_ms_fp32_3s": round(times["fp32"] * 1000), "pc_ms_int8_3s": round(times["int8"] * 1000),
        "hidden": cfg.hidden_size, "layers": cfg.num_hidden_layers,
    }
    with open(os.path.join(out, "export.json"), "w", encoding="utf-8") as f:
        json.dump(info, f, indent=2)
    print(json.dumps(info, indent=2))


if __name__ == "__main__":
    main()
