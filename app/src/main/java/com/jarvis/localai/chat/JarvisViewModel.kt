package com.jarvis.localai.chat

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.localai.data.ConversationEntity
import com.jarvis.localai.data.LocalDao
import com.jarvis.localai.data.MessageEntity
import com.jarvis.localai.data.ModelEntity
import com.jarvis.localai.data.PreferencesRepository
import com.jarvis.localai.inference.EngineMessage
import com.jarvis.localai.inference.LlamaCppEngine
import com.jarvis.localai.inference.ModelFiles
import com.jarvis.localai.inference.ModelStatus
import com.jarvis.localai.models.ModelCatalog
import com.jarvis.localai.models.ModelDownloadRepository
import com.jarvis.localai.models.ModelRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class JarvisUiState(
    val conversations: List<ConversationEntity> = emptyList(),
    val messages: List<MessageEntity> = emptyList(),
    val models: List<ModelEntity> = emptyList(),
    val settingsPrompt: String = "You are JARVIS, a concise and helpful private assistant.",
    val settingsTheme: String = "dark",
    val selectedConversationId: String? = null,
    val activeModelId: String? = null,
    val modelStatus: ModelStatus = ModelStatus.Uninitialized,
    val selectedImageUri: Uri? = null,
    val streamingMessageId: Long? = null,
    val streamingText: String = "",
    val generating: Boolean = false,
    val loadingModelId: String? = null,
    val downloadProgress: String? = null,
    val error: String? = null
)

