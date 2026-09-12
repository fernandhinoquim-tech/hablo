"""
Goodness of Pronunciation (GOP) sobre las salidas CTC de un reconocedor de
fonemas. Matemática pura sobre numpy; es lo que habría que replicar en Kotlin.

Dos variantes, las dos de la literatura reciente sobre GOP con CTC (Interspeech
2025, arXiv 2506.02080, y el "alignment-free GOP" de 2024 que cita):

  GOP-FA (forced alignment): se alinea a la fuerza la secuencia esperada de
    fonemas con el trellis CTC (Viterbi con blanks) y, para cada fonema, se
    promedia sobre sus tramas
        log p(fonema esperado | trama) - max_q log p(q | trama)
    con q sobre los fonemas (sin blank). Es <= 0; 0 = el esperado fue el
    mejor en todas sus tramas.

  GOP-AF (alignment-free): sin alinear. Para cada fonema esperado p_i:
        log P(Y | X) - max( log P(Y sin p_i | X), max_{q != p_i} log P(Y con p_i->q | X) )
    con P(.|X) la verosimilitud CTC de la secuencia completa. Negativo = hay
    una alternativa (borrarlo o cambiarlo) que explica mejor el audio.

  INS(p_i): puntaje de inserción antes de p_i (para "espeak", "wok-ed"):
        max_q log P(Y con q insertado antes de p_i | X) - log P(Y | X)
    Positivo grande = el audio prefiere que haya algo ahí. Se usa -INS como
    "bondad" para que, igual que arriba, más bajo = peor.

Ninguna de estas puntuaciones es un veredicto: el veredicto sale de un umbral
elegido por MCC sobre un corpus etiquetado (ver phoneme_eval.py).
"""

import numpy as np

NEG = -1e30


def log_softmax(logits):
    m = logits.max(-1, keepdims=True)
    e = np.exp(logits - m)
    return logits - m - np.log(e.sum(-1, keepdims=True))


def _extend(ids, blank):
    ext = [blank]
    for i in ids:
        ext += [i, blank]
    return np.asarray(ext)


def ctc_logp(logp, ids, blank):
    """log P(ids | logp) con el algoritmo forward de CTC (vectorizado sobre estados)."""
    if len(ids) == 0:
        return float(logp[:, blank].sum())
    ext = _extend(ids, blank)
    L = len(ext)
    T = logp.shape[0]
    # ¿se puede saltar desde s-2? solo si el estado es un fonema distinto al de s-2
    skip = np.zeros(L, dtype=bool)
    skip[2:] = (ext[2:] != blank) & (ext[2:] != ext[:-2])
    alpha = np.full(L, NEG)
    alpha[0] = logp[0, ext[0]]
    if L > 1:
        alpha[1] = logp[0, ext[1]]
    for t in range(1, T):
        prev1 = np.concatenate(([NEG], alpha[:-1]))
        prev2 = np.concatenate(([NEG, NEG], alpha[:-2]))
        prev2 = np.where(skip, prev2, NEG)
        a = np.logaddexp(np.logaddexp(alpha, prev1), prev2)
        alpha = a + logp[t, ext]
    return float(np.logaddexp(alpha[-1], alpha[-2] if L > 1 else NEG))


def forced_align(logp, ids, blank):
    """Viterbi sobre el trellis CTC. Devuelve, por fonema esperado, la lista de
    tramas en las que el mejor camino emite ese fonema."""
    ext = _extend(ids, blank)
    L = len(ext)
    T = logp.shape[0]
    skip = np.zeros(L, dtype=bool)
    skip[2:] = (ext[2:] != blank) & (ext[2:] != ext[:-2])
    delta = np.full((T, L), NEG)
    back = np.zeros((T, L), dtype=np.int8)  # 0: mismo estado, 1: s-1, 2: s-2
    delta[0, 0] = logp[0, ext[0]]
    if L > 1:
        delta[0, 1] = logp[0, ext[1]]
    for t in range(1, T):
        stay = delta[t - 1]
        p1 = np.concatenate(([NEG], delta[t - 1, :-1]))
        p2 = np.where(skip, np.concatenate(([NEG, NEG], delta[t - 1, :-2])), NEG)
        stacked = np.stack([stay, p1, p2])
        best = stacked.argmax(0)
        delta[t] = stacked.max(0) + logp[t, ext]
        back[t] = best
    s = L - 1 if (L == 1 or delta[T - 1, L - 1] >= delta[T - 1, L - 2]) else L - 2
    frames = [[] for _ in ids]
    for t in range(T - 1, -1, -1):
        if ext[s] != blank:
            frames[(s - 1) // 2].append(t)
        if t > 0:
            s -= int(back[t, s])
    return [sorted(f) for f in frames]


def gop_fa(logp, ids, blank, phone_ids):
    """GOP por alineación forzada, un valor por fonema esperado (<= 0)."""
    frames = forced_align(logp, ids, blank)
    best_phone = logp[:, phone_ids].max(-1)  # mejor fonema (sin blank) por trama
    out = []
    for i, fr in enumerate(frames):
        if not fr:
            out.append(float("nan"))
            continue
        out.append(float(np.mean([logp[t, ids[i]] - best_phone[t] for t in fr])))
    return out, frames


def gop_af(logp, ids, blank, phone_ids):
    """GOP sin alinear: verosimilitud de la secuencia contra sus alternativas.
    Devuelve (gop, ins) por fonema esperado."""
    base = ctc_logp(logp, ids, blank)
    gop, ins = [], []
    for i in range(len(ids)):
        alts = [ctc_logp(logp, ids[:i] + ids[i + 1:], blank)]          # borrado
        for q in phone_ids:
            if q != ids[i]:
                alts.append(ctc_logp(logp, ids[:i] + [q] + ids[i + 1:], blank))  # sustitución
        gop.append(base - max(alts))
        best_ins = max(ctc_logp(logp, ids[:i] + [q] + ids[i:], blank) for q in phone_ids)
        ins.append(best_ins - base)
    return gop, ins


def mcc(tp, fp, tn, fn):
    d = np.sqrt(float(tp + fp) * (tp + fn) * (tn + fp) * (tn + fn))
    return 0.0 if d == 0 else (tp * tn - fp * fn) / d


def best_threshold(scores, labels):
    """scores: bondad (más bajo = peor); labels: 1 = error real, 0 = bien dicho.
    Elige el percentil del puntaje (entre todos los valores) que maximiza el
    MCC de 'marcar como error si score < umbral'. Devuelve dict con umbral,
    percentil, MCC y matriz de confusión."""
    s = np.asarray(scores, dtype=float)
    y = np.asarray(labels, dtype=int)
    best = None
    for pct in range(1, 100):
        thr = np.percentile(s, pct)
        pred = (s < thr).astype(int)
        tp = int(((pred == 1) & (y == 1)).sum())
        fp = int(((pred == 1) & (y == 0)).sum())
        tn = int(((pred == 0) & (y == 0)).sum())
        fn = int(((pred == 0) & (y == 1)).sum())
        m = mcc(tp, fp, tn, fn)
        if best is None or m > best["mcc"]:
            best = dict(thr=float(thr), pct=pct, mcc=m, tp=tp, fp=fp, tn=tn, fn=fn)
    return best
