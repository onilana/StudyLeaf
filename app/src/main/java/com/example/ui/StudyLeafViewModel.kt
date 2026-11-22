package com.example.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.StudyLeafAiEngine
import com.example.data.engine.DocumentEngine
import com.example.data.local.ConversionHistoryEntity
import com.example.data.local.DocumentEntity
import com.example.data.local.PreferencesRepository
import com.example.data.local.StudyLeafDatabase
import com.example.data.model.AnnotationTool
import com.example.data.model.ConversionToolType
import com.example.data.model.DocumentFormat
import com.example.data.model.FileViewMode
import com.example.data.model.NormalizedPoint
import com.example.data.model.PageAnnotation
import com.example.data.model.ScanEnhancementSettings
import com.example.data.model.ScanResolutionOption
import com.example.data.model.ScannerFilterMode
import com.example.data.model.SortOrder
import com.example.data.model.ThemePreference
import com.example.data.repository.StudyLeafRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class StudyLeafTab(val label: String) {
    HOME("Home"),
    FILES("Files"),
    HISTORY("History")
}

enum class LibraryFilter(val label: String) {
    ALL("All"),
    PDF("PDF"),
    WORD("Word"),
    EXCEL("Excel"),
    IMAGE("Image"),
    SCAN("Scanned"),
    FAVORITES("Favorites")
}

enum class AiAssistantMode(val label: String) {
    SUMMARIZE("Summary"),
    FLASHCARDS("Flashcards"),
    ASK("Ask Gemini")
}

data class AiAssistantUiState(
    val activeMode: AiAssistantMode = AiAssistantMode.SUMMARIZE,
    val extractedPreview: String = "",
    val lastCopiedPrompt: String = "",
    val savedReplyDraft: String = "",
    val isPreparingShare: Boolean = false,
    val isGeminiAppInstalled: Boolean = false
)

data class DocumentEditorUiState(
    val isLoading: Boolean = false,
    val isAiExtracting: Boolean = false,
    val extractedText: String = "",
    val extractionVersion: Int = 0,
    val statusBanner: String? = null
)

sealed interface StudyLeafOverlay {
    data object None : StudyLeafOverlay
    data class Reader(
        val documentId: Long,
        val initialDocument: DocumentEntity? = null
    ) : StudyLeafOverlay
    data class DocumentEditor(
        val documentId: Long,
        val initialDocument: DocumentEntity? = null
    ) : StudyLeafOverlay
    data object Scanner : StudyLeafOverlay
    data class ConversionStudio(
        val tool: ConversionToolType,
        val preselectedDocId: Long? = null
    ) : StudyLeafOverlay
    data class PageReorderBoard(val documentId: Long) : StudyLeafOverlay
    data class AiAssistant(
        val documentId: Long?,
        val pageIndex: Int = 0
    ) : StudyLeafOverlay
}

data class StudyLeafUiState(
    val activeTab: StudyLeafTab = StudyLeafTab.HOME,
    val overlay: StudyLeafOverlay = StudyLeafOverlay.None,
    val searchQuery: String = "",
    val activeFilter: LibraryFilter = LibraryFilter.ALL,
    val activeFolder: String = "All",
    val selectedPreviewDocId: Long? = null,
    val isProcessing: Boolean = false,
    val processingLabel: String = "",
    val snackbarMessage: String? = null
)

class StudyLeafViewModel(application: Application) : AndroidViewModel(application) {

    private val database = StudyLeafDatabase.getInstance(application)
    val engine = DocumentEngine(application)
    private val aiEngine = StudyLeafAiEngine()
    private val repository = StudyLeafRepository(application, database, engine)
    private val preferences = PreferencesRepository(application)

    private val _uiState = MutableStateFlow(StudyLeafUiState())
    val uiState: StateFlow<StudyLeafUiState> = _uiState.asStateFlow()

    private val _aiState = MutableStateFlow(AiAssistantUiState())
    val aiState: StateFlow<AiAssistantUiState> = _aiState.asStateFlow()

    private val _editorState = MutableStateFlow(DocumentEditorUiState())
    val editorState: StateFlow<DocumentEditorUiState> = _editorState.asStateFlow()

    private var activeAiJob: Job? = null
    private var activeEditorJob: Job? = null

    val documents: StateFlow<List<DocumentEntity>> = repository.documents.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val history: StateFlow<List<ConversionHistoryEntity>> = repository.history.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val themePreference: StateFlow<ThemePreference> = preferences.themePreference.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ThemePreference.LIGHT
    )

    val fileViewMode: StateFlow<FileViewMode> = preferences.fileViewMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FileViewMode.LIST
    )

    val sortOrder: StateFlow<SortOrder> = preferences.sortOrder.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SortOrder.RECENT
    )

    init {
        viewModelScope.launch {
            repository.ensureSeeded()
        }
    }

    fun selectTab(tab: StudyLeafTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun updateSearch(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectFilter(filter: LibraryFilter) {
        _uiState.update { it.copy(activeFilter = filter) }
    }

    fun selectFolder(folder: String) {
        _uiState.update { it.copy(activeFolder = folder) }
    }

    fun selectPreviewDocument(docId: Long) {
        _uiState.update { it.copy(selectedPreviewDocId = docId) }
    }

    fun openReader(docId: Long) {
        viewModelScope.launch { repository.markOpened(docId) }
        _uiState.update {
            it.copy(
                selectedPreviewDocId = docId,
                overlay = StudyLeafOverlay.Reader(docId)
            )
        }
    }

    fun openScanner() {
        _uiState.update { it.copy(overlay = StudyLeafOverlay.Scanner) }
    }

    fun openTool(tool: ConversionToolType, preselectedDocId: Long? = null) {
        if (tool == ConversionToolType.SCAN_DOCUMENT) {
            openScanner()
        } else if (tool == ConversionToolType.SPLIT_REORDER && preselectedDocId != null) {
            _uiState.update { it.copy(overlay = StudyLeafOverlay.PageReorderBoard(preselectedDocId)) }
        } else {
            _uiState.update {
                it.copy(overlay = StudyLeafOverlay.ConversionStudio(tool, preselectedDocId))
            }
        }
    }

    fun openPageReorderBoard(docId: Long) {
        _uiState.update { it.copy(overlay = StudyLeafOverlay.PageReorderBoard(docId)) }
    }

    fun openDocumentEditor(docId: Long) {
        val doc = documents.value.firstOrNull { it.id == docId } ?: return
        activeEditorJob?.cancel()
        _editorState.value = DocumentEditorUiState(isLoading = true)
        _uiState.update {
            it.copy(
                selectedPreviewDocId = docId,
                overlay = StudyLeafOverlay.DocumentEditor(docId, doc)
            )
        }
        activeEditorJob = viewModelScope.launch {
            val localText = engine.extractEditableText(doc.filePath)
            val isPdfOrScan = doc.format == DocumentFormat.PDF || doc.format == DocumentFormat.SCAN
            if (localText.length >= 24 || !isPdfOrScan) {
                val resolved = localText.ifBlank { doc.summarySnippet }
                _editorState.update { state ->
                    state.copy(
                        isLoading = false,
                        isAiExtracting = false,
                        extractedText = resolved,
                        extractionVersion = state.extractionVersion + 1,
                        statusBanner = if (isPdfOrScan) {
                            "Extracted text on-device. Tap AI Extract Text if any scanned/styled page needs OCR."
                        } else null
                    )
                }
            } else {
                _editorState.update { state ->
                    state.copy(
                        isLoading = false,
                        isAiExtracting = false,
                        extractedText = localText.ifBlank { doc.summarySnippet },
                        extractionVersion = state.extractionVersion + 1,
                        statusBanner = "Loaded local text. Tap AI Extract Text to open this page in your Google Gemini account and paste the transcribed text."
                    )
                }
            }
        }
    }

    fun reExtractEditorTextWithAi(doc: DocumentEntity) {
        activeEditorJob?.cancel()
        activeEditorJob = viewModelScope.launch {
            _editorState.update { it.copy(isAiExtracting = true) }
            val prompt = aiEngine.buildOcrExtractPrompt(doc.title, pageNumber = 1)
            val pageUri = renderPageToShareableUri(doc, pageIndex = 0)
            val launched = aiEngine.shareToGeminiApp(
                context = getApplication(),
                fileUri = pageUri,
                mimeType = "image/png",
                prompt = prompt,
                chooserTitle = "Transcribe Page in Google Gemini"
            )
            _editorState.update {
                it.copy(
                    isAiExtracting = false,
                    statusBanner = if (launched) {
                        "Sent page to your Google Gemini account & copied OCR prompt. Copy Gemini's reply and paste it below."
                    } else {
                        "Copied OCR prompt. Open Gemini to transcribe this page and paste the text below."
                    }
                )
            }
        }
    }

    fun saveEditedDocument(
        source: DocumentEntity,
        newTitle: String,
        editedText: String,
        targetFormat: DocumentFormat,
        overwriteOriginal: Boolean,
        fontSizePt: Float,
        textAlignment: Paint.Align = Paint.Align.LEFT
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingLabel = "Saving edited ${targetFormat.label}…") }
            try {
                val saved = repository.saveEditedDocument(
                    source = source,
                    newTitle = newTitle,
                    editedText = editedText,
                    targetFormat = targetFormat,
                    overwriteOriginal = overwriteOriginal,
                    fontSizePt = fontSizePt,
                    textAlignment = textAlignment
                )
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        processingLabel = "",
                        selectedPreviewDocId = saved.id,
                        overlay = StudyLeafOverlay.Reader(saved.id, saved),
                        snackbarMessage = if (overwriteOriginal && targetFormat == source.format) {
                            "Updated \"${saved.title}\""
                        } else {
                            "Saved \"${saved.title}\" (${targetFormat.label})"
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e("StudyLeafVM", "Failed to save edited document", e)
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        processingLabel = "",
                        snackbarMessage = e.message ?: "Could not save edited document"
                    )
                }
            }
        }
    }

    fun openAiAssistant(docId: Long? = null, pageIndex: Int = 0) {
        activeAiJob?.cancel()
        val resolvedDocId = docId ?: _uiState.value.selectedPreviewDocId ?: documents.value.firstOrNull()?.id
        val geminiInstalled = aiEngine.isGeminiAppInstalled(getApplication())
        _aiState.value = AiAssistantUiState(isGeminiAppInstalled = geminiInstalled)
        _uiState.update {
            it.copy(overlay = StudyLeafOverlay.AiAssistant(resolvedDocId, pageIndex))
        }
        preloadDocumentTextForGemini(resolvedDocId)
    }

    fun selectAiDocument(docId: Long) {
        activeAiJob?.cancel()
        _aiState.update {
            it.copy(
                extractedPreview = "",
                lastCopiedPrompt = ""
            )
        }
        _uiState.update { state ->
            if (state.overlay is StudyLeafOverlay.AiAssistant) {
                state.copy(overlay = StudyLeafOverlay.AiAssistant(docId, 0))
            } else state
        }
        preloadDocumentTextForGemini(docId)
    }

    fun selectAiMode(mode: AiAssistantMode) {
        _aiState.update { it.copy(activeMode = mode) }
    }

    fun updateAiSavedReplyDraft(text: String) {
        _aiState.update { it.copy(savedReplyDraft = text) }
    }

    fun pasteClipboardIntoAiReply() {
        val pasted = aiEngine.readClipboardText(getApplication())
        if (pasted.isNotBlank()) {
            _aiState.update { it.copy(savedReplyDraft = pasted) }
            showMessage("Pasted Gemini reply from clipboard")
        } else {
            showMessage("Clipboard is empty — copy a response from Gemini first")
        }
    }

    private fun preloadDocumentTextForGemini(docId: Long?) {
        val doc = documents.value.firstOrNull { it.id == docId } ?: return
        activeAiJob = viewModelScope.launch {
            val extracted = engine.extractEditableText(doc.filePath)
            _aiState.update { it.copy(extractedPreview = extracted) }
        }
    }

    fun openGeminiAccountInApp(
        toolbarColorArgb: Int,
        mode: AiAssistantMode? = null,
        customQuestion: String = ""
    ) {
        val overlay = _uiState.value.overlay as? StudyLeafOverlay.AiAssistant
        val doc = overlay?.documentId?.let { id -> documents.value.firstOrNull { it.id == id } }
        val pageNum = (overlay?.pageIndex ?: 0) + 1
        val prompt = if (doc != null && mode != null) {
            buildPromptForMode(
                doc = doc,
                pageNumber = pageNum,
                mode = mode,
                customQuestion = customQuestion,
                extractedText = _aiState.value.extractedPreview
            )
        } else ""

        val launched = aiEngine.openGeminiInApp(
            context = getApplication(),
            prompt = prompt,
            toolbarColorArgb = toolbarColorArgb
        )
        if (prompt.isNotBlank()) {
            _aiState.update { it.copy(lastCopiedPrompt = prompt) }
        }
        if (launched) {
            showMessage(
                if (prompt.isNotBlank()) {
                    "Prompt copied! Paste in Gemini to run on your own Google account"
                } else {
                    "Opened your Google Gemini account"
                }
            )
        } else {
            showMessage("Could not open browser for Google Gemini")
        }
    }

    fun sharePageToGeminiApp(
        mode: AiAssistantMode,
        customQuestion: String = ""
    ) {
        val overlay = _uiState.value.overlay as? StudyLeafOverlay.AiAssistant ?: return
        val doc = documents.value.firstOrNull { it.id == overlay.documentId } ?: return
        viewModelScope.launch {
            _aiState.update { it.copy(isPreparingShare = true) }
            val prompt = buildPromptForMode(
                doc = doc,
                pageNumber = overlay.pageIndex + 1,
                mode = mode,
                customQuestion = customQuestion,
                extractedText = _aiState.value.extractedPreview
            )
            val pageUri = renderPageToShareableUri(doc, overlay.pageIndex)
            val launched = aiEngine.shareToGeminiApp(
                context = getApplication(),
                fileUri = pageUri,
                mimeType = "image/png",
                prompt = prompt,
                chooserTitle = "Ask Google Gemini"
            )
            _aiState.update {
                it.copy(
                    isPreparingShare = false,
                    lastCopiedPrompt = prompt
                )
            }
            if (launched) {
                showMessage("Shared page & copied prompt to your Google Gemini app")
            } else {
                showMessage("No compatible app found to share this page")
            }
        }
    }

    private fun buildPromptForMode(
        doc: DocumentEntity,
        pageNumber: Int,
        mode: AiAssistantMode,
        customQuestion: String,
        extractedText: String
    ): String = when (mode) {
        AiAssistantMode.SUMMARIZE -> aiEngine.buildSummaryPrompt(doc.title, pageNumber, extractedText)
        AiAssistantMode.FLASHCARDS -> aiEngine.buildFlashcardsPrompt(doc.title, pageNumber, extractedText)
        AiAssistantMode.ASK -> {
            val q = customQuestion.ifBlank { "Explain the key ideas on this page clearly." }
            aiEngine.buildQuestionPrompt(doc.title, pageNumber, q, extractedText)
        }
    }

    private suspend fun renderPageToShareableUri(doc: DocumentEntity, pageIndex: Int): Uri? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = engine.renderPdfPage(doc.filePath, pageIndex, targetWidthPx = 1440) ?: return@runCatching null
                val app = getApplication<Application>()
                val shareDir = File(app.cacheDir, "shared_pages").apply { mkdirs() }
                val outFile = File(shareDir, "studyleaf_${doc.id}_p${pageIndex + 1}.png")
                FileOutputStream(outFile).use { fos ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 95, fos)
                }
                FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", outFile)
            }.onFailure { err ->
                Log.e("StudyLeafGemini", "Failed to render page for Gemini share", err)
            }.getOrNull()
        }

    fun pinAiSummaryAsStickyNote(noteText: String) {
        val overlay = _uiState.value.overlay as? StudyLeafOverlay.AiAssistant ?: return
        val docId = overlay.documentId ?: return
        val cleaned = noteText.trim()
        if (cleaned.isEmpty()) return
        viewModelScope.launch {
            repository.addAnnotation(
                PageAnnotation(
                    documentId = docId,
                    pageIndex = overlay.pageIndex,
                    tool = AnnotationTool.NOTE,
                    colorHex = 0xFF74C69D,
                    points = listOf(NormalizedPoint(0.82f, 0.18f)),
                    noteText = cleaned.take(320)
                )
            )
            showMessage("Pinned Gemini notes as sticky note on page ${overlay.pageIndex + 1}")
        }
    }

    fun closeOverlay() {
        activeAiJob?.cancel()
        activeEditorJob?.cancel()
        _uiState.update { it.copy(overlay = StudyLeafOverlay.None) }
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun showMessage(msg: String) {
        _uiState.update { it.copy(snackbarMessage = msg) }
    }

    fun toggleTheme(currentlyDark: Boolean) {
        viewModelScope.launch {
            preferences.setThemePreference(
                if (currentlyDark) ThemePreference.LIGHT else ThemePreference.DARK
            )
        }
    }

    fun toggleFileViewMode() {
        viewModelScope.launch {
            val next = if (fileViewMode.value == FileViewMode.LIST) FileViewMode.GRID else FileViewMode.LIST
            preferences.setFileViewMode(next)
        }
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch {
            preferences.setSortOrder(order)
        }
    }

    fun toggleFavorite(docId: Long) {
        viewModelScope.launch { repository.toggleFavorite(docId) }
    }

    fun renameDocument(docId: Long, newTitle: String) {
        viewModelScope.launch {
            repository.renameDocument(docId, newTitle)
            showMessage("Renamed to \"$newTitle\"")
        }
    }

    fun deleteDocument(doc: DocumentEntity) {
        viewModelScope.launch {
            repository.deleteDocument(doc)
            _uiState.update { state ->
                val clearedPreview = if (state.selectedPreviewDocId == doc.id) null else state.selectedPreviewDocId
                val clearedOverlay = if (state.overlay is StudyLeafOverlay.Reader && state.overlay.documentId == doc.id) {
                    StudyLeafOverlay.None
                } else state.overlay
                state.copy(
                    selectedPreviewDocId = clearedPreview,
                    overlay = clearedOverlay,
                    snackbarMessage = "Deleted \"${doc.title}\""
                )
            }
        }
    }

    fun importUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingLabel = "Importing into local vault…") }
            val imported = repository.importDeviceDocument(uri)
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    processingLabel = "",
                    selectedPreviewDocId = imported?.id ?: it.selectedPreviewDocId,
                    snackbarMessage = if (imported != null) {
                        "Imported \"${imported.title}\" to Offline Vault"
                    } else {
                        "Could not import selected file"
                    }
                )
            }
        }
    }

    fun openExternalUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingLabel = "Opening document in StudyLeaf…") }
            val imported = repository.importDeviceDocument(uri)
            if (imported != null) {
                repository.markOpened(imported.id)
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        processingLabel = "",
                        selectedPreviewDocId = imported.id,
                        overlay = StudyLeafOverlay.Reader(imported.id, imported),
                        snackbarMessage = "Opened \"${imported.title}\""
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        processingLabel = "",
                        snackbarMessage = "Could not open the selected file"
                    )
                }
            }
        }
    }

    fun observeAnnotations(documentId: Long): Flow<List<PageAnnotation>> =
        repository.observeAnnotations(documentId)

    fun observeAnnotationCount(documentId: Long): Flow<Int> =
        repository.observeAnnotationCount(documentId)

    fun addAnnotation(annotation: PageAnnotation) {
        viewModelScope.launch { repository.addAnnotation(annotation) }
    }

    fun deleteAnnotation(annotationId: Long) {
        viewModelScope.launch { repository.deleteAnnotation(annotationId) }
    }

    fun clearPageAnnotations(documentId: Long, pageIndex: Int) {
        viewModelScope.launch {
            repository.clearPageAnnotations(documentId, pageIndex)
            showMessage("Cleared annotations on page ${pageIndex + 1}")
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            showMessage("Conversion history cleared")
        }
    }

    fun saveScannedPdf(
        bitmaps: List<Bitmap>,
        title: String,
        resolution: ScanResolutionOption = ScanResolutionOption.MEDIUM
    ) {
        if (bitmaps.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingLabel = "Compiling ${resolution.label} scan PDF…") }
            val doc = repository.convertImagesToPdf(
                bitmaps = bitmaps,
                outputTitle = title.ifBlank { "Scanned Study Notes" },
                marginPt = 18,
                isScan = true,
                scanResolution = resolution
            )
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    processingLabel = "",
                    selectedPreviewDocId = doc.id,
                    overlay = StudyLeafOverlay.Reader(doc.id, doc),
                    snackbarMessage = "Saved \"${doc.title}\" (${resolution.label} • ${doc.pageCount} pg)"
                )
            }
        }
    }

    fun runImageToPdf(bitmaps: List<Bitmap>, title: String, marginPt: Int) {
        if (bitmaps.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingLabel = "Converting images to PDF…") }
            val doc = repository.convertImagesToPdf(
                bitmaps = bitmaps,
                outputTitle = title.ifBlank { "Image Collection" },
                marginPt = marginPt,
                isScan = false
            )
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    processingLabel = "",
                    selectedPreviewDocId = doc.id,
                    overlay = StudyLeafOverlay.Reader(doc.id),
                    snackbarMessage = "Created \"${doc.title}.pdf\" on-device"
                )
            }
        }
    }

    fun runMergePdfs(sources: List<DocumentEntity>, outputTitle: String) {
        if (sources.size < 2) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingLabel = "Merging ${sources.size} documents…") }
            val doc = repository.mergeDocuments(
                sources = sources,
                outputTitle = outputTitle.ifBlank { "Merged Study Pack" }
            )
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    processingLabel = "",
                    selectedPreviewDocId = doc.id,
                    overlay = StudyLeafOverlay.Reader(doc.id),
                    snackbarMessage = "Merged ${doc.pageCount} pages into \"${doc.title}\""
                )
            }
        }
    }

    fun runTextToPdf(title: String, bodyText: String, fontSizePt: Float, marginPt: Int, sourceName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingLabel = "Typesetting document to PDF…") }
            val doc = repository.convertTextToPdf(
                title = title.ifBlank { "Typeset Notes" },
                bodyText = bodyText,
                fontSizePt = fontSizePt,
                marginPt = marginPt,
                sourceName = sourceName
            )
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    processingLabel = "",
                    selectedPreviewDocId = doc.id,
                    overlay = StudyLeafOverlay.Reader(doc.id),
                    snackbarMessage = "Typeset \"${doc.title}\" (${doc.pageCount} pages)"
                )
            }
        }
    }

    fun runReorderPages(source: DocumentEntity, orderedPages: List<Int>, outputTitle: String) {
        if (orderedPages.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingLabel = "Rebuilding PDF pages…") }
            val doc = repository.reorderDocumentPages(
                source = source,
                orderedZeroBasedPages = orderedPages,
                outputTitle = outputTitle.ifBlank { "${source.title} (Reordered)" }
            )
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    processingLabel = "",
                    selectedPreviewDocId = doc.id,
                    overlay = StudyLeafOverlay.Reader(doc.id),
                    snackbarMessage = "Saved \"${doc.title}\" with ${doc.pageCount} pages"
                )
            }
        }
    }

    fun runOptimizePdf(source: DocumentEntity, qualityScale: Float, outputTitle: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingLabel = "Optimizing PDF size…") }
            try {
                val doc = repository.optimizeDocument(
                    source = source,
                    qualityScale = qualityScale,
                    outputTitle = outputTitle.ifBlank { "${source.title} (Compact)" }
                )
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        processingLabel = "",
                        selectedPreviewDocId = doc.id,
                        overlay = StudyLeafOverlay.Reader(doc.id, doc),
                        snackbarMessage = "Optimized \"${doc.title}\" saved locally"
                    )
                }
            } catch (e: Exception) {
                Log.e("StudyLeafVM", "Optimize PDF failed", e)
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        processingLabel = "",
                        snackbarMessage = e.message ?: "Could not optimize this PDF"
                    )
                }
            }
        }
    }

    fun runPdfToImages(source: DocumentEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingLabel = "Exporting pages to PNG…") }
            val exported = repository.exportPdfToImages(source)
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    processingLabel = "",
                    selectedPreviewDocId = exported.firstOrNull()?.id ?: it.selectedPreviewDocId,
                    overlay = StudyLeafOverlay.None,
                    activeTab = StudyLeafTab.FILES,
                    snackbarMessage = "Exported ${exported.size} PNG image(s) to Vault"
                )
            }
        }
    }

    fun runPdfToWord(source: DocumentEntity, outputTitle: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingLabel = "Converting PDF to editable Word (.docx)…") }
            val localText = engine.extractEditableText(source.filePath)
            val resolvedText = localText.ifBlank { source.summarySnippet }
            val finalTitle = outputTitle.trim().ifBlank { "${source.title} (Word)" }
            val doc = repository.saveEditedDocument(
                source = source,
                newTitle = finalTitle,
                editedText = resolvedText.ifBlank { source.title },
                targetFormat = DocumentFormat.WORD,
                overwriteOriginal = false
            )
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    processingLabel = "",
                    selectedPreviewDocId = doc.id,
                    overlay = StudyLeafOverlay.Reader(doc.id, doc),
                    snackbarMessage = "Converted \"${doc.title}.docx\" on-device"
                )
            }
        }
    }

    fun runUploadedPdfToWord(uri: Uri, customOutputTitle: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingLabel = "Importing PDF & converting to Word…") }
            val imported = repository.importDeviceDocument(uri)
            if (imported != null) {
                runPdfToWord(
                    source = imported,
                    outputTitle = customOutputTitle.ifBlank { "${imported.title} (Word)" }
                )
            } else {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        processingLabel = "",
                        snackbarMessage = "Could not import the selected PDF file"
                    )
                }
            }
        }
    }

    fun runWordDocToPdf(source: DocumentEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingLabel = "Converting Word document to PDF…") }
            val bodyText = engine.extractEditableText(source.filePath)
                .ifBlank { source.summarySnippet.ifBlank { source.title } }
            val doc = repository.convertTextToPdf(
                title = "${source.title} (PDF)",
                bodyText = bodyText,
                fontSizePt = 13f,
                marginPt = 42,
                sourceName = source.title
            )
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    processingLabel = "",
                    selectedPreviewDocId = doc.id,
                    overlay = StudyLeafOverlay.Reader(doc.id, doc),
                    snackbarMessage = "Converted \"${doc.title}\" (${doc.pageCount} pg)"
                )
            }
        }
    }

    fun runImageDocToPdf(source: DocumentEntity) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingLabel = "Converting image to PDF…") }
            val bmp = engine.renderPdfPage(source.filePath, 0, targetWidthPx = 1600)
            if (bmp != null) {
                val doc = repository.convertImagesToPdf(
                    bitmaps = listOf(bmp),
                    outputTitle = "${source.title} (PDF)",
                    marginPt = 24,
                    isScan = false
                )
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        processingLabel = "",
                        selectedPreviewDocId = doc.id,
                        overlay = StudyLeafOverlay.Reader(doc.id, doc),
                        snackbarMessage = "Converted \"${doc.title}.pdf\""
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        processingLabel = "",
                        snackbarMessage = "Could not decode image for PDF conversion"
                    )
                }
            }
        }
    }

    fun runDocOrPdfToExcel(source: DocumentEntity, outputTitle: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingLabel = "Converting to Excel (.xlsx)…") }
            val doc = repository.convertDocOrPdfToExcel(
                source = source,
                outputTitle = outputTitle.ifBlank { "${source.title} Sheet" }
            )
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    processingLabel = "",
                    selectedPreviewDocId = doc.id,
                    overlay = StudyLeafOverlay.Reader(doc.id),
                    snackbarMessage = "Created \"${doc.title}.xlsx\" on-device"
                )
            }
        }
    }

    fun runExcelToPdf(source: DocumentEntity, outputTitle: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingLabel = "Rendering Excel to PDF…") }
            val doc = repository.convertExcelToPdf(
                source = source,
                outputTitle = outputTitle.ifBlank { "${source.title} PDF" }
            )
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    processingLabel = "",
                    selectedPreviewDocId = doc.id,
                    overlay = StudyLeafOverlay.Reader(doc.id),
                    snackbarMessage = "Converted \"${doc.title}\" to PDF (${doc.pageCount} pg)"
                )
            }
        }
    }

    fun runExcelToWord(source: DocumentEntity, outputTitle: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingLabel = "Exporting Excel to Word (.docx)…") }
            val doc = repository.convertExcelToWord(
                source = source,
                outputTitle = outputTitle.ifBlank { "${source.title} Report" }
            )
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    processingLabel = "",
                    selectedPreviewDocId = doc.id,
                    overlay = StudyLeafOverlay.Reader(doc.id),
                    snackbarMessage = "Created \"${doc.title}.docx\" on-device"
                )
            }
        }
    }

    fun runCompressImages(
        bitmaps: List<Bitmap>,
        qualityPercent: Int,
        maxDimensionPx: Int,
        outputTitle: String
    ) {
        if (bitmaps.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingLabel = "Compressing ${bitmaps.size} image(s)…") }
            val saved = repository.compressImages(
                bitmaps = bitmaps,
                qualityPercent = qualityPercent,
                maxDimensionPx = maxDimensionPx,
                outputTitle = outputTitle.ifBlank { "Compressed Image" }
            )
            val totalKb = (saved.sumOf { it.sizeBytes } / 1024L).coerceAtLeast(1L)
            val first = saved.firstOrNull()
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    processingLabel = "",
                    selectedPreviewDocId = first?.id ?: it.selectedPreviewDocId,
                    overlay = if (first != null) StudyLeafOverlay.Reader(first.id) else StudyLeafOverlay.None,
                    snackbarMessage = "Saved ${saved.size} compressed image(s) (${totalKb} KB)"
                )
            }
        }
    }

    suspend fun warpScan(
        source: Bitmap,
        corners: List<NormalizedPoint>,
        filterMode: ScannerFilterMode,
        enhancement: ScanEnhancementSettings = ScanEnhancementSettings()
    ): Bitmap = repository.warpScanPreview(source, corners, filterMode, enhancement)

    fun buildShareIntent(doc: DocumentEntity): Intent? {
        val file = File(doc.filePath)
        if (!file.exists()) return null
        val app = getApplication<Application>()
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
        val lower = file.name.lowercase()
        val mime = when (doc.format) {
            DocumentFormat.PDF, DocumentFormat.SCAN -> "application/pdf"
            DocumentFormat.IMAGE -> if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) "image/jpeg" else "image/png"
            DocumentFormat.WORD -> if (lower.endsWith(".docx")) {
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            } else {
                "text/plain"
            }
            DocumentFormat.EXCEL -> if (lower.endsWith(".csv")) {
                "text/csv"
            } else {
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            }
        }
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, doc.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