class JarvisViewModel(
    application: Application,
    private val dao: LocalDao,
    private val engine: LlamaCppEngine,
    private val modelRepository: ModelRepository,
    private val downloads: ModelDownloadRepository,
    private val preferences: PreferencesRepository
) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(JarvisUiState())
    val state = mutableState.asStateFlow()
    private var messageObservation: Job? = null
    private var generationJob: Job? = null
    private var downloadJob: Job? = null

    init {
        viewModelScope.launch {
            dao.observeConversations().collect { items -> mutableState.update { it.copy(conversations = items) } }
        }
        viewModelScope.launch {
            modelRepository.models.collect { items -> mutableState.update { it.copy(models = items) } }
        }
        viewModelScope.launch {
            preferences.settings.collect { prefs ->
                mutableState.update { it.copy(settingsPrompt = prefs.systemPrompt, settingsTheme = prefs.theme) }
            }
        }
        viewModelScope.launch {
            preferences.activeModelId.collect { id -> mutableState.update { it.copy(activeModelId = id) } }
        }
        viewModelScope.launch {
            engine.status.collect { status -> mutableState.update { it.copy(modelStatus = status) } }
        }
        viewModelScope.launch {
            if (dao.observeConversations().first().isEmpty()) createConversation()
            else openConversation(dao.observeConversations().first().first().id)
            preferences.activeModelId.first()?.let { id ->
                dao.getModel(id)?.takeIf { File(it.modelPath).isFile }?.let { model ->
                    runCatching { loadModel(model) }.onFailure { setError(it.message ?: "Could not restore the selected model.") }
                }
            }
        }
    }

    fun clearError() = mutableState.update { it.copy(error = null) }

    fun setSelectedImage(uri: Uri?) = mutableState.update { it.copy(selectedImageUri = uri) }

    fun selectConversation(id: String) = viewModelScope.launch { openConversation(id) }

    fun createConversation() = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val conversation = ConversationEntity(UUID.randomUUID().toString(), "New conversation", now, now)
        dao.saveConversation(conversation)
        openConversation(conversation.id)
    }

    fun renameConversation(id: String, title: String) = viewModelScope.launch {
        val current = dao.getConversation(id) ?: return@launch
        dao.saveConversation(current.copy(title = title.trim().ifBlank { "New conversation" }, updatedAt = System.currentTimeMillis()))
    }

    fun deleteConversation(id: String) = viewModelScope.launch {
        dao.deleteConversationMessages(id)
        dao.deleteConversation(id)
        val remaining = dao.observeConversations().first()
        if (remaining.isEmpty()) createConversation() else if (state.value.selectedConversationId == id) openConversation(remaining.first().id)
    }

    fun clearCurrentConversation() = viewModelScope.launch {
        state.value.selectedConversationId?.let { dao.clearMessages(it) }
    }

    fun saveSystemPrompt(value: String) = viewModelScope.launch { preferences.saveSystemPrompt(value) }

    fun saveTheme(value: String) = viewModelScope.launch { preferences.saveTheme(value) }

    fun sendMessage(text: String) {
        val cleaned = text.trim()
        val imageUri = state.value.selectedImageUri
        if (cleaned.isBlank() && imageUri == null) return
        if (state.value.generating) return
        generationJob = viewModelScope.launch { generateMessage(cleaned, imageUri, null) }
    }

    fun stopGeneration() = viewModelScope.launch {
        engine.cancelGeneration()
        generationJob?.cancelAndJoin()
    }

    fun regenerateLastResponse() = viewModelScope.launch {
        val conversationId = state.value.selectedConversationId ?: return@launch
        val messages = dao.observeMessages(conversationId).first()
        val lastUser = messages.lastOrNull { it.role == "user" } ?: return@launch
        val previousAssistant = messages.lastOrNull { it.role == "assistant" }
        if (previousAssistant != null && previousAssistant.createdAt >= lastUser.createdAt) dao.deleteMessage(previousAssistant.id)
        if (state.value.generating) return@launch
        val image = lastUser.imageUri?.let { Uri.fromFile(File(it)) }
        generationJob = viewModelScope.launch { generateMessage(lastUser.content, image, lastUser) }
    }

    fun loadModel(model: ModelEntity) = viewModelScope.launch {
        mutableState.update { it.copy(loadingModelId = model.id, error = null) }
        try {
            engine.unloadModel()
            engine.loadModel(ModelFiles(model.modelPath, model.projectorPath, model.displayName))
            preferences.saveActiveModel(model.id)
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            setError(error.message ?: "Could not load this model.")
        } finally {
            mutableState.update { it.copy(loadingModelId = null) }
        }
    }

    fun unloadModel() = viewModelScope.launch {
        engine.unloadModel()
        preferences.saveActiveModel(null)
    }

    fun importModels(uris: List<Uri>) = viewModelScope.launch {
        runCatching { modelRepository.importDocuments(uris) }
            .onFailure { setError(it.message ?: "Could not import the selected model files.") }
    }

    fun downloadCatalogModel() {
        if (downloadJob?.isActive == true) return
        val entry = ModelCatalog.entries.first()
        downloadJob = viewModelScope.launch {
            mutableState.update { it.copy(downloadProgress = "Starting download", error = null) }
            try {
                downloads.download(entry) { filename, received, total ->
                    mutableState.update {
                        val size = if (total > 0) "${received / 1_048_576} / ${total / 1_048_576} MB" else "${received / 1_048_576} MB"
                        it.copy(downloadProgress = "$filename: $size")
                    }
                }
                modelRepository.registerDownload(entry)
                mutableState.update { it.copy(downloadProgress = null) }
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                setError(error.message ?: "Model download failed.")
            } finally {
                mutableState.update { it.copy(downloadProgress = null) }
            }
        }
    }

    fun cancelDownload() = downloadJob?.cancel()

    fun deleteModel(model: ModelEntity) = viewModelScope.launch {
        if (model.id == state.value.activeModelId) {
            setError("Unload the active model before deleting it.")
            return@launch
        }
        runCatching { modelRepository.delete(model) }
            .onFailure { setError(it.message ?: "Could not delete the model.") }
    }

    private suspend fun openConversation(id: String) {
        messageObservation?.cancel()
        mutableState.update { it.copy(selectedConversationId = id) }
        messageObservation = viewModelScope.launch {
            dao.observeMessages(id).collect { messages -> mutableState.update { it.copy(messages = messages) } }
        }
    }

    private suspend fun generateMessage(text: String, imageUri: Uri?, existingUser: MessageEntity?) {
        val conversationId = state.value.selectedConversationId ?: return
        val model = state.value.models.firstOrNull { it.id == state.value.activeModelId }
        if (model == null || state.value.modelStatus !is ModelStatus.Ready) {
            setError("Load a local model in Models before sending a prompt.")
            return
        }
        mutableState.update { it.copy(generating = true, error = null, selectedImageUri = null, streamingText = "") }
        var assistantId: Long? = null
        val response = StringBuilder()
        try {
            val image = imageUri?.let { ImagePreprocessor.prepare(getApplication(), it) }
            val question = text.ifBlank { "Describe this image." }
            val currentMessages = dao.observeMessages(conversationId).first()
            val historyMessages = if (existingUser == null) currentMessages else currentMessages.filterNot { it.id == existingUser.id }
            if (existingUser == null) {
                dao.insertMessage(MessageEntity(
                    conversationId = conversationId,
                    role = "user",
                    content = question,
                    imageUri = image?.savedFile?.absolutePath,
                    createdAt = System.currentTimeMillis()
                ))
                val conversation = dao.getConversation(conversationId)
                if (conversation != null) {
                    val title = if (currentMessages.isEmpty()) question.take(40) else conversation.title
                    dao.saveConversation(conversation.copy(title = title, updatedAt = System.currentTimeMillis()))
                }
            }
            assistantId = dao.insertMessage(MessageEntity(
                conversationId = conversationId,
                role = "assistant",
                content = "",
                createdAt = System.currentTimeMillis()
            ))
            mutableState.update { it.copy(streamingMessageId = assistantId) }
            val history = historyMessages.map { EngineMessage(it.role, it.content) } + EngineMessage("user", question)
            val prompt = preferences.settings.first().systemPrompt
            val promptHistory = listOf(EngineMessage("system", prompt)) + history
            val stream = if (image != null) engine.generateMultimodalResponse(promptHistory, image.input)
            else engine.generateText(promptHistory)
            stream.collect { token ->
                response.append(token)
                mutableState.update { it.copy(streamingText = response.toString()) }
            }
            dao.updateMessage(assistantId, response.toString())
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) {
                assistantId?.let { id ->
                    if (response.isNotEmpty()) dao.updateMessage(id, response.toString()) else dao.deleteMessage(id)
                }
                throw error
            }
            assistantId?.let { id ->
                if (response.isNotEmpty()) dao.updateMessage(id, response.toString()) else dao.deleteMessage(id)
            }
            setError(error.message ?: "Local generation failed. Check the model and available memory.")
        } finally {
            mutableState.update { it.copy(generating = false, streamingMessageId = null, streamingText = "") }
        }
    }

    private fun setError(message: String) = mutableState.update { it.copy(error = message) }

    override fun onCleared() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { engine.releaseResources() }
        super.onCleared()
    }
}