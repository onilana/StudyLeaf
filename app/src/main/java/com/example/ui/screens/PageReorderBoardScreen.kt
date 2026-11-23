package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.engine.DocumentEngine
import com.example.data.engine.PdfPageSpec
import com.example.data.engine.parsePageRangeSpec
import com.example.data.local.DocumentEntity
import com.example.ui.components.StudyLeafWindowSize
import com.studyleaf.R

private data class ReorderPageSlot(
    val uid: Int,
    val originalPageIndex: Int,
    val rotationDegrees: Int = 0
)

@Composable
fun PageReorderBoardScreen(
    document: DocumentEntity,
    windowSize: StudyLeafWindowSize,
    engine: DocumentEngine,
    onSaveReordered: (List<PdfPageSpec>, String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var outputTitle by remember(document.id) { mutableStateOf("${document.title} (Organized)") }
    var totalSourcePages by remember(document.id) { mutableIntStateOf(document.pageCount.coerceAtLeast(1)) }
    val slots = remember(document.id) { mutableStateListOf<ReorderPageSlot>() }
    var selectedUids by remember(document.id) { mutableStateOf<Set<Int>>(emptySet()) }
    var rangeInput by remember(document.id) { mutableStateOf("") }
    var rangeError by remember(document.id) { mutableStateOf<String?>(null) }
    var nextUid by remember(document.id) { mutableIntStateOf(1000) }
    val thumbCache = remember(document.id) { mutableMapOf<Int, Bitmap?>() }

    fun resetOriginalOrder(count: Int) {
        slots.clear()
        for (i in 0 until count) {
            slots.add(ReorderPageSlot(uid = i, originalPageIndex = i, rotationDegrees = 0))
        }
        selectedUids = emptySet()
        rangeInput = ""
        rangeError = null
    }

    LaunchedEffect(document.id, document.filePath) {
        val count = engine.getPdfPageCount(document.filePath).coerceAtLeast(1)
        totalSourcePages = count
        resetOriginalOrder(count)
    }

    val columns = when (windowSize) {
        StudyLeafWindowSize.COMPACT -> 2
        StudyLeafWindowSize.MEDIUM -> 3
        StudyLeafWindowSize.EXPANDED -> 4
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp
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
                        .testTag("reorder_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Organize, Split & Rotate",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${slots.size} page${if (slots.size == 1) "" else "s"} ready • 100% local",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                IconButton(
                    onClick = { resetOriginalOrder(totalSourcePages) },
                    modifier = Modifier.testTag("reorder_reset_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reset pages")
                }
            }
        }

        // PenguinPDF-Style Range Split & Multi-Select Action Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = rangeInput,
                        onValueChange = {
                            rangeInput = it
                            rangeError = null
                        },
                        placeholder = {
                            Text("Split range (e.g. 1-3, 5, 8-)", style = MaterialTheme.typography.bodySmall)
                        },
                        singleLine = true,
                        isError = rangeError != null,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("reorder_range_input")
                    )
                    FilledTonalButton(
                        onClick = {
                            val parsed = parsePageRangeSpec(rangeInput, totalSourcePages)
                            if (parsed.isEmpty()) {
                                rangeError = "Enter pages 1–$totalSourcePages"
                            } else {
                                slots.clear()
                                parsed.forEach { pIdx ->
                                    slots.add(ReorderPageSlot(uid = nextUid++, originalPageIndex = pIdx))
                                }
                                selectedUids = emptySet()
                                rangeError = null
                            }
                        },
                        enabled = rangeInput.isNotBlank(),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier.testTag("reorder_apply_range_button")
                    ) {
                        Icon(Icons.Filled.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Split Range", style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (rangeError != null) {
                    Text(
                        text = rangeError!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedUids.size == slots.size && slots.isNotEmpty(),
                        onClick = {
                            selectedUids = if (selectedUids.size == slots.size) emptySet() else slots.map { it.uid }.toSet()
                        },
                        label = {
                            Text(
                                text = if (selectedUids.size == slots.size && slots.isNotEmpty()) "Deselect All" else "Select All",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        leadingIcon = { Icon(Icons.Filled.SelectAll, contentDescription = null, modifier = Modifier.size(15.dp)) },
                        modifier = Modifier.testTag("reorder_select_all_chip")
                    )

                    if (selectedUids.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = {
                                val keep = slots.filter { it.uid in selectedUids }
                                if (keep.isNotEmpty()) {
                                    slots.clear()
                                    slots.addAll(keep)
                                    selectedUids = emptySet()
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("reorder_extract_selected_button")
                        ) {
                            Text("Extract (${selectedUids.size})", style = MaterialTheme.typography.labelSmall)
                        }

                        FilledTonalButton(
                            onClick = {
                                val remaining = slots.filterNot { it.uid in selectedUids }
                                if (remaining.isNotEmpty()) {
                                    slots.clear()
                                    slots.addAll(remaining)
                                    selectedUids = emptySet()
                                }
                            },
                            enabled = selectedUids.size < slots.size,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("reorder_delete_selected_button")
                        ) {
                            Text("Delete (${selectedUids.size})", style = MaterialTheme.typography.labelSmall)
                        }

                        FilledTonalButton(
                            onClick = {
                                for (i in slots.indices) {
                                    val s = slots[i]
                                    if (s.uid in selectedUids) {
                                        slots[i] = s.copy(rotationDegrees = (s.rotationDegrees + 90) % 360)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("reorder_rotate_selected_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Rotate 90°", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(slots, key = { _, item -> item.uid }) { index, slot ->
                var bmp by remember(slot.originalPageIndex) {
                    mutableStateOf(thumbCache[slot.originalPageIndex])
                }
                LaunchedEffect(slot.originalPageIndex) {
                    if (bmp == null) {
                        val rendered = engine.renderPdfPage(document.filePath, slot.originalPageIndex, 360)
                        thumbCache[slot.originalPageIndex] = rendered
                        bmp = rendered
                    }
                }
                val isSelected = slot.uid in selectedUids

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.74f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .clickable {
                                    selectedUids = if (isSelected) selectedUids - slot.uid else selectedUids + slot.uid
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp!!.asImageBitmap(),
                                    contentDescription = "Page ${index + 1}",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { rotationZ = slot.rotationDegrees.toFloat() }
                                )
                            } else {
                                Text("P.${slot.originalPageIndex + 1}", style = MaterialTheme.typography.labelMedium)
                            }
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                            ) {
                                Text(
                                    text = "#${index + 1}" + if (slot.rotationDegrees != 0) " · ${slot.rotationDegrees}°" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                            Icon(
                                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                                contentDescription = if (isSelected) "Page selected" else "Select page",
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF9CA3AF),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(20.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        val moved = slots.removeAt(index)
                                        slots.add(index - 1, moved)
                                    }
                                },
                                enabled = index > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Move earlier", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = {
                                    slots[index] = slot.copy(rotationDegrees = (slot.rotationDegrees + 90) % 360)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate 90 degrees", modifier = Modifier.size(17.dp))
                            }
                            IconButton(
                                onClick = {
                                    slots.add(index + 1, ReorderPageSlot(uid = nextUid++, originalPageIndex = slot.originalPageIndex, rotationDegrees = slot.rotationDegrees))
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate page", modifier = Modifier.size(15.dp))
                            }
                            IconButton(
                                onClick = {
                                    if (slots.size > 1) {
                                        selectedUids = selectedUids - slot.uid
                                        slots.removeAt(index)
                                    }
                                },
                                enabled = slots.size > 1,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Filled.DeleteOutline,
                                    contentDescription = "Remove page",
                                    tint = if (slots.size > 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (index < slots.lastIndex) {
                                        val moved = slots.removeAt(index)
                                        slots.add(index + 1, moved)
                                    }
                                },
                                enabled = index < slots.lastIndex,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Move later", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        // Bottom Confirmation Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = outputTitle,
                    onValueChange = { outputTitle = it },
                    label = { Text("Save As PDF Title") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reorder_title_input")
                )
                Button(
                    onClick = {
                        val specs = slots.map { PdfPageSpec(it.originalPageIndex, it.rotationDegrees) }
                        onSaveReordered(specs, outputTitle)
                    },
                    enabled = slots.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("reorder_save_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Save Organized PDF (${slots.size} page${if (slots.size == 1) "" else "s"})")
                }
            }
        }
    }
}
