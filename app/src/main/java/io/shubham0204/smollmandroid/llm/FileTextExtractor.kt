package io.shubham0204.smollmandroid.llm

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

object FileTextExtractor {

    private const val TAG = "RAG_READ"
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        PDFBoxResourceLoader.init(context)
        Log.d(TAG, "PDFBoxResourceLoader initialized")
    }

    // ── RAM-based char limit ─────────────────────────────────

    private fun maxExtractedChars(): Int {
        val ctx = appContext ?: return 500_000
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 500_000
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalGB = memInfo.totalMem / (1024 * 1024 * 1024)
        return when {
            totalGB >= 16 -> 2_000_000
            totalGB >= 12 -> 1_000_000
            totalGB >= 8  -> 500_000
            else          -> 300_000
        }.also { Log.d(TAG, "RAM=${totalGB}GB → maxChars=$it") }
    }

    // ── Public API ───────────────────────────────────────────

    fun extract(bytes: ByteArray, fileName: String): String {
        val name = fileName.lowercase()
        return when {
            name.endsWith(".epub") -> extractEpub(bytes, fileName)
            name.endsWith(".pdf")  -> extractPdf(bytes, fileName)
            else                   -> String(bytes, Charsets.UTF_8)
        }
    }

    // ── PDF ──────────────────────────────────────────────────

    private fun extractPdf(bytes: ByteArray, fileName: String): String {
        Log.d(TAG, "PDF extract start: $fileName sizeBytes=${bytes.size}")
        val t0 = System.currentTimeMillis()
        return try {
            val doc = PDDocument.load(bytes)
            Log.d(TAG, "PDF loaded: pages=${doc.numberOfPages}")
            val stripper = PDFTextStripper()
            stripper.sortByPosition = true
            stripper.addMoreFormatting = false
            val text = stripper.getText(doc)
            doc.close()
            val ms = System.currentTimeMillis() - t0
            val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
            Log.d(TAG, "PDF extract done: chars=${text.length} words=$words elapsed=${ms}ms")
            text
        } catch (e: Exception) {
            Log.e(TAG, "PDF extract failed: ${e.message}", e)
            throw e
        }
    }

    // ── EPUB ─────────────────────────────────────────────────

    private fun extractEpub(bytes: ByteArray, fileName: String): String {
        Log.d(TAG, "EPUB extract start: $fileName sizeBytes=${bytes.size}")
        val maxChars = maxExtractedChars()
        val t0 = System.currentTimeMillis()
        val sb = StringBuilder()
        val zip = ZipInputStream(ByteArrayInputStream(bytes))
        var entry = zip.nextEntry
        var chapterCount = 0

        while (entry != null && sb.length < maxChars) {
            if (!entry.isDirectory) {
                val eName = entry.name.lowercase()
                if (eName.endsWith(".xhtml") || eName.endsWith(".html") || eName.endsWith(".htm")) {
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(4096)
                    var n = zip.read(buf)
                    while (n > 0) { out.write(buf, 0, n); n = zip.read(buf) }

                    val html = String(out.toByteArray(), Charsets.UTF_8)
                    val title = extractTitle(html, entry.name)
                    val body = stripHtml(html)

                    if (body.isNotBlank()) {
                        chapterCount++
                        sb.append("\n\n[CHAPTER: $title]\n\n")
                        sb.append(body)
                    }
                }
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
        zip.close()

        val ms = System.currentTimeMillis() - t0
        val len = sb.length
        val truncated = len >= maxChars
        if (truncated) sb.setLength(maxChars)
        Log.d(TAG, "EPUB extract done: chapters=$chapterCount chars=$len truncated=$truncated elapsed=${ms}ms")
        return sb.toString().trim()
    }

    private fun extractTitle(html: String, fallback: String): String {
        val m = Regex("<title[^>]*>([^<]*)</title>", RegexOption.IGNORE_CASE).find(html)
        if (m != null) return m.groupValues[1].trim()
        return fallback.substringAfterLast("/").removeSuffix(".xhtml").removeSuffix(".html").removeSuffix(".htm")
    }

    private fun stripHtml(html: String): String {
        return html
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<head[^>]*>[\\s\\S]*?</head>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("&amp;"), "&")
            .replace(Regex("&lt;"), "<")
            .replace(Regex("&gt;"), ">")
            .replace(Regex("&quot;"), "\"")
            .replace(Regex("&apos;"), "'")
            .replace(Regex("&#\\d+;"), " ")
            .replace(Regex("&#x[0-9a-fA-F]+;"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
