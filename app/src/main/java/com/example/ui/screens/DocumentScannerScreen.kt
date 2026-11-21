package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.engine.DocumentEngine
import com.example.data.engine.PdfSeedGenerator
import com.example.data.model.NormalizedPoint
import com.example.data.model.ScannerFilterMode
import com.example.ui.theme.DocScannerColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot

@Composable
fun DocumentScannerScreen(
    engine: DocumentEngine,
    onWarpPreview: suspend (Bitmap, List<NormalizedPoint>, ScannerFilterMode) -> Bitmap,
    onSaveScannedPdf: (List<Bitmap>, String) -> Unit,
    onShowMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rectifiedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRectifying by remember { mutableStateOf(false) }
    var showingPreview by remember { mutableStateOf(false) }
    var filterMode by remember { mutableStateOf(ScannerFilterMode.BOTANICAL_CRISP) }
    var outputTitle by remember { mutableStateOf("Scanned Lecture Notes") }

    // Four interactive corner handles in normalized [0..1] coordinates matching the skewed sheet
    val corners = remember {
        mutableStateListOf(
            NormalizedPoint(0.14f, 0.14f),
            NormalizedPoint(0.88f, 0.11f),
            NormalizedPoint(0.92f, 0.88f),
            NormalizedPoint(0.10f, 0.85f)
        )
    }
    var draggingHandleIndex by remember { mutableIntStateOf(-1) }
    val batchPages = remember { mutableStateListOf<Bitmap>() }

    // Load default skewed desk sheet immediately so users can test perspective alignment right away
    LaunchedEffect(Unit) {
        if (sourceBitmap == null) {
            sourceBitmap = withContext(Dispatchers.Default) {
                PdfSeedGenerator.generateSampleSkewedDeskBitmap()
            }
        }
    }

    // Recompute warped preview whenever filter changes in preview mode
    LaunchedEffect(showingPreview, filterMode, sourceBitmap) {
        val src = sourceBitmap ?: return@LaunchedEffect
        if (showingPreview) {
            isRectifying = true
            rectifiedBitmap = onWarpPreview(src, corners.toList(), filterMode)
            isRectifying = false
        }
    }

    // Real Camera capture via FileProvider + TakePicture
    var pendingCaptureFile by remember { mutableStateOf<File?>(null) }
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCaptureFile
        if (success && file != null && file.exists()) {
            scope.launch {
                val decoded = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.absolutePath) }
                if (decoded != null) {
                    sourceBitmap = decoded
                    showingPreview = false
                    corners[0] = NormalizedPoint(0.10f, 0.10f)
                    corners[1] = NormalizedPoint(0.90f, 0.10f)
                    corners[2] = NormalizedPoint(0.90f, 0.90f)
                    corners[3] = NormalizedPoint(0.10f, 0.90f)
                }
            }
        }
    }

    fun launchCameraCapture() {
        val file = engine.createTempScanFile()
        pendingCaptureFile = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        takePictureLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraCapture()
        } else {
            onShowMessage("Camera permission denied. You can still import photos or use the desk canvas.")
        }
    }

    // Zero-permission Android Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val decoded = engine.decodeBitmapFromUri(uri, maxDim = 1400)
                if (decoded != null) {
                    sourceBitmap = decoded
                    showingPreview = false
                    corners[0] = NormalizedPoint(0.08f, 0.08f)
                    corners[1] = NormalizedPoint(0.92f, 0.08f)
                    corners[2] = NormalizedPoint(0.92f, 0.92f)
                    corners[3] = NormalizedPoint(0.08f, 0.92f)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101312))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Scanner Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("scanner_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = Color.White
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.scanner_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = if (showingPreview) "Optical filter & batch review" else "Perspective corner alignment",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFA5D0B9)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = {
                        val hasPerm = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPerm) launchCameraCapture()
                        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("scanner_camera_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = stringResource(R.string.scanner_capture_camera),
                        tint = Color.White
                    )
                }
                IconButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("scanner_pick_photo_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddPhotoAlternate,
                        contentDescription = stringResource(R.string.scanner_pick_photo),
                        tint = Color.White
                    )
                }
            }
        }

        // Main Perspective Canvas or Rectified Output Preview
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            val src = sourceBitmap
            if (src == null) {
                CircularProgressIndicator(color = DocScannerColor)
            } else if (!showingPreview) {
                val aspect = (src.width.toFloat() / src.height.toFloat()).coerceIn(0.55f, 1.5f)
                var boxSize by remember { mutableStateOf(IntSize.Zero) }

                Box(
                    modifier = Modifier
                        .aspectRatio(aspect)
                        .clip(RoundedCornerShape(14.dp))
                        .onSizeChanged { boxSize = it }
                        .pointerInput(boxSize) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    if (boxSize.width == 0 || boxSize.height == 0) return@detectDragGestures
                                    var bestIdx = -1
                                    var bestDist = Float.MAX_VALUE
                                    corners.forEachIndexed { idx, pt ->
                                        val cx = pt.x * boxSize.width
                                        val cy = pt.y * boxSize.height
                                        val dist = hypot(start.x - cx, start.y - cy)
                                        if (dist < bestDist && dist < 130f) {
                                            bestDist = dist
                                            bestIdx = idx
                                        }
                                    }
                                    draggingHandleIndex = bestIdx
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val idx = draggingHandleIndex
                                    if (idx in 0..3 && boxSize.width > 0 && boxSize.height > 0) {
                                        val cur = corners[idx]
                                        val nx = (cur.x + dragAmount.x / boxSize.width).coerceIn(0.03f, 0.97f)
                                        val ny = (cur.y + dragAmount.y / boxSize.height).coerceIn(0.03f, 0.97f)
                                        corners[idx] = NormalizedPoint(nx, ny)
                                    }
                                },
                                onDragEnd = { draggingHandleIndex = -1 },
                                onDragCancel = { draggingHandleIndex = -1 }
                            )
                        }
                        .testTag("scanner_perspective_canvas")
                ) {
                    Image(
                        bitmap = src.asImageBitmap(),
                        contentDescription = "Document scan source",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )

                    // StudyLeaf Spec: Semi-transparent dark scrim rgba(0,0,0,0.55) + Scanner Teal (#0E8388) polygon + 24dp circular handles
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val pts = corners.map { Offset(it.x * w, it.y * h) }

                        val scrimPath = Path().apply {
                            fillType = PathFillType.EvenOdd
                            addRect(androidx.compose.ui.geometry.Rect(0f, 0f, w, h))
                            moveTo(pts[0].x, pts[0].y)
                            lineTo(pts[1].x, pts[1].y)
                            lineTo(pts[2].x, pts[2].y)
                            lineTo(pts[3].x, pts[3].y)
                            close()
                        }
                        drawPath(scrimPath, color = Color.Black.copy(alpha = 0.55f))

                        val quadPath = Path().apply {
                            moveTo(pts[0].x, pts[0].y)
                            lineTo(pts[1].x, pts[1].y)
                            lineTo(pts[2].x, pts[2].y)
                            lineTo(pts[3].x, pts[3].y)
                            close()
                        }
                        drawPath(
                            path = quadPath,
                            color = DocScannerColor,
                            style = Stroke(width = 3.dp.toPx())
                        )

                        // Rule-of-thirds alignment grid inside quad
                        for (t in listOf(0.33f, 0.66f)) {
                            val top = Offset(pts[0].x + (pts[1].x - pts[0].x) * t, pts[0].y + (pts[1].y - pts[0].y) * t)
                            val bot = Offset(pts[3].x + (pts[2].x - pts[3].x) * t, pts[3].y + (pts[2].y - pts[3].y) * t)
                            drawLine(DocScannerColor.copy(alpha = 0.4f), top, bot, strokeWidth = 1.dp.toPx())

                            val left = Offset(pts[0].x + (pts[3].x - pts[0].x) * t, pts[0].y + (pts[3].y - pts[0].y) * t)
                            val right = Offset(pts[1].x + (pts[2].x - pts[1].x) * t, pts[1].y + (pts[2].y - pts[1].y) * t)
                            drawLine(DocScannerColor.copy(alpha = 0.4f), left, right, strokeWidth = 1.dp.toPx())
                        }

                        // 24dp circular drag targets with high-contrast inner rings
                        val outerRadius = 12.dp.toPx()
                        val innerRadius = 6.dp.toPx()
                        pts.forEachIndexed { idx, center ->
                            val isActive = idx == draggingHandleIndex
                            drawCircle(
                                color = if (isActive) Color(0xFF92F7C3) else DocScannerColor,
                                radius = if (isActive) outerRadius * 1.25f else outerRadius,
                                center = center
                            )
                            drawCircle(
                                color = Color.White,
                                radius = innerRadius,
                                center = center,
                                style = Stroke(width = 2.5.dp.toPx())
                            )
                        }
                    }
                }
            } else {
                // Rectified Enhanced Preview
                if (isRectifying || rectifiedBitmap == null) {
                    CircularProgressIndicator(color = DocScannerColor)
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        shadowElevation = 6.dp,
                        modifier = Modifier.aspectRatio(0.71f)
                    ) {
                        Image(
                            bitmap = rectifiedBitmap!!.asImageBitmap(),
                            contentDescription = "Perspective-corrected scan preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // Floating Scanner Controls Sheet (Level 3 elevation)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!showingPreview) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CropFree,
                            contentDescription = null,
                            tint = DocScannerColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.scanner_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                corners[0] = NormalizedPoint(0.14f, 0.14f)
                                corners[1] = NormalizedPoint(0.88f, 0.11f)
                                corners[2] = NormalizedPoint(0.92f, 0.88f)
                                corners[3] = NormalizedPoint(0.10f, 0.85f)
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("scanner_reset_corners"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Auto Fit")
                        }
                        Button(
                            onClick = { showingPreview = true },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("scanner_apply_crop_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.scanner_apply_crop))
                        }
                    }
                } else {
                    // Optical Enhancement Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScannerFilterMode.entries.forEach { mode ->
                            FilterChip(
                                selected = filterMode == mode,
                                onClick = { filterMode = mode },
                                shape = CircleShape,
                                label = { Text(mode.label, style = MaterialTheme.typography.labelMedium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier.testTag("scanner_filter_${mode.name.lowercase()}")
                            )
                        }
                    }

                    OutlinedTextField(
                        value = outputTitle,
                        onValueChange = { outputTitle = it },
                        label = { Text("Document Title") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("scanner_title_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showingPreview = false },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("scanner_recrop_button"),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Adjust Crop")
                        }
                        FilledTonalButton(
                            onClick = {
                                rectifiedBitmap?.let {
                                    batchPages.add(it)
                                    showingPreview = false
                                    onShowMessage("Added page ${batchPages.size} to scan batch")
                                }
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("scanner_add_page_button"),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Page (${batchPages.size})")
                        }
                        Button(
                            onClick = {
                                val pagesToSave = buildList {
                                    addAll(batchPages)
                                    rectifiedBitmap?.let { add(it) }
                                }
                                if (pagesToSave.isNotEmpty()) {
                                    onSaveScannedPdf(pagesToSave, outputTitle)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("scanner_save_pdf_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("Save PDF")
                        }
                    }
                }
            }
        }
    }
}
