package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.studyleaf.BuildConfig
import com.studyleaf.R
import com.example.data.engine.DocumentEngine
import com.example.data.local.DocumentEntity
import com.example.data.model.ConversionToolType
import com.example.data.model.DocumentFormat
import com.example.ui.LibraryFilter
import com.example.ui.components.CompactToolGridCard
import com.example.ui.components.DocumentListItem
import com.example.ui.components.DocumentSupportingPreviewPane
import com.example.ui.components.StudyLeafBrandMark
import com.example.ui.components.StudyLeafFilterChipsRow
import com.example.ui.components.StudyLeafSearchBar
import com.example.ui.components.StudyLeafWindowSize
import com.example.ui.theme.StudyLeafThemeDefaults
import com.example.update.InAppUpdatePhase
import com.example.update.InAppUpdateUiState
import java.util.Calendar

@Composable
fun HomeScreen(
    windowSize: StudyLeafWindowSize,
    isDarkTheme: Boolean,
    documents: List<DocumentEntity>,
    totalConversions: Int,
    searchQuery: String,
    activeFilter: LibraryFilter,
    selectedPreviewDoc: DocumentEntity?,
    selectedDocAnnotationCount: Int,
    engine: DocumentEngine,
    onToggleTheme: () -> Unit,
    onOpenGeminiAccount: (Int) -> Unit,
    updateState: InAppUpdateUiState = InAppUpdateUiState(),
    onCheckForUpdates: () -> Unit = {},
    onStartInAppUpdate: () -> Unit = {},
    onCompleteInAppUpdate: () -> Unit = {},
    onOpenExternalRelease: (String) -> Unit = {},
    onSearchChange: (String) -> Unit,
    onFilterChange: (LibraryFilter) -> Unit,
    onQuickToolClick: (ConversionToolType) -> Unit,
    onImportDocument: () -> Unit,
    onViewAllFiles: () -> Unit,
    onDocumentClick: (DocumentEntity) -> Unit,
    onOpenReader: (Long) -> Unit,
    onEditDocument: (Long) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onReorderPages: (Long) -> Unit,
    onAskAi: (Long?) -> Unit,
    onRenameRequest: (DocumentEntity) -> Unit,
    onShare: (DocumentEntity) -> Unit,
    onDeleteRequest: (DocumentEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredDocs = remember(documents, searchQuery, activeFilter) {
        documents.filter { doc ->
            val matchesQuery = searchQuery.isBlank() ||
                doc.title.contains(searchQuery, ignoreCase = true) ||
                doc.folderName.contains(searchQuery, ignoreCase = true) ||
                doc.summarySnippet.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (activeFilter) {
                LibraryFilter.ALL -> true
                LibraryFilter.PDF -> doc.format == DocumentFormat.PDF
                LibraryFilter.WORD -> doc.format == DocumentFormat.WORD
                LibraryFilter.EXCEL -> doc.format == DocumentFormat.EXCEL
                LibraryFilter.IMAGE -> doc.format == DocumentFormat.IMAGE
                LibraryFilter.SCAN -> doc.format == DocumentFormat.SCAN
                LibraryFilter.FAVORITES -> doc.isFavorite
            }
            matchesQuery && matchesFilter
        }
    }

    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    if (windowSize == StudyLeafWindowSize.EXPANDED) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = windowSize.horizontalMargin),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            HomeMainColumn(
                greeting = greeting,
                isDarkTheme = isDarkTheme,
                documents = filteredDocs,
                totalCount = documents.size,
                totalConversions = totalConversions,
                searchQuery = searchQuery,
                activeFilter = activeFilter,
                selectedDocId = selectedPreviewDoc?.id,
                onToggleTheme = onToggleTheme,
                onOpenGeminiAccount = onOpenGeminiAccount,
                updateState = updateState,
                onCheckForUpdates = onCheckForUpdates,
                onStartInAppUpdate = onStartInAppUpdate,
                onCompleteInAppUpdate = onCompleteInAppUpdate,
                onOpenExternalRelease = onOpenExternalRelease,
                onSearchChange = onSearchChange,
                onFilterChange = onFilterChange,
                onQuickToolClick = onQuickToolClick,
                onImportDocument = onImportDocument,
                onViewAllFiles = onViewAllFiles,
                onDocumentClick = onDocumentClick,
                onOpenReader = onOpenReader,
                onEditDocument = onEditDocument,
                onToggleFavorite = onToggleFavorite,
                onReorderPages = onReorderPages,
                onAskAi = onAskAi,
                onRenameRequest = onRenameRequest,
                onShare = onShare,
                onDeleteRequest = onDeleteRequest,
                modifier = Modifier
                    .weight(1.18f)
                    .fillMaxHeight()
            )
            Box(
                modifier = Modifier
                    .weight(0.82f)
                    .fillMaxHeight()
                    .padding(vertical = 16.dp)
            ) {
                DocumentSupportingPreviewPane(
                    document = selectedPreviewDoc ?: filteredDocs.firstOrNull(),
                    annotationCount = selectedDocAnnotationCount,
                    engine = engine,
                    onOpenReader = onOpenReader,
                    onReorderPages = onReorderPages,
                    onAskAi = { onAskAi(it) },
                    onShare = onShare
                )
            }
        }
    } else {
        HomeMainColumn(
            greeting = greeting,
            isDarkTheme = isDarkTheme,
            documents = filteredDocs,
            totalCount = documents.size,
            totalConversions = totalConversions,
            searchQuery = searchQuery,
            activeFilter = activeFilter,
            selectedDocId = null,
            onToggleTheme = onToggleTheme,
            onOpenGeminiAccount = onOpenGeminiAccount,
            updateState = updateState,
            onCheckForUpdates = onCheckForUpdates,
            onStartInAppUpdate = onStartInAppUpdate,
            onCompleteInAppUpdate = onCompleteInAppUpdate,
            onOpenExternalRelease = onOpenExternalRelease,
            onSearchChange = onSearchChange,
            onFilterChange = onFilterChange,
            onQuickToolClick = onQuickToolClick,
            onImportDocument = onImportDocument,
            onViewAllFiles = onViewAllFiles,
            onDocumentClick = onDocumentClick,
            onOpenReader = onOpenReader,
            onEditDocument = onEditDocument,
            onToggleFavorite = onToggleFavorite,
            onReorderPages = onReorderPages,
            onAskAi = onAskAi,
            onRenameRequest = onRenameRequest,
            onShare = onShare,
            onDeleteRequest = onDeleteRequest,
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = windowSize.horizontalMargin)
        )
    }
}

