package com.jarvis.localai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.jarvis.localai.chat.JarvisViewModel
import com.jarvis.localai.data.LocalDatabase
import com.jarvis.localai.data.PreferencesRepository
import com.jarvis.localai.inference.LlamaCppEngine
import com.jarvis.localai.models.ModelDownloadRepository
import com.jarvis.localai.models.ModelRepository
import com.jarvis.localai.ui.JarvisApp
import com.jarvis.localai.ui.JarvisRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dao = LocalDatabase.create(applicationContext).dao()
        val factory = JarvisViewModelFactory {
            JarvisViewModel(
                application,
                dao,
                LlamaCppEngine(),
                ModelRepository(applicationContext, dao),
                ModelDownloadRepository(applicationContext),
                PreferencesRepository(applicationContext)
            )
        }
        val viewModel = ViewModelProvider(this, factory)[JarvisViewModel::class.java]
        setContent {
            JarvisRoot(viewModel)
        }
    }
}

private class JarvisViewModelFactory(
    private val create: () -> JarvisViewModel
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = create() as T
}