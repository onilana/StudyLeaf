package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.studyleaf.R
import com.example.data.engine.DocumentEngine
import com.example.data.local.DocumentEntity
import com.example.data.model.DocumentFormat
import com.example.data.model.FileViewMode
import com.example.data.model.SortOrder
import com.example.data.model.formatFileSize
import com.example.ui.components.DocumentListItem
import com.example.ui.components.DocumentPreviewIcon
import com.example.ui.components.DocumentSupportingPreviewPane
import com.example.ui.components.FormatBadge
import com.example.ui.components.StudyLeafSearchBar
import com.example.ui.components.StudyLeafWindowSize
import com.example.ui.theme.StudyLeafThemeDefaults

@Composable
fun FilesScreen(
    windowSize: StudyLeafWindowSize,
    documents: List<DocumentEntity>,
    searchQuery: String,
    activeFolder: String,
    viewMode: FileViewMode,
    sortOrder: SortOrder,
    selectedPreviewDoc: DocumentEntity?,
    selectedDocAnnotationCount: Int,
    engine: DocumentEngine,
    onSearchChange: (String) -> Unit,
    onFolderSelected: (String) -> Unit,
    onToggleViewMode: () -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
    onImportDocument: () -> Unit,
    onImportPhotos: () -> Unit,
    onDocumentClick: (DocumentEntity) -> Unit,
    onOpenReader: (Long) -> Unit,
    onEditDocument: (Long) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onReorderPages: (Long) -> Unit,
    onAskAi: (Long) -> Unit,
    onRenameRequest: (DocumentEntity) -> Unit,
    onShare: (DocumentEntity) -> Unit,
    onDeleteRequest: (DocumentEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val folders = remember(documents) {
        val dynamicFolders = documents.map { it.folderName }.distinct().sorted()
        listOf("All") + dynamicFolders
    }

    val filteredDocs = remember(documents, searchQuery, activeFolder, sortOrder) {
        val filtered = documents.filter { doc ->
            val matchesQuery = searchQuery.isBlank() ||
                doc.title.contains(searchQuery, ignoreCase = true) ||
                doc.folderName.contains(searchQuery, ignoreCase = true)
            val matchesFolder = activeFolder == "All" || doc.folderName == activeFolder
            matchesQuery && matchesFolder
        }
        when (sortOrder) {
            SortOrder.RECENT -> filtered.sortedByDescending { it.lastOpenedAt }
            SortOrder.NAME -> filtered.sortedBy { it.title.lowercase() }
            SortOrder.SIZE -> filtered.sortedByDescending { it.sizeBytes }
        }
    }

    if (windowSize == StudyLeafWindowSize.EXPANDED) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = windowSize.horizontalMargin),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            FilesVaultColumn(
                windowSize = windowSize,
                allDocuments = documents,
                filteredDocs = filteredDocs,
                folders = folders,
                searchQuery = searchQuery,
                activeFolder = activeFolder,
                viewMode = viewMode,
                sortOrder = sortOrder,
                selectedDocId = selectedPreviewDoc?.id,
                engine = engine,
                onSearchChange = onSearchChange,
                onFolderSelected = onFolderSelected,
                onToggleViewMode = onToggleViewMode,
                onSortOrderChange = onSortOrderChange,
                onImportDocument = onImportDocument,
                onImportPhotos = onImportPhotos,
                onDocumentClick = onDocumentClick,
                onOpenReader = onOpenReader,
                onEditDocument = onEditDocument,
                onToggleFavorite = onToggleFavorite,
                onReorderPages = onReorderPages,
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
                    onAskAi = onAskAi,
                    onShare = onShare
                )
            }
        }
    } else {
        FilesVaultColumn(
            windowSize = windowSize,
            allDocuments = documents,
            filteredDocs = filteredDocs,
            folders = folders,
            searchQuery = searchQuery,
            activeFolder = activeFolder,
            viewMode = viewMode,
            sortOrder = sortOrder,
            selectedDocId = null,
            engine = engine,
            onSearchChange = onSearchChange,
            onFolderSelected = onFolderSelected,
            onToggleViewMode = onToggleViewMode,
            onSortOrderChange = onSortOrderChange,
            onImportDocument = onImportDocument,
            onImportPhotos = onImportPhotos,
            onDocumentClick = onDocumentClick,
            onOpenReader = onOpenReader,
            onEditDocument = onEditDocument,
            onToggleFavorite = onToggleFavorite,
            onReorderPages = onReorderPages,
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
private fun FilesVaultColumn(
    windowSize: StudyLeafWindowSize,
    allDocuments: List<DocumentEntity>,
    filteredDocs: List<DocumentEntity>,
    folders: List<String>,
    searchQuery: String,
    activeFolder: String,
    viewMode: FileViewMode,
    sortOrder: SortOrder,
    selectedDocId: Long?,
    engine: DocumentEngine,
    onSearchChange: (String) -> Unit,
    onFolderSelected: (String) -> Unit,
    onToggleViewMode: () -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
    onImportDocument: () -> Unit,
    onImportPhotos: () -> Unit,
    onDocumentClick: (DocumentEntity) -> Unit,
    onOpenReader: (Long) -> Unit,
    onEditDocument: (Long) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onReorderPages: (Long) -> Unit,
    onRenameRequest: (DocumentEntity) -> Unit,
    onShare: (DocumentEntity) -> Unit,
    onDeleteRequest: (DocumentEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(top = 16.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "vault_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.files_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${allDocuments.size} documents • ${formatFileSize(allDocuments.sumOf { it.sizeBytes })} on device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(
                            onClick = { sortMenuExpanded = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("files_sort_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort files",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            SortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Sort by ${order.label}",
                                            fontWeight = if (order == sortOrder) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        sortMenuExpanded = false
                                        onSortOrderChange(order)
                                    }
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = onToggleViewMode,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("files_view_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (viewMode == FileViewMode.LIST) Icons.Filled.GridView else Icons.AutoMirrored.Filled.ViewList,
                            contentDescription = stringResource(R.string.cd_grid_toggle),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item(key = "storage_bar") {
            StorageBreakdownCard(documents = allDocuments)
        }

        item(key = "import_actions") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onImportDocument,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("vault_import_document_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import Doc / PDF", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = onImportPhotos,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("vault_import_photos_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import Photo", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        item(key = "vault_search") {
            StudyLeafSearchBar(
                query = searchQuery,
                onQueryChange = onSearchChange,
                placeholder = "Filter local files by name or folder…"
            )
        }

        item(key = "folder_chips") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                folders.forEach { folder ->
                    val selected = folder == activeFolder
                    FilterChip(
                        selected = selected,
                        onClick = { onFolderSelected(folder) },
                        shape = CircleShape,
                        leadingIcon = if (folder != "All") {
                            { Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp)) }
                        } else null,
                        label = { Text(folder, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.testTag("folder_chip_${folder.lowercase()}")
                    )
                }
            }
        }

        if (filteredDocs.isEmpty()) {
            item(key = "empty_vault") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
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
        } else if (viewMode == FileViewMode.LIST) {
            items(filteredDocs, key = { it.id }) { doc ->
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
        } else {
            val gridCols = if (windowSize == StudyLeafWindowSize.COMPACT) 2 else 3
            items(filteredDocs.chunked(gridCols)) { rowDocs ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowDocs.forEach { doc ->
                        DocumentGridCard(
                            document = doc,
                            isSelected = selectedDocId == doc.id,
                            engine = engine,
                            onClick = { onDocumentClick(doc) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(gridCols - rowDocs.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageBreakdownCard(documents: List<DocumentEntity>) {
    val channels = StudyLeafThemeDefaults.extendedColors
    val pdfCount = documents.count { it.format == DocumentFormat.PDF }
    val wordCount = documents.count { it.format == DocumentFormat.WORD }
    val excelCount = documents.count { it.format == DocumentFormat.EXCEL }
    val imgCount = documents.count { it.format == DocumentFormat.IMAGE }
    val scanCount = documents.count { it.format == DocumentFormat.SCAN }
    val total = documents.size.coerceAtLeast(1).toFloat()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (pdfCount > 0) {
                    Box(modifier = Modifier.weight(pdfCount / total).fillMaxHeight().background(channels.docPdf))
                }
                if (wordCount > 0) {
                    Box(modifier = Modifier.weight(wordCount / total).fillMaxHeight().background(channels.docWord))
                }
                if (excelCount > 0) {
                    Box(modifier = Modifier.weight(excelCount / total).fillMaxHeight().background(channels.docExcel))
                }
                if (imgCount > 0) {
                    Box(modifier = Modifier.weight(imgCount / total).fillMaxHeight().background(channels.docImage))
                }
                if (scanCount > 0) {
                    Box(modifier = Modifier.weight(scanCount / total).fillMaxHeight().background(channels.docScanner))
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ChannelCountDot("PDF", pdfCount, channels.docPdf)
                ChannelCountDot("Word", wordCount, channels.docWord)
                ChannelCountDot("Excel", excelCount, channels.docExcel)
                ChannelCountDot("Img", imgCount, channels.docImage)
                ChannelCountDot("Scan", scanCount, channels.docScanner)
            }
        }
    }
}

@Composable
private fun ChannelCountDot(label: String, count: Int, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = "$label ($count)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DocumentGridCard(
    document: DocumentEntity,
    isSelected: Boolean,
    engine: DocumentEngine,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var thumb by remember(document.id, document.filePath) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(document.id, document.filePath) {
        thumb = engine.renderPdfPage(document.filePath, 0, targetWidthPx = 320)
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("grid_doc_${document.id}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.82f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentAlignment = Alignment.Center
            ) {
                if (thumb != null) {
                    Image(
                        bitmap = thumb!!.asImageBitmap(),
                        contentDescription = document.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    DocumentPreviewIcon(format = document.format, size = 44.dp)
                }
                FormatBadge(
                    format = document.format,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                )
                if (document.isFavorite) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Favorite",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(18.dp)
                    )
                }
            }
            Text(
                text = document.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${document.pageCount} pg • ${formatFileSize(document.sizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
