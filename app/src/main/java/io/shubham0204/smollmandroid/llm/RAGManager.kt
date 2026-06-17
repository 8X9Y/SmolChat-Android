package io.shubham0204.smollmandroid.llm

import android.util.Log

class RAGManager {

    private var chunks: List<Chunk> = emptyList()
    private var sourceName: String = ""
    private var totalChars: Int = 0

    fun indexDocument(fileName: String, content: String) {
        clear()
        sourceName = fileName
        totalChars = content.length
        Log.d("RAG_CHUNK", "[2/5] start: $fileName chars=$totalChars")
        val t0 = System.currentTimeMillis()
        chunks = TextChunker.chunk(content, source = fileName)
        val ms = System.currentTimeMillis() - t0
        val avgChars = if (chunks.isNotEmpty()) totalChars / chunks.size else 0
        val distinctChapters = chunks.map { it.chapter }.filter { it.isNotEmpty() }.distinct()
        Log.d("RAG_CHUNK", "[2/5] done: chunks=${chunks.size} avgChars=$avgChars chapters=${distinctChapters.size} elapsed=${ms}ms")
        distinctChapters.forEachIndexed { i, ch ->
            val count = chunks.count { it.chapter == ch }
            Log.d("RAG_CHUNK", "[2/5]   chapter[$i]: '$ch' ($count chunks)")
        }
    }

    /**
     * Search and return top chunks, optimized for speed:
     * - Reduced TopK (3 instead of 5) for faster TTFT
     * - Context cap at 2500 chars
     * - Merges adjacent same-chapter chunks
     */
    fun search(query: String, topK: Int = 3): List<Chunk> {
        if (chunks.isEmpty()) {
            Log.d("RAG_SEARCH", "[3/5] skip: no chunks indexed")
            return emptyList()
        }
        Log.d("RAG_SEARCH", "[3/5] start: query='$query' topK=$topK poolSize=${chunks.size}")
        val t0 = System.currentTimeMillis()
        val raw = KeywordSearcher.search(query, chunks, topK)
        val ms = System.currentTimeMillis() - t0

        // Merge adjacent same-chapter chunks
        val merged = mergeAdjacent(raw)
        Log.d("RAG_SEARCH", "[3/5] merge: ${raw.size}→${merged.size} chunks")

        // Cap total at 2500 chars
        val maxCtx = 2500
        var used = 0
        val capped = merged.map { c ->
            val remain = (maxCtx - used).coerceAtLeast(100)
            val txt = if (c.text.length > remain) c.text.take(remain) + "..." else c.text
            used += txt.length
            c.copy(text = txt)
        }

        val totalChars = capped.sumOf { it.text.length }
        Log.d("RAG_SEARCH", "[3/5] done: retrieved=${capped.size} totalChars=$totalChars elapsed=${ms}ms")
        capped.forEachIndexed { i, c ->
            val preview = c.text.take(80).replace("\n", " ")
            val chInfo = if (c.chapter.isNotEmpty()) " ch='${c.chapter}'" else ""
            Log.d("RAG_SEARCH", "[3/5]   hit[$i] id=${c.id}$chInfo chars=${c.text.length} |$preview...|")
        }
        return capped
    }

    /** Merge adjacent chunks that share the same chapter and are within 2 IDs of each other. */
    private fun mergeAdjacent(sorted: List<Chunk>): List<Chunk> {
        if (sorted.size <= 1) return sorted
        val result = mutableListOf<Chunk>()
        var current = sorted.first()
        for (i in 1 until sorted.size) {
            val next = sorted[i]
            if (next.chapter == current.chapter && next.id - current.id <= 2) {
                current = current.copy(
                    text = current.text + "\n" + next.text,
                    id = current.id
                )
            } else {
                result.add(current)
                current = next
            }
        }
        result.add(current)
        return result
    }

    fun clear() {
        if (chunks.isNotEmpty()) {
            Log.d("RAG_CLEAR", "[CLEAR] discarding ${chunks.size} chunks from $sourceName")
        }
        chunks = emptyList()
        sourceName = ""
        totalChars = 0
    }

    val chunkCount: Int get() = chunks.size
    val charCount: Int get() = totalChars
}
