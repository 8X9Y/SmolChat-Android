package io.shubham0204.smollmandroid.llm

/**
 * Simplified keyword-based chunk retriever (RAG - Phase 1).
 *
 * Tokenizes the query into keywords and scores each chunk
 * by how many distinct keywords appear in it.
 *
 * Supports both English (whitespace-delimited) and Chinese
 * (character-sequence keywords).
 */
object KeywordSearcher {

    fun search(
        query: String,
        chunks: List<Chunk>,
        topK: Int = 5,
    ): List<Chunk> {
        if (chunks.isEmpty()) return emptyList()
        val keywords = tokenize(query)
        if (keywords.isEmpty()) return chunks.take(topK)

        // Score each chunk: count distinct keyword hits
        val scored: List<Pair<Int, Int>> = chunks.mapIndexed { idx, chunk ->
            val score: Int = keywords.count { kw -> kw in chunk.text }
            Pair(idx, score)
        }

        // Pick top-K by score, then restore original order
        val topIndices: List<Int> = scored
            .sortedByDescending { pair: Pair<Int, Int> -> pair.second }
            .take(topK)
            .sortedBy { pair: Pair<Int, Int> -> pair.first }
            .map { pair: Pair<Int, Int> -> pair.first }

        return topIndices.map { i: Int -> chunks[i] }
    }

    /**
     * Tokenize a query into search keywords.
     *
     * English: split on non-letter/non-digit, keep tokens with length >= 2.
     * Chinese: extract consecutive CJK character sequences as single keywords.
     */
    private fun tokenize(text: String): List<String> {
        val lower = text.lowercase()
        val tokens = mutableListOf<String>()

        // English tokens
        tokens.addAll(
            lower.split(Regex("[^a-z0-9]+"))
                .filter { it.length >= 2 }
        )

        // Chinese: find CJK runs
        var i = 0
        while (i < lower.length) {
            if (isCjk(lower[i])) {
                val start = i
                while (i < lower.length && isCjk(lower[i])) i++
                val cjkRun = lower.substring(start, i)
                if (cjkRun.length >= 1) {
                    tokens.add(cjkRun)
                    // Also add consecutive 2-char substrings for better matching
                    if (cjkRun.length >= 2) {
                        for (j in 0 until cjkRun.length - 1) {
                            tokens.add(cjkRun.substring(j, j + 2))
                        }
                    }
                }
            } else {
                i++
            }
        }

        return tokens.distinct().filter { it.length >= 2 }
    }

    private fun isCjk(c: Char): Boolean =
        c in '\u4e00'..'\u9fff'  // CJK Unified
        || c in '\u3400'..'\u4dbf'  // CJK Extension A
        || c in '\uf900'..'\ufaff'  // CJK Compatibility
}
