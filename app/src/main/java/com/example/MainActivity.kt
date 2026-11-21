package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.configureAppCheck
import com.example.data.model.ThemePreference
import com.example.ui.StudyLeafApp
import com.example.ui.StudyLeafViewModel
import com.example.ui.theme.StudyLeafTheme

class MainActivity : ComponentActivity() {

    private val pendingExternalUri = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        if (savedInstanceState == null) {
            pendingExternalUri.value = extractIncomingDocumentUri(intent)
        }

        enableEdgeToEdge()
        setContent {
            val viewModel: StudyLeafViewModel = viewModel()
            val themePref by viewModel.themePreference.collectAsStateWithLifecycle()
            val incomingUri by pendingExternalUri

            LaunchedEffect(incomingUri) {
                val uri = incomingUri
                if (uri != null) {
                    viewModel.openExternalUri(uri)
                    pendingExternalUri.value = null
                }
            }

            val isDark = when (themePref) {
                ThemePreference.LIGHT -> false
                ThemePreference.DARK -> true
                ThemePreference.SYSTEM -> isSystemInDarkTheme()
            }
            StudyLeafTheme(darkTheme = isDark) {
                StudyLeafApp(
                    viewModel = viewModel,
                    isDarkTheme = isDark
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractIncomingDocumentUri(intent)?.let { uri ->
            pendingExternalUri.value = uri
        }
    }

    private fun extractIncomingDocumentUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.data ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            }
            Intent.ACTION_SEND -> {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            }
            else -> null
        }
    }
}