@Composable
private fun HomeMainColumn(
    greeting: String,
    isDarkTheme: Boolean,
    documents: List<DocumentEntity>,
    totalCount: Int,
    totalConversions: Int,
    searchQuery: String,
    activeFilter: LibraryFilter,
    selectedDocId: Long?,
    onToggleTheme: () -> Unit,
    onOpenGeminiAccount: (Int) -> Unit,
    updateState: InAppUpdateUiState,
    onCheckForUpdates: () -> Unit,
    onStartInAppUpdate: () -> Unit,
    onCompleteInAppUpdate: () -> Unit,
    onOpenExternalRelease: (String) -> Unit,
    onSearchChange: (String) -> Unit,
    onFilterChange: (LibraryFilter) -> Unit,
    onQuickToolClick: (ConversionToolType) -> Unit,
    onImportDocument: () -> Unit,
    onViewAllFiles: () -> Unit,
    onDocumentClick: (DocumentEntity) -> Unit,
    onOpenReader: (Long) -> Unit,
    onEditDocument: (Long) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onReorderPages: (Long) -> Unit,
    onAskAi: (Long?) -> Unit,
    onRenameRequest: (DocumentEntity) -> Unit,
    onShare: (DocumentEntity) -> Unit,
    onDeleteRequest: (DocumentEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val channels = StudyLeafThemeDefaults.extendedColors
    var showAboutDialog by remember { mutableStateOf(false) }

    // Compact 3-row x 3-column grid (9 tiles total, including On-Device PDF Studio + Gemini AI)
    val gridToolTiles = listOf(
        HomeToolTile(
            title = "Scan Doc",
            desc = "Crop & enhance",
            icon = Icons.Filled.DocumentScanner,
            color = channels.docScanner,
            onClick = { onQuickToolClick(ConversionToolType.SCAN_DOCUMENT) },
            tag = "quick_action_scan"
        ),
        HomeToolTile(
            title = "Image ⇄ PDF",
            desc = "Photos & PNGs",
            icon = Icons.Filled.Image,
            color = channels.docImage,
            onClick = { onQuickToolClick(ConversionToolType.IMAGE_TO_PDF) },
            tag = "quick_action_image_pdf"
        ),
        HomeToolTile(
            title = "Merge PDF",
            desc = "Combine files",
            icon = Icons.Filled.CallMerge,
            color = channels.docPdf,
            onClick = { onQuickToolClick(ConversionToolType.MERGE_PDF) },
            tag = "quick_action_merge"
        ),
        HomeToolTile(
            title = stringResource(R.string.tool_office_converter_title),
            desc = stringResource(R.string.tool_office_converter_desc),
            icon = Icons.Filled.SwapHoriz,
            color = channels.docWord,
            onClick = { onQuickToolClick(ConversionToolType.WORD_TO_PDF) },
            tag = "quick_action_office_converter"
        ),
        HomeToolTile(
            title = stringResource(R.string.tool_compress_img_title),
            desc = stringResource(R.string.tool_compress_img_desc),
            icon = Icons.Filled.PhotoSizeSelectLarge,
            color = channels.docImage,
            onClick = { onQuickToolClick(ConversionToolType.COMPRESS_IMAGE) },
            tag = "tool_compress_image"
        ),
        HomeToolTile(
            title = "Organize PDF",
            desc = "Split · Rotate",
            icon = Icons.Filled.ViewAgenda,
            color = channels.docPdf,
            onClick = { onQuickToolClick(ConversionToolType.SPLIT_REORDER) },
            tag = "tool_split_reorder"
        ),
        HomeToolTile(
            title = "Optimize PDF",
            desc = "Shrink PDF",
            icon = Icons.Filled.Compress,
            color = channels.docPdf,
            onClick = { onQuickToolClick(ConversionToolType.OPTIMIZE_PDF) },
            tag = "tool_optimize_pdf"
        ),
        HomeToolTile(
            title = stringResource(R.string.tool_studio_ai_title),
            desc = stringResource(R.string.tool_studio_ai_desc),
            icon = Icons.Filled.OfflinePin,
            color = MaterialTheme.colorScheme.secondary,
            highlighted = true,
            onClick = { onAskAi(selectedDocId) },
            tag = "tool_on_device_studio_ai"
        )
    )

    val displayedRecent = remember(documents, searchQuery, activeFilter) {
        if (searchQuery.isBlank() && activeFilter == LibraryFilter.ALL) {
            documents.take(5)
        } else {
            documents
        }
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showAboutDialog = true }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StudyLeafBrandMark(size = 32.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "StudyLeaf",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$greeting • $totalCount files • $totalConversions ops",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Surface(
                        onClick = { onAskAi(selectedDocId) },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .padding(end = 2.dp)
                            .testTag("home_ai_companion_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "AI",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("toggle_theme_button")
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = stringResource(R.string.cd_toggle_theme),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { showAboutDialog = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("home_about_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = stringResource(R.string.cd_about_app),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        item(key = "search_bar") {
            StudyLeafSearchBar(
                query = searchQuery,
                onQueryChange = onSearchChange
            )
        }

        item(key = "tools_header") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.tools_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "100% local · zero cloud uploads · sign & redact offline",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = onImportDocument,
                        modifier = Modifier.testTag("home_import_button"),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FileUpload,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Import",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        // Balanced compact tools grid (3 + 3 + 2 equal-width cards)
        item(key = "unified_tools_grid") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                gridToolTiles.chunked(3).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { tile ->
                            CompactToolGridCard(
                                title = tile.title,
                                subtitle = tile.desc,
                                icon = tile.icon,
                                channelColor = tile.color,
                                highlighted = tile.highlighted,
                                onClick = tile.onClick,
                                modifier = Modifier.weight(1f),
                                testTag = tile.tag
                            )
                        }
                    }
                }
            }
        }

        item(key = "recent_header") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.section_recent_documents),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (documents.size > displayedRecent.size) {
                        TextButton(
                            onClick = onViewAllFiles,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("home_view_all_recent")
                        ) {
                            Text(
                                text = "View all (${documents.size})",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    } else {
                        Text(
                            text = "${displayedRecent.size} files",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                StudyLeafFilterChipsRow(
                    selectedFilter = activeFilter,
                    onFilterSelected = onFilterChange
                )
            }
        }

        if (displayedRecent.isEmpty()) {
            item(key = "empty_recent") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.files_empty_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.files_empty_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(displayedRecent, key = { it.id }) { doc ->
                DocumentListItem(
                    document = doc,
                    isSelected = selectedDocId == doc.id,
                    onClick = { onDocumentClick(doc) },
                    onToggleFavorite = { onToggleFavorite(doc.id) },
                    onOpenReader = { onOpenReader(doc.id) },
                    onReorderPages = { onReorderPages(doc.id) },
                    onRename = { onRenameRequest(doc) },
                    onShare = { onShare(doc) },
                    onDelete = { onDeleteRequest(doc) },
                    onEditDocument = { onEditDocument(doc.id) }
                )
            }
        }
    }

    val toolbarColorArgb = MaterialTheme.colorScheme.primary.toArgb()
    if (showAboutDialog) {
        StudyLeafAboutDialog(
            updateState = updateState,
            onCheckForUpdates = onCheckForUpdates,
            onStartInAppUpdate = onStartInAppUpdate,
            onCompleteInAppUpdate = onCompleteInAppUpdate,
            onOpenExternalRelease = onOpenExternalRelease,
            onOpenGeminiAccount = {
                showAboutDialog = false
                onOpenGeminiAccount(toolbarColorArgb)
            },
            onDismiss = { showAboutDialog = false }
        )
    }
}

