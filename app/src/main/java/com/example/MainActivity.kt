package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.ThemePreference
import com.example.ui.StudyLeafApp
import com.example.ui.StudyLeafViewModel
import com.example.ui.theme.StudyLeafTheme
import com.example.update.InAppUpdateController

class MainActivity : ComponentActivity() {

    private val pendingExternalUri = mutableStateOf<Uri?>(null)
    private lateinit var inAppUpdateController: InAppUpdateController

    private val inAppUpdateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (::inAppUpdateController.isInitialized) {
            inAppUpdateController.onActivityResult(result.resultCode)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        inAppUpdateController = InAppUpdateController(applicationContext)
        inAppUpdateController.attach(inAppUpdateLauncher)

        if (savedInstanceState == null) {
            pendingExternalUri.value = extractIncomingDocumentUri(intent)
        }

        enableEdgeToEdge()
        setContent {
            val viewModel: StudyLeafViewModel = viewModel()
            val themePref by viewModel.themePreference.collectAsStateWithLifecycle()
            val updateState by inAppUpdateController.state.collectAsStateWithLifecycle()
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
                    isDarkTheme = isDark,
                    updateState = updateState,
                    onCheckForUpdates = { inAppUpdateController.checkForUpdate(userInitiated = true) },
                    onStartInAppUpdate = { inAppUpdateController.startPlayFlexibleUpdate() },
                    onCompleteInAppUpdate = { inAppUpdateController.completeDownloadedUpdate() },
                    onDismissUpdateBanner = { inAppUpdateController.dismissBanner() },
                    onOpenExternalRelease = { url ->
                        inAppUpdateController.openExternalReleasePage(this@MainActivity, url)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::inAppUpdateController.isInitialized) {
            inAppUpdateController.checkOnResume()
        }
    }

    override fun onDestroy() {
        if (::inAppUpdateController.isInitialized) {
            inAppUpdateController.detach()
        }
        super.onDestroy()
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
