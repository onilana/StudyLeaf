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
}
