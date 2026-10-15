package com.jarvis.localai.models

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

class ModelDownloadRepository(context: Context) {
    private val directory = File(context.filesDir, "models").apply { mkdirs() }

    suspend fun download(entry: ModelCatalogEntry, onProgress: (String, Long, Long) -> Unit) =
        withContext(Dispatchers.IO) {
            listOf(entry.modelFilename, entry.projectorFilename).forEach { filename ->
                coroutineContext.ensureActive()
                downloadFile(entry.downloadUrl(filename), File(directory, filename), filename, onProgress)
            }
        }

    private suspend fun downloadFile(
        url: String,
        destination: File,
        name: String,
        onProgress: (String, Long, Long) -> Unit
    ) {
        val partial = File(destination.parentFile, "${destination.name}.part")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 120_000
            instanceFollowRedirects = true
        }
        try {
            connection.connect()
            check(connection.responseCode in 200..299) { "Model host returned HTTP ${connection.responseCode}." }
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var received = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        received += count
                        onProgress(name, received, total)
                    }
                }
            }
            check(partial.renameTo(destination)) { "Could not finalize downloaded model file." }
        } catch (error: Throwable) {
            partial.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }
}