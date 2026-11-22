package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.DocumentEntity
import com.example.ui.AiAssistantMode
import com.example.ui.AiAssistantUiState
import com.example.ui.components.FormatBadge
import com.example.ui.components.StudyLeafWindowSize
import com.studyleaf.R

@Composable
fun StudyAiAssistantScreen(
    documents: List<DocumentEntity>,
    activeDoc: DocumentEntity?,
    pageIndex: Int,
    aiState: AiAssistantUiState,
    windowSize: StudyLeafWindowSize,
    onSelectDocument: (Long) -> Unit,
    onSelectMode: (AiAssistantMode) -> Unit,
    onOpenGeminiInApp: (toolbarColorArgb: Int, mode: AiAssistantMode?, customQuestion: String) -> Unit,
    onSharePageToGeminiApp: (mode: AiAssistantMode, customQuestion: String) -> Unit,
    onUpdateSavedReplyDraft: (String) -> Unit,
    onPasteClipboardReply: () -> Unit,
    onPinSummaryAsNote: (String) -> Unit,
    onExportGuideToPdf: (String, String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var questionInput by remember { mutableStateOf("") }
    val toolbarColorArgb = MaterialTheme.colorScheme.primary.toArgb()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Top Header Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("ai_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Google Gemini",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (activeDoc != null) {
                                "${activeDoc.title} • Page ${pageIndex + 1}"
                            } else {
                                "Sign in with your own Google account"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (activeDoc != null) {
                        FormatBadge(format = activeDoc.format)
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    IconButton(
                        onClick = { onOpenGeminiInApp(toolbarColorArgb, null, "") },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("ai_open_account_top_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = stringResource(R.string.cd_open_gemini_account),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Document Switcher Strip
                if (documents.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        documents.forEach { doc ->
                            val selected = doc.id == activeDoc?.id
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectDocument(doc.id) },
                                shape = CircleShape,
                                label = {
                                    Text(
                                        text = doc.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier.testTag("ai_doc_chip_${doc.id}")
                            )
                        }
                    }
                }

                // Mode Switcher Pills: Summary | Flashcards | Ask Gemini
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiAssistantMode.entries.forEach { mode ->
                        val selected = aiState.activeMode == mode
                        Surface(
                            onClick = { onSelectMode(mode) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 42.dp)
                                .testTag("ai_mode_${mode.name.lowercase()}"),
                            shape = CircleShape,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                val icon = when (mode) {
                                    AiAssistantMode.SUMMARIZE -> Icons.Filled.Summarize
                                    AiAssistantMode.FLASHCARDS -> Icons.Filled.Style
                                    AiAssistantMode.ASK -> Icons.Filled.AutoAwesome
                                }
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Main Workspace Body
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = windowSize.horizontalMargin),
            contentPadding = PaddingValues(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Account Notice Strip
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = stringResource(R.string.gemini_account_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Active Mode Action Card
            item {
                val (headline, description, primaryLabel) = when (aiState.activeMode) {
                    AiAssistantMode.SUMMARIZE -> Triple(
                        "Summarize Page ${pageIndex + 1}",
                        "Copies a structured summary prompt (with your page text) and opens your own Google Gemini account inside StudyLeaf.",
                        "Summarize in Gemini (In-App)"
                    )
                    AiAssistantMode.FLASHCARDS -> Triple(
                        "Build Study Flashcards",
                        "Copies a Q&A flashcard prompt from Page ${pageIndex + 1} so your Google Gemini account generates exam-ready cards.",
                        "Create Flashcards in Gemini"
                    )
                    AiAssistantMode.ASK -> Triple(
                        "Ask Your Own Gemini Account",
                        "Type any question about Page ${pageIndex + 1}—StudyLeaf copies your question & page context and opens Gemini right inside the app.",
                        "Ask in Gemini (In-App)"
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = headline,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (aiState.activeMode == AiAssistantMode.ASK) {
                            val quickPrompts = listOf(
                                "Explain the core concept simply",
                                "List key formulas & definitions",
                                "Quiz me with 3 exam questions"
                            )
                            OutlinedTextField(
                                value = questionInput,
                                onValueChange = { questionInput = it },
                                placeholder = { Text("What would you like to ask Gemini about this page?") },
                                minLines = 2,
                                maxLines = 4,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ai_question_input")
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                quickPrompts.forEach { preset ->
                                    FilterChip(
                                        selected = questionInput == preset,
                                        onClick = { questionInput = preset },
                                        shape = CircleShape,
                                        label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }

                        // Primary Button: Open Google Gemini in-app (Sign into own Google account)
                        Button(
                            onClick = {
                                onOpenGeminiInApp(toolbarColorArgb, aiState.activeMode, questionInput)
                            },
                            enabled = activeDoc != null,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .testTag("ai_open_gemini_in_app_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(primaryLabel)
                        }

                        // Secondary Button (Option 1): Share rendered Page PNG directly to Google Gemini app
                        FilledTonalButton(
                            onClick = {
                                onSharePageToGeminiApp(aiState.activeMode, questionInput)
                            },
                            enabled = activeDoc != null && !aiState.isPreparingShare,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("ai_share_page_gemini_app_button")
                        ) {
                            if (aiState.isPreparingShare) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Preparing page image…")
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (aiState.isGeminiAppInstalled) {
                                        "Send Page Image to Gemini App"
                                    } else {
                                        "Share Page Image to Gemini / AI App"
                                    }
                                )
                            }
                        }

                        if (aiState.lastCopiedPrompt.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Prompt copied to clipboard — just tap Paste in Gemini.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }

            // Save Gemini Reply Back into StudyLeaf Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.gemini_paste_reply_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            OutlinedButton(
                                onClick = onPasteClipboardReply,
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("ai_paste_clipboard_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentPaste,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Paste", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Text(
                            text = "Copy Gemini's answer, tap Paste, and save it straight onto your document as a sticky note or a new PDF study guide.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = aiState.savedReplyDraft,
                            onValueChange = onUpdateSavedReplyDraft,
                            placeholder = { Text("Paste Gemini's summary, flashcards, or notes here…") },
                            minLines = 3,
                            maxLines = 8,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_saved_reply_input")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { onPinSummaryAsNote(aiState.savedReplyDraft) },
                                enabled = aiState.savedReplyDraft.isNotBlank() && activeDoc != null,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ai_pin_note_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.StickyNote2,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pin Note", maxLines = 1)
                            }
                            Button(
                                onClick = {
                                    val title = "${activeDoc?.title ?: "Document"} — Gemini Study Notes"
                                    onExportGuideToPdf(title, aiState.savedReplyDraft)
                                },
                                enabled = aiState.savedReplyDraft.isNotBlank(),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ai_export_summary_pdf")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PictureAsPdf,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save PDF", maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
