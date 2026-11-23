package com.example.ui.screens

import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.engine.DocumentEngine
import com.example.data.engine.DocxEditableBlock
import com.example.data.engine.PdfRedactionOverlay
import com.example.data.engine.PdfSignatureOverlay
import com.example.data.engine.PdfTextOverlayEdit
import com.example.data.local.DocumentEntity
import com.example.data.model.DocumentFormat
import com.example.ui.DocumentEditorUiState
import com.example.ui.components.DocxPreservedEditorView
import com.example.ui.components.PdfVisualEditorCanvas
import com.example.ui.components.SelectedPdfOverlay
import com.example.ui.components.StudyLeafWindowSize
import com.studyleaf.R

private enum class EditorWorkspaceMode {
    PRESERVE_ORIGINAL,
    PLAIN_TEXT
}

private data class VisualPdfCanvasSnapshot(
    val edits: List<PdfTextOverlayEdit> = emptyList(),
    val signatures: List<PdfSignatureOverlay> = emptyList(),
    val redactions: List<PdfRedactionOverlay> = emptyList()
)

@Composable
fun DocumentEditorScreen(
    document: DocumentEntity,
    editorState: DocumentEditorUiState,
    windowSize: StudyLeafWindowSize,
    engine: DocumentEngine,
    onBack: () -> Unit,
    onRunAiExtract: () -> Unit,
    onSave: (
        newTitle: String,
        editedText: String,
        targetFormat: DocumentFormat,
        overwriteOriginal: Boolean,
        fontSizePt: Float,
        textAlignment: Paint.Align
    ) -> Unit,
    onSaveVisualPdf: (
        newTitle: String,
        edits: List<PdfTextOverlayEdit>,
        signatures: List<PdfSignatureOverlay>,
        redactions: List<PdfRedactionOverlay>,
        sanitizeMetadata: Boolean,
        overwriteOriginal: Boolean
    ) -> Unit,
    onSavePreservedDocx: (
        newTitle: String,
        editedParagraphsByIndex: Map<Int, String>,
        previewBodyText: String,
        overwriteOriginal: Boolean
    ) -> Unit
) {
    val isPdfOrScan = document.format == DocumentFormat.PDF || document.format == DocumentFormat.SCAN
    val isDocx = document.filePath.lowercase().endsWith(".docx")
    val supportsPreserveMode = isPdfOrScan || (isDocx && editorState.docxParsed != null)

    var workspaceMode by remember(document.id) {
        mutableStateOf(
            if (isPdfOrScan || isDocx) EditorWorkspaceMode.PRESERVE_ORIGINAL
            else EditorWorkspaceMode.PLAIN_TEXT
        )
    }

    var titleText by remember(document.id) { mutableStateOf(document.title) }
    var bodyText by remember(document.id) { mutableStateOf("") }
    var initializedVersion by remember(document.id) { mutableStateOf(-1) }
    var hasUnsavedChanges by remember(document.id) { mutableStateOf(false) }

    // Plain-text undo/redo
    val textUndoHistory = remember(document.id) { mutableStateListOf<String>() }
    val textRedoHistory = remember(document.id) { mutableStateListOf<String>() }

    // PDF Visual Studio state (Text + Signatures + Redactions) & unified undo/redo
    var visualState by remember(document.id) { mutableStateOf(VisualPdfCanvasSnapshot()) }
    var selectedVisualOverlay by remember(document.id) { mutableStateOf<SelectedPdfOverlay?>(null) }
    var nextOverlayId by remember(document.id) { mutableLongStateOf(1L) }
    val visualUndoHistory = remember(document.id) { mutableStateListOf<VisualPdfCanvasSnapshot>() }
    val visualRedoHistory = remember(document.id) { mutableStateListOf<VisualPdfCanvasSnapshot>() }
    var sanitizeMetadataOnSave by remember(document.id) { mutableStateOf(false) }

    // DOCX Structured Blocks & undo/redo
    var docxBlocks by remember(document.id) { mutableStateOf<List<DocxEditableBlock>>(emptyList()) }
    val docxUndoHistory = remember(document.id) { mutableStateListOf<List<DocxEditableBlock>>() }
    val docxRedoHistory = remember(document.id) { mutableStateListOf<List<DocxEditableBlock>>() }

    var showFindReplace by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var replaceStatus by remember { mutableStateOf<String?>(null) }

    var fontSizePt by remember { mutableFloatStateOf(13f) }
    var textAlignment by remember { mutableStateOf(Paint.Align.LEFT) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    LaunchedEffect(editorState.extractionVersion) {
        if (editorState.extractionVersion > initializedVersion && !editorState.isLoading) {
            if (initializedVersion >= 0 && bodyText.isNotBlank() && bodyText != editorState.extractedText) {
                textUndoHistory.add(bodyText)
                textRedoHistory.clear()
                hasUnsavedChanges = true
            }
            bodyText = editorState.extractedText
            editorState.docxParsed?.let { parsed ->
                if (docxBlocks.isEmpty()) {
                    docxBlocks = parsed.blocks
                }
            }
            if (isDocx && editorState.docxParsed == null) {
                workspaceMode = EditorWorkspaceMode.PLAIN_TEXT
            }
            initializedVersion = editorState.extractionVersion
        }
    }

    val effectiveMode = if (supportsPreserveMode) workspaceMode else EditorWorkspaceMode.PLAIN_TEXT

    fun updatePlainBody(nextText: String, recordSnapshot: Boolean = true) {
        if (nextText == bodyText) return
        if (recordSnapshot) {
            val shouldPush = textUndoHistory.isEmpty() ||
                kotlin.math.abs(nextText.length - bodyText.length) >= 4 ||
                nextText.endsWith(" ") ||
                nextText.endsWith("\n")
            if (shouldPush) {
                if (textUndoHistory.size >= 40) textUndoHistory.removeAt(0)
                textUndoHistory.add(bodyText)
            }
            textRedoHistory.clear()
        }
        bodyText = nextText
        hasUnsavedChanges = true
    }

    fun pushVisualSnapshot(nextSnapshot: VisualPdfCanvasSnapshot, recordUndo: Boolean = true) {
        if (recordUndo) {
            if (visualUndoHistory.size >= 30) visualUndoHistory.removeAt(0)
            visualUndoHistory.add(visualState)
            visualRedoHistory.clear()
        }
        visualState = nextSnapshot
        hasUnsavedChanges = true
    }

    fun updateDocxBlock(paragraphIndex: Int, nextText: String) {
        val current = docxBlocks
        val prev = current.firstOrNull { it.paragraphIndex == paragraphIndex } ?: return
        if (prev.text == nextText) return
        if (docxUndoHistory.isEmpty() || kotlin.math.abs(nextText.length - prev.text.length) >= 4 || nextText.endsWith(" ")) {
            if (docxUndoHistory.size >= 40) docxUndoHistory.removeAt(0)
            docxUndoHistory.add(current)
            docxRedoHistory.clear()
        }
        docxBlocks = current.map { block ->
            if (block.paragraphIndex == paragraphIndex) block.copy(text = nextText) else block
        }
        hasUnsavedChanges = true
    }

    val canUndo = when {
        effectiveMode == EditorWorkspaceMode.PRESERVE_ORIGINAL && isPdfOrScan -> visualUndoHistory.isNotEmpty()
        effectiveMode == EditorWorkspaceMode.PRESERVE_ORIGINAL && isDocx -> docxUndoHistory.isNotEmpty()
        else -> textUndoHistory.isNotEmpty()
    }
    val canRedo = when {
        effectiveMode == EditorWorkspaceMode.PRESERVE_ORIGINAL && isPdfOrScan -> visualRedoHistory.isNotEmpty()
        effectiveMode == EditorWorkspaceMode.PRESERVE_ORIGINAL && isDocx -> docxRedoHistory.isNotEmpty()
        else -> textRedoHistory.isNotEmpty()
    }

    fun performUndo() {
        when {
            effectiveMode == EditorWorkspaceMode.PRESERVE_ORIGINAL && isPdfOrScan -> {
                if (visualUndoHistory.isNotEmpty()) {
                    visualRedoHistory.add(visualState)
                    visualState = visualUndoHistory.removeAt(visualUndoHistory.lastIndex)
                    selectedVisualOverlay = null
                }
            }
            effectiveMode == EditorWorkspaceMode.PRESERVE_ORIGINAL && isDocx -> {
                if (docxUndoHistory.isNotEmpty()) {
                    docxRedoHistory.add(docxBlocks)
                    docxBlocks = docxUndoHistory.removeAt(docxUndoHistory.lastIndex)
                }
            }
            else -> {
                if (textUndoHistory.isNotEmpty()) {
                    textRedoHistory.add(bodyText)
                    bodyText = textUndoHistory.removeAt(textUndoHistory.lastIndex)
                }
            }
        }
        hasUnsavedChanges = true
    }

    fun performRedo() {
        when {
            effectiveMode == EditorWorkspaceMode.PRESERVE_ORIGINAL && isPdfOrScan -> {
                if (visualRedoHistory.isNotEmpty()) {
                    visualUndoHistory.add(visualState)
                    visualState = visualRedoHistory.removeAt(visualRedoHistory.lastIndex)
                    selectedVisualOverlay = null
                }
            }
            effectiveMode == EditorWorkspaceMode.PRESERVE_ORIGINAL && isDocx -> {
                if (docxRedoHistory.isNotEmpty()) {
                    docxUndoHistory.add(docxBlocks)
                    docxBlocks = docxRedoHistory.removeAt(docxRedoHistory.lastIndex)
                }
            }
            else -> {
                if (textRedoHistory.isNotEmpty()) {
                    textUndoHistory.add(bodyText)
                    bodyText = textRedoHistory.removeAt(textRedoHistory.lastIndex)
                }
            }
        }
        hasUnsavedChanges = true
    }

    val handleBack: () -> Unit = {
        if (hasUnsavedChanges) showDiscardDialog = true else onBack()
    }

    BackHandler(onBack = handleBack)

    val totalVisualItems = visualState.edits.size + visualState.signatures.size + visualState.redactions.size
    val subtitleLabel = remember(effectiveMode, isPdfOrScan, isDocx, totalVisualItems, docxBlocks, bodyText) {
        when {
            effectiveMode == EditorWorkspaceMode.PRESERVE_ORIGINAL && isPdfOrScan ->
                "Local PDF Studio • $totalVisualItems edit(s) • 100% private"
            effectiveMode == EditorWorkspaceMode.PRESERVE_ORIGINAL && isDocx -> {
                val words = docxBlocks.sumOf { b -> b.text.trim().split(Regex("\\s+")).count { it.isNotBlank() } }
                "DOCX • $words words • Format preserved"
            }
            else -> {
                val words = bodyText.trim().split(Regex("\\s+")).count { it.isNotBlank() }
                "${document.format.label} • $words words"
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard unsaved edits?") },
            text = { Text("You have edited \"${titleText.ifBlank { document.title }}\". Leave without saving?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onBack()
                    },
                    modifier = Modifier.testTag("editor_confirm_discard")
                ) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep Editing")
                }
            }
        )
    }

    if (showSaveDialog) {
        val hasRedactions = visualState.redactions.isNotEmpty()
        EditorSaveDialog(
            document = document,
            titleText = titleText,
            isPreservePdf = effectiveMode == EditorWorkspaceMode.PRESERVE_ORIGINAL && isPdfOrScan,
            isPreserveDocx = effectiveMode == EditorWorkspaceMode.PRESERVE_ORIGINAL && isDocx && editorState.docxParsed != null,
            hasRedactions = hasRedactions,
            sanitizeMetadata = sanitizeMetadataOnSave || hasRedactions,
            onSanitizeMetadataChange = { sanitizeMetadataOnSave = it },
            onDismiss = { showSaveDialog = false },
            onSaveVisualPdfOverwrite = {
                showSaveDialog = false
                hasUnsavedChanges = false
                onSaveVisualPdf(
                    titleText.ifBlank { document.title },
                    visualState.edits,
                    visualState.signatures,
                    visualState.redactions,
                    sanitizeMetadataOnSave || hasRedactions,
                    true
                )
            },
            onSaveVisualPdfCopy = {
                showSaveDialog = false
                hasUnsavedChanges = false
                onSaveVisualPdf(
                    "${titleText.ifBlank { document.title }} (Edited)",
                    visualState.edits,
                    visualState.signatures,
                    visualState.redactions,
                    sanitizeMetadataOnSave || hasRedactions,
                    false
                )
            },
            onSaveDocxPreserved = { overwrite ->
                showSaveDialog = false
                hasUnsavedChanges = false
                val changedMap = docxBlocks
                    .filter { it.text != it.originalText }
                    .associate { it.paragraphIndex to it.text }
                val combinedPreview = docxBlocks.map { it.text }.filter { it.isNotBlank() }.joinToString("\n\n")
                val resolvedTitle = if (overwrite) titleText.ifBlank { document.title } else "${titleText.ifBlank { document.title }} (Edited)"
                onSavePreservedDocx(resolvedTitle, changedMap, combinedPreview, overwrite)
            },
            onSavePlain = { resolvedTitle, format, overwrite ->
                showSaveDialog = false
                hasUnsavedChanges = false
                val textToSave = if (effectiveMode == EditorWorkspaceMode.PRESERVE_ORIGINAL && isDocx) {
                    docxBlocks.map { it.text }.filter { it.isNotBlank() }.joinToString("\n\n")
                } else {
                    bodyText
                }
                onSave(resolvedTitle, textToSave, format, overwrite, fontSizePt, textAlignment)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Slim Editor Top Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = handleBack,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("editor_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.editor_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitleLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = ::performUndo,
                    enabled = canUndo,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("editor_undo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = stringResource(R.string.cd_undo),
                        modifier = Modifier.size(19.dp)
                    )
                }

                IconButton(
                    onClick = ::performRedo,
                    enabled = canRedo,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("editor_redo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = stringResource(R.string.cd_redo),
                        modifier = Modifier.size(19.dp)
                    )
                }

                Button(
                    onClick = { showSaveDialog = true },
                    enabled = !editorState.isLoading,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier
                        .padding(end = 6.dp, start = 4.dp)
                        .testTag("editor_save_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Save,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Mode Switcher & Contextual Toolbar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (supportsPreserveMode) {
                    FilterChip(
                        selected = effectiveMode == EditorWorkspaceMode.PRESERVE_ORIGINAL,
                        onClick = { workspaceMode = EditorWorkspaceMode.PRESERVE_ORIGINAL },
                        label = {
                            Text(
                                text = stringResource(R.string.editor_mode_preserve_layout),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.Layers, contentDescription = null, modifier = Modifier.size(15.dp))
                        },
                        modifier = Modifier.testTag("editor_mode_preserve")
                    )

                    FilterChip(
                        selected = effectiveMode == EditorWorkspaceMode.PLAIN_TEXT,
                        onClick = { workspaceMode = EditorWorkspaceMode.PLAIN_TEXT },
                        label = {
                            Text(
                                text = stringResource(R.string.editor_mode_plain_text),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.TextFields, contentDescription = null, modifier = Modifier.size(15.dp))
                        },
                        modifier = Modifier.testTag("editor_mode_plain_text")
                    )
                }

                if (!(effectiveMode == EditorWorkspaceMode.PRESERVE_ORIGINAL && isPdfOrScan)) {
                    FilterChip(
                        selected = showFindReplace,
                        onClick = { showFindReplace = !showFindReplace },
                        label = { Text(stringResource(R.string.editor_find_replace), style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(Icons.Filled.FindReplace, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.testTag("editor_find_replace_toggle")
                    )
                }

                if (effectiveMode == EditorWorkspaceMode.PLAIN_TEXT) {
                    if (isPdfOrScan) {
                        FilledTonalButton(
                            onClick = onRunAiExtract,
                            enabled = !editorState.isLoading,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("editor_ai_extract_button")
                        ) {
                            if (editorState.isAiExtracting) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.editor_ai_extract), style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    FilterChip(
                        selected = false,
                        onClick = {
                            val reflowed = reflowExtractedParagraphs(bodyText)
                            if (reflowed != bodyText) {
                                if (textUndoHistory.size >= 40) textUndoHistory.removeAt(0)
                                textUndoHistory.add(bodyText)
                                textRedoHistory.clear()
                                bodyText = reflowed
                                hasUnsavedChanges = true
                            }
                        },
                        label = { Text("Reflow Lines", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.WrapText, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.testTag("editor_reflow_button")
                    )

                    listOf(
                        Triple(Paint.Align.LEFT, Icons.AutoMirrored.Filled.FormatAlignLeft, "Left"),
                        Triple(Paint.Align.CENTER, Icons.Filled.FormatAlignCenter, "Center"),
                        Triple(Paint.Align.RIGHT, Icons.AutoMirrored.Filled.FormatAlignRight, "Right")
                    ).forEach { (align, icon, label) ->
                        FilterChip(
                            selected = textAlignment == align,
                            onClick = { textAlignment = align },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(icon, contentDescription = "Align $label", modifier = Modifier.size(15.dp)) },
                            modifier = Modifier.testTag("editor_align_${label.lowercase()}")
                        )
                    }

                    listOf(11f to "Small", 13f to "Normal", 15f to "Large").forEach { (pt, label) ->
                        FilterChip(
                            selected = fontSizePt == pt,
                            onClick = { fontSizePt = pt },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }

        // Expandable Find & Replace Bar (supports Preserved DOCX and Plain Text)
        AnimatedVisibility(visible = showFindReplace && !(effectiveMode == EditorWorkspaceMode.PRESERVE_ORIGINAL && isPdfOrScan)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = findQuery,
                            onValueChange = {
                                findQuery = it
                                replaceStatus = null
                            },
                            placeholder = { Text("Find word…", style = MaterialTheme.typography.bodySmall) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("editor_find_input")
                        )
                        OutlinedTextField(
                            value = replaceQuery,
                            onValueChange = {
                                replaceQuery = it
                                replaceStatus = null
                            },
                            placeholder = { Text("Replace with…", style = MaterialTheme.typography.bodySmall) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("editor_replace_input")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isDocxPreserve = effectiveMode == EditorWorkspaceMode.PRESERVE_ORIGINAL && isDocx
                        val matches = remember(bodyText, docxBlocks, findQuery, isDocxPreserve) {
                            if (findQuery.isBlank()) 0
                            else {
                                val regex = Regex(Regex.escape(findQuery), RegexOption.IGNORE_CASE)
                                if (isDocxPreserve) docxBlocks.sumOf { regex.findAll(it.text).count() }
                                else regex.findAll(bodyText).count()
                            }
                        }
                        Text(
                            text = replaceStatus ?: if (findQuery.isBlank()) {
                                "Enter a word to find & replace"
                            } else {
                                "$matches match(es) found"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilledTonalButton(
                            onClick = {
                                if (findQuery.isNotBlank()) {
                                    val regex = Regex(Regex.escape(findQuery), RegexOption.IGNORE_CASE)
                                    val safeReplacement = Regex.escapeReplacement(replaceQuery)
                                    if (isDocxPreserve) {
                                        val count = docxBlocks.sumOf { regex.findAll(it.text).count() }
                                        if (count > 0) {
                                            if (docxUndoHistory.size >= 40) docxUndoHistory.removeAt(0)
                                            docxUndoHistory.add(docxBlocks)
                                            docxRedoHistory.clear()
                                            docxBlocks = docxBlocks.map { b ->
                                                b.copy(text = regex.replace(b.text, safeReplacement))
                                            }
                                            hasUnsavedChanges = true
                                            replaceStatus = "Replaced $count occurrence(s) keeping format"
                                        } else {
                                            replaceStatus = "No matches found"
                                        }
                                    } else {
                                        val count = regex.findAll(bodyText).count()
                                        if (count > 0) {
                                            if (textUndoHistory.size >= 40) textUndoHistory.removeAt(0)
                                            textUndoHistory.add(bodyText)
                                            textRedoHistory.clear()
                                            bodyText = regex.replace(bodyText, safeReplacement)
                                            hasUnsavedChanges = true
                                            replaceStatus = "Replaced $count occurrence(s)"
                                        } else {
                                            replaceStatus = "No matches found"
                                        }
                                    }
                                }
                            },
                            enabled = findQuery.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("editor_replace_all_button")
                        ) {
                            Text("Replace All", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // Main Editor Workspace
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            if (editorState.isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.editor_extracting_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val horizontalMargin = if (windowSize == StudyLeafWindowSize.COMPACT) 10 else 24
                when {
                    effectiveMode == EditorWorkspaceMode.PRESERVE_ORIGINAL && isPdfOrScan -> {
                        PdfVisualEditorCanvas(
                            document = document,
                            pageCount = editorState.pageCount,
                            engine = engine,
                            detectedLines = editorState.pdfTextLines,
                            edits = visualState.edits,
                            signatures = visualState.signatures,
                            redactions = visualState.redactions,
                            selectedOverlay = selectedVisualOverlay,
                            onSelectOverlay = { selectedVisualOverlay = it },
                            onEditExistingLine = { line, coverColorArgb, inkColorArgb ->
                                val existing = visualState.edits.firstOrNull {
                                    it.pageIndex == line.pageIndex && it.sourceLineId == line.id
                                }
                                if (existing != null) {
                                    selectedVisualOverlay = SelectedPdfOverlay.TextBox(existing.id)
                                } else {
                                    val id = nextOverlayId++
                                    val paddedX = (line.xFraction - 0.004f).coerceIn(0f, 0.92f)
                                    val paddedY = (line.yFraction - 0.002f).coerceIn(0f, 0.94f)
                                    val paddedW = (line.widthFraction + 0.035f)
                                        .coerceIn(0.12f, (1f - paddedX).coerceAtLeast(0.12f))
                                    val paddedH = (line.heightFraction + 0.005f).coerceIn(0.018f, 0.30f)
                                    val item = PdfTextOverlayEdit(
                                        id = id,
                                        pageIndex = line.pageIndex,
                                        xFraction = paddedX,
                                        yFraction = paddedY,
                                        widthFraction = paddedW,
                                        text = line.text,
                                        fontSizePt = line.fontSizePt,
                                        colorArgb = inkColorArgb,
                                        isBold = line.isBold,
                                        alignment = Paint.Align.LEFT,
                                        coverOriginal = true,
                                        coverColorArgb = coverColorArgb,
                                        sourceLineId = line.id,
                                        minCoverHeightFraction = paddedH
                                    )
                                    pushVisualSnapshot(
                                        visualState.copy(edits = visualState.edits + item),
                                        recordUndo = true
                                    )
                                    selectedVisualOverlay = SelectedPdfOverlay.TextBox(id)
                                }
                            },
                            onAddTextAt = { pageIdx, xFrac, yFrac, preset, cover, bold, colorArgb, wFrac ->
                                val id = nextOverlayId++
                                val item = PdfTextOverlayEdit(
                                    id = id,
                                    pageIndex = pageIdx,
                                    xFraction = xFrac.coerceIn(0.02f, (1f - wFrac).coerceAtLeast(0.05f)),
                                    yFraction = yFrac.coerceIn(0.02f, 0.90f),
                                    widthFraction = wFrac,
                                    text = preset,
                                    fontSizePt = if (preset == "✓" || preset == "✗") 18f else 13f,
                                    colorArgb = colorArgb,
                                    isBold = bold,
                                    coverOriginal = cover
                                )
                                pushVisualSnapshot(visualState.copy(edits = visualState.edits + item), recordUndo = true)
                                selectedVisualOverlay = SelectedPdfOverlay.TextBox(id)
                            },
                            onUpdateTextEdit = { updated ->
                                visualState = visualState.copy(
                                    edits = visualState.edits.map { if (it.id == updated.id) updated else it }
                                )
                                hasUnsavedChanges = true
                            },
                            onDeleteTextEdit = { id ->
                                pushVisualSnapshot(
                                    visualState.copy(edits = visualState.edits.filterNot { it.id == id }),
                                    recordUndo = true
                                )
                                if (selectedVisualOverlay == SelectedPdfOverlay.TextBox(id)) selectedVisualOverlay = null
                            },
                            onAddSignatureAt = { pageIdx, xFrac, yFrac, strokes, colorArgb ->
                                val id = nextOverlayId++
                                val sig = PdfSignatureOverlay(
                                    id = id,
                                    pageIndex = pageIdx,
                                    xFraction = xFrac.coerceIn(0.02f, 0.62f),
                                    yFraction = yFrac.coerceIn(0.02f, 0.84f),
                                    widthFraction = 0.34f,
                                    strokes = strokes,
                                    colorArgb = colorArgb
                                )
                                pushVisualSnapshot(visualState.copy(signatures = visualState.signatures + sig), recordUndo = true)
                                selectedVisualOverlay = SelectedPdfOverlay.Signature(id)
                            },
                            onUpdateSignature = { updated ->
                                visualState = visualState.copy(
                                    signatures = visualState.signatures.map { if (it.id == updated.id) updated else it }
                                )
                                hasUnsavedChanges = true
                            },
                            onDeleteSignature = { id ->
                                pushVisualSnapshot(
                                    visualState.copy(signatures = visualState.signatures.filterNot { it.id == id }),
                                    recordUndo = true
                                )
                                if (selectedVisualOverlay == SelectedPdfOverlay.Signature(id)) selectedVisualOverlay = null
                            },
                            onAddRedactionAt = { pageIdx, xFrac, yFrac ->
                                val id = nextOverlayId++
                                val red = PdfRedactionOverlay(
                                    id = id,
                                    pageIndex = pageIdx,
                                    xFraction = xFrac.coerceIn(0.02f, 0.60f),
                                    yFraction = yFrac.coerceIn(0.02f, 0.90f),
                                    widthFraction = 0.36f,
                                    heightFraction = 0.045f
                                )
                                pushVisualSnapshot(visualState.copy(redactions = visualState.redactions + red), recordUndo = true)
                                selectedVisualOverlay = SelectedPdfOverlay.Redaction(id)
                            },
                            onUpdateRedaction = { updated ->
                                visualState = visualState.copy(
                                    redactions = visualState.redactions.map { if (it.id == updated.id) updated else it }
                                )
                                hasUnsavedChanges = true
                            },
                            onDeleteRedaction = { id ->
                                pushVisualSnapshot(
                                    visualState.copy(redactions = visualState.redactions.filterNot { it.id == id }),
                                    recordUndo = true
                                )
                                if (selectedVisualOverlay == SelectedPdfOverlay.Redaction(id)) selectedVisualOverlay = null
                            }
                        )
                    }

                    effectiveMode == EditorWorkspaceMode.PRESERVE_ORIGINAL && isDocx && editorState.docxParsed != null -> {
                        DocxPreservedEditorView(
                            titleText = titleText,
                            onTitleChange = {
                                titleText = it
                                hasUnsavedChanges = true
                            },
                            blocks = docxBlocks,
                            embeddedImageCount = editorState.docxParsed.embeddedImageCount,
                            onBlockTextChange = ::updateDocxBlock,
                            horizontalMarginDp = horizontalMargin
                        )
                    }

                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 820.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = horizontalMargin.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (supportsPreserveMode) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Plain Text mode reflows text without original images. Switch back to Preserve Layout to edit, sign, or redact on the original pages.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 2.dp,
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                ) {
                                    OutlinedTextField(
                                        value = titleText,
                                        onValueChange = {
                                            titleText = it
                                            hasUnsavedChanges = true
                                        },
                                        label = { Text("Document Title") },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("editor_title_input")
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    val composeAlign = when (textAlignment) {
                                        Paint.Align.CENTER -> TextAlign.Center
                                        Paint.Align.RIGHT -> TextAlign.End
                                        else -> TextAlign.Start
                                    }

                                    OutlinedTextField(
                                        value = bodyText,
                                        onValueChange = { updatePlainBody(it) },
                                        placeholder = {
                                            Text(
                                                text = stringResource(R.string.editor_placeholder),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        },
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                                            fontSize = (fontSizePt + 2f).sp,
                                            lineHeight = ((fontSizePt + 2f) * 1.52f).sp,
                                            textAlign = composeAlign
                                        ),
                                        minLines = 18,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color.Transparent,
                                            unfocusedBorderColor = Color.Transparent,
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("editor_body_input")
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorSaveDialog(
    document: DocumentEntity,
    titleText: String,
    isPreservePdf: Boolean,
    isPreserveDocx: Boolean,
    hasRedactions: Boolean,
    sanitizeMetadata: Boolean,
    onSanitizeMetadataChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSaveVisualPdfOverwrite: () -> Unit,
    onSaveVisualPdfCopy: () -> Unit,
    onSaveDocxPreserved: (overwrite: Boolean) -> Unit,
    onSavePlain: (title: String, format: DocumentFormat, overwrite: Boolean) -> Unit
) {
    val resolvedName = titleText.ifBlank { document.title }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.SaveAs,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(stringResource(R.string.editor_save_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = when {
                        isPreservePdf -> "Save \"$resolvedName\" locally with all original images, signatures & layout intact:"
                        isPreserveDocx -> "Save \"$resolvedName\" with original Word styles, images & alignment preserved:"
                        else -> "Choose how to save \"$resolvedName\":"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isPreservePdf) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Sanitize & Strip Hidden Text",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (hasRedactions) "Enforced automatically for redacted files" else "Removes cached metadata & hidden text layers",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = sanitizeMetadata,
                                onCheckedChange = { if (!hasRedactions) onSanitizeMetadataChange(it) },
                                enabled = !hasRedactions,
                                modifier = Modifier.testTag("editor_sanitize_switch")
                            )
                        }
                    }
                }

                when {
                    isPreservePdf -> {
                        Button(
                            onClick = onSaveVisualPdfOverwrite,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_save_overwrite"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Update Existing PDF (Keep Layout)")
                        }
                        FilledTonalButton(
                            onClick = onSaveVisualPdfCopy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_save_new_pdf"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save as New PDF Copy (Keep Layout)")
                        }
                    }

                    isPreserveDocx -> {
                        Button(
                            onClick = { onSaveDocxPreserved(true) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_save_overwrite"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Update Existing Word (.docx)")
                        }
                        FilledTonalButton(
                            onClick = { onSaveDocxPreserved(false) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_save_as_word"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Article, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save as New Word Copy (.docx)")
                        }
                        OutlinedButton(
                            onClick = { onSavePlain("$resolvedName (PDF)", DocumentFormat.PDF, false) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_save_new_pdf"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.editor_save_as_pdf))
                        }
                    }

                    else -> {
                        val isWordOriginal = document.format == DocumentFormat.WORD
                        val overwriteFormat = if (isWordOriginal) DocumentFormat.WORD else DocumentFormat.PDF
                        Button(
                            onClick = { onSavePlain(resolvedName, overwriteFormat, true) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_save_overwrite"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isWordOriginal) "Update Existing Word (.docx)" else "Update Existing PDF")
                        }
                        FilledTonalButton(
                            onClick = { onSavePlain("$resolvedName (Edited)", DocumentFormat.PDF, false) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_save_new_pdf"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.editor_save_as_pdf))
                        }
                        OutlinedButton(
                            onClick = { onSavePlain("$resolvedName (Editable)", DocumentFormat.WORD, false) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_save_as_word"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Article, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.editor_save_as_word))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

private fun reflowExtractedParagraphs(raw: String): String {
    if (raw.isBlank()) return raw
    val normalized = raw.replace("\r\n", "\n").trim()
    val paragraphs = normalized.split(Regex("\\n{2,}"))
    return paragraphs.joinToString("\n\n") { para ->
        val lines = para.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size <= 1) return@joinToString lines.firstOrNull().orEmpty()
        val out = StringBuilder()
        for (idx in lines.indices) {
            val line = lines[idx]
            val startsList = line.startsWith("•") || line.startsWith("- ") ||
                line.startsWith("* ") || Regex("^\\d+[.)]\\s+").containsMatchIn(line)
            if (out.isNotEmpty()) {
                if (startsList) {
                    out.append('\n')
                } else if (out.endsWith("-") && !out.endsWith(" -")) {
                    out.deleteCharAt(out.length - 1)
                } else {
                    out.append(' ')
                }
            }
            out.append(line)
        }
        out.toString()
    }
}
