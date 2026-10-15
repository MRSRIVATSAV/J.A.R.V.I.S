package com.jarvis.localai.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.jarvis.localai.chat.JarvisUiState
import com.jarvis.localai.chat.JarvisViewModel
import com.jarvis.localai.data.ConversationEntity
import com.jarvis.localai.data.MessageEntity
import com.jarvis.localai.data.ModelEntity
import com.jarvis.localai.inference.ModelStatus
import com.jarvis.localai.models.ModelCatalog
import java.io.File
import java.text.DateFormat
import java.util.Date

private data class JarvisPalette(
    val ink: Color,
    val surface: Color,
    val elevated: Color,
    val border: Color,
    val cyan: Color,
    val blue: Color,
    val mint: Color,
    val muted: Color,
    val danger: Color,
    val text: Color,
    val secondaryText: Color,
    val userBubble: Color,
    val errorSurface: Color,
    val errorText: Color
)

private val darkPalette = JarvisPalette(
    Color(0xFF050A0F), Color(0xFF0B141C), Color(0xFF101F29), Color(0xFF1D3947),
    Color(0xFF53DDF5), Color(0xFF3D91E8), Color(0xFF7FE4C1), Color(0xFF9CB2BC),
    Color(0xFFFF8D8D), Color(0xFFE8F2F6), Color(0xFFCCDFE6), Color(0xFF102B3A),
    Color(0xFF301719), Color(0xFFFFD5D5)
)
private val lightPalette = JarvisPalette(
    Color(0xFFF1F6F8), Color(0xFFFFFFFF), Color(0xFFE4EEF1), Color(0xFFB8CDD3),
    Color(0xFF007B8C), Color(0xFF1769A6), Color(0xFF137A56), Color(0xFF526A73),
    Color(0xFFAA3434), Color(0xFF14262D), Color(0xFF405B64), Color(0xFFDCEEF3),
    Color(0xFFFCE9E9), Color(0xFF671F24)
)
private val LocalJarvisPalette = compositionLocalOf { darkPalette }
private val Ink: Color @Composable get() = LocalJarvisPalette.current.ink
private val SurfaceColor: Color @Composable get() = LocalJarvisPalette.current.surface
private val ElevatedColor: Color @Composable get() = LocalJarvisPalette.current.elevated
private val BorderColor: Color @Composable get() = LocalJarvisPalette.current.border
private val Cyan: Color @Composable get() = LocalJarvisPalette.current.cyan
private val Blue: Color @Composable get() = LocalJarvisPalette.current.blue
private val Mint: Color @Composable get() = LocalJarvisPalette.current.mint
private val Muted: Color @Composable get() = LocalJarvisPalette.current.muted
private val Danger: Color @Composable get() = LocalJarvisPalette.current.danger
private val PrimaryText: Color @Composable get() = LocalJarvisPalette.current.text
private val SecondaryText: Color @Composable get() = LocalJarvisPalette.current.secondaryText
private val ErrorSurface: Color @Composable get() = LocalJarvisPalette.current.errorSurface
private val ErrorText: Color @Composable get() = LocalJarvisPalette.current.errorText

private enum class AppPage { Chat, Models, Settings }

@Composable
fun JarvisRoot(viewModel: JarvisViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    JarvisTheme(isDark = state.settingsTheme == "dark") { JarvisApp(viewModel) }
}

