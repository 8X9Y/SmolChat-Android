package io.shubham0204.smollmandroid.llm

import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

/**
 * File text extractor using PDFBox for PDF parsing.
 */
object FileTextExtractor {

    private const val LOGTAG = "RAG_READ"

    /** Initialize PDFBox resource loader (call once from Application). */
    fun init(context: android.content.Context) {
        PDFBoxResourceLoader.init(context)
        Log.d(LOGTAG, "PDFBoxResourceLoader initialized")
    }

    /**
     * Extract text from a file.
     * For PDF: uses PDFBox. For plain text: reads as UTF-8.
     */
    fun extract(bytes: ByteArray, fileName: String): String {
        val name = fileName.lowercase()
        return if (name.endsWith(".pdf")) {
            extractPdf(bytes, fileName)
        } else {
            String(bytes, Charsets.UTF_8)
        }
    }

    private fun extractPdf(bytes: ByteArray, fileName: String): String {
        Log.d(LOGTAG, "PDF extract start: fileName=$fileName, sizeBytes=${bytes.size}")

        val startTime = System.currentTimeMillis()

        return try {
            val document = PDDocument.load(bytes)
            Log.d(LOGTAG, "PDF loaded: pages=${document.numberOfPages}")

            val stripper = PDFTextStripper()
            stripper.sortByPosition = true
            stripper.addMoreFormatting = false
            val text = stripper.getText(document)

            document.close()

            val elapsed = System.currentTimeMillis() - startTime
            val charCount = text.length
            val wordCount = text.split(Regex("\\s+")).count { it.isNotBlank() }

            Log.d(LOGTAG, "PDF extract done: chars=$charCount, words=$wordCount, elapsedMs=$elapsed")

            text
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            Log.e(LOGTAG, "PDF extract failed: elapsedMs=$elapsed, error=${e.message}", e)
            throw e
        }
    }
}
