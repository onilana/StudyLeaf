package com.example.data.engine

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Inflater
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class PdfEditableTextLine(
    val id: String,
    val pageIndex: Int,
    val text: String,
    val xFraction: Float,
    val yFraction: Float,
    val widthFraction: Float,
    val heightFraction: Float,
    val fontSizePt: Float,
    val isBold: Boolean = false
)

/**
 * Pure-Kotlin, on-device PDF text extractor with:
 * - FlateDecode stream inflation & PDF 1.5+ /ObjStm object stream unpacking
 * - Per-font /ToUnicode CMap parsing (beginbfchar & beginbfrange) & bold font detection
 * - Full PDF text state machine (CTM q/Q/cm, Tm/Tlm, Td/TD/T*, Tf, TL, Tz, Tj, TJ kerning, ', ")
 * - 2D spatial line, column-span & bounding-box reconstruction mapped to each page's MediaBox/CropBox
 */
object PdfTextExtractor {

    private data class Matrix2D(
        val a: Double = 1.0,
        val b: Double = 0.0,
        val c: Double = 0.0,
        val d: Double = 1.0,
        val e: Double = 0.0,
        val f: Double = 0.0
    ) {
        fun multiply(other: Matrix2D): Matrix2D = Matrix2D(
            a = a * other.a + b * other.c,
            b = a * other.b + b * other.d,
            c = c * other.a + d * other.c,
            d = c * other.b + d * other.d,
            e = e * other.a + f * other.c + other.e,
            f = e * other.b + f * other.d + other.f
        )

        companion object {
            val IDENTITY = Matrix2D()
            fun translate(tx: Double, ty: Double) = Matrix2D(1.0, 0.0, 0.0, 1.0, tx, ty)
        }
    }

    internal data class FontCMap(
        val oneByte: MutableMap<Int, String> = mutableMapOf(),
        val twoByte: MutableMap<Int, String> = mutableMapOf()
    ) {
        fun isEmpty(): Boolean = oneByte.isEmpty() && twoByte.isEmpty()

        fun mergeFrom(other: FontCMap) {
            other.oneByte.forEach { (k, v) -> oneByte.putIfAbsent(k, v) }
            other.twoByte.forEach { (k, v) -> twoByte.putIfAbsent(k, v) }
        }

        fun decode(bytes: ByteArray): String? {
            if (bytes.isEmpty() || isEmpty()) return null

            // Prefer 2-byte decoding when twoByte entries exist and length is even
            if (twoByte.isNotEmpty() && bytes.size >= 2 && bytes.size % 2 == 0) {
                val sb = StringBuilder()
                var matched = 0
                val total = bytes.size / 2
                var i = 0
                while (i + 1 < bytes.size) {
                    val code = ((bytes[i].toInt() and 0xFF) shl 8) or (bytes[i + 1].toInt() and 0xFF)
                    val mapped = twoByte[code]
                    if (mapped != null) {
                        sb.append(mapped)
                        matched++
                    } else if (code in 32..126) {
                        sb.append(code.toChar())
                    }
                    i += 2
                }
                if (matched > 0 && matched.toFloat() / total >= 0.45f) {
                    return sb.toString()
                }
            }

            // 1-byte decoding
            if (oneByte.isNotEmpty()) {
                val sb = StringBuilder()
                var matched = 0
                for (b in bytes) {
                    val code = b.toInt() and 0xFF
                    val mapped = oneByte[code]
                    if (mapped != null) {
                        sb.append(mapped)
                        matched++
                    } else if (code in 32..126) {
                        sb.append(code.toChar())
                    }
                }
                if (matched > 0 && matched.toFloat() / bytes.size >= 0.45f) {
                    return sb.toString()
                }
            }

            return null
        }
    }

    private data class PositionedRun(
        val x: Double,
        val y: Double,
        val width: Double,
        val fontSize: Double,
        val text: String,
        val order: Int,
        val isBold: Boolean = false
    )

    internal data class PdfPageBox(
        val x0: Double = 0.0,
        val y0: Double = 0.0,
        val x1: Double = 595.0,
        val y1: Double = 842.0,
        val rotateDeg: Int = 0
    ) {
        val width: Double get() = max(1.0, x1 - x0)
        val height: Double get() = max(1.0, y1 - y0)
    }

    private data class PageDescriptor(
        val pageIndex: Int,
        val box: PdfPageBox,
        val contentObjNums: List<Int>
    )

    private sealed interface PdfToken {
        data class Str(val bytes: ByteArray, val isHex: Boolean) : PdfToken
        data class Num(val value: Double) : PdfToken
        data class Name(val name: String) : PdfToken
        data class Op(val name: String) : PdfToken
        data class Arr(val items: List<PdfToken>) : PdfToken
    }

    private data class DecodedStream(
        val objNum: Int?,
        val dictText: String,
        val data: ByteArray
    )

