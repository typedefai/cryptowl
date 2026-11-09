package com.typedefai.cryptowl

import org.json.JSONArray
import org.json.JSONObject

enum class DiffLineKind { EQUAL, ADDED, REMOVED }

data class DiffLine(val kind: DiffLineKind, val text: String)

/**
 * Line-based LCS diff — mirror of `cryptowl-desktop/src/TextDiff.cpp`.
 * Bounded to ~4M cells so pathological inputs fail instead of freezing.
 */
object TextDiff {

    fun diffLines(before: String, after: String): List<DiffLine> {
        val a = before.split('\n')
        val b = after.split('\n')
        val n = a.size
        val m = b.size
        if ((n + 1L) > MAX_CELLS / (m + 1L)) return emptyList()

        val columns = m + 1
        val lcs = IntArray((n + 1) * columns)
        for (i in 1..n) {
            for (j in 1..m) {
                lcs[i * columns + j] = if (a[i - 1] == b[j - 1]) {
                    lcs[(i - 1) * columns + j - 1] + 1
                } else {
                    maxOf(lcs[(i - 1) * columns + j], lcs[i * columns + j - 1])
                }
            }
        }

        val reversed = mutableListOf<DiffLine>()
        var i = n
        var j = m
        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && a[i - 1] == b[j - 1] -> {
                    reversed.add(DiffLine(DiffLineKind.EQUAL, a[i - 1]))
                    i--
                    j--
                }
                j > 0 && (i == 0 || lcs[i * columns + j - 1] >= lcs[(i - 1) * columns + j]) -> {
                    reversed.add(DiffLine(DiffLineKind.ADDED, b[j - 1]))
                    j--
                }
                else -> {
                    reversed.add(DiffLine(DiffLineKind.REMOVED, a[i - 1]))
                    i--
                }
            }
        }
        return reversed.asReversed()
    }

    /** Pretty-prints a JSON payload for diffing (desktop does the same). */
    fun prettyJson(payload: String): String = try {
        when (val trimmed = payload.trim()) {
            "" -> ""
            else -> if (trimmed.startsWith("{")) {
                JSONObject(trimmed).toString(2)
            } else if (trimmed.startsWith("[")) {
                JSONArray(trimmed).toString(2)
            } else {
                payload
            }
        }
    } catch (e: Exception) {
        payload
    }

    private const val MAX_CELLS = 4L * 1024 * 1024
}
