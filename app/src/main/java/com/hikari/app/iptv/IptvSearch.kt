package com.hikari.app.iptv

import java.text.Normalizer
import java.util.Locale

/**
 * Typo-tolerant matching for IPTV channel names and groups.
 *
 * A playlist is an unlocked text file written by whoever made it: "ESPN",
 * "ESPN HD", "ESPN2" and "Espn 2 FHD" all coexist, and a viewer searching
 * "espn" expects all of them. More importantly, remotes are small and thumbs
 * are large, so "epsn", "skysprots" and "carton network" should still find
 * the channel — the old `contains` check answered those with an empty list.
 *
 * The matching is deliberately conservative:
 *  - an exact (case/diacritic-insensitive) substring always wins, and is cheap;
 *  - only when that finds nothing does the typo pass run;
 *  - short query words (<= 3 letters) are never fuzzy-matched, so "fox" cannot
 *    become "box";
 *  - the permitted edit distance scales with word length (1 for 4-6 letters,
 *    2 for 7+), which is the usual "one key slip" budget people mean.
 *
 * [score] returns -1 for no match, 0 for an exact hit, and the accumulated
 * edit distance for a fuzzy hit — callers can sort by it without another pass.
 */
object IptvSearch {

    /** True when [query] matches any of [fields] (name, group, keywords…). */
    fun matches(query: String, vararg fields: String?): Boolean = score(query, *fields) >= 0

    /**
     * Filters [items] in two passes, which is what makes this cheap enough for
     * a 50,000-channel playlist:
     *  1. the ordinary substring test (the app's historical search);
     *  2. only when that returns nothing, the fuzzy pass — scored and sorted,
     *     so the closest names surface first.
     */
    fun <T> filter(query: String, items: List<T>, fields: (T) -> List<String?>): List<T> {
        val q = query.trim()
        if (q.isBlank()) return items
        val exact = items.filter { item ->
            fields(item).any { it?.contains(q, ignoreCase = true) == true }
        }
        if (exact.isNotEmpty()) return exact
        return items
            .mapNotNull { item ->
                val score = score(q, *fields(item).toTypedArray())
                if (score < 0) null else score to item
            }
            .sortedBy { it.first }
            .map { it.second }
    }

    fun score(query: String, vararg fields: String?): Int {
        val q = query.trim()
        if (q.isBlank()) return 0
        val present = fields.filterNotNull().filter { it.isNotBlank() }
        if (present.isEmpty()) return -1
        // Fast, familiar path: the same substring test the app always used.
        if (present.any { it.contains(q, ignoreCase = true) }) return 0

        val queryTokens = normalize(q).split(' ').filter { it.isNotBlank() }
        if (queryTokens.isEmpty()) return -1
        val fieldTokens = present
            .flatMap { normalize(it).split(' ') }
            .filter { it.isNotBlank() }
        if (fieldTokens.isEmpty()) return -1
        // Words that were separated in the query ("hbo max") should still match
        // the concatenated channel name ("HBOMax"), so keep a no-space variant.
        val joined = fieldTokens.joinToString("")

        var total = 0
        for (token in queryTokens) {
            if (joined.contains(token)) continue
            var best = Int.MAX_VALUE
            for (word in fieldTokens) {
                val d = editDistance(token, word, limit = allowedDistance(token.length))
                if (d < best) {
                    best = d
                    if (best == 0) break
                }
            }
            if (best > allowedDistance(token.length)) return -1
            total += best
        }
        return total
    }

    /** One key slip per four-ish letters; short words must match exactly. */
    private fun allowedDistance(length: Int): Int = when {
        length <= 3 -> 0
        length <= 6 -> 1
        else -> 2
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase(Locale.ROOT)
            .replace(NON_WORD, " ")
            .trim()

    /**
     * Optimal-string-alignment distance (Levenshtein plus adjacent
     * transpositions) capped at [limit]. Transpositions matter here: "epsn"
     * for "espn" and "skysprots" for "skysports" are the two slips people
     * actually make on a remote, and plain Levenshtein charges them twice.
     * Three rows instead of a full matrix: the words are channel names, not
     * documents, and this runs per playlist row.
     */
    private fun editDistance(a: String, b: String, limit: Int): Int {
        if (a == b) return 0
        if (limit <= 0) return 1
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
        var twoBack = IntArray(b.length + 1)
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var best = minOf(
                    current[j - 1] + 1,
                    previous[j] + 1,
                    previous[j - 1] + cost,
                )
                // Adjacent transposition ("ab" ↔ "ba") costs one edit, not two.
                if (i > 1 && j > 1 &&
                    a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]
                ) {
                    best = minOf(best, twoBack[j - 2] + 1)
                }
                current[j] = best
                if (best < rowMin) rowMin = best
            }
            if (rowMin > limit) return limit + 1
            val recycled = twoBack
            twoBack = previous
            previous = current
            current = recycled
        }
        return previous[b.length]
    }

    private val DIACRITICS = Regex("\\p{M}+")
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
}
