package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.engine.DocxEditableBlock

@Composable
fun DocxPreservedEditorView(
    titleText: String,
    onTitleChange: (String) -> Unit,
    blocks: List<DocxEditableBlock>,
    embeddedImageCount: Int,
    onBlockTextChange: (paragraphIndex: Int, nextText: String) -> Unit,
    horizontalMarginDp: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .widthIn(max = 820.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalMarginDp.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Preservation guarantee badge
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Verified,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                val imgSummary = if (embeddedImageCount > 0) {
                    "$embeddedImageCount embedded image(s), "
                } else ""
                Text(
                    text = "In-Place Word Editor: ${imgSummary}tables, styles, fonts & paragraph alignments are kept intact inside the original .docx file.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Paper Sheet Card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = titleText,
                    onValueChange = onTitleChange,
                    label = { Text("Document Title") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("docx_editor_title_input")
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                if (blocks.isEmpty()) {
                    Text(
                        text = "No editable paragraphs detected in this Word file. Embedded drawings and styles will remain intact.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    blocks.forEach { block ->
                        val blockAlign = when (block.alignment) {
                            "center" -> TextAlign.Center
                            "right", "end" -> TextAlign.End
                            "both", "distribute" -> TextAlign.Justify
                            else -> TextAlign.Start
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (block.hasImage) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Image,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Original Embedded Image (Preserved in place)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }

                            if (block.text.isNotEmpty() || !block.hasImage) {
                                OutlinedTextField(
                                    value = block.text,
                                    onValueChange = { onBlockTextChange(block.paragraphIndex, it) },
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = 15.sp,
                                        lineHeight = 22.sp,
                                        textAlign = blockAlign
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("docx_block_input_${block.paragraphIndex}")
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
