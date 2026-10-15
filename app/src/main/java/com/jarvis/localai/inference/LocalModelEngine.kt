package com.jarvis.localai.inference

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class EngineMessage(val role: String, val content: String)

data class ImageInput(val width: Int, val height: Int, val rgbBytes: ByteArray)

data class ModelFiles(val modelPath: String, val projectorPath: String? = null, val displayName: String)

sealed interface ModelStatus {
    data object Uninitialized : ModelStatus
    data object Loading : ModelStatus
    data class Ready(val modelName: String, val supportsVision: Boolean, val loadTimeMs: Long) : ModelStatus
    data object Generating : ModelStatus
    data class Failed(val message: String) : ModelStatus
}

interface LocalModelEngine {
    val status: StateFlow<ModelStatus>
    suspend fun loadModel(files: ModelFiles)
    fun generateText(messages: List<EngineMessage>): Flow<String>
    fun generateMultimodalResponse(messages: List<EngineMessage>, image: ImageInput): Flow<String>
    suspend fun cancelGeneration()
    suspend fun unloadModel()
    suspend fun releaseResources()
}