@Composable
private fun JarvisTheme(isDark: Boolean, content: @Composable () -> Unit) {
    val palette = if (isDark) darkPalette else lightPalette
    CompositionLocalProvider(LocalJarvisPalette provides palette) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = palette.cyan,
                secondary = palette.mint,
                background = palette.ink,
                surface = palette.surface,
                onPrimary = palette.ink,
                onBackground = palette.text,
                onSurface = palette.text
            ),
            content = content
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun JarvisApp(viewModel: JarvisViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var page by remember { mutableStateOf(AppPage.Chat) }
    var showHistory by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        viewModel.setSelectedImage(uri)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.importModels(uris)
    }

    Scaffold(
        containerColor = Ink,
        contentWindowInsets = WindowInsets.navigationBars
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .imePadding()
        ) {
            TopBar(
                state = state,
                onHistory = { showHistory = true },
                onNewChat = viewModel::createConversation,
                onModels = { page = AppPage.Models },
                onClear = { confirmClear = true }
            )
            state.error?.let { ErrorStrip(it, viewModel::clearError) }
            AnimatedContent(targetState = page, label = "page") { target ->
                when (target) {
                    AppPage.Chat -> ChatPage(
                        state = state,
                        onPickImage = { imagePicker.launch("image/*") },
                        onRemoveImage = { viewModel.setSelectedImage(null) },
                        onSend = viewModel::sendMessage,
                        onStop = viewModel::stopGeneration,
                        onRegenerate = viewModel::regenerateLastResponse
                    )
                    AppPage.Models -> ModelsPage(
                        state = state,
                        onImport = { filePicker.launch(arrayOf("*/*")) },
                        onDownload = viewModel::downloadCatalogModel,
                        onCancelDownload = viewModel::cancelDownload,
                        onLoad = viewModel::loadModel,
                        onUnload = viewModel::unloadModel,
                        onDelete = viewModel::deleteModel
                    )
                    AppPage.Settings -> SettingsPage(state, viewModel::saveSystemPrompt, viewModel::saveTheme)
                }
            }
            BottomBar(page = page, onSelect = { page = it })
        }
    }

    if (showHistory) {
        ModalBottomSheet(
            onDismissRequest = { showHistory = false },
            containerColor = SurfaceColor,
            contentColor = PrimaryText
        ) {
            HistorySheet(
                conversations = state.conversations,
                selectedId = state.selectedConversationId,
                onSelect = { viewModel.selectConversation(it); showHistory = false },
                onRename = viewModel::renameConversation,
                onDelete = viewModel::deleteConversation
            )
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear this conversation?") },
            text = { Text("Messages in this conversation will be deleted from this device.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearCurrentConversation(); confirmClear = false }) { Text("Clear", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
            containerColor = SurfaceColor
        )
    }
}

@Composable
private fun TopBar(state: JarvisUiState, onHistory: () -> Unit, onNewChat: () -> Unit, onModels: () -> Unit, onClear: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Ink).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusOrb(state.modelStatus)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("JARVIS LOCAL", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    fontSize = 16.sp, color = PrimaryText, letterSpacing = 0.sp)
                Text("Your private AI. Running on your device.", fontSize = 11.sp, color = Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            HeaderButton("History", onHistory)
            Spacer(Modifier.width(5.dp))
            HeaderButton("New", onNewChat)
        }
        Row(Modifier.padding(start = 39.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val statusText = when (val status = state.modelStatus) {
                ModelStatus.Uninitialized -> "NO MODEL LOADED"
                ModelStatus.Loading -> "LOADING MODEL"
                is ModelStatus.Ready -> if (status.supportsVision) "READY · TEXT + VISION" else "READY · TEXT ONLY"
                ModelStatus.Generating -> "GENERATING"
                is ModelStatus.Failed -> "MODEL ERROR"
            }
            Text(statusText, color = if (state.modelStatus is ModelStatus.Ready) Mint else Muted,
                fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 0.sp)
            Spacer(Modifier.width(8.dp))
            val modelName = state.models.firstOrNull { it.id == state.activeModelId }?.displayName
                ?: (state.modelStatus as? ModelStatus.Ready)?.modelName
                ?: "Choose a local model"
            val loadedTime = (state.modelStatus as? ModelStatus.Ready)?.loadTimeMs
            Text(
                text = if (loadedTime != null) "$modelName · ${loadedTime} ms" else modelName,
                color = SecondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onModels, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text("Models", color = Cyan, fontSize = 12.sp)
            }
            TextButton(onClick = onClear, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text("Clear", color = Muted, fontSize = 12.sp)
            }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp), color = BorderColor.copy(alpha = 0.8f))
    }
}

@Composable
private fun StatusOrb(status: ModelStatus) {
    val color = when (status) {
        is ModelStatus.Ready -> Mint
        ModelStatus.Loading, ModelStatus.Generating -> Cyan
        is ModelStatus.Failed -> Danger
        ModelStatus.Uninitialized -> Muted
    }
    Box(
        Modifier.size(29.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(11.dp).clip(CircleShape).background(color))
    }
}

@Composable
private fun HeaderButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
        Text(label, color = Cyan, fontSize = 12.sp)
    }
}

