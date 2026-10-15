package com.jarvis.localai.models

data class ModelCatalogEntry(
    val id: String,
    val name: String,
    val repository: String,
    val revision: String,
    val modelFilename: String,
    val projectorFilename: String,
    val modelSizeBytes: Long,
    val projectorSizeBytes: Long,
    val license: String,
    val quantization: String,
    val estimatedMemory: String,
    val contextLength: Int
) {
    fun downloadUrl(filename: String): String =
        "https://huggingface.co/$repository/resolve/$revision/$filename?download=true"
}

object ModelCatalog {
    val entries = listOf(
        ModelCatalogEntry(
            id = "smolvlm-500m-q8",
            name = "SmolVLM 500M Instruct Q8_0",
            repository = "ggml-org/SmolVLM-500M-Instruct-GGUF",
            revision = "72e986006ef53e37cdd3f6d4241c90b0f01df376",
            modelFilename = "SmolVLM-500M-Instruct-Q8_0.gguf",
            projectorFilename = "mmproj-SmolVLM-500M-Instruct-Q8_0.gguf",
            modelSizeBytes = 436_806_912,
            projectorSizeBytes = 108_783_360,
            license = "Apache-2.0",
            quantization = "Q8_0",
            estimatedMemory = "1.5-2.5 GB estimate; device-dependent",
            contextLength = 2048
        )
    )
}