package com.jarvis.localai.inference

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LlamaCppEngine : LocalModelEngine {
    private val mutex = Mutex()
    private val mutableStatus = MutableStateFlow<ModelStatus>(ModelStatus.Uninitialized)
    override val status: StateFlow<ModelStatus> = mutableStatus.asStateFlow()

    init {
        System.loadLibrary("jarvis_llama")
    }

    override suspend fun loadModel(files: ModelFiles) = mutex.withLock {
        mutableStatus.value = ModelStatus.Loading
        val startedAt = System.nanoTime()
        val failure = withContext(Dispatchers.IO) {
            nativeLoadModel(files.modelPath, files.projectorPath)
        }
        if (failure != null) {
            mutableStatus.value = ModelStatus.Failed(failure)
            throw IllegalStateException(failure)
        }
        mutableStatus.value = ModelStatus.Ready(
            files.displayName,
            files.projectorPath != null,
            (System.nanoTime() - startedAt) / 1_000_000
        )
    }

    override fun generateText(messages: List<EngineMessage>): Flow<String> = generate(messages, null)

    override fun generateMultimodalResponse(
        messages: List<EngineMessage>,
        image: ImageInput
    ): Flow<String> = generate(messages, image)

    private fun generate(messages: List<EngineMessage>, image: ImageInput?): Flow<String> = flow {
        require(messages.isNotEmpty()) { "Enter a message before sending." }
        mutex.withLock {
            val ready = mutableStatus.value as? ModelStatus.Ready
                ?: throw IllegalStateException("Load a local model before sending a message.")
            if (image != null && !ready.supportsVision) {
                throw IllegalStateException("The active model is text-only. Load a model with a matching vision projector.")
            }
            mutableStatus.value = ModelStatus.Generating
            try {
                val error = withContext(Dispatchers.IO) {
                    nativeStartGeneration(
                        messages.map(EngineMessage::role).toTypedArray(),
                        messages.map(EngineMessage::content).toTypedArray(),
                        image?.rgbBytes,
                        image?.width ?: 0,
                        image?.height ?: 0
                    )
                }
                if (error != null) throw IllegalStateException(error)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val next = withContext(Dispatchers.IO) { nativeNextToken() } ?: break
                    emit(next)
                }
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable + Dispatchers.IO) { nativeCancelGeneration() }
                throw cancelled
            } finally {
                mutableStatus.value = ready
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun cancelGeneration() = withContext(Dispatchers.IO) {
        nativeCancelGeneration()
    }

    override suspend fun unloadModel() = mutex.withLock {
        withContext(Dispatchers.IO) { nativeUnloadModel() }
        mutableStatus.value = ModelStatus.Uninitialized
    }

    override suspend fun releaseResources() = unloadModel()

    private external fun nativeLoadModel(modelPath: String, projectorPath: String?): String?
    private external fun nativeStartGeneration(
        roles: Array<String>,
        contents: Array<String>,
        imageRgb: ByteArray?,
        width: Int,
        height: Int
    ): String?
    private external fun nativeNextToken(): String?
    private external fun nativeCancelGeneration()
    private external fun nativeUnloadModel()
}