@Composable
private fun ChatPage(
    state: JarvisUiState,
    onPickImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onRegenerate: () -> Unit
) {
    var prompt by remember { mutableStateOf("") }
    val context = LocalContext.current
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.streamingText) {
        val count = state.messages.size
        if (count > 0) listState.animateScrollToItem(count - 1)
    }
    Column(Modifier.fillMaxSize()) {
        if (state.messages.isEmpty()) {
            WelcomePanel(Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                items(state.messages, key = { it.id }) { message ->
                    val visibleText = if (message.id == state.streamingMessageId) state.streamingText else message.content
                    MessageBubble(
                        message = message,
                        text = visibleText,
                        onCopy = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("JARVIS response", visibleText))
                        },
                        onRegenerate = onRegenerate
                    )
                }
            }
        }
        state.selectedImageUri?.let { uri ->
            Row(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = uri, contentDescription = "Selected image preview",
                    modifier = Modifier.size(62.dp).clip(RoundedCornerShape(6.dp)))
                Spacer(Modifier.width(10.dp))
                Text("Image attached", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onRemoveImage) { Text("Remove", color = Danger) }
            }
        }
        Composer(
            prompt = prompt,
            onPromptChange = { prompt = it },
            onPickImage = onPickImage,
            onSend = { onSend(prompt); prompt = "" },
            onStop = onStop,
            generating = state.generating,
            modelReady = state.modelStatus is ModelStatus.Ready
        )
    }
}

@Composable
private fun WelcomePanel(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Text("J", fontFamily = FontFamily.Monospace, fontSize = 44.sp, color = Cyan, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(12.dp))
        Text("JARVIS LOCAL", fontFamily = FontFamily.Monospace, fontSize = 17.sp,
            color = PrimaryText, fontWeight = FontWeight.SemiBold)
        Text("Your private AI. Running on your device.", color = Muted, fontSize = 13.sp,
            modifier = Modifier.padding(top = 7.dp))
        Spacer(Modifier.height(18.dp))
        Text("Load a compatible model to begin. Prompts and images stay on this device; model downloads are optional.",
            color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun MessageBubble(message: MessageEntity, text: String, onCopy: () -> Unit, onRegenerate: () -> Unit) {
    val user = message.role == "user"
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (user) Alignment.End else Alignment.Start) {
        Text(if (user) "YOU" else "JARVIS", color = if (user) Blue else Cyan,
            fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp))
        Surface(
            color = if (user) LocalJarvisPalette.current.userBubble else SurfaceColor,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, if (user) Blue.copy(alpha = 0.45f) else BorderColor),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(Modifier.padding(12.dp)) {
                message.imageUri?.let { path ->
                    AsyncImage(model = File(path), contentDescription = "Attached image",
                        modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(5.dp)))
                    Spacer(Modifier.height(8.dp))
                }
                Text(text.ifBlank { if (message.role == "assistant") " " else "" }, color = PrimaryText, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
        if (!user && text.isNotBlank()) {
            Row {
                TextButton(onClick = onCopy) { Text("Copy", color = Muted, fontSize = 11.sp) }
                TextButton(onClick = onRegenerate) { Text("Regenerate", color = Muted, fontSize = 11.sp) }
            }
        }
    }
}

@Composable
private fun Composer(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onPickImage: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    generating: Boolean,
    modelReady: Boolean
) {
    Column(Modifier.fillMaxWidth().background(Ink).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Surface(
            color = SurfaceColor,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, BorderColor),
            modifier = Modifier.fillMaxWidth().animateContentSize()
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                BasicTextField(
                    value = prompt,
                    onValueChange = onPromptChange,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 6.dp),
                    enabled = !generating,
                    minLines = 1,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    textStyle = androidx.compose.ui.text.TextStyle(color = PrimaryText, fontSize = 14.sp, lineHeight = 20.sp),
                    decorationBox = { inner ->
                        Box {
                            if (prompt.isBlank()) Text("Ask JARVIS anything...", color = Muted, fontSize = 14.sp)
                            inner()
                        }
                    }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onPickImage, enabled = !generating) { Text("Attach image", color = Cyan, fontSize = 12.sp) }
                    Spacer(Modifier.weight(1f))
                    if (generating) {
                        OutlinedButton(onClick = onStop, border = BorderStroke(1.dp, Danger),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger)) {
                            Text("Stop")
                        }
                    } else {
                        Button(onClick = onSend, enabled = modelReady, colors = ButtonDefaults.buttonColors(containerColor = Cyan)) {
                            Text("Send", color = Ink, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        Text(
            if (modelReady) "Inference stays on this device" else "Load a model before sending",
            color = Muted, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 5.dp)
        )
    }
}

@Composable
private fun ModelsPage(
    state: JarvisUiState,
    onImport: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onLoad: (ModelEntity) -> Unit,
    onUnload: () -> Unit,
    onDelete: (ModelEntity) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("MODEL LIBRARY", color = PrimaryText, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("GGUF · llama.cpp · ARM64", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
            }
            OutlinedButton(onClick = onImport, border = BorderStroke(1.dp, BorderColor)) { Text("Import", color = Cyan) }
        }
        val freeBytes = LocalContext.current.filesDir.usableSpace
        Text("App-private storage free: ${formatBytes(freeBytes)}", color = Muted, fontSize = 10.sp,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, end = 14.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { CatalogModelCard(state, onDownload, onCancelDownload) }
            item { Text("ON THIS DEVICE", color = Muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp)) }
            if (state.models.isEmpty()) item { InfoPanel("No model files imported", "Import a GGUF model and, for vision models, its matching mmproj GGUF.") }
            items(state.models, key = { it.id }) { model ->
                InstalledModelCard(
                    model = model,
                    active = model.id == state.activeModelId && state.modelStatus is ModelStatus.Ready,
                    loading = model.id == state.loadingModelId,
                    onLoad = { onLoad(model) },
                    onUnload = onUnload,
                    onDelete = { onDelete(model) }
                )
            }
        }
    }
}

