package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StickyNote2
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.R
import com.example.data.engine.DocumentEngine
import com.example.data.local.DocumentEntity
import com.example.data.model.AnnotationColorOption
import com.example.data.model.AnnotationTool
import com.example.data.model.DocumentFormat
import com.example.data.model.NormalizedPoint
import com.example.data.model.PageAnnotation
import com.example.ui.components.StudyLeafWindowSize
import kotlin.math.roundToInt

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
    onConvertToExcel: () -> Unit,
    onShare: () -> Unit
) {
    BackHandler(onBack = onBack)

    var currentPage by remember(document.id) { mutableIntStateOf(0) }
    var totalPages by remember(document.id) { mutableIntStateOf(document.pageCount.coerceAtLeast(1)) }
    var pageBitmap by remember(document.id, currentPage) { mutableStateOf<Bitmap?>(null) }
    var isLoadingPage by remember(document.id, currentPage) { mutableStateOf(true) }

    var chromeVisible by remember { mutableStateOf(true) }
    var toolsMenuExpanded by remember { mutableStateOf(false) }
    var showThumbnailRail by remember(document.id) { mutableStateOf(false) }

    var activeTool by remember { mutableStateOf(AnnotationTool.PAN) }
    var activeColor by remember { mutableStateOf(AnnotationColorOption.YELLOW) }

    var zoomScale by remember(currentPage) { mutableFloatStateOf(1f) }
    var panOffset by remember(currentPage) { mutableStateOf(Offset.Zero) }

    val draftPoints = remember { mutableStateListOf<NormalizedPoint>() }
    var pendingNotePosition by remember { mutableStateOf<NormalizedPoint?>(null) }
    var noteInputText by remember { mutableStateOf("") }
    var inspectingNote by remember { mutableStateOf<PageAnnotation?>(null) }

    val pageAnnotations = remember(annotations, currentPage) {
        annotations.filter { it.pageIndex == currentPage }
    }

    LaunchedEffect(document.id, document.filePath) {
        if (document.format == DocumentFormat.PDF || document.format == DocumentFormat.SCAN) {
            totalPages = engine.getPdfPageCount(document.filePath).coerceAtLeast(1)
        } else {
            totalPages = 1
        }
    }

    LaunchedEffect(document.id, document.filePath, currentPage) {
        isLoadingPage = true
        pageBitmap = engine.renderPdfPage(document.filePath, currentPage, targetWidthPx = 1440)
        isLoadingPage = false
    }

    // Add Sticky Note dialog
    if (pendingNotePosition != null) {
        AlertDialog(
            onDismissRequest = {
                pendingNotePosition = null
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
                        val pos = pendingNotePosition
                        if (pos != null && noteInputText.isNotBlank()) {
                            onAddAnnotation(
                                PageAnnotation(
                                    documentId = document.id,
                                    pageIndex = currentPage,
                                    tool = AnnotationTool.NOTE,
                                    colorHex = activeColor.hex,
                                    points = listOf(pos),
                                    noteText = noteInputText.trim()
                                )
                            )
                        }
                        pendingNotePosition = null
                        noteInputText = ""
                    },
                    modifier = Modifier.testTag("save_sticky_note_button")
                ) {
                    Text("Pin Note")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingNotePosition = null
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = showThumbnailRail && totalPages > 1) {
                ThumbnailSideRail(
                    filePath = document.filePath,
                    totalPages = totalPages,
                    currentPage = currentPage,
                    engine = engine,
                    onSelectPage = { currentPage = it }
                )
            }

            // Fullscreen Document Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds()
                    .pointerInput(activeTool) {
                        if (activeTool == AnnotationTool.PAN) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (zoomScale * zoom).coerceIn(1f, 3.5f)
                                zoomScale = newScale
                                panOffset = if (newScale <= 1.01f) {
                                    Offset.Zero
                                } else {
                                    Offset(
                                        (panOffset.x + pan.x).coerceIn(-700f, 700f),
                                        (panOffset.y + pan.y).coerceIn(-900f, 900f)
                                    )
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isLoadingPage) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                } else if (pageBitmap != null) {
                    val bmp = pageBitmap!!
                    val aspect = (bmp.width.toFloat() / bmp.height.toFloat()).coerceIn(0.45f, 1.8f)
                    var sheetSize by remember { mutableStateOf(IntSize.Zero) }

                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 2.dp, vertical = 2.dp)
                            .aspectRatio(aspect, matchHeightConstraintsFirst = false)
                            .graphicsLayer(
                                scaleX = zoomScale,
                                scaleY = zoomScale,
                                translationX = panOffset.x,
                                translationY = panOffset.y
                            )
                            .onSizeChanged { sheetSize = it }
                            .pointerInput(activeTool, activeColor, currentPage, sheetSize) {
                                if (activeTool == AnnotationTool.PAN) {
                                    detectTapGestures(
                                        onTap = {
                                            if (toolsMenuExpanded) {
                                                toolsMenuExpanded = false
                                            } else {
                                                chromeVisible = !chromeVisible
                                            }
                                        },
                                        onDoubleTap = {
                                            if (zoomScale > 1.1f) {
                                                zoomScale = 1f
                                                panOffset = Offset.Zero
                                            } else {
                                                zoomScale = 2f
                                            }
                                        }
                                    )
                                } else if (activeTool == AnnotationTool.NOTE) {
                                    detectTapGestures { offset ->
                                        if (sheetSize.width > 0 && sheetSize.height > 0) {
                                            pendingNotePosition = NormalizedPoint(
                                                (offset.x / sheetSize.width).coerceIn(0.05f, 0.95f),
                                                (offset.y / sheetSize.height).coerceIn(0.05f, 0.95f)
                                            )
                                        }
                                    }
                                } else {
                                    detectDragGestures(
                                        onDragStart = { start ->
                                            draftPoints.clear()
                                            if (sheetSize.width > 0 && sheetSize.height > 0) {
                                                draftPoints.add(
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
                                                draftPoints.add(
                                                    NormalizedPoint(
                                                        (p.x / sheetSize.width).coerceIn(0f, 1f),
                                                        (p.y / sheetSize.height).coerceIn(0f, 1f)
                                                    )
                                                )
                                            }
                                        },
                                        onDragEnd = {
                                            if (draftPoints.size >= 2) {
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
                                                onAddAnnotation(
                                                    PageAnnotation(
                                                        documentId = document.id,
                                                        pageIndex = currentPage,
                                                        tool = activeTool,
                                                        colorHex = activeColor.hex,
                                                        points = committed
                                                    )
                                                )
                                            }
                                            draftPoints.clear()
                                        },
                                        onDragCancel = { draftPoints.clear() }
                                    )
                                }
                            },
                        shape = RoundedCornerShape(4.dp),
                        color = Color.White,
                        shadowElevation = 2.dp
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "${document.title} page ${currentPage + 1}",
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier.fillMaxSize()
                            )

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val w = size.width
                                val h = size.height

                                fun drawStrokeAnnotation(tool: AnnotationTool, color: Color, pts: List<NormalizedPoint>) {
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
                                            onClick = { inspectingNote = note },
                                            modifier = Modifier
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
                    }
                } else {
                    Text(
                        text = "Unable to render document page.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
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

                            // Convert PDF/Word to Excel
                            if (document.format != DocumentFormat.IMAGE) {
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

                            if (pageAnnotations.isNotEmpty()) {
                                VerticalDivider(
                                    modifier = Modifier
                                        .height(22.dp)
                                        .padding(horizontal = 3.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                                CompactReaderToolButton(
                                    icon = Icons.AutoMirrored.Filled.Undo,
                                    label = "Undo",
                                    selected = false,
                                    onClick = { pageAnnotations.lastOrNull()?.let { onDeleteAnnotation(it.id) } },
                                    tag = "dock_undo_button"
                                )
                                CompactReaderToolButton(
                                    icon = Icons.Filled.DeleteSweep,
                                    label = "Clear",
                                    selected = false,
                                    tintOverride = MaterialTheme.colorScheme.error,
                                    onClick = { onClearPageAnnotations(currentPage) },
                                    tag = "dock_clear_page_button"
                                )
                            }
                        }
                    }
                }
            }
        }

        // Minimal Bottom Page Switcher Pill (only for multi-page docs when chrome visible)
        AnimatedVisibility(
            visible = chromeVisible && totalPages > 1,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.86f),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = { if (currentPage > 0) currentPage-- },
                        enabled = currentPage > 0,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("reader_prev_page")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous page",
                            tint = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "${currentPage + 1} / $totalPages",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    IconButton(
                        onClick = { if (currentPage < totalPages - 1) currentPage++ },
                        enabled = currentPage < totalPages - 1,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("reader_next_page")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next page",
                            tint = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
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
    tintOverride: Color? = null
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(34.dp)
            .testTag(tag),
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tintOverride ?: if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
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
            contentPadding = PaddingValues(top = 56.dp, bottom = 64.dp, start = 10.dp, end = 10.dp),
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