@Composable
private fun StudyLeafAboutDialog(
    updateState: InAppUpdateUiState,
    onCheckForUpdates: () -> Unit,
    onStartInAppUpdate: () -> Unit,
    onCompleteInAppUpdate: () -> Unit,
    onOpenExternalRelease: (String) -> Unit,
    onOpenGeminiAccount: () -> Unit,
    onDismiss: () -> Unit
) {
    val phase = updateState.phase

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            StudyLeafBrandMark(size = 42.dp)
        },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.about_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AboutInfoRow(
                            icon = Icons.Filled.Person,
                            label = stringResource(R.string.about_author_label),
                            value = stringResource(R.string.about_author_name)
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        )
                        AboutInfoRow(
                            icon = Icons.Filled.VerifiedUser,
                            label = stringResource(R.string.about_publisher_label),
                            value = stringResource(R.string.about_author_name)
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.about_package_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = BuildConfig.APPLICATION_ID,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onOpenGeminiAccount,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("about_open_gemini_account_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.gemini_account_open_in_app),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                when (phase) {
                    is InAppUpdatePhase.ReadyToInstall -> {
                        Button(
                            onClick = onCompleteInAppUpdate,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("about_restart_install_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.update_restart_install),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    is InAppUpdatePhase.PlayUpdateAvailable -> {
                        Button(
                            onClick = onStartInAppUpdate,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("about_download_in_app_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.update_download_now),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    is InAppUpdatePhase.ExternalReleaseAvailable -> {
                        Button(
                            onClick = { onOpenExternalRelease(phase.releaseUrl) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("about_view_external_release_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.update_view_release, phase.latestVersion),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    else -> {
                        FilledTonalButton(
                            onClick = onCheckForUpdates,
                            enabled = phase !is InAppUpdatePhase.Checking && phase !is InAppUpdatePhase.Downloading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("about_check_updates_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (phase is InAppUpdatePhase.Checking || phase is InAppUpdatePhase.Downloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (phase is InAppUpdatePhase.Downloading) {
                                        val pct = (phase.progressFraction * 100f).toInt().coerceIn(0, 100)
                                        "${stringResource(R.string.update_downloading)} $pct%"
                                    } else {
                                        stringResource(R.string.about_checking_updates)
                                    },
                                    style = MaterialTheme.typography.labelLarge
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.SystemUpdate,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.about_check_updates),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }

                if (phase is InAppUpdatePhase.Downloading) {
                    if (phase.progressFraction > 0f) {
                        LinearProgressIndicator(
                            progress = { phase.progressFraction },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }

                val statusText = when (phase) {
                    is InAppUpdatePhase.UpToDate ->
                        stringResource(R.string.about_up_to_date, phase.currentVersion)
                    is InAppUpdatePhase.ReadyToInstall ->
                        stringResource(R.string.update_ready_title)
                    is InAppUpdatePhase.PlayUpdateAvailable ->
                        "New update ready to download directly inside StudyLeaf."
                    is InAppUpdatePhase.ExternalReleaseAvailable ->
                        buildString {
                            append("StudyLeaf v${phase.latestVersion} is available.")
                            if (phase.releaseNotes.isNotBlank()) {
                                append("\n${phase.releaseNotes}")
                            }
                        }
                    is InAppUpdatePhase.Error -> phase.message
                    else -> null
                }

                if (statusText != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("about_update_status")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("about_close_button")
            ) {
                Text(text = stringResource(R.string.action_close))
            }
        }
    )
}

@Composable
private fun AboutInfoRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private data class HomeToolTile(
    val title: String,
    val desc: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit,
    val tag: String,
    val highlighted: Boolean = false
)
