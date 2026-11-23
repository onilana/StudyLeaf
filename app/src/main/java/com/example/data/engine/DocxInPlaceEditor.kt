package com.example.data.engine

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class DocxEditableBlock(
    val paragraphIndex: Int,
    val originalText: String,
    val text: String,
    val hasImage: Boolean,
    val alignment: String // "left", "center", "right", "both"
)

data class DocxParsedDocument(
    val blocks: List<DocxEditableBlock>,
    val embeddedImageCount: Int
)

/**
 * Edits existing .docx files in-place inside their ZIP package, modifying ONLY the text nodes
 * (<w:t>) of changed paragraphs in word/document.xml while keeping 100% of embedded images
 * (word/media assets), styles, fonts, colors, tables, headers/footers, and original alignments intact.
 */
object DocxInPlaceEditor {

    private data class ParagraphSpan(
        val index: Int,
        val start: Int,
        val end: Int,
        val rawXml: String
    )

    fun parseDocx(file: File): DocxParsedDocument? {
        if (!file.exists() || !file.name.lowercase().endsWith(".docx")) return null
        return try {
            var documentXml: String? = null
            var imageCount = 0

            ZipInputStream(FileInputStream(file)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name == "word/document.xml") {
                        documentXml = zis.readBytes().toString(Charsets.UTF_8)
                    } else if (name.startsWith("word/media/") && !entry.isDirectory) {
                        imageCount++
                    }
                    entry = zis.nextEntry
                }
            }

            val xml = documentXml ?: return null
            val spans = findLeafParagraphSpans(xml)
            val blocks = mutableListOf<DocxEditableBlock>()

            for (span in spans) {
                val hasImg = span.rawXml.contains("<w:drawing") ||
                    span.rawXml.contains("<w:pict") ||
                    span.rawXml.contains("<v:imagedata") ||
                    span.rawXml.contains("<a:blip")
                val text = extractParagraphText(span.rawXml)
                val align = extractParagraphAlignment(span.rawXml)
                if (text.isNotBlank() || hasImg) {
                    blocks.add(
                        DocxEditableBlock(
                            paragraphIndex = span.index,
                            originalText = text,
                            text = text,
                            hasImage = hasImg,
                            alignment = align
                        )
                    )
                }
            }

