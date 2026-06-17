package io.shubham0204.smollmandroid.llm

import android.util.Log

/**
 * RAG orchestrator (Phase 1: keyword-based retrieval).
 */
class RAGManager {

    private var chunks: List<Chunk> = emptyList()
    private var sourceName: String = ""
    private var totalChars: Int = 0

    // ── Public API ───────────────────────────────────────────

    fun indexDocument(fileName: String, content: String) {
        clear()
        sourceName = fileName
        totalChars = content.length
        Log.d("RAG_CHUNK", "[2/5] start: $fileName chars=$totalChars")
        val startMs = System.currentTimeMillis()
        chunks = TextChunker.chunk(content, source = fileName)
        val elapsed = System.currentTimeMillis() - startMs
        val avgChars = if (chunks.isNotEmpty()) totalChars / chunks.size else 0
        Log.d("RAG_CHUNK", "[2/5] done: chunks=${chunks.size} avgChars=$avgChars elapsed=${elapsed}ms")
    }

    fun search(query: String, topK: Int = 5): List<Chunk> {
        if (chunks.isEmpty()) {
            Log.d("RAG_SEARCH", "[3/5] skip: no chunks indexed")
            return emptyList()
        }
        Log.d("RAG_SEARCH", "[3/5] start: query='$query' topK=$topK poolSize=${chunks.size}")
        val startMs = System.currentTimeMillis()
        val retrieved = KeywordSearcher.search(query, chunks, topK)
        val elapsed = System.currentTimeMillis() - startMs
        val promptChars = retrieved.sumOf { it.text.length }
        retrieved.forEachIndexed { i, c ->
            val preview = c.text.take(60).replace("\n", " ")
            Log.d("RAG_SEARCH", "[3/5]   hit[$i] id=${c.id} chars=${c.text.length} |$preview...|")
        }
        Log.d("RAG_SEARCH", "[3/5] done: retrieved=${retrieved.size} promptChars=$promptChars elapsed=${elapsed}ms")
        return retrieved
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
