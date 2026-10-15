package com.jarvis.localai.models

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.jarvis.localai.data.LocalDao
import com.jarvis.localai.data.ModelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ModelRepository(private val context: Context, private val dao: LocalDao) {
    val models: Flow<List<ModelEntity>> = dao.observeModels()
    private val directory = File(context.filesDir, "models").apply { mkdirs() }

    suspend fun importDocuments(uris: List<Uri>): List<ModelEntity> = withContext(Dispatchers.IO) {
        require(uris.isNotEmpty()) { "Choose at least one GGUF file." }
        val imported = mutableListOf<File>()
        try {
            val copied = uris.map { uri -> copyDocument(uri).also(imported::add) }
            val projectors = copied.filter { it.name.contains("mmproj", ignoreCase = true) }
            val modelFiles = copied.filterNot { it.name.contains("mmproj", ignoreCase = true) }
            require(modelFiles.isNotEmpty()) { "Select a model GGUF. A projector file must be imported together with its model." }

            modelFiles.map { modelFile ->
                require(GgufFileValidator.hasHeader(modelFile)) { "${modelFile.name} is not a GGUF file." }
                val matchedProjector = GgufFileValidator.matchProjector(modelFile, projectors)
                if (matchedProjector != null) {
                    require(GgufFileValidator.hasHeader(matchedProjector)) { "${matchedProjector.name} is not a GGUF file." }
                }
                val record = ModelEntity(
                    id = UUID.randomUUID().toString(),
                    displayName = modelFile.name.substringBeforeLast('.'),
                    modelPath = modelFile.absolutePath,
                    projectorPath = matchedProjector?.absolutePath,
                    format = "GGUF",
                    quantization = modelFile.name.substringAfterLast('-', "Unknown").substringBefore('.'),
                    fileSizeBytes = modelFile.length() + (matchedProjector?.length() ?: 0L),
                    createdAt = System.currentTimeMillis()
                )
                dao.saveModel(record)
                record
            }
        } catch (error: Throwable) {
            imported.forEach(File::delete)
            throw error
        }
    }

    suspend fun registerDownload(entry: ModelCatalogEntry): ModelEntity = withContext(Dispatchers.IO) {
        val model = File(directory, entry.modelFilename)
        val projector = File(directory, entry.projectorFilename)
        require(GgufFileValidator.hasHeader(model) && GgufFileValidator.hasHeader(projector)) {
            "Downloaded files failed GGUF validation. Remove them and try again."
        }
        val record = ModelEntity(
            id = entry.id,
            displayName = entry.name,
            modelPath = model.absolutePath,
            projectorPath = projector.absolutePath,
            format = "GGUF",
            quantization = entry.quantization,
            fileSizeBytes = model.length() + projector.length(),
            createdAt = System.currentTimeMillis()
        )
        dao.saveModel(record)
        record
    }

    suspend fun delete(model: ModelEntity) = withContext(Dispatchers.IO) {
        dao.deleteModel(model.id)
        File(model.modelPath).delete()
        model.projectorPath?.let(::File)?.delete()
    }

    private fun copyDocument(uri: Uri): File {
        val resolver = context.contentResolver
        val displayName = queryName(resolver, uri) ?: "imported-${UUID.randomUUID()}.gguf"
        require(displayName.endsWith(".gguf", ignoreCase = true)) { "Only GGUF model and projector files are supported." }
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val modelDirectory = File(directory, UUID.randomUUID().toString()).apply { mkdirs() }
        val destination = File(modelDirectory, safeName)
        try {
            val input = resolver.openInputStream(uri) ?: error("Could not open the selected document.")
            input.use { source -> destination.outputStream().use(source::copyTo) }
            require(GgufFileValidator.hasHeader(destination)) { "The selected file is not a GGUF file." }
            return destination
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
    }

    private fun queryName(resolver: ContentResolver, uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

}