            DocxParsedDocument(blocks = blocks, embeddedImageCount = imageCount)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Copies every ZIP entry (images, styles, themes, relationships, headers, footers) verbatim
     * from [sourceDocx] to [destDocx], patching ONLY the modified <w:t> text inside word/document.xml.
     */
    fun saveEditedDocxPreservingFormat(
        sourceDocx: File,
        destDocx: File,
        editedParagraphsByIndex: Map<Int, String>
    ): Boolean {
        if (!sourceDocx.exists() || !sourceDocx.name.lowercase().endsWith(".docx")) return false
        val tempOut = File(destDocx.parentFile, "${destDocx.name}.tmp_${System.currentTimeMillis()}")
        return try {
            var foundDocumentXml = false
            ZipInputStream(FileInputStream(sourceDocx)).use { zis ->
                ZipOutputStream(FileOutputStream(tempOut)).use { zos ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        val outEntry = ZipEntry(name)
                        zos.putNextEntry(outEntry)

                        if (name == "word/document.xml") {
                            foundDocumentXml = true
                            val originalXml = zis.readBytes().toString(Charsets.UTF_8)
                            val updatedXml = patchDocumentXml(originalXml, editedParagraphsByIndex)
                            zos.write(updatedXml.toByteArray(Charsets.UTF_8))
                        } else if (!entry.isDirectory) {
                            val buf = ByteArray(8192)
                            var read = zis.read(buf)
                            while (read >= 0) {
                                if (read > 0) zos.write(buf, 0, read)
                                read = zis.read(buf)
                            }
                        }

                        zos.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            if (!foundDocumentXml) {
                tempOut.delete()
                return false
            }

            if (destDocx.exists()) destDocx.delete()
            if (!tempOut.renameTo(destDocx)) {
                tempOut.copyTo(destDocx, overwrite = true)
                tempOut.delete()
            }
            true
        } catch (_: Exception) {
            runCatching { tempOut.delete() }
            false
        }
    }

    internal fun patchDocumentXml(
        originalXml: String,
        editedParagraphsByIndex: Map<Int, String>
    ): String {
        if (editedParagraphsByIndex.isEmpty()) return originalXml
        val spans = findLeafParagraphSpans(originalXml)
        if (spans.isEmpty()) return originalXml

        val sb = StringBuilder(originalXml.length + 256)
        var cursor = 0
        for (span in spans) {
            sb.append(originalXml, cursor, span.start)
            val newText = editedParagraphsByIndex[span.index]
            if (newText != null) {
                val oldText = extractParagraphText(span.rawXml)
                if (newText != oldText) {
                    sb.append(replaceParagraphTextPreservingXml(span.rawXml, newText))
                } else {
                    sb.append(span.rawXml)
                }
            } else {
                sb.append(span.rawXml)
            }
            cursor = span.end
        }
        sb.append(originalXml, cursor, originalXml.length)
        return sb.toString()
    }

    /**
     * Finds non-overlapping leaf <w:p>...</w:p> spans (so nested paragraphs inside drawing textboxes
     * don't corrupt outer paragraph boundaries).
     */
    private fun findLeafParagraphSpans(xml: String): List<ParagraphSpan> {
        val spans = mutableListOf<ParagraphSpan>()
        val openTagRegex = Regex("<w:p(?:\\s[^>]*)?>")
        val closeTag = "</w:p>"
        var pos = 0
        var idx = 0

        while (pos < xml.length) {
            val openMatch = openTagRegex.find(xml, pos) ?: break
            val openStart = openMatch.range.first
            val openEnd = openMatch.range.last + 1

            // Self-closing <w:p .../>
            if (openMatch.value.endsWith("/>")) {
                pos = openEnd
                continue
            }

            // Walk matching depth to find the exact closing </w:p>
            var depth = 1
            var scan = openEnd
            var nestedFound = false
            while (scan < xml.length && depth > 0) {
                val nextOpen = openTagRegex.find(xml, scan)
                val nextCloseIdx = xml.indexOf(closeTag, scan)
                if (nextCloseIdx < 0) break

                if (nextOpen != null && nextOpen.range.first < nextCloseIdx) {
                    if (!nextOpen.value.endsWith("/>")) {
                        depth++
                        nestedFound = true
                    }
                    scan = nextOpen.range.last + 1
                } else {
                    depth--
                    scan = nextCloseIdx + closeTag.length
                }
            }

            if (depth == 0) {
                if (!nestedFound) {
                    spans.add(
                        ParagraphSpan(
                            index = idx++,
                            start = openStart,
                            end = scan,
                            rawXml = xml.substring(openStart, scan)
                        )
                    )
                    pos = scan
                } else {
                    // Outer paragraph wraps a nested <w:p> (e.g. drawing textbox): skip outer tag and parse inner leaf paragraphs safely
                    pos = openEnd
                }
            } else {
                break
            }
        }
        return spans
    }

    private fun extractParagraphText(pXml: String): String {
        val textRegex = Regex("<w:t(?:\\s[^>]*)?>(.*?)</w:t>|<w:tab\\s*/>|<w:br(?:\\s[^>]*)?/>", RegexOption.DOT_MATCHES_ALL)
        val sb = StringBuilder()
        textRegex.findAll(pXml).forEach { m ->
            when {
                m.value.startsWith("<w:tab") -> sb.append("\t")
                m.value.startsWith("<w:br") -> sb.append("\n")
                else -> sb.append(unescapeXml(m.groupValues[1]))
            }
        }
        return sb.toString()
    }

    private fun extractParagraphAlignment(pXml: String): String {
        val match = Regex("<w:jc\\s+[^>]*w:val=\"([^\"]+)\"").find(pXml)
        return match?.groupValues?.getOrNull(1)?.lowercase() ?: "left"
    }

    private fun replaceParagraphTextPreservingXml(pXml: String, newText: String): String {
        val tRegex = Regex("<w:t(?:\\s[^>]*)?>(.*?)</w:t>", RegexOption.DOT_MATCHES_ALL)
        val matches = tRegex.findAll(pXml).toList()

        if (matches.isEmpty()) {
            // Paragraph originally had no <w:t> (e.g. image-only paragraph): append a clean <w:r><w:t> before </w:p>
            if (newText.isBlank()) return pXml
            val closeIdx = pXml.lastIndexOf("</w:p>")
            if (closeIdx < 0) return pXml
            val runXml = "<w:r><w:t xml:space=\"preserve\">${escapeXml(newText)}</w:t></w:r>"
            return pXml.substring(0, closeIdx) + runXml + pXml.substring(closeIdx)
        }

        val sb = StringBuilder(pXml.length + newText.length)
        var cursor = 0
        matches.forEachIndexed { i, m ->
            sb.append(pXml, cursor, m.range.first)
            if (i == 0) {
                // Keep exact first run's <w:rPr> formatting, write full newText with xml:space="preserve"
                val lines = newText.replace("\r\n", "\n").split("\n")
                if (lines.size == 1) {
                    sb.append("<w:t xml:space=\"preserve\">${escapeXml(lines[0])}</w:t>")
                } else {
                    lines.forEachIndexed { lineIdx, line ->
                        if (lineIdx > 0) sb.append("<w:br/>")
                        sb.append("<w:t xml:space=\"preserve\">${escapeXml(line)}</w:t>")
                    }
                }
            } else {
                // Clear subsequent <w:t> fragments from the old split runs without touching drawings/styles
                sb.append("<w:t></w:t>")
            }
            cursor = m.range.last + 1
        }
        sb.append(pXml, cursor, pXml.length)
        return sb.toString()
    }

    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun unescapeXml(text: String): String = text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")
}
