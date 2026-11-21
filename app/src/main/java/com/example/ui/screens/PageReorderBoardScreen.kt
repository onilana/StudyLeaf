package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.engine.DocumentEngine
import com.example.data.local.DocumentEntity
import com.example.ui.components.StudyLeafWindowSize

private data class ReorderPageSlot(
    val uid: Int,
    val originalPageIndex: Int
)

@Composable
fun PageReorderBoardScreen(
    document: DocumentEntity,
    windowSize: StudyLeafWindowSize,
    engine: DocumentEngine,
    onSaveReordered: (List<Int>, String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var outputTitle by remember(document.id) { mutableStateOf("${document.title} (Edited)") }
    val slots = remember(document.id) { mutableStateListOf<ReorderPageSlot>() }
    var nextUid by remember(document.id) { mutableStateOf(100) }
    val thumbCache = remember(document.id) { mutableMapOf<Int, Bitmap?>() }

    LaunchedEffect(document.id, document.filePath) {
        val count = engine.getPdfPageCount(document.filePath).coerceAtLeast(1)
        slots.clear()
        for (i in 0 until count) {
            slots.add(ReorderPageSlot(uid = i, originalPageIndex = i))
        }
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
                        text = "Page Reorder Board",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${slots.size} page${if (slots.size == 1) "" else "s"} in new order",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
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

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.74f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp!!.asImageBitmap(),
                                    contentDescription = "Page ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
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
                                    text = "#${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
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
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Move earlier", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = {
                                    slots.add(index + 1, ReorderPageSlot(uid = nextUid++, originalPageIndex = slot.originalPageIndex))
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate page", modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = {
                                    if (slots.size > 1) slots.removeAt(index)
                                },
                                enabled = slots.size > 1,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    Icons.Filled.DeleteOutline,
                                    contentDescription = "Remove page",
                                    tint = if (slots.size > 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
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
                                modifier = Modifier.size(34.dp)
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
                    .padding(16.dp),
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
                    onClick = { onSaveReordered(slots.map { it.originalPageIndex }, outputTitle) },
                    enabled = slots.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("reorder_save_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Save Reordered PDF (${slots.size} pages)")
                }
            }
        }
    }
}
