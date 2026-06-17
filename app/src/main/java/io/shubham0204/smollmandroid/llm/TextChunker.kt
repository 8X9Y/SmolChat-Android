package io.shubham0204.smollmandroid.llm

data class Chunk(
    val id: Int,
    val text: String,
    val source: String,
    val index: Int,
    val chapter: String = "",
)

/**
 * Splits text into chunks at natural boundaries.
 *
 * Supports optional chapter markers in the format:
 *   [CHAPTER: Title]
 * When found, all following chunks carry that chapter name.
 */
object TextChunker {

    const val DEFAULT_CHUNK_SIZE = 512

    fun chunk(
        text: String,
        source: String,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
    ): List<Chunk> {
        val paragraphs = text.split(Regex("\n\\s*\n")).filter { it.isNotBlank() }
        val result = mutableListOf<Chunk>()
        var id = 0
        var currentChapter = ""

        for (para in paragraphs) {
            // Detect chapter marker
            val chapterMatch = Regex("^\\[CHAPTER:\\s*(.+?)\\]$").find(para.trim())
            if (chapterMatch != null) {
                currentChapter = chapterMatch.groupValues[1].trim()
                continue
            }

            if (para.length <= chunkSize) {
                result.add(
                    Chunk(id = id, text = para.trim(), source = source, index = id, chapter = currentChapter)
                )
                id++
            } else {
                for (seg in splitLong(para, chunkSize)) {
                    if (seg.isNotBlank()) {
                        result.add(
                            Chunk(id = id, text = seg.trim(), source = source, index = id, chapter = currentChapter)
                        )
                        id++
                    }
                }
            }
        }
        return result
    }

    private fun splitLong(text: String, maxLen: Int): List<String> {
        val out = mutableListOf<String>()
        var pos = 0
        while (pos < text.length) {
            var end = (pos + maxLen).coerceAtMost(text.length)
            if (end < text.length) {
                val brk = text.lastIndexOfAny(
                    charArrayOf('\n', '.', '!', '?', '\u3002', '\uFF01', '\uFF1F'),
                    end
                )
                if (brk > pos + maxLen / 2) end = brk + 1
            }
            var ts = pos
            while (ts < end && text[ts].isWhitespace()) ts++
            var te = end
            while (te > ts && text[te - 1].isWhitespace()) te--
            if (te > ts) out.add(text.substring(ts, te))
            pos = (te - 1).coerceAtLeast(pos + maxLen / 4)
        }
        return out
    }
}
