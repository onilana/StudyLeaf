package com.example.data.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.example.data.model.DocumentFormat
import com.example.data.model.NormalizedPoint
import com.example.data.model.ScanEnhancementSettings
import com.example.data.model.ScannerFilterMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class GeneratedFileInfo(
    val file: File,
    val pageCount: Int,
    val sizeBytes: Long
)

class DocumentEngine(private val context: Context) {

    private val documentsDir: File
        get() = File(context.filesDir, "documents").apply { if (!exists()) mkdirs() }

    private val scansCacheDir: File
        get() = File(context.cacheDir, "scans").apply { if (!exists()) mkdirs() }

    fun createTempScanFile(): File =
        File(scansCacheDir, "scan_${System.currentTimeMillis()}.jpg")

    private fun safeFileName(title: String, extension: String): String {
        val cleaned = title.trim()
            .replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
            .replace(Regex("\\s+"), "_")
            .take(48)
            .ifBlank { "StudyLeaf_Doc" }
        return "${cleaned}_${System.currentTimeMillis()}.$extension"
    }

    suspend fun renderPdfPage(
        filePath: String,
        pageIndex: Int,
        targetWidthPx: Int = 1080
    ): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) return@withContext null

        val lower = file.name.lowercase()
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp")) {
            return@withContext decodeScaledBitmap(filePath, targetWidthPx)
        }
        if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".docx") || lower.endsWith(".doc")) {
            val text = readTextOrDocx(file)
            return@withContext renderTextPreviewBitmap(file.nameWithoutExtension, text, targetWidthPx)
        }
        if (lower.endsWith(".xlsx") || lower.endsWith(".csv") || lower.endsWith(".xls")) {
            val rows = ExcelAndImageEngine.readSpreadsheetRows(file)
            val displayRows = if (rows.isNotEmpty()) {
                rows
            } else {
                val extracted = extractReadableStrings(file)
                if (extracted.isNotEmpty()) {
                    listOf(listOf("Row", "Extracted Spreadsheet Content")) +
                        extracted.take(40).mapIndexed { idx, s -> listOf("${idx + 1}", s) }
                } else {
                    listOf(listOf("File", file.name), listOf("Info", "Opened in StudyLeaf Vault"))
                }
            }
            return@withContext ExcelAndImageEngine.renderSpreadsheetPreviewBitmap(
                title = file.nameWithoutExtension.replace("_", " "),
                rows = displayRows,
                targetWidth = targetWidthPx
            )
        }

        var descriptor: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(descriptor)
            if (renderer.pageCount == 0) return@withContext null
            val safeIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
            val page = renderer.openPage(safeIndex)
            try {
                val aspect = page.height.toFloat() / max(1, page.width).toFloat()
                val width = targetWidthPx.coerceIn(180, 1800)
                val height = max(180, (width * aspect).roundToInt())
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } finally {
                page.close()
            }
        } catch (_: Exception) {
            null
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { descriptor?.close() } catch (_: Exception) {}
        }
    }

    suspend fun getPdfPageCount(filePath: String): Int = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) return@withContext 1
        if (!file.name.lowercase().endsWith(".pdf")) return@withContext 1
        var descriptor: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(descriptor)
            max(1, renderer.pageCount)
        } catch (_: Exception) {
            1
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { descriptor?.close() } catch (_: Exception) {}
        }
    }

    suspend fun mergePdfs(
        sourcePaths: List<String>,
        outputTitle: String
    ): GeneratedFileInfo = withContext(Dispatchers.IO) {
        val outFile = File(documentsDir, safeFileName(outputTitle, "pdf"))
        val pdfDoc = PdfDocument()
        var totalPages = 0
        try {
            for (path in sourcePaths) {
                val srcFile = File(path)
                if (!srcFile.exists()) continue
                if (srcFile.name.lowercase().endsWith(".pdf")) {
                    ParcelFileDescriptor.open(srcFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        PdfRenderer(pfd).use { renderer ->
                            for (i in 0 until renderer.pageCount) {
                                renderer.openPage(i).use { page ->
                                    val w = 595
                                    val h = max(400, (w * page.height.toFloat() / max(1, page.width)).roundToInt())
                                    val bmp = Bitmap.createBitmap(w * 2, h * 2, Bitmap.Config.ARGB_8888)
                                    Canvas(bmp).drawColor(Color.WHITE)
                                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                    val info = PdfDocument.PageInfo.Builder(w, h, totalPages + 1).create()
                                    val outPage = pdfDoc.startPage(info)
                                    outPage.canvas.drawBitmap(bmp, null, Rect(0, 0, w, h), null)
                                    pdfDoc.finishPage(outPage)
                                    bmp.recycle()
                                    totalPages++
                                }
                            }
                        }
                    }
                } else {
                    val bmp = renderPdfPage(path, 0, 1190)
                    if (bmp != null) {
                        val w = 595
                        val h = max(400, (w * bmp.height.toFloat() / max(1, bmp.width)).roundToInt())
                        val info = PdfDocument.PageInfo.Builder(w, h, totalPages + 1).create()
                        val outPage = pdfDoc.startPage(info)
                        outPage.canvas.drawBitmap(bmp, null, Rect(0, 0, w, h), null)
                        pdfDoc.finishPage(outPage)
                        bmp.recycle()
                        totalPages++
                    }
                }
            }
            if (totalPages == 0) {
                val info = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                val page = pdfDoc.startPage(info)
                page.canvas.drawColor(Color.WHITE)
                pdfDoc.finishPage(page)
                totalPages = 1
            }
            FileOutputStream(outFile).use { pdfDoc.writeTo(it) }
        } finally {
            pdfDoc.close()
        }
        GeneratedFileInfo(outFile, totalPages, outFile.length())
    }

    suspend fun reorderAndExtractPdf(
        sourcePath: String,
        orderedZeroBasedPages: List<Int>,
        outputTitle: String
    ): GeneratedFileInfo = withContext(Dispatchers.IO) {
        val outFile = File(documentsDir, safeFileName(outputTitle, "pdf"))
        val srcFile = File(sourcePath)
        val pdfDoc = PdfDocument()
        var written = 0
        try {
            ParcelFileDescriptor.open(srcFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    for (idx in orderedZeroBasedPages) {
                        if (idx !in 0 until renderer.pageCount) continue
                        renderer.openPage(idx).use { page ->
                            val w = 595
                            val h = max(400, (w * page.height.toFloat() / max(1, page.width)).roundToInt())
                            val bmp = Bitmap.createBitmap(w * 2, h * 2, Bitmap.Config.ARGB_8888)
                            Canvas(bmp).drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            val info = PdfDocument.PageInfo.Builder(w, h, written + 1).create()
                            val outPage = pdfDoc.startPage(info)
                            outPage.canvas.drawBitmap(bmp, null, Rect(0, 0, w, h), null)
                            pdfDoc.finishPage(outPage)
                            bmp.recycle()
                            written++
                        }
                    }
                }
            }
            FileOutputStream(outFile).use { pdfDoc.writeTo(it) }
        } finally {
            pdfDoc.close()
        }
        GeneratedFileInfo(outFile, max(1, written), outFile.length())
    }

    suspend fun optimizePdf(
        sourcePath: String,
        qualityScale: Float,
        outputTitle: String
    ): GeneratedFileInfo = withContext(Dispatchers.IO) {
        val outFile = File(documentsDir, safeFileName(outputTitle, "pdf"))
        val srcFile = File(sourcePath)
        val pdfDoc = PdfDocument()
        var written = 0
        val renderScale = qualityScale.coerceIn(0.6f, 1.5f)
        try {
            ParcelFileDescriptor.open(srcFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val w = 595
                            val h = max(400, (w * page.height.toFloat() / max(1, page.width)).roundToInt())
                            val bmpW = (w * renderScale).roundToInt().coerceAtLeast(320)
                            val bmpH = (h * renderScale).roundToInt().coerceAtLeast(420)
                            val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.RGB_565)
                            Canvas(bmp).drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val info = PdfDocument.PageInfo.Builder(w, h, written + 1).create()
                            val outPage = pdfDoc.startPage(info)
                            outPage.canvas.drawBitmap(bmp, null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
                            pdfDoc.finishPage(outPage)
                            bmp.recycle()
                            written++
                        }
                    }
                }
            }
            FileOutputStream(outFile).use { pdfDoc.writeTo(it) }
        } finally {
            pdfDoc.close()
        }
        GeneratedFileInfo(outFile, max(1, written), outFile.length())
    }

    suspend fun imagesToPdf(
        bitmaps: List<Bitmap>,
        outputTitle: String,
        marginPt: Int = 28
    ): GeneratedFileInfo = withContext(Dispatchers.IO) {
        val outFile = File(documentsDir, safeFileName(outputTitle, "pdf"))
        val pdfDoc = PdfDocument()
        val pageW = 595
        val pageH = 842
        try {
            bitmaps.forEachIndexed { index, bitmap ->
                val info = PdfDocument.PageInfo.Builder(pageW, pageH, index + 1).create()
                val page = pdfDoc.startPage(info)
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)
                val availW = (pageW - marginPt * 2).toFloat()
                val availH = (pageH - marginPt * 2).toFloat()
                val scale = min(availW / max(1, bitmap.width), availH / max(1, bitmap.height))
                val drawW = (bitmap.width * scale).roundToInt()
                val drawH = (bitmap.height * scale).roundToInt()
                val left = (pageW - drawW) / 2
                val top = (pageH - drawH) / 2
                canvas.drawBitmap(
                    bitmap,
                    null,
                    Rect(left, top, left + drawW, top + drawH),
                    Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                )
                pdfDoc.finishPage(page)
            }
            FileOutputStream(outFile).use { pdfDoc.writeTo(it) }
        } finally {
            pdfDoc.close()
        }
        GeneratedFileInfo(outFile, max(1, bitmaps.size), outFile.length())
    }

    suspend fun textToPdf(
        title: String,
        bodyText: String,
        fontSizePt: Float = 13f,
        marginPt: Int = 44
    ): GeneratedFileInfo = withContext(Dispatchers.IO) {
        val outFile = File(documentsDir, safeFileName(title, "pdf"))
        val pdfDoc = PdfDocument()
        val pageW = 595
        val pageH = 842
        val contentW = (pageW - marginPt * 2).toFloat()

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#012D1D")
            textSize = 20f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#006C48")
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A1C1E")
            textSize = fontSizePt
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val dividerPaint = Paint().apply {
            color = Color.parseColor("#C1C8C2")
            strokeWidth = 1f
        }

        val wrappedLines = mutableListOf<String>()
        bodyText.split("\n").forEach { paragraph ->
            if (paragraph.isBlank()) {
                wrappedLines.add("")
            } else {
                val words = paragraph.split(Regex("\\s+"))
                var current = StringBuilder()
                for (word in words) {
                    val candidate = if (current.isEmpty()) word else "$current $word"
                    if (bodyPaint.measureText(candidate) <= contentW) {
                        current = StringBuilder(candidate)
                    } else {
                        if (current.isNotEmpty()) wrappedLines.add(current.toString())
                        current = StringBuilder(word)
                    }
                }
                if (current.isNotEmpty()) wrappedLines.add(current.toString())
            }
        }
        if (wrappedLines.isEmpty()) wrappedLines.add(" ")

        val lineSpacing = fontSizePt * 1.48f
        var lineIdx = 0
        var pageNum = 0

        try {
            while (lineIdx < wrappedLines.size) {
                pageNum++
                val info = PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create()
                val page = pdfDoc.startPage(info)
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)

                var y = marginPt.toFloat() + 14f
                if (pageNum == 1) {
                    canvas.drawText("STUDYLEAF ON-DEVICE TYPESET", marginPt.toFloat(), y, metaPaint)
                    y += 24f
                    canvas.drawText(title.take(48), marginPt.toFloat(), y, titlePaint)
                    y += 14f
                    canvas.drawLine(marginPt.toFloat(), y, pageW - marginPt.toFloat(), y, dividerPaint)
                    y += 26f
                }

                val maxY = pageH - marginPt - 20f
                while (lineIdx < wrappedLines.size && y <= maxY) {
                    val line = wrappedLines[lineIdx]
                    if (line.isNotEmpty()) {
                        canvas.drawText(line, marginPt.toFloat(), y, bodyPaint)
                    }
                    y += lineSpacing
                    lineIdx++
                }

                canvas.drawText(
                    "Page $pageNum  •  StudyLeaf Offline Vault",
                    marginPt.toFloat(),
                    pageH - 22f,
                    metaPaint
                )
                pdfDoc.finishPage(page)
            }
            FileOutputStream(outFile).use { pdfDoc.writeTo(it) }
        } finally {
            pdfDoc.close()
        }
        GeneratedFileInfo(outFile, max(1, pageNum), outFile.length())
    }

    suspend fun pdfToPngImages(
        sourcePath: String,
        outputPrefix: String
    ): List<GeneratedFileInfo> = withContext(Dispatchers.IO) {
        val srcFile = File(sourcePath)
        val outputs = mutableListOf<GeneratedFileInfo>()
        if (!srcFile.exists() || !srcFile.name.lowercase().endsWith(".pdf")) return@withContext outputs

        ParcelFileDescriptor.open(srcFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val count = min(renderer.pageCount, 8)
                for (i in 0 until count) {
                    renderer.openPage(i).use { page ->
                        val w = 1080
                        val h = max(600, (w * page.height.toFloat() / max(1, page.width)).roundToInt())
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        Canvas(bmp).drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val outFile = File(documentsDir, safeFileName("${outputPrefix}_p${i + 1}", "png"))
                        FileOutputStream(outFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 95, it) }
                        bmp.recycle()
                        outputs.add(GeneratedFileInfo(outFile, 1, outFile.length()))
                    }
                }
            }
        }
        outputs
    }

    suspend fun docOrPdfToExcel(
        sourcePath: String,
        sourceTitle: String,
        summarySnippet: String,
        pageCount: Int,
        outputTitle: String
    ): GeneratedFileInfo = withContext(Dispatchers.IO) {
        val srcFile = File(sourcePath)
        val outFile = File(documentsDir, safeFileName(outputTitle, "xlsx"))
        val lower = srcFile.name.lowercase()
        val rows = mutableListOf<List<String>>()

        if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".docx")) {
            val text = readTextOrDocx(srcFile)
            val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            rows.add(listOf("Row", "Section / Field", "Extracted Content"))
            if (lines.isEmpty()) {
                rows.add(listOf("1", sourceTitle, summarySnippet.ifBlank { "No text content" }))
            } else {
                lines.forEachIndexed { index, line ->
                    val colonIdx = line.indexOf(':')
                    if (colonIdx in 2..32) {
                        val key = line.substring(0, colonIdx).trim()
                        val value = line.substring(colonIdx + 1).trim()
                        rows.add(listOf("${index + 1}", key, value))
                    } else if (line.contains("|")) {
                        val parts = line.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                        rows.add(listOf("${index + 1}") + parts)
                    } else {
                        rows.add(listOf("${index + 1}", "Paragraph ${index + 1}", line))
                    }
                }
            }
        } else {
            val actualPages = if (lower.endsWith(".pdf")) getPdfPageCount(sourcePath) else max(1, pageCount)
            rows.add(listOf("Page", "Document Title", "Summary / Notes", "Source File"))
            val summaryParts = summarySnippet
                .split(Regex("[.;]"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            for (p in 1..actualPages) {
                val snippet = summaryParts.getOrNull(p - 1)
                    ?: summarySnippet.ifBlank { "Extracted page $p content" }
                rows.add(listOf("Page $p", sourceTitle, snippet, srcFile.name))
            }
            rows.add(listOf("Meta", "Total Pages", "$actualPages", "${srcFile.length()} bytes"))
        }

        ExcelAndImageEngine.writeXlsxWorkbook(
            sheetName = outputTitle.take(28),
            rows = rows,
            destFile = outFile
        )
        GeneratedFileInfo(outFile, 1, outFile.length())
    }

    suspend fun excelToPdf(
        sourcePath: String,
        outputTitle: String
    ): GeneratedFileInfo = withContext(Dispatchers.IO) {
        val srcFile = File(sourcePath)
        val outFile = File(documentsDir, safeFileName(outputTitle, "pdf"))
        val rows = ExcelAndImageEngine.readSpreadsheetRows(srcFile)
        val pages = ExcelAndImageEngine.renderSpreadsheetToPdf(
            title = outputTitle,
            rows = rows,
            destFile = outFile
        )
        GeneratedFileInfo(outFile, pages, outFile.length())
    }

    suspend fun excelToWordDocx(
        sourcePath: String,
        outputTitle: String
    ): GeneratedFileInfo = withContext(Dispatchers.IO) {
        val srcFile = File(sourcePath)
        val outFile = File(documentsDir, safeFileName(outputTitle, "docx"))
        val rows = ExcelAndImageEngine.readSpreadsheetRows(srcFile)
        val paragraphs = if (rows.isEmpty()) {
            listOf("Converted from ${srcFile.name} (no rows found).")
        } else {
            val header = rows.first()
            val list = mutableListOf<String>()
            list.add("Columns: ${header.joinToString(" | ")}")
            list.add("")
            rows.drop(1).forEachIndexed { idx, row ->
                val formatted = header.mapIndexed { colIdx, colName ->
                    val cell = row.getOrNull(colIdx).orEmpty()
                    if (colName.isNotBlank()) "$colName: $cell" else cell
                }.joinToString("  •  ")
                list.add("${idx + 1}. $formatted")
            }
            if (rows.size == 1) {
                list.add(rows.first().joinToString("  •  "))
            }
            list
        }
        ExcelAndImageEngine.writeDocxDocument(
            title = outputTitle,
            paragraphs = paragraphs,
            destFile = outFile
        )
        GeneratedFileInfo(outFile, 1, outFile.length())
    }

    suspend fun compressImages(
        bitmaps: List<Bitmap>,
        qualityPercent: Int,
        maxDimensionPx: Int,
        outputTitle: String
    ): List<GeneratedFileInfo> = withContext(Dispatchers.IO) {
        val results = mutableListOf<GeneratedFileInfo>()
        bitmaps.forEachIndexed { index, bitmap ->
            val suffix = if (bitmaps.size == 1) outputTitle else "${outputTitle}_${index + 1}"
            val outFile = File(documentsDir, safeFileName(suffix, "jpg"))
            val bytes = ExcelAndImageEngine.compressImageToFile(
                source = bitmap,
                qualityPercent = qualityPercent,
                maxDimensionPx = maxDimensionPx,
                destFile = outFile
            )
            results.add(GeneratedFileInfo(outFile, 1, bytes))
        }
        results
    }

    suspend fun perspectiveWarpAndEnhance(
        source: Bitmap,
        corners: List<NormalizedPoint>,
        filterMode: ScannerFilterMode,
        enhancement: ScanEnhancementSettings = ScanEnhancementSettings()
    ): Bitmap = withContext(Dispatchers.Default) {
        val safeCorners = if (corners.size == 4) corners else listOf(
            NormalizedPoint(0.08f, 0.08f),
            NormalizedPoint(0.92f, 0.08f),
            NormalizedPoint(0.92f, 0.92f),
            NormalizedPoint(0.08f, 0.92f)
        )
        val outW = enhancement.resolution.widthPx
        val outH = enhancement.resolution.heightPx
        val result = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)

        val srcPts = floatArrayOf(
            safeCorners[0].x * source.width, safeCorners[0].y * source.height,
            safeCorners[1].x * source.width, safeCorners[1].y * source.height,
            safeCorners[2].x * source.width, safeCorners[2].y * source.height,
            safeCorners[3].x * source.width, safeCorners[3].y * source.height
        )
        val dstPts = floatArrayOf(
            0f, 0f,
            outW.toFloat(), 0f,
            outW.toFloat(), outH.toFloat(),
            0f, outH.toFloat()
        )

        val matrix = Matrix()
        matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)

        val combinedMatrix = ColorMatrix()
        when (filterMode) {
            ScannerFilterMode.ORIGINAL -> Unit
            ScannerFilterMode.BOTANICAL_CRISP -> {
                combinedMatrix.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            1.22f, 0f, 0f, 0f, -14f,
                            0f, 1.24f, 0f, 0f, -12f,
                            0f, 0f, 1.20f, 0f, -14f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            ScannerFilterMode.MAGIC_COLOR -> {
                val sat = ColorMatrix().apply { setSaturation(1.38f) }
                val vivid = ColorMatrix(
                    floatArrayOf(
                        1.26f, 0f, 0f, 0f, -10f,
                        0f, 1.26f, 0f, 0f, -10f,
                        0f, 0f, 1.26f, 0f, -10f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                sat.postConcat(vivid)
                combinedMatrix.postConcat(sat)
            }
            ScannerFilterMode.WHITEBOARD -> {
                val sat = ColorMatrix().apply { setSaturation(1.20f) }
                val lift = ColorMatrix(
                    floatArrayOf(
                        1.42f, 0f, 0f, 0f, -18f,
                        0f, 1.42f, 0f, 0f, -16f,
                        0f, 0f, 1.44f, 0f, -14f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                sat.postConcat(lift)
                combinedMatrix.postConcat(sat)
            }
            ScannerFilterMode.GRAYSCALE -> {
                combinedMatrix.postConcat(ColorMatrix().apply { setSaturation(0f) })
            }
            ScannerFilterMode.BW_PRINT -> {
                val sat = ColorMatrix().apply { setSaturation(0f) }
                val contrast = ColorMatrix(
                    floatArrayOf(
                        2.1f, 0f, 0f, 0f, -130f,
                        0f, 2.1f, 0f, 0f, -130f,
                        0f, 0f, 2.1f, 0f, -130f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                sat.postConcat(contrast)
                combinedMatrix.postConcat(sat)
            }
            ScannerFilterMode.WARM_PAPER -> {
                val warm = ColorMatrix(
                    floatArrayOf(
                        1.08f, 0.04f, 0f, 0f, 6f,
                        0.02f, 1.03f, 0f, 0f, 2f,
                        0f, 0f, 0.90f, 0f, -6f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                combinedMatrix.postConcat(warm)
            }
            ScannerFilterMode.HIGH_CONTRAST -> {
                val sat = ColorMatrix().apply { setSaturation(0f) }
                val inkBoost = ColorMatrix(
                    floatArrayOf(
                        2.65f, 0f, 0f, 0f, -185f,
                        0f, 2.65f, 0f, 0f, -185f,
                        0f, 0f, 2.65f, 0f, -185f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                sat.postConcat(inkBoost)
                combinedMatrix.postConcat(sat)
            }
        }

        // Apply user contrast and brightness adjustments
        val c = enhancement.contrast.coerceIn(0.6f, 1.8f)
        val b = (enhancement.brightness.coerceIn(-0.4f, 0.4f) * 255f)
        val offset = (-128f * c + 128f) + b
        if (kotlin.math.abs(c - 1f) > 0.01f || kotlin.math.abs(b) > 0.5f) {
            combinedMatrix.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        c, 0f, 0f, 0f, offset,
                        0f, c, 0f, 0f, offset,
                        0f, 0f, c, 0f, offset,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(combinedMatrix)
        }
        canvas.drawBitmap(source, matrix, paint)

        // Apply on-device Clarity sharpening kernel (3x3 unsharp mask)
        val clarityAmount = enhancement.clarity.coerceIn(0f, 1f)
        if (clarityAmount > 0.03f) {
            applyClaritySharpenInPlace(result, clarityAmount * 0.75f)
        }
        result
    }

    private fun applyClaritySharpenInPlace(bitmap: Bitmap, strength: Float) {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 3 || h < 3) return
        val src = IntArray(w * h)
        val dst = IntArray(w * h)
        bitmap.getPixels(src, 0, w, 0, 0, w, h)
        System.arraycopy(src, 0, dst, 0, src.size)

        val centerWeight = 1f + 4f * strength
        val neighborWeight = -strength

        for (y in 1 until h - 1) {
            val rowOffset = y * w
            val upOffset = (y - 1) * w
            val downOffset = (y + 1) * w
            for (x in 1 until w - 1) {
                val idx = rowOffset + x
                val cPx = src[idx]
                val uPx = src[upOffset + x]
                val dPx = src[downOffset + x]
                val lPx = src[idx - 1]
                val rPx = src[idx + 1]

                val r = (((cPx shr 16) and 0xFF) * centerWeight +
                    (((uPx shr 16) and 0xFF) + ((dPx shr 16) and 0xFF) +
                        ((lPx shr 16) and 0xFF) + ((rPx shr 16) and 0xFF)) * neighborWeight)
                    .toInt().coerceIn(0, 255)
                val g = (((cPx shr 8) and 0xFF) * centerWeight +
                    (((uPx shr 8) and 0xFF) + ((dPx shr 8) and 0xFF) +
                        ((lPx shr 8) and 0xFF) + ((rPx shr 8) and 0xFF)) * neighborWeight)
                    .toInt().coerceIn(0, 255)
                val b = ((cPx and 0xFF) * centerWeight +
                    ((uPx and 0xFF) + (dPx and 0xFF) +
                        (lPx and 0xFF) + (rPx and 0xFF)) * neighborWeight)
                    .toInt().coerceIn(0, 255)

                dst[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        bitmap.setPixels(dst, 0, w, 0, 0, w, h)
    }

    suspend fun decodeBitmapFromUri(uri: Uri, maxDim: Int = 1400): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val raw = BitmapFactory.decodeStream(stream) ?: return@use null
                    val scale = min(1f, maxDim.toFloat() / max(raw.width, raw.height).toFloat())
                    if (scale < 0.99f) {
                        val scaled = Bitmap.createScaledBitmap(
                            raw,
                            max(1, (raw.width * scale).roundToInt()),
                            max(1, (raw.height * scale).roundToInt()),
                            true
                        )
                        raw.recycle()
                        scaled
                    } else {
                        raw
                    }
                }
            } catch (_: Exception) {
                null
            }
        }

    suspend fun importUriToVault(uri: Uri): Triple<File, DocumentFormat, String>? =
        withContext(Dispatchers.IO) {
            try {
                val displayName = resolveDisplayName(uri) ?: "Imported_Document"
                val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
                val lowerName = displayName.lowercase()
                val (format, ext) = when {
                    mime.contains("pdf") || lowerName.endsWith(".pdf") -> DocumentFormat.PDF to "pdf"
                    mime.contains("spreadsheetml") || lowerName.endsWith(".xlsx") ->
                        DocumentFormat.EXCEL to "xlsx"
                    mime.contains("ms-excel") || mime.contains("excel") || lowerName.endsWith(".xls") ->
                        DocumentFormat.EXCEL to "xls"
                    mime.contains("csv") || lowerName.endsWith(".csv") ->
                        DocumentFormat.EXCEL to "csv"
                    mime.contains("wordprocessingml") || lowerName.endsWith(".docx") ->
                        DocumentFormat.WORD to "docx"
                    mime.contains("msword") || mime.contains("word") || lowerName.endsWith(".doc") ->
                        DocumentFormat.WORD to "doc"
                    mime.contains("text") || lowerName.endsWith(".txt") || lowerName.endsWith(".md") ->
                        DocumentFormat.WORD to "txt"
                    mime.startsWith("image/") || lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ->
                        DocumentFormat.IMAGE to "png"
                    else -> DocumentFormat.PDF to "pdf"
                }
                val title = displayName.substringBeforeLast(".").ifBlank { "Imported Document" }
                val destFile = File(documentsDir, safeFileName(title, ext))
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output -> input.copyTo(output) }
                } ?: return@withContext null
                Triple(destFile, format, title)
            } catch (_: Exception) {
                null
            }
        }

    fun readTextOrDocx(file: File): String {
        if (!file.exists()) return ""
        return try {
            if (file.name.lowercase().endsWith(".docx")) {
                val sb = StringBuilder()
                ZipInputStream(file.inputStream()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == "word/document.xml") {
                            val xml = zis.bufferedReader().readText()
                            val stripped = xml
                                .replace(Regex("</w:p>"), "\n")
                                .replace(Regex("<[^>]+>"), "")
                                .replace("&amp;", "&")
                                .replace("&lt;", "<")
                                .replace("&gt;", ">")
                            sb.append(stripped)
                            break
                        }
                        entry = zis.nextEntry
                    }
                }
                sb.toString().ifBlank { file.readText() }
            } else if (file.name.lowercase().endsWith(".doc")) {
                extractReadableStrings(file).joinToString("\n").ifBlank { "Legacy Word document opened in StudyLeaf." }
            } else {
                file.readText()
            }
        } catch (_: Exception) {
            "Document text preview unavailable."
        }
    }

    private fun extractReadableStrings(file: File): List<String> {
        return try {
            val bytes = file.readBytes().take(128 * 1024).toByteArray()
            val text = String(bytes, Charsets.ISO_8859_1)
            Regex("[\\x20-\\x7E]{6,}")
                .findAll(text)
                .map { it.value.trim() }
                .filter { it.length >= 6 && it.any { ch -> ch.isLetter() } }
                .distinct()
                .take(80)
                .toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        } catch (_: Exception) {
            uri.lastPathSegment
        }
    }

    private fun decodeScaledBitmap(path: String, targetWidth: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        var sample = 1
        while (opts.outWidth / sample > targetWidth * 2) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun renderTextPreviewBitmap(title: String, text: String, targetWidth: Int): Bitmap {
        val w = targetWidth.coerceIn(360, 1200)
        val h = (w * 1.414f).roundToInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val scale = w / 595f
        val margin = 42f * scale
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2B579A")
            textSize = 18f * scale
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A1C1E")
            textSize = 12.5f * scale
        }
        canvas.drawText(title.take(36), margin, margin + 20f * scale, headerPaint)
        var y = margin + 52f * scale
        val maxW = w - margin * 2
        for (para in text.split("\n")) {
            if (y > h - margin) break
            val words = para.split(Regex("\\s+"))
            var line = ""
            for (word in words) {
                val next = if (line.isEmpty()) word else "$line $word"
                if (bodyPaint.measureText(next) <= maxW) {
                    line = next
                } else {
                    canvas.drawText(line, margin, y, bodyPaint)
                    y += 18f * scale
                    line = word
                    if (y > h - margin) break
                }
            }
            if (line.isNotEmpty() && y <= h - margin) {
                canvas.drawText(line, margin, y, bodyPaint)
                y += 22f * scale
            }
        }
        return bmp
    }
}
