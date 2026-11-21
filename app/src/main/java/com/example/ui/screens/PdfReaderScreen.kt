package com.example.ui.screens

import android.graphics.Bitmap
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.engine.DocumentEngine
import com.example.data.local.DocumentEntity
import com.example.data.model.AnnotationColorOption
import com.example.data.model.AnnotationTool
import com.example.data.model.DocumentFormat
import com.example.data.model.NormalizedPoint
import com.example.data.model.PageAnnotation
import com.example.ui.components.StudyLeafWindowSize
import com.studyleaf.R
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun PdfReaderScreen(
    document: DocumentEntity,
    annotations: List<PageAnnotation>,
    windowSize: StudyLeafWindowSize,
    engine: DocumentEngine,
    onBack: () -> Unit,
    onAddAnnotation: (PageAnnotation) -> Unit,
    onDeleteAnnotation: (Long) -> Unit,
    onClearPageAnnotations: (Int) -> Unit,
    onOpenReorderBoard: () -> Unit,
    onOpenAiForPage: (Int) -> Unit,
    onConvertToPdf: () -> Unit,
    onConvertToWord: () -> Unit,
    onConvertToExcel: () -> Unit,
    onConvertToImages: () -> Unit,
    onOptimizePdf: () -> Unit,
    onMergeWithOthers: () -> Unit,
    onCompressImage: () -> Unit,
    onEditDocument: () -> Unit,
    onShare: () -> Unit
) {
    BackHandler(onBack = onBack)

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var totalPages by remember(document.id) { mutableIntStateOf(document.pageCount.coerceAtLeast(1)) }
    val pageCache = remember(document.id, document.filePath) { LruCache<Int, Bitmap>(8) }

    var chromeVisible by remember { mutableStateOf(true) }
    var toolsMenuExpanded by remember { mutableStateOf(false) }
    var showThumbnailRail by remember(document.id) { mutableStateOf(false) }
    var showConvertDialog by remember { mutableStateOf(false) }

    var activeTool by remember { mutableStateOf(AnnotationTool.PAN) }
    var activeColor by remember { mutableStateOf(AnnotationColorOption.YELLOW) }

    var zoomScale by remember(document.id) { mutableFloatStateOf(1f) }
    var panOffsetX by remember(document.id) { mutableFloatStateOf(0f) }

    var draftPageIndex by remember { mutableStateOf<Int?>(null) }
    val draftPoints = remember { mutableStateListOf<NormalizedPoint>() }
    val redoStack = remember(document.id) { mutableStateListOf<PageAnnotation>() }
    var pendingNoteTarget by remember { mutableStateOf<Pair<Int, NormalizedPoint>?>(null) }
    var noteInputText by remember { mutableStateOf("") }
    var inspectingNote by remember { mutableStateOf<PageAnnotation?>(null) }

    val addAnnotationAndResetRedo: (PageAnnotation) -> Unit = { annotation ->
        redoStack.clear()
        onAddAnnotation(annotation)
    }

    val currentPage by remember(totalPages) {
        derivedStateOf {
            val visible = listState.layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) {
                listState.firstVisibleItemIndex.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
            } else {
                val viewportCenter =
                    (listState.layoutInfo.viewportStartOffset + listState.layoutInfo.viewportEndOffset) / 2
                visible.minByOrNull { item ->
                    abs((item.offset + item.size / 2) - viewportCenter)
                }?.index?.coerceIn(0, (totalPages - 1).coerceAtLeast(0)) ?: 0
            }
        }
    }

    val annotationsByPage = remember(annotations) {
        annotations.groupBy { it.pageIndex }
    }
    val currentPageAnnotations = annotationsByPage[currentPage].orEmpty()

    LaunchedEffect(document.id, document.filePath) {
        if (document.format == DocumentFormat.PDF || document.format == DocumentFormat.SCAN) {
            totalPages = engine.getPdfPageCount(document.filePath).coerceAtLeast(1)
        } else {
            totalPages = 1
        }
    }

    // Add Sticky Note dialog
    if (pendingNoteTarget != null) {
        AlertDialog(
            onDismissRequest = {
                pendingNoteTarget = null
                noteInputText = ""
            },
            title = { Text(stringResource(R.string.reader_add_note_title)) },
            text = {
                OutlinedTextField(
                    value = noteInputText,
                    onValueChange = { noteInputText = it },
                    placeholder = { Text(stringResource(R.string.reader_note_hint)) },
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sticky_note_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = pendingNoteTarget
                        if (target != null && noteInputText.isNotBlank()) {
                            addAnnotationAndResetRedo(
                                PageAnnotation(
                                    documentId = document.id,
                                    pageIndex = target.first,
                                    tool = AnnotationTool.NOTE,
                                    colorHex = activeColor.hex,
                                    points = listOf(target.second),
                                    noteText = noteInputText.trim()
                                )
                            )
                        }
                        pendingNoteTarget = null
                        noteInputText = ""
                    },
                    modifier = Modifier.testTag("save_sticky_note_button")
                ) {
                    Text("Pin Note")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingNoteTarget = null
                    noteInputText = ""
                }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Inspect Sticky Note dialog
    inspectingNote?.let { note ->
        AlertDialog(
            onDismissRequest = { inspectingNote = null },
            title = { Text("Sticky Note • Page ${note.pageIndex + 1}") },
            text = {
                Text(
                    text = note.noteText,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { inspectingNote = null }) {
                    Text(stringResource(R.string.action_close))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onDeleteAnnotation(note.id)
                    inspectingNote = null
                }) {
                    Text("Remove Note", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    // Universal Convert to Any Format Dialog
    if (showConvertDialog) {
        ConvertOpenFileDialog(
            document = document,
            onDismiss = { showConvertDialog = false },
            onConvertToPdf = {
                showConvertDialog = false
                onConvertToPdf()
            },
            onConvertToWord = {
                showConvertDialog = false
                onConvertToWord()
            },
            onConvertToExcel = {
                showConvertDialog = false
                onConvertToExcel()
            },
            onConvertToImages = {
                showConvertDialog = false
                onConvertToImages()
            },
            onEditDocument = {
                showConvertDialog = false
                onEditDocument()
            },
            onOptimizePdf = {
                showConvertDialog = false
                onOptimizePdf()
            },
            onOpenReorderBoard = {
                showConvertDialog = false
                onOpenReorderBoard()
            },
            onMergeWithOthers = {
                showConvertDialog = false
                onMergeWithOthers()
            },
            onCompressImage = {
                showConvertDialog = false
                onCompressImage()
            }
        )
    }

    val topContentPadding by animateDpAsState(
        targetValue = if (chromeVisible) 52.dp else 10.dp,
        label = "reader_top_padding"
    )
    val horizontalPagePadding = if (windowSize == StudyLeafWindowSize.COMPACT) 10.dp else 20.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = showThumbnailRail && totalPages > 1) {
                ThumbnailSideRail(
                    filePath = document.filePath,
                    totalPages = totalPages,
                    currentPage = currentPage,
                    engine = engine,
                    onSelectPage = { pageIdx ->
                        scope.launch {
                            listState.animateScrollToItem(pageIdx)
                        }
                    }
                )
            }

            // Continuous Vertical Scrollable Multi-Page Document Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds()
                    .pointerInput(activeTool) {
                        if (activeTool == AnnotationTool.PAN) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    val pressedCount = event.changes.count { it.pressed }
                                    if (pressedCount >= 2) {
                                        val zoom = event.calculateZoom()
                                        val pan = event.calculatePan()
                                        if (zoom != 1f || pan != Offset.Zero) {
                                            val nextScale = (zoomScale * zoom).coerceIn(1f, 3.5f)
                                            zoomScale = nextScale
                                            panOffsetX = if (nextScale <= 1.02f) {
                                                0f
                                            } else {
                                                (panOffsetX + pan.x).coerceIn(-650f, 650f)
                                            }
                                            event.changes.forEach { it.consume() }
                                        }
                                    } else if (pressedCount == 1 && zoomScale > 1.02f) {
                                        val pan = event.calculatePan()
                                        if (abs(pan.x) > abs(pan.y) * 0.8f) {
                                            panOffsetX = (panOffsetX + pan.x).coerceIn(-650f, 650f)
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                    }
            ) {
                LazyColumn(
                    state = listState,
                    userScrollEnabled = activeTool == AnnotationTool.PAN,
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .graphicsLayer(
                            scaleX = zoomScale,
                            scaleY = zoomScale,
                            translationX = panOffsetX
                        )
                        .testTag("reader_pages_list"),
                    contentPadding = PaddingValues(
                        top = topContentPadding,
                        bottom = 24.dp,
                        start = horizontalPagePadding,
                        end = horizontalPagePadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(
                        items = (0 until totalPages).toList(),
                        key = { pageIdx -> "${document.id}_$pageIdx" }
                    ) { pageIdx ->
                        PdfScrollPageCard(
                            document = document,
                            pageIndex = pageIdx,
                            engine = engine,
                            pageCache = pageCache,
                            pageAnnotations = annotationsByPage[pageIdx].orEmpty(),
                            activeTool = activeTool,
                            activeColor = activeColor,
                            draftPoints = if (draftPageIndex == pageIdx) draftPoints else emptyList(),
                            onTapPage = {
                                if (toolsMenuExpanded) {
                                    toolsMenuExpanded = false
                                } else {
                                    chromeVisible = !chromeVisible
                                }
                            },
                            onDoubleTapPage = {
                                if (zoomScale > 1.1f) {
                                    zoomScale = 1f
                                    panOffsetX = 0f
                                } else {
                                    zoomScale = 2f
                                }
                            },
                            onRequestAddNote = { point ->
                                pendingNoteTarget = pageIdx to point
                            },
                            onInspectNote = { note ->
                                inspectingNote = note
                            },
                            onDraftStart = { startPt ->
                                draftPageIndex = pageIdx
                                draftPoints.clear()
                                draftPoints.add(startPt)
                            },
                            onDraftMove = { pt ->
                                if (draftPageIndex == pageIdx) {
                                    draftPoints.add(pt)
                                }
                            },
                            onDraftEnd = {
                                if (draftPageIndex == pageIdx && draftPoints.size >= 2) {
                                    val committed = when (activeTool) {
                                        AnnotationTool.HIGHLIGHT,
                                        AnnotationTool.UNDERLINE,
                                        AnnotationTool.STRIKETHROUGH -> {
                                            val first = draftPoints.first()
                                            val last = draftPoints.last()
                                            listOf(first, NormalizedPoint(last.x, first.y))
                                        }
                                        else -> draftPoints.toList()
                                    }
                                    addAnnotationAndResetRedo(
                                        PageAnnotation(
                                            documentId = document.id,
                                            pageIndex = pageIdx,
                                            tool = activeTool,
                                            colorHex = activeColor.hex,
                                            points = committed
                                        )
                                    )
                                }
                                draftPoints.clear()
                                draftPageIndex = null
                            },
                            onDraftCancel = {
                                draftPoints.clear()
                                draftPageIndex = null
                            }
                        )
                    }
                }
            }
        }

        // Compact Translucent Floating Top Bar + Horizontal Hamburger Tools Strip
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
                    tonalElevation = 2.dp,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("reader_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = document.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    append(document.format.label)
                                    if (totalPages > 1) append(" • ${currentPage + 1}/$totalPages")
                                    if (activeTool != AnnotationTool.PAN) append(" • ${activeTool.label}")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (activeTool != AnnotationTool.PAN) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }

                        // Compact active-tool reset chip when drawing/annotating
                        if (activeTool != AnnotationTool.PAN) {
                            Surface(
                                onClick = { activeTool = AnnotationTool.PAN },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .testTag("reader_exit_draw_mode")
                            ) {
                                Text(
                                    text = "Done",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        val isEditableTextDoc = document.format == DocumentFormat.PDF ||
                            document.format == DocumentFormat.SCAN ||
                            document.format == DocumentFormat.WORD

                        // Universal Convert to Any Format button (always accessible on any open file)
                        IconButton(
                            onClick = {
                                toolsMenuExpanded = false
                                showConvertDialog = true
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("reader_convert_top_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SwapHoriz,
                                contentDescription = stringResource(R.string.cd_convert_any),
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        if (isEditableTextDoc) {
                            IconButton(
                                onClick = {
                                    toolsMenuExpanded = false
                                    onEditDocument()
                                },
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("reader_edit_text_top_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.EditNote,
                                    contentDescription = stringResource(R.string.cd_edit_document),
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        // Share icon (compact)
                        IconButton(
                            onClick = onShare,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("reader_share_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = stringResource(R.string.cd_share_document),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Horizontal Hamburger Menu right next to Share icon
                        IconButton(
                            onClick = { toolsMenuExpanded = !toolsMenuExpanded },
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("reader_tools_hamburger")
                        ) {
                            Icon(
                                imageVector = if (toolsMenuExpanded) Icons.Filled.Close else Icons.Filled.Menu,
                                contentDescription = stringResource(R.string.cd_reader_tools_menu),
                                tint = if (toolsMenuExpanded) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Horizontal Scrollable Tools Strip triggered by the top-right Hamburger Menu
                AnimatedVisibility(
                    visible = toolsMenuExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                        tonalElevation = 4.dp,
                        shadowElevation = 4.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                    ) {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            // Edit PDF / Word text
                            if (document.format == DocumentFormat.PDF ||
                                document.format == DocumentFormat.SCAN ||
                                document.format == DocumentFormat.WORD
                            ) {
                                CompactReaderToolButton(
                                    icon = Icons.Filled.EditNote,
                                    label = "Edit Text",
                                    selected = false,
                                    tintOverride = MaterialTheme.colorScheme.primary,
                                    onClick = {
                                        toolsMenuExpanded = false
                                        onEditDocument()
                                    },
                                    tag = "reader_edit_text_button"
                                )
                            }

                            // Gemini AI
                            CompactReaderToolButton(
                                icon = Icons.Filled.AutoAwesome,
                                label = "Ask AI",
                                selected = false,
                                tintOverride = MaterialTheme.colorScheme.secondary,
                                onClick = {
                                    toolsMenuExpanded = false
                                    onOpenAiForPage(currentPage)
                                },
                                tag = "reader_ai_companion_button"
                            )

                            // Thumbnail Rail toggle (multi-page)
                            if (totalPages > 1) {
                                CompactReaderToolButton(
                                    icon = Icons.Filled.ViewSidebar,
                                    label = "Pages",
                                    selected = showThumbnailRail,
                                    onClick = { showThumbnailRail = !showThumbnailRail },
                                    tag = "reader_toggle_thumbnails"
                                )
                            }

                            // Reorder / Split pages (PDF/SCAN)
                            if (document.format == DocumentFormat.PDF || document.format == DocumentFormat.SCAN) {
                                CompactReaderToolButton(
                                    icon = Icons.Filled.ViewAgenda,
                                    label = "Reorder",
                                    selected = false,
                                    onClick = {
                                        toolsMenuExpanded = false
                                        onOpenReorderBoard()
                                    },
                                    tag = "reader_reorder_pages"
                                )
                            }

                            // Universal Convert to Any Format
                            CompactReaderToolButton(
                                icon = Icons.Filled.SwapHoriz,
                                label = "Convert",
                                selected = showConvertDialog,
                                tintOverride = MaterialTheme.colorScheme.secondary,
                                onClick = {
                                    toolsMenuExpanded = false
                                    showConvertDialog = true
                                },
                                tag = "reader_convert_menu_button"
                            )

                            // Convert PDF/Word to Excel shortcut
                            if (document.format != DocumentFormat.IMAGE && document.format != DocumentFormat.EXCEL) {
                                CompactReaderToolButton(
                                    icon = Icons.Filled.TableChart,
                                    label = "Excel",
                                    selected = false,
                                    onClick = {
                                        toolsMenuExpanded = false
                                        onConvertToExcel()
                                    },
                                    tag = "reader_convert_excel"
                                )
                            }

                            VerticalDivider(
                                modifier = Modifier
                                    .height(22.dp)
                                    .padding(horizontal = 3.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )

                            // Annotation Tools
                            CompactReaderToolButton(
                                icon = Icons.Filled.PanTool,
                                label = "Pan",
                                selected = activeTool == AnnotationTool.PAN,
                                onClick = { activeTool = AnnotationTool.PAN },
                                tag = "dock_tool_pan"
                            )
                            CompactReaderToolButton(
                                icon = Icons.Filled.BorderColor,
                                label = "Highlight",
                                selected = activeTool == AnnotationTool.HIGHLIGHT,
                                onClick = { activeTool = AnnotationTool.HIGHLIGHT },
                                tag = "dock_tool_highlight"
                            )
                            CompactReaderToolButton(
                                icon = Icons.Filled.FormatUnderlined,
                                label = "Underline",
                                selected = activeTool == AnnotationTool.UNDERLINE,
                                onClick = { activeTool = AnnotationTool.UNDERLINE },
                                tag = "dock_tool_underline"
                            )
                            CompactReaderToolButton(
                                icon = Icons.Filled.FormatStrikethrough,
                                label = "Strike",
                                selected = activeTool == AnnotationTool.STRIKETHROUGH,
                                onClick = { activeTool = AnnotationTool.STRIKETHROUGH },
                                tag = "dock_tool_strike"
                            )
                            CompactReaderToolButton(
                                icon = Icons.Filled.Draw,
                                label = "Pen",
                                selected = activeTool == AnnotationTool.PEN,
                                onClick = { activeTool = AnnotationTool.PEN },
                                tag = "dock_tool_pen"
                            )
                            CompactReaderToolButton(
                                icon = Icons.Filled.StickyNote2,
                                label = "Note",
                                selected = activeTool == AnnotationTool.NOTE,
                                onClick = { activeTool = AnnotationTool.NOTE },
                                tag = "dock_tool_note"
                            )

                            VerticalDivider(
                                modifier = Modifier
                                    .height(22.dp)
                                    .padding(horizontal = 3.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )

                            // 4 Annotation Color Swatches
                            AnnotationColorOption.entries.forEach { swatch ->
                                val isChosen = activeColor == swatch
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .clickable { activeColor = swatch }
                                        .testTag("swatch_${swatch.name.lowercase()}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(if (isChosen) 19.dp else 15.dp)
                                            .clip(CircleShape)
                                            .background(swatch.color)
                                            .border(
                                                width = if (isChosen) 2.dp else 1.dp,
                                                color = if (isChosen) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.2f),
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }

                            VerticalDivider(
                                modifier = Modifier
                                    .height(22.dp)
                                    .padding(horizontal = 3.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )

                            val undoTarget = currentPageAnnotations.lastOrNull() ?: annotations.lastOrNull()
                            CompactReaderToolButton(
                                icon = Icons.AutoMirrored.Filled.Undo,
                                label = "Undo",
                                selected = false,
                                enabled = undoTarget != null,
                                onClick = {
                                    undoTarget?.let { target ->
                                        redoStack.add(target.copy(id = 0L))
                                        onDeleteAnnotation(target.id)
                                    }
                                },
                                tag = "dock_undo_button"
                            )
                            CompactReaderToolButton(
                                icon = Icons.AutoMirrored.Filled.Redo,
                                label = "Redo",
                                selected = false,
                                enabled = redoStack.isNotEmpty(),
                                onClick = {
                                    if (redoStack.isNotEmpty()) {
                                        val restored = redoStack.removeAt(redoStack.lastIndex)
                                        onAddAnnotation(restored.copy(id = 0L))
                                    }
                                },
                                tag = "dock_redo_button"
                            )

                            if (currentPageAnnotations.isNotEmpty()) {
                                CompactReaderToolButton(
                                    icon = Icons.Filled.DeleteSweep,
                                    label = "Clear",
                                    selected = false,
                                    tintOverride = MaterialTheme.colorScheme.error,
                                    onClick = {
                                        redoStack.removeAll { it.pageIndex == currentPage }
                                        onClearPageAnnotations(currentPage)
                                    },
                                    tag = "dock_clear_page_button"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfScrollPageCard(
    document: DocumentEntity,
    pageIndex: Int,
    engine: DocumentEngine,
    pageCache: LruCache<Int, Bitmap>,
    pageAnnotations: List<PageAnnotation>,
    activeTool: AnnotationTool,
    activeColor: AnnotationColorOption,
    draftPoints: List<NormalizedPoint>,
    onTapPage: () -> Unit,
    onDoubleTapPage: () -> Unit,
    onRequestAddNote: (NormalizedPoint) -> Unit,
    onInspectNote: (PageAnnotation) -> Unit,
    onDraftStart: (NormalizedPoint) -> Unit,
    onDraftMove: (NormalizedPoint) -> Unit,
    onDraftEnd: () -> Unit,
    onDraftCancel: () -> Unit
) {
    var bitmap by remember(document.id, document.filePath, pageIndex) {
        mutableStateOf(pageCache.get(pageIndex))
    }
    var isLoading by remember(document.id, document.filePath, pageIndex) {
        mutableStateOf(bitmap == null)
    }
    var sheetSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(document.id, document.filePath, pageIndex) {
        val cached = pageCache.get(pageIndex)
        if (cached != null) {
            bitmap = cached
            isLoading = false
        } else {
            isLoading = true
            val rendered = engine.renderPdfPage(document.filePath, pageIndex, targetWidthPx = 1440)
            if (rendered != null) {
                pageCache.put(pageIndex, rendered)
            }
            bitmap = rendered
            isLoading = false
        }
    }

    val aspect = remember(bitmap) {
        bitmap?.let { (it.width.toFloat() / it.height.toFloat()).coerceIn(0.45f, 1.8f) } ?: 0.707f
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 860.dp)
            .aspectRatio(aspect)
            .onSizeChanged { sheetSize = it }
            .pointerInput(activeTool, activeColor, pageIndex, sheetSize) {
                if (activeTool == AnnotationTool.PAN) {
                    detectTapGestures(
                        onTap = { onTapPage() },
                        onDoubleTap = { onDoubleTapPage() }
                    )
                } else if (activeTool == AnnotationTool.NOTE) {
                    detectTapGestures { offset ->
                        if (sheetSize.width > 0 && sheetSize.height > 0) {
                            onRequestAddNote(
                                NormalizedPoint(
                                    (offset.x / sheetSize.width).coerceIn(0.05f, 0.95f),
                                    (offset.y / sheetSize.height).coerceIn(0.05f, 0.95f)
                                )
                            )
                        }
                    }
                } else {
                    detectDragGestures(
                        onDragStart = { start ->
                            if (sheetSize.width > 0 && sheetSize.height > 0) {
                                onDraftStart(
                                    NormalizedPoint(
                                        (start.x / sheetSize.width).coerceIn(0f, 1f),
                                        (start.y / sheetSize.height).coerceIn(0f, 1f)
                                    )
                                )
                            }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (sheetSize.width > 0 && sheetSize.height > 0) {
                                val p = change.position
                                onDraftMove(
                                    NormalizedPoint(
                                        (p.x / sheetSize.width).coerceIn(0f, 1f),
                                        (p.y / sheetSize.height).coerceIn(0f, 1f)
                                    )
                                )
                            }
                        },
                        onDragEnd = onDraftEnd,
                        onDragCancel = onDraftCancel
                    )
                }
            }
            .testTag("reader_page_card_$pageIndex"),
        shape = RoundedCornerShape(2.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.5.dp
                    )
                }
                bitmap != null -> {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "${document.title} page ${pageIndex + 1}",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        fun drawStrokeAnnotation(
                            tool: AnnotationTool,
                            color: Color,
                            pts: List<NormalizedPoint>
                        ) {
                            if (pts.size < 2) return
                            when (tool) {
                                AnnotationTool.HIGHLIGHT -> {
                                    drawLine(
                                        color = color.copy(alpha = 0.42f),
                                        start = Offset(pts.first().x * w, pts.first().y * h),
                                        end = Offset(pts.last().x * w, pts.first().y * h),
                                        strokeWidth = 24f,
                                        cap = StrokeCap.Round
                                    )
                                }
                                AnnotationTool.UNDERLINE -> {
                                    drawLine(
                                        color = color,
                                        start = Offset(pts.first().x * w, pts.first().y * h),
                                        end = Offset(pts.last().x * w, pts.first().y * h),
                                        strokeWidth = 5f,
                                        cap = StrokeCap.Round
                                    )
                                }
                                AnnotationTool.STRIKETHROUGH -> {
                                    drawLine(
                                        color = color,
                                        start = Offset(pts.first().x * w, pts.first().y * h),
                                        end = Offset(pts.last().x * w, pts.first().y * h),
                                        strokeWidth = 5f,
                                        cap = StrokeCap.Butt
                                    )
                                }
                                AnnotationTool.PEN -> {
                                    val path = Path().apply {
                                        moveTo(pts.first().x * w, pts.first().y * h)
                                        for (i in 1 until pts.size) {
                                            lineTo(pts[i].x * w, pts[i].y * h)
                                        }
                                    }
                                    drawPath(
                                        path = path,
                                        color = color,
                                        style = Stroke(
                                            width = 6f,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                                else -> Unit
                            }
                        }

                        pageAnnotations.forEach { ann ->
                            if (ann.tool != AnnotationTool.NOTE) {
                                drawStrokeAnnotation(ann.tool, Color(ann.colorHex), ann.points)
                            }
                        }
                        if (draftPoints.size >= 2) {
                            drawStrokeAnnotation(activeTool, activeColor.color, draftPoints)
                        }
                    }

                    if (sheetSize.width > 0 && sheetSize.height > 0) {
                        pageAnnotations.filter { it.tool == AnnotationTool.NOTE && it.points.isNotEmpty() }
                            .forEach { note ->
                                val pt = note.points.first()
                                val px = (pt.x * sheetSize.width).roundToInt() - 24
                                val py = (pt.y * sheetSize.height).roundToInt() - 24
                                Surface(
                                    onClick = { onInspectNote(note) },
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .offset { IntOffset(px.coerceAtLeast(4), py.coerceAtLeast(4)) }
                                        .size(30.dp)
                                        .testTag("sticky_note_pin_${note.id}"),
                                    shape = CircleShape,
                                    color = Color(note.colorHex),
                                    shadowElevation = 3.dp,
                                    border = BorderStroke(1.5.dp, Color.White)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Filled.StickyNote2,
                                            contentDescription = "Open sticky note",
                                            tint = Color(0xFF012D1D),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                    }
                }
                else -> {
                    Text(
                        text = "Unable to render page ${pageIndex + 1}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactReaderToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String,
    enabled: Boolean = true,
    tintOverride: Color? = null
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(34.dp)
            .testTag(tag),
        shape = CircleShape,
        color = if (selected && enabled) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            val baseTint = tintOverride ?: if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) baseTint else baseTint.copy(alpha = 0.35f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ThumbnailSideRail(
    filePath: String,
    totalPages: Int,
    currentPage: Int,
    engine: DocumentEngine,
    onSelectPage: (Int) -> Unit
) {
    Surface(
        modifier = Modifier
            .width(108.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(top = 56.dp, bottom = 24.dp, start = 10.dp, end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items((0 until totalPages).toList(), key = { it }) { pageIdx ->
                var thumb by remember(filePath, pageIdx) { mutableStateOf<Bitmap?>(null) }
                LaunchedEffect(filePath, pageIdx) {
                    thumb = engine.renderPdfPage(filePath, pageIdx, targetWidthPx = 220)
                }
                val selected = pageIdx == currentPage
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelectPage(pageIdx) }
                        .padding(3.dp)
                        .testTag("thumbnail_page_$pageIdx")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.72f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(6.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (thumb != null) {
                            Image(
                                bitmap = thumb!!.asImageBitmap(),
                                contentDescription = "Page ${pageIdx + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = "${pageIdx + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = "Page ${pageIdx + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ConvertOpenFileDialog(
    document: DocumentEntity,
    onDismiss: () -> Unit,
    onConvertToPdf: () -> Unit,
    onConvertToWord: () -> Unit,
    onConvertToExcel: () -> Unit,
    onConvertToImages: () -> Unit,
    onEditDocument: () -> Unit,
    onOptimizePdf: () -> Unit,
    onOpenReorderBoard: () -> Unit,
    onMergeWithOthers: () -> Unit,
    onCompressImage: () -> Unit
) {
    val isPdfLike = document.format == DocumentFormat.PDF || document.format == DocumentFormat.SCAN

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.convert_dialog_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "${document.title} (${document.format.label})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (document.format == DocumentFormat.WORD ||
                    document.format == DocumentFormat.EXCEL ||
                    document.format == DocumentFormat.IMAGE
                ) {
                    ConvertActionRow(
                        icon = Icons.Filled.PictureAsPdf,
                        title = "Convert to PDF",
                        subtitle = "Generate a clean, printable PDF document",
                        onClick = onConvertToPdf,
                        tag = "convert_action_to_pdf"
                    )
                }

                if (isPdfLike || document.format == DocumentFormat.EXCEL) {
                    ConvertActionRow(
                        icon = Icons.Filled.Description,
                        title = "Convert to Word (.docx)",
                        subtitle = "Extract text into an editable Word document",
                        onClick = onConvertToWord,
                        tag = "convert_action_to_word"
                    )
                }

                if (isPdfLike || document.format == DocumentFormat.WORD) {
                    ConvertActionRow(
                        icon = Icons.Filled.TableChart,
                        title = "Convert to Excel (.xlsx)",
                        subtitle = "Structure lines and tables into a spreadsheet",
                        onClick = onConvertToExcel,
                        tag = "convert_action_to_excel"
                    )
                }

                if (isPdfLike) {
                    ConvertActionRow(
                        icon = Icons.Filled.Image,
                        title = "Export Pages to PNG Images",
                        subtitle = "Save each page as a crisp image in Vault",
                        onClick = onConvertToImages,
                        tag = "convert_action_to_images"
                    )
                }

                if (isPdfLike || document.format == DocumentFormat.WORD) {
                    ConvertActionRow(
                        icon = Icons.Filled.EditNote,
                        title = "Edit Text & Words",
                        subtitle = "Modify text and save as PDF or Word (.docx)",
                        onClick = onEditDocument,
                        tag = "convert_action_edit_text"
                    )
                }

                if (isPdfLike) {
                    ConvertActionRow(
                        icon = Icons.Filled.Compress,
                        title = "Optimize / Shrink PDF",
                        subtitle = "Reduce file size with quality presets",
                        onClick = onOptimizePdf,
                        tag = "convert_action_optimize_pdf"
                    )
                    ConvertActionRow(
                        icon = Icons.Filled.ViewAgenda,
                        title = "Split & Reorder Pages",
                        subtitle = "Rearrange or extract selected pages",
                        onClick = onOpenReorderBoard,
                        tag = "convert_action_reorder_pages"
                    )
                }

                if (document.format == DocumentFormat.IMAGE) {
                    ConvertActionRow(
                        icon = Icons.Filled.PhotoSizeSelectLarge,
                        title = "Compress Image",
                        subtitle = "Shrink resolution and JPEG quality offline",
                        onClick = onCompressImage,
                        tag = "convert_action_compress_image"
                    )
                }

                ConvertActionRow(
                    icon = Icons.AutoMirrored.Filled.CallMerge,
                    title = "Merge with Other Files",
                    subtitle = "Combine into one multi-page PDF packet",
                    onClick = onMergeWithOthers,
                    tag = "convert_action_merge"
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("convert_dialog_close_button")
            ) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

@Composable
private fun ConvertActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
