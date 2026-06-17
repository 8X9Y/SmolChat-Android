package io.shubham0204.smollmandroid.llm

data class Chunk(
    val id: Int,
    val text: String,
    val source: String,
    val index: Int,
)

/**
 * Splits text into overlapping chunks at natural boundaries.
 *
 * Strategy: split by paragraphs first, then sentences, then hard cutoff.
 * Memory-safe: adjusts indices before substring() — no double allocations.
 */
object TextChunker {

    const val DEFAULT_CHUNK_SIZE = 512
    const val DEFAULT_OVERLAP = 128

    fun chunk(
        text: String,
        source: String,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
        overlap: Int = DEFAULT_OVERLAP,
    ): List<Chunk> {
        // 1. Split by blank lines (paragraphs)
        val paragraphs = text.split(Regex("\n\\s*\n")).filter { it.isNotBlank() }
        val chunks = mutableListOf<Chunk>()
        var id = 0

        for (para in paragraphs) {
            if (para.length <= chunkSize) {
                chunks.add(Chunk(id = id, text = para.trim(), source = source, index = id))
                id++
            } else {
                // 2. Split long paragraph into sentence-level segments
                val segments = splitIntoSegments(para, chunkSize)
                for (seg in segments) {
                    if (seg.isNotBlank()) {
                        chunks.add(Chunk(id = id, text = seg.trim(), source = source, index = id))
                        id++
                    }
                }
            }
        }

        return chunks
    }

    /**
     * Split a long text into segments of roughly chunkSize characters.
     * Tries to break at sentences, falls back to hard cutoff.
     */
    private fun splitIntoSegments(text: String, maxLen: Int): List<String> {
        val result = mutableListOf<String>()
        var pos = 0
        while (pos < text.length) {
            var end = (pos + maxLen).coerceAtMost(text.length)
            if (end < text.length) {
                // Try sentence break
                val brk = text.lastIndexOfAny(
                    charArrayOf('\n', '.', '!', '?', '\u3002', '\uFF01', '\uFF1F'),
                    end
                )
                if (brk > pos + maxLen / 2) end = brk + 1
            }
            // Trim by index
            var ts = pos
            while (ts < end && text[ts].isWhitespace()) ts++
            var te = end
            while (te > ts && text[te - 1].isWhitespace()) te--
            if (te > ts) result.add(text.substring(ts, te))
            pos = (te - 1).coerceAtLeast(pos + maxLen / 4)
        }
        return result
    }
}
