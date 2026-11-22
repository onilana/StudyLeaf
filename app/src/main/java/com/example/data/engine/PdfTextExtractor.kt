package com.example.data.engine

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Inflater
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Pure-Kotlin, on-device PDF text extractor with:
 * - FlateDecode stream inflation
 * - Per-font /ToUnicode CMap parsing (beginbfchar & beginbfrange)
 * - Full PDF text state machine (CTM q/Q/cm, Tm/Tlm, Td/TD/T*, Tf, TL, Tj, TJ kerning, ', ")
 * - 2D spatial line & word reconstruction so per-glyph Td/Tm/BT blocks never split into 1 char/line
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
        val order: Int
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

    internal fun extractTextFromBytes(bytes: ByteArray): String {
        val latin = String(bytes, Charsets.ISO_8859_1)

        // 1. Scan all streams (inflating FlateDecode streams, skipping binary images/fonts/metadata)
        val streams = extractCandidateStreams(bytes, latin)

        // 2. Parse ToUnicode CMaps
        val cmapsByObjNum = mutableMapOf<Int, FontCMap>()
        val fallbackCMap = FontCMap()
        val contentStreams = mutableListOf<String>()

        for (stream in streams) {
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
        val fontNameToCMap = buildFontNameToCMapLookup(latin, cmapsByObjNum)

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
        val tokens = tokenizeContentStream(stream)
        if (tokens.isEmpty()) return ""

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
                    order = runOrder++
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

        return reconstructLinesFromRuns(runs)
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
        cmapsByObjNum: Map<Int, FontCMap>
    ): Map<String, FontCMap> {
        if (cmapsByObjNum.isEmpty()) return emptyMap()

        // 1. Map fontObjNum -> toUnicodeObjNum
        val fontObjToCMapObj = mutableMapOf<Int, Int>()
        val objRegex = Regex("(\\d+)\\s+\\d+\\s+obj\\b(.*?)endobj", RegexOption.DOT_MATCHES_ALL)
        val toUnicodeRegex = Regex("/ToUnicode\\s+(\\d+)\\s+\\d+\\s+R")

        objRegex.findAll(latin).forEach { m ->
            val objBody = m.groupValues[2]
            if (objBody.length <= 4096 && objBody.contains("/ToUnicode")) {
                val fontObjNum = m.groupValues[1].toIntOrNull() ?: return@forEach
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

        fontDictRegex.findAll(latin).forEach { dictMatch ->
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

        // Also match indirect font dictionaries (e.g. << /F1 12 0 R /F2 15 0 R >>)
        if (fontObjToCMapObj.isNotEmpty()) {
            fontEntryRegex.findAll(latin).forEach { entry ->
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
