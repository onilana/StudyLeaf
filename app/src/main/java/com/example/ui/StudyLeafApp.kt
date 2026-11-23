package com.example.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studyleaf.R
import com.example.data.local.DocumentEntity
import com.example.data.model.ConversionToolType
import com.example.data.model.DocumentFormat
import com.example.ui.components.StudyLeafWindowSize
import com.example.ui.components.calculateWindowSize
import com.example.ui.screens.ConversionStudioScreen
import com.example.ui.screens.DocumentEditorScreen
import com.example.ui.screens.DocumentScannerScreen
import com.example.ui.screens.FilesScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PageReorderBoardScreen
import com.example.ui.screens.PdfReaderScreen
import com.example.ui.screens.StudyAiAssistantScreen
import com.example.update.InAppUpdatePhase
import com.example.update.InAppUpdateUiState
import kotlinx.coroutines.flow.flowOf

@Composable
fun StudyLeafApp(
    viewModel: StudyLeafViewModel,
    isDarkTheme: Boolean,
    updateState: InAppUpdateUiState = InAppUpdateUiState(),
    onCheckForUpdates: () -> Unit = {},
    onStartInAppUpdate: () -> Unit = {},
    onCompleteInAppUpdate: () -> Unit = {},
    onDismissUpdateBanner: () -> Unit = {},
    onOpenExternalRelease: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val fileViewMode by viewModel.fileViewMode.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var renameTarget by remember { mutableStateOf<DocumentEntity?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<DocumentEntity?>(null) }

    // Zero-permission Storage Access Framework importer
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importUri(uri)
    }

    // Zero-permission Android Photo Picker importer
    val pickPhotosLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 8)
    ) { uris ->
        uris.forEach { viewModel.importUri(it) }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSnackbar()
        }
    }

    fun shareDoc(doc: DocumentEntity) {
        val intent = viewModel.buildShareIntent(doc)
        if (intent != null) {
            context.startActivity(Intent.createChooser(intent, "Share ${doc.title}"))
        } else {
            viewModel.showMessage("Document file is unavailable for sharing")
        }
    }

    // Rename Dialog
    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Document") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    label = { Text("New Title") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_doc_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameDocument(target.id, renameInput)
                        renameTarget = null
                    },
                    modifier = Modifier.testTag("confirm_rename_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Delete Confirmation Dialog
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete document?") },
            text = { Text("Remove \"${target.title}\" and its local annotations from this device?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDocument(target)
                        deleteTarget = null
                    },
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val windowSize = calculateWindowSize(maxWidth)
        val previewDoc = remember(documents, uiState.selectedPreviewDocId) {
            documents.firstOrNull { it.id == uiState.selectedPreviewDocId } ?: documents.firstOrNull()
        }
        val previewAnnotationCount by remember(previewDoc?.id) {
            if (previewDoc != null) viewModel.observeAnnotationCount(previewDoc.id)
            else flowOf(0)
        }.collectAsStateWithLifecycle(initialValue = 0)

        Box(modifier = Modifier.fillMaxSize()) {
            when (val overlay = uiState.overlay) {
                is StudyLeafOverlay.Reader -> {
                    val doc = documents.firstOrNull { it.id == overlay.documentId } ?: overlay.initialDocument
                    if (doc != null) {
                        val docAnnotations by remember(doc.id) {
                            viewModel.observeAnnotations(doc.id)
                        }.collectAsStateWithLifecycle(initialValue = emptyList())

                        PdfReaderScreen(
                            document = doc,
                            annotations = docAnnotations,
                            windowSize = windowSize,
                            engine = viewModel.engine,
                            onBack = viewModel::closeOverlay,
                            onAddAnnotation = viewModel::addAnnotation,
                            onDeleteAnnotation = viewModel::deleteAnnotation,
                            onClearPageAnnotations = { pageIdx ->
                                viewModel.clearPageAnnotations(doc.id, pageIdx)
                            },
                            onOpenReorderBoard = { viewModel.openPageReorderBoard(doc.id) },
                            onOpenAiForPage = { pageIdx -> viewModel.openAiAssistant(doc.id, pageIdx) },
                            onConvertToPdf = {
                                when (doc.format) {
                                    DocumentFormat.WORD -> viewModel.runWordDocToPdf(doc)
                                    DocumentFormat.EXCEL -> viewModel.runExcelToPdf(doc, "${doc.title} PDF")
                                    DocumentFormat.IMAGE -> viewModel.runImageDocToPdf(doc)
                                    else -> Unit
                                }
                            },
                            onConvertToWord = {
                                if (doc.format == DocumentFormat.EXCEL) {
                                    viewModel.runExcelToWord(doc, "${doc.title} Report")
                                } else {
                                    viewModel.runPdfToWord(doc, "${doc.title} (Word)")
                                }
                            },
                            onConvertToExcel = { viewModel.runDocOrPdfToExcel(doc, "${doc.title} Sheet") },
                            onConvertToImages = { viewModel.runPdfToImages(doc) },
                            onOptimizePdf = { viewModel.openTool(ConversionToolType.OPTIMIZE_PDF, doc.id) },
                            onMergeWithOthers = { viewModel.openTool(ConversionToolType.MERGE_PDF, doc.id) },
                            onCompressImage = { viewModel.openTool(ConversionToolType.COMPRESS_IMAGE, doc.id) },
                            onEditDocument = { viewModel.openDocumentEditor(doc.id) },
                            onShare = { shareDoc(doc) }
                        )
                    } else {
                        LaunchedEffect(Unit) { viewModel.closeOverlay() }
                    }
                }

                is StudyLeafOverlay.DocumentEditor -> {
                    val doc = documents.firstOrNull { it.id == overlay.documentId } ?: overlay.initialDocument
                    if (doc != null) {
                        DocumentEditorScreen(
                            document = doc,
                            editorState = editorState,
                            windowSize = windowSize,
                            engine = viewModel.engine,
                            onBack = { viewModel.openReader(doc.id) },
                            onRunAiExtract = { viewModel.reExtractEditorTextWithAi(doc) },
                            onSave = { newTitle, editedText, targetFormat, overwriteOriginal, fontSizePt, textAlignment ->
                                viewModel.saveEditedDocument(
                                    source = doc,
                                    newTitle = newTitle,
                                    editedText = editedText,
                                    targetFormat = targetFormat,
                                    overwriteOriginal = overwriteOriginal,
                                    fontSizePt = fontSizePt,
                                    textAlignment = textAlignment
                                )
                            },
                            onSaveVisualPdf = { newTitle, edits, signatures, redactions, sanitizeMetadata, overwriteOriginal ->
                                viewModel.saveVisualEditedPdf(
                                    source = doc,
                                    newTitle = newTitle,
                                    edits = edits,
                                    signatures = signatures,
                                    redactions = redactions,
                                    sanitizeMetadata = sanitizeMetadata,
                                    overwriteOriginal = overwriteOriginal
                                )
                            },
                            onSavePreservedDocx = { newTitle, editedMap, previewBody, overwriteOriginal ->
                                viewModel.savePreservedDocxDocument(
                                    source = doc,
                                    newTitle = newTitle,
                                    editedParagraphsByIndex = editedMap,
                                    previewBodyText = previewBody,
                                    overwriteOriginal = overwriteOriginal
                                )
                            }
                        )
                    } else {
                        LaunchedEffect(Unit) { viewModel.closeOverlay() }
                    }
                }

                StudyLeafOverlay.Scanner -> {
                    DocumentScannerScreen(
                        engine = viewModel.engine,
                        onWarpPreview = viewModel::warpScan,
                        onSaveScannedPdf = viewModel::saveScannedPdf,
                        onShowMessage = viewModel::showMessage,
                        onBack = viewModel::closeOverlay
                    )
                }

                is StudyLeafOverlay.ConversionStudio -> {
                    ConversionStudioScreen(
                        tool = overlay.tool,
                        preselectedDocId = overlay.preselectedDocId,
                        documents = documents,
                        engine = viewModel.engine,
                        onBack = viewModel::closeOverlay,
                        onConvertImagesToPdf = viewModel::runImageToPdf,
                        onMergePdfs = viewModel::runMergePdfs,
                        onConvertTextToPdf = viewModel::runTextToPdf,
                        onConvertPdfToWord = viewModel::runPdfToWord,
                        onConvertUploadedPdfToWord = viewModel::runUploadedPdfToWord,
                        onOpenReorderBoard = viewModel::openPageReorderBoard,
                        onOptimizePdf = viewModel::runOptimizePdf,
                        onExportPdfToImages = viewModel::runPdfToImages,
                        onDocOrPdfToExcel = viewModel::runDocOrPdfToExcel,
                        onExcelToPdf = viewModel::runExcelToPdf,
                        onExcelToWord = viewModel::runExcelToWord,
                        onCompressImages = viewModel::runCompressImages
                    )
                }

                is StudyLeafOverlay.PageReorderBoard -> {
                    val doc = documents.firstOrNull { it.id == overlay.documentId }
                    if (doc != null) {
                        PageReorderBoardScreen(
                            document = doc,
                            windowSize = windowSize,
                            engine = viewModel.engine,
                            onSaveReordered = { specs, title ->
                                viewModel.runReorderPages(
                                    source = doc,
                                    orderedPages = specs.map { it.originalPageIndex },
                                    outputTitle = title,
                                    pageSpecs = specs
                                )
                            },
                            onBack = viewModel::closeOverlay
                        )
                    } else {
                        LaunchedEffect(Unit) { viewModel.closeOverlay() }
                    }
                }

                is StudyLeafOverlay.AiAssistant -> {
                    val activeDoc = documents.firstOrNull { it.id == overlay.documentId } ?: documents.firstOrNull()
                    StudyAiAssistantScreen(
                        documents = documents,
                        activeDoc = activeDoc,
                        pageIndex = overlay.pageIndex,
                        aiState = aiState,
                        windowSize = windowSize,
                        onSelectDocument = viewModel::selectAiDocument,
                        onSelectMode = viewModel::selectAiMode,
                        onOpenGeminiInApp = { toolbarColor, mode, question ->
                            viewModel.openGeminiAccountInApp(
                                toolbarColorArgb = toolbarColor,
                                mode = mode,
                                customQuestion = question
                            )
                        },
                        onSharePageToGeminiApp = { mode, question ->
                            viewModel.sharePageToGeminiApp(
                                mode = mode,
                                customQuestion = question
                            )
                        },
                        onUpdateSavedReplyDraft = viewModel::updateAiSavedReplyDraft,
                        onPasteClipboardReply = viewModel::pasteClipboardIntoAiReply,
                        onPinSummaryAsNote = viewModel::pinAiSummaryAsStickyNote,
                        onExportGuideToPdf = { title, body ->
                            viewModel.runTextToPdf(
                                title = title,
                                bodyText = body,
                                fontSizePt = 12.5f,
                                marginPt = 42,
                                sourceName = "Google Gemini Study Notes"
                            )
                        },
                        onBack = viewModel::closeOverlay
                    )
                }

                StudyLeafOverlay.None -> {
                    val isCompact = windowSize == StudyLeafWindowSize.COMPACT
                    val openDocHandler: (DocumentEntity) -> Unit = { doc ->
                        if (windowSize == StudyLeafWindowSize.EXPANDED) {
                            if (uiState.selectedPreviewDocId == doc.id) {
                                viewModel.openReader(doc.id)
                            } else {
                                viewModel.selectPreviewDocument(doc.id)
                            }
                        } else {
                            viewModel.openReader(doc.id)
                        }
                    }

                    val contentBody: @Composable (Modifier) -> Unit = { contentModifier ->
                        when (uiState.activeTab) {
                            StudyLeafTab.HOME -> HomeScreen(
                                windowSize = windowSize,
                                isDarkTheme = isDarkTheme,
                                documents = documents,
                                totalConversions = history.size,
                                searchQuery = uiState.searchQuery,
                                activeFilter = uiState.activeFilter,
                                selectedPreviewDoc = previewDoc,
                                selectedDocAnnotationCount = previewAnnotationCount,
                                engine = viewModel.engine,
                                onToggleTheme = { viewModel.toggleTheme(isDarkTheme) },
                                onOpenGeminiAccount = { toolbarColor ->
                                    viewModel.openGeminiAccountInApp(toolbarColorArgb = toolbarColor)
                                },
                                updateState = updateState,
                                onCheckForUpdates = onCheckForUpdates,
                                onStartInAppUpdate = onStartInAppUpdate,
                                onCompleteInAppUpdate = onCompleteInAppUpdate,
                                onOpenExternalRelease = onOpenExternalRelease,
                                onSearchChange = viewModel::updateSearch,
                                onFilterChange = viewModel::selectFilter,
                                onQuickToolClick = { viewModel.openTool(it) },
                                onImportDocument = {
                                    openDocumentLauncher.launch(
                                        arrayOf(
                                            "application/pdf",
                                            "text/plain",
                                            "text/csv",
                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                            "image/*"
                                        )
                                    )
                                },
                                onViewAllFiles = { viewModel.selectTab(StudyLeafTab.FILES) },
                                onDocumentClick = openDocHandler,
                                onOpenReader = viewModel::openReader,
                                onEditDocument = viewModel::openDocumentEditor,
                                onToggleFavorite = viewModel::toggleFavorite,
                                onReorderPages = viewModel::openPageReorderBoard,
                                onAskAi = { docId -> viewModel.openAiAssistant(docId, 0) },
                                onRenameRequest = { doc ->
                                    renameTarget = doc
                                    renameInput = doc.title
                                },
                                onShare = ::shareDoc,
                                onDeleteRequest = { deleteTarget = it },
                                modifier = contentModifier
                            )

                            StudyLeafTab.FILES -> FilesScreen(
                                windowSize = windowSize,
                                documents = documents,
                                searchQuery = uiState.searchQuery,
                                activeFolder = uiState.activeFolder,
                                viewMode = fileViewMode,
                                sortOrder = sortOrder,
                                selectedPreviewDoc = previewDoc,
                                selectedDocAnnotationCount = previewAnnotationCount,
                                engine = viewModel.engine,
                                onSearchChange = viewModel::updateSearch,
                                onFolderSelected = viewModel::selectFolder,
                                onToggleViewMode = viewModel::toggleFileViewMode,
                                onSortOrderChange = viewModel::setSortOrder,
                                onImportDocument = {
                                    openDocumentLauncher.launch(
                                        arrayOf(
                                            "application/pdf",
                                            "text/plain",
                                            "text/csv",
                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                            "image/*"
                                        )
                                    )
                                },
                                onImportPhotos = {
                                    pickPhotosLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onDocumentClick = openDocHandler,
                                onOpenReader = viewModel::openReader,
                                onEditDocument = viewModel::openDocumentEditor,
                                onToggleFavorite = viewModel::toggleFavorite,
                                onReorderPages = viewModel::openPageReorderBoard,
                                onAskAi = { docId -> viewModel.openAiAssistant(docId, 0) },
                                onRenameRequest = { doc ->
                                    renameTarget = doc
                                    renameInput = doc.title
                                },
                                onShare = ::shareDoc,
                                onDeleteRequest = { deleteTarget = it },
                                modifier = contentModifier
                            )

                            StudyLeafTab.HISTORY -> HistoryScreen(
                                windowSize = windowSize,
                                history = history,
                                existingDocIds = documents.map { it.id }.toSet(),
                                onOpenDocument = viewModel::openReader,
                                onClearHistory = viewModel::clearHistory,
                                modifier = contentModifier
                            )
                        }
                    }

                    if (isCompact) {
                        Scaffold(
                            containerColor = MaterialTheme.colorScheme.background,
                            contentWindowInsets = WindowInsets.statusBars,
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            floatingActionButton = {
                                ExtendedFloatingActionButton(
                                    onClick = viewModel::openScanner,
                                    shape = CircleShape,
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Filled.DocumentScanner,
                                            contentDescription = stringResource(R.string.action_scan)
                                        )
                                    },
                                    text = { Text(stringResource(R.string.action_scan)) },
                                    modifier = Modifier.testTag("fab_scan_document")
                                )
                            },
                            bottomBar = {
                                Column {
                                    HorizontalDivider(
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                    NavigationBar(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        tonalElevation = 0.dp
                                    ) {
                                        StudyLeafTab.entries.forEach { tab ->
                                            val selected = uiState.activeTab == tab
                                            NavigationBarItem(
                                                selected = selected,
                                                onClick = { viewModel.selectTab(tab) },
                                                icon = {
                                                    Icon(
                                                        imageVector = tabIcon(tab, selected),
                                                        contentDescription = tab.label
                                                    )
                                                },
                                                label = {
                                                    Text(
                                                        text = tab.label,
                                                        style = MaterialTheme.typography.labelMedium
                                                    )
                                                },
                                                colors = NavigationBarItemDefaults.colors(
                                                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                ),
                                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                            )
                                        }
                                    }
                                }
                            }
                        ) { innerPadding ->
                            contentBody(Modifier.padding(innerPadding))
                        }
                    } else {
                        // Medium & Expanded: Persistent NavigationRail on the left
                        Scaffold(
                            containerColor = MaterialTheme.colorScheme.background,
                            contentWindowInsets = WindowInsets(0, 0, 0, 0),
                            snackbarHost = { SnackbarHost(snackbarHostState) }
                        ) { innerPadding ->
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                NavigationRail(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .statusBarsPadding()
                                        .navigationBarsPadding(),
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    header = {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        FloatingActionButton(
                                            onClick = viewModel::openScanner,
                                            shape = RoundedCornerShape(16.dp),
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.testTag("rail_scan_fab")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.DocumentScanner,
                                                contentDescription = stringResource(R.string.action_scan)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                ) {
                                    StudyLeafTab.entries.forEach { tab ->
                                        val selected = uiState.activeTab == tab
                                        NavigationRailItem(
                                            selected = selected,
                                            onClick = { viewModel.selectTab(tab) },
                                            icon = {
                                                Icon(
                                                    imageVector = tabIcon(tab, selected),
                                                    contentDescription = tab.label
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = tab.label,
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            },
                                            colors = NavigationRailItemDefaults.colors(
                                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
                                        )
                                    }
                                }
                                VerticalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .statusBarsPadding()
                                        .navigationBarsPadding()
                                ) {
                                    contentBody(Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                }
            }

            // Non-intrusive In-App Update Progress / Restart Banner (never kicks user to Play Store)
            if (!updateState.bannerDismissed) {
                val showBanner = updateState.phase is InAppUpdatePhase.PlayUpdateAvailable ||
                    updateState.phase is InAppUpdatePhase.Downloading ||
                    updateState.phase is InAppUpdatePhase.ReadyToInstall
                if (showBanner) {
                    InAppUpdateBanner(
                        phase = updateState.phase,
                        onStartUpdate = onStartInAppUpdate,
                        onRestartInstall = onCompleteInAppUpdate,
                        onDismiss = onDismissUpdateBanner,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            // Processing Scrim Overlay (Level 4 elevation)
            if (uiState.isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 8.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(26.dp),
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = uiState.processingLabel.ifBlank { "Processing locally…" },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InAppUpdateBanner(
    phase: InAppUpdatePhase,
    onStartUpdate: () -> Unit,
    onRestartInstall: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("in_app_update_banner"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = when (phase) {
                        is InAppUpdatePhase.ReadyToInstall -> Icons.Filled.RestartAlt
                        is InAppUpdatePhase.Downloading -> Icons.Filled.Download
                        else -> Icons.Filled.SystemUpdate
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    when (phase) {
                        is InAppUpdatePhase.PlayUpdateAvailable -> {
                            Text(
                                text = stringResource(R.string.update_available_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Download right inside StudyLeaf without leaving the app.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                            )
                        }
                        is InAppUpdatePhase.Downloading -> {
                            val pct = (phase.progressFraction * 100f).toInt().coerceIn(0, 100)
                            Text(
                                text = "${stringResource(R.string.update_downloading)} $pct%",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        is InAppUpdatePhase.ReadyToInstall -> {
                            Text(
                                text = stringResource(R.string.update_ready_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Tap Restart & Install to finish updating StudyLeaf.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                            )
                        }
                        else -> Unit
                    }
                }
                when (phase) {
                    is InAppUpdatePhase.PlayUpdateAvailable -> {
                        Button(
                            onClick = onStartUpdate,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("update_start_in_app_button")
                        ) {
                            Text(stringResource(R.string.update_download_now), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    is InAppUpdatePhase.ReadyToInstall -> {
                        Button(
                            onClick = onRestartInstall,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("update_restart_install_button")
                        ) {
                            Text(stringResource(R.string.update_restart_install), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    else -> Unit
                }
                if (phase !is InAppUpdatePhase.Downloading) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            if (phase is InAppUpdatePhase.Downloading) {
                if (phase.progressFraction > 0f) {
                    LinearProgressIndicator(
                        progress = { phase.progressFraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

private fun tabIcon(tab: StudyLeafTab, selected: Boolean): ImageVector = when (tab) {
    StudyLeafTab.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
    StudyLeafTab.FILES -> if (selected) Icons.Filled.Folder else Icons.Outlined.Folder
    StudyLeafTab.HISTORY -> if (selected) Icons.Filled.History else Icons.Outlined.History
}
