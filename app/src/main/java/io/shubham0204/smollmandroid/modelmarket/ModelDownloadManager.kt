package io.shubham0204.smollmandroid.modelmarket

import android.content.Context
import android.util.Log
import io.shubham0204.smollm.GGUFReader
import io.shubham0204.smollm.SmolLM
import io.shubham0204.smollmandroid.data.AppDB
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

@Single
class ModelDownloadManager(
    private val context: Context,
    private val appDB: AppDB,
) {
    companion object {
        private const val TAG = "DOM_5_DLMGR"
        private const val BUFFER_SIZE = 8192
    }

    sealed class DownloadState {
        data object Starting : DownloadState()
        data class Progress(val bytesRead: Long, val totalBytes: Long) : DownloadState()
        data object Importing : DownloadState()
        data class Ready(val modelName: String) : DownloadState()
        data class Error(val message: String) : DownloadState()
    }
    suspend fun downloadFromUrl(
        url: String,
        fileName: String,
        onProgress: suspend (DownloadState) -> Unit,
    ) {
        onProgress(DownloadState.Starting)
        try {
            Log.d(TAG, "downloadFromUrl: $url")
            val tempFile = File(context.cacheDir, "$fileName.tmp")
            onProgress(DownloadState.Progress(0, 1))
            downloadFile(url, tempFile)
            onProgress(DownloadState.Importing)
            importModel(tempFile, fileName)
            tempFile.delete()
            onProgress(DownloadState.Ready(fileName))
        } catch (e: Exception) {
            Log.w(TAG, "downloadFromUrl failed: ${e.message}")
            File(context.cacheDir, "$fileName.tmp").delete()
            onProgress(DownloadState.Error(e.message ?: "Download failed"))
        }
    }

    suspend fun downloadAndImport(
        modelInfo: ModelInfo,
        onProgress: suspend (DownloadState) -> Unit,
    ) {
        onProgress(DownloadState.Starting)

        val urlsToTry: MutableList<String> = mutableListOf()
        modelInfo.mirrorUrl?.let { urlsToTry.add(it) }
        if (modelInfo.hfRepo.isNotEmpty()) urlsToTry.add(modelInfo.hfResolveUrl())
        if (urlsToTry.isEmpty()) {
            onProgress(DownloadState.Error("No download URL"))
            return
        }

        var lastError: String? = null
        for (url in urlsToTry) {
            try {
                Log.d(TAG, "Downloading from: $url")
                val tempFile = File(context.cacheDir, modelInfo.filename + ".tmp")
                onProgress(DownloadState.Progress(0, 1))
                downloadFile(url, tempFile)
                onProgress(DownloadState.Importing)
                importModel(tempFile, modelInfo.filename)
                tempFile.delete()
                onProgress(DownloadState.Ready(modelInfo.name))
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed: ${e.message}")
                lastError = e.message
                File(context.cacheDir, modelInfo.filename + ".tmp").delete()
            }
        }

        onProgress(DownloadState.Error(lastError ?: "All URLs failed"))
    }

    private suspend fun downloadFile(
        urlString: String,
        destFile: File,
    ) = withContext(Dispatchers.IO) {
        var currentUrl = urlString
        var redirectCount = 0
        val maxRedirects = 5

        while (redirectCount < maxRedirects) {
            val url = URL(currentUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 120000
                instanceFollowRedirects = false
            }
            try {
                conn.connect()
                val code = conn.responseCode
                when {
                    code == HttpURLConnection.HTTP_MOVED_TEMP ||
                    code == HttpURLConnection.HTTP_MOVED_PERM ||
                    code == HttpURLConnection.HTTP_SEE_OTHER -> {
                        val redirect = conn.getHeaderField("Location")
                        conn.disconnect()
                        if (redirect != null) {
                            currentUrl = redirect
                            redirectCount++
                            continue
                        }
                        throw RuntimeException("Redirect without Location header")
                    }
                    code != HttpURLConnection.HTTP_OK ->
                        throw RuntimeException("HTTP $code")
                }

                val total = conn.contentLengthLong
                var bytesRead = 0L
                conn.inputStream.use { input ->
                    FileOutputStream(destFile).use { out ->
                        val buf = ByteArray(BUFFER_SIZE)
                        var n: Int
                        while (input.read(buf).also { n = it } != -1) {
                            out.write(buf, 0, n)
                            bytesRead += n
                        }
                    }
                }
                if (total > 0 && bytesRead < total) {
                    throw RuntimeException("Incomplete: $bytesRead/$total")
                }
                return@withContext
            } finally {
                conn.disconnect()
            }
        }
        throw RuntimeException("Too many redirects")
    }

    private suspend fun importModel(
        tempFile: File,
        fileName: String,
    ) = withContext(Dispatchers.IO) {
        val dest = File(context.filesDir, fileName)
        tempFile.copyTo(dest, overwrite = true)
        val ggufReader = GGUFReader()
        ggufReader.load(dest.absolutePath)
        val ctxSize = ggufReader.getContextSize() ?: SmolLM.DefaultInferenceParams.contextSize
        val tmpl = ggufReader.getChatTemplate() ?: SmolLM.DefaultInferenceParams.chatTemplate
        appDB.addModel(fileName, "", dest.absolutePath, ctxSize.toInt(), tmpl)
        Log.d(TAG, "Imported: $fileName")
    }
}
