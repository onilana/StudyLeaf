package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.data.model.ConversionToolType
import com.example.data.model.DocumentFormat
import com.example.data.model.formatFileSize
import com.example.ui.components.DocumentPreviewIcon
import com.example.ui.components.FormatBadge
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@Composable
fun ConversionStudioScreen(
    tool: ConversionToolType,
    preselectedDocId: Long?,
    documents: List<DocumentEntity>,
    engine: DocumentEngine,
    onBack: () -> Unit,
    onConvertImagesToPdf: (List<Bitmap>, String, Int) -> Unit,
    onMergePdfs: (List<DocumentEntity>, String) -> Unit,
    onConvertTextToPdf: (String, String, Float, Int, String) -> Unit,
    onOpenReorderBoard: (Long) -> Unit,
    onOptimizePdf: (DocumentEntity, Float, String) -> Unit,
    onExportPdfToImages: (DocumentEntity) -> Unit,
    onDocOrPdfToExcel: (DocumentEntity, String) -> Unit,
    onExcelToPdf: (DocumentEntity, String) -> Unit,
    onExcelToWord: (DocumentEntity, String) -> Unit,
    onCompressImages: (List<Bitmap>, Int, Int, String) -> Unit
) {
    BackHandler(onBack = onBack)

    val channelColor = tool.formatChannel.channelColor()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top App Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp
        ) {
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
                        .testTag("studio_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tool.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Processed 100% locally on device",
                        style = MaterialTheme.typography.labelSmall,
                        color = channelColor
                    )
                }
                FormatBadge(format = tool.formatChannel)
                Spacer(modifier = Modifier.width(8.dp))
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (tool) {
                ConversionToolType.IMAGE_TO_PDF -> ImageAndPdfBidirectionalStudio(
                    documents = documents,
                    engine = engine,
                    onConvertImagesToPdf = onConvertImagesToPdf,
                    onExportPdfToImages = onExportPdfToImages
                )
                ConversionToolType.MERGE_PDF -> MergePdfStudio(
                    documents = documents,
                    onMerge = onMergePdfs
                )
                ConversionToolType.WORD_TO_PDF -> WordToPdfStudio(
                    documents = documents,
                    preselectedDocId = preselectedDocId,
                    engine = engine,
                    onConvert = onConvertTextToPdf
                )
                ConversionToolType.EXCEL_CONVERTER -> ExcelConverterStudio(
                    documents = documents,
                    preselectedDocId = preselectedDocId,
                    onDocOrPdfToExcel = onDocOrPdfToExcel,
                    onExcelToPdf = onExcelToPdf,
                    onExcelToWord = onExcelToWord
                )
                ConversionToolType.COMPRESS_IMAGE -> ImageCompressorStudio(
                    documents = documents,
                    engine = engine,
                    onCompressImages = onCompressImages
                )
                ConversionToolType.SPLIT_REORDER -> PickPdfStudio(
                    documents = documents.filter { it.format == DocumentFormat.PDF || it.format == DocumentFormat.SCAN },
                    actionLabel = "Open Page Reorder Board",
                    helperText = "Choose a PDF document to rearrange, extract, or remove individual pages.",
                    onProceed = { onOpenReorderBoard(it.id) }
                )
                ConversionToolType.OPTIMIZE_PDF -> OptimizePdfStudio(
                    documents = documents.filter { it.format == DocumentFormat.PDF || it.format == DocumentFormat.SCAN },
                    onOptimize = onOptimizePdf
                )
                ConversionToolType.PDF_TO_IMAGE -> PickPdfStudio(
                    documents = documents.filter { it.format == DocumentFormat.PDF || it.format == DocumentFormat.SCAN },
                    actionLabel = "Export Pages to PNG",
                    helperText = "Render each page of the chosen PDF into a high-resolution PNG stored in your local vault.",
                    onProceed = onExportPdfToImages
                )
                ConversionToolType.SCAN_DOCUMENT -> Unit
            }
        }
    }
}

