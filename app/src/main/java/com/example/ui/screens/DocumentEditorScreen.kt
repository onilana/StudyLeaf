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
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAs
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.example.data.local.DocumentEntity
import com.example.data.model.DocumentFormat
import com.example.ui.DocumentEditorUiState
import com.example.ui.components.StudyLeafWindowSize
import com.studyleaf.R

@Composable
fun DocumentEditorScreen(
    document: DocumentEntity,
    editorState: DocumentEditorUiState,
    windowSize: StudyLeafWindowSize,
    onBack: () -> Unit,
    onRunAiExtract: () -> Unit,
    onSave: (
        newTitle: String,
        editedText: String,
        targetFormat: DocumentFormat,
        overwriteOriginal: Boolean,
        fontSizePt: Float,
        textAlignment: Paint.Align
    ) -> Unit
) {
    var titleText by remember(document.id) { mutableStateOf(document.title) }
    var bodyText by remember(document.id) { mutableStateOf("") }
    var initializedVersion by remember(document.id) { mutableStateOf(-1) }
    var hasUnsavedChanges by remember(document.id) { mutableStateOf(false) }

    val undoHistory = remember(document.id) { mutableStateListOf<String>() }
    val redoHistory = remember(document.id) { mutableStateListOf<String>() }

    var showFindReplace by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var replaceStatus by remember { mutableStateOf<String?>(null) }

    var fontSizePt by remember { mutableFloatStateOf(13f) }
    var textAlignment by remember { mutableStateOf(Paint.Align.LEFT) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    // Hydrate editor whenever extraction completes or refreshes via AI OCR
    LaunchedEffect(editorState.extractionVersion) {
        if (editorState.extractionVersion > initializedVersion && !editorState.isLoading) {
            if (initializedVersion >= 0 && bodyText.isNotBlank() && bodyText != editorState.extractedText) {
                undoHistory.add(bodyText)
                redoHistory.clear()
                hasUnsavedChanges = true
            }
            bodyText = editorState.extractedText
            initializedVersion = editorState.extractionVersion
        }
    }

    fun updateBody(nextText: String, recordSnapshot: Boolean = true) {
        if (nextText == bodyText) return
        if (recordSnapshot) {
            val shouldPush = undoHistory.isEmpty() ||
                kotlin.math.abs(nextText.length - bodyText.length) >= 4 ||
                nextText.endsWith(" ") ||
                nextText.endsWith("\n")
            if (shouldPush) {
                if (undoHistory.size >= 40) undoHistory.removeAt(0)
                undoHistory.add(bodyText)
            }
            redoHistory.clear()
        }
        bodyText = nextText
        hasUnsavedChanges = true
    }

    val handleBack: () -> Unit = {
        if (hasUnsavedChanges) {
            showDiscardDialog = true
        } else {
            onBack()
        }
    }

    BackHandler(onBack = handleBack)

    val wordCount = remember(bodyText) {
        bodyText.trim().split(Regex("\\s+")).count { it.isNotBlank() }
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
        val isWordOriginal = document.format == DocumentFormat.WORD
        val overwriteFormat = if (isWordOriginal) DocumentFormat.WORD else DocumentFormat.PDF
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
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
                        text = "Choose how to save \"${titleText.ifBlank { document.title }}\":",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            showSaveDialog = false
                            hasUnsavedChanges = false
                            onSave(
                                titleText.ifBlank { document.title },
                                bodyText,
                                overwriteFormat,
                                true,
                                fontSizePt,
                                textAlignment
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_save_overwrite"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isWordOriginal) {
                                "Update Existing Word (.docx)"
                            } else {
                                "Update Existing PDF"
                            }
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            showSaveDialog = false
                            hasUnsavedChanges = false
                            onSave(
                                "${titleText.ifBlank { document.title }} (Edited)",
                                bodyText,
                                DocumentFormat.PDF,
                                false,
                                fontSizePt,
                                textAlignment
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_save_new_pdf"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.editor_save_as_pdf))
                    }

                    OutlinedButton(
                        onClick = {
                            showSaveDialog = false
                            hasUnsavedChanges = false
                            onSave(
                                "${titleText.ifBlank { document.title }} (Editable)",
                                bodyText,
                                DocumentFormat.WORD,
                                false,
                                fontSizePt,
                                textAlignment
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editor_save_as_word"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Article,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.editor_save_as_word))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
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
                        text = "${document.format.label} • $wordCount words",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        if (undoHistory.isNotEmpty()) {
                            val previous = undoHistory.removeAt(undoHistory.lastIndex)
                            redoHistory.add(bodyText)
                            bodyText = previous
                            hasUnsavedChanges = true
                        }
                    },
                    enabled = undoHistory.isNotEmpty(),
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
                    onClick = {
                        if (redoHistory.isNotEmpty()) {
                            val next = redoHistory.removeAt(redoHistory.lastIndex)
                            undoHistory.add(bodyText)
                            bodyText = next
                            hasUnsavedChanges = true
                        }
                    },
                    enabled = redoHistory.isNotEmpty(),
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

        // Formatting, Find & Replace, and AI OCR Toolbar
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
                FilterChip(
                    selected = showFindReplace,
                    onClick = { showFindReplace = !showFindReplace },
                    label = { Text(stringResource(R.string.editor_find_replace), style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.FindReplace,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("editor_find_replace_toggle")
                )

                if (document.format == DocumentFormat.PDF || document.format == DocumentFormat.SCAN) {
                    FilledTonalButton(
                        onClick = onRunAiExtract,
                        enabled = !editorState.isLoading,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("editor_ai_extract_button")
                    ) {
                        if (editorState.isAiExtracting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.editor_ai_extract),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                FilterChip(
                    selected = false,
                    onClick = {
                        val reflowed = reflowExtractedParagraphs(bodyText)
                        if (reflowed != bodyText) {
                            if (undoHistory.size >= 40) undoHistory.removeAt(0)
                            undoHistory.add(bodyText)
                            redoHistory.clear()
                            bodyText = reflowed
                            hasUnsavedChanges = true
                        }
                    },
                    label = { Text("Reflow Lines", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.WrapText,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
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
                        leadingIcon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = "Align $label",
                                modifier = Modifier.size(15.dp)
                            )
                        },
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

        // Expandable Find & Replace Bar (great for editing a specific word across a PDF/Word file)
        AnimatedVisibility(visible = showFindReplace) {
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
                        val matches = remember(bodyText, findQuery) {
                            if (findQuery.isBlank()) 0
                            else Regex(Regex.escape(findQuery), RegexOption.IGNORE_CASE).findAll(bodyText).count()
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
                                    val count = regex.findAll(bodyText).count()
                                    if (count > 0) {
                                        if (undoHistory.size >= 40) undoHistory.removeAt(0)
                                        undoHistory.add(bodyText)
                                        redoHistory.clear()
                                        bodyText = regex.replace(bodyText, Regex.escapeReplacement(replaceQuery))
                                        hasUnsavedChanges = true
                                        replaceStatus = "Replaced $count occurrence(s)"
                                    } else {
                                        replaceStatus = "No matches found"
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

        // Extraction status or notice banner
        if (editorState.statusBanner != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
            ) {
                Text(
                    text = editorState.statusBanner,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        // Main Editable Paper Canvas
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
                        text = if (editorState.isAiExtracting) {
                            "Reading words from PDF pages with AI…"
                        } else {
                            stringResource(R.string.editor_extracting_text)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val horizontalMargin = if (windowSize == StudyLeafWindowSize.COMPACT) 10.dp else 24.dp
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 820.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = horizontalMargin, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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
                                onValueChange = { updateBody(it) },
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

/**
 * Joins accidental hard line breaks inside paragraphs while preserving blank-line paragraph breaks,
 * numbered/bulleted lists, and short headings.
 */
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