@Composable
private fun CatalogModelCard(state: JarvisUiState, onDownload: () -> Unit, onCancel: () -> Unit) {
    val entry = ModelCatalog.entries.first()
    Column(Modifier.fillMaxWidth().background(SurfaceColor, RoundedCornerShape(8.dp)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("OPTIONAL DOWNLOAD", color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.weight(1f))
            Text(entry.license, color = Mint, fontSize = 10.sp)
        }
        Text(entry.name, color = PrimaryText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
            modifier = Modifier.padding(top = 7.dp))
        Text("Vision-language · GGUF + matching projector · ${entry.quantization}", color = Muted, fontSize = 11.sp,
            modifier = Modifier.padding(top = 5.dp))
        Text("Files: ${formatBytes(entry.modelSizeBytes)} + ${formatBytes(entry.projectorSizeBytes)} · Context ${entry.contextLength}",
            color = Muted, fontSize = 11.sp,
            modifier = Modifier.padding(top = 3.dp))
        Text("Estimated memory: ${entry.estimatedMemory}", color = Muted, fontSize = 11.sp,
            modifier = Modifier.padding(top = 3.dp))
        Text("Official ggml-org Hugging Face files. Runtime compatibility is documented; target-device memory and speed are not benchmarked.",
            color = Muted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 9.dp))
        state.downloadProgress?.let { progress ->
            Text(progress, color = Cyan, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 5.dp), color = Cyan, trackColor = BorderColor)
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("No automatic first-launch download", color = Muted, fontSize = 10.sp, modifier = Modifier.weight(1f))
            if (state.downloadProgress != null) {
                OutlinedButton(onClick = onCancel, border = BorderStroke(1.dp, Danger)) { Text("Cancel", color = Danger) }
            } else {
                Button(onClick = onDownload, colors = ButtonDefaults.buttonColors(containerColor = Cyan)) {
                    Text("Download pair", color = Ink)
                }
            }
        }
    }
}

@Composable
private fun InstalledModelCard(
    model: ModelEntity,
    active: Boolean,
    loading: Boolean,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onDelete: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(SurfaceColor, RoundedCornerShape(8.dp)).padding(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(model.displayName, color = PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("${model.format} · ${model.quantization} · ${formatBytes(model.fileSizeBytes)}", color = Muted, fontSize = 10.sp,
                    modifier = Modifier.padding(top = 4.dp))
            }
            if (active) Text("ACTIVE", color = Mint, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }
        Text(
            if (model.projectorPath == null) "No vision projector attached; image input is unavailable for this entry" else "Projector present; native load validates the model/projector pairing",
            color = Muted, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 7.dp)
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (active) OutlinedButton(onClick = onUnload, border = BorderStroke(1.dp, BorderColor)) { Text("Unload", color = Cyan) }
            else Button(onClick = onLoad, enabled = !loading, colors = ButtonDefaults.buttonColors(containerColor = Blue)) {
                if (loading) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Load", color = Color.White)
            }
            TextButton(onClick = onDelete, enabled = !active) { Text("Delete", color = if (active) Muted else Danger) }
        }
    }
}

