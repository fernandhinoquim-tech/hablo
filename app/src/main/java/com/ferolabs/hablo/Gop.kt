package com.ferolabs.hablo

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/**
 * Goodness of Pronunciation sobre las salidas CTC de un reconocedor de
 * fonemas. Réplica de `tools/asr-bench/gop.py`: si se cambia uno, cambiar el
 * otro, porque los umbrales de `assets/gop/thresholds.json` se calibran con
 * el de Python sobre el corpus.
 *
 * Solo las dos puntuaciones que la app usa:
 *
 *  GOP-AF (alignment-free) del fonema i:
 *      log P(Y | X) − max( log P(Y sin p_i | X), max_q log P(Y con p_i→q | X) )
 *    Negativo = hay una alternativa (borrarlo o cambiarlo) que explica mejor
 *    el audio. Se usa para sustituciones y borrados (h, sh...).
 *
 *  INS antes del fonema i:
 *      max_q log P(Y con q insertado antes de p_i | X) − log P(Y | X)
 *    Positivo grande = el audio prefiere que haya algo ahí ("espeak", la t
 *    de "chip"). La app compara −INS contra el umbral para que, igual que
 *    arriba, más bajo = peor.
 *
 * Nada de esto es un veredicto: el veredicto sale de un umbral calibrado.
 */
object Gop {

    private const val NEG = -1e30

    private fun logAddExp(a: Double, b: Double): Double {
        if (a <= NEG) return b
        if (b <= NEG) return a
        val m = max(a, b)
        return m + ln(exp(a - m) + exp(b - m))
    }

    /** logits [T][V] → log-softmax por trama, en double. */
    fun logSoftmax(logits: Array<FloatArray>): Array<DoubleArray> =
        Array(logits.size) { t ->
            val row = logits[t]
            var m = Double.NEGATIVE_INFINITY
            for (v in row) if (v > m) m = v.toDouble()
            var sum = 0.0
            for (v in row) sum += exp(v - m)
            val lse = m + ln(sum)
            DoubleArray(row.size) { row[it] - lse }
        }

    /** log P(ids | logp): algoritmo forward de CTC. */
    fun ctcLogp(logp: Array<DoubleArray>, ids: IntArray, blank: Int): Double {
        val t0 = logp.size
        if (ids.isEmpty()) {
            var s = 0.0
            for (t in 0 until t0) s += logp[t][blank]
            return s
        }
        val l = 2 * ids.size + 1
        val ext = IntArray(l) { if (it % 2 == 0) blank else ids[it / 2] }
        val skip = BooleanArray(l) { s -> s >= 2 && ext[s] != blank && ext[s] != ext[s - 2] }

        var alpha = DoubleArray(l) { NEG }
        alpha[0] = logp[0][ext[0]]
        if (l > 1) alpha[1] = logp[0][ext[1]]
        var next = DoubleArray(l)
        for (t in 1 until t0) {
            val row = logp[t]
            for (s in 0 until l) {
                var a = alpha[s]
                if (s >= 1) a = logAddExp(a, alpha[s - 1])
                if (skip[s]) a = logAddExp(a, alpha[s - 2])
                next[s] = if (a <= NEG) NEG else a + row[ext[s]]
            }
            val tmp = alpha; alpha = next; next = tmp
        }
        return logAddExp(alpha[l - 1], if (l > 1) alpha[l - 2] else NEG)
    }

    private fun without(ids: IntArray, i: Int): IntArray =
        IntArray(ids.size - 1) { if (it < i) ids[it] else ids[it + 1] }

    private fun replaced(ids: IntArray, i: Int, q: Int): IntArray =
        ids.copyOf().also { it[i] = q }

    private fun inserted(ids: IntArray, i: Int, q: Int): IntArray =
        IntArray(ids.size + 1) { when { it < i -> ids[it]; it == i -> q; else -> ids[it - 1] } }

    /** GOP-AF del fonema [i]. [base] es log P(ids) ya calculado. */
    fun gopAf(logp: Array<DoubleArray>, ids: IntArray, i: Int, blank: Int, phoneIds: IntArray, base: Double): Double {
        var best = ctcLogp(logp, without(ids, i), blank)
        for (q in phoneIds) {
            if (q == ids[i]) continue
            val alt = ctcLogp(logp, replaced(ids, i, q), blank)
            if (alt > best) best = alt
        }
        return base - best
    }

    /** INS antes del fonema [i]. */
    fun ins(logp: Array<DoubleArray>, ids: IntArray, i: Int, blank: Int, phoneIds: IntArray, base: Double): Double {
        var best = NEG
        for (q in phoneIds) {
            val alt = ctcLogp(logp, inserted(ids, i, q), blank)
            if (alt > best) best = alt
        }
        return best - base
    }
}
