package com.example.sudoku

import kotlin.random.Random

object SudokuEngine {
    private fun mask(g: IntArray, i: Int): Int {
        val r = i / 9; val c = i % 9; var m = 0
        for (k in 0 until 9) m = m or (1 shl g[r * 9 + k]) or (1 shl g[k * 9 + c])
        val br = r - r % 3; val bc = c - c % 3
        for (a in 0..2) for (b in 0..2) m = m or (1 shl g[(br + a) * 9 + bc + b])
        return m.inv() and 0x3FE
    }

    // cella vuota con meno candidati (indice, maschera candidati)
    private fun pick(g: IntArray): Pair<Int, Int> {
        var best = -1; var bm = 0; var bc = 10
        for (i in 0 until 81) if (g[i] == 0) {
            val m = mask(g, i); val c = Integer.bitCount(m)
            if (c < bc) { bc = c; best = i; bm = m; if (c < 2) break }
        }
        return best to bm
    }

    private fun count(g: IntArray, lim: Int): Int {
        val (i, m) = pick(g)
        if (i < 0) return 1
        var n = 0
        for (v in 1..9) if (m shr v and 1 == 1) {
            g[i] = v; n += count(g, lim - n); g[i] = 0
            if (n >= lim) break
        }
        return n
    }

    private fun fill(g: IntArray): Boolean {
        val (i, m) = pick(g)
        if (i < 0) return true
        for (v in (1..9).filter { m shr it and 1 == 1 }.shuffled()) {
            g[i] = v
            if (fill(g)) return true
        }
        g[i] = 0
        return false
    }

    /** Restituisce (schema, soluzione). Lo schema ha soluzione unica. */
    fun generate(clues: Int): Pair<IntArray, IntArray> {
        val sol = IntArray(81); fill(sol)
        val p = sol.clone(); var left = 81
        for (i in (0 until 81).shuffled(Random)) {
            if (left <= clues) break
            val v = p[i]; p[i] = 0
            if (count(p.clone(), 2) != 1) p[i] = v else left--
        }
        return p to sol
    }
}
