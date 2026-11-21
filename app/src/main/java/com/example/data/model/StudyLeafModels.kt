package com.example.data.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.AnnotationBlue
import com.example.ui.theme.AnnotationGreen
import com.example.ui.theme.AnnotationPink
import com.example.ui.theme.AnnotationYellow
import com.example.ui.theme.DocExcelColor
import com.example.ui.theme.DocImageColor
import com.example.ui.theme.DocPdfColor
import com.example.ui.theme.DocScannerColor
import com.example.ui.theme.DocWordColor

enum class DocumentFormat(val label: String, val extension: String) {
    PDF("PDF", "pdf"),
    WORD("DOCX", "docx"),
    EXCEL("XLSX", "xlsx"),
    IMAGE("PNG", "png"),
    SCAN("SCAN", "pdf");

    fun channelColor(): Color = when (this) {
        PDF -> DocPdfColor
        WORD -> DocWordColor
        EXCEL -> DocExcelColor
        IMAGE -> DocImageColor
        SCAN -> DocScannerColor
    }
}

enum class AnnotationTool(val label: String) {
    PAN("Pan"),
    HIGHLIGHT("Highlight"),
    UNDERLINE("Underline"),
    STRIKETHROUGH("Strike"),
    PEN("Pen"),
    NOTE("Note")
}

enum class AnnotationColorOption(val label: String, val hex: Long, val color: Color) {
    YELLOW("Amber", 0xFFFFD166, AnnotationYellow),
    GREEN("Mint", 0xFF74C69D, AnnotationGreen),
    BLUE("Sky", 0xFF4EA8DE, AnnotationBlue),
    PINK("Coral", 0xFFF28482, AnnotationPink)
}

enum class ScannerFilterMode(val label: String) {
    ORIGINAL("Original"),
    BOTANICAL_CRISP("Crisp Doc"),
    GRAYSCALE("Grayscale"),
    BW_PRINT("B&W Print")
}

enum class ConversionToolType(
    val title: String,
    val shortBadge: String,
    val formatChannel: DocumentFormat
) {
    SCAN_DOCUMENT("Scan Document", "SCAN → PDF", DocumentFormat.SCAN),
    IMAGE_TO_PDF("Image ⇄ PDF", "IMG ⇄ PDF", DocumentFormat.IMAGE),
    MERGE_PDF("Merge PDFs", "MERGE", DocumentFormat.PDF),
    WORD_TO_PDF("Word & Text to PDF", "DOC → PDF", DocumentFormat.WORD),
    EXCEL_CONVERTER("Excel ⇄ Word / PDF", "XLSX ⇄ DOC", DocumentFormat.EXCEL),
    COMPRESS_IMAGE("Image Compressor", "COMPRESS", DocumentFormat.IMAGE),
    SPLIT_REORDER("Split & Reorder", "PAGES", DocumentFormat.PDF),
    OPTIMIZE_PDF("Optimize PDF", "OPTIMIZE", DocumentFormat.PDF),
    PDF_TO_IMAGE("PDF to PNG", "PDF → PNG", DocumentFormat.IMAGE)
}

enum class ThemePreference {
    SYSTEM, LIGHT, DARK
}

enum class SortOrder(val label: String) {
    RECENT("Recent"),
    NAME("Name"),
    SIZE("Size")
}

enum class FileViewMode {
    LIST, GRID
}

data class NormalizedPoint(val x: Float, val y: Float)

data class PageAnnotation(
    val id: Long = 0L,
    val documentId: Long,
    val pageIndex: Int,
    val tool: AnnotationTool,
    val colorHex: Long,
    val points: List<NormalizedPoint>,
    val noteText: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

fun encodePoints(points: List<NormalizedPoint>): String =
    points.joinToString(";") { "${it.x},${it.y}" }

fun decodePoints(raw: String): List<NormalizedPoint> {
    if (raw.isBlank()) return emptyList()
    return raw.split(";").mapNotNull { pair ->
        val parts = pair.split(",")
        if (parts.size == 2) {
            val x = parts[0].toFloatOrNull()
            val y = parts[1].toFloatOrNull()
            if (x != null && y != null) NormalizedPoint(x, y) else null
        } else null
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format("%.2f MB", mb)
}