@Composable
private fun SettingsPage(state: JarvisUiState, onSavePrompt: (String) -> Unit, onSaveTheme: (String) -> Unit) {
    var prompt by remember(state.settingsPrompt) { mutableStateOf(state.settingsPrompt) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("SETTINGS", color = PrimaryText, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("Theme", color = Cyan, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.settingsTheme == "dark", onClick = { onSaveTheme("dark") }, label = { Text("Dark") })
            FilterChip(selected = state.settingsTheme == "light", onClick = { onSaveTheme("light") }, label = { Text("Light") })
        }
        Text("System prompt", color = Cyan, fontSize = 12.sp)
        OutlinedTextField(value = prompt, onValueChange = { prompt = it }, modifier = Modifier.fillMaxWidth(), minLines = 4,
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Cyan,
                unfocusedBorderColor = BorderColor,
                focusedTextColor = PrimaryText,
                unfocusedTextColor = PrimaryText
            ))
        Button(onClick = { onSavePrompt(prompt) }, colors = ButtonDefaults.buttonColors(containerColor = Cyan)) {
            Text("Save system prompt", color = Ink)
        }
        InfoPanel("LOCAL PROCESSING", "Chat prompts and selected images are processed on this device. The optional Download action contacts Hugging Face for the two selected model files. No account, paid API, analytics, or advertising SDK is used.")
        InfoPanel("LIMITS", "GGUF files are checked for their file signature, then validated by llama.cpp when loaded. A matching vision projector is required for image input. Available RAM, thermal behavior, and generation speed vary by device.")
    }
}

@Composable
private fun HistorySheet(
    conversations: List<ConversationEntity>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var renaming by remember { mutableStateOf<ConversationEntity?>(null) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
        Text("CONVERSATIONS", color = Cyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 12.dp))
        conversations.forEach { conversation ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).background(
                if (conversation.id == selectedId) ElevatedColor else Color.Transparent, RoundedCornerShape(6.dp)
            ).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { onSelect(conversation.id) }.padding(vertical = 4.dp)) {
                    Text(conversation.title, color = PrimaryText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(conversation.updatedAt)), color = Muted, fontSize = 10.sp)
                }
                TextButton(onClick = { renaming = conversation }) { Text("Rename", color = Cyan, fontSize = 10.sp) }
                TextButton(onClick = { onDelete(conversation.id) }) { Text("Delete", color = Danger, fontSize = 10.sp) }
            }
        }
    }
    renaming?.let { conversation ->
        var value by remember(conversation) { mutableStateOf(conversation.title) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename conversation") },
            text = { OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onRename(conversation.id, value); renaming = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
            containerColor = SurfaceColor
        )
    }
}

@Composable
private fun BottomBar(page: AppPage, onSelect: (AppPage) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(SurfaceColor).padding(horizontal = 14.dp, vertical = 6.dp)
            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().coerceAtMost(8.dp)),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        PageButton("Chat", page == AppPage.Chat) { onSelect(AppPage.Chat) }
        PageButton("Models", page == AppPage.Models) { onSelect(AppPage.Models) }
        PageButton("Settings", page == AppPage.Settings) { onSelect(AppPage.Settings) }
    }
}

@Composable
private fun PageButton(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, color = if (selected) Cyan else Muted, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun ErrorStrip(message: String, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(ErrorSurface).padding(start = 12.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(message, color = ErrorText, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onDismiss) { Text("Close", color = Danger, fontSize = 10.sp) }
    }
}

@Composable
private fun InfoPanel(title: String, body: String) {
    Column(Modifier.fillMaxWidth().background(SurfaceColor, RoundedCornerShape(6.dp)).padding(12.dp)) {
        Text(title, color = Cyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        Text(body, color = Muted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.0f MB".format(bytes / 1_048_576.0)
    else -> "$bytes B"
}