package com.example.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiChatMessage
import com.example.ai.StructuredStudyDeck
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File

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

sealed interface AiSummaryState {
    data object Idle : AiSummaryState
    data object Loading : AiSummaryState
    data class Streaming(val text: String) : AiSummaryState
    data class Success(val text: String) : AiSummaryState
    data class Error(val message: String) : AiSummaryState
}

sealed interface AiDeckState {
    data object Idle : AiDeckState
    data object Loading : AiDeckState
    data class Success(val deck: StructuredStudyDeck) : AiDeckState
    data class Error(val message: String) : AiDeckState
}

data class AiAssistantUiState(
    val activeMode: AiAssistantMode = AiAssistantMode.SUMMARIZE,
    val summaryState: AiSummaryState = AiSummaryState.Idle,
    val deckState: AiDeckState = AiDeckState.Idle,
    val chatMessages: List<AiChatMessage> = emptyList(),
    val isChatLoading: Boolean = false,
    val chatError: String? = null
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
    private var nextChatId: Long = 1L

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
                // Automatically extract words from rendered PDF pages via Gemini Vision OCR
                extractPdfWordsWithAi(doc, fallbackText = localText.ifBlank { doc.summarySnippet })
            }
        }
    }

    fun reExtractEditorTextWithAi(doc: DocumentEntity) {
        activeEditorJob?.cancel()
        activeEditorJob = viewModelScope.launch {
            extractPdfWordsWithAi(doc, fallbackText = _editorState.value.extractedText.ifBlank { doc.summarySnippet })
        }
    }

    private suspend fun extractPdfWordsWithAi(doc: DocumentEntity, fallbackText: String) {
        _editorState.update { it.copy(isLoading = true, isAiExtracting = true, statusBanner = null) }
        try {
            val pageCount = engine.getPdfPageCount(doc.filePath).coerceIn(1, 6)
            val bitmaps = buildList {
                for (i in 0 until pageCount) {
                    engine.renderPdfPage(doc.filePath, i, targetWidthPx = 1200)?.let { add(it) }
                }
            }
            val aiText = if (bitmaps.isNotEmpty()) {
                withTimeout(60_000L) {
                    aiEngine.extractEditableTextFromPages(bitmaps, doc.title)
                }
            } else ""
            if (aiText.isNotBlank()) {
                engine.saveEditableTextCache(File(doc.filePath), aiText)
                _editorState.update { state ->
                    state.copy(
                        isLoading = false,
                        isAiExtracting = false,
                        extractedText = aiText,
                        extractionVersion = state.extractionVersion + 1,
                        statusBanner = "Extracted editable text from $pageCount PDF page(s). Edit any word below and tap Save."
                    )
                }
            } else {
                _editorState.update { state ->
                    state.copy(
                        isLoading = false,
                        isAiExtracting = false,
                        extractedText = fallbackText,
                        extractionVersion = state.extractionVersion + 1,
                        statusBanner = "Loaded document notes. Edit directly or tap AI Extract Text to retry."
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("StudyLeafEditor", "AI OCR extraction failed", e)
            _editorState.update { state ->
                state.copy(
                    isLoading = false,
                    isAiExtracting = false,
                    extractedText = fallbackText,
                    extractionVersion = state.extractionVersion + 1,
                    statusBanner = "Offline/fallback text loaded (${e.localizedMessage ?: "AI unavailable"}). You can edit and save right away."
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
        fontSizePt: Float
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingLabel = "Saving edited ${targetFormat.label}…") }
            val saved = repository.saveEditedDocument(
                source = source,
                newTitle = newTitle,
                editedText = editedText,
                targetFormat = targetFormat,
                overwriteOriginal = overwriteOriginal,
                fontSizePt = fontSizePt
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
        }
    }

    fun openAiAssistant(docId: Long? = null, pageIndex: Int = 0) {
        activeAiJob?.cancel()
        val resolvedDocId = docId ?: _uiState.value.selectedPreviewDocId ?: documents.value.firstOrNull()?.id
        _aiState.value = AiAssistantUiState()
        _uiState.update {
            it.copy(overlay = StudyLeafOverlay.AiAssistant(resolvedDocId, pageIndex))
        }
    }

    fun selectAiDocument(docId: Long) {
        activeAiJob?.cancel()
        _aiState.value = AiAssistantUiState(activeMode = _aiState.value.activeMode)
        _uiState.update { state ->
            if (state.overlay is StudyLeafOverlay.AiAssistant) {
                state.copy(overlay = StudyLeafOverlay.AiAssistant(docId, 0))
            } else state
        }
    }

    fun selectAiMode(mode: AiAssistantMode) {
        _aiState.update { it.copy(activeMode = mode) }
    }

    fun runAiPageSummary() {
        val overlay = _uiState.value.overlay as? StudyLeafOverlay.AiAssistant ?: return
        val doc = documents.value.firstOrNull { it.id == overlay.documentId } ?: return
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _aiState.update { it.copy(summaryState = AiSummaryState.Loading) }
            val accumulated = StringBuilder()
            try {
                val pageBitmap = engine.renderPdfPage(doc.filePath, overlay.pageIndex, targetWidthPx = 1024)
                withTimeout(60_000L) {
                    aiEngine.summarizePageStream(
                        pageBitmap = pageBitmap,
                        documentTitle = doc.title,
                        pageNumber = overlay.pageIndex + 1
                    ).collect { chunk ->
                        accumulated.append(chunk)
                        _aiState.update { it.copy(summaryState = AiSummaryState.Streaming(accumulated.toString())) }
                    }
                }
                if (accumulated.isNotBlank()) {
                    _aiState.update { it.copy(summaryState = AiSummaryState.Success(accumulated.toString())) }
                } else {
                    _aiState.update { it.copy(summaryState = AiSummaryState.Error("Gemini returned an empty summary.")) }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e("StudyLeafAI", "Page summary timed out", e)
                _aiState.update {
                    it.copy(summaryState = AiSummaryState.Error("Summary request timed out after 60 seconds. Please try again."))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("StudyLeafAI", "Page summary failed", e)
                _aiState.update {
                    it.copy(summaryState = AiSummaryState.Error(e.localizedMessage ?: "Failed to summarize page."))
                }
            }
        }
    }

    fun runAiFlashcards() {
        val overlay = _uiState.value.overlay as? StudyLeafOverlay.AiAssistant ?: return
        val doc = documents.value.firstOrNull { it.id == overlay.documentId } ?: return
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _aiState.update { it.copy(deckState = AiDeckState.Loading) }
            try {
                val pageBitmap = engine.renderPdfPage(doc.filePath, overlay.pageIndex, targetWidthPx = 1024)
                val deck = withTimeout(60_000L) {
                    aiEngine.generateStudyDeck(
                        pageBitmap = pageBitmap,
                        documentTitle = doc.title,
                        pageNumber = overlay.pageIndex + 1
                    )
                }
                _aiState.update { it.copy(deckState = AiDeckState.Success(deck)) }
            } catch (e: TimeoutCancellationException) {
                Log.e("StudyLeafAI", "Flashcard deck generation timed out", e)
                _aiState.update {
                    it.copy(deckState = AiDeckState.Error("Study deck generation timed out after 60 seconds. Please try again."))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("StudyLeafAI", "Flashcard deck generation failed", e)
                _aiState.update {
                    it.copy(deckState = AiDeckState.Error(e.localizedMessage ?: "Failed to generate flashcards."))
                }
            }
        }
    }

    fun sendAiQuestion(question: String) {
        val overlay = _uiState.value.overlay as? StudyLeafOverlay.AiAssistant ?: return
        val doc = documents.value.firstOrNull { it.id == overlay.documentId } ?: return
        activeAiJob?.cancel()

        val prior = _aiState.value.chatMessages
        val userMsg = AiChatMessage(id = nextChatId++, isUser = true, text = question)
        val replyId = nextChatId++
        val placeholderReply = AiChatMessage(id = replyId, isUser = false, text = "", isStreaming = true)

        _aiState.update {
            it.copy(
                chatMessages = it.chatMessages + userMsg + placeholderReply,
                isChatLoading = true,
                chatError = null
            )
        }

        activeAiJob = viewModelScope.launch {
            val accumulated = StringBuilder()
            try {
                val pageBitmap = engine.renderPdfPage(doc.filePath, overlay.pageIndex, targetWidthPx = 1024)
                withTimeout(60_000L) {
                    aiEngine.askDocumentStream(
                        pageBitmap = pageBitmap,
                        documentTitle = doc.title,
                        pageNumber = overlay.pageIndex + 1,
                        priorMessages = prior,
                        question = question
                    ).collect { chunk ->
                        accumulated.append(chunk)
                        _aiState.update { state ->
                            state.copy(
                                chatMessages = state.chatMessages.map { m ->
                                    if (m.id == replyId) m.copy(text = accumulated.toString(), isStreaming = true) else m
                                }
                            )
                        }
                    }
                }
                val finalText = accumulated.toString()
                if (finalText.isBlank()) {
                    _aiState.update { state ->
                        state.copy(
                            chatMessages = state.chatMessages.filterNot { it.id == replyId },
                            isChatLoading = false,
                            chatError = "Gemini returned an empty answer."
                        )
                    }
                } else {
                    _aiState.update { state ->
                        state.copy(
                            chatMessages = state.chatMessages.map { m ->
                                if (m.id == replyId) m.copy(text = finalText, isStreaming = false) else m
                            },
                            isChatLoading = false
                        )
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e("StudyLeafAI", "Chat question timed out", e)
                _aiState.update { state ->
                    state.copy(
                        chatMessages = state.chatMessages.filterNot { it.id == replyId },
                        isChatLoading = false,
                        chatError = "Response timed out after 60 seconds. Please try again."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("StudyLeafAI", "Chat question failed", e)
                _aiState.update { state ->
                    state.copy(
                        chatMessages = state.chatMessages.filterNot { it.id == replyId },
                        isChatLoading = false,
                        chatError = e.localizedMessage ?: "Failed to answer question."
                    )
                }
            }
        }
    }

    fun pinAiSummaryAsStickyNote(noteText: String) {
        val overlay = _uiState.value.overlay as? StudyLeafOverlay.AiAssistant ?: return
        val docId = overlay.documentId ?: return
        viewModelScope.launch {
            repository.addAnnotation(
                PageAnnotation(
                    documentId = docId,
                    pageIndex = overlay.pageIndex,
                    tool = AnnotationTool.NOTE,
                    colorHex = 0xFF74C69D,
                    points = listOf(NormalizedPoint(0.82f, 0.18f)),
                    noteText = noteText
                )
            )
            showMessage("Pinned AI summary as sticky note on page ${overlay.pageIndex + 1}")
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
                    overlay = StudyLeafOverlay.Reader(doc.id),
                    snackbarMessage = "Optimized \"${doc.title}\" saved locally"
                )
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
            val resolvedText = if (localText.length >= 24) {
                localText
            } else {
                try {
                    val pageCount = engine.getPdfPageCount(source.filePath).coerceIn(1, 6)
                    val bitmaps = buildList {
                        for (i in 0 until pageCount) {
                            engine.renderPdfPage(source.filePath, i, targetWidthPx = 1200)?.let { add(it) }
                        }
                    }
                    val aiText = if (bitmaps.isNotEmpty()) {
                        withTimeout(60_000L) {
                            aiEngine.extractEditableTextFromPages(bitmaps, source.title)
                        }
                    } else ""
                    if (aiText.isNotBlank()) {
                        engine.saveEditableTextCache(File(source.filePath), aiText)
                        aiText
                    } else {
                        localText.ifBlank { source.summarySnippet }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("StudyLeafConvert", "AI OCR fallback failed during PDF to Word", e)
                    localText.ifBlank { source.summarySnippet }
                }
            }
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
