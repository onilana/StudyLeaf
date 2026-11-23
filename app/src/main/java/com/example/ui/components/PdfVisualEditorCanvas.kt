package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Approval
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.engine.DocumentEngine
import com.example.data.engine.PdfEditableTextLine
import com.example.data.engine.PdfRedactionOverlay
import com.example.data.engine.PdfSignatureOverlay
import com.example.data.engine.PdfTextOverlayEdit
import com.example.data.local.DocumentEntity
import com.example.data.model.NormalizedPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class PdfVisualTool(val label: String) {
    TEXT("Text"),
    SIGN("Sign"),
    REDACT("Redact"),
    STAMP("Form Stamp")
}

sealed interface SelectedPdfOverlay {
    data class TextBox(val id: Long) : SelectedPdfOverlay
    data class Signature(val id: Long) : SelectedPdfOverlay
    data class Redaction(val id: Long) : SelectedPdfOverlay
}

private val OVERLAY_INK_COLORS = listOf(
    AndroidColor.parseColor("#1A1C1E") to "Black",
    AndroidColor.parseColor("#006C48") to "Forest",
    AndroidColor.parseColor("#1D4ED8") to "Navy",
    AndroidColor.parseColor("#B91C1C") to "Crimson"
)

@Composable
fun PdfVisualEditorCanvas(
    document: DocumentEntity,
    pageCount: Int,
    engine: DocumentEngine,
    detectedLines: List<PdfEditableTextLine>,
    edits: List<PdfTextOverlayEdit>,
    signatures: List<PdfSignatureOverlay>,
    redactions: List<PdfRedactionOverlay>,
    selectedOverlay: SelectedPdfOverlay?,
    onSelectOverlay: (SelectedPdfOverlay?) -> Unit,
    onEditExistingLine: (line: PdfEditableTextLine, coverColorArgb: Int, inkColorArgb: Int) -> Unit,
    onAddTextAt: (pageIndex: Int, xFraction: Float, yFraction: Float, presetText: String, cover: Boolean, isBold: Boolean, colorArgb: Int, widthFraction: Float) -> Unit,
    onUpdateTextEdit: (PdfTextOverlayEdit) -> Unit,
    onDeleteTextEdit: (Long) -> Unit,
    onAddSignatureAt: (pageIndex: Int, xFraction: Float, yFraction: Float, strokes: List<List<NormalizedPoint>>, colorArgb: Int) -> Unit,
    onUpdateSignature: (PdfSignatureOverlay) -> Unit,
    onDeleteSignature: (Long) -> Unit,
    onAddRedactionAt: (pageIndex: Int, xFraction: Float, yFraction: Float) -> Unit,
    onUpdateRedaction: (PdfRedactionOverlay) -> Unit,
    onDeleteRedaction: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val safePageCount = pageCount.coerceAtLeast(1)
    var currentPage by remember(document.id) { mutableIntStateOf(0) }
    var activeTool by remember(document.id) { mutableStateOf(PdfVisualTool.TEXT) }
    var showEditableHighlights by remember(document.id) { mutableStateOf(true) }
    var pageBitmap by remember(document.id, currentPage) { mutableStateOf<Bitmap?>(null) }
    var isRendering by remember(document.id, currentPage) { mutableStateOf(true) }
    val scrollState = rememberScrollState()

    var showSignaturePad by remember { mutableStateOf(false) }
    var pendingSignatureSpot by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var savedSignatureStrokes by remember { mutableStateOf<Pair<List<List<NormalizedPoint>>, Int>?>(null) }

    val todayStamp = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    LaunchedEffect(document.filePath, currentPage) {
        isRendering = true
        pageBitmap = engine.renderPdfPage(
            filePath = document.filePath,
            pageIndex = currentPage,
            targetWidthPx = 1280
        )
        isRendering = false
    }

    if (showSignaturePad) {
        SignaturePadDialog(
            onDismiss = {
                showSignaturePad = false
                pendingSignatureSpot = null
            },
            onConfirmSignature = { strokes, colorArgb ->
                savedSignatureStrokes = strokes to colorArgb
                val spot = pendingSignatureSpot ?: (0.52f to 0.76f)
                onAddSignatureAt(currentPage, spot.first, spot.second, strokes, colorArgb)
                showSignaturePad = false
                pendingSignatureSpot = null
            }
        )
    }

    val pageDetectedLines = remember(detectedLines, currentPage) {
        detectedLines.filter { it.pageIndex == currentPage }
    }
    val pageEdits = remember(edits, currentPage) { edits.filter { it.pageIndex == currentPage } }
    val editedSourceIds = remember(pageEdits) { pageEdits.mapNotNull { it.sourceLineId }.toSet() }
    val uneditedPageLines = remember(pageDetectedLines, editedSourceIds) {
        pageDetectedLines.filterNot { it.id in editedSourceIds }
    }
    val pageSignatures = remember(signatures, currentPage) { signatures.filter { it.pageIndex == currentPage } }
    val pageRedactions = remember(redactions, currentPage) { redactions.filter { it.pageIndex == currentPage } }

    val selectedTextEdit = remember(edits, selectedOverlay) {
        (selectedOverlay as? SelectedPdfOverlay.TextBox)?.let { sel -> edits.firstOrNull { it.id == sel.id } }
    }
    val selectedSignature = remember(signatures, selectedOverlay) {
        (selectedOverlay as? SelectedPdfOverlay.Signature)?.let { sel -> signatures.firstOrNull { it.id == sel.id } }
    }
    val selectedRedaction = remember(redactions, selectedOverlay) {
        (selectedOverlay as? SelectedPdfOverlay.Redaction)?.let { sel -> redactions.firstOrNull { it.id == sel.id } }
    }

    // Bring inspector card smoothly into view when user taps an existing PDF text line
    LaunchedEffect(selectedOverlay) {
        if (selectedOverlay is SelectedPdfOverlay.TextBox) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // PenguinPDF-Style Modular Tool Strip (Text · Sign · Redact · Form Stamp)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 820.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PdfVisualTool.entries.forEach { tool ->
                        val icon = when (tool) {
                            PdfVisualTool.TEXT -> Icons.Filled.TextFields
                            PdfVisualTool.SIGN -> Icons.Filled.Draw
                            PdfVisualTool.REDACT -> Icons.Filled.Block
                            PdfVisualTool.STAMP -> Icons.Filled.Approval
                        }
                        FilterChip(
                            selected = activeTool == tool,
                            onClick = { activeTool = tool },
                            label = {
                                Text(
                                    text = tool.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (activeTool == tool) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.testTag("visual_tool_${tool.name.lowercase()}")
                        )
                    }
                }

                // Contextual Page Controls + Quick Primary Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (currentPage > 0) {
                                    currentPage--
                                    onSelectOverlay(null)
                                }
                            },
                            enabled = currentPage > 0,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("visual_editor_prev_page")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous page")
                        }
                        Text(
                            text = "Page ${currentPage + 1} / $safePageCount",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(
                            onClick = {
                                if (currentPage < safePageCount - 1) {
                                    currentPage++
                                    onSelectOverlay(null)
                                }
                            },
                            enabled = currentPage < safePageCount - 1,
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("visual_editor_next_page")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next page")
                        }
                    }

                    when (activeTool) {
                        PdfVisualTool.TEXT -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (pageDetectedLines.isNotEmpty()) {
                                    FilterChip(
                                        selected = showEditableHighlights,
                                        onClick = { showEditableHighlights = !showEditableHighlights },
                                        label = {
                                            Text(
                                                text = "Text (${pageDetectedLines.size})",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (showEditableHighlights) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                                contentDescription = "Toggle existing text highlights",
                                                modifier = Modifier.size(15.dp)
                                            )
                                        },
                                        modifier = Modifier.testTag("visual_editor_toggle_detected_lines")
                                    )
                                }
                                FilledTonalButton(
                                    onClick = {
                                        onAddTextAt(
                                            currentPage,
                                            0.10f,
                                            0.16f + ((pageEdits.size % 5) * 0.12f),
                                            "",
                                            true,
                                            false,
                                            AndroidColor.parseColor("#1A1C1E"),
                                            0.44f
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("visual_editor_add_text_button")
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("New Text", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        PdfVisualTool.SIGN -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (savedSignatureStrokes != null) {
                                    FilledTonalButton(
                                        onClick = {
                                            val saved = savedSignatureStrokes!!
                                            onAddSignatureAt(currentPage, 0.50f, 0.74f, saved.first, saved.second)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("visual_editor_reuse_sig")
                                    ) {
                                        Text("Stamp Saved", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                FilledTonalButton(
                                    onClick = {
                                        pendingSignatureSpot = 0.50f to 0.74f
                                        showSignaturePad = true
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("visual_editor_draw_sig_button")
                                ) {
                                    Icon(Icons.Filled.Draw, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Draw Signature", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        PdfVisualTool.REDACT -> {
                            FilledTonalButton(
                                onClick = {
                                    onAddRedactionAt(
                                        currentPage,
                                        0.12f,
                                        0.20f + ((pageRedactions.size % 6) * 0.09f)
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("visual_editor_add_redact_button")
                            ) {
                                Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Redaction Bar", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        PdfVisualTool.STAMP -> {
                            Text(
                                text = "Tap a stamp below",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Quick Form Fill Stamps strip when STAMP tool is selected
                if (activeTool == PdfVisualTool.STAMP) {
                    val quickStamps = listOf(
                        Triple("✓", AndroidColor.parseColor("#006C48"), 0.14f),
                        Triple("✗", AndroidColor.parseColor("#B91C1C"), 0.14f),
                        Triple(todayStamp, AndroidColor.parseColor("#1A1C1E"), 0.28f),
                        Triple("APPROVED", AndroidColor.parseColor("#006C48"), 0.30f),
                        Triple("CONFIDENTIAL", AndroidColor.parseColor("#B91C1C"), 0.36f),
                        Triple("DRAFT", AndroidColor.parseColor("#1D4ED8"), 0.24f)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickStamps.forEach { (stampText, colorArgb, wFrac) ->
                            FilledTonalButton(
                                onClick = {
                                    onAddTextAt(
                                        currentPage,
                                        0.16f + ((pageEdits.size % 4) * 0.08f),
                                        0.22f + ((pageEdits.size % 5) * 0.10f),
                                        stampText,
                                        false,
                                        true,
                                        colorArgb,
                                        wFrac
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("quick_stamp_${stampText.lowercase().replace(" ", "_")}")
                            ) {
                                Text(stampText, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Permanent Redaction Security Notice when Redact tool or redactions are active
        if (activeTool == PdfVisualTool.REDACT || redactions.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 820.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Permanent Redaction (${redactions.size} box${if (redactions.size == 1) "" else "es"}): covered pixels & underlying text are permanently destroyed on save — cannot be copied or peeled off.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Original PDF Page Surface (preserves 100% of embedded images, tables, vectors & alignment)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 820.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            val bmp = pageBitmap
            if (isRendering || bmp == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRendering) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                    } else {
                        Text(
                            text = "Could not render page preview.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val aspect = (bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat()).coerceIn(0.45f, 1.8f)
                val density = LocalDensity.current
                val latestCurrentPage by rememberUpdatedState(currentPage)
                val latestTool by rememberUpdatedState(activeTool)
                val latestSavedSig by rememberUpdatedState(savedSignatureStrokes)
                val latestDetectedLines by rememberUpdatedState(pageDetectedLines)
                val latestPageEdits by rememberUpdatedState(pageEdits)
                val latestBmp by rememberUpdatedState(bmp)

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspect)
                        .clip(RoundedCornerShape(12.dp))
                        .pointerInput(document.id, currentPage) {
                            detectTapGestures { offset ->
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                val rawX = (offset.x / w).coerceIn(0f, 1f)
                                val rawY = (offset.y / h).coerceIn(0f, 1f)
                                val xFrac = rawX.coerceIn(0.02f, 0.72f)
                                val yFrac = rawY.coerceIn(0.02f, 0.88f)
                                when (latestTool) {
                                    PdfVisualTool.TEXT -> {
                                        val hitLine = findTappedExistingLine(latestDetectedLines, rawX, rawY)
                                        if (hitLine != null) {
                                            val existingEdit = latestPageEdits.firstOrNull { it.sourceLineId == hitLine.id }
                                            if (existingEdit != null) {
                                                onSelectOverlay(SelectedPdfOverlay.TextBox(existingEdit.id))
                                            } else {
                                                val bg = sampleLineBackgroundColor(
                                                    latestBmp,
                                                    hitLine.xFraction,
                                                    hitLine.yFraction,
                                                    hitLine.widthFraction,
                                                    hitLine.heightFraction
                                                )
                                                val ink = sampleLineInkColor(
                                                    latestBmp,
                                                    hitLine.xFraction,
                                                    hitLine.yFraction,
                                                    hitLine.widthFraction,
                                                    hitLine.heightFraction,
                                                    bg
                                                )
                                                onEditExistingLine(hitLine, bg, ink)
                                            }
                                        } else {
                                            onAddTextAt(
                                                latestCurrentPage,
                                                xFrac,
                                                yFrac,
                                                "",
                                                true,
                                                false,
                                                AndroidColor.parseColor("#1A1C1E"),
                                                0.42f
                                            )
                                        }
                                    }
                                    PdfVisualTool.SIGN -> {
                                        val saved = latestSavedSig
                                        if (saved != null) {
                                            onAddSignatureAt(latestCurrentPage, xFrac, yFrac, saved.first, saved.second)
                                        } else {
                                            pendingSignatureSpot = xFrac to yFrac
                                            showSignaturePad = true
                                        }
                                    }
                                    PdfVisualTool.REDACT -> onAddRedactionAt(latestCurrentPage, xFrac, yFrac)
                                    PdfVisualTool.STAMP -> onAddTextAt(
                                        latestCurrentPage,
                                        xFrac,
                                        yFrac,
                                        "✓",
                                        false,
                                        true,
                                        AndroidColor.parseColor("#006C48"),
                                        0.14f
                                    )
                                }
                            }
                        }
                        .testTag("visual_pdf_page_canvas")
                ) {
                    val containerWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
                    val containerHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
                    val ptScale = (maxWidth.value / 595f).coerceIn(0.35f, 1.8f)

                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Original PDF page ${currentPage + 1} with images and formatting preserved",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )

                    // 0. Click-to-Edit Existing PDF Text Line Hit Highlights
                    if (activeTool == PdfVisualTool.TEXT && showEditableHighlights) {
                        uneditedPageLines.forEach { line ->
                            val lineW = (maxWidth * (line.widthFraction + 0.012f).coerceIn(0.05f, 0.98f))
                            val lineH = (maxHeight * (line.heightFraction + 0.004f).coerceIn(0.016f, 0.25f))
                                .coerceAtLeast(14.dp)
                            val lineX = ((line.xFraction - 0.004f).coerceAtLeast(0f) * containerWidthPx).roundToInt()
                            val lineY = ((line.yFraction - 0.002f).coerceAtLeast(0f) * containerHeightPx).roundToInt()

                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(lineX, lineY) }
                                    .size(width = lineW, height = lineH)
                                    .background(
                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.07f),
                                        shape = RoundedCornerShape(2.dp)
                                    )
                                    .border(
                                        width = 0.6.dp,
                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.34f),
                                        shape = RoundedCornerShape(2.dp)
                                    )
                                    .pointerInput(line.id, bmp) {
                                        detectTapGestures(
                                            onTap = {
                                                val bg = sampleLineBackgroundColor(
                                                    bmp,
                                                    line.xFraction,
                                                    line.yFraction,
                                                    line.widthFraction,
                                                    line.heightFraction
                                                )
                                                val ink = sampleLineInkColor(
                                                    bmp,
                                                    line.xFraction,
                                                    line.yFraction,
                                                    line.widthFraction,
                                                    line.heightFraction,
                                                    bg
                                                )
                                                onEditExistingLine(line, bg, ink)
                                            }
                                        )
                                    }
                                    .testTag("visual_pdf_existing_line_${line.id}")
                            )
                        }
                    }

                    // 1. Permanent Redaction Boxes
                    pageRedactions.forEach { red ->
                        val isSelected = selectedOverlay == SelectedPdfOverlay.Redaction(red.id)
                        val latestRed by rememberUpdatedState(red)
                        val boxW = maxWidth * red.widthFraction.coerceIn(0.06f, 0.96f)
                        val boxH = maxHeight * red.heightFraction.coerceIn(0.015f, 0.45f)
                        val offX = (red.xFraction * containerWidthPx).roundToInt()
                        val offY = (red.yFraction * containerHeightPx).roundToInt()

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(offX, offY) }
                                .size(width = boxW, height = boxH)
                                .background(Color(0xFF0B0D0F), RoundedCornerShape(2.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 0.75.dp,
                                    color = if (isSelected) Color(0xFFEF4444) else Color.White.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(2.dp)
                                )
                                .pointerInput(red.id, containerWidthPx, containerHeightPx) {
                                    detectTapGestures(onTap = { onSelectOverlay(SelectedPdfOverlay.Redaction(latestRed.id)) })
                                }
                                .pointerInput(red.id, containerWidthPx, containerHeightPx) {
                                    detectDragGestures(
                                        onDragStart = { onSelectOverlay(SelectedPdfOverlay.Redaction(latestRed.id)) },
                                        onDrag = { change, drag ->
                                            change.consume()
                                            val cur = latestRed
                                            val nextX = (cur.xFraction + drag.x / containerWidthPx)
                                                .coerceIn(0f, (1f - cur.widthFraction).coerceAtLeast(0.02f))
                                            val nextY = (cur.yFraction + drag.y / containerHeightPx)
                                                .coerceIn(0f, (1f - cur.heightFraction).coerceAtLeast(0.02f))
                                            onUpdateRedaction(cur.copy(xFraction = nextX, yFraction = nextY))
                                        }
                                    )
                                }
                                .testTag("visual_pdf_redaction_${red.id}")
                        )
                    }

                    // 2. Text / In-Place Replaced Line / Form Stamp Boxes
                    pageEdits.forEach { edit ->
                        val isSelected = selectedOverlay == SelectedPdfOverlay.TextBox(edit.id)
                        val latestEdit by rememberUpdatedState(edit)
                        val isInPlaceLine = edit.sourceLineId != null
                        val boxWidthDp = maxWidth * edit.widthFraction.coerceIn(0.08f, 0.96f)
                        val minCoverHeightDp = if (edit.minCoverHeightFraction > 0f) {
                            maxHeight * edit.minCoverHeightFraction.coerceIn(0f, 0.45f)
                        } else {
                            0.dp
                        }
                        val offsetX = (edit.xFraction * containerWidthPx).roundToInt()
                        val offsetY = (edit.yFraction * containerHeightPx).roundToInt()
                        val composeAlign = when (edit.alignment) {
                            Paint.Align.CENTER -> TextAlign.Center
                            Paint.Align.RIGHT -> TextAlign.End
                            else -> TextAlign.Start
                        }

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(offsetX, offsetY) }
                                .width(boxWidthDp)
                                .heightIn(min = minCoverHeightDp)
                                .background(
                                    color = if (edit.coverOriginal) Color(edit.coverColorArgb) else Color.Transparent,
                                    shape = RoundedCornerShape(2.dp)
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.75.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(2.dp)
                                )
                                .pointerInput(edit.id, containerWidthPx, containerHeightPx) {
                                    detectTapGestures(onTap = { onSelectOverlay(SelectedPdfOverlay.TextBox(latestEdit.id)) })
                                }
                                .pointerInput(edit.id, containerWidthPx, containerHeightPx) {
                                    detectDragGestures(
                                        onDragStart = { onSelectOverlay(SelectedPdfOverlay.TextBox(latestEdit.id)) },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val cur = latestEdit
                                            val maxX = (1f - cur.widthFraction).coerceAtLeast(0.02f)
                                            val nextX = (cur.xFraction + dragAmount.x / containerWidthPx).coerceIn(0f, maxX)
                                            val nextY = (cur.yFraction + dragAmount.y / containerHeightPx).coerceIn(0f, 0.96f)
                                            onUpdateTextEdit(cur.copy(xFraction = nextX, yFraction = nextY))
                                        }
                                    )
                                }
                                .padding(
                                    horizontal = ((if (isInPlaceLine) 2.5f else 6f) * ptScale).dp.coerceAtLeast(1.5.dp),
                                    vertical = ((if (isInPlaceLine) 1.5f else 4f) * ptScale).dp.coerceAtLeast(1.dp)
                                )
                                .testTag("visual_pdf_overlay_${edit.id}")
                        ) {
                            val lineMult = if (isInPlaceLine) 1.24f else 1.32f
                            Text(
                                text = if (edit.text.isEmpty() && !isInPlaceLine) "Tap below to type…" else edit.text,
                                color = if (edit.text.isEmpty() && !isInPlaceLine) Color(0xFF6B7280) else Color(edit.colorArgb),
                                fontSize = (edit.fontSizePt * ptScale).coerceIn(6.5f, 44f).sp,
                                lineHeight = (edit.fontSizePt * lineMult * ptScale).coerceIn(8f, 54f).sp,
                                fontWeight = if (edit.isBold) FontWeight.Bold else FontWeight.Normal,
                                textAlign = composeAlign,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // 3. Hand-Drawn Signatures
                    pageSignatures.forEach { sig ->
                        val isSelected = selectedOverlay == SelectedPdfOverlay.Signature(sig.id)
                        val latestSig by rememberUpdatedState(sig)
                        val sigW = maxWidth * sig.widthFraction.coerceIn(0.12f, 0.85f)
                        val sigH = (sigW / sig.aspectRatio.coerceIn(1.2f, 4.5f)).coerceAtLeast(24.dp)
                        val offX = (sig.xFraction * containerWidthPx).roundToInt()
                        val offY = (sig.yFraction * containerHeightPx).roundToInt()

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(offX, offY) }
                                .size(width = sigW, height = sigH)
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.75.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .pointerInput(sig.id, containerWidthPx, containerHeightPx) {
                                    detectTapGestures(onTap = { onSelectOverlay(SelectedPdfOverlay.Signature(latestSig.id)) })
                                }
                                .pointerInput(sig.id, containerWidthPx, containerHeightPx) {
                                    detectDragGestures(
                                        onDragStart = { onSelectOverlay(SelectedPdfOverlay.Signature(latestSig.id)) },
                                        onDrag = { change, drag ->
                                            change.consume()
                                            val cur = latestSig
                                            val nextX = (cur.xFraction + drag.x / containerWidthPx)
                                                .coerceIn(0f, (1f - cur.widthFraction).coerceAtLeast(0.02f))
                                            val nextY = (cur.yFraction + drag.y / containerHeightPx).coerceIn(0f, 0.90f)
                                            onUpdateSignature(cur.copy(xFraction = nextX, yFraction = nextY))
                                        }
                                    )
                                }
                                .testTag("visual_pdf_signature_${sig.id}")
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val strokeColor = Color(sig.colorArgb)
                                val strokePx = (sig.strokeWidthPt * ptScale * density.density).coerceIn(2f, 12f)
                                val style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                sig.strokes.forEach { stroke ->
                                    if (stroke.size == 1) {
                                        drawCircle(
                                            color = strokeColor,
                                            radius = strokePx / 2f,
                                            center = Offset(stroke[0].x * size.width, stroke[0].y * size.height)
                                        )
                                    } else if (stroke.size >= 2) {
                                        val path = Path()
                                        path.moveTo(stroke[0].x * size.width, stroke[0].y * size.height)
                                        for (i in 1 until stroke.size) {
                                            val prev = stroke[i - 1]
                                            val cur = stroke[i]
                                            path.quadraticTo(
                                                prev.x * size.width,
                                                prev.y * size.height,
                                                ((prev.x + cur.x) * 0.5f) * size.width,
                                                ((prev.y + cur.y) * 0.5f) * size.height
                                            )
                                        }
                                        val last = stroke.last()
                                        path.lineTo(last.x * size.width, last.y * size.height)
                                        drawPath(path = path, color = strokeColor, style = style)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Contextual Inspector for Selected Overlay (Text, Signature, or Redaction)
        when {
            selectedTextEdit != null -> {
                TextBoxInspectorCard(
                    selectedEdit = selectedTextEdit,
                    onUpdate = onUpdateTextEdit,
                    onDelete = { onDeleteTextEdit(selectedTextEdit.id) }
                )
            }

            selectedSignature != null -> {
                SignatureInspectorCard(
                    signature = selectedSignature,
                    onUpdate = onUpdateSignature,
                    onRedraw = {
                        pendingSignatureSpot = selectedSignature.xFraction to selectedSignature.yFraction
                        onDeleteSignature(selectedSignature.id)
                        showSignaturePad = true
                    },
                    onDelete = { onDeleteSignature(selectedSignature.id) }
                )
            }

            selectedRedaction != null -> {
                RedactionInspectorCard(
                    redaction = selectedRedaction,
                    onUpdate = onUpdateRedaction,
                    onDelete = { onDeleteRedaction(selectedRedaction.id) }
                )
            }

            else -> {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 820.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    val tip = when (activeTool) {
                        PdfVisualTool.TEXT -> if (pageDetectedLines.isNotEmpty()) {
                            "Tap any highlighted text directly on the page to edit it in place (${pageDetectedLines.size} editable line${if (pageDetectedLines.size == 1) "" else "s"} on this page). Tap blank space to add new text."
                        } else {
                            "Scanned/image page (no embedded PDF text layer) — tap anywhere on the page to whiteout & type replacement text, or use Sign / Redact."
                        }
                        PdfVisualTool.SIGN -> "Tap the PDF page (or Draw Signature) to place a crisp hand-drawn signature, then drag and resize it anywhere."
                        PdfVisualTool.REDACT -> "Tap over sensitive information to place a permanent black redaction bar. Drag and resize before saving."
                        PdfVisualTool.STAMP -> "Tap a stamp above (✓, ✗, Date, APPROVED) or tap directly on a checkbox on the page."
                    }
                    Text(
                        text = tip,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun TextBoxInspectorCard(
    selectedEdit: PdfTextOverlayEdit,
    onUpdate: (PdfTextOverlayEdit) -> Unit,
    onDelete: () -> Unit
) {
    val isInPlaceLine = selectedEdit.sourceLineId != null
    val initialCoverColor = remember(selectedEdit.id) { selectedEdit.coverColorArgb }
    val initialInkColor = remember(selectedEdit.id) { selectedEdit.colorArgb }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 820.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isInPlaceLine) Icons.Filled.Edit else Icons.Filled.OpenWith,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isInPlaceLine) {
                            "Editing Existing PDF Text (drag to adjust)"
                        } else {
                            "New Text Box (drag on page to align)"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (isInPlaceLine) {
                    FilledTonalButton(
                        onClick = onDelete,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("visual_editor_revert_line")
                    ) {
                        Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Revert", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("visual_editor_delete_box")
                    ) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = "Delete text box",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = selectedEdit.text,
                onValueChange = { onUpdate(selectedEdit.copy(text = it)) },
                label = {
                    Text(if (isInPlaceLine) "Edit PDF text in place (clear to erase line)" else "Replacement or new text")
                },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("visual_editor_box_text_input")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedEdit.coverOriginal,
                    onClick = { onUpdate(selectedEdit.copy(coverOriginal = !selectedEdit.coverOriginal)) },
                    label = { Text(if (isInPlaceLine) "Replace Original" else "Whiteout Cover", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = { Icon(Icons.Filled.Layers, contentDescription = null, modifier = Modifier.size(15.dp)) },
                    modifier = Modifier.testTag("visual_editor_whiteout_toggle")
                )

                FilterChip(
                    selected = selectedEdit.isBold,
                    onClick = { onUpdate(selectedEdit.copy(isBold = !selectedEdit.isBold)) },
                    label = { Text("Bold", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = { Icon(Icons.Filled.FormatBold, contentDescription = null, modifier = Modifier.size(15.dp)) },
                    modifier = Modifier.testTag("visual_editor_bold_toggle")
                )

                listOf(
                    Triple(Paint.Align.LEFT, Icons.AutoMirrored.Filled.FormatAlignLeft, "Left"),
                    Triple(Paint.Align.CENTER, Icons.Filled.FormatAlignCenter, "Center"),
                    Triple(Paint.Align.RIGHT, Icons.AutoMirrored.Filled.FormatAlignRight, "Right")
                ).forEach { (align, icon, label) ->
                    FilterChip(
                        selected = selectedEdit.alignment == align,
                        onClick = { onUpdate(selectedEdit.copy(alignment = align)) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(icon, contentDescription = "Align $label", modifier = Modifier.size(15.dp)) }
                    )
                }
            }

            if (selectedEdit.coverOriginal && isInPlaceLine) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Backdrop", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf(
                        initialCoverColor to "Auto Match",
                        AndroidColor.WHITE to "White",
                        AndroidColor.parseColor("#FAF9F6") to "Paper"
                    ).distinctBy { it.first }.forEach { (bgArgb, label) ->
                        FilterChip(
                            selected = selectedEdit.coverColorArgb == bgArgb,
                            onClick = { onUpdate(selectedEdit.copy(coverColorArgb = bgArgb)) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Size", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(
                        onClick = { onUpdate(selectedEdit.copy(fontSizePt = (selectedEdit.fontSizePt - 1f).coerceAtLeast(8f))) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = "Decrease font size", modifier = Modifier.size(16.dp))
                    }
                    Text("${selectedEdit.fontSizePt.roundToInt()}pt", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    IconButton(
                        onClick = { onUpdate(selectedEdit.copy(fontSizePt = (selectedEdit.fontSizePt + 1f).coerceAtMost(72f))) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Increase font size", modifier = Modifier.size(16.dp))
                    }
                }

                val inkOptions = remember(initialInkColor) {
                    if (OVERLAY_INK_COLORS.any { it.first == initialInkColor }) {
                        OVERLAY_INK_COLORS
                    } else {
                        listOf(initialInkColor to "Matched") + OVERLAY_INK_COLORS
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    inkOptions.forEach { (argb, label) ->
                        val isPicked = selectedEdit.colorArgb == argb
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .border(
                                    width = if (isPicked) 2.5.dp else 1.dp,
                                    color = if (isPicked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape
                                )
                                .clickable { onUpdate(selectedEdit.copy(colorArgb = argb)) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isPicked) {
                                Icon(Icons.Filled.Check, contentDescription = "$label ink selected", tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Box Width", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = selectedEdit.widthFraction,
                    onValueChange = { w ->
                        val clampedW = w.coerceIn(0.10f, 0.96f)
                        val clampedX = selectedEdit.xFraction.coerceAtMost((1f - clampedW).coerceAtLeast(0f))
                        onUpdate(selectedEdit.copy(widthFraction = clampedW, xFraction = clampedX))
                    },
                    valueRange = 0.10f..0.96f,
                    modifier = Modifier.weight(1f)
                )
                Text("${(selectedEdit.widthFraction * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
            }
        }
    }
}

private fun findTappedExistingLine(
    lines: List<PdfEditableTextLine>,
    xFrac: Float,
    yFrac: Float
): PdfEditableTextLine? {
    val padX = 0.014f
    val padY = 0.009f
    return lines
        .filter { line ->
            xFrac in (line.xFraction - padX)..(line.xFraction + line.widthFraction + padX) &&
                yFrac in (line.yFraction - padY)..(line.yFraction + line.heightFraction + padY)
        }
        .minByOrNull { line ->
            val centerY = line.yFraction + line.heightFraction * 0.5f
            abs(yFrac - centerY)
        }
}

private fun sampleLineBackgroundColor(
    bitmap: Bitmap,
    xFrac: Float,
    yFrac: Float,
    wFrac: Float,
    hFrac: Float
): Int {
    return try {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 4 || h <= 4) return AndroidColor.WHITE

        val x0 = (xFrac * w).roundToInt().coerceIn(0, w - 1)
        val y0 = (yFrac * h).roundToInt().coerceIn(0, h - 1)
        val x1 = ((xFrac + wFrac) * w).roundToInt().coerceIn(x0, w - 1)
        val y1 = ((yFrac + hFrac) * h).roundToInt().coerceIn(y0, h - 1)
        val pad = 4

        val samples = mutableListOf<Int>()
        val topY = (y0 - pad).coerceIn(0, h - 1)
        val botY = (y1 + pad).coerceIn(0, h - 1)
        val leftX = (x0 - pad).coerceIn(0, w - 1)
        val rightX = (x1 + pad).coerceIn(0, w - 1)

        val steps = 8
        for (i in 0..steps) {
            val sx = (x0 + ((x1 - x0) * i) / steps).coerceIn(0, w - 1)
            samples.add(bitmap.getPixel(sx, topY))
            samples.add(bitmap.getPixel(sx, botY))
        }
        val midY = ((y0 + y1) / 2).coerceIn(0, h - 1)
        samples.add(bitmap.getPixel(leftX, midY))
        samples.add(bitmap.getPixel(rightX, midY))

        // Bucket colors in 16-level RGB bins to find the dominant clean page background
        val buckets = HashMap<Int, MutableList<Int>>()
        for (px in samples) {
            val r = AndroidColor.red(px)
            val g = AndroidColor.green(px)
            val b = AndroidColor.blue(px)
            val key = ((r / 16) shl 10) or ((g / 16) shl 5) or (b / 16)
            buckets.getOrPut(key) { mutableListOf() }.add(px)
        }

        val winner = buckets.values.maxWithOrNull(
            compareBy<List<Int>> { it.size }.thenBy { list ->
                val p = list.first()
                AndroidColor.red(p) + AndroidColor.green(p) + AndroidColor.blue(p)
            }
        ) ?: return AndroidColor.WHITE

        val avgR = winner.map { AndroidColor.red(it) }.average().roundToInt().coerceIn(0, 255)
        val avgG = winner.map { AndroidColor.green(it) }.average().roundToInt().coerceIn(0, 255)
        val avgB = winner.map { AndroidColor.blue(it) }.average().roundToInt().coerceIn(0, 255)
        // Snap near-pure white to crisp #FFFFFF to avoid subtle gray tint boxes on white paper
        if (avgR >= 244 && avgG >= 244 && avgB >= 244) {
            AndroidColor.WHITE
        } else {
            AndroidColor.rgb(avgR, avgG, avgB)
        }
    } catch (_: Exception) {
        AndroidColor.WHITE
    }
}

private fun sampleLineInkColor(
    bitmap: Bitmap,
    xFrac: Float,
    yFrac: Float,
    wFrac: Float,
    hFrac: Float,
    bgArgb: Int
): Int {
    val defaultInk = AndroidColor.parseColor("#1A1C1E")
    return try {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 4 || h <= 4) return defaultInk

        val x0 = (xFrac * w).roundToInt().coerceIn(0, w - 1)
        val y0 = (yFrac * h).roundToInt().coerceIn(0, h - 1)
        val x1 = ((xFrac + wFrac) * w).roundToInt().coerceIn(x0, w - 1)
        val y1 = ((yFrac + hFrac) * h).roundToInt().coerceIn(y0, h - 1)

        val bgR = AndroidColor.red(bgArgb)
        val bgG = AndroidColor.green(bgArgb)
        val bgB = AndroidColor.blue(bgArgb)

        val candidates = mutableListOf<Pair<Double, Int>>()
        val cols = 16
        val rows = 6
        for (c in 0..cols) {
            val pxX = (x0 + ((x1 - x0) * c) / cols).coerceIn(0, w - 1)
            for (r in 0..rows) {
                val pxY = (y0 + ((y1 - y0) * r) / rows).coerceIn(0, h - 1)
                val px = bitmap.getPixel(pxX, pxY)
                val dr = (AndroidColor.red(px) - bgR).toDouble()
                val dg = (AndroidColor.green(px) - bgG).toDouble()
                val db = (AndroidColor.blue(px) - bgB).toDouble()
                val dist = sqrt(dr * dr + dg * dg + db * db)
                if (dist >= 65.0) {
                    candidates.add(dist to px)
                }
            }
        }

        if (candidates.isEmpty()) return defaultInk
        val topInk = candidates.sortedByDescending { it.first }.take((candidates.size / 4).coerceIn(3, 12))
        val avgR = topInk.map { AndroidColor.red(it.second) }.average().roundToInt().coerceIn(0, 255)
        val avgG = topInk.map { AndroidColor.green(it.second) }.average().roundToInt().coerceIn(0, 255)
        val avgB = topInk.map { AndroidColor.blue(it.second) }.average().roundToInt().coerceIn(0, 255)
        AndroidColor.rgb(avgR, avgG, avgB)
    } catch (_: Exception) {
        defaultInk
    }
}

@Composable
private fun SignatureInspectorCard(
    signature: PdfSignatureOverlay,
    onUpdate: (PdfSignatureOverlay) -> Unit,
    onRedraw: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 820.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.Draw, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text("Hand-Drawn Signature (drag to move)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(
                        onClick = onRedraw,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Redraw", style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete signature", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Size", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = signature.widthFraction,
                    onValueChange = { w ->
                        val clampedW = w.coerceIn(0.14f, 0.78f)
                        val clampedX = signature.xFraction.coerceAtMost((1f - clampedW).coerceAtLeast(0f))
                        onUpdate(signature.copy(widthFraction = clampedW, xFraction = clampedX))
                    },
                    valueRange = 0.14f..0.78f,
                    modifier = Modifier.weight(1f)
                )
                Text("${(signature.widthFraction * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun RedactionInspectorCard(
    redaction: PdfRedactionOverlay,
    onUpdate: (PdfRedactionOverlay) -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 820.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Text("Permanent Redaction Bar (drag to cover)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete redaction bar", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Width", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = redaction.widthFraction,
                    onValueChange = { w ->
                        val clampedW = w.coerceIn(0.08f, 0.94f)
                        val clampedX = redaction.xFraction.coerceAtMost((1f - clampedW).coerceAtLeast(0f))
                        onUpdate(redaction.copy(widthFraction = clampedW, xFraction = clampedX))
                    },
                    valueRange = 0.08f..0.94f,
                    modifier = Modifier.weight(1f)
                )
                Text("${(redaction.widthFraction * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Height", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = redaction.heightFraction,
                    onValueChange = { h ->
                        val clampedH = h.coerceIn(0.02f, 0.35f)
                        val clampedY = redaction.yFraction.coerceAtMost((1f - clampedH).coerceAtLeast(0f))
                        onUpdate(redaction.copy(heightFraction = clampedH, yFraction = clampedY))
                    },
                    valueRange = 0.02f..0.35f,
                    modifier = Modifier.weight(1f)
                )
                Text("${(redaction.heightFraction * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
