package com.example.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.studyleaf.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed interface InAppUpdatePhase {
    data object Idle : InAppUpdatePhase
    data object Checking : InAppUpdatePhase
    data class UpToDate(val currentVersion: String) : InAppUpdatePhase
    data class PlayUpdateAvailable(val availableVersionCode: Int) : InAppUpdatePhase
    data class Downloading(
        val progressFraction: Float,
        val downloadedMb: Float,
        val totalMb: Float
    ) : InAppUpdatePhase
    data object ReadyToInstall : InAppUpdatePhase
    data class ExternalReleaseAvailable(
        val latestVersion: String,
        val releaseNotes: String,
        val releaseUrl: String
    ) : InAppUpdatePhase
    data class Error(val message: String) : InAppUpdatePhase
}

data class InAppUpdateUiState(
    val phase: InAppUpdatePhase = InAppUpdatePhase.Idle,
    val bannerDismissed: Boolean = false
)

/**
 * Policy-compliant in-app update controller:
 * 1. Uses Google Play In-App Updates (Flexible flow) so Play Store users download & apply
 *    updates directly inside StudyLeaf without ever leaving the app to open the Play Store.
 * 2. When running a non-Play build (direct GitHub build / emulator), checks GitHub Releases
 *    metadata to notify the user of newer versions and opens the release page via ACTION_VIEW.
 */
class InAppUpdateController(private val appContext: Context) {

    companion object {
        private const val TAG = "StudyLeafUpdate"
        private const val GITHUB_LATEST_RELEASE_API =
            "https://api.github.com/repos/onilana/StudyLeaf/releases/latest"
        private const val GITHUB_RELEASES_FALLBACK_URL =
            "https://github.com/onilana/StudyLeaf/releases/latest"
    }

    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(InAppUpdateUiState())
    val state: StateFlow<InAppUpdateUiState> = _state.asStateFlow()

    private var updateLauncher: ActivityResultLauncher<IntentSenderRequest>? = null
    private var cachedAppUpdateInfo: AppUpdateInfo? = null
    private var isListenerRegistered = false

    private val installStateListener = InstallStateUpdatedListener { installState ->
        when (installState.installStatus()) {
            InstallStatus.PENDING -> {
                _state.value = InAppUpdateUiState(
                    phase = InAppUpdatePhase.Downloading(0f, 0f, 0f),
                    bannerDismissed = false
                )
            }
            InstallStatus.DOWNLOADING -> {
                val downloaded = installState.bytesDownloaded().coerceAtLeast(0L)
                val total = installState.totalBytesToDownload().coerceAtLeast(0L)
                val fraction = if (total > 0L) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                _state.value = InAppUpdateUiState(
                    phase = InAppUpdatePhase.Downloading(
                        progressFraction = fraction,
                        downloadedMb = downloaded / (1024f * 1024f),
                        totalMb = total / (1024f * 1024f)
                    ),
                    bannerDismissed = false
                )
            }
            InstallStatus.DOWNLOADED -> {
                _state.value = InAppUpdateUiState(
                    phase = InAppUpdatePhase.ReadyToInstall,
                    bannerDismissed = false
                )
            }
            InstallStatus.INSTALLED -> {
                cachedAppUpdateInfo = null
                _state.value = InAppUpdateUiState(
                    phase = InAppUpdatePhase.UpToDate(BuildConfig.VERSION_NAME),
                    bannerDismissed = true
                )
            }
            InstallStatus.CANCELED -> {
                val info = cachedAppUpdateInfo
                _state.value = if (info != null) {
                    InAppUpdateUiState(
                        phase = InAppUpdatePhase.PlayUpdateAvailable(info.availableVersionCode()),
                        bannerDismissed = false
                    )
                } else {
                    InAppUpdateUiState(phase = InAppUpdatePhase.Idle)
                }
            }
            InstallStatus.FAILED -> {
                _state.value = InAppUpdateUiState(
                    phase = InAppUpdatePhase.Error("In-app update failed. Please try again."),
                    bannerDismissed = false
                )
            }
            else -> Unit
        }
    }

