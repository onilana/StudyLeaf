package com.example.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.NormalizedPoint

private val SIGNATURE_INK_COLORS = listOf(
    AndroidColor.parseColor("#111827") to "Black",
    AndroidColor.parseColor("#1D4ED8") to "Blue",
    AndroidColor.parseColor("#006C48") to "Forest"
)

@Composable
fun SignaturePadDialog(
    onDismiss: () -> Unit,
    onConfirmSignature: (strokes: List<List<NormalizedPoint>>, colorArgb: Int) -> Unit
) {
    val completedStrokes = remember { mutableStateListOf<List<NormalizedPoint>>() }
    val activeStroke = remember { mutableStateListOf<NormalizedPoint>() }
    var selectedColorArgb by remember { mutableIntStateOf(SIGNATURE_INK_COLORS.first().first) }
    var drawCounter by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Draw,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Draw Your Signature",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Sign inside the pad with your finger or stylus. Processed 100% locally on your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2.15f),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(14.dp))
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { start ->
                                        val w = size.width.toFloat().coerceAtLeast(1f)
                                        val h = size.height.toFloat().coerceAtLeast(1f)
                                        activeStroke.clear()
                                        activeStroke.add(
                                            NormalizedPoint(
                                                (start.x / w).coerceIn(0f, 1f),
                                                (start.y / h).coerceIn(0f, 1f)
                                            )
                                        )
                                        drawCounter++
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val w = size.width.toFloat().coerceAtLeast(1f)
                                        val h = size.height.toFloat().coerceAtLeast(1f)
                                        activeStroke.add(
                                            NormalizedPoint(
                                                (change.position.x / w).coerceIn(0f, 1f),
                                                (change.position.y / h).coerceIn(0f, 1f)
                                            )
                                        )
                                        drawCounter++
                                    },
                                    onDragEnd = {
                                        if (activeStroke.isNotEmpty()) {
                                            completedStrokes.add(activeStroke.toList())
                                            activeStroke.clear()
                                            drawCounter++
                                        }
                                    },
                                    onDragCancel = {
                                        activeStroke.clear()
                                        drawCounter++
                                    }
                                )
                            }
                            .testTag("signature_pad_canvas")
                    ) {
                        // Subtle signature baseline guide
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Read drawCounter to invalidate on every drag point
                            @Suppress("UNUSED_VARIABLE")
                            val tick = drawCounter

                            val guideY = size.height * 0.78f
                            drawLine(
                                color = Color(0xFFE5E7EB),
                                start = Offset(size.width * 0.08f, guideY),
                                end = Offset(size.width * 0.92f, guideY),
                                strokeWidth = 2f
                            )

                            val inkColor = Color(selectedColorArgb)
                            val strokeStyle = Stroke(
                                width = 6.5f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )

                            fun drawNormStroke(pts: List<NormalizedPoint>) {
                                if (pts.isEmpty()) return
                                if (pts.size == 1) {
                                    drawCircle(
                                        color = inkColor,
                                        radius = 3.5f,
                                        center = Offset(pts[0].x * size.width, pts[0].y * size.height)
                                    )
                                } else {
                                    val path = Path()
                                    path.moveTo(pts[0].x * size.width, pts[0].y * size.height)
                                    for (i in 1 until pts.size) {
                                        val prev = pts[i - 1]
                                        val cur = pts[i]
                                        path.quadraticTo(
                                            prev.x * size.width,
                                            prev.y * size.height,
                                            ((prev.x + cur.x) * 0.5f) * size.width,
                                            ((prev.y + cur.y) * 0.5f) * size.height
                                        )
                                    }
                                    val last = pts.last()
                                    path.lineTo(last.x * size.width, last.y * size.height)
                                    drawPath(path = path, color = inkColor, style = strokeStyle)
                                }
                            }

                            completedStrokes.forEach { drawNormStroke(it) }
                            if (activeStroke.isNotEmpty()) drawNormStroke(activeStroke)
                        }

                        if (completedStrokes.isEmpty() && activeStroke.isEmpty()) {
                            Text(
                                text = "Sign here",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF9CA3AF),
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }

                // Ink color + Undo/Clear row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SIGNATURE_INK_COLORS.forEach { (argb, label) ->
                            val picked = selectedColorArgb == argb
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb))
                                    .border(
                                        width = if (picked) 2.5.dp else 1.dp,
                                        color = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColorArgb = argb },
                                contentAlignment = Alignment.Center
                            ) {
                                if (picked) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "$label signature ink",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                if (completedStrokes.isNotEmpty()) {
                                    completedStrokes.removeAt(completedStrokes.lastIndex)
                                    drawCounter++
                                }
                            },
                            enabled = completedStrokes.isNotEmpty(),
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("signature_pad_undo")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo stroke",
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                completedStrokes.clear()
                                activeStroke.clear()
                                drawCounter++
                            },
                            enabled = completedStrokes.isNotEmpty(),
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("signature_pad_clear")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Clear signature",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val normalized = normalizeSignatureBounds(completedStrokes.toList())
                    onConfirmSignature(normalized, selectedColorArgb)
                },
                enabled = completedStrokes.isNotEmpty(),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("signature_pad_apply")
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Place on Page")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Tightly normalizes drawn strokes inside 0.04..0.96 so the signature box fits snugly on the PDF page.
 */
private fun normalizeSignatureBounds(strokes: List<List<NormalizedPoint>>): List<List<NormalizedPoint>> {
    val all = strokes.flatten()
    if (all.isEmpty()) return strokes
    val minX = all.minOf { it.x }
    val maxX = all.maxOf { it.x }
    val minY = all.minOf { it.y }
    val maxY = all.maxOf { it.y }
    val spanX = (maxX - minX).coerceAtLeast(0.08f)
    val spanY = (maxY - minY).coerceAtLeast(0.08f)
    return strokes.map { stroke ->
        stroke.map { pt ->
            NormalizedPoint(
                x = 0.04f + ((pt.x - minX) / spanX) * 0.92f,
                y = 0.06f + ((pt.y - minY) / spanY) * 0.88f
            )
        }
    }
}
