package com.example.data.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class DocxInPlaceEditorTest {

    @Test
    fun saveEditedDocx_preservesEmbeddedImagesAlignmentAndStyles() {
        val tempDir = java.nio.file.Files.createTempDirectory("studyleaf_docx_test").toFile()
        try {
            val source = File(tempDir, "original.docx")
            val dest = File(tempDir, "edited.docx")
            val fakePngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4)

            val originalXml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                  <w:body>
                    <w:p>
                      <w:pPr><w:jc w:val="center"/></w:pPr>
                      <w:r><w:rPr><w:b/><w:color w:val="006C48"/></w:rPr><w:t>Original Heading</w:t></w:r>
                    </w:p>
                    <w:p>
                      <w:r><w:drawing><a:blip r:embed="rId1"/></w:drawing></w:r>
                    </w:p>
                    <w:p>
                      <w:pPr><w:jc w:val="right"/></w:pPr>
                      <w:r><w:t>Old </w:t></w:r><w:r><w:t>Footer Text</w:t></w:r>
                    </w:p>
                  </w:body>
                </w:document>
            """.trimIndent()

            ZipOutputStream(FileOutputStream(source)).use { zos ->
                zos.putNextEntry(ZipEntry("word/document.xml"))
                zos.write(originalXml.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("word/media/image1.png"))
                zos.write(fakePngBytes)
                zos.closeEntry()

                zos.putNextEntry(ZipEntry("word/styles.xml"))
                zos.write("<w:styles><w:style w:styleId=\"Title\"/></w:styles>".toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            val parsed = DocxInPlaceEditor.parseDocx(source)
            assertNotNull(parsed)
            assertEquals(1, parsed!!.embeddedImageCount)
            assertEquals(3, parsed.blocks.size)
            assertEquals("center", parsed.blocks[0].alignment)
            assertEquals("Original Heading", parsed.blocks[0].text)
            assertTrue(parsed.blocks[1].hasImage)
            assertEquals("right", parsed.blocks[2].alignment)
            assertEquals("Old Footer Text", parsed.blocks[2].text)

            val savedOk = DocxInPlaceEditor.saveEditedDocxPreservingFormat(
                sourceDocx = source,
                destDocx = dest,
                editedParagraphsByIndex = mapOf(
                    parsed.blocks[0].paragraphIndex to "Updated Heading & Notes",
                    parsed.blocks[2].paragraphIndex to "New Aligned Footer"
                )
            )
            assertTrue(savedOk)
            assertTrue(dest.exists())

            var patchedXml = ""
            var preservedImageBytes: ByteArray? = null
            var preservedStylesXml: String? = null

            ZipInputStream(FileInputStream(dest)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    when (entry.name) {
                        "word/document.xml" -> patchedXml = zis.readBytes().toString(Charsets.UTF_8)
                        "word/media/image1.png" -> preservedImageBytes = zis.readBytes()
                        "word/styles.xml" -> preservedStylesXml = zis.readBytes().toString(Charsets.UTF_8)
                    }
                    entry = zis.nextEntry
                }
            }

            // 1. Embedded image bytes must be byte-for-byte identical
            assertNotNull(preservedImageBytes)
            assertArrayEquals(fakePngBytes, preservedImageBytes)

            // 2. Styles XML must remain untouched
            assertEquals("<w:styles><w:style w:styleId=\"Title\"/></w:styles>", preservedStylesXml)

            // 3. Alignment, run styling, and drawing XML tags must remain intact
            assertTrue(patchedXml.contains("<w:jc w:val=\"center\"/>"))
            assertTrue(patchedXml.contains("<w:jc w:val=\"right\"/>"))
            assertTrue(patchedXml.contains("<w:color w:val=\"006C48\"/>"))
            assertTrue(patchedXml.contains("<w:drawing><a:blip r:embed=\"rId1\"/></w:drawing>"))

            // 4. Edited text must be properly XML-escaped and readable on re-parse
            val reparsed = DocxInPlaceEditor.parseDocx(dest)
            assertNotNull(reparsed)
            assertEquals(1, reparsed!!.embeddedImageCount)
            assertEquals("Updated Heading & Notes", reparsed.blocks[0].text)
            assertEquals("center", reparsed.blocks[0].alignment)
            assertTrue(reparsed.blocks[1].hasImage)
            assertEquals("New Aligned Footer", reparsed.blocks[2].text)
            assertEquals("right", reparsed.blocks[2].alignment)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