    fun attach(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        updateLauncher = launcher
        if (!isListenerRegistered) {
            runCatching {
                appUpdateManager.registerListener(installStateListener)
                isListenerRegistered = true
            }
        }
    }

    fun detach() {
        updateLauncher = null
        if (isListenerRegistered) {
            runCatching { appUpdateManager.unregisterListener(installStateListener) }
            isListenerRegistered = false
        }
        scope.cancel()
    }

    fun onActivityResult(resultCode: Int) {
        if (resultCode != Activity.RESULT_OK) {
            val info = cachedAppUpdateInfo
            if (info != null) {
                _state.value = InAppUpdateUiState(
                    phase = InAppUpdatePhase.PlayUpdateAvailable(info.availableVersionCode()),
                    bannerDismissed = false
                )
            } else {
                _state.value = InAppUpdateUiState(phase = InAppUpdatePhase.Idle)
            }
        }
    }

    fun dismissBanner() {
        _state.value = _state.value.copy(bannerDismissed = true)
    }

    /**
     * Silently verifies whether an in-app update is already downloaded or available when resuming.
     */
    fun checkOnResume() {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                if (info.installStatus() == InstallStatus.DOWNLOADED) {
                    cachedAppUpdateInfo = info
                    _state.value = InAppUpdateUiState(
                        phase = InAppUpdatePhase.ReadyToInstall,
                        bannerDismissed = false
                    )
                } else if (
                    info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                ) {
                    cachedAppUpdateInfo = info
                    if (_state.value.phase is InAppUpdatePhase.Idle) {
                        _state.value = InAppUpdateUiState(
                            phase = InAppUpdatePhase.PlayUpdateAvailable(info.availableVersionCode()),
                            bannerDismissed = false
                        )
                    }
                } else if (
                    info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS &&
                    info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                ) {
                    val launcher = updateLauncher ?: return@addOnSuccessListener
                    runCatching {
                        appUpdateManager.startUpdateFlowForResult(
                            info,
                            launcher,
                            AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                        )
                    }
                }
            }
            .addOnFailureListener {
                // Expected on non-Play installs or emulators without Play Store ownership; ignore silently.
            }
    }

    /**
     * Checks for an update. If installed from Google Play and an update is available, starts the
     * official Google Play Flexible In-App Update right inside StudyLeaf (without leaving the app).
     * For direct/non-Play builds, checks GitHub Releases metadata cleanly.
     */
    fun checkForUpdate(userInitiated: Boolean = true) {
        if (_state.value.phase is InAppUpdatePhase.Downloading) return
        if (_state.value.phase is InAppUpdatePhase.ReadyToInstall) {
            completeDownloadedUpdate()
            return
        }

        if (userInitiated) {
            _state.value = InAppUpdateUiState(
                phase = InAppUpdatePhase.Checking,
                bannerDismissed = false
            )
        }

        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                when {
                    info.installStatus() == InstallStatus.DOWNLOADED -> {
                        cachedAppUpdateInfo = info
                        _state.value = InAppUpdateUiState(
                            phase = InAppUpdatePhase.ReadyToInstall,
                            bannerDismissed = false
                        )
                    }
                    info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                        info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> {
                        cachedAppUpdateInfo = info
                        _state.value = InAppUpdateUiState(
                            phase = InAppUpdatePhase.PlayUpdateAvailable(info.availableVersionCode()),
                            bannerDismissed = false
                        )
                        if (userInitiated) {
                            startPlayFlexibleUpdate()
                        }
                    }
                    info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                        info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> {
                        cachedAppUpdateInfo = info
                        val launcher = updateLauncher
                        if (userInitiated && launcher != null) {
                            runCatching {
                                appUpdateManager.startUpdateFlowForResult(
                                    info,
                                    launcher,
                                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                                )
                            }
                        } else {
                            _state.value = InAppUpdateUiState(
                                phase = InAppUpdatePhase.PlayUpdateAvailable(info.availableVersionCode()),
                                bannerDismissed = false
                            )
                        }
                    }
                    else -> {
                        // Play Store reports no newer build; also check GitHub metadata on user-initiated check
                        if (userInitiated) {
                            checkGitHubReleaseMetadataFallback()
                        }
                    }
                }
            }
            .addOnFailureListener { err ->
                Log.d(TAG, "Play In-App Update unavailable (likely direct/sideloaded build): ${err.message}")
                if (userInitiated) {
                    checkGitHubReleaseMetadataFallback()
                }
            }
    }

    fun startPlayFlexibleUpdate() {
        val info = cachedAppUpdateInfo
        val launcher = updateLauncher
        if (info == null || launcher == null) {
            checkForUpdate(userInitiated = true)
            return
        }
        val started = runCatching {
            appUpdateManager.startUpdateFlowForResult(
                info,
                launcher,
                AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
            )
        }.getOrDefault(false)

        if (!started) {
            _state.value = InAppUpdateUiState(
                phase = InAppUpdatePhase.Error("Unable to start in-app download right now."),
                bannerDismissed = false
            )
        }
    }

    fun completeDownloadedUpdate() {
        runCatching {
            appUpdateManager.completeUpdate()
        }.onFailure { err ->
            Log.e(TAG, "completeUpdate failed", err)
            _state.value = InAppUpdateUiState(
                phase = InAppUpdatePhase.Error("Could not restart to finish update. Please reopen StudyLeaf."),
                bannerDismissed = false
            )
        }
    }

    fun openExternalReleasePage(context: Context, url: String) {
        val target = url.ifBlank { GITHUB_RELEASES_FALLBACK_URL }
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private fun checkGitHubReleaseMetadataFallback() {
        scope.launch {
            val remote = withContext(Dispatchers.IO) { fetchLatestGitHubRelease() }
            if (remote != null && isNewerSemanticVersion(remote.version, BuildConfig.VERSION_NAME)) {
                _state.value = InAppUpdateUiState(
                    phase = InAppUpdatePhase.ExternalReleaseAvailable(
                        latestVersion = remote.version,
                        releaseNotes = remote.notes,
                        releaseUrl = remote.htmlUrl
                    ),
                    bannerDismissed = false
                )
            } else {
                _state.value = InAppUpdateUiState(
                    phase = InAppUpdatePhase.UpToDate(BuildConfig.VERSION_NAME),
                    bannerDismissed = false
                )
            }
        }
    }

    private data class RemoteReleaseInfo(
        val version: String,
        val notes: String,
        val htmlUrl: String
    )

    private fun fetchLatestGitHubRelease(): RemoteReleaseInfo? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(GITHUB_LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "StudyLeaf-Android/${BuildConfig.VERSION_NAME}")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val rawTag = json.optString("tag_name", "").trim()
            val cleanedVersion = rawTag.removePrefix("v").removePrefix("V").trim()
            if (cleanedVersion.isBlank()) return null
            val htmlUrl = json.optString("html_url", GITHUB_RELEASES_FALLBACK_URL)
                .ifBlank { GITHUB_RELEASES_FALLBACK_URL }
            val notes = json.optString("body", "")
                .replace(Regex("\\r\\n"), "\n")
                .trim()
                .take(260)
            RemoteReleaseInfo(
                version = cleanedVersion,
                notes = notes,
                htmlUrl = htmlUrl
            )
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    internal fun isNewerSemanticVersion(remoteVersion: String, currentVersion: String): Boolean {
        val remoteParts = remoteVersion.removePrefix("v").removePrefix("V")
            .split(".", "-", "_")
            .mapNotNull { it.toIntOrNull() }
        val currentParts = currentVersion.removePrefix("v").removePrefix("V")
            .split(".", "-", "_")
            .mapNotNull { it.toIntOrNull() }
        if (remoteParts.isEmpty() || currentParts.isEmpty()) return false
        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return false
    }
}