    fun extractText(file: File): String {
        return try {
            if (!file.exists() || file.length() == 0L) return ""
            val bytes = file.readBytes()
            extractTextFromBytes(bytes)
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Extracts every existing text line/column-span across all pages with normalized top-left
     * bounding boxes so users can tap directly on existing PDF text to edit it in place.
     */
    fun extractPositionedLines(file: File): List<PdfEditableTextLine> {
        return try {
            if (!file.exists() || file.length() == 0L) return emptyList()
            val bytes = file.readBytes()
            extractPositionedLinesFromBytes(bytes)
        } catch (_: Exception) {
            emptyList()
        }
    }

    internal fun extractPositionedLinesFromBytes(bytes: ByteArray): List<PdfEditableTextLine> {
        val latin = String(bytes, Charsets.ISO_8859_1)
        val streams = extractCandidateStreams(bytes, latin)

        val cmapsByObjNum = mutableMapOf<Int, FontCMap>()
        val fallbackCMap = FontCMap()
        val contentStreamsByObj = mutableMapOf<Int, String>()
        val orderedContentStreams = mutableListOf<Pair<Int?, String>>()
        val unpackedObjects = mutableMapOf<Int, String>()

        for (stream in streams) {
            if (stream.dictText.contains("/Type /ObjStm") || stream.dictText.contains("/Type/ObjStm")) {
                parseObjectStream(stream.dictText, stream.data, unpackedObjects)
                continue
            }
            val text = String(stream.data, Charsets.ISO_8859_1)
            val isCMap = text.contains("beginbfchar") || text.contains("beginbfrange") ||
                (text.contains("begincmap") && text.contains("endcmap"))
            if (isCMap) {
                val cmap = parseToUnicodeCMap(text)
                if (!cmap.isEmpty()) {
                    if (stream.objNum != null) {
                        cmapsByObjNum[stream.objNum] = cmap
                    }
                    fallbackCMap.mergeFrom(cmap)
                }
            } else if (looksLikeTextContentStream(text)) {
                if (stream.objNum != null) {
                    contentStreamsByObj[stream.objNum] = text
                }
                orderedContentStreams.add(stream.objNum to text)
            }
        }

        val allObjects = collectAllObjectDictionaries(latin, unpackedObjects)
        val fontNameToCMap = buildFontNameToCMapLookup(latin, cmapsByObjNum, allObjects)
        val boldFonts = buildBoldFontLookup(latin, allObjects)

        val pages = resolvePageDescriptors(allObjects)
        val result = mutableListOf<PdfEditableTextLine>()

        val matchedPagesWithStreams = pages.filter { p ->
            p.contentObjNums.any { contentStreamsByObj.containsKey(it) }
        }

        if (matchedPagesWithStreams.isNotEmpty()) {
            for (page in pages) {
                val combinedStream = page.contentObjNums
                    .mapNotNull { contentStreamsByObj[it] }
                    .joinToString("\n")
                if (combinedStream.isBlank()) continue
                val runs = extractRunsFromContentStream(
                    stream = combinedStream,
                    fontCMaps = fontNameToCMap,
                    fallbackCMap = fallbackCMap,
                    boldFonts = boldFonts
                )
                result.addAll(
                    reconstructEditableLinesFromRuns(
                        runs = runs,
                        pageIndex = page.pageIndex,
                        box = page.box
                    )
                )
            }
        } else {
            // Fallback when PDF uses non-standard page tree: match decoded text streams sequentially
            orderedContentStreams.forEachIndexed { idx, (_, streamText) ->
                val runs = extractRunsFromContentStream(
                    stream = streamText,
                    fontCMaps = fontNameToCMap,
                    fallbackCMap = fallbackCMap,
                    boldFonts = boldFonts
                )
                if (runs.isEmpty()) return@forEachIndexed
                val inferredBox = pages.getOrNull(idx)?.box ?: inferPageBoxFromRuns(runs)
                result.addAll(
                    reconstructEditableLinesFromRuns(
                        runs = runs,
                        pageIndex = idx,
                        box = inferredBox
                    )
                )
            }
        }

        return result
    }

    internal fun extractPositionedLinesFromContentStream(
        stream: String,
        pageIndex: Int = 0,
        box: PdfPageBox = PdfPageBox(),
        fontCMaps: Map<String, FontCMap> = emptyMap(),
        fallbackCMap: FontCMap = FontCMap(),
        boldFonts: Set<String> = emptySet()
    ): List<PdfEditableTextLine> {
        val runs = extractRunsFromContentStream(
            stream = stream,
            fontCMaps = fontCMaps,
            fallbackCMap = fallbackCMap,
            boldFonts = boldFonts
        )
        return reconstructEditableLinesFromRuns(runs, pageIndex, box)
    }

    internal fun extractTextFromBytes(bytes: ByteArray): String {
        val latin = String(bytes, Charsets.ISO_8859_1)

        // 1. Scan all streams (inflating FlateDecode streams, skipping binary images/fonts/metadata)
        val streams = extractCandidateStreams(bytes, latin)

        // 2. Parse ToUnicode CMaps & unpack any /ObjStm
        val cmapsByObjNum = mutableMapOf<Int, FontCMap>()
        val fallbackCMap = FontCMap()
        val contentStreams = mutableListOf<String>()
        val unpackedObjects = mutableMapOf<Int, String>()

        for (stream in streams) {
            if (stream.dictText.contains("/Type /ObjStm") || stream.dictText.contains("/Type/ObjStm")) {
                parseObjectStream(stream.dictText, stream.data, unpackedObjects)
                continue
            }
            val text = String(stream.data, Charsets.ISO_8859_1)
            val isCMap = text.contains("beginbfchar") || text.contains("beginbfrange") ||
                (text.contains("begincmap") && text.contains("endcmap"))
            if (isCMap) {
                val cmap = parseToUnicodeCMap(text)
                if (!cmap.isEmpty()) {
                    if (stream.objNum != null) {
                        cmapsByObjNum[stream.objNum] = cmap
                    }
                    fallbackCMap.mergeFrom(cmap)
                }
            } else if (looksLikeTextContentStream(text)) {
                contentStreams.add(text)
            }
        }

        // 3. Map font resource names (/F1, /TT0, /C2_0, etc.) to their ToUnicode CMap
        val allObjects = collectAllObjectDictionaries(latin, unpackedObjects)
        val fontNameToCMap = buildFontNameToCMapLookup(latin, cmapsByObjNum, allObjects)

        // 4. Parse each content stream with full text-state & 2D spatial reconstruction
        val pageTexts = mutableListOf<String>()
        for (content in contentStreams) {
            val extracted = extractTextFromContentStream(
                stream = content,
                fontCMaps = fontNameToCMap,
                fallbackCMap = fallbackCMap
            )
            if (extracted.isNotBlank()) {
                pageTexts.add(extracted)
            }
        }

        val combined = pageTexts
            .joinToString("\n\n")
            .replace(Regex("[ \\t]+\\n"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

        return repairResidualSingleCharLines(combined)
    }

    /**
     * Exposed for unit testing direct content streams without full PDF wrappers.
     */
    internal fun extractTextFromContentStream(
        stream: String,
        fontCMaps: Map<String, FontCMap> = emptyMap(),
        fallbackCMap: FontCMap = FontCMap()
    ): String {
        val runs = extractRunsFromContentStream(stream, fontCMaps, fallbackCMap, emptySet())
        return reconstructLinesFromRuns(runs)
    }

    private fun extractRunsFromContentStream(
        stream: String,
        fontCMaps: Map<String, FontCMap>,
        fallbackCMap: FontCMap,
        boldFonts: Set<String>
    ): List<PositionedRun> {
        val tokens = tokenizeContentStream(stream)
        if (tokens.isEmpty()) return emptyList()

        val runs = mutableListOf<PositionedRun>()
        var runOrder = 0

        val ctmStack = ArrayDeque<Matrix2D>()
        var ctm = Matrix2D.IDENTITY
        var inTextObject = false
        var tm = Matrix2D.IDENTITY
        var tlm = Matrix2D.IDENTITY

        var currentFont = ""
        var currentFontSize = 12.0
        var textLeading = 14.0
        var horizScale = 1.0

        val operands = mutableListOf<PdfToken>()

        fun decodeTokenBytes(token: PdfToken.Str): String {
            val fontCMap = fontCMaps[currentFont]
            if (fontCMap != null) {
                val mapped = fontCMap.decode(token.bytes)
                if (!mapped.isNullOrEmpty()) return mapped
            }
            if (token.isHex && !fallbackCMap.isEmpty()) {
                val mapped = fallbackCMap.decode(token.bytes)
                if (!mapped.isNullOrEmpty()) return mapped
            }
            return decodePdfBytesFallback(token.bytes, token.isHex)
        }

        fun emitRun(text: String) {
            if (text.isEmpty()) return
            val cleaned = cleanControlChars(text)
            if (cleaned.isEmpty()) return

            val pageMatrix = tm.multiply(ctm)
            val x = pageMatrix.e
            val y = pageMatrix.f
            val scaleX = hypot(pageMatrix.a, pageMatrix.b).let { if (it < 0.001) 1.0 else it }
            val effectiveFontSize = max(4.0, abs(currentFontSize) * scaleX)
            val estimatedWidth = estimateTextWidth(cleaned, effectiveFontSize, horizScale)

            runs.add(
                PositionedRun(
                    x = x,
                    y = y,
                    width = estimatedWidth,
                    fontSize = effectiveFontSize,
                    text = cleaned,
                    order = runOrder++,
                    isBold = boldFonts.contains(currentFont)
                )
            )

            // Advance text matrix horizontally by estimated glyph advance in text space
            val textSpaceAdvance = estimatedWidth / scaleX
            tm = Matrix2D.translate(textSpaceAdvance, 0.0).multiply(tm)
        }

        fun moveNextLine() {
            tlm = Matrix2D.translate(0.0, -textLeading).multiply(tlm)
            tm = tlm
        }

        for (token in tokens) {
            if (token !is PdfToken.Op) {
                operands.add(token)
                continue
            }

            when (token.name) {
                "q" -> {
                    if (ctmStack.size < 64) ctmStack.addLast(ctm)
                }
                "Q" -> {
                    if (ctmStack.isNotEmpty()) ctm = ctmStack.removeLast()
                }
                "cm" -> {
                    if (operands.size >= 6) {
                        val nums = operands.takeLast(6).mapNotNull { (it as? PdfToken.Num)?.value }
                        if (nums.size == 6) {
                            val m = Matrix2D(nums[0], nums[1], nums[2], nums[3], nums[4], nums[5])
                            ctm = m.multiply(ctm)
                        }
                    }
                }
                "BT" -> {
                    inTextObject = true
                    tm = Matrix2D.IDENTITY
                    tlm = Matrix2D.IDENTITY
                }
                "ET" -> {
                    inTextObject = false
                }
                "Tf" -> {
                    if (operands.size >= 2) {
                        val fontName = (operands[operands.size - 2] as? PdfToken.Name)?.name
                        val size = (operands.last() as? PdfToken.Num)?.value
                        if (fontName != null) currentFont = fontName
                        if (size != null && abs(size) > 0.1) {
                            currentFontSize = abs(size)
                            if (textLeading <= 0.1) textLeading = currentFontSize * 1.25
                        }
                    }
                }
                "TL" -> {
                    val leading = (operands.lastOrNull() as? PdfToken.Num)?.value
                    if (leading != null) textLeading = leading
                }
                "Tz" -> {
                    val tz = (operands.lastOrNull() as? PdfToken.Num)?.value
                    if (tz != null && tz > 1.0) horizScale = (tz / 100.0).coerceIn(0.25, 4.0)
                }
                "Td" -> {
                    if (operands.size >= 2) {
                        val tx = (operands[operands.size - 2] as? PdfToken.Num)?.value ?: 0.0
                        val ty = (operands.last() as? PdfToken.Num)?.value ?: 0.0
                        tlm = Matrix2D.translate(tx, ty).multiply(tlm)
                        tm = tlm
                    }
                }
                "TD" -> {
                    if (operands.size >= 2) {
                        val tx = (operands[operands.size - 2] as? PdfToken.Num)?.value ?: 0.0
                        val ty = (operands.last() as? PdfToken.Num)?.value ?: 0.0
                        textLeading = -ty
                        tlm = Matrix2D.translate(tx, ty).multiply(tlm)
                        tm = tlm
                    }
                }
                "Tm" -> {
                    if (operands.size >= 6) {
                        val nums = operands.takeLast(6).mapNotNull { (it as? PdfToken.Num)?.value }
                        if (nums.size == 6) {
                            tlm = Matrix2D(nums[0], nums[1], nums[2], nums[3], nums[4], nums[5])
                            tm = tlm
                        }
                    }
                }
                "T*" -> {
                    moveNextLine()
                }
                "Tj" -> {
                    if (inTextObject) {
                        val strTok = operands.lastOrNull() as? PdfToken.Str
                        if (strTok != null) {
                            emitRun(decodeTokenBytes(strTok))
                        }
                    }
                }
                "'" -> {
                    if (inTextObject) {
                        moveNextLine()
                        val strTok = operands.lastOrNull() as? PdfToken.Str
                        if (strTok != null) {
                            emitRun(decodeTokenBytes(strTok))
                        }
                    }
                }
                "\"" -> {
                    if (inTextObject) {
                        moveNextLine()
                        val strTok = operands.lastOrNull() as? PdfToken.Str
                        if (strTok != null) {
                            emitRun(decodeTokenBytes(strTok))
                        }
                    }
                }
                "TJ" -> {
                    if (inTextObject) {
                        val arrTok = operands.lastOrNull() as? PdfToken.Arr
                        if (arrTok != null) {
                            val combined = StringBuilder()
                            val startTm = tm
                            var extraSpaceAdvance = 0.0
                            for (item in arrTok.items) {
                                when (item) {
                                    is PdfToken.Str -> {
                                        val piece = decodeTokenBytes(item)
                                        if (piece.isNotEmpty()) combined.append(piece)
                                    }
                                    is PdfToken.Num -> {
                                        // PDF TJ uses negative numbers to advance forward (rightward).
                                        // Kerning is typically -10..-90; word spaces are usually -140..-400+.
                                        val displacement = (-item.value / 1000.0) * abs(currentFontSize) * horizScale
                                        extraSpaceAdvance += displacement
                                        if (item.value <= -150.0 && combined.isNotEmpty() && !combined.endsWith(" ")) {
                                            combined.append(' ')
                                        }
                                    }
                                    else -> Unit
                                }
                            }
                            if (combined.isNotEmpty()) {
                                tm = startTm
                                emitRun(combined.toString())
                                if (extraSpaceAdvance != 0.0) {
                                    tm = Matrix2D.translate(extraSpaceAdvance, 0.0).multiply(tm)
                                }
                            }
                        }
                    }
                }
            }
            operands.clear()
        }

        return runs
    }

    private fun reconstructEditableLinesFromRuns(
        runs: List<PositionedRun>,
        pageIndex: Int,
        box: PdfPageBox
    ): List<PdfEditableTextLine> {
        if (runs.isEmpty()) return emptyList()

        val sortedByY = runs.sortedWith(
            compareByDescending<PositionedRun> { it.y }.thenBy { it.x }.thenBy { it.order }
        )

        data class VisualLine(
            var baselineY: Double,
            var avgFontSize: Double,
            val items: MutableList<PositionedRun>
        )

        val visualLines = mutableListOf<VisualLine>()
        for (run in sortedByY) {
            val existing = visualLines.lastOrNull { line ->
                val tolerance = max(2.5, min(line.avgFontSize, run.fontSize) * 0.48)
                abs(run.y - line.baselineY) <= tolerance
            }
            if (existing != null) {
                val n = existing.items.size
                existing.baselineY = (existing.baselineY * n + run.y) / (n + 1)
                existing.avgFontSize = (existing.avgFontSize * n + run.fontSize) / (n + 1)
                existing.items.add(run)
            } else {
                visualLines.add(VisualLine(run.y, run.fontSize, mutableListOf(run)))
            }
        }

        visualLines.sortByDescending { it.baselineY }

        val rot = ((box.rotateDeg % 360) + 360) % 360
        val visibleBoxW = if (rot == 90 || rot == 270) box.height else box.width

        val editableLines = mutableListOf<PdfEditableTextLine>()
        var spanCounter = 0

        fun emitSpan(spanRuns: List<PositionedRun>, baselineY: Double) {
            if (spanRuns.isEmpty()) return
            val sb = StringBuilder()
            var prevEndX: Double? = null
            var prevFont = spanRuns.first().fontSize
            var minX = Double.POSITIVE_INFINITY
            var maxX = Double.NEGATIVE_INFINITY
            var fontWeightedSum = 0.0
            var charWeightSum = 0
            var boldChars = 0

            for (run in spanRuns) {
                val t = run.text
                if (t.isEmpty()) continue
                if (prevEndX != null && sb.isNotEmpty()) {
                    val gap = run.x - prevEndX
                    val refFont = min(prevFont, run.fontSize).coerceIn(6.0, 48.0)
                    val spaceThreshold = max(1.6, refFont * 0.22)
                    if (!sb.last().isWhitespace() && !t.first().isWhitespace() && gap >= spaceThreshold) {
                        sb.append(' ')
                    }
                }
                sb.append(t)
                minX = min(minX, run.x)
                val endX = run.x + max(run.width, run.fontSize * 0.35)
                maxX = max(maxX, endX)
                prevEndX = if (prevEndX == null) endX else max(prevEndX, endX)
                prevFont = run.fontSize
                val w = t.trim().length.coerceAtLeast(1)
                fontWeightedSum += run.fontSize * w
                charWeightSum += w
                if (run.isBold) boldChars += w
            }

            val finalText = sb.toString()
                .replace(Regex("[ \\t]{2,}"), " ")
                .trim()
            if (finalText.isEmpty() || !isReadableLine(finalText)) return
            if (!minX.isFinite() || !maxX.isFinite()) return

            val avgFont = if (charWeightSum > 0) fontWeightedSum / charWeightSum else 12.0
            val uLeft = ((minX - box.x0) / box.width).coerceIn(0.0, 0.98)
            val uRight = ((maxX - box.x0) / box.width).coerceIn(uLeft + 0.02, 1.0)
            val topPdfY = baselineY + avgFont * 0.90
            val bottomPdfY = baselineY - avgFont * 0.28
            val uTop = ((box.y1 - topPdfY) / box.height).coerceIn(0.0, 0.98)
            val uBottom = ((box.y1 - bottomPdfY) / box.height).coerceIn(uTop + 0.012, 1.0)

            // Apply PDF /Rotate into visual screen coordinates (0..1 top-left)
            val (rx0, ry0, rx1, ry1) = when (rot) {
                90 -> arrayOf(1.0 - uBottom, uLeft, 1.0 - uTop, uRight)
                180 -> arrayOf(1.0 - uRight, 1.0 - uBottom, 1.0 - uLeft, 1.0 - uTop)
                270 -> arrayOf(uTop, 1.0 - uRight, uBottom, 1.0 - uLeft)
                else -> arrayOf(uLeft, uTop, uRight, uBottom)
            }

            val normX = min(rx0, rx1).toFloat().coerceIn(0f, 0.95f)
            val normY = min(ry0, ry1).toFloat().coerceIn(0f, 0.96f)
            val normW = abs(rx1 - rx0).toFloat().coerceIn(0.04f, (1f - normX).coerceAtLeast(0.04f))
            val normH = abs(ry1 - ry0).toFloat().coerceIn(0.014f, 0.25f)
            val canonicalPt = (avgFont * (595.0 / visibleBoxW)).toFloat().coerceIn(8f, 72f)
            val isBold = charWeightSum > 0 && boldChars * 2 >= charWeightSum

            editableLines.add(
                PdfEditableTextLine(
                    id = "p${pageIndex}_l${spanCounter++}",
                    pageIndex = pageIndex,
                    text = finalText,
                    xFraction = normX,
                    yFraction = normY,
                    widthFraction = normW,
                    heightFraction = normH,
                    fontSizePt = canonicalPt,
                    isBold = isBold
                )
            )
        }

        for (line in visualLines) {
            val orderedRuns = line.items.sortedWith(
                compareBy<PositionedRun> { it.x }.thenBy { it.order }
            )
            // Split wide column gaps (e.g. tables, two-column headers/forms) into independent editable spans
            val currentSpan = mutableListOf<PositionedRun>()
            var prevEndX: Double? = null
            var prevFont = line.avgFontSize

            for (run in orderedRuns) {
                if (run.text.isEmpty()) continue
                if (prevEndX != null && currentSpan.isNotEmpty()) {
                    val gap = run.x - prevEndX
                    val refFont = min(prevFont, run.fontSize).coerceIn(6.0, 48.0)
                    val columnSplitThreshold = max(28.0, refFont * 3.4)
                    if (gap > columnSplitThreshold) {
                        emitSpan(currentSpan, line.baselineY)
                        currentSpan.clear()
                        prevEndX = null
                    }
                }
                currentSpan.add(run)
                val endX = run.x + max(run.width, run.fontSize * 0.35)
                prevEndX = if (prevEndX == null) endX else max(prevEndX, endX)
                prevFont = run.fontSize
            }
            if (currentSpan.isNotEmpty()) {
                emitSpan(currentSpan, line.baselineY)
            }
        }

        return editableLines
    }

    private fun inferPageBoxFromRuns(runs: List<PositionedRun>): PdfPageBox {
        val maxX = runs.maxOfOrNull { it.x + it.width } ?: 595.0
        val maxY = runs.maxOfOrNull { it.y + it.fontSize } ?: 842.0
        val minY = runs.minOfOrNull { it.y } ?: 0.0
        return when {
            maxX <= 618.0 && maxY <= 798.0 && minY >= -10.0 -> PdfPageBox(0.0, 0.0, 612.0, 792.0)
            maxX <= 605.0 && maxY <= 852.0 && minY >= -10.0 -> PdfPageBox(0.0, 0.0, 595.0, 842.0)
            else -> PdfPageBox(0.0, 0.0, max(595.0, maxX + 36.0), max(842.0, maxY + 36.0))
        }
    }

    private fun parseObjectStream(
        dictText: String,
        decodedBytes: ByteArray,
        outObjects: MutableMap<Int, String>
    ) {
        try {
            val nMatch = Regex("/N\\s+(\\d+)").find(dictText) ?: return
            val firstMatch = Regex("/First\\s+(\\d+)").find(dictText) ?: return
            val count = nMatch.groupValues[1].toIntOrNull()?.coerceIn(1, 5000) ?: return
            val firstOffset = firstMatch.groupValues[1].toIntOrNull() ?: return
            if (firstOffset <= 0 || firstOffset >= decodedBytes.size) return

            val header = String(decodedBytes, 0, firstOffset, Charsets.ISO_8859_1)
            val nums = Regex("\\d+").findAll(header).mapNotNull { it.value.toIntOrNull() }.toList()
            val pairCount = min(count, nums.size / 2)
            if (pairCount <= 0) return

            val entries = ArrayList<Pair<Int, Int>>(pairCount)
            for (i in 0 until pairCount) {
                entries.add(nums[i * 2] to nums[i * 2 + 1])
            }
            for (i in entries.indices) {
                val (objNum, relOff) = entries[i]
                val start = firstOffset + relOff
                val end = if (i + 1 < entries.size) {
                    firstOffset + entries[i + 1].second
                } else {
                    decodedBytes.size
                }
                if (start in 0 until decodedBytes.size && end > start && end <= decodedBytes.size) {
                    val len = min(end - start, 16384)
                    outObjects[objNum] = String(decodedBytes, start, len, Charsets.ISO_8859_1)
                }
            }
        } catch (_: Exception) {
            // Ignore malformed object streams gracefully
        }
    }

    private fun collectAllObjectDictionaries(
        latin: String,
        unpackedObjects: Map<Int, String>
    ): Map<Int, String> {
        val objects = HashMap<Int, String>(unpackedObjects.size + 64)
        objects.putAll(unpackedObjects)
        val objRegex = Regex("(\\d+)\\s+\\d+\\s+obj\\b(.*?)endobj", RegexOption.DOT_MATCHES_ALL)
        objRegex.findAll(latin).forEach { m ->
            val objNum = m.groupValues[1].toIntOrNull() ?: return@forEach
            val body = m.groupValues[2]
            // Strip binary stream payload if present so only the object dictionary stays in memory
            val streamIdx = body.indexOf("stream")
            val dictPart = if (streamIdx >= 0) body.substring(0, streamIdx) else body
            if (dictPart.length <= 16384) {
                objects[objNum] = dictPart
            }
        }
        return objects
    }

    private fun resolvePageDescriptors(allObjects: Map<Int, String>): List<PageDescriptor> {
        if (allObjects.isEmpty()) return emptyList()

        val pagesTypeRegex = Regex("/Type\\s*/Pages\\b")
        val pageTypeRegex = Regex("/Type\\s*/Page\\b(?!s)")
        val catalogTypeRegex = Regex("/Type\\s*/Catalog\\b")
        val refRegex = Regex("(\\d+)\\s+\\d+\\s+R")
        val kidsRegex = Regex("/Kids\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL)
        val parentRegex = Regex("/Parent\\s+(\\d+)\\s+\\d+\\s+R")
        val pagesRefRegex = Regex("/Pages\\s+(\\d+)\\s+\\d+\\s+R")
        val rotateRegex = Regex("/Rotate\\s+(-?\\d+)")
        val contentsArrayRegex = Regex("/Contents\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL)
        val contentsSingleRegex = Regex("/Contents\\s+(\\d+)\\s+\\d+\\s+R")

        fun parseBox(dict: String, key: String): PdfPageBox? {
            val boxRegex = Regex(
                "$key\\s*\\[\\s*(-?\\d+(?:\\.\\d+)?)\\s+(-?\\d+(?:\\.\\d+)?)\\s+(-?\\d+(?:\\.\\d+)?)\\s+(-?\\d+(?:\\.\\d+)?)\\s*]"
            )
            val m = boxRegex.find(dict) ?: return null
            val x0 = m.groupValues[1].toDoubleOrNull() ?: return null
            val y0 = m.groupValues[2].toDoubleOrNull() ?: return null
            val x1 = m.groupValues[3].toDoubleOrNull() ?: return null
            val y1 = m.groupValues[4].toDoubleOrNull() ?: return null
            if (abs(x1 - x0) < 10.0 || abs(y1 - y0) < 10.0) return null
            return PdfPageBox(
                x0 = min(x0, x1),
                y0 = min(y0, y1),
                x1 = max(x0, x1),
                y1 = max(y0, y1)
            )
        }

        fun extractContentRefs(dict: String): List<Int> {
            val arrMatch = contentsArrayRegex.find(dict)
            if (arrMatch != null) {
                return refRegex.findAll(arrMatch.groupValues[1])
                    .mapNotNull { it.groupValues[1].toIntOrNull() }
                    .toList()
            }
            val singleMatch = contentsSingleRegex.find(dict)
            if (singleMatch != null) {
                val id = singleMatch.groupValues[1].toIntOrNull() ?: return emptyList()
                // Handle indirect Contents array object (e.g. `12 0 obj [13 0 R 14 0 R] endobj`)
                val targetBody = allObjects[id]?.trim()
                if (targetBody != null && targetBody.startsWith("[")) {
                    val nested = refRegex.findAll(targetBody)
                        .mapNotNull { it.groupValues[1].toIntOrNull() }
                        .toList()
                    if (nested.isNotEmpty()) return nested
                }
                return listOf(id)
            }
            return emptyList()
        }

        val pagesNodes = mutableSetOf<Int>()
        val pageNodes = mutableListOf<Int>()
        var rootPagesObj: Int? = null

        for ((objNum, body) in allObjects) {
            when {
                catalogTypeRegex.containsMatchIn(body) -> {
                    val pRef = pagesRefRegex.find(body)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    if (pRef != null) rootPagesObj = pRef
                }
                pagesTypeRegex.containsMatchIn(body) -> pagesNodes.add(objNum)
                pageTypeRegex.containsMatchIn(body) -> pageNodes.add(objNum)
            }
        }

        if (rootPagesObj == null) {
            rootPagesObj = pagesNodes.firstOrNull { id ->
                val body = allObjects[id].orEmpty()
                !parentRegex.containsMatchIn(body)
            } ?: pagesNodes.firstOrNull()
        }

        val orderedPages = mutableListOf<PageDescriptor>()
        val visited = mutableSetOf<Int>()

        fun walkNode(objNum: Int, inheritedMediaBox: PdfPageBox, inheritedRotate: Int) {
            if (!visited.add(objNum)) return
            val body = allObjects[objNum] ?: return
            val localMedia = parseBox(body, "/MediaBox") ?: inheritedMediaBox
            val localCrop = parseBox(body, "/CropBox")
            val localRotate = rotateRegex.find(body)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: inheritedRotate

            if (pagesTypeRegex.containsMatchIn(body)) {
                val kidsBody = kidsRegex.find(body)?.groupValues?.getOrNull(1) ?: return
                refRegex.findAll(kidsBody).forEach { m ->
                    val kidObj = m.groupValues[1].toIntOrNull() ?: return@forEach
                    walkNode(kidObj, localMedia, localRotate)
                }
            } else if (pageTypeRegex.containsMatchIn(body)) {
                val effectiveBox = (localCrop ?: localMedia).copy(rotateDeg = localRotate)
                val contents = extractContentRefs(body)
                orderedPages.add(
                    PageDescriptor(
                        pageIndex = orderedPages.size,
                        box = effectiveBox,
                        contentObjNums = contents
                    )
                )
            }
        }

        if (rootPagesObj != null) {
            walkNode(rootPagesObj, PdfPageBox(), 0)
        }

        // If tree walk found nothing, fall back to all /Type /Page objects sorted by object number
        if (orderedPages.isEmpty() && pageNodes.isNotEmpty()) {
            pageNodes.sorted().forEach { objNum ->
                val body = allObjects[objNum] ?: return@forEach
                val box = (parseBox(body, "/CropBox") ?: parseBox(body, "/MediaBox") ?: PdfPageBox())
                    .copy(rotateDeg = rotateRegex.find(body)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0)
                orderedPages.add(
                    PageDescriptor(
                        pageIndex = orderedPages.size,
                        box = box,
                        contentObjNums = extractContentRefs(body)
                    )
                )
            }
        }

        return orderedPages
    }

    private fun buildBoldFontLookup(
        latin: String,
        allObjects: Map<Int, String>
    ): Set<String> {
        val boldFontObjs = mutableSetOf<Int>()
        val baseFontRegex = Regex("/(?:BaseFont|FontName)\\s*/([A-Za-z0-9_+\\-,]+)")
        val boldIndicatorRegex = Regex("(?i)(bold|black|heavy|semibold|demibold|extrabold|medium|-bd$|_bd$|,bold)")

        for ((objNum, body) in allObjects) {
            val match = baseFontRegex.find(body)
            if (match != null && boldIndicatorRegex.containsMatchIn(match.groupValues[1])) {
                boldFontObjs.add(objNum)
            }
        }
        if (boldFontObjs.isEmpty()) return emptySet()

        val boldResourceNames = mutableSetOf<String>()
        val fontEntryRegex = Regex("(/[A-Za-z0-9_+\\-.]+)\\s+(\\d+)\\s+\\d+\\s+R")
        val sources = sequenceOf(latin) + allObjects.values.asSequence()
        for (src in sources) {
            if (!src.contains("/Font") && !src.contains("/F")) continue
            fontEntryRegex.findAll(src).forEach { entry ->
                val refObj = entry.groupValues[2].toIntOrNull() ?: return@forEach
                if (boldFontObjs.contains(refObj)) {
                    boldResourceNames.add(entry.groupValues[1])
                }
            }
        }
        return boldResourceNames
    }

    private fun reconstructLinesFromRuns(runs: List<PositionedRun>): String {
        if (runs.isEmpty()) return ""

        // Cluster runs into visual lines by Y proximity.
        // In PDF page space (after CTM), higher Y is higher on the page -> sort descending by Y.
        val sortedByY = runs.sortedWith(
            compareByDescending<PositionedRun> { it.y }.thenBy { it.x }.thenBy { it.order }
        )

        data class VisualLine(
            var baselineY: Double,
            var avgFontSize: Double,
            val items: MutableList<PositionedRun>
        )

        val lines = mutableListOf<VisualLine>()
        for (run in sortedByY) {
            val existing = lines.lastOrNull { line ->
                val tolerance = max(2.5, min(line.avgFontSize, run.fontSize) * 0.48)
                abs(run.y - line.baselineY) <= tolerance
            }
            if (existing != null) {
                val n = existing.items.size
                existing.baselineY = (existing.baselineY * n + run.y) / (n + 1)
                existing.avgFontSize = (existing.avgFontSize * n + run.fontSize) / (n + 1)
                existing.items.add(run)
            } else {
                lines.add(VisualLine(run.y, run.fontSize, mutableListOf(run)))
            }
        }

        // Order lines top-to-bottom
        lines.sortByDescending { it.baselineY }

        val sb = StringBuilder()
        var prevLineY: Double? = null
        var prevFontSize = 12.0

        for (line in lines) {
            val orderedRuns = line.items.sortedWith(
                compareBy<PositionedRun> { it.x }.thenBy { it.order }
            )
            val lineBuilder = StringBuilder()
            var prevEndX: Double? = null
            var prevRunFont = line.avgFontSize

            for (run in orderedRuns) {
                val text = run.text
                if (text.isEmpty()) continue

                if (prevEndX != null && lineBuilder.isNotEmpty()) {
                    val gap = run.x - prevEndX
                    val refFont = min(prevRunFont, run.fontSize).coerceIn(6.0, 48.0)
                    val spaceThreshold = max(1.6, refFont * 0.22)
                    val prevEndsSpace = lineBuilder.last().isWhitespace()
                    val nextStartsSpace = text.first().isWhitespace()

                    if (!prevEndsSpace && !nextStartsSpace && gap >= spaceThreshold) {
                        lineBuilder.append(' ')
                    }
                }

                lineBuilder.append(text)
                val runEnd = run.x + run.width
                prevEndX = if (prevEndX == null) runEnd else max(prevEndX, runEnd)
                prevRunFont = run.fontSize
            }

            val finalLine = lineBuilder.toString()
                .replace(Regex("[ \\t]{2,}"), " ")
                .trim()

            if (finalLine.isEmpty()) continue
            if (!isReadableLine(finalLine)) continue

            if (sb.isNotEmpty() && prevLineY != null) {
                val yGap = abs(prevLineY - line.baselineY)
                val expectedLineStep = max(prevFontSize, line.avgFontSize) * 1.45
                if (yGap > expectedLineStep * 1.38) {
                    sb.append("\n\n")
                } else {
                    sb.append('\n')
                }
            }
            sb.append(finalLine)
            prevLineY = line.baselineY
            prevFontSize = line.avgFontSize
        }

        return sb.toString().trim()
    }

    private fun isReadableLine(line: String): Boolean {
        if (line.isBlank()) return false
        val readable = line.count {
            it.isLetterOrDigit() || it.isWhitespace() ||
                it in ".,;:!?'-()[]{}/*%&@#\"+=<>~_•–—’‘“”$€£¥°±×÷"
        }
        return readable.toFloat() / line.length >= 0.68f
    }

    /**
     * Safety net for unusual PDFs where glyphs had tiny vertical drift across consecutive lines,
     * resulting in vertical 1-character-per-line columns.
     */
    internal fun repairResidualSingleCharLines(text: String): String {
        if (text.isBlank()) return text
        val lines = text.split("\n")
        if (lines.size < 6) return text

        val out = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            val trimmed = lines[i].trim()
            if (trimmed.length == 1 && !trimmed[0].isWhitespace()) {
                var j = i
                while (j < lines.size && lines[j].trim().length == 1) {
                    j++
                }
                val runLen = j - i
                if (runLen >= 4) {
                    val stitched = (i until j).joinToString("") { lines[it].trim() }
                    out.add(stitched)
                    i = j
                    continue
                }
            }
            out.add(lines[i])
            i++
        }
        return out.joinToString("\n")
    }

    private fun estimateTextWidth(text: String, fontSize: Double, horizScale: Double): Double {
        var units = 0.0
        for (ch in text) {
            units += when (ch) {
                ' ' -> 0.27
                'i', 'l', 'I', 'j', 't', 'f', 'r', '.', ',', ':', ';', '!', '\'', '|' -> 0.29
                'm', 'w', 'M', 'W', '@', '%', '—' -> 0.78
                in 'A'..'Z' -> 0.62
                in '0'..'9' -> 0.54
                else -> 0.50
            }
        }
        return units * fontSize * horizScale
    }

    private fun extractCandidateStreams(bytes: ByteArray, latin: String): List<DecodedStream> {
        val result = mutableListOf<DecodedStream>()
        val objHeaderRegex = Regex("(\\d+)\\s+\\d+\\s+obj\\b")

        var searchPos = 0
        while (searchPos < latin.length) {
            val streamIdx = latin.indexOf("stream", searchPos)
            if (streamIdx < 0) break

            // Make sure "stream" is a standalone keyword (not "endstream" or part of an identifier)
            val prevCh = if (streamIdx > 0) latin[streamIdx - 1] else ' '
            val afterIdx = streamIdx + 6
            val nextCh = if (afterIdx < latin.length) latin[afterIdx] else ' '
            if (prevCh.isLetterOrDigit() || (nextCh != '\r' && nextCh != '\n')) {
                searchPos = streamIdx + 6
                continue
            }

            val dictLookbackStart = max(0, streamIdx - 512)
            val dictSlice = latin.substring(dictLookbackStart, streamIdx)

            // Skip binary/non-text streams quickly
            val isSkipped = dictSlice.contains("/Subtype /Image") ||
                dictSlice.contains("/Subtype/Image") ||
                dictSlice.contains("/Subtype /XML") ||
                dictSlice.contains("/Subtype/XML") ||
                dictSlice.contains("/Type /XRef") ||
                dictSlice.contains("/Type/XRef") ||
                dictSlice.contains("/Length1")

            var dataStart = afterIdx
            if (dataStart < bytes.size && bytes[dataStart] == '\r'.code.toByte()) dataStart++
            if (dataStart < bytes.size && bytes[dataStart] == '\n'.code.toByte()) dataStart++

            val endStreamIdx = latin.indexOf("endstream", dataStart)
            if (endStreamIdx < 0) break

            if (!isSkipped) {
                val streamLength = (endStreamIdx - dataStart).coerceAtLeast(0)
                if (streamLength in 1..(6 * 1024 * 1024)) {
                    val isFlate = dictSlice.contains("/FlateDecode")
                    val hasOtherFilter = dictSlice.contains("/DCTDecode") ||
                        dictSlice.contains("/JPXDecode") ||
                        dictSlice.contains("/CCITTFaxDecode") ||
                        dictSlice.contains("/JBIG2Decode")

                    if (!hasOtherFilter) {
                        val rawSlice = bytes.copyOfRange(dataStart, endStreamIdx)
                        val decoded = if (isFlate) inflateStreamBytes(rawSlice) else rawSlice
                        if (decoded != null && decoded.isNotEmpty()) {
                            val objMatch = objHeaderRegex.findAll(dictSlice).lastOrNull()
                            val objNum = objMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
                            result.add(DecodedStream(objNum, dictSlice, decoded))
                        }
                    }
                }
            }

            searchPos = endStreamIdx + 9
        }
        return result
    }

    private fun looksLikeTextContentStream(content: String): Boolean {
        return content.contains("BT") && (
            content.contains("Tj") ||
                content.contains("TJ") ||
                content.contains(")\'") ||
                content.contains(">\'")
            )
    }

    private fun buildFontNameToCMapLookup(
        latin: String,
        cmapsByObjNum: Map<Int, FontCMap>,
        allObjects: Map<Int, String> = emptyMap()
    ): Map<String, FontCMap> {
        if (cmapsByObjNum.isEmpty()) return emptyMap()

        // 1. Map fontObjNum -> toUnicodeObjNum (from both plaintext objects and unpacked /ObjStm)
        val fontObjToCMapObj = mutableMapOf<Int, Int>()
        val toUnicodeRegex = Regex("/ToUnicode\\s+(\\d+)\\s+\\d+\\s+R")

        for ((fontObjNum, objBody) in allObjects) {
            if (objBody.length <= 4096 && objBody.contains("/ToUnicode")) {
                val tuMatch = toUnicodeRegex.find(objBody)
                val tuObjNum = tuMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (tuObjNum != null) {
                    fontObjToCMapObj[fontObjNum] = tuObjNum
                }
            }
        }

        // 2. Map /FontName -> fontObjNum from /Font << /F1 12 0 R ... >>
        val fontNameToCMap = mutableMapOf<String, FontCMap>()
        val fontEntryRegex = Regex("(/[A-Za-z0-9_+\\-.]+)\\s+(\\d+)\\s+\\d+\\s+R")
        val fontDictRegex = Regex("/Font\\s*<<([^>]{1,4096})>>")
        val searchTexts = sequenceOf(latin) + allObjects.values.asSequence()

        for (src in searchTexts) {
            fontDictRegex.findAll(src).forEach { dictMatch ->
                val inner = dictMatch.groupValues[1]
                fontEntryRegex.findAll(inner).forEach { entry ->
                    val name = entry.groupValues[1]
                    val fontObj = entry.groupValues[2].toIntOrNull() ?: return@forEach
                    val cmapObj = fontObjToCMapObj[fontObj]
                    val cmap = cmapObj?.let { cmapsByObjNum[it] }
                    if (cmap != null) {
                        fontNameToCMap[name] = cmap
                    }
                }
            }
        }

        // Also match indirect font dictionaries (e.g. << /F1 12 0 R /F2 15 0 R >>)
        if (fontObjToCMapObj.isNotEmpty()) {
            for (src in searchTexts) {
                fontEntryRegex.findAll(src).forEach { entry ->
                    val name = entry.groupValues[1]
                    if (!fontNameToCMap.containsKey(name)) {
                        val refObj = entry.groupValues[2].toIntOrNull() ?: return@forEach
                        val cmapObj = fontObjToCMapObj[refObj]
                        val cmap = cmapObj?.let { cmapsByObjNum[it] }
                        if (cmap != null) {
                            fontNameToCMap[name] = cmap
                        }
                    }
                }
            }
        }

        return fontNameToCMap
    }

    internal fun parseToUnicodeCMap(cmapText: String): FontCMap {
        val cmap = FontCMap()

        // beginbfchar ... endbfchar
        val bfCharBlockRegex = Regex("beginbfchar(.*?)endbfchar", RegexOption.DOT_MATCHES_ALL)
        val bfCharEntryRegex = Regex("<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>")

        bfCharBlockRegex.findAll(cmapText).forEach { block ->
            bfCharEntryRegex.findAll(block.groupValues[1]).forEach { entry ->
                val srcHex = entry.groupValues[1]
                val dstHex = entry.groupValues[2]
                val srcCode = srcHex.toIntOrNull(16) ?: return@forEach
                val unicodeStr = decodeUtf16BeHex(dstHex)
                if (unicodeStr.isNotEmpty()) {
                    if (srcHex.length <= 2) {
                        cmap.oneByte[srcCode] = unicodeStr
                    } else {
                        cmap.twoByte[srcCode] = unicodeStr
                    }
                }
            }
        }

        // beginbfrange ... endbfrange
        val bfRangeBlockRegex = Regex("beginbfrange(.*?)endbfrange", RegexOption.DOT_MATCHES_ALL)
        val bfRangeSimpleRegex = Regex("<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>")
        val bfRangeArrayRegex = Regex("<([0-9A-Fa-f]+)>\\s*<([0-9A-Fa-f]+)>\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL)
        val hexItemRegex = Regex("<([0-9A-Fa-f]+)>")

        bfRangeBlockRegex.findAll(cmapText).forEach { block ->
            val body = block.groupValues[1]
            bfRangeSimpleRegex.findAll(body).forEach { entry ->
                val startHex = entry.groupValues[1]
                val endHex = entry.groupValues[2]
                val dstStartHex = entry.groupValues[3]
                val start = startHex.toIntOrNull(16) ?: return@forEach
                val end = endHex.toIntOrNull(16) ?: return@forEach
                val dstStart = dstStartHex.toIntOrNull(16) ?: return@forEach
                val count = (end - start).coerceIn(0, 512)
                val targetMap = if (startHex.length <= 2) cmap.oneByte else cmap.twoByte
                for (offset in 0..count) {
                    val cp = dstStart + offset
                    if (cp in 1..0xFFFF) {
                        targetMap[start + offset] = cp.toChar().toString()
                    }
                }
            }
            bfRangeArrayRegex.findAll(body).forEach { entry ->
                val startHex = entry.groupValues[1]
                val start = startHex.toIntOrNull(16) ?: return@forEach
                val targetMap = if (startHex.length <= 2) cmap.oneByte else cmap.twoByte
                var idx = 0
                hexItemRegex.findAll(entry.groupValues[3]).forEach { hexMatch ->
                    val unicodeStr = decodeUtf16BeHex(hexMatch.groupValues[1])
                    if (unicodeStr.isNotEmpty()) {
                        targetMap[start + idx] = unicodeStr
                    }
                    idx++
                }
            }
        }

        return cmap
    }

    private fun decodeUtf16BeHex(hexRaw: String): String {
        val hex = hexRaw.filter { !it.isWhitespace() }
        if (hex.isEmpty()) return ""
        val padded = if (hex.length % 2 == 1) "0$hex" else hex
        val bytes = ByteArray(padded.length / 2)
        for (i in bytes.indices) {
            bytes[i] = (padded.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: 0).toByte()
        }
        return if (bytes.size == 1) {
            (bytes[0].toInt() and 0xFF).toChar().toString()
        } else {
            String(bytes, Charsets.UTF_16BE)
        }
    }

    private fun tokenizeContentStream(stream: String): List<PdfToken> {
        val tokens = mutableListOf<PdfToken>()
        val len = stream.length
        var i = 0

        fun skipWsAndComments() {
            while (i < len) {
                val c = stream[i]
                if (c == '%') {
                    while (i < len && stream[i] != '\n' && stream[i] != '\r') i++
                } else if (c.isWhitespace() || c == '\u0000') {
                    i++
                } else {
                    break
                }
            }
        }

        fun readLiteralBytes(): ByteArray {
            val out = ByteArrayOutputStream()
            i++ // skip '('
            var depth = 1
            while (i < len && depth > 0) {
                val c = stream[i]
                if (c == '\\' && i + 1 < len) {
                    val next = stream[i + 1]
                    when (next) {
                        'n' -> { out.write('\n'.code); i += 2 }
                        'r' -> { out.write('\r'.code); i += 2 }
                        't' -> { out.write('\t'.code); i += 2 }
                        'b' -> { out.write('\b'.code); i += 2 }
                        'f' -> { out.write(12); i += 2 }
                        '(', ')', '\\' -> { out.write(next.code); i += 2 }
                        '\r' -> {
                            i += 2
                            if (i < len && stream[i] == '\n') i++
                        }
                        '\n' -> { i += 2 }
                        in '0'..'7' -> {
                            var oct = 0
                            var digits = 0
                            var k = i + 1
                            while (k < len && digits < 3 && stream[k] in '0'..'7') {
                                oct = (oct shl 3) + (stream[k] - '0')
                                k++
                                digits++
                            }
                            out.write(oct and 0xFF)
                            i = k
                        }
                        else -> { out.write(next.code); i += 2 }
                    }
                } else if (c == '(') {
                    depth++
                    out.write(c.code)
                    i++
                } else if (c == ')') {
                    depth--
                    if (depth > 0) out.write(c.code)
                    i++
                } else {
                    out.write(c.code and 0xFF)
                    i++
                }
            }
            return out.toByteArray()
        }

        fun readHexBytes(): ByteArray {
            i++ // skip '<'
            val hex = StringBuilder()
            while (i < len && stream[i] != '>') {
                val c = stream[i]
                if (c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F') {
                    hex.append(c)
                }
                i++
            }
            if (i < len && stream[i] == '>') i++
            if (hex.isEmpty()) return ByteArray(0)
            if (hex.length % 2 == 1) hex.append('0')
            val out = ByteArray(hex.length / 2)
            for (k in out.indices) {
                out[k] = (hex.substring(k * 2, k * 2 + 2).toIntOrNull(16) ?: 0).toByte()
            }
            return out
        }

        fun readWord(): String {
            val start = i
            while (i < len) {
                val c = stream[i]
                if (c.isWhitespace() || c in "()<>[]{}/%") break
                i++
            }
            return stream.substring(start, i)
        }

        fun readToken(): PdfToken? {
            skipWsAndComments()
            if (i >= len) return null
            val c = stream[i]
            return when (c) {
                '(' -> PdfToken.Str(readLiteralBytes(), isHex = false)
                '<' -> {
                    if (i + 1 < len && stream[i + 1] == '<') {
                        i += 2
                        PdfToken.Op("<<")
                    } else {
                        PdfToken.Str(readHexBytes(), isHex = true)
                    }
                }
                '>' -> {
                    if (i + 1 < len && stream[i + 1] == '>') {
                        i += 2
                        PdfToken.Op(">>")
                    } else {
                        i++
                        null
                    }
                }
                '[' -> {
                    i++ // skip '['
                    val items = mutableListOf<PdfToken>()
                    while (i < len) {
                        skipWsAndComments()
                        if (i >= len) break
                        if (stream[i] == ']') {
                            i++
                            break
                        }
                        val item = readToken()
                        if (item != null) items.add(item) else i++
                    }
                    PdfToken.Arr(items)
                }
                ']' -> {
                    i++
                    null
                }
                '/' -> {
                    i++
                    val name = readWord()
                    PdfToken.Name("/$name")
                }
                else -> {
                    val word = readWord()
                    if (word.isEmpty()) {
                        i++
                        null
                    } else {
                        val num = word.toDoubleOrNull()
                        if (num != null) PdfToken.Num(num) else PdfToken.Op(word)
                    }
                }
            }
        }

        while (i < len) {
            val tok = readToken()
            if (tok != null) tokens.add(tok)
        }

        return tokens
    }

    private fun decodePdfBytesFallback(bytes: ByteArray, isHex: Boolean): String {
        if (bytes.isEmpty()) return ""
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        // Check if UTF-16BE without BOM (e.g., <00480065006C006C006F>)
        if (isHex && bytes.size >= 2 && bytes.size % 2 == 0) {
            var zeroHighBytes = 0
            for (k in bytes.indices step 2) {
                if (bytes[k].toInt() == 0) zeroHighBytes++
            }
            if (zeroHighBytes * 2 >= bytes.size / 2) {
                val utf16 = String(bytes, Charsets.UTF_16BE)
                if (utf16.any { it.isLetterOrDigit() }) return utf16
            }
        }
        // WinAnsi / PDFDocEncoding common punctuation + ISO-8859-1
        val sb = StringBuilder(bytes.size)
        for (b in bytes) {
            val code = b.toInt() and 0xFF
            when (code) {
                0x85 -> sb.append("...")
                0x91 -> sb.append('\u2018')
                0x92 -> sb.append('\u2019')
                0x93 -> sb.append('\u201C')
                0x94 -> sb.append('\u201D')
                0x95 -> sb.append('\u2022')
                0x96 -> sb.append('\u2013')
                0x97 -> sb.append('\u2014')
                0xA0 -> sb.append(' ')
                else -> if (code in 32..126 || code >= 160) sb.append(code.toChar())
            }
        }
        return sb.toString()
    }

    private fun cleanControlChars(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            when {
                ch == '\t' -> sb.append(' ')
                ch == '\r' || ch == '\n' -> sb.append(' ')
                ch.code in 32..126 || ch.code in 160..65533 -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun inflateStreamBytes(compressed: ByteArray): ByteArray? {
        val inflater = Inflater()
        return try {
            inflater.setInput(compressed)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var guard = 0
            while (!inflater.finished() && guard < 4096) {
                val count = inflater.inflate(buf)
                if (count <= 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                } else {
                    out.write(buf, 0, count)
                }
                guard++
            }
            if (out.size() > 0) out.toByteArray() else null
        } catch (_: Exception) {
            null
        } finally {
            inflater.end()
        }
    }
}
