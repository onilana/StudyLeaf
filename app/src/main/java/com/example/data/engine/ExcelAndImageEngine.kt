package com.example.data.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ExcelAndImageEngine {

    private fun xmlEscape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun columnRef(colIndex: Int): String {
        var idx = colIndex
        val sb = StringBuilder()
        while (idx >= 0) {
            sb.insert(0, ('A'.code + (idx % 26)).toChar())
            idx = idx / 26 - 1
        }
        return sb.toString()
    }

    /**
     * Generates a valid OpenXML .xlsx workbook on-device using ZipOutputStream.
     */
    fun writeXlsxWorkbook(sheetName: String, rows: List<List<String>>, destFile: File) {
        val safeSheetName = sheetName
            .replace(Regex("[\\\\/*?:\\[\\]]"), " ")
            .trim()
            .take(28)
            .ifBlank { "StudyLeaf" }

        val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""

        val rootRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

        val workbookXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="${xmlEscape(safeSheetName)}" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>"""

        val workbookRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""

        val sheetBuilder = StringBuilder()
        sheetBuilder.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sheetBuilder.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        rows.forEachIndexed { rIdx, row ->
            val rowNum = rIdx + 1
            sheetBuilder.append("""<row r="$rowNum">""")
            row.forEachIndexed { cIdx, cellValue ->
                val ref = "${columnRef(cIdx)}$rowNum"
                val num = cellValue.trim().toDoubleOrNull()
                if (num != null && cellValue.trim().isNotEmpty() && !cellValue.trim().startsWith("0")) {
                    sheetBuilder.append("""<c r="$ref"><v>${cellValue.trim()}</v></c>""")
                } else {
                    sheetBuilder.append("""<c r="$ref" t="inlineStr"><is><t>${xmlEscape(cellValue)}</t></is></c>""")
                }
            }
            sheetBuilder.append("</row>")
        }
        sheetBuilder.append("</sheetData></worksheet>")

        ZipOutputStream(FileOutputStream(destFile)).use { zos ->
            fun put(name: String, content: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            put("[Content_Types].xml", contentTypesXml)
            put("_rels/.rels", rootRelsXml)
            put("xl/workbook.xml", workbookXml)
            put("xl/_rels/workbook.xml.rels", workbookRelsXml)
            put("xl/worksheets/sheet1.xml", sheetBuilder.toString())
        }
    }

    /**
     * Generates a valid OpenXML .docx document on-device from rows or paragraphs.
     */
    fun writeDocxDocument(title: String, paragraphs: List<String>, destFile: File) {
        val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

        val rootRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

        val bodyBuilder = StringBuilder()
        bodyBuilder.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        bodyBuilder.append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>""")
        bodyBuilder.append("""<w:p><w:r><w:rPr><w:b/><w:sz w:val="32"/></w:rPr><w:t>${xmlEscape(title)}</w:t></w:r></w:p>""")
        paragraphs.forEach { para ->
            bodyBuilder.append("""<w:p><w:r><w:t xml:space="preserve">${xmlEscape(para)}</w:t></w:r></w:p>""")
        }
        bodyBuilder.append("</w:body></w:document>")

        ZipOutputStream(FileOutputStream(destFile)).use { zos ->
            fun put(name: String, content: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            put("[Content_Types].xml", contentTypesXml)
            put("_rels/.rels", rootRelsXml)
            put("word/document.xml", bodyBuilder.toString())
        }
    }

    /**
     * Parses rows from a local .xlsx workbook or .csv file.
     */
    fun readSpreadsheetRows(file: File): List<List<String>> {
        if (!file.exists()) return emptyList()
        val lower = file.name.lowercase()
        if (lower.endsWith(".csv")) {
            return file.readLines()
                .filter { it.isNotBlank() }
                .map { line -> line.split(",").map { it.trim().removeSurrounding("\"") } }
        }
        if (lower.endsWith(".xlsx")) {
            return try {
                val sharedStrings = mutableListOf<String>()
                var sheetXml: String? = null
                ZipInputStream(file.inputStream()).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        when {
                            entry.name == "xl/sharedStrings.xml" -> {
                                val xml = zis.bufferedReader().readText()
                                val matches = Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
                                matches.forEach { sharedStrings.add(unescapeXml(it.groupValues[1])) }
                            }
                            entry.name.startsWith("xl/worksheets/sheet") && sheetXml == null -> {
                                sheetXml = zis.bufferedReader().readText()
                            }
                        }
                        entry = zis.nextEntry
                    }
                }
                val xml = sheetXml ?: return emptyList()
                val rows = mutableListOf<List<String>>()
                val rowRegex = Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
                val cellRegex = Regex("<c([^>]*)>(.*?)</c>", RegexOption.DOT_MATCHES_ALL)
                val inlineTextRegex = Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
                val valRegex = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL)

                rowRegex.findAll(xml).forEach { rowMatch ->
                    val cells = mutableListOf<String>()
                    cellRegex.findAll(rowMatch.groupValues[1]).forEach { cellMatch ->
                        val attrs = cellMatch.groupValues[1]
                        val inner = cellMatch.groupValues[2]
                        val value = when {
                            attrs.contains("""t="inlineStr"""") -> {
                                inlineTextRegex.findAll(inner).joinToString("") { unescapeXml(it.groupValues[1]) }
                            }
                            attrs.contains("""t="s"""") -> {
                                val idx = valRegex.find(inner)?.groupValues?.get(1)?.toIntOrNull()
                                if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else ""
                            }
                            else -> {
                                valRegex.find(inner)?.groupValues?.get(1)?.let(::unescapeXml)
                                    ?: inlineTextRegex.find(inner)?.groupValues?.get(1)?.let(::unescapeXml)
                                    ?: ""
                            }
                        }
                        cells.add(value)
                    }
                    if (cells.isNotEmpty()) rows.add(cells)
                }
                rows
            } catch (_: Exception) {
                emptyList()
            }
        }
        return emptyList()
    }

    private fun unescapeXml(s: String): String = s
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")

    /**
     * Renders spreadsheet rows into a multi-page grid-lined PDF table.
     */
    fun renderSpreadsheetToPdf(title: String, rows: List<List<String>>, destFile: File): Int {
        val pdf = PdfDocument()
        val pageW = 595
        val pageH = 842
        val margin = 36f
        val safeRows = if (rows.isEmpty()) listOf(listOf("Column A", "Column B"), listOf("No rows found", "")) else rows
        val colCount = safeRows.maxOf { it.size }.coerceIn(1, 6)
        val colW = (pageW - margin * 2) / colCount
        val rowH = 26f

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1D6F42")
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val headerBg = Paint().apply { color = Color.parseColor("#E6F4EC") }
        val zebraBg = Paint().apply { color = Color.parseColor("#F7FAF8") }
        val borderPaint = Paint().apply {
            color = Color.parseColor("#C1C8C2")
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        val headerText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#012D1D")
            textSize = 10.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val cellText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A1C1E")
            textSize = 10f
        }

        var rowIndex = 0
        var pageNum = 0
        try {
            while (rowIndex < safeRows.size) {
                pageNum++
                val info = PdfDocument.PageInfo.Builder(pageW, pageH, pageNum).create()
                val page = pdf.startPage(info)
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)

                var y = margin + 16f
                if (pageNum == 1) {
                    canvas.drawText(title.take(44), margin, y, titlePaint)
                    y += 18f
                }

                while (rowIndex < safeRows.size && y + rowH <= pageH - margin) {
                    val row = safeRows[rowIndex]
                    val isHeader = rowIndex == 0
                    val rect = RectF(margin, y, pageW - margin, y + rowH)
                    if (isHeader) canvas.drawRect(rect, headerBg)
                    else if (rowIndex % 2 == 0) canvas.drawRect(rect, zebraBg)

                    for (c in 0 until colCount) {
                        val left = margin + c * colW
                        val cellRect = RectF(left, y, left + colW, y + rowH)
                        canvas.drawRect(cellRect, borderPaint)
                        val text = row.getOrNull(c).orEmpty().take((colW / 5.5f).roundToInt().coerceAtLeast(6))
                        canvas.drawText(
                            text,
                            left + 6f,
                            y + 17f,
                            if (isHeader) headerText else cellText
                        )
                    }
                    y += rowH
                    rowIndex++
                }
                pdf.finishPage(page)
            }
            FileOutputStream(destFile).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        return max(1, pageNum)
    }

    /**
     * Renders a crisp preview bitmap of a spreadsheet for the Reader & Preview Pane.
     */
    fun renderSpreadsheetPreviewBitmap(title: String, rows: List<List<String>>, targetWidth: Int): Bitmap {
        val w = targetWidth.coerceIn(360, 1200)
        val h = (w * 1.414f).roundToInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)

        val scale = w / 595f
        val margin = 32f * scale
        val safeRows = if (rows.isEmpty()) listOf(listOf("Item", "Value"), listOf("Empty Sheet", "—")) else rows
        val colCount = safeRows.maxOf { it.size }.coerceIn(1, 5)
        val colW = (w - margin * 2) / colCount
        val rowH = 28f * scale

        val barPaint = Paint().apply { color = Color.parseColor("#1D6F42") }
        canvas.drawRect(0f, 0f, w.toFloat(), 6f * scale, barPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1D6F42")
            textSize = 17f * scale
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText(title.take(40), margin, margin + 18f * scale, titlePaint)

        val headerBg = Paint().apply { color = Color.parseColor("#E3F5EB") }
        val altBg = Paint().apply { color = Color.parseColor("#F6FAF7") }
        val gridPaint = Paint().apply {
            color = Color.parseColor("#C1C8C2")
            style = Paint.Style.STROKE
            strokeWidth = 1f * scale
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#012D1D")
            textSize = 11f * scale
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A1C1E")
            textSize = 10.5f * scale
        }

        var y = margin + 36f * scale
        safeRows.forEachIndexed { idx, row ->
            if (y + rowH > h - margin) return@forEachIndexed
            val rowRect = RectF(margin, y, w - margin, y + rowH)
            if (idx == 0) canvas.drawRect(rowRect, headerBg)
            else if (idx % 2 == 0) canvas.drawRect(rowRect, altBg)

            for (c in 0 until colCount) {
                val left = margin + c * colW
                canvas.drawRect(RectF(left, y, left + colW, y + rowH), gridPaint)
                val maxChars = (colW / (6.2f * scale)).roundToInt().coerceAtLeast(5)
                val value = row.getOrNull(c).orEmpty().take(maxChars)
                canvas.drawText(value, left + 6f * scale, y + 18f * scale, if (idx == 0) headerPaint else cellPaint)
            }
            y += rowH
        }
        return bmp
    }

    /**
     * Compresses an image bitmap to JPEG with configurable quality & max dimension.
     */
    fun compressImageToFile(
        source: Bitmap,
        qualityPercent: Int,
        maxDimensionPx: Int,
        destFile: File
    ): Long {
        val largest = max(source.width, source.height)
        val scaled = if (maxDimensionPx > 0 && largest > maxDimensionPx) {
            val factor = min(1f, maxDimensionPx.toFloat() / largest.toFloat())
            Bitmap.createScaledBitmap(
                source,
                max(1, (source.width * factor).roundToInt()),
                max(1, (source.height * factor).roundToInt()),
                true
            )
        } else {
            source
        }
        FileOutputStream(destFile).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, qualityPercent.coerceIn(15, 95), out)
        }
        if (scaled !== source) scaled.recycle()
        return destFile.length()
    }
}
