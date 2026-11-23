package com.example.data.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfTextExtractorTest {

    @Test
    fun perCharacterTdDoesNotSplitIntoOneCharPerLine() {
        val stream = """
            BT
            /F1 12 Tf
            72 720 Td (S) Tj
            7 0 Td (t) Tj
            4 0 Td (u) Tj
            6 0 Td (d) Tj
            6 0 Td (y) Tj
            12 0 Td (L) Tj
            6 0 Td (e) Tj
            6 0 Td (a) Tj
            4 0 Td (f) Tj
            ET
        """.trimIndent()

        val extracted = PdfTextExtractor.extractTextFromContentStream(stream)
        assertEquals("Study Leaf", extracted)
        assertFalse(extracted.contains("\n"))
    }

    @Test
    fun perCharacterTmAndSeparateBtBlocksStayOnSameLine() {
        val stream = """
            BT /F1 12 Tf 1 0 0 1 72 700 Tm (H) Tj ET
            BT /F1 12 Tf 1 0 0 1 80 700 Tm (e) Tj ET
            BT /F1 12 Tf 1 0 0 1 86 700 Tm (l) Tj ET
            BT /F1 12 Tf 1 0 0 1 89 700 Tm (l) Tj ET
            BT /F1 12 Tf 1 0 0 1 92 700 Tm (o) Tj ET
            BT /F1 12 Tf 1 0 0 1 106 700 Tm (P) Tj ET
            BT /F1 12 Tf 1 0 0 1 114 700 Tm (D) Tj ET
            BT /F1 12 Tf 1 0 0 1 122 700 Tm (F) Tj ET
        """.trimIndent()

        val extracted = PdfTextExtractor.extractTextFromContentStream(stream)
        assertEquals("Hello PDF", extracted)
    }

    @Test
    fun tjKerningArrayPreservesWordsAndInsertsWordSpaces() {
        val stream = """
            BT
            /F1 12 Tf
            72 720 Td
            [(P) -15 (D) 10 (F) -260 (E) -10 (d) (i) (t) (o) (r)] TJ
            0 -18 Td
            [(S) (e) (c) (o) (n) (d) -240 (L) (i) (n) (e)] TJ
            ET
        """.trimIndent()

        val extracted = PdfTextExtractor.extractTextFromContentStream(stream)
        assertEquals("PDF Editor\nSecond Line", extracted)
    }

    @Test
    fun toUnicodeCMapDecodesHexGlyphIds() {
        val cmapText = """
            begincmap
            2 beginbfchar
            <0003> <0020>
            <0024> <0041>
            endbfchar
            1 beginbfrange
            <0044> <0046> <0061>
            endbfrange
            endcmap
        """.trimIndent()

        val cmap = PdfTextExtractor.parseToUnicodeCMap(cmapText)
        val stream = """
            BT
            /F1 12 Tf
            72 720 Td
            <00240003004400450046> Tj
            ET
        """.trimIndent()

        val extracted = PdfTextExtractor.extractTextFromContentStream(
            stream = stream,
            fontCMaps = mapOf("/F1" to cmap)
        )
        assertEquals("A abc", extracted)
    }

    @Test
    fun flippedSkiaCtmOrdersLinesTopToBottom() {
        val stream = """
            q
            1 0 0 -1 0 842 cm
            BT
            /F1 12 Tf
            1 0 0 1 44 100 Tm (Top Line) Tj
            1 0 0 1 44 120 Tm (Bottom Line) Tj
            ET
            Q
        """.trimIndent()

        val extracted = PdfTextExtractor.extractTextFromContentStream(stream)
        assertEquals("Top Line\nBottom Line", extracted)
    }

    @Test
    fun repairResidualSingleCharLinesStitchesVerticalStack() {
        val broken = "C\nh\na\np\nt\ne\nr"
        val repaired = PdfTextExtractor.repairResidualSingleCharLines(broken)
        assertTrue(repaired == "Chapter")
    }

    @Test
    fun extractPositionedLinesSplitsWideTableColumnsAndMapsNormalizedBounds() {
        val stream = """
            BT
            /F1 14 Tf
            50 750 Td (Invoice #1042) Tj
            ET
            BT
            /F1 14 Tf
            380 750 Td (Total: $250.00) Tj
            ET
        """.trimIndent()

        val lines = PdfTextExtractor.extractPositionedLinesFromContentStream(
            stream = stream,
            pageIndex = 0,
            box = PdfTextExtractor.PdfPageBox(0.0, 0.0, 595.0, 842.0),
            boldFonts = setOf("/F1")
        )

        assertEquals(2, lines.size)
        assertEquals("Invoice #1042", lines[0].text)
        assertEquals("Total: $250.00", lines[1].text)
        assertTrue(lines[0].isBold)
        assertTrue(lines[0].xFraction < 0.12f)
        assertTrue(lines[1].xFraction > 0.60f)
        // Both sit near the top of the 842pt page (~10% down)
        assertTrue(lines[0].yFraction in 0.07f..0.14f)
        assertTrue(lines[1].yFraction in 0.07f..0.14f)
        assertEquals(14f, lines[0].fontSizePt, 0.5f)
    }

    @Test
    fun extractPositionedLinesMapsMultiPagePdfTreeAndBoldFonts() {
        val page1Stream = "BT /F1 18 Tf 60 760 Td (Chapter One) Tj ET\nBT /F2 12 Tf 60 720 Td (Intro body line) Tj ET"
        val page2Stream = "q 1 0 0 -1 0 842 cm BT /F2 12 Tf 1 0 0 -1 50 110 Tm (Second Page Top) Tj ET Q"

        val fakePdf = """
            %PDF-1.4
            1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
            2 0 obj << /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 /MediaBox [0 0 595 842] >> endobj
            3 0 obj << /Type /Page /Parent 2 0 R /Resources << /Font << /F1 5 0 R /F2 6 0 R >> >> /Contents 7 0 R >> endobj
            4 0 obj << /Type /Page /Parent 2 0 R /Resources << /Font << /F2 6 0 R >> >> /Contents 8 0 R >> endobj
            5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >> endobj
            6 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj
            7 0 obj << /Length ${page1Stream.length} >>
            stream
            $page1Stream
            endstream
            endobj
            8 0 obj << /Length ${page2Stream.length} >>
            stream
            $page2Stream
            endstream
            endobj
            %%EOF
        """.trimIndent().toByteArray(Charsets.ISO_8859_1)

        val lines = PdfTextExtractor.extractPositionedLinesFromBytes(fakePdf)
        assertEquals(3, lines.size)

        val p0Lines = lines.filter { it.pageIndex == 0 }
        val p1Lines = lines.filter { it.pageIndex == 1 }
        assertEquals(2, p0Lines.size)
        assertEquals(1, p1Lines.size)

        assertEquals("Chapter One", p0Lines[0].text)
        assertTrue(p0Lines[0].isBold)
        assertEquals(18f, p0Lines[0].fontSizePt, 0.5f)
        assertEquals("Intro body line", p0Lines[1].text)
        assertFalse(p0Lines[1].isBold)
        assertTrue(p0Lines[0].yFraction < p0Lines[1].yFraction)

        assertEquals("Second Page Top", p1Lines[0].text)
        assertTrue(p1Lines[0].yFraction in 0.08f..0.16f)
    }
}