private enum class ExcelConversionMode(val label: String) {
    DOC_PDF_TO_EXCEL("Word/PDF → Excel"),
    EXCEL_TO_PDF("Excel → PDF"),
    EXCEL_TO_WORD("Excel → Word")
}

@Composable
private fun ExcelConverterStudio(
    documents: List<DocumentEntity>,
    preselectedDocId: Long?,
    onDocOrPdfToExcel: (DocumentEntity, String) -> Unit,
    onExcelToPdf: (DocumentEntity, String) -> Unit,
    onExcelToWord: (DocumentEntity, String) -> Unit
) {
    val preselected = remember(documents, preselectedDocId) {
        documents.firstOrNull { it.id == preselectedDocId }
    }
    var mode by remember(preselected?.id) {
        mutableStateOf(
            if (preselected?.format == DocumentFormat.EXCEL) ExcelConversionMode.EXCEL_TO_PDF
            else ExcelConversionMode.DOC_PDF_TO_EXCEL
        )
    }

    val eligibleDocs = remember(documents, mode) {
        when (mode) {
            ExcelConversionMode.DOC_PDF_TO_EXCEL -> documents.filter {
                it.format == DocumentFormat.PDF || it.format == DocumentFormat.SCAN || it.format == DocumentFormat.WORD
            }
            ExcelConversionMode.EXCEL_TO_PDF,
            ExcelConversionMode.EXCEL_TO_WORD -> documents.filter { it.format == DocumentFormat.EXCEL }
        }
    }

    var selectedDoc by remember(eligibleDocs, preselectedDocId) {
        mutableStateOf(
            eligibleDocs.firstOrNull { it.id == preselectedDocId } ?: eligibleDocs.firstOrNull()
        )
    }
    var outputTitle by remember(selectedDoc?.id, mode) {
        val base = selectedDoc?.title ?: "Converted Document"
        val suffix = when (mode) {
            ExcelConversionMode.DOC_PDF_TO_EXCEL -> "Sheet"
            ExcelConversionMode.EXCEL_TO_PDF -> "Table PDF"
            ExcelConversionMode.EXCEL_TO_WORD -> "Report"
        }
        mutableStateOf("$base ($suffix)")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Conversion Direction", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExcelConversionMode.entries.forEach { option ->
                        FilterChip(
                            selected = mode == option,
                            onClick = { mode = option },
                            shape = CircleShape,
                            label = { Text(option.label) },
                            modifier = Modifier.testTag("excel_mode_${option.name.lowercase()}")
                        )
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = outputTitle,
                onValueChange = { outputTitle = it },
                label = {
                    Text(
                        when (mode) {
                            ExcelConversionMode.DOC_PDF_TO_EXCEL -> "Output Excel (.xlsx) Name"
                            ExcelConversionMode.EXCEL_TO_PDF -> "Output PDF Name"
                            ExcelConversionMode.EXCEL_TO_WORD -> "Output Word (.docx) Name"
                        }
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("excel_output_title_input")
            )
        }

        item {
            Text(
                text = when (mode) {
                    ExcelConversionMode.DOC_PDF_TO_EXCEL -> "Select Word or PDF source (${eligibleDocs.size})"
                    ExcelConversionMode.EXCEL_TO_PDF,
                    ExcelConversionMode.EXCEL_TO_WORD -> "Select Excel (.xlsx / .csv) source (${eligibleDocs.size})"
                },
                style = MaterialTheme.typography.titleSmall
            )
        }

        if (eligibleDocs.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.TableChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Text("No matching files in Vault", style = MaterialTheme.typography.titleSmall)
                        }
                        Text(
                            text = if (mode == ExcelConversionMode.DOC_PDF_TO_EXCEL) {
                                "Import a Word or PDF file from Home to extract it into an Excel workbook."
                            } else {
                                "Import an .xlsx/.csv file from Home or convert a Word/PDF file to Excel first."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(eligibleDocs, key = { it.id }) { doc ->
                val selected = selectedDoc?.id == doc.id
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { selectedDoc = doc }
                        .testTag("excel_pick_${doc.id}"),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(
                        if (selected) 1.5.dp else 1.dp,
                        if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, onClick = { selectedDoc = doc })
                        DocumentPreviewIcon(format = doc.format, size = 34.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                doc.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${doc.format.label} • ${doc.pageCount} pg • ${formatFileSize(doc.sizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    val src = selectedDoc ?: return@Button
                    when (mode) {
                        ExcelConversionMode.DOC_PDF_TO_EXCEL -> onDocOrPdfToExcel(src, outputTitle)
                        ExcelConversionMode.EXCEL_TO_PDF -> onExcelToPdf(src, outputTitle)
                        ExcelConversionMode.EXCEL_TO_WORD -> onExcelToWord(src, outputTitle)
                    }
                },
                enabled = selectedDoc != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("excel_convert_execute_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    when (mode) {
                        ExcelConversionMode.DOC_PDF_TO_EXCEL -> "Convert to Excel (.xlsx)"
                        ExcelConversionMode.EXCEL_TO_PDF -> "Convert Excel to PDF"
                        ExcelConversionMode.EXCEL_TO_WORD -> "Convert Excel to Word (.docx)"
                    }
                )
            }
        }
    }
}

@Composable
private fun ImageCompressorStudio(
    documents: List<DocumentEntity>,
    engine: DocumentEngine,
    onCompressImages: (List<Bitmap>, Int, Int, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val selectedBitmaps = remember { mutableStateListOf<Pair<String, Bitmap>>() }
    var outputTitle by remember { mutableStateOf("Compressed Photo") }
    var qualityPercent by remember { mutableFloatStateOf(70f) }
    var maxDimensionPx by remember { mutableIntStateOf(1600) }

    val vaultImages = remember(documents) { documents.filter { it.format == DocumentFormat.IMAGE } }

    val multiPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 12)
    ) { uris ->
        scope.launch {
            uris.forEachIndexed { idx, uri ->
                val bmp = engine.decodeBitmapFromUri(uri, 2400)
                if (bmp != null) {
                    selectedBitmaps.add("Photo ${selectedBitmaps.size + idx + 1}" to bmp)
                }
            }
        }
    }

    val estimatedKb = remember(selectedBitmaps.size, qualityPercent, maxDimensionPx) {
        if (selectedBitmaps.isEmpty()) 0L
        else {
            selectedBitmaps.sumOf { (_, bmp) ->
                val largest = maxOf(bmp.width, bmp.height).coerceAtLeast(1)
                val scale = if (maxDimensionPx > 0 && largest > maxDimensionPx) {
                    maxDimensionPx.toFloat() / largest.toFloat()
                } else 1f
                val pixels = (bmp.width * scale) * (bmp.height * scale)
                val bpp = (0.12f + (qualityPercent / 100f) * 0.72f)
                ((pixels * bpp) / 8f / 1024f).roundToInt().coerceAtLeast(8).toLong()
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        multiPhotoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("compress_img_pick_photos"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pick Photos")
                }
                if (vaultImages.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                vaultImages.forEach { doc ->
                                    val bmp = engine.renderPdfPage(doc.filePath, 0, 1600)
                                    if (bmp != null) selectedBitmaps.add(doc.title to bmp)
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("compress_img_add_vault"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("+ Vault Images (${vaultImages.size})")
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = outputTitle,
                onValueChange = { outputTitle = it },
                label = { Text("Output Image Title") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("compress_img_title_input")
            )
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("JPEG Quality", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "${qualityPercent.roundToInt()}%",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Slider(
                        value = qualityPercent,
                        onValueChange = { qualityPercent = it },
                        valueRange = 20f..95f,
                        steps = 14,
                        modifier = Modifier.testTag("compress_img_quality_slider")
                    )
                    Text("Max Resolution", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Original" to 0, "1600px" to 1600, "1080px" to 1080, "720px" to 720).forEach { (label, px) ->
                            FilterChip(
                                selected = maxDimensionPx == px,
                                onClick = { maxDimensionPx = px },
                                shape = CircleShape,
                                label = { Text(label) }
                            )
                        }
                    }
                    if (selectedBitmaps.isNotEmpty()) {
                        Text(
                            text = "Estimated compressed output: ~$estimatedKb KB (${selectedBitmaps.size} file${if (selectedBitmaps.size == 1) "" else "s"})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        if (selectedBitmaps.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoSizeSelectLarge,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Select one or more photos to compress and downscale offline into lightweight JPEGs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    selectedBitmaps.forEachIndexed { idx, (label, bmp) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Box(modifier = Modifier.size(104.dp, 128.dp)) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = label,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                IconButton(
                                    onClick = { selectedBitmaps.removeAt(idx) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(26.dp)
                                        .padding(2.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    onCompressImages(
                        selectedBitmaps.map { it.second },
                        qualityPercent.roundToInt(),
                        maxDimensionPx,
                        outputTitle
                    )
                },
                enabled = selectedBitmaps.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("compress_img_execute_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Compress & Save ${selectedBitmaps.size} Image(s)")
            }
        }
    }
}

@Composable
private fun ImageAndPdfBidirectionalStudio(
    documents: List<DocumentEntity>,
    engine: DocumentEngine,
    onConvertImagesToPdf: (List<Bitmap>, String, Int) -> Unit,
    onExportPdfToImages: (DocumentEntity) -> Unit
) {
    var exportPdfMode by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !exportPdfMode,
                onClick = { exportPdfMode = false },
                shape = CircleShape,
                label = { Text("Images → PDF") },
                modifier = Modifier.testTag("mode_images_to_pdf")
            )
            FilterChip(
                selected = exportPdfMode,
                onClick = { exportPdfMode = true },
                shape = CircleShape,
                label = { Text("PDF → PNG Images") },
                modifier = Modifier.testTag("mode_pdf_to_png")
            )
        }

        if (exportPdfMode) {
            PickPdfStudio(
                documents = documents.filter { it.format == DocumentFormat.PDF || it.format == DocumentFormat.SCAN },
                actionLabel = "Export Pages to PNG",
                helperText = "Render each page of the chosen PDF into a high-resolution PNG stored in your local vault.",
                onProceed = onExportPdfToImages
            )
        } else {
            ImageToPdfStudio(
                documents = documents,
                engine = engine,
                onConvert = onConvertImagesToPdf
            )
        }
    }
}

@Composable
private fun ImageToPdfStudio(
    documents: List<DocumentEntity>,
    engine: DocumentEngine,
    onConvert: (List<Bitmap>, String, Int) -> Unit
) {
    val scope = rememberCoroutineScope()
    val selectedBitmaps = remember { mutableStateListOf<Pair<String, Bitmap>>() }
    var outputTitle by remember { mutableStateOf("Study Plates Packet") }
    var marginPt by remember { mutableIntStateOf(28) }

    val vaultImages = remember(documents) { documents.filter { it.format == DocumentFormat.IMAGE } }

    val multiPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 12)
    ) { uris ->
        scope.launch {
            uris.forEachIndexed { idx, uri ->
                val bmp = engine.decodeBitmapFromUri(uri, 1200)
                if (bmp != null) {
                    selectedBitmaps.add("Photo ${selectedBitmaps.size + idx + 1}" to bmp)
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        multiPhotoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("img_pdf_pick_photos"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pick Photos")
                }
                if (vaultImages.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                vaultImages.forEach { doc ->
                                    val bmp = engine.renderPdfPage(doc.filePath, 0, 1080)
                                    if (bmp != null) selectedBitmaps.add(doc.title to bmp)
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("img_pdf_add_vault_images"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("+ Vault Plates (${vaultImages.size})")
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = outputTitle,
                onValueChange = { outputTitle = it },
                label = { Text("Output PDF Title") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("img_pdf_title_input")
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Page Margins", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Compact" to 14, "Standard" to 28, "Wide" to 48).forEach { (label, pt) ->
                        FilterChip(
                            selected = marginPt == pt,
                            onClick = { marginPt = pt },
                            shape = CircleShape,
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Selected Images (${selectedBitmaps.size})",
                style = MaterialTheme.typography.titleSmall
            )
        }

        if (selectedBitmaps.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = "Tap \"Pick Photos\" or \"+ Vault Plates\" to queue images for A4 PDF conversion.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    selectedBitmaps.forEachIndexed { idx, (label, bmp) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Box(modifier = Modifier.size(112.dp, 140.dp)) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = label,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                IconButton(
                                    onClick = { selectedBitmaps.removeAt(idx) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(28.dp)
                                        .padding(2.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = { onConvert(selectedBitmaps.map { it.second }, outputTitle, marginPt) },
                enabled = selectedBitmaps.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("img_pdf_convert_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(stringResource(R.string.action_convert_to_pdf))
            }
        }
    }
}

@Composable
private fun MergePdfStudio(
    documents: List<DocumentEntity>,
    onMerge: (List<DocumentEntity>, String) -> Unit
) {
    val selectedOrder = remember { mutableStateListOf<DocumentEntity>() }
    var outputTitle by remember { mutableStateOf("Combined Study Packet") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = outputTitle,
                onValueChange = { outputTitle = it },
                label = { Text("Merged PDF Name") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("merge_title_input")
            )
        }

        if (selectedOrder.isNotEmpty()) {
            item {
                Text(
                    text = "Merge Order (${selectedOrder.size} selected • ${selectedOrder.sumOf { it.pageCount }} pages)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            items(selectedOrder.indices.toList(), key = { "ord_${selectedOrder[it].id}" }) { idx ->
                val doc = selectedOrder[idx]
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${idx + 1}.",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = doc.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                if (idx > 0) {
                                    val moved = selectedOrder.removeAt(idx)
                                    selectedOrder.add(idx - 1, moved)
                                }
                            },
                            enabled = idx > 0
                        ) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = {
                                if (idx < selectedOrder.lastIndex) {
                                    val moved = selectedOrder.removeAt(idx)
                                    selectedOrder.add(idx + 1, moved)
                                }
                            },
                            enabled = idx < selectedOrder.lastIndex
                        ) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Select 2 or more documents to merge",
                style = MaterialTheme.typography.titleSmall
            )
        }

        items(documents, key = { it.id }) { doc ->
            val checked = selectedOrder.any { it.id == doc.id }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        if (checked) selectedOrder.removeAll { it.id == doc.id }
                        else selectedOrder.add(doc)
                    }
                    .testTag("merge_pick_${doc.id}"),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = {
                            if (checked) selectedOrder.removeAll { it.id == doc.id }
                            else selectedOrder.add(doc)
                        }
                    )
                    DocumentPreviewIcon(format = doc.format, size = 36.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(doc.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${doc.pageCount} pg • ${formatFileSize(doc.sizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = { onMerge(selectedOrder.toList(), outputTitle) },
                enabled = selectedOrder.size >= 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("merge_execute_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Merge ${selectedOrder.size} Documents into PDF")
            }
        }
    }
}

@Composable
private fun WordToPdfStudio(
    documents: List<DocumentEntity>,
    preselectedDocId: Long?,
    engine: DocumentEngine,
    onConvert: (String, String, Float, Int, String) -> Unit
) {
    val wordDocs = remember(documents) { documents.filter { it.format == DocumentFormat.WORD } }
    var docTitle by remember { mutableStateOf("Field Ecology Notes") }
    var bodyText by remember {
        mutableStateOf(
            "Write or paste your lecture outline, lab summary, or load an existing Word/Text file below. StudyLeaf typesets clean margins and page footers entirely offline."
        )
    }
    var sourceLabel by remember { mutableStateOf("Direct Text Editor") }
    var fontSize by remember { mutableFloatStateOf(13f) }
    var marginPt by remember { mutableIntStateOf(44) }

    LaunchedEffect(preselectedDocId, wordDocs) {
        val target = wordDocs.firstOrNull { it.id == preselectedDocId } ?: wordDocs.firstOrNull()
        if (target != null) {
            docTitle = target.title
            bodyText = engine.readTextOrDocx(File(target.filePath))
            sourceLabel = target.title
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (wordDocs.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Load from Vault", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        wordDocs.forEach { doc ->
                            FilterChip(
                                selected = sourceLabel == doc.title,
                                onClick = {
                                    docTitle = doc.title
                                    bodyText = engine.readTextOrDocx(File(doc.filePath))
                                    sourceLabel = doc.title
                                },
                                shape = CircleShape,
                                label = { Text(doc.title, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = docTitle,
                onValueChange = { docTitle = it },
                label = { Text("Document Title") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("word_pdf_title_input")
            )
        }

        item {
            OutlinedTextField(
                value = bodyText,
                onValueChange = { bodyText = it },
                label = { Text("Document Content") },
                minLines = 8,
                maxLines = 14,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("word_pdf_body_input")
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("11pt" to 11f, "13pt" to 13f, "15pt" to 15f).forEach { (label, sz) ->
                    FilterChip(
                        selected = fontSize == sz,
                        onClick = { fontSize = sz },
                        shape = CircleShape,
                        label = { Text("Font $label") }
                    )
                }
            }
        }

        item {
            Button(
                onClick = { onConvert(docTitle, bodyText, fontSize, marginPt, sourceLabel) },
                enabled = bodyText.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("word_pdf_convert_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.action_convert_to_pdf))
            }
        }
    }
}

@Composable
private fun OptimizePdfStudio(
    documents: List<DocumentEntity>,
    onOptimize: (DocumentEntity, Float, String) -> Unit
) {
    var selectedDoc by remember(documents) { mutableStateOf(documents.firstOrNull()) }
    var presetIndex by remember { mutableIntStateOf(0) }
    val presets = listOf(
        Triple("Eco Screen", 0.72f, "Smallest footprint for phone reading"),
        Triple("Study Balanced", 1.0f, "Crisp text with reduced bitmap weight"),
        Triple("Print Ready", 1.35f, "High density for diagrams & lab figures")
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Choose Optimization Preset", style = MaterialTheme.typography.titleSmall)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEachIndexed { idx, (label, _, desc) ->
                    val chosen = presetIndex == idx
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { presetIndex = idx },
                        shape = RoundedCornerShape(14.dp),
                        color = if (chosen) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(
                            1.dp,
                            if (chosen) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = chosen, onClick = { presetIndex = idx })
                            Column {
                                Text(label, style = MaterialTheme.typography.titleSmall)
                                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        item { Text("Select PDF Document", style = MaterialTheme.typography.titleSmall) }

        items(documents, key = { it.id }) { doc ->
            val selected = selectedDoc?.id == doc.id
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { selectedDoc = doc },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                    if (selected) 1.5.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected, onClick = { selectedDoc = doc })
                    DocumentPreviewIcon(format = doc.format, size = 36.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(doc.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${doc.pageCount} pg • ${formatFileSize(doc.sizeBytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    val doc = selectedDoc ?: return@Button
                    val preset = presets[presetIndex]
                    onOptimize(doc, preset.second, "${doc.title} (${preset.first})")
                },
                enabled = selectedDoc != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("optimize_execute_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Optimize PDF Locally")
            }
        }
    }
}

@Composable
private fun PickPdfStudio(
    documents: List<DocumentEntity>,
    actionLabel: String,
    helperText: String,
    onProceed: (DocumentEntity) -> Unit
) {
    var selectedDoc by remember(documents) { mutableStateOf(documents.firstOrNull()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = helperText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(documents, key = { it.id }) { doc ->
            val selected = selectedDoc?.id == doc.id
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { selectedDoc = doc },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                    if (selected) 1.5.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selected, onClick = { selectedDoc = doc })
                    DocumentPreviewIcon(format = doc.format, size = 36.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(doc.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${doc.pageCount} pg • ${formatFileSize(doc.sizeBytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Button(
                onClick = { selectedDoc?.let(onProceed) },
                enabled = selectedDoc != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("pick_pdf_proceed_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(actionLabel)
            }
        }
    }